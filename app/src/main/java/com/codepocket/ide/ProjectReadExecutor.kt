package com.codepocket.ide

import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A stuck document provider cannot consume the semantic worker or grow threads/queues. */
internal class ProjectReadExecutor(private val timeoutMs: Long = 5000) : Closeable {
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1),
        { task -> Thread(task, "python-project-read").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    fun <T> read(block: () -> T): T {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        worker.purge()
        val task = worker.submit(Callable { block() })
        try { return task.get(timeoutMs, TimeUnit.MILLISECONDS) }
        catch (e: ExecutionException) { throw e.cause ?: e }
        finally { if (!task.isDone) { task.cancel(true); worker.purge() } }
    }
    override fun close() { worker.shutdownNow() }
    companion object {
        // Shared across Activity recreation: repeated permission stalls cannot leave one thread per screen.
        val shared = ProjectReadExecutor()
    }
}
