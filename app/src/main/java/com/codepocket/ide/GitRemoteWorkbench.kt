package com.codepocket.ide

import android.content.Context
import android.os.Looper
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException

internal class GitRemoteGuardFailure(val code:String,val paths:List<String> = emptyList()):IOException(code)
internal class GitRemoteWriteGuard(private val current:()->Boolean,private val pending:()->List<String>) {
    fun isCurrent()=current()
    fun check() {
        if(!current())throw GitRemoteGuardFailure("editor_changed")
        val paths=pending();if(paths.isNotEmpty())throw GitRemoteGuardFailure("drafts_pending",paths)
    }
    companion object {
        fun drafts(keys:Map<String,String>,store:DraftStore,current:()->Boolean)=GitRemoteWriteGuard(current) {
            keys.mapNotNull{(path,key)->store.read(key)?.also{require(it.path==path)}?.let{path}}
        }
    }
}
internal data class GitRemoteRun(val reply:GitRemoteReply=GitRemoteReply(error="backend"),val storageError:String?=null,
    val detail:String?=null,val draftPaths:List<String> = emptyList(),val receipt:GitPushReceipt?=null,
    val published:Boolean=false,val sourceFiles:Map<String,GitFileVersion> = emptyMap()) {
    val error get()=storageError?:reply.error
    val ok get()=reply.ok && storageError==null
}

/** Connects owned remote snapshots, private IPC, original publication and durable push receipts.
 * Only publication/recovery joins WorkspaceIo; waiting for HTTPS never blocks ordinary IO. */
internal class GitRemoteWorkbench(context:Context,private val client:GitRemoteClient,private val ledger:GitPushLedger):Closeable {
    constructor(context:Context):this(context,GitRemoteClient(context),GitPushLedger(context))
    private val app=context.applicationContext
    private fun recovery(identity:String)=File(app.filesDir.canonicalFile,"git-recovery/"+DocumentTree.digest(identity))
    private fun <T> serial(work:()->T):T {
        val task=WorkspaceIo.executor.submit<T>{work()};var interrupted=false
        try {
            while(true)try{return task.get()}catch(_:InterruptedException){interrupted=true}
        }catch(error:ExecutionException){throw error.cause?:error}
        finally{if(interrupted)Thread.currentThread().interrupt()}
    }
    fun perform(workspace:Workspace,action:String,fields:JSONObject=JSONObject(),expected:String?=null,
        credentials:GitCredentials?=null,guard:GitRemoteWriteGuard?=null,cancelled:()->Boolean={false})=
        perform(workspace.gitStore(),workspace.identity,action,fields,expected,credentials,guard,cancelled)
    fun perform(store:GitWorkspaceStore,identity:String,action:String,fields:JSONObject=JSONObject(),expected:String?=null,
        credentials:GitCredentials?=null,guard:GitRemoteWriteGuard?=null,cancelled:()->Boolean={false}):GitRemoteRun {
        check(Looper.myLooper()!=Looper.getMainLooper()) { "远端Git操作必须在后台执行" }
        var snapshot:GitWorkspaceSnapshot?=null
        fun current(){if(cancelled() || Thread.currentThread().isInterrupted)throw InterruptedException()}
        return try {
            require(action in GitRemoteProtocol.operations);current()
            if(action=="fast_forward"){require(guard!=null);serial{current();guard.check()}}
            serial{current();store.recover(recovery(identity))};current()
            val copy=store.capture(GitRemoteJobs.next(app),action in setOf("clone","fast_forward"));snapshot=copy
            val request=JSONObject(fields.toString()).put("action",action).put("modeAvailable",copy.modeAvailable)
            if(action in GitRemoteProtocol.publications || action=="push") {
                val before=client.request(copy.root,JSONObject().put("action","snapshot").put("modeAvailable",copy.modeAvailable))
                if(!before.ok)return GitRemoteRun(before)
                request.put("expected",expected?:before.state!!.snapshot)
            }
            if(credentials!=null){request.put("username",credentials.username).put("token",credentials.token)}
            current()
            val sources=copy.before.files.filterKeys{it!=".git" && !it.startsWith(".git/")}
            if(action=="push") {
                val (reply,receipt)=client.push(copy.root,request,identity,ledger)
                // The receipt has already been persisted. A stale/closed UI must not erase it.
                return GitRemoteRun(reply,receipt=receipt,sourceFiles=sources)
            }
            val reply=client.request(copy.root,request);current()
            if(!reply.ok)return GitRemoteRun(reply,sourceFiles=sources)
            var published=false
            if(action in GitRemoteProtocol.publications) {
                require(reply.plan=="result.plan")
                val url=reply.remote?.url?:error("远端结果缺少来源");val branch=reply.remote.branch?:error("远端结果缺少分支")
                val plan=GitRemotePlan.read(copy,action,request.getString("expected"),reply.state!!.snapshot,url,branch)
                serial {
                    current();if(action=="fast_forward")guard!!.check()
                    store.publishRemote(copy,plan,recovery(identity));published=true
                }
            } else {
                require(store.metadataChanges(copy).isEmpty()) { "远端读取改变了本地快照" };store.assertFresh(copy)
            }
            GitRemoteRun(reply,published=published,sourceFiles=sources)
        }catch(error:InterruptedException){throw error}
        catch(error:GitRemoteGuardFailure){GitRemoteRun(storageError=error.code,draftPaths=error.paths)}
        catch(error:GitRecoveryRequired){GitRemoteRun(storageError="recovery_required",detail=error.message)}
        catch(error:IllegalArgumentException){GitRemoteRun(storageError="workspace_changed",detail=error.message)}
        catch(error:Exception){GitRemoteRun(storageError="storage",detail=error.message)}
        finally{snapshot?.let{runCatching{GitRemoteJobs.discard(app,it.root)}}}
    }
    fun branches(url:String,credentials:GitCredentials?=null,cancelled:()->Boolean={false}):GitRemoteRun {
        check(Looper.myLooper()!=Looper.getMainLooper())
        val job=GitRemoteJobs.next(app);require(job.mkdir());val root=File(job,"work");require(root.mkdir())
        try {
            if(cancelled() || Thread.currentThread().isInterrupted)throw InterruptedException()
            val request=JSONObject().put("action","remote_refs").put("url",GitRemoteAddress.canonical(url)).put("modeAvailable",false)
            credentials?.let{request.put("username",it.username).put("token",it.token)}
            val result=client.request(root,request)
            require(root.listFiles().orEmpty().isEmpty()) { "远端引用查询改变了私有空目录" }
            return GitRemoteRun(result)
        }finally{runCatching{GitRemoteJobs.discard(app,root)}}
    }
    fun receipts(identity:String)=ledger.forWorkspace(identity)
    fun clearConfirmedReceipts()=ledger.clearConfirmed()
    fun recoveryDescription(identity:String,store:GitWorkspaceStore)=store.recoveryDescription(recovery(identity))
    fun exportRecovery(identity:String,store:GitWorkspaceStore,output:java.io.OutputStream)=store.exportRecovery(recovery(identity),output)
    override fun close()=client.close()
}
