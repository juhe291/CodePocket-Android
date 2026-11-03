package com.codepocket.ide

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal interface CppTerminalEndpoint {
    fun dimensions(): IntArray
    /** Empty: bounded wait. Null: real terminal EOF. */
    fun read(): ByteArray?
    fun write(bytes: ByteArray, offset: Int, length: Int): Int
}
internal object NativeTerminalIO {
    init { System.loadLibrary("codepocket_execute") }
    external fun open(fd: Int, input: Boolean): Int
    external fun close(fd: Int)
    external fun dimensions(fd: Int): IntArray?
    external fun read(fd: Int): ByteArray?
    external fun write(fd: Int, bytes: ByteArray, offset: Int, length: Int): Int
}
internal class NativeTerminalEndpoint(input: Int, output: Int) : CppTerminalEndpoint, java.io.Closeable {
    private val inputLock=Any();private val outputLock=Any();private var closed=false
    private val reader=NativeTerminalIO.open(input,true).also{if(it<0)throw IOException("无法打开非阻塞终端输入")}
    private val writer=NativeTerminalIO.open(output,false).also{if(it<0){NativeTerminalIO.close(reader);throw IOException("无法打开非阻塞终端输出")}}
    override fun dimensions() = synchronized(inputLock) { check(!closed);NativeTerminalIO.dimensions(reader) ?: throw IOException("无法读取真实终端尺寸") }
    override fun read() = synchronized(inputLock) { if(closed)null else NativeTerminalIO.read(reader) }
    override fun write(bytes: ByteArray, offset: Int, length: Int) = synchronized(outputLock) { if(closed)-1 else NativeTerminalIO.write(writer, bytes, offset, length) }
    override fun close() = synchronized(inputLock) { synchronized(outputLock) {
        if(!closed){closed=true;NativeTerminalIO.close(reader);NativeTerminalIO.close(writer)}
    } }
}

/** Private PTY input never enters the Shell's input queue. Completes both relays before return. */
internal class CppTerminalRunner(private val launch: (IntArray) -> TerminalExecutionProcess,
    private val endpoint: CppTerminalEndpoint, private val cancelled: () -> Boolean,
    private val own: (ExecutionProcess?) -> Unit, private val limit: Int = 220000, private val timeoutMs: Long = 300000,
    private val nanoTime:()->Long = System::nanoTime) {
    fun run(prefill: String, interactive:Boolean=true, running: () -> Unit): CppCommandResult {
        require(prefill.length <= 20000 && limit > 0 && timeoutMs > 0)
        if (cancelled()) throw CancellationException()
        val dimensions=endpoint.dimensions()
        val began=nanoTime()
        val process = launch(dimensions)
        val relay = Executors.newFixedThreadPool(3); val stopping = AtomicBoolean()
        val failure = AtomicReference<Throwable?>()
        fun stop() = stopping.get() || cancelled() || Thread.currentThread().isInterrupted
        fun send(bytes: ByteArray): Boolean {
            var offset = 0
            while (offset < bytes.size && !stop()) {
                val count = process.write(bytes, offset, minOf(8192, bytes.size-offset))
                if (count < 0) return false
                offset += count
            }
            return offset == bytes.size
        }
        try {
            own(process)
            val fault = relay.submit<CppRuntimeFault?> {
                val record=ByteArrayOutputStream(); val bytes=ByteArray(528)
                try {
                    while(!stop() && record.size()<528) {
                        val count=process.faults.read(bytes,0,528-record.size())
                        if(count<0)break
                        record.write(bytes,0,count)
                    }
                    if(record.size()==0) null else {
                        // Resume even a malformed report. Never leave a reporting child stopped.
                        val maps=process.faultMapsAndResume().orEmpty()
                        CppRuntimeFault.decode(record.toByteArray(),maps)
                    }
                } catch (_: Exception) {
                    if(!stop())runCatching{process.faultMapsAndResume()}
                    null // Fault reporting failure must not invent a source location.
                }
            }
            val input = relay.submit {
                try {
                    if(!interactive)return@submit // Fixed cases read their finite file; never drain the outer terminal.
                    if (!send(prefill.toByteArray(Charsets.UTF_8))) return@submit
                    while (!stop()) {
                        val bytes = endpoint.read()
                        if (bytes == null) { if (!stop()) { process.eof(); send(byteArrayOf(4)) }; break }
                        if (!send(bytes)) break
                    }
                } catch (error: Exception) { if (!stop()) failure.compareAndSet(null,error) }
            }
            val output = relay.submit<String> {
                val captured = ByteArrayOutputStream(); val bytes = ByteArray(8192)
                try {
                    while (!stop()) {
                        val count = process.stdout.read(bytes); if (count < 0) break
                        if (captured.size()+count > limit) throw IOException("终端输出超过 $limit 字节")
                        captured.write(bytes,0,count)
                        var offset = 0
                        while (offset < count && !stop()) {
                            val written = endpoint.write(bytes,offset,minOf(256,count-offset))
                            if (written < 0) throw IOException("真实终端输出连接已关闭")
                            offset += written
                        }
                    }
                    captured.toString(Charsets.UTF_8.name())
                } catch (error: Exception) { if (!stop()) failure.compareAndSet(null,error); throw error }
            }
            running(); var lastSize = endpoint.dimensions()
            while (true) {
                if (cancelled()) throw CancellationException()
                // A coordinator interrupt is the documented caller contract; keep it an
                // InterruptedException instead of a cancellation so ownership release stays explicit.
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val exit = process.poll()
                if (exit != null) {
                    // Freeze before draining output/fault pipes or cleaning up relays.
                    val elapsedMs=TimeUnit.NANOSECONDS.toMillis(nanoTime()-began).coerceAtLeast(0)
                    val text = output.get(1500,TimeUnit.MILLISECONDS)
                    stopping.set(true); input.get(1000,TimeUnit.MILLISECONDS)
                    return CppCommandResult(exit,text,"",fault.get(1000,TimeUnit.MILLISECONDS),elapsedMs)
                }
                failure.get()?.let { throw IOException("C++ 终端转发失败",it) }
                if (TimeUnit.NANOSECONDS.toMillis(nanoTime()-began) >= timeoutMs) throw TimeoutException(
                    if(timeoutMs==300000L)"C++ 终端会话超过5分钟" else "本地 C++ 执行超过 $timeoutMs 毫秒")
                val size = endpoint.dimensions()
                if (!size.contentEquals(lastSize)) { process.resize(size[0],size[1]); lastSize=size }
                Thread.sleep(25)
            }
        } finally {
            stopping.set(true); process.close(); relay.shutdownNow()
            // Native reads/polls are bounded. No next request or Shell return until they stop.
            var interrupted=Thread.interrupted()
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2)
            try {
                while(!relay.isTerminated && System.nanoTime()<deadline) {
                    try { relay.awaitTermination(100,TimeUnit.MILLISECONDS) }
                    catch (_:InterruptedException) { interrupted=true }
                }
                if(!relay.isTerminated)throw IOException("C++ 终端转发尚未释放")
                own(null)
            } finally { if(interrupted)Thread.currentThread().interrupt() }
        }
    }
}
