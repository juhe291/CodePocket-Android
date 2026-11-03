package com.codepocket.ide

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Own Android process. Parent can terminate native-blocked CPython without killing the editor. */
class PythonExecutionService : Service() {
    private val busy = AtomicBoolean(false)
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { request ->
        if (request.what == HELLO) {
            runCatching { request.replyTo.send(Message.obtain(null, READY).apply {
                data = Bundle().apply { putLong("id", request.data.getLong("id")); putInt("pid", Process.myPid()); putInt("pgid", runCatching { com.codepocket.ide.terminal.bridge.ProcessControl.getGroup(0) }.getOrDefault(0)) }
            }) }
            return@Handler true
        }
        if (request.what != RUN || !busy.compareAndSet(false, true)) return@Handler true
        val reply = request.replyTo
        val args = request.data
        val id = args.getLong("id")
        Thread({
            @Suppress("DEPRECATION")
            val input = args.getParcelable<android.os.ParcelFileDescriptor>("ttyIn")
            @Suppress("DEPRECATION")
            val output = args.getParcelable<android.os.ParcelFileDescriptor>("ttyOut")
            @Suppress("DEPRECATION")
            val error = args.getParcelable<android.os.ParcelFileDescriptor>("ttyErr")
            val result = try {
                val snapshot=args.getString("pythonJob")?.let{job->
                    val root=File(args.getString("root")!!)
                    require(root==File(filesDir.canonicalFile,"workspace")||root==File(job,"workspace")){"Python 运行目录不属于当前任务"}
                    PythonRunSnapshot.open(File(cacheDir.canonicalFile,"python-runs"),File(job),root,File(args.getString("path")!!))
                }
                val overlays=snapshot?.manifest?.path.orEmpty()
                if (args.getBoolean("terminal")) {
                    require(input != null && output != null && error != null) { "Missing real terminal descriptors" }
                    // Only this private :python process is changed. Native modules/subprocesses inherit real PTY stdio too.
                    runCatching { com.codepocket.ide.terminal.bridge.ProcessControl.isolate() }
                    android.system.Os.dup2(input.fileDescriptor,0)
                    android.system.Os.dup2(output.fileDescriptor,1)
                    android.system.Os.dup2(error.fileDescriptor,2)
                    val events = TerminalEvents(id, reply)
                    PythonEngine.runTerminal(applicationContext,args.getString("code")!!,args.getString("stdin")!!,
                        args.getString("path")!!,args.getString("root")!!,args.getBoolean("interactive",true),events,overlays,
                        args.getString("breakpoints").orEmpty())
                } else PythonEngine.run(applicationContext, args.getString("code")!!, args.getString("stdin")!!,
                    args.getString("path")!!, args.getString("root")!!,overlays)
            } catch (e: Exception) {
                RunResult("", "Python 执行失败：${e.message}", "—", 0, engine = "Python · 独立进程", kind = RunKind.RUNTIME)
            } finally {
                listOfNotNull(input,output,error).forEach { runCatching { it.close() } }
            }
            runCatching { reply.send(Message.obtain(null, RESULT).apply { data = result.toBundle(id) }) }
        }, "CodePocket-Python").start()
        true
    })
    class TerminalEvents(private val id: Long, private val reply: Messenger) {
        fun onState(state: String, sequence: Long) {
            reply.send(Message.obtain(null, STATE).apply { data = Bundle().apply {
                putLong("id", id);putString("state",state);putLong("sequence",sequence)
            } })
        }
    }
    override fun onCreate() { super.onCreate();runCatching { com.codepocket.ide.terminal.bridge.ProcessControl.isolate() } }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    companion object {
        const val RUN = 1
        const val READY = 2
        const val RESULT = 3
        const val HELLO = 4
        const val STATE = 5
    }
}

internal fun RunResult.toBundle(id: Long) = Bundle().apply {
    putLong("id", id); putString("stdout", stdout); putString("stderr", stderr)
    putString("exit", exitCode); putLong("elapsed", elapsedMs); putString("engine", engine); putString("kind", kind.name)
    errorLine?.let { putInt("line", it) }; putString("file", errorFile)
    putString("sourceHash",errorSourceHash)
}
internal fun runResultFromBundle(data: Bundle) = RunResult(
    data.getString("stdout").orEmpty(), data.getString("stderr").orEmpty(), data.getString("exit", "—"),
    data.getLong("elapsed"), if (data.containsKey("line")) data.getInt("line") else null,
    data.getString("engine").orEmpty(), RunKind.valueOf(data.getString("kind")!!), data.getString("file"),
    errorSourceHash=data.getString("sourceHash")
)
