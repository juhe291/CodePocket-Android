package com.codepocket.ide

import android.content.*
import android.os.*
import com.codepocket.ide.terminal.bridge.TerminalBridgeBroker

/** Main-process binding. Completion is delivered only after the service releases the run. */
internal class LocalCppRunClient(private val app: Context, private val id: Long,
    private val snapshot: CppRunSnapshot, private val scope: CppBuildScope, private val input: String,
    private val root: String, private val streams: TerminalBridgeBroker.Streams,
    private val current: () -> Boolean, private val stage: (String) -> Unit,
    private val done: (RunResult) -> Unit,private val fixedInput:Boolean=false) : java.io.Closeable {
    var released=false;private set
    private var bound=false;private var sent=false;private var remote:Messenger?=null
    private var pending:RunResult?=null
    private val reply=Messenger(Handler(Looper.getMainLooper()){message->
        if(!released && current() && message.data.getLong("id")==id)when(message.what){
            CppExecutionService.STATE->if(pending==null)stage(message.data.getString("stage").orEmpty())
            CppExecutionService.RESULT->runCatching{runResultFromBundle(message.data)}.onSuccess(::complete)
                .onFailure{complete(failure("本地 C++ 结果解析失败：${it.message}"))}
        }
        true
    })
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
            if(released || !current())return
            remote=Messenger(binder)
            runCatching { remote!!.send(Message.obtain(null,CppExecutionService.RUN).apply {
                replyTo=reply;data=Bundle().apply {
                    putLong("id",id);putString("job",snapshot.directory.path);putString("entry",snapshot.entry)
                    putString("projectRoot",root);putString("stdin",input);putString("scope",scope.name)
                    putBoolean("fixedInput",fixedInput)
                    putParcelable("ttyIn",streams.input);putParcelable("ttyOut",streams.output);putParcelable("ttyErr",streams.error)
                }
            }) }.onSuccess{sent=true;streams.close()}.onFailure{complete(failure("无法启动本地 C++：${it.message}"))}
        }
        override fun onServiceDisconnected(name:ComponentName){if(!released)complete(failure("本地 C++ 执行服务意外退出；可以修改后重新运行"))}
        override fun onBindingDied(name:ComponentName)=onServiceDisconnected(name)
        override fun onNullBinding(name:ComponentName)=onServiceDisconnected(name)
    }
    private fun failure(message:String)=RunResult("",message,"—",0,engine=CppExecutionService.ENGINE,kind=RunKind.SERVICE)
    fun start() {
        try {
            bound=app.bindService(Intent(app,CppExecutionService::class.java),connection,Context.BIND_AUTO_CREATE)
            if(!bound)complete(failure("无法绑定本地 C++ 执行服务"))
        } catch(error:Exception){complete(failure("本地 C++ 启动失败：${error.message}"))}
    }
    fun cancel(reason:RunResult) {
        if(released || pending!=null)return
        pending=reason
        if(!sent){complete(reason);return}
        runCatching { remote!!.send(Message.obtain(null,CppExecutionService.CANCEL).apply{data=Bundle().apply{putLong("id",id)}}) }
            .onFailure{complete(failure("本地 C++ 取消通道已关闭：${it.message}"))}
    }
    private fun complete(result:RunResult) {
        if(released)return
        released=true;streams.close();close()
        done(pending?:result)
    }
    override fun close(){if(bound){bound=false;runCatching{app.unbindService(connection)}};remote=null}
}
