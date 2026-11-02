package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class GitRecoveryRequired(message:String,cause:Throwable?=null):IOException(message,cause)

/** Publishes only approved local-Git metadata. Provider writes are verified and journaled,
 * not advertised as a cross-file atomic transaction. Unknown concurrent bytes are never replaced. */
internal class GitMetadataTransaction(private val store:GitWorkspaceStore,directory:File,
    private val replace:(File,File)->Unit) {
    private val gateway=store.gateway
    private val directory=directory.absoluteFile.also { require(it==it.canonicalFile && (it.isDirectory || it.mkdirs())) }
    private val pending=File(this.directory,"pending.json")
    private fun valid(path:String) {
        require(path.startsWith(".git/") && path.split('/').all{it.isNotBlank() && it!="." && it!=".." && '\\' !in it})
        path.split('/').forEach(Workspace::validateName)
        require(path in listOf(".git/index",".git/HEAD",".git/COMMIT_EDITMSG",".git/config",".git/description",".git/info/exclude",".git/logs/HEAD") ||
            path.startsWith(".git/refs/heads/") || path.startsWith(".git/logs/refs/heads/") ||
            Regex("\\.git/objects/[a-f0-9]{2}/[a-f0-9]{38}").matches(path)) { "Git元数据变更不在本地操作范围内" }
    }
    private fun find(path:String):DocumentNode? {
        if(path.isEmpty())return gateway.root
        var node=gateway.root;val visited=mutableSetOf(node.id)
        for(part in path.split('/')) {
            Workspace.validateName(part);require(node.directory)
            val matches=gateway.children(node.id).filter{it.name==part}
            require(matches.size<=1) { "提供方存在重名Git文件" }
            node=matches.singleOrNull()?:return null
            require(visited.add(node.id)) { "Git提供方目录存在循环" }
        }
        return node
    }
    private fun bytes(node:DocumentNode):ByteArray {
        require(!node.directory)
        return gateway.readBinary(node.id,GitWorkspaceStore.MAX_FILE)
    }
    private fun hash(node:DocumentNode?)=node?.let{GitWorkspaceStore.digest(bytes(it))}
    private fun writeRecord(record:JSONObject) {
        val data=record.toString().toByteArray(Charsets.UTF_8);require(data.size<=4_000_000)
        val temporary=File(directory,"pending-${UUID.randomUUID()}.next")
        require(temporary.createNewFile())
        try {FileOutputStream(temporary).use{it.write(data);it.fd.sync()};replace(temporary,pending)}
        finally {require(temporary.delete() || !temporary.exists())}
    }
    private fun artifact(record:JSONObject,exists:Boolean=true):File {
        val name=record.getString("journal");require(Regex("transaction-[a-f0-9-]{36}").matches(name))
        return File(directory,name).also{require(it.absoluteFile==it.canonicalFile && (!exists || it.isDirectory))}
    }
    private fun cacheFile(folder:File,side:String,path:String)=File(folder,"$side/$path").also {
        valid(path);require(it.absoluteFile==it.canonicalFile && it.path.startsWith(folder.path+File.separator))
    }
    private fun cached(folder:File,side:String,path:String,expected:String):ByteArray {
        require(Regex("[a-f0-9]{64}").matches(expected))
        val source=cacheFile(folder,side,path);require(source.isFile && source.length()<=GitWorkspaceStore.MAX_FILE)
        return source.readBytes().also{require(GitWorkspaceStore.digest(it)==expected)}
    }
    private fun backup(folder:File,side:String,path:String,source:File,expected:String) {
        val data=source.readBytes();require(GitWorkspaceStore.digest(data)==expected)
        val target=cacheFile(folder,side,path);require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        require(target.createNewFile());FileOutputStream(target).use{it.write(data);it.fd.sync()}
    }
    private fun optional(entry:JSONObject,name:String)=if(entry.isNull(name))null else entry.getString(name)
    private fun removeArtifact(owned:File) {
        require(owned.parentFile==directory)
        if(!owned.exists())return
        fun remove(file:File) {
            require(file.absoluteFile==file.canonicalFile && (file==owned || file.path.startsWith(owned.path+File.separator)))
            if(file.isDirectory)file.listFiles()?.forEach(::remove)
            require(file.delete() || !file.exists())
        }
        remove(owned)
    }
    private fun cleanup(record:JSONObject) {
        val owned=artifact(record,false)
        // The user state is already committed/restored. Clear the pointer first;
        // a crash during private cache deletion then leaves only a safe orphan.
        require(pending.delete() || !pending.exists());removeArtifact(owned)
    }
    private fun rollback(record:JSONObject,release:()->Unit={},verify:()->Unit={}) {
        require(record.getString("scope")==gateway.identity) { "Git恢复记录不属于当前工作区" }
        val folder=artifact(record);val entries=record.getJSONArray("entries")
        require(entries.length()<=GitWorkspaceStore.MAX_ENTRIES)
        for(i in entries.length()-1 downTo 0) {
            verify()
            val entry=entries.getJSONObject(i);val path=entry.getString("path");valid(path)
            val before=optional(entry,"before");val after=entry.getString("after")
            require((before==null || Regex("[a-f0-9]{64}").matches(before)) && Regex("[a-f0-9]{64}").matches(after))
            val node=find(path);val current=hash(node)
            if(current==before)continue
            if(node==null || entry.optString("target_id")!=node.id)
                throw GitRecoveryRequired("Git恢复时文件身份已变化，原始备份已保留")
            val createdEmpty=before==null && entry.optBoolean("created") && current==GitWorkspaceStore.digest(byteArrayOf())
            if(current!=after && !createdEmpty)
                throw GitRecoveryRequired("Git元数据出现其他版本，已保留备份并停止恢复")
            if(before==null)gateway.delete(node.id)
            else {
                gateway.writeBytes(node.id,cached(folder,"before",path,before))
                require(hash(find(path))==before) { "Git恢复写后校验失败" }
            }
        }
        release()
        val directories=record.getJSONArray("created_directories")
        for(i in directories.length()-1 downTo 0) {
            val entry=directories.getJSONObject(i);val path=entry.getString("path")
            require(path==".git" || path.startsWith(".git/"));path.split('/').forEach(Workspace::validateName)
            val node=find(path)?:continue
            if(node.id!=entry.getString("id") || !node.directory || gateway.children(node.id).isNotEmpty())
                throw GitRecoveryRequired("Git恢复目录已有其他内容，已停止清理")
            gateway.delete(node.id)
        }
        cleanup(record)
    }
    fun recover() {
        if(!pending.exists()) {
            directory.listFiles()?.filter{it.isDirectory && Regex("transaction-[a-f0-9-]{36}").matches(it.name)}?.forEach(::removeArtifact)
            return
        }
        try {
            require(pending.isFile && pending.absoluteFile==pending.canonicalFile && pending.length()<=4_000_000)
            val record=JSONObject(pending.readText())
            require(record.getInt("version")==1 && record.getString("scope")==gateway.identity)
            require(record.getString("state") in listOf("applying","committed"))
            (gateway as? FileGitGateway)?.releaseRecordedLease(record.optJSONObject("metadata_locks"))
            if(record.getString("state")=="committed")cleanup(record) else {
                val entries=record.getJSONArray("entries")
                val paths=(0 until entries.length()).map{entries.getJSONObject(it).getString("path").also(::valid)}
                val lease=if(find(".git")?.directory==true)(gateway as? FileGitGateway)?.metadataLease(paths) else null
                var released=false
                fun release(){if(!released){released=true;lease?.close()}}
                try {
                    lease?.let{record.put("metadata_locks",it.record());writeRecord(record);it.acquire()}
                    rollback(record,::release){lease?.verify()}
                }finally{release()}
            }
        } catch(error:GitRecoveryRequired) {throw error}
        catch(error:Exception) {throw GitRecoveryRequired("Git恢复尚未完成，原始备份已保留",error)}
    }
    private fun record():JSONObject {
        require(pending.isFile && pending.absoluteFile==pending.canonicalFile && pending.length()<=4_000_000)
        return JSONObject(pending.readText()).also{require(it.getInt("version")==1 && it.getString("scope")==gateway.identity)}
    }
    fun description():String {
        if(!pending.exists())return "没有待恢复的 Git 记录"
        val record=record();val entries=record.getJSONArray("entries");require(entries.length()<=GitWorkspaceStore.MAX_ENTRIES)
        return buildString {
            append("Git 元数据恢复记录\n状态：").append(record.getString("state"))
            append("\n工作正文未纳入此事务。未知版本不会自动覆盖，可导出备份与当前字节进行人工比较。\n")
            for(i in 0 until entries.length()) {
                val entry=entries.getJSONObject(i);val path=entry.getString("path");valid(path)
                append('\n').append(path).append("\n原 SHA256：").append(optional(entry,"before")?:"新建文件")
                append("\n拟写 SHA256：").append(entry.getString("after")).append('\n')
            }
        }
    }
    fun export(output:java.io.OutputStream) {
        val record=record();val folder=artifact(record);val entries=record.getJSONArray("entries")
        require(entries.length()<=GitWorkspaceStore.MAX_ENTRIES)
        var total=0L
        java.util.zip.ZipOutputStream(output).use{zip->
            fun add(name:String,data:ByteArray) {
                total+=data.size;require(total<=GitWorkspaceStore.MAX_TOTAL*3+4_000_000)
                zip.putNextEntry(java.util.zip.ZipEntry(name));zip.write(data);zip.closeEntry()
            }
            add("pending.json",record.toString(2).toByteArray(Charsets.UTF_8))
            for(i in 0 until entries.length()) {
                val entry=entries.getJSONObject(i);val path=entry.getString("path");valid(path)
                optional(entry,"before")?.let{add("before/$path",cached(folder,"before",path,it))}
                add("after/$path",cached(folder,"after",path,entry.getString("after")))
                find(path)?.takeIf{!it.directory}?.let{add("current/$path",bytes(it))}
            }
        }
    }
    fun publish(snapshot:GitWorkspaceSnapshot) {
        recover();store.assertFresh(snapshot)
        val changes=store.metadataChanges(snapshot)
        if(changes.isEmpty())return
        for(change in changes) {
            valid(change.path);require(change.after!=null) { "本地Git操作不能删除原元数据" }
            require(!change.path.startsWith(".git/objects/") || change.before==null) { "原Git对象不可覆盖" }
            require(change.path!=".git/config" || change.before==null) { "原Git配置不可覆盖" }
        }
        val after=store.resultVersion(snapshot)
        val folder=File(directory,"transaction-${UUID.randomUUID()}");require(folder.mkdir())
        val entries=JSONArray()
        val record=JSONObject().put("version",1).put("scope",gateway.identity).put("state","applying")
            .put("journal",folder.name).put("entries",entries).put("created_directories",JSONArray())
        // Immutable objects first, then logs/index/config, branch refs, HEAD last.
        fun priority(path:String)=when {
            path.startsWith(".git/objects/")->0
            path.startsWith(".git/logs/")->1
            path.startsWith(".git/refs/")->4
            path==".git/HEAD"->5
            else->2
        }
        var prepared=false
        var lease:GitMetadataLease?=null;var released=false
        fun release(){if(!released){released=true;lease?.close()}}
        try {
            changes.sortedWith(compareBy<GitMetadataChange>{priority(it.path)}.thenBy{it.path}).forEach { change->
                change.before?.let{backup(folder,"before",change.path,File(snapshot.beforeRoot,change.path),it.sha256)}
                backup(folder,"after",change.path,File(snapshot.root,change.path),change.after!!.sha256)
                entries.put(JSONObject().put("path",change.path).put("before",change.before?.sha256?:JSONObject.NULL)
                    .put("after",change.after.sha256).put("target_id",JSONObject.NULL).put("created",false))
            }
            writeRecord(record);prepared=true
            store.assertFresh(snapshot)
            val additions=(after.directories-snapshot.before.directories).sortedWith(compareBy<String>{it.count{c->c=='/'}}.thenBy{it})
            for(path in additions) {
                require(path==".git" || path.startsWith(".git/"));require(find(path)==null) { "Git目录已被其他操作创建" }
                val parent=find(path.substringBeforeLast('/',""))?:error("Git父目录不存在")
                val node=gateway.create(parent.id,path.substringAfterLast('/'),true)
                record.getJSONArray("created_directories").put(JSONObject().put("path",path).put("id",node.id));writeRecord(record)
                require(node.name==path.substringAfterLast('/') && node.directory) { "提供方改变了Git目录名称" }
            }
            lease=(gateway as? FileGitGateway)?.metadataLease(changes.map{it.path})
            lease?.let{record.put("metadata_locks",it.record());writeRecord(record);it.acquire()}
            require(store.currentVersion(lease)==snapshot.before.copy(directories=snapshot.before.directories+additions)) {
                "Git工作区已变化，请刷新后重试"
            }
            for(i in 0 until entries.length()) {
                lease?.verify()
                val entry=entries.getJSONObject(i);val path=entry.getString("path");val before=optional(entry,"before")
                var node=find(path);require(hash(node)==before) { "Git元数据已在操作之外变化" }
                if(node==null) {
                    val parent=find(path.substringBeforeLast('/'))?:error("Git父目录不存在")
                    node=gateway.createBinary(parent.id,path.substringAfterLast('/'))
                    entry.put("created",true).put("target_id",node.id);writeRecord(record)
                    require(node.name==path.substringAfterLast('/') && !node.directory) { "提供方改变了Git文件名称" }
                    require(bytes(node).isEmpty()) { "提供方新建Git文件已有其他内容" }
                } else {entry.put("target_id",node.id);writeRecord(record)}
                gateway.writeBytes(node.id,cached(folder,"after",path,entry.getString("after")))
                val actual=find(path);require(hash(actual)==entry.getString("after")) { "Git写后校验失败" }
                entry.put("target_id",actual!!.id);writeRecord(record)
            }
            require(store.currentVersion(lease)==after) { "Git提交期间工作区发生变化" }
            record.put("state","committed");writeRecord(record)
            // Retain the completed journal until the next operation, then clear
            // only its private cache. User metadata is never cleaned on success.
        } catch(error:Throwable) {
            if(prepared)try{rollback(record,::release){lease?.verify()}}catch(recovery:Throwable){
                error.addSuppressed(recovery);throw GitRecoveryRequired("Git操作未完成，原始备份已保留供恢复",error)
            }
            else runCatching{cleanup(record)}.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        } finally {release()}
    }
}
