package com.codepocket.ide

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.Future
import com.codepocket.ide.terminal.shell.ShellRuntime
import com.codepocket.ide.terminal.bridge.TerminalBridgeBroker
import com.codepocket.ide.terminal.bridge.BridgeCommand
import org.json.JSONArray
import java.io.File

/** Process-wide owner; no Activity/View references. Python lives in the private :python process. */
object RunSession {
    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private val tickets = RunTickets()
    var running = false
        private set
    var result: RunResult? = null
        private set
    var listener: (() -> Unit)? = null
    private var connection: ServiceConnection? = null
    private var application: android.app.Application? = null
    private var pythonPid = 0
    private var cancellation: RunCancellation? = null
    private var future: Future<*>? = null
    private var began = 0L
    private var path = ""
    private var root = ""
    private var executionPath = ""
    private var workspaceId = ""
    private var sourceKey: String? = null
    private var sourceHash = ""
    private var cppRun = false
    private var localCppMode=false
    private var localCppRunning=false
    private var localProgramBegan=0L
    private var cppSnapshot:CppRunSnapshot?=null
    private var pythonSnapshot:PythonRunSnapshot?=null
    private var fixedInput=false
    private var completion:((RunResult)->Unit)?=null
    private var cppScope=CppBuildScope.CURRENT
    private var localCppClient:LocalCppRunClient?=null
    private var deadline: Runnable? = null
    private var terminalOwner: ShellRuntime? = null
    private var terminalControl: ShellRuntime.RunControl? = null
    private var terminalClock: TerminalRunClock? = null
    private var terminalBridge: TerminalBridgeBroker? = null
    private var terminalStreams: TerminalBridgeBroker.Streams? = null
    private var terminalLaunchFile: File? = null
    private var runBreakpoints: List<Int> = emptyList()
    private var startupStage = "准备本地终端"
    private fun startup(stage:String) { startupStage=stage;terminalOwner?.refreshForeground() }
    private var interactiveInput = false
    private var outputBytes = 0L
    private var lastKilledPid = 0
    private var bindingRetries = 0
    private var pythonGroup = 0
    val waitingForInput get() = running && terminalClock?.state == TerminalRunClock.State.WAITING
    private fun now() = android.os.SystemClock.uptimeMillis()

    internal fun start(app: Context, code: String, input: String, cpp: Boolean, sourcePath: String, projectRoot: String, identity: String,
        pythonEntry: String = sourcePath, entryKey: String? = null, terminal: ShellRuntime? = null,
        interactive: Boolean = false, cppJob:CppRunSnapshot?=null, scope:CppBuildScope=CppBuildScope.CURRENT,pythonJob:PythonRunSnapshot?=null,
        caseMode:Boolean=false,onComplete:((RunResult)->Unit)?=null,breakpoints:List<Int> = emptyList()): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        require(cppJob==null || cpp && terminal!=null)
        require(pythonJob==null||!cpp)
        runBreakpoints = if (cpp || terminal == null) emptyList()
            else breakpoints.filter { it > 0 }.distinct().sorted().take(500)
        val id = tickets.begin() ?: return false
        localCppMode=cppJob!=null;localCppRunning=false;localProgramBegan=0;cppSnapshot=cppJob;cppScope=scope;localCppClient=null
        pythonSnapshot=pythonJob
        fixedInput=caseMode;completion=null
        startupStage="准备本地终端";terminalOwner=terminal;interactiveInput=interactive;outputBytes=0;bindingRetries=0
        terminalClock=if(terminal!=null&&!cpp)TerminalRunClock(now())else null
        val displayFile=File(sourcePath).name
        val control=object:ShellRuntime.RunControl {
            override fun control(code:Int):Boolean {
                if(!tickets.accepts(id))return false
                if(code==3){stop();return true}
                return code==4&&!acceptsInput()
            }
            override fun acceptsInput() = if(localCppMode)interactive&&localCppRunning else !cpp&&interactive&&terminalClock?.state!=TerminalRunClock.State.STARTING
            override fun output(bytes:Int):Boolean {
                outputBytes+=bytes
                if(outputBytes>220000&&tickets.accepts(id)) {
                    finish(id,RunSession.terminal(RunKind.RUNTIME,"终端输出超过220000字节，已停止程序"));return false
                }
                return true
            }
            override fun label() = "$displayFile · "+if(localCppMode)"C++ · 本地 Clang21 · "+if(localCppRunning)"运行中 · Ctrl+C停止" else startupStage
                else if(cpp)"C++ · Wandbox云端 · 预填输入，完成后返回结果"
                else when(terminalClock?.state) {
                    TerminalRunClock.State.WAITING -> "Python · 等待终端输入 · Enter提交 / Ctrl+D结束输入"
                    TerminalRunClock.State.RUNNING -> if(interactive)"Python · 运行中 · Ctrl+C停止" else "Python · 使用预填输入 · Ctrl+C停止"
                    else -> "Python · $startupStage"
                }
            override fun shellPromptReturned() {
                if(tickets.accepts(id)&&localCppMode){finish(id,RunSession.terminal(RunKind.SERVICE,"本地 C++ 终端桥已退出"));return}
                if(tickets.accepts(id)&&terminalClock?.state==TerminalRunClock.State.STARTING)
                    finish(id,RunSession.terminal(RunKind.SERVICE,"Python前台桥在启动完成前已退出（$startupStage）。请查看终端中的启动错误；可以修改后重新运行。"))
            }
            override fun closed(){if(tickets.accepts(id))finish(id,RunSession.terminal(RunKind.CANCELLED,"所属终端已结束，程序已停止"))}
        }
        terminalControl=control
        if(terminal!=null&&!terminal.beginForeground(control)) {
            tickets.finish(id);terminalOwner=null;terminalControl=null;terminalClock=null;cppSnapshot=null;pythonSnapshot=null;return false
        }
        running = true; result = null; application = app.applicationContext as android.app.Application
        completion=onComplete
        began = System.nanoTime(); path = sourcePath; root = projectRoot; workspaceId = identity; cppRun = cpp
        executionPath = pythonEntry; sourceKey = entryKey; sourceHash = DocumentTree.digest(code)
        listener?.invoke()
        if(terminalClock!=null) scheduleTerminalDeadline(id)
        else if(localCppMode)scheduleLocalCppDeadline(id,100000,"本地 C++ 准备或构建超时，已停止")
        else deadline = Runnable { if (tickets.accepts(id)) finish(id, RunSession.terminal(RunKind.TIMEOUT,
            if (cpp) "云端请求总时间超过45秒，已停止等待；服务端任务可能仍在运行。" else "Python总时间超过10秒，已停止执行。")) }
            .also { main.postDelayed(it, if (cpp) 45000 else 10000) }
        if(localCppMode)connectTerminal(id,code,input)
        else if (cpp) {
            val cancel = RunCancellation(); cancellation = cancel
            future = executor.submit {
                val response = try { WandboxEngine.run(code, input, cancel) }
                catch (e: Exception) { RunResult("", "${e.javaClass.simpleName}: ${e.message}", "—", elapsed(),
                    engine = "Wandbox", kind = ExecutionFailure.classify(true, e)) }
                main.post { finish(id, response) }
            }
        } else if(terminal!=null) connectTerminal(id,code,input) else bindPython(id, code, input)
        return true
    }

    private fun scheduleLocalCppDeadline(id:Long,millis:Long,message:String) {
        deadline?.let(main::removeCallbacks)
        deadline=Runnable{if(tickets.accepts(id))finish(id,terminal(RunKind.TIMEOUT,message))}.also{main.postDelayed(it,millis)}
    }
    private fun bindLocalCpp(id:Long,input:String,streams:TerminalBridgeBroker.Streams) {
        val app=application?:return;val snapshot=cppSnapshot?:return
        terminalStreams=null;startup("连接本地 C++ 执行服务")
        val client=LocalCppRunClient(app,id,snapshot,cppScope,input,root,streams,{tickets.accepts(id)},{stage->
            if(tickets.accepts(id)) {
                if(stage=="running") {
                    if(localProgramBegan==0L)localProgramBegan=System.nanoTime()
                    localCppRunning=true;startup("运行中")
                    scheduleLocalCppDeadline(id,if(fixedInput)15000 else 305000,
                        if(fixedInput)"本地 C++ 用例运行超时，已停止" else "本地 C++ 终端会话超过5分钟，已停止")
                } else startup(stage)
                listener?.invoke()
            }
        },{response->finish(id,response)},fixedInput=fixedInput)
        localCppClient=client;client.start()
    }

    private fun scheduleTerminalDeadline(id:Long) {
        deadline?.let(main::removeCallbacks)
        val clock=terminalClock?:return
        val delay=clock.remaining(now())
        if(delay<=0) {
            finish(id,terminal(RunKind.TIMEOUT,if(clock.state==TerminalRunClock.State.WAITING)
                "等待终端输入超过5分钟，已停止程序" else if(clock.state==TerminalRunClock.State.STARTING) "Python启动超时：$startupStage。请复制终端记录以查看启动错误。"
                else "Python有效执行超过时间限制，已停止程序"));return
        }
        deadline=Runnable { if(tickets.accepts(id))scheduleTerminalDeadline(id) }.also { main.postDelayed(it,delay.coerceAtLeast(1)) }
    }

    private fun connectTerminal(id:Long,code:String,input:String) {
        val app=application?:return
        if(!File("/system/bin/app_process").canExecute()) {
            finish(id,terminal(RunKind.SERVICE,"此系统无法执行app_process终端桥，未切换为模拟运行"));return
        }
        startup("创建本地终端桥")
        terminalBridge=TerminalBridgeBroker.start(object:TerminalBridgeBroker.Listener {
            override fun listening(broker:TerminalBridgeBroker) {
                if(!tickets.accepts(id)){broker.close();return}
                runCatching {
                    val directory=File(app.filesDir,"terminal-launch").apply { if(!isDirectory&&!mkdirs())error("无法创建终端启动目录") }
                    val script=File(directory,"run-${java.util.UUID.randomUUID()}.sh")
                    script.writeText(BridgeCommand.STARTED+broker.command(app.applicationInfo.sourceDir)+"\n")
                    android.system.Os.chmod(script.path,384) // 0600: sourced data, never an app-writable executable.
                    terminalLaunchFile=script
                    startup("等待 Shell 提示符")
                    terminalOwner?.queueCommand(". "+BridgeCommand.quote(script.path), { error ->
                        if(tickets.accepts(id))finish(id,terminal(RunKind.SERVICE,error))
                    }, { if(tickets.accepts(id))startup("等待前台桥连接（app_process）") })
                }.onFailure { finish(id,terminal(RunKind.SERVICE,"终端启动失败：${it.message}")) }
            }
            override fun connected(broker:TerminalBridgeBroker,streams:TerminalBridgeBroker.Streams) {
                if(!tickets.accepts(id)){streams.close();broker.close();return}
                terminalStreams=streams
                if(localCppMode)bindLocalCpp(id,input,streams)
                else {startup("连接 Python 独立服务");bindPython(id,code,input)}
            }
            override fun disconnected(broker:TerminalBridgeBroker,error:String) {
                if(tickets.accepts(id))finish(id,terminal(RunKind.CANCELLED,"终端前台桥已退出，${if(localCppMode)"本地 C++" else "Python"}已停止：$error"))
            }
        })
    }

    private fun bindPython(id: Long, code: String, input: String) {
        var remote: Messenger? = null
        lateinit var reply: Messenger
        reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            val data = message.data
            if (!tickets.accepts(id) || data.getLong("id") != id) return@Handler true
            when (message.what) {
                PythonExecutionService.READY -> {
                    val pid = data.getInt("pid")
                    if(pid==lastKilledPid&&bindingRetries++<20) {
                        connection?.let { runCatching { application?.unbindService(it) } };connection=null
                        main.postDelayed({if(tickets.accepts(id))bindPython(id,code,input)},50)
                        return@Handler true
                    }
                    if (pid > 0 && pid != Process.myPid() && pid!=lastKilledPid) {
                        pythonPid = pid;pythonGroup=data.getInt("pgid")
                        if(terminalOwner!=null)startup("初始化 Python 运行时")
                        // A cancelled pre-handshake bind never receives RUN, so it cannot leave native code running.
                        runCatching { remote!!.send(Message.obtain(null, PythonExecutionService.RUN).apply {
                            replyTo = reply
                            this.data = Bundle().apply {
                                putLong("id", id); putString("code", code); putString("stdin", input)
                                putString("path", executionPath); putString("root", root)
                                putString("pythonJob",pythonSnapshot?.directory?.path)
                                putString("breakpoints",JSONArray(runBreakpoints).toString())
                                terminalStreams?.let { streams ->
                                    putBoolean("terminal",true);putBoolean("interactive",interactiveInput)
                                    putParcelable("ttyIn",streams.input);putParcelable("ttyOut",streams.output);putParcelable("ttyErr",streams.error)
                                }
                            }
                        }) }
                            .onSuccess { terminalStreams?.close();terminalStreams=null }
                            .onFailure { finish(id, terminal(RunKind.SERVICE, "无法启动 Python 执行：${it.message}")) }
                    } else finish(id, terminal(RunKind.SERVICE, "Python 执行进程身份异常，已停止"))
                }
                PythonExecutionService.STATE -> {
                    val clock=terminalClock?:return@Handler true
                    if(clock.remaining(now())<=0){scheduleTerminalDeadline(id);return@Handler true}
                    val next=when(data.getString("state")){"running"->TerminalRunClock.State.RUNNING;"waiting"->TerminalRunClock.State.WAITING;else->null}
                    if(next!=null&&clock.update(next,data.getLong("sequence"),now())) {
                        scheduleTerminalDeadline(id);terminalOwner?.refreshForeground();listener?.invoke()
                    }
                }
                PythonExecutionService.RESULT -> runCatching { runResultFromBundle(data) }
                    .onSuccess { finish(id, it) }
                    .onFailure { finish(id, terminal(RunKind.SERVICE, "Python 结果解析失败：${it.message}")) }
            }
            true
        })
        val binding = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (!tickets.accepts(id)) return
                runCatching {
                    remote = Messenger(binder)
                    remote!!.send(Message.obtain(null, PythonExecutionService.HELLO).apply {
                        replyTo = reply
                        this.data = Bundle().apply {
                            putLong("id", id); putString("code", code); putString("stdin", input)
                            putString("path", executionPath); putString("root", root)
                        }
                    })
                }.onFailure { finish(id, terminal(RunKind.SERVICE, "无法启动 Python 执行：${it.message}")) }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (tickets.accepts(id)) finish(id, terminal(RunKind.RUNTIME, "Python 执行进程意外结束；可以修改后重新运行"))
            }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
            override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
        }
        connection = binding
        try {
            if (application?.bindService(Intent(application, PythonExecutionService::class.java), binding, Context.BIND_AUTO_CREATE) != true)
                finish(id, terminal(RunKind.SERVICE, "无法绑定 Python 执行进程"))
        } catch (e: Exception) { finish(id, terminal(RunKind.SERVICE, "Python 启动失败：${e.message}")) }
    }

    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        val id = tickets.active ?: return
        finish(id, terminal(RunKind.CANCELLED, if(localCppMode)"已停止本地 C++ 运行。" else if (cppRun)
            "已取消本地等待；Wandbox 服务端任务可能继续执行。" else "已停止 Python 运行。"))
    }
    private fun elapsed() = (System.nanoTime() - if(localCppMode&&localProgramBegan!=0L)localProgramBegan else began) / 1000000
    private fun terminal(kind: RunKind, message: String) = RunResult("", message, "—", elapsed(),
        engine = if(localCppMode)CppExecutionService.ENGINE else if (cppRun) "Wandbox" else "Python · 独立进程", kind = kind)
    private fun finish(id: Long, response: RunResult) {
        if(!tickets.accepts(id))return
        localCppClient?.takeIf{!it.released}?.let{
            localCppRunning=false;startup("正在停止本地 C++");it.cancel(response);return
        }
        if (!tickets.finish(id)) return
        deadline?.let(main::removeCallbacks); deadline = null
        val cancel = cancellation; cancellation = null
        val task = future; future = null
        if (cancel != null) executor.execute { runCatching { cancel.cancel() }; task?.cancel(true) }
        if (pythonPid > 0 && pythonPid != Process.myPid()) {
            if(pythonGroup==pythonPid)runCatching {
                if(com.codepocket.ide.terminal.bridge.ProcessControl.getGroup(pythonPid)==pythonPid)android.system.Os.kill(-pythonPid,android.system.OsConstants.SIGKILL)
            }
            lastKilledPid=pythonPid;Process.killProcess(pythonPid)
        }
        pythonPid = 0;pythonGroup=0
        runBreakpoints = emptyList()
        terminalStreams?.close();terminalStreams=null
        val owner=terminalOwner;val control=terminalControl
        terminalOwner=null;terminalControl=null;terminalClock=null
        if(owner!=null&&control!=null) {
            owner.clearForeground(control)
            if(localCppMode) {
                owner.appendExternal("\r\n[本地 C++ · ${response.kind.label}]\r\n"+
                    (if(response.kind==RunKind.COMPILE||fixedInput)response.stdout else "")+response.stderr+
                    "\r\n[退出码 ${response.exitCode} · ${if(localProgramBegan!=0L)"执行 " else ""}${response.elapsedMs}ms]\r\n")
            } else if(cppRun) {
                owner.appendExternal("\r\n[Wandbox · 云端预填输入 · ${response.kind.label}]\r\n"+response.stdout+
                    (if(response.stderr.isNotEmpty())"\r\n"+response.stderr else "")+"\r\n[退出码 ${response.exitCode} · ${response.elapsedMs}ms]\r\n")
                owner.redrawPrompt()
            } else if(response.kind in setOf(RunKind.CANCELLED,RunKind.TIMEOUT,RunKind.SERVICE) ||
                response.kind==RunKind.RUNTIME && response.engine=="Python · 独立进程")
                owner.appendExternal("\r\n[CodePocket] ${response.stderr}\r\n")
        }
        val status=when(response.kind){RunKind.CANCELLED->130;RunKind.TIMEOUT->124;else->response.exitCode.toIntOrNull()?.and(255)?:1}
        terminalBridge?.complete(status);terminalBridge=null
        terminalLaunchFile?.let { runCatching { it.delete() } };terminalLaunchFile=null
        connection?.let { runCatching { application?.unbindService(it) } }; connection = null
        application = null
        val diagnosticPath=response.errorFile?.let { file ->
            runCatching { File(file).canonicalPath.removePrefix(File(root).canonicalPath+File.separator).replace(File.separatorChar,'/') }.getOrNull()
        }
        val diagnosticHash=cppSnapshot?.texts?.get(diagnosticPath)?.let(DocumentTree::digest)
        result = response.copy(sourcePath = path, projectRoot = root, workspaceId = workspaceId, sourceKey = sourceKey, sourceHash = sourceHash,
            errorSourceHash=diagnosticHash?:response.errorSourceHash)
        localCppClient?.close();localCppClient=null
        cppSnapshot?.let{runCatching{it.close()}};cppSnapshot=null;localCppRunning=false
        pythonSnapshot?.let{runCatching{it.close()}};pythonSnapshot=null
        val done=completion;completion=null
        val completed=result!!
        running = false
        listener?.invoke()
        if(done!=null)main.post{done(completed)}
    }
}
