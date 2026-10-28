package com.codepocket.ide

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal data class CppCommandResult(val exit: ExecutionExit, val stdout: String, val stderr: String,
    val fault: CppRuntimeFault? = null, val elapsedMs:Long = 0)

/** One owned native generation per command. Both pipes drain concurrently and share a budget. */
internal class CppCommandExecutor(private val launch: () -> ExecutionProcess,
    private val cancelled: () -> Boolean, private val own: (ExecutionProcess?) -> Unit,
    private val timeoutMs: Long, private val limit: Int = 220000) {
    fun run(): CppCommandResult {
        require(timeoutMs > 0 && limit > 0)
        if (cancelled()) throw CancellationException()
        val process = launch()
        val readers = Executors.newFixedThreadPool(2)
        val exceeded = AtomicBoolean(); val count = AtomicInteger()
        fun read(stream: InputStream): String {
            val output = ByteArrayOutputStream()
            val bytes = ByteArray(8192)
            while (true) {
                val n = stream.read(bytes); if (n < 0) break
                if (count.addAndGet(n) > limit) { exceeded.set(true); break }
                output.write(bytes, 0, n)
            }
            return output.toString(Charsets.UTF_8.name())
        }
        val started = System.nanoTime()
        try {
            own(process)
            val stdout = readers.submit<String> { read(process.stdout) }
            val stderr = readers.submit<String> { read(process.stderr) }
            var status: ExecutionExit
            while (true) {
                if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
                if (exceeded.get()) throw IOException("编译器输出超过 $limit 字节")
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= timeoutMs) throw TimeoutException("C++ 命令执行超时")
                val polled = process.poll()
                if (polled != null) { status = polled; break }
                Thread.sleep(10)
            }
            // An exited child can leave bytes in both pipes. Do not close it before EOF.
            // Descendants holding a pipe open are bounded; finally releases the owned group.
            val out = stdout.get(1500, TimeUnit.MILLISECONDS)
            val error = stderr.get(1500, TimeUnit.MILLISECONDS)
            if (exceeded.get()) throw IOException("编译器输出超过 $limit 字节")
            if (cancelled()) throw CancellationException()
            return CppCommandResult(status, out, error)
        } finally {
            process.close(); own(null); readers.shutdownNow()
        }
    }
}
