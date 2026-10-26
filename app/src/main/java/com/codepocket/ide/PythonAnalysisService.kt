package com.codepocket.ide

import android.app.Service
import android.content.Intent
import android.os.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Dedicated :analysis process; its deadline and shutdown can never kill :python or the PTY. */
class PythonAnalysisService:Service() {
    private data class Request(val id:Long,val payload:String,val reply:Messenger)
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var active:Request?=null
    private var queued:Request?=null
    private var destroyed=false
    private var initialized=false
    private val messenger=Messenger(Handler(Looper.getMainLooper()){message->
        val data=message.data
        when(message.what){
            ANALYZE->{
                val reply=message.replyTo
                val payload=data.getString("payload")
                if(reply!=null && payload!=null && payload.length<=128000){
                    val next=Request(data.getLong("id"),payload,reply)
                    if(active==null)start(next)else{queued?.let{respond(it,failure("superseded"))};queued=next}
                }
            }
            CANCEL->{if(queued?.id==data.getLong("id"))queued=null}
        };true
    })
    private fun failure(reason:String)=JSONObject().put("available",false).put("engine",SemanticProtocol.ENGINE).put("error",reason).toString()
    private fun respond(request:Request,result:String){runCatching{request.reply.send(Message.obtain(null,RESULT).apply{
        data=Bundle().apply{putLong("id",request.id);putString("result",result)}
    })}}
    private fun start(request:Request){
        active=request
        val deadline=Runnable{
            if(active===request && !destroyed){respond(request,failure("timeout"));stopSelf();android.os.Process.killProcess(android.os.Process.myPid())}
        }
        handler.postDelayed(deadline,if(initialized)8000L else 30000L)
        worker.execute{
            val result=try{
                if(!Python.isStarted())Python.start(AndroidPlatform(applicationContext))
                val cache=File(cacheDir,"python-semantics").apply{mkdirs()}
                Python.getInstance().getModule("semantics").callAttr("analyze",request.payload,cache.path).toString()
            }catch(_:Exception){failure("backend")}
            catch(_:LinkageError){failure("backend")}
            handler.post{
                if(!destroyed && active===request){initialized=true;handler.removeCallbacks(deadline);respond(request,result);active=null
                    val next=queued;queued=null;if(next!=null)start(next)}
            }
        }
    }
    override fun onBind(intent:Intent):IBinder=messenger.binder
    override fun onDestroy(){
        destroyed=true;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy()
        if(active!=null)android.os.Process.killProcess(android.os.Process.myPid())
    }
    companion object { const val ANALYZE=1;const val RESULT=2;const val CANCEL=3 }
}
