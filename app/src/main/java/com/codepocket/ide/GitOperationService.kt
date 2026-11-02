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

/** Private :git process. It operates only on owned snapshots, never publishes provider metadata. */
class GitOperationService:Service() {
    private class Request(val id:Long,val payload:String,val reply:Messenger) {
        val cancelled=AtomicBoolean();val expired=AtomicBoolean()
    }
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var active:Request?=null;private var queued:Request?=null
    @Volatile private var destroyed=false
    private var backend:(File,String)->String={root,request->
        if(!Python.isStarted())Python.start(AndroidPlatform(applicationContext))
        Python.getInstance().getModule("git_workspace").callAttr("run",root.absolutePath,request).toString()
    }
    override fun onCreate(){super.onCreate();worker.execute{GitJobs.clearOldLeases(this)}}
    private val messenger=Messenger(Handler(Looper.getMainLooper()){message->
        val data=message.data
        when(message.what) {
            PythonAnalysisService.ANALYZE->{
                val payload=data.getString("payload");val reply=message.replyTo
                if(payload!=null && reply!=null) {
                    val request=Request(data.getLong("id"),payload,reply)
                    if(payload.length>GitProtocol.MAX_REQUEST)respond(request,GitProtocol.failure("input_limit"))
                    else if(active==null)start(request)
                    else {active?.cancelled?.set(true);queued?.let{respond(it,GitProtocol.failure("superseded"))};queued=request}
                }
            }
            PythonAnalysisService.CANCEL->{val id=data.getLong("id");if(queued?.id==id)queued=null;if(active?.id==id)active?.cancelled?.set(true)}
        };true
    })
    private fun respond(request:Request,result:String) {
        val bounded=result.takeIf{it.length<=GitProtocol.MAX_RESPONSE}?:GitProtocol.failure("output_limit")
        runCatching{request.reply.send(Message.obtain(null,PythonAnalysisService.RESULT).apply{
            data=Bundle().apply{putLong("id",request.id);putString("result",bounded)}
        })}
    }
    private fun start(request:Request) {
        active=request
        val timeout=Runnable{if(!destroyed && active===request){
            request.expired.set(true);request.cancelled.set(true);respond(request,GitProtocol.failure("timeout"))
            stopSelf();android.os.Process.killProcess(android.os.Process.myPid())
        }}
        handler.postDelayed(timeout,30000L)
        worker.execute {
            var root:File?=null;var leased=false
            val result=try {
                if(destroyed || request.cancelled.get())throw InterruptedException()
                val packet=JSONObject(request.payload);require(packet.get("root") is String)
                root=GitJobs.root(this,packet.getString("root"))
                val body=packet.getJSONObject("request");require(body.getString("action") in GitProtocol.operations)
                require(GitJobs.lease(root).createNewFile())
                leased=true
                backend(root,body.toString())
            } catch(_:InterruptedException){GitProtocol.failure("cancelled")}
            catch(_:IllegalArgumentException){GitProtocol.failure("snapshot_path")}
            catch(_:Exception){GitProtocol.failure("backend")}
            catch(_:LinkageError){GitProtocol.failure("backend")}
            finally {if(leased)root?.let{snapshot->
                runCatching{GitJobs.lease(snapshot).delete()}
                if(destroyed || request.cancelled.get())runCatching{GitJobs.discard(this,snapshot)}
            }}
            handler.post {
                if(!destroyed && active===request) {
                    handler.removeCallbacks(timeout)
                    if(!request.expired.get())respond(request,if(request.cancelled.get())GitProtocol.failure("cancelled") else result)
                    active=null;val next=queued;queued=null;if(next!=null)start(next)
                }
            }
        }
    }
    override fun onBind(intent:Intent):IBinder=messenger.binder
    override fun onDestroy() {
        destroyed=true;active?.cancelled?.set(true);queued=null;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy()
        if(active!=null)android.os.Process.killProcess(android.os.Process.myPid())
    }
}
