package com.codepocket.ide

import android.content.*
import android.os.*
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class SemanticLocation(val root:String,val path:String,val project:Boolean)
data class SemanticAnalysis(val available:Boolean,val completion:CompletionBatch?=null,
    val signature:SignatureResult?=null,val error:String?=null)
interface SemanticTransport:Closeable { fun analyze(payload:String):String? }

/** Transport can be supplied by host tests; production always binds the private Android service. */
class PythonSemanticClient(private val transport:SemanticTransport):Closeable {
    constructor(context:Context):this(AndroidSemanticTransport(context))
    fun analyze(request:CompletionRequest,location:SemanticLocation,mode:String):SemanticAnalysis {
        if(request.cpp || request.code.length>45000 || request.caret!=request.end ||
            request.caret !in 0..request.code.length)return SemanticAnalysis(false,error="unsupported")
        if(Thread.currentThread().isInterrupted)throw InterruptedException()
        return try {
            val payload=JSONObject().put("code",request.code).put("caret",request.caret)
                .put("root",location.root).put("path",location.path).put("project",location.project).put("mode",mode)
            SemanticProtocol.decode(request,transport.analyze(payload.toString()),mode,!location.project)
        } catch(e:InterruptedException){throw e}
        catch(_:Exception){SemanticAnalysis(false,error="transport")}
    }
    override fun close()=transport.close()
}

/** Only validated UTF-16 edits enter the existing Sora transaction path. Empty semantic results stay empty. */
object SemanticProtocol {
    const val ENGINE="Jedi 0.20.1"
    private fun boundary(code:String,n:Int)=n in 0..code.length &&
        !(n>0 && n<code.length && code[n-1].isHighSurrogate() && code[n].isLowSurrogate())
    fun decode(request:CompletionRequest,raw:String?,mode:String,currentFileOnly:Boolean=false):SemanticAnalysis = try {
        if(raw==null || raw.length>128000)SemanticAnalysis(false,error="unavailable")
        else {
            val json=JSONObject(raw)
            if(!json.optBoolean("available") || json.optString("engine")!=ENGINE)
                SemanticAnalysis(false,error=json.optString("error","unavailable"))
            else if(mode=="complete"){
                val values=json.getJSONArray("items");require(values.length()<=30)
                if(values.length()==0)SemanticAnalysis(true)
                else {
                    val start=json.getInt("start");val end=json.getInt("end")
                    require(boundary(request.code,start) && boundary(request.code,end) && start<=request.caret && end>=request.caret)
                    val prefix=request.code.substring(start,request.caret)
                    require(request.code.substring(start,end).all{it=='_' || it.isLetterOrDigit() || Character.getType(it)==Character.NON_SPACING_MARK.toInt()})
                    val items=(0 until values.length()).map{index->
                        val item=values.getJSONObject(index);val label=item.getString("label")
                        require(label.length in 1..256 && label.startsWith(prefix) &&
                            label.none{it.isWhitespace() || it=='\u0000' || it in ".()[]{};"})
                        val kind=when(item.getString("kind")){
                            "function"->CompletionKind.FUNCTION;"class"->CompletionKind.TYPE
                            "module"->CompletionKind.MODULE;"param"->CompletionKind.PARAMETER
                            "keyword"->CompletionKind.KEYWORD;else->CompletionKind.VARIABLE
                        }
                        CompletionItem(label,kind,item.optString("detail").take(600))
                    }
                    SemanticAnalysis(true,completion=CompletionBatch(request,start,end,items,ENGINE,currentFileOnly))
                }
            }else{
                require(mode=="signature")
                val values=json.getJSONArray("signatures");require(values.length()<=8)
                if(values.length()==0)SemanticAnalysis(true)
                else {
                    val first=values.getJSONObject(0);val name=first.getString("name");val opened=first.getInt("opened")
                    require(name.length in 1..256 && boundary(request.code,opened) && opened<request.caret && request.code[opened]=='(')
                    val forms=mutableListOf<SignatureForm>();val active=mutableListOf<Int?>()
                    for(index in 0 until values.length()){
                        val item=values.getJSONObject(index)
                        if(item.getString("name")!=name || item.getInt("opened")!=opened)continue
                        val params=item.getJSONArray("parameters");require(params.length()<=40)
                        val parts=(0 until params.length()).map{params.getString(it).also{part->require(part.length<=256)}}
                        forms.add(SignatureForm(parts,parts.indices.toList()))
                        active.add(if(item.isNull("active"))null else item.getInt("active").takeIf{it in parts.indices})
                    }
                    val reference=BuiltinSignature(name,false,forms,first.optString("detail").take(600),ENGINE,true,currentFileOnly)
                    SemanticAnalysis(true,signature=SignatureResult(request,reference,opened,active.firstOrNull()?:0,null,active))
                }
            }
        }
    }catch(_:Exception){SemanticAnalysis(false,error="invalid_response")}
}

/** One lazy binding shared by completion/signature workers; no work or waiting on the UI thread. */
internal class AndroidSemanticTransport(context:Context, private val service:Class<out android.app.Service> = PythonAnalysisService::class.java):SemanticTransport {
    private val app=context.applicationContext
    private val lock=Any()
    private var connection:Connection?=null
    @Volatile private var closed=false
    private class Call(val owner:Connection) { val done=CountDownLatch(1);@Volatile var result:String?=null }
    private val calls=ConcurrentHashMap<Long,Call>()
    private val reply=Messenger(Handler(Looper.getMainLooper()){message->
        val data=message.data
        calls[data.getLong("id")]?.let{it.result=data.getString("result");it.done.countDown()}
        true
    })
    private inner class Connection:ServiceConnection {
        val ready=CountDownLatch(1)
        @Volatile var remote:Messenger?=null
        var bound=false
        override fun onServiceConnected(name:ComponentName?,binder:IBinder?){
            synchronized(lock){if(!closed && connection===this && bound)remote=binder?.let{Messenger(it)}}
            ready.countDown()
        }
        override fun onServiceDisconnected(name:ComponentName?){remote=null;ready.countDown();calls.values.filter{it.owner===this}.forEach{it.done.countDown()}}
        override fun onBindingDied(name:ComponentName?){onServiceDisconnected(name)}
        override fun onNullBinding(name:ComponentName?){onServiceDisconnected(name)}
    }
    private fun connect():Connection?=synchronized(lock){
        if(closed)null else connection?:Connection().also{next->
            connection=next
            next.bound=runCatching{app.bindService(Intent(app,service),next,Context.BIND_AUTO_CREATE)}.getOrDefault(false)
            if(!next.bound)next.ready.countDown()
        }
    }
    override fun analyze(payload:String):String? {
        check(Looper.myLooper()!=Looper.getMainLooper()){ "Semantic IPC must run on a worker" }
        val current=connect()?:return null
        if(!current.ready.await(5,TimeUnit.SECONDS)) { reset(current);return null }
        val remote=current.remote?:run{reset(current);return null}
        val id=nextId.incrementAndGet();val call=Call(current);calls[id]=call
        try {
            remote.send(Message.obtain(null,PythonAnalysisService.ANALYZE).apply{
                replyTo=reply;data=Bundle().apply{putLong("id",id);putString("payload",payload)}
            })
            return if(call.done.await(35,TimeUnit.SECONDS))call.result else {reset(current);null}
        }finally{
            calls.remove(id)
            runCatching{remote.send(Message.obtain(null,PythonAnalysisService.CANCEL).apply{data=Bundle().apply{putLong("id",id)}})}
        }
    }
    private fun reset(current:Connection)=synchronized(lock){
        if(connection===current){connection=null;current.remote=null
            current.ready.countDown();calls.values.filter{it.owner===current}.forEach{it.done.countDown()}
            if(current.bound){runCatching{app.unbindService(current)};current.bound=false}
        }
    }
    override fun close(){
        closed=true;synchronized(lock){connection?.let{reset(it)}}
        calls.values.forEach{it.done.countDown()};calls.clear()
    }
    companion object{private val nextId=AtomicLong()}
}
