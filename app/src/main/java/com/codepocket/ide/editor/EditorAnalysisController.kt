package com.codepocket.ide.editor

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** UI-owned requests; the worker receives immutable strings, never an Android View/Editable. */
class EditorAnalysisController(
    private val port: EditorPort,
    private val dispatch: (() -> Unit) -> Unit,
    private val worker: ExecutorService = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(), { task -> Thread(task, "CodePocket-highlight").apply { isDaemon = true } }),
    private val analyze: (EditorFrame) -> EditorAnalysis = EditorAnalyzer()::analyze
) : AutoCloseable {
    private var generation = 0L
    private var task: Future<*>? = null
    private var closed = false

    fun request() {
        if (closed) return
        cancel()
        val frame = port.snapshot()?.takeUnless { it.composing } ?: return
        val request = generation
        task = worker.submit {
            val result = try { analyze(frame) }
            catch (_: InterruptedException) { return@submit }
            catch (_: RuntimeException) {
                EditorAnalysis(frame, emptyList(), null, HighlightFallback.ANALYSIS_FAILED)
            }
            if (Thread.currentThread().isInterrupted) return@submit
            dispatch {
                // Cancellation alone cannot retract a result which is already queued on the UI thread.
                if (!closed && request == generation) port.applyDecorations(result)
            }
        }
    }

    fun cancel() {
        generation++
        task?.cancel(true); task = null
        (worker as? ThreadPoolExecutor)?.purge()
    }

    override fun close() {
        closed = true; cancel(); worker.shutdownNow()
    }
}
