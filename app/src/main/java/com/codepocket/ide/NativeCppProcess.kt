package com.codepocket.ide

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal interface AnalysisProcess : Closeable {
    val input: InputStream
    val output: OutputStream
    val errors: InputStream
}

internal object NativeAnalysisBridge {
    private val loaded by lazy { System.loadLibrary("codepocket_analysis"); true }
    fun launch(arguments: List<String>, directory: String, environment: List<String>): Long {
        check(loaded)
        return spawn(arguments.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray(), directory.toByteArray(Charsets.UTF_8), environment.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray())
    }
    private external fun spawn(arguments: Array<ByteArray>, directory: ByteArray, environment: Array<ByteArray>): Long
    external fun read(handle: Long, channel: Int, limit: Int): ByteArray?
    external fun write(handle: Long, data: ByteArray, offset: Int, count: Int): Int
    external fun stop(handle: Long)
}

/** Native-owned PID/FD generations support hard cancellation on API24 without reflection or shell commands. */
internal class NativeCppProcess(arguments: List<String>, directory: String, environment: List<String>) : AnalysisProcess {
    private val handle = NativeAnalysisBridge.launch(arguments, directory, environment)
    private val closed = AtomicBoolean()
    private inner class Reader(private val channel: Int) : InputStream() {
        private var buffer = ByteArray(0); private var position = 0
        override fun read(): Int { val one = ByteArray(1); return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255 }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            if (length == 0) return 0
            while (position == buffer.size) {
                if (closed.get()) return -1
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                buffer = NativeAnalysisBridge.read(handle, channel, minOf(65536, length)) ?: return -1
                position = 0
                if (buffer.isNotEmpty()) break
            }
            val count = minOf(length, buffer.size - position); buffer.copyInto(bytes, offset, position, position + count); position += count
            return count
        }
        override fun close() = this@NativeCppProcess.close()
    }
    override val input: InputStream = Reader(1)
    override val errors: InputStream = Reader(2)
    override val output: OutputStream = object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            var at = offset
            while (at < offset + length) {
                check(!closed.get())
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                at += NativeAnalysisBridge.write(handle, bytes, at, minOf(65536, offset + length - at))
            }
        }
        override fun close() = this@NativeCppProcess.close()
    }
    override fun close() { if (closed.compareAndSet(false, true)) NativeAnalysisBridge.stop(handle) }
}
