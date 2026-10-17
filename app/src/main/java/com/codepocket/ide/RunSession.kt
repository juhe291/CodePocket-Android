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
    private var deadline: Runnable? = null

    fun start(app: Context, code: String, input: String, cpp: Boolean, sourcePath: String, projectRoot: String, identity: String,
        pythonEntry: String = sourcePath, entryKey: String? = null): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val id = tickets.begin() ?: return false
        running = true; result = null; application = app.applicationContext as android.app.Application
        began = System.nanoTime(); path = sourcePath; root = projectRoot; workspaceId = identity; cppRun = cpp
        executionPath = pythonEntry; sourceKey = entryKey; sourceHash = DocumentTree.digest(code)
        listener?.invoke()
        deadline = Runnable { if (tickets.accepts(id)) finish(id, terminal(RunKind.TIMEOUT,
            if (cpp) "云端请求总时间超过 45 秒，已停止等待；服务端任务可能仍在运行。" else "Python 总时间超过 10 秒（含初始化），已停止执行会话。")) }
            .also { main.postDelayed(it, if (cpp) 45000 else 10000) }
        if (cpp) {
            val cancel = RunCancellation(); cancellation = cancel
            future = executor.submit {
                val response = try { WandboxEngine.run(code, input, cancel) }
                catch (e: Exception) { RunResult("", "${e.javaClass.simpleName}: ${e.message}", "—", elapsed(),
                    engine = "Wandbox", kind = ExecutionFailure.classify(true, e)) }
                main.post { finish(id, response) }
            }
        } else bindPython(id, code, input)
        return true
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
                    if (pid > 0 && pid != Process.myPid()) {
                        pythonPid = pid
                        // A cancelled pre-handshake bind never receives RUN, so it cannot leave native code running.
                        runCatching { remote!!.send(Message.obtain(null, PythonExecutionService.RUN).apply {
                            replyTo = reply
                            this.data = Bundle().apply {
                                putLong("id", id); putString("code", code); putString("stdin", input)
                                putString("path", executionPath); putString("root", root)
                            }
                        }) }
                            .onFailure { finish(id, terminal(RunKind.SERVICE, "无法启动 Python 执行：${it.message}")) }
                    } else finish(id, terminal(RunKind.SERVICE, "Python 执行进程身份异常，已停止"))
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
        finish(id, terminal(RunKind.CANCELLED, if (cppRun)
            "已取消本地等待；Wandbox 服务端任务可能继续执行。" else "已停止 Python 运行。"))
    }
    private fun elapsed() = (System.nanoTime() - began) / 1000000
    private fun terminal(kind: RunKind, message: String) = RunResult("", message, "—", elapsed(),
        engine = if (cppRun) "Wandbox" else "Python · 独立进程", kind = kind)
    private fun finish(id: Long, response: RunResult) {
        if (!tickets.finish(id)) return
        deadline?.let(main::removeCallbacks); deadline = null
        val cancel = cancellation; cancellation = null
        val task = future; future = null
        if (cancel != null) executor.execute { runCatching { cancel.cancel() }; task?.cancel(true) }
        if (pythonPid > 0 && pythonPid != Process.myPid()) Process.killProcess(pythonPid)
        pythonPid = 0
        connection?.let { runCatching { application?.unbindService(it) } }; connection = null
        application = null
        result = response.copy(sourcePath = path, projectRoot = root, workspaceId = workspaceId, sourceKey = sourceKey, sourceHash = sourceHash)
        running = false
        listener?.invoke()
    }
}
