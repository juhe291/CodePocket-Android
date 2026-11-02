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

/** Owns :formatting and its own clangd child. No workspace paths or project configuration enter this service. */
class CodeFormattingService : Service() {
    private class Request(val id: Long, val payload: String, val reply: Messenger) {
        val cancelled = AtomicBoolean(); val expired = AtomicBoolean()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var active: Request? = null
    private var queued: Request? = null
    @Volatile private var destroyed = false
    @Volatile private var ownedProcess: AnalysisProcess? = null
    private var engine: CppLspEngine? = null
    private var style: String? = null
    // Host tests may supply a real pinned host process. Production always uses installed native libraries.
    private var engineFactory: (File, () -> Boolean, (AnalysisProcess) -> Unit) -> CppLspEngine = { root, cancelled, own ->
        CppRuntime(this).createFormatter(root, cancelled, own)
    }
    private fun failure(reason: String) = CodeFormatting.failure(reason)
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        val data = message.data
        when (message.what) {
            PythonAnalysisService.ANALYZE -> {
                val reply = message.replyTo; val payload = data.getString("payload")
                if (reply != null && payload != null) {
                    val request = Request(data.getLong("id"), payload, reply)
                    if (payload.length > CodeFormatting.MAX_JSON) respond(request, failure("input_limit"))
                    else if (active == null) start(request)
                    else { active?.cancelled?.set(true); queued?.let { respond(it, failure("superseded")) }; queued = request }
                }
            }
            PythonAnalysisService.CANCEL -> {
                val id = data.getLong("id")
                if (queued?.id == id) queued = null
                if (active?.id == id) active?.cancelled?.set(true)
            }
        }
        true
    })
    private fun respond(request: Request, result: String) {
        val bounded = result.takeIf { it.length <= CodeFormatting.MAX_JSON } ?: failure("output_limit")
        runCatching { request.reply.send(Message.obtain(null, PythonAnalysisService.RESULT).apply {
            data = Bundle().apply { putLong("id", request.id); putString("result", bounded) }
        }) }
    }
    private fun start(request: Request) {
        active = request
        val deadline = Runnable {
            if (!destroyed && active === request) {
                request.expired.set(true); request.cancelled.set(true); respond(request, failure("timeout")); ownedProcess?.close()
                // Python cannot be interrupted safely; this is only the manifest's dedicated process.
                stopSelf(); android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        handler.postDelayed(deadline, 30000L)
        worker.execute {
            val result = try { execute(request) {
                handler.post { if (!destroyed && active === request && !request.expired.get()) {
                    handler.removeCallbacks(deadline); handler.postDelayed(deadline, 8000L)
                } }
            } }
            catch (_: java.util.concurrent.CancellationException) { failure("cancelled") }
            catch (_: Exception) { engine?.close(); engine = null; ownedProcess?.close(); ownedProcess = null; failure("backend") }
            catch (_: LinkageError) { engine?.close(); engine = null; ownedProcess?.close(); ownedProcess = null; failure("backend") }
            handler.post {
                if (!destroyed && active === request) {
                    handler.removeCallbacks(deadline)
                    if (!request.expired.get()) respond(request, if (request.cancelled.get()) failure("cancelled") else result)
                    active = null; val next = queued; queued = null; if (next != null) start(next)
                }
            }
        }
    }
    private fun execute(request: Request, warm: () -> Unit): String {
        fun cancelled() = destroyed || request.cancelled.get()
        if (cancelled()) throw java.util.concurrent.CancellationException()
        val json = JSONObject(request.payload)
        val code = json.getString("code"); val start = json.getInt("start"); val end = json.getInt("end")
        require(json.get("python") is Boolean && json.get("spaces") is Boolean && json.get("indent") is Int &&
            json.get("code") is String && json.get("start") is Int && json.get("end") is Int)
        val python = json.getBoolean("python"); val options = FormatOptions(json.getInt("indent"), json.getBoolean("spaces"))
        CodeFormatting.payload(EditorSnapshot("format", 0, code, start, end), python, options)
        if (python) {
            if (Python.isStarted()) warm()
            if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext))
            if (cancelled()) throw java.util.concurrent.CancellationException()
            return Python.getInstance().getModule("code_formatting").callAttr("format_document", request.payload).toString()
        }
        val expectedStyle = options.style()
        if (engine != null && style == expectedStyle) warm()
        val parent = File(cacheDir, "code-formatting").canonicalFile.apply { require(isDirectory || mkdirs()) }
        val root = File(parent, "cpp-${options.indent}-${options.spaces}").apply { require(isDirectory || mkdir()) }
        require(root.canonicalFile == root && root.parentFile == parent)
        if (engine == null || style != expectedStyle) {
            engine?.close(); engine = null; ownedProcess = null
            File(root, ".clang-format").writeText(expectedStyle)
            File(root, "document.cpp").writeText("")
            engine = engineFactory(root, ::cancelled) { process -> ownedProcess = process; if (cancelled()) process.close() }
            style = expectedStyle
        }
        val edits = engine!!.format(code, "document.cpp", ::cancelled)
        return CodeFormatting.response(code, false, CodeFormatting.cppEdits(code, start, end, edits))
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onDestroy() {
        destroyed = true; active?.cancelled?.set(true); queued = null
        handler.removeCallbacksAndMessages(null); ownedProcess?.close(); worker.shutdownNow(); super.onDestroy()
        if (active != null) android.os.Process.killProcess(android.os.Process.myPid())
    }
}
