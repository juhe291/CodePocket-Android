package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A separate remote-publication journal. SAF has no multi-document atomic commit.
 * Unknown bytes, unknown provider identities and unacknowledged deletions retain backups. */
internal class GitWorktreeTransaction(private val store:GitWorkspaceStore,directory:File,
    private val replace:(File,File)->Unit) {
    private val gateway=store.gateway
    private val directory=directory.absoluteFile.also{require(it==it.canonicalFile && (it.isDirectory || it.mkdirs()))}
    private val pending=File(this.directory,"pending.json")
    companion object {const val MAX_RECORD=16_000_000L}
    private val hash=Regex("[a-f0-9]{64}")
    private fun metadata(path:String)=path==".git" || path.startsWith(".git/")
    private fun find(path:String):DocumentNode? {
        if(path.isEmpty())return gateway.root
        GitRemotePlan.path(path)
        var node=gateway.root;val seen=mutableSetOf(node.id)
        for(part in path.split('/')) {
            if(!node.directory)return null
            val matches=gateway.children(node.id).filter{it.name==part};require(matches.size<=1)
            node=matches.singleOrNull()?:return null;require(seen.add(node.id))
        }
        return node
    }
    private fun data(node:DocumentNode)=gateway.readBinary(node.id,GitWorkspaceStore.MAX_FILE).also{require(!node.directory)}
    private fun sameBytes(node:DocumentNode?,version:GitFileVersion)=node!=null && !node.directory && data(node).let {
        it.size==version.size && GitWorkspaceStore.digest(it)==version.sha256
    }
    private fun same(node:DocumentNode?,version:GitFileVersion?)=if(version==null)node==null else
        sameBytes(node,version) && (version.executable==null || gateway.executable(node!!.id)==version.executable)
    private fun mode(node:DocumentNode,version:GitFileVersion) {
        version.executable?.let{gateway.setExecutable(node.id,it);check(gateway.executable(node.id)==it)}
    }
    private fun version(value:GitFileVersion?)=value?.let{JSONObject().put("sha256",it.sha256).put("size",it.size)
        .put("executable",it.executable?:JSONObject.NULL)}?:JSONObject.NULL
    private fun version(entry:JSONObject,key:String):GitFileVersion? {
        if(entry.isNull(key))return null
        val json=entry.getJSONObject(key);val sha=json.getString("sha256");val size=json.get("size")
        require(hash.matches(sha) && (size is Int || size is Long))
        val length=(size as Number).toLong();require(length in 0..GitWorkspaceStore.MAX_FILE.toLong())
        val executable=if(json.isNull("executable"))null else json.get("executable").also{require(it is Boolean)} as Boolean
        return GitFileVersion(sha,length.toInt(),executable)
    }
    private fun optional(json:JSONObject,key:String)=if(json.isNull(key))null else json.getString(key)
    private fun write(record:JSONObject) {
        val bytes=record.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=MAX_RECORD)
        val next=File(directory,"remote-pending-${UUID.randomUUID()}.next");require(next.createNewFile())
        try {FileOutputStream(next).use{it.write(bytes);it.fd.sync()};replace(next,pending)}
        finally {require(next.delete() || !next.exists())}
    }
    private fun record():JSONObject {
        require(pending.absoluteFile==pending.canonicalFile && pending.isFile && pending.length()<=MAX_RECORD)
        val json=JSONObject(pending.readText());require(json.getInt("version")==2 && json.getString("kind")=="remote" && json.getString("scope")==gateway.identity)
        require(json.getString("state") in setOf("applying","rolling_back","committed") && json.getString("action") in GitRemotePlan.actions)
        val entries=json.getJSONArray("entries");require(entries.length()<=GitWorkspaceStore.MAX_ENTRIES*2)
        for(i in 0 until entries.length()) {
            val entry=entries.getJSONObject(i);GitRemotePlan.path(entry.getString("path"))
            require(entry.getString("kind") in setOf("write","delete","mkdir","rmdir"))
            require(entry.getString("phase") in setOf("planned","intent","created","applied","recreating","restoring","restored"))
            if(entry.getString("kind")=="write")require(version(entry,"after")!=null)
            if(entry.getString("kind")=="delete")require(version(entry,"before")!=null && version(entry,"after")==null)
        }
        return json
    }
    private fun artifact(record:JSONObject,exists:Boolean=true):File {
        val name=record.getString("journal");require(Regex("remote-transaction-[a-f0-9-]{36}").matches(name))
        return File(directory,name).also{require(it.absoluteFile==it.canonicalFile && (!exists || it.isDirectory))}
    }
    private fun cache(folder:File,side:String,path:String):File {
        require(side in setOf("before","after"));GitRemotePlan.path(path)
        return File(folder,"$side/$path").also{require(it.absoluteFile==it.canonicalFile && it.path.startsWith(folder.path+File.separator))}
    }
    private fun cached(folder:File,side:String,path:String,version:GitFileVersion):ByteArray {
        val source=cache(folder,side,path);require(source.isFile && source.length()==version.size.toLong())
        return source.readBytes().also{require(GitWorkspaceStore.digest(it)==version.sha256)}
    }
    private fun backup(folder:File,side:String,path:String,source:File,version:GitFileVersion) {
        require(source.absoluteFile==source.canonicalFile && source.isFile && source.length()==version.size.toLong())
        val bytes=source.readBytes();require(GitWorkspaceStore.digest(bytes)==version.sha256)
        val target=cache(folder,side,path);require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs());require(target.createNewFile())
        FileOutputStream(target).use{it.write(bytes);it.fd.sync()}
    }
    private fun remove(folder:File) {
        require(folder.parentFile==directory && Regex("remote-transaction-[a-f0-9-]{36}").matches(folder.name))
        if(!folder.exists())return
        fun visit(file:File) {
            require(file.absoluteFile==file.canonicalFile && (file==folder || file.path.startsWith(folder.path+File.separator)))
            if(file.isDirectory)file.listFiles()?.forEach(::visit)
            require(file.delete() || !file.exists())
        }
        visit(folder)
    }
    private fun cleanup(record:JSONObject) {
        val folder=artifact(record,false)
        require(pending.delete() || !pending.exists());remove(folder)
    }
    private fun parent(path:String)=find(path.substringBeforeLast('/',""))?.also{require(it.directory)}?:error("Git父目录不存在")
    private fun created(node:DocumentNode,path:String,directory:Boolean) {
        require(node.name==path.substringAfterLast('/') && node.directory==directory && find(path)?.id==node.id) { "提供方改变了目标名称或身份" }
    }
    private fun apply(record:JSONObject,entry:JSONObject,folder:File) {
        val path=entry.getString("path");val kind=entry.getString("kind");var node=find(path)
        val before=version(entry,"before");val after=version(entry,"after")
        when(kind) {
            "mkdir"->{
                require(node==null);entry.put("phase","intent");write(record);require(find(path)==null)
                node=gateway.create(parent(path).id,path.substringAfterLast('/'),true)
                entry.put("target_id",node.id).put("target_name",node.name).put("phase","applied");write(record);created(node,path,true)
                require(gateway.children(node.id).isEmpty())
            }
            "rmdir"->{
                require(node!=null && node.directory && gateway.children(node.id).isEmpty())
                entry.put("target_id",node.id).put("phase","intent");write(record)
                require(find(path)?.id==node.id && gateway.children(node.id).isEmpty())
                gateway.delete(node.id);require(find(path)==null);entry.put("phase","applied");write(record)
            }
            "delete"->{
                require(same(node,before) && node!=null)
                entry.put("target_id",node.id).put("phase","intent");write(record)
                require(find(path)?.id==node.id && same(find(path),before))
                gateway.delete(node.id);require(find(path)==null);entry.put("phase","applied");write(record)
            }
            "write"->{
                require(same(node,before));entry.put("target_id",node?.id?:JSONObject.NULL).put("phase","intent");write(record)
                if(node==null) {
                    require(find(path)==null)
                    node=gateway.createBinary(parent(path).id,path.substringAfterLast('/'))
                    entry.put("target_id",node.id).put("target_name",node.name).put("created_mode",if(after!!.executable==null)JSONObject.NULL else gateway.executable(node.id))
                        .put("phase","created");write(record);created(node,path,false);require(data(node).isEmpty())
                } else require(find(path)?.id==node.id && same(find(path),before))
                gateway.writeBytes(node.id,cached(folder,"after",path,after!!));mode(node,after)
                require(find(path)?.id==node.id && same(find(path),after)) { "Git写后校验失败" }
                entry.put("phase","applied");write(record)
            }
            else->error("未知Git事务步骤")
        }
    }
    private fun restored(record:JSONObject,entry:JSONObject) {entry.put("phase","restored");write(record)}
    private fun restore(record:JSONObject,entry:JSONObject,folder:File) {
        val phase=entry.getString("phase");if(phase in setOf("planned","restored"))return
        val path=entry.getString("path");val kind=entry.getString("kind");var node=find(path)
        val before=version(entry,"before");val after=version(entry,"after")
        val target=optional(entry,"target_id");val restoredId=optional(entry,"restore_id")
        fun unknown():Nothing=throw GitRecoveryRequired("Git恢复遇到未知版本或未确认的提供方操作：$path，原始备份已保留")
        if(entry.has("target_name") && entry.getString("target_name")!=path.substringAfterLast('/'))unknown()
        when(kind) {
            "write"->{
                if(before==null) {
                    if(node==null){if(target==null && phase=="intent")unknown();restored(record,entry);return}
                    if(node.id!=target || node.directory)unknown()
                    val empty=data(node).isEmpty()
                    val known=empty || after!=null && sameBytes(node,after)
                    val transient=if(entry.isNull("created_mode"))null else entry.getBoolean("created_mode")
                    if(!known || after!!.executable!=null && gateway.executable(node.id) !in setOf(transient,after.executable))unknown()
                    gateway.delete(node.id);require(find(path)==null)
                } else {
                    if(node==null || node.id!=target || node.directory)unknown()
                    if(same(node,before)){restored(record,entry);return}
                    if(after==null || !sameBytes(node,after) || before.executable!=null && gateway.executable(node.id) !in setOf(before.executable,after.executable))unknown()
                    entry.put("phase","restoring");write(record)
                    require(find(path)?.id==node.id && sameBytes(find(path),after))
                    gateway.writeBytes(node.id,cached(folder,"before",path,before));mode(node,before)
                    require(find(path)?.id==node.id && same(find(path),before))
                }
            }
            "delete"->{
                require(before!=null)
                if(node!=null && node.id==target && same(node,before)){restored(record,entry);return}
                if(node==null) {
                    if(phase !in setOf("applied","recreating","restoring"))unknown()
                    // The delete returned and its acknowledgement was persisted.
                    // Recreating is exclusive; an unacknowledged create is never guessed.
                    entry.put("phase","recreating");write(record);require(find(path)==null)
                    node=gateway.createBinary(parent(path).id,path.substringAfterLast('/'))
                    entry.put("restore_id",node.id).put("phase","restoring");write(record);created(node,path,false)
                    require(data(node).isEmpty())
                } else if(node.id!=restoredId || node.directory || !(data(node).isEmpty() || sameBytes(node,before)))unknown()
                entry.put("phase","restoring");write(record)
                require(find(path)?.id==node.id)
                gateway.writeBytes(node.id,cached(folder,"before",path,before));mode(node,before)
                require(find(path)?.id==node.id && same(find(path),before))
            }
            "mkdir"->{
                if(node==null){if(target==null && phase=="intent")unknown();restored(record,entry);return}
                if(node.id!=target || !node.directory || gateway.children(node.id).isNotEmpty())unknown()
                gateway.delete(node.id);require(find(path)==null)
            }
            "rmdir"->{
                if(node!=null && node.id==target && node.directory){restored(record,entry);return}
                if(node==null) {
                    if(phase !in setOf("applied","recreating"))unknown()
                    entry.put("phase","recreating");write(record);require(find(path)==null)
                    node=gateway.create(parent(path).id,path.substringAfterLast('/'),true)
                    entry.put("restore_id",node.id);write(record);created(node,path,true)
                } else if(node.id!=restoredId || !node.directory || gateway.children(node.id).isNotEmpty())unknown()
            }
            else->unknown()
        }
        restored(record,entry)
    }
    private fun rollback(record:JSONObject,release:()->Unit={},verify:()->Unit={}) {
        require(record.getString("scope")==gateway.identity)
        val folder=artifact(record);record.put("state","rolling_back");write(record)
        val entries=record.getJSONArray("entries");val deadline=System.nanoTime()+120_000_000_000L
        var locksDone=record.optBoolean("locks_done",false)
        for(i in entries.length()-1 downTo 0) {
            require(System.nanoTime()<=deadline) { "Git恢复超时，备份已保留" }
            val entry=entries.getJSONObject(i)
            if(entry.getString("kind")=="mkdir" && metadata(entry.getString("path")) && !locksDone) {
                record.put("locks_done",true);write(record);release();locksDone=true
            }
            if(!locksDone)verify()
            restore(record,entry,folder)
        }
        release();cleanup(record)
    }
    fun recover() {
        if(!pending.exists()) {
            directory.listFiles()?.filter{it.isDirectory && Regex("remote-transaction-[a-f0-9-]{36}").matches(it.name)}?.forEach(::remove)
            return
        }
        try {
            val record=record();(gateway as? FileGitGateway)?.releaseRecordedLease(record.optJSONObject("metadata_locks"))
            if(record.getString("state")=="committed"){cleanup(record);return}
            val entries=record.getJSONArray("entries")
            val references=(0 until entries.length()).map{entries.getJSONObject(it)}.filter{it.getString("kind") in setOf("write","delete")}
                .map{it.getString("path")}
                .filter{!metadata(it) || find(it.substringBeforeLast('/',""))?.directory==true}
            val lease=if(!record.optBoolean("locks_done") && find(".git")?.directory==true)
                (gateway as? FileGitGateway)?.metadataLease(references,true) else null
            var active=false;var released=false
            fun release(){if(!released){released=true;lease?.close()}}
            try {
                lease?.let{record.put("metadata_locks",it.record());write(record);it.acquire();active=true}
                rollback(record,::release){if(active)lease?.verify()}
            } finally{release()}
        } catch(error:GitRecoveryRequired){throw error}
        catch(error:Exception){throw GitRecoveryRequired("Git正文恢复尚未完成，原始备份已保留",error)}
    }
    fun publish(snapshot:GitWorkspaceSnapshot,plan:GitRemotePlan) {
        require(!pending.exists());store.assertFresh(snapshot)
        val changes=store.remoteChanges(snapshot,plan);val after=store.resultVersion(snapshot)
        require(plan.files==after.files.mapValues{(_,v)->v.copy(executable=null)} && plan.directories==after.directories)
        if(changes.isEmpty() && after.directories==snapshot.before.directories)return
        val folder=File(directory,"remote-transaction-${UUID.randomUUID()}");require(folder.mkdir())
        val entries=JSONArray()
        fun step(kind:String,path:String,change:GitMetadataChange?=null) {
            GitRemotePlan.path(path)
            entries.put(JSONObject().put("kind",kind).put("path",path).put("phase","planned").put("target_id",JSONObject.NULL)
                .put("restore_id",JSONObject.NULL).put("before",version(change?.before)).put("after",version(change?.after)))
        }
        val depth=compareBy<String>{it.count{c->c=='/'}}.thenBy{it}
        val added=(after.directories-snapshot.before.directories).filter{it.isNotEmpty()}.sortedWith(depth)
        added.filter(::metadata).forEach{step("mkdir",it)}
        val objects=changes.filter{it.path.startsWith(".git/objects/")}
        objects.forEach{step("write",it.path,it)}
        changes.filter{!metadata(it.path) && it.after==null}.forEach{step("delete",it.path,it)}
        (snapshot.before.directories-after.directories).filter{it.isNotEmpty()}.sortedWith(depth.reversed()).forEach{require(!metadata(it));step("rmdir",it)}
        added.filter{!metadata(it)}.forEach{step("mkdir",it)}
        changes.filter{!metadata(it.path) && it.after!=null}.forEach{step("write",it.path,it)}
        fun priority(path:String)=when {path==".git/HEAD"->4;path.startsWith(".git/refs/")->3;path==".git/index"->2;else->1}
        changes.filter{metadata(it.path) && it !in objects}.sortedWith(compareBy<GitMetadataChange>{priority(it.path)}.thenBy{it.path})
            .forEach{step("write",it.path,it)}
        val record=JSONObject().put("version",2).put("kind","remote").put("scope",gateway.identity).put("action",plan.action)
            .put("url",plan.url).put("branch",plan.remoteBranch).put("before_head",plan.beforeHead?:JSONObject.NULL).put("after_head",plan.afterHead?:JSONObject.NULL)
            .put("state","applying").put("journal",folder.name).put("entries",entries).put("locks_done",false)
        var prepared=false;var lease:GitMetadataLease?=null;var active=false;var released=false
        fun release(){if(!released){released=true;lease?.close()}}
        try {
            for(change in changes) {
                change.before?.let{backup(folder,"before",change.path,File(snapshot.beforeRoot,change.path),it)}
                change.after?.let{backup(folder,"after",change.path,File(snapshot.root,change.path),it)}
            }
            store.assertFresh(snapshot);prepared=true;write(record)
            val initial=added.filter(::metadata).size
            for(i in 0 until initial)apply(record,entries.getJSONObject(i),folder)
            lease=(gateway as? FileGitGateway)?.metadataLease(changes.map{it.path},true)
            lease?.let{record.put("metadata_locks",it.record());write(record);it.acquire();active=true}
            require(store.currentVersion(lease)==snapshot.before.copy(directories=snapshot.before.directories+added.filter(::metadata))) {
                "Git工作区已在发布前变化"
            }
            val deadline=System.nanoTime()+120_000_000_000L
            for(i in initial until entries.length()) {
                if(Thread.currentThread().isInterrupted)throw InterruptedException()
                require(System.nanoTime()<=deadline) { "Git正文发布超时" };lease?.verify()
                apply(record,entries.getJSONObject(i),folder)
            }
            require(store.currentVersion(lease)==after) { "Git发布期间工作区出现其他版本" }
            record.put("state","committed");write(record)
        } catch(error:Throwable) {
            if(prepared)try{rollback(record,::release){if(active)lease?.verify()}}catch(recovery:Throwable){
                error.addSuppressed(recovery);throw GitRecoveryRequired("Git正文操作未完成，备份与恢复记录已保留",error)
            } else runCatching{remove(folder)}.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        } finally {release()}
    }
    fun description():String {
        if(!pending.exists())return "没有待恢复的 Git 记录"
        val record=record();val entries=record.getJSONArray("entries")
        return buildString {
            append("Git 正文与元数据恢复记录\n状态：").append(record.getString("state"))
            append("\n操作：").append(record.getString("action")).append("\n来源：").append(record.optString("url"))
            append("\n远端分支：").append(record.optString("branch"))
            append("\n未知字节、身份或未确认的删除不会自动覆盖。可导出前后备份和当前字节进行比较。\n")
            for(i in 0 until entries.length()) {
                val entry=entries.getJSONObject(i);append('\n').append(entry.getString("kind")).append(' ').append(entry.getString("path"))
                append("\n步骤：").append(entry.getString("phase"))
                version(entry,"before")?.let{append("\n原 SHA256：").append(it.sha256)}
                version(entry,"after")?.let{append("\n拟写 SHA256：").append(it.sha256)}
                append('\n')
            }
        }
    }
    fun export(output:OutputStream) {
        val record=record();val folder=artifact(record);val entries=record.getJSONArray("entries");val emitted=mutableSetOf<String>();var total=0L
        ZipOutputStream(output).use{zip->
            fun add(name:String,bytes:ByteArray) {
                if(!emitted.add(name))return
                total+=bytes.size;require(total<=GitWorkspaceStore.MAX_TOTAL*3+MAX_RECORD)
                zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()
            }
            add("pending.json",record.toString(2).toByteArray())
            for(i in 0 until entries.length()) {
                val entry=entries.getJSONObject(i);val path=entry.getString("path")
                version(entry,"before")?.let{add("before/$path",cached(folder,"before",path,it))}
                version(entry,"after")?.let{add("after/$path",cached(folder,"after",path,it))}
                find(path)?.takeIf{!it.directory}?.let{add("current/$path",data(it))}
            }
        }
    }
}
