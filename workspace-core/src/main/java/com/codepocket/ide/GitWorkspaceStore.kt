package com.codepocket.ide

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Content identities, not editor-mirror versions. Provider IDs remain opaque. */
data class GitFileVersion(val sha256:String,val size:Int,val executable:Boolean?=null)
data class GitTreeVersion(val files:Map<String,GitFileVersion>,val directories:Set<String>)
data class GitMetadataChange(val path:String,val before:GitFileVersion?,val after:GitFileVersion?)
class GitWorkspaceSnapshot internal constructor(val root:File,val beforeRoot:File,val before:GitTreeVersion,
    val modeAvailable:Boolean,internal val owner:UUID)

/** Binary adapter for a real private workspace; writes replace one file atomically. */
class FileGitGateway(directory:File,private val replace:(File,File)->Unit={a,b->android.system.Os.rename(a.path,b.path)}):DocumentGateway {
    private val directory=directory.canonicalFile.also { require(it.isDirectory) }
    override val identity:String get()=directory.absolutePath
    internal fun metadataLease(paths:Collection<String>):GitMetadataLease {
        val locks=(paths.filter{it.startsWith(".git/refs/heads/")}+listOf(".git/index",".git/HEAD",".git/config"))
            .distinct().sorted().map{file("$it.lock")}
        return GitMetadataLease(directory,locks)
    }
    internal fun releaseRecordedLease(record:org.json.JSONObject?) {
        if(record==null)return
        val token=record.getString("token");require(Regex("CodePocket [a-f0-9-]{36}\n").matches(token))
        val paths=record.getJSONArray("paths");require(paths.length()<=GitWorkspaceStore.MAX_ENTRIES+3)
        for(i in 0 until paths.length()) {
            val path=paths.getString(i)
            require(path in listOf(".git/index.lock",".git/HEAD.lock",".git/config.lock") || path.startsWith(".git/refs/heads/")&&path.endsWith(".lock"))
            val lock=file(path)
            if(!lock.exists())continue
            check(lock.isFile && lock.length()==token.toByteArray(Charsets.UTF_8).size.toLong() && lock.readText(Charsets.UTF_8)==token && lock.delete()) {
                "Git 锁包含其他版本，已停止恢复"
            }
        }
    }
    private fun file(id:String):File {
        require(id.isEmpty() || !File(id).isAbsolute && id.split('/').none { it.isEmpty() || it=="." || it==".." || '\\' in it })
        val file=if(id.isEmpty())directory else File(directory,id)
        require(file.absoluteFile==file.canonicalFile && (file==directory || file.path.startsWith(directory.path+File.separator))) { "Git路径越界或包含链接" }
        return file
    }
    private fun node(file:File)=DocumentNode(file.relativeTo(directory).invariantSeparatorsPath.let{if(it==".")"" else it},file.name,file.isDirectory)
    override val root=DocumentNode("",directory.name,true)
    override fun children(id:String):List<DocumentNode> = (file(id).listFiles()?:throw IOException("无法读取Git目录")).map {
        file(node(it).id);node(it)
    }
    override fun read(id:String)=readBinary(id,GitWorkspaceStore.MAX_FILE).toString(Charsets.UTF_8)
    override fun readBytes(id:String)=readBinary(id,1_000_000)
    override fun readBinary(id:String,limit:Int):ByteArray {
        require(limit in 1..GitWorkspaceStore.MAX_FILE)
        val source=file(id);require(source.isFile && source.length()<=limit)
        return source.inputStream().use { stream->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val count=stream.read(buffer);if(count<0)break;require(out.size()+count<=limit);out.write(buffer,0,count)}
            out.toByteArray()
        }
    }
    override fun write(id:String,text:String)=writeBytes(id,text.toByteArray(Charsets.UTF_8))
    override fun writeBytes(id:String,bytes:ByteArray) {
        val target=file(id);require(target.isFile)
        // A sibling of the private workspace is on the same filesystem and is
        // outside repository enumeration; only this newly created file is cleaned.
        val temporary=File(directory.parentFile,".codepocket-git-"+UUID.randomUUID())
        require(temporary.absoluteFile==temporary.canonicalFile && temporary.createNewFile())
        try { FileOutputStream(temporary).use{it.write(bytes);it.fd.sync()};replace(temporary,target) }
        finally { require(temporary.delete() || !temporary.exists()) }
    }
    override fun create(parent:String,name:String,directory:Boolean):DocumentNode {
        Workspace.validateName(name);val folder=file(parent);require(folder.isDirectory)
        val target=file((if(parent.isEmpty())"" else "$parent/")+name)
        require(!target.exists() && if(directory)target.mkdir() else target.createNewFile())
        return node(target)
    }
    override fun executable(id:String)=file(id).canExecute()
    override fun rename(id:String,name:String):DocumentNode {
        Workspace.validateName(name);val source=file(id);require(source!=directory)
        val parent=source.parentFile?:error("无法重命名根目录")
        val target=file(parent.relativeTo(directory).invariantSeparatorsPath.let{if(it.isEmpty() || it==".")name else "$it/$name"})
        require(!target.exists() && source.renameTo(target));return node(target)
    }
    override fun move(id:String,oldParent:String,newParent:String):DocumentNode {
        val source=file(id);require(source.parentFile==file(oldParent) && source!=directory)
        val target=file((if(newParent.isEmpty())"" else "$newParent/")+source.name)
        require(file(newParent).isDirectory && !target.exists() && source.renameTo(target));return node(target)
    }
    override fun delete(id:String) {val source=file(id);require(source!=directory && source.delete())}
}

/** Captures live binary provider content; never scans or writes the editor mirror. */
class GitWorkspaceStore(internal val gateway:DocumentGateway,private val modeAvailable:Boolean=false,
    private val replaceJournal:(File,File)->Unit={a,b->android.system.Os.rename(a.path,b.path)}) {
    private val owner=UUID.randomUUID()
    private fun check(deadline:Long) {
        if(Thread.currentThread().isInterrupted)throw InterruptedException()
        require(System.nanoTime()<=deadline) { "Git工作区读取超时" }
    }
    private fun scan(work:File?=null,base:File?=null,lease:GitMetadataLease?=null):GitTreeVersion {
        lease?.verify()
        val files=linkedMapOf<String,GitFileVersion>();val directories=linkedSetOf("")
        val visited=mutableSetOf(gateway.root.id);var count=0;var total=0L
        val deadline=System.nanoTime()+20_000_000_000L
        fun visit(parent:DocumentNode,path:String,depth:Int) {
            check(deadline);require(depth<=32) { "Git目录超过32层" }
            val names=mutableSetOf<String>()
            gateway.children(parent.id).sortedBy{it.name}.forEach { entry->
                check(deadline)
                Workspace.validateName(entry.name)
                require(names.add(entry.name) && visited.add(entry.id)) { "Git提供方存在重名、循环或重复文档" }
                val relative=if(path.isEmpty())entry.name else "$path/${entry.name}"
                if(relative in lease?.paths.orEmpty())return@forEach
                require(++count<=MAX_ENTRIES) { "Git工作区超过10000项" }
                if(entry.directory) {
                    directories.add(relative)
                    if(work!=null)require(File(work,relative).mkdir())
                    if(base!=null && (relative==".git" || relative.startsWith(".git/")))require(File(base,relative).mkdirs())
                    visit(entry,relative,depth+1)
                } else {
                    val bytes=gateway.readBinary(entry.id,MAX_FILE);check(deadline)
                    total+=bytes.size;require(bytes.size<=MAX_FILE && total<=MAX_TOTAL) { "Git工作区超过128MB或单文件超过16MB" }
                    val executable=if(modeAvailable && !relative.startsWith(".git/"))gateway.executable(entry.id) else null
                    files[relative]=GitFileVersion(digest(bytes),bytes.size,executable)
                    if(work!=null) {
                        val file=File(work,relative);require(file.createNewFile());file.writeBytes(bytes)
                        if(executable!=null)file.setExecutable(executable,false)
                    }
                    if(base!=null && relative.startsWith(".git/")) {
                        val file=File(base,relative);require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                        require(file.createNewFile());file.writeBytes(bytes)
                    }
                }
            }
        }
        visit(gateway.root,"",0)
        return GitTreeVersion(files.toMap(),directories.toSet())
    }
    fun capture(destination:File):GitWorkspaceSnapshot {
        val job=destination.absoluteFile
        require(job==job.canonicalFile && !job.exists() && job.mkdir()) { "Git快照目录必须是新建的私有目录" }
        try {
            val work=File(job,"work");val base=File(job,"before")
            require(work.mkdir() && base.mkdir())
            val before=scan(work,base)
            return GitWorkspaceSnapshot(work,base,before,modeAvailable,owner)
        } catch(error:Throwable) {runCatching{deleteOwned(job)}.exceptionOrNull()?.let(error::addSuppressed);throw error}
    }
    fun assertFresh(snapshot:GitWorkspaceSnapshot) = assertFresh(snapshot,null)
    internal fun assertFresh(snapshot:GitWorkspaceSnapshot,lease:GitMetadataLease?) {
        require(snapshot.owner==owner) { "Git快照不属于当前工作区" }
        require(scan(lease=lease)==snapshot.before) { "Git工作区已变化，请刷新后重试" }
    }
    internal fun currentVersion(lease:GitMetadataLease?=null)=scan(lease=lease)
    internal fun resultVersion(snapshot:GitWorkspaceSnapshot):GitTreeVersion {
        require(snapshot.owner==owner)
        return GitWorkspaceStore(FileGitGateway(snapshot.root),snapshot.modeAvailable).scan()
    }
    fun publish(snapshot:GitWorkspaceSnapshot,recovery:File)=synchronized(transactionLock(recovery)) {
        GitMetadataTransaction(this,recovery,replaceJournal).publish(snapshot)
    }
    fun recover(recovery:File)=synchronized(transactionLock(recovery)) {
        GitMetadataTransaction(this,recovery,replaceJournal).recover()
    }
    fun recoveryDescription(recovery:File)=synchronized(transactionLock(recovery)) {
        GitMetadataTransaction(this,recovery,replaceJournal).description()
    }
    fun exportRecovery(recovery:File,output:java.io.OutputStream)=synchronized(transactionLock(recovery)) {
        GitMetadataTransaction(this,recovery,replaceJournal).export(output)
    }
    fun metadataChanges(snapshot:GitWorkspaceSnapshot):List<GitMetadataChange> {
        require(snapshot.owner==owner)
        val after=resultVersion(snapshot)
        val ordinary:(String)->Boolean={it!=".git" && !it.startsWith(".git/")}
        require(snapshot.before.files.filterKeys(ordinary)==after.files.filterKeys(ordinary) &&
            snapshot.before.directories.filter(ordinary).toSet()==after.directories.filter(ordinary).toSet()) { "Git操作改变了工作正文，已拒绝发布" }
        return (snapshot.before.files.keys+after.files.keys).sorted().mapNotNull { path->
            val before=snapshot.before.files[path];val current=after.files[path]
            if(before==current)null else GitMetadataChange(path,before,current).also{require(path.startsWith(".git/"))}
        }
    }
    companion object {
        private val transactionLocks=ConcurrentHashMap<String,Any>()
        private fun transactionLock(recovery:File)=transactionLocks.computeIfAbsent(recovery.canonicalPath){Any()}
        const val MAX_FILE=16_000_000
        const val MAX_TOTAL=128_000_000L
        const val MAX_ENTRIES=10_000
        fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        /** Only a newly created, owned job is removed; never follow links during cleanup. */
        private fun deleteOwned(root:File) {
            require(root.absoluteFile==root.canonicalFile)
            fun remove(file:File) {
                require(file==root || file.absolutePath.startsWith(root.absolutePath+File.separator))
                if(file.isDirectory && file.absoluteFile==file.canonicalFile)file.listFiles()?.forEach(::remove)
                require(file.delete() || !file.exists())
            }
            remove(root)
        }
    }
}
