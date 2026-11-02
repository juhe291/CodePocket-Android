package com.codepocket.ide

import android.app.Service
import android.content.Intent
import android.os.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Exclusive explicit network operation in :git_remote. New requests never supersede a push. */
class GitRemoteService:Service() {
    private class Request(val id:Long,var payload:String?,val reply:Messenger) {
        val cancelled=AtomicBoolean();val expired=AtomicBoolean()
        @Volatile var push=false;@Volatile var head:String?=null
    }
    private val handler=Handler(Looper.getMainLooper());private val worker=Executors.newSingleThreadExecutor()
    private var active:Request?=null;@Volatile private var destroyed=false
    private var backend:(File,String,AtomicBoolean,File?)->String={root,request,cancelled,plan->
        if(!Python.isStarted())Python.start(AndroidPlatform(applicationContext))
        Python.getInstance().getModule("git_remote").callAttr("run_android",root.path,request,cancelled,plan?.path).toString()
    }
    override fun onCreate(){super.onCreate();worker.execute{GitRemoteJobs.clearOldLeases(this)}}
    private val messenger=Messenger(Handler(Looper.getMainLooper()){message->
        val data=message.data
        when(message.what) {
            PythonAnalysisService.ANALYZE->{
                val payload=data.getString("payload");val reply=message.replyTo
                if(payload!=null && reply!=null) {
                    val request=Request(data.getLong("id"),payload,reply)
                    if(payload.length>GitProtocol.MAX_REQUEST)respond(request,GitRemoteProtocol.failure("input_limit"))
                    else if(active!=null)respond(request,GitRemoteProtocol.failure("busy")) else start(request)
                }
            }
            PythonAnalysisService.CANCEL->if(active?.id==data.getLong("id"))active?.cancelled?.set(true)
        };true
    })
    private fun respond(request:Request,result:String) {
        val bounded=result.takeIf{it.length<=GitProtocol.MAX_RESPONSE}?:GitRemoteProtocol.failure(if(request.push)"push_unknown" else "output_limit",request.head)
        runCatching{request.reply.send(Message.obtain(null,PythonAnalysisService.RESULT).apply {
            data=Bundle().apply{putLong("id",request.id);putString("result",bounded)}
        })}
    }
    private fun start(request:Request) {
        active=request
        val deadline=Runnable{if(!destroyed && active===request){
            request.expired.set(true);request.cancelled.set(true)
            respond(request,GitRemoteProtocol.failure(if(request.push)"push_unknown" else "timeout",request.head))
            stopSelf();android.os.Process.killProcess(android.os.Process.myPid())
        }}
        handler.postDelayed(deadline,120000)
        worker.execute {
            var root:File?=null;var leased=false;var intent:String?=null;var claimed=false;var enteredBackend=false
            var ledger:GitPushLedger?=null
            val result=try {
                val payload=request.payload?:error("missing request");request.payload=null
                val packet=JSONObject(payload);require(packet.keys().asSequence().all{it in setOf("root","request","intent")})
                root=GitRemoteJobs.root(this,packet.getString("root"));val body=packet.getJSONObject("request")
                val action=body.getString("action");require(action in GitRemoteProtocol.operations)
                require(body.keys().asSequence().all{it in setOf("action","expected","url","branch","expected_head","expected_remote","username","token","modeAvailable")})
                request.push=action=="push";request.head=if(request.push)GitRemoteProtocol.oid(body.getString("expected_head")) else null
                if(request.push){intent=packet.getString("intent");ledger=GitPushLedger(this);ledger.claim(intent!!,root,body);claimed=true}
                else require(!packet.has("intent"))
                if(destroyed || request.cancelled.get())throw InterruptedException()
                require(GitRemoteJobs.lease(root).createNewFile());leased=true
                val plan=if(action in GitRemoteProtocol.publications)File(root.parentFile,"result.plan") else null
                enteredBackend=true
                val raw=GitRemoteProtocol.checked(backend(root,body.toString(),request.cancelled,plan),body)
                val reply=GitRemoteProtocol.decode(raw)
                var response=raw
                if(claimed) {
                    val knownRejections=setOf("remote_changed","remote_rejected","needs_fetch","non_fast_forward","authentication",
                        "remote_not_found","credentials","url_invalid","url_scope","remote_not_configured","precondition","stale","input","branch",
                        "branch_not_found","configuration","not_repository","repository_layout","operation_in_progress","engine","dirty","detached",
                        "unsupported_tree","unsupported_index","limit","path","locked")
                    val outcome=if(reply.ok)reply.outcome!! else if(reply.error in knownRejections)"rejected" else "unknown"
                    ledger!!.finish(intent!!,outcome,reply.error)
                    if(outcome=="unknown")response=GitRemoteProtocol.failure("push_unknown",request.head)
                }
                response
            }catch(_:InterruptedException){
                if(claimed)runCatching{ledger!!.finish(intent!!,if(enteredBackend)"unknown" else "not_sent",if(enteredBackend)"push_unknown" else "cancelled")}
                GitRemoteProtocol.failure(if(claimed && enteredBackend)"push_unknown" else "cancelled",request.head)
            }catch(_:Exception){
                if(claimed)runCatching{ledger!!.finish(intent!!,"unknown","push_unknown")}
                GitRemoteProtocol.failure(if(claimed)"push_unknown" else if(request.push)"push_intent" else "snapshot_path",request.head)
            }catch(_:LinkageError){
                if(claimed)runCatching{ledger!!.finish(intent!!,"unknown","push_unknown")}
                GitRemoteProtocol.failure(if(request.push)"push_unknown" else "backend",request.head)
            }finally {
                request.payload=null
                if(leased)root?.let { snapshot->runCatching{GitRemoteJobs.lease(snapshot).delete()}
                    if(destroyed || request.cancelled.get())runCatching{GitRemoteJobs.discard(this,snapshot)} }
            }
            handler.post {
                if(!destroyed && active===request) {
                    handler.removeCallbacks(deadline)
                    // Preserve a completed push and its persisted receipt even after CANCEL.
                    if(!request.expired.get())respond(request,if(request.cancelled.get() && !request.push)GitRemoteProtocol.failure("cancelled") else result)
                    active=null
                }
            }
        }
    }
    override fun onBind(intent:Intent):IBinder=messenger.binder
    override fun onDestroy() {
        destroyed=true;active?.cancelled?.set(true);handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy()
        if(active!=null)android.os.Process.killProcess(android.os.Process.myPid())
    }
}
