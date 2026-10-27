package com.codepocket.ide

import android.app.Service
import android.content.Intent
import android.os.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns only a clangd child in :cpp_analysis. Deadlines never terminate an Android app process. */
class CppAnalysisService : Service() {
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
    private var rootPath: String? = null
    private val runtime by lazy { CppRuntime(this) }
    private var engineFactory: (File, () -> Boolean, (AnalysisProcess) -> Unit) -> CppLspEngine = { root,cancelled,own -> runtime.create(root,cancelled,own) }
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        val data = message.data
        when (message.what) {
            PythonAnalysisService.ANALYZE -> {
                val reply = message.replyTo; val payload = data.getString("payload")
                if (reply != null && payload != null && payload.length <= 128000) {
                    val request = Request(data.getLong("id"), payload, reply)
                    if (active == null) start(request) else {
                        active?.cancelled?.set(true)
                        queued?.let { respond(it, failure("superseded")) }; queued = request
                    }
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
    private fun failure(reason: String) = JSONObject().put("available", false).put("engine", CppSemanticProtocol.ENGINE).put("error", reason).toString()
    private fun respond(request: Request, result: String) {
        runCatching { request.reply.send(Message.obtain(null, PythonAnalysisService.RESULT).apply {
            data = Bundle().apply { putLong("id", request.id); putString("result", result) }
        }) }
    }
    private fun start(request: Request) {
        active = request
        val deadline = Runnable {
            if (active === request && !destroyed) {
                request.expired.set(true); request.cancelled.set(true)
                respond(request, failure("timeout"))
                // The native handle can close only this service's child, never editor/Python/PTY.
                ownedProcess?.close()
            }
        }
        handler.postDelayed(deadline, if (engine == null) 30000L else 8000L)
        worker.execute {
            val result = try {
                val payload = JSONObject(request.payload)
                val root = File(payload.getString("root")).absoluteFile
                val parent = File(cacheDir, "cpp-project-context").canonicalFile
                require(root.parentFile == parent && root.canonicalFile == root && root.name.matches(Regex("[0-9a-f]{64}")) && root.isDirectory)
                if (rootPath != root.path) { engine?.close(); engine = null; ownedProcess = null; rootPath = root.path }
                if (destroyed || request.cancelled.get()) throw java.util.concurrent.CancellationException()
                val current = engine ?: engineFactory(root, { destroyed || request.expired.get() }) { process ->
                    ownedProcess = process
                    if (destroyed || request.expired.get()) process.close()
                }.also { engine = it }
                current.analyze(payload.getString("code"), payload.getInt("caret"), payload.getString("path"), payload.getString("mode")) {
                    destroyed || request.cancelled.get()
                }
            } catch (_: java.util.concurrent.CancellationException) { failure("cancelled") }
            catch (_: Exception) { engine?.close(); engine = null; ownedProcess?.close(); ownedProcess = null; failure("backend") }
            catch (_: LinkageError) { engine?.close(); engine = null; ownedProcess?.close(); ownedProcess = null; failure("backend") }
            handler.post {
                if (!destroyed && active === request) {
                    handler.removeCallbacks(deadline)
                    if (!request.expired.get()) respond(request, result)
                    if (request.expired.get()) { engine?.close(); engine = null; ownedProcess = null }
                    active = null
                    val next = queued; queued = null; if (next != null) start(next)
                }
            }
        }
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onDestroy() {
        destroyed = true; active?.cancelled?.set(true); queued = null
        handler.removeCallbacksAndMessages(null); ownedProcess?.close(); worker.shutdownNow()
        super.onDestroy()
    }
}
