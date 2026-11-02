package com.codepocket.ide

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class GitPushReceipt(val id:String,val scope:String,val url:String,val branch:String,val head:String,
    val expectedRemote:String?,val job:String,val snapshot:String,val state:String,val error:String?,val created:Long) {
    val confirmed get()=state in setOf("pushed","up_to_date","rejected","not_sent")
}

/** Durable intent precedes IPC. A filesystem lock serializes UI and :git_remote writers,
 * so a late cancellation cannot replace a confirmed server report with "unknown". */
internal class GitPushLedger(directory:File,private val replace:(File,File)->Unit={a,b->android.system.Os.rename(a.path,b.path)}) {
    constructor(context:Context):this(File(context.applicationContext.noBackupFilesDir,"git-push-receipts"))
    private val folder=directory.absoluteFile.also{require(it==it.canonicalFile && (it.isDirectory || it.mkdirs() || it.isDirectory))}
    private val mutex=locks.computeIfAbsent(folder.path){Any()}
    private val uuid=Regex("[a-f0-9-]{36}");private val sha=Regex("[a-f0-9]{64}")
    private fun file(id:String)=File(folder,"$id.json").also{require(uuid.matches(id) && it.absoluteFile==it.canonicalFile)}
    private fun <T> locked(work:()->T):T=synchronized(mutex) {
        val lock=File(folder,"ledger.lock");require(lock.absoluteFile==lock.canonicalFile && (!lock.exists() || lock.isFile))
        FileOutputStream(lock,true).channel.use{channel->channel.lock().use{work()}}
    }
    private fun json(r:GitPushReceipt)=JSONObject().put("version",1).put("id",r.id).put("scope",r.scope).put("url",r.url).put("branch",r.branch)
        .put("head",r.head).put("expected_remote",r.expectedRemote?:JSONObject.NULL).put("job",r.job).put("snapshot",r.snapshot)
        .put("state",r.state).put("error",r.error?:JSONObject.NULL).put("created",r.created)
    private fun read(id:String):GitPushReceipt {
        val source=file(id);require(source.isFile && source.length() in 1..8192)
        val j=JSONObject(source.readText());require(j.get("version")==1 && j.getString("id")==id)
        require(j.keys().asSequence().toSet()==setOf("version","id","scope","url","branch","head","expected_remote","job","snapshot","state","error","created"))
        val scope=j.getString("scope");val snapshot=j.getString("snapshot");val job=j.getString("job")
        require(sha.matches(scope) && sha.matches(snapshot) && uuid.matches(job))
        val url=GitRemoteAddress.canonical(j.getString("url"));val branch=GitRemoteProtocol.branch(j.getString("branch"))
        val head=GitRemoteProtocol.oid(j.getString("head"));val remote=if(j.isNull("expected_remote"))null else GitRemoteProtocol.oid(j.getString("expected_remote"))
        val state=j.getString("state");require(state in states)
        val error=if(j.isNull("error"))null else j.getString("error").also{require(it in GitRemoteProtocol.errors)}
        return GitPushReceipt(id,scope,url,branch,head,remote,job,snapshot,state,error,j.getLong("created"))
    }
    private fun write(r:GitPushReceipt) {
        val bytes=json(r).toString().toByteArray();require(bytes.size<=8192)
        val temporary=File(folder,"receipt-${UUID.randomUUID()}.next");require(temporary.createNewFile())
        try{FileOutputStream(temporary).use{it.write(bytes);it.fd.sync()};replace(temporary,file(r.id))}finally{require(temporary.delete() || !temporary.exists())}
    }
    fun begin(scope:String,root:File,request:JSONObject):GitPushReceipt=locked {
        require(request.getString("action")=="push" && request.has("expected_remote"))
        require(folder.listFiles().orEmpty().count{it.name.endsWith(".json")}<1000) { "推送记录已满，请先清理已确认记录" }
        val id=UUID.randomUUID().toString();val job=root.parentFile!!.name;require(uuid.matches(job))
        val snapshot=request.getString("expected");require(sha.matches(snapshot))
        val result=GitPushReceipt(id,GitWorkspaceStore.digest(scope.toByteArray()),GitRemoteAddress.canonical(request.getString("url")),
            GitRemoteProtocol.branch(request.getString("branch")),GitRemoteProtocol.oid(request.getString("expected_head")),
            if(request.isNull("expected_remote"))null else GitRemoteProtocol.oid(request.getString("expected_remote")),job,snapshot,"pending",null,System.currentTimeMillis())
        require(file(id).createNewFile());write(result);result
    }
    fun claim(id:String,root:File,request:JSONObject):GitPushReceipt=locked {
        require(request.optString("action")=="push" && request.has("expected_remote"))
        val r=read(id);require(r.state=="pending" && r.job==root.parentFile!!.name && r.snapshot==request.getString("expected") &&
            r.url==GitRemoteAddress.canonical(request.getString("url")) && r.branch==request.getString("branch") && r.head==request.getString("expected_head") &&
            r.expectedRemote==(if(request.isNull("expected_remote"))null else request.getString("expected_remote"))) { "推送意图与请求不符" }
        r.copy(state="started").also(::write)
    }
    fun finish(id:String,state:String,error:String?=null):GitPushReceipt=locked {
        require(state in states-setOf("pending","started") && (error==null || error in GitRemoteProtocol.errors))
        val r=read(id)
        if(r.confirmed)return@locked r
        r.copy(state=state,error=error).also(::write)
    }
    fun get(id:String)=locked{read(id)}
    fun forWorkspace(scope:String)=locked {
        val digest=GitWorkspaceStore.digest(scope.toByteArray())
        folder.listFiles().orEmpty().filter{it.name.endsWith(".json")}.map{read(it.name.removeSuffix(".json"))}
            .filter{it.scope==digest}.sortedByDescending{it.created}
    }
    fun clearConfirmed()=locked {
        folder.listFiles().orEmpty().filter{it.name.endsWith(".json")}.forEach { source->
            if(read(source.name.removeSuffix(".json")).confirmed)require(source.delete())
        }
    }
    companion object {
        private val locks=ConcurrentHashMap<String,Any>()
        private val states=setOf("pending","started","unknown","pushed","up_to_date","rejected","not_sent")
    }
}
