package com.codepocket.ide

import android.content.Context
import android.os.Looper
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutionException

/** Background capture/IPC/publication; ordinary saves keep their original WorkspaceIo queue. */
internal class GitWorkbench(context:Context,private val client:GitClient):Closeable {
    constructor(context:Context):this(context,GitClient(AndroidSemanticTransport(context,GitOperationService::class.java)))
    private val app=context.applicationContext
    private fun recovery(workspace:Workspace)=File(app.filesDir.canonicalFile,"git-recovery/"+DocumentTree.digest(workspace.identity))
    fun perform(workspace:Workspace,action:String,fields:JSONObject=JSONObject(),expected:String?=null,
        saved:Map<String,String> = emptyMap(),cancelled:()->Boolean={false}):GitResponse {
        check(Looper.myLooper()!=Looper.getMainLooper()) { "Git操作必须在后台执行" }
        var snapshot:GitWorkspaceSnapshot?=null
        fun current(){if(cancelled() || Thread.currentThread().isInterrupted)throw InterruptedException()}
        fun <T> publish(work:()->T):T {
            val task=WorkspaceIo.executor.submit<T>{current();work()};var interrupted=false
            try {
                // Once publication starts, retain its source/backup files until
                // it finishes or rolls back, even if the UI request is cancelled.
                while(true)try{return task.get()}catch(_:InterruptedException){interrupted=true}
            } catch(error:ExecutionException){throw error.cause?:error}
            finally {if(interrupted)Thread.currentThread().interrupt()}
        }
        return try {
            require(action in GitProtocol.operations);current()
            val store=workspace.gitStore();publish{store.recover(recovery(workspace))};current()
            val captured=store.capture(GitJobs.next(app));snapshot=captured
            val request=JSONObject(fields.toString()).put("action",action).put("modeAvailable",captured.modeAvailable)
            if(saved.isNotEmpty()) {
                require(action=="stage")
                val paths=request.getJSONArray("paths");require((0 until paths.length()).map{paths.getString(it)}.toSet()==saved.keys)
                require(saved.all{(path,hash)->captured.before.files[path]?.sha256==hash}) { "保存后文件又发生变化" }
                val latest=client.request(captured.root,JSONObject().put("action","status").put("modeAvailable",captured.modeAvailable))
                if(!latest.ok)return latest
                request.put("expected",latest.state!!.snapshot)
            } else if(expected!=null)request.put("expected",expected)
            current();val response=client.request(captured.root,request);current()
            if(response.ok) {
                if(action in GitProtocol.writes)publish{store.publish(captured,recovery(workspace))}
                else {require(store.metadataChanges(captured).isEmpty()) { "Git读取修改了元数据" };store.assertFresh(captured)}
            }
            current()
            response.copy(sourceFiles=captured.before.files.filterKeys{it!=".git" && !it.startsWith(".git/")})
        } catch(error:InterruptedException){throw error}
        catch(error:GitRecoveryRequired){GitResponse(error="recovery_required",detail=error.message)}
        catch(error:IllegalArgumentException){GitResponse(error="workspace_changed",detail=error.message)}
        catch(error:Exception){GitResponse(error="storage",detail=error.message)}
        finally {snapshot?.let{runCatching{GitJobs.discard(app,it.root)}}}
    }
    fun recoveryDescription(workspace:Workspace)=workspace.gitStore().recoveryDescription(recovery(workspace))
    fun exportRecovery(workspace:Workspace,output:java.io.OutputStream)=workspace.gitStore().exportRecovery(recovery(workspace),output)
    override fun close()=client.close()
}
