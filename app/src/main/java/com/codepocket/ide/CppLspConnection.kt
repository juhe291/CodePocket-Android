package com.codepocket.ide

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** UTF-8 Content-Length framing. Reader threads never call the UI and retain no diagnostic history. */
internal class CppLspConnection(private val process: AnalysisProcess, private val frameLimit: Int = 2_000_000) : Closeable {
    private val closed = AtomicBoolean()
    private val failure = AtomicReference<Throwable?>()
    private val replies = ArrayBlockingQueue<JSONObject>(8)
    private val outputLock = Any()
    private val parsedVersions = ConcurrentHashMap<URI, Int>()
    private var serial = 0L
    private val reader = Thread({
        try {
            val input = BufferedInputStream(process.input, 65536)
            while (!closed.get()) {
                val message = readFrame(input, frameLimit)
                if (message.optString("method") == "textDocument/publishDiagnostics") {
                    val params = message.getJSONObject("params")
                    val version = params.optInt("version", -1)
                    if (version >= 0) {
                        if (parsedVersions.size > 4) parsedVersions.clear()
                        parsedVersions[URI(params.getString("uri"))] = version
                    }
                }
                if (message.has("id")) {
                    if (message.has("method")) send(JSONObject().put("jsonrpc", "2.0").put("id", message.get("id"))
                        .put("error", JSONObject().put("code", -32601).put("message", "Unsupported client method")))
                    else if (!replies.offer(message)) throw IOException("Too many analysis replies")
                }
            }
        } catch (error: Exception) { if (!closed.get()) failure.compareAndSet(null, error) }
    }, "cpp-lsp-reader").apply { isDaemon = true; start() }
    private val errorReader = Thread({
        try { val bytes = ByteArray(4096); while (!closed.get() && process.errors.read(bytes) >= 0) { /* drain bounded chunks */ } }
        catch (_: Exception) { }
    }, "cpp-lsp-errors").apply { isDaemon = true; start() }

    private fun send(message: JSONObject) {
        val bytes = message.toString().toByteArray(Charsets.UTF_8); require(bytes.size <= 2_000_000)
        synchronized(outputLock) {
            check(!closed.get())
            process.output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
            process.output.write(bytes); process.output.flush()
        }
    }
    fun notify(method: String, params: Any?) = send(JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params ?: JSONObject.NULL))
    fun awaitParsed(uri: String, version: Int, cancelled: () -> Boolean) {
        val key = URI(uri); val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (parsedVersions[key] != version) {
            failure.get()?.let { throw IOException("Analysis parse channel ended", it) }
            if (closed.get()) throw IOException("Analysis channel closed")
            if (cancelled()) throw java.util.concurrent.CancellationException()
            if (System.nanoTime() >= deadline) throw TimeoutException("C++ parse deadline")
            Thread.sleep(10)
        }
    }
    fun request(method: String, params: Any?, timeoutMs: Long = 30000, cancelled: () -> Boolean = { false }): JSONObject {
        val id = ++serial
        send(JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params ?: JSONObject.NULL))
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs); var cancellationSent = false
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            failure.get()?.let { throw IOException("Analysis channel ended", it) }
            if (closed.get()) throw IOException("Analysis channel closed")
            if (System.nanoTime() >= deadline) throw TimeoutException("C++ analysis deadline")
            if (!cancellationSent && cancelled()) { notify("$/cancelRequest", JSONObject().put("id", id)); cancellationSent = true }
            val message = replies.poll(50, TimeUnit.MILLISECONDS) ?: continue
            if (message.optLong("id", Long.MIN_VALUE) == id) return message
        }
    }
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            process.close(); replies.clear()
            if (Thread.currentThread() !== reader) reader.join(250)
            if (Thread.currentThread() !== errorReader) errorReader.join(250)
        }
    }
    companion object {
        internal fun readFrame(input: java.io.InputStream, frameLimit: Int = 2_000_000): JSONObject {
            require(frameLimit in 1..8_000_000)
            var total = 0; var length: Int? = null
            while (true) {
                val line = ByteArrayOutputStream()
                while (true) {
                    val ch = input.read(); if (ch < 0) throw EOFException()
                    require(++total <= 8192)
                    if (ch == 10) break
                    require(ch in 13..126)
                    line.write(ch)
                }
                val value = line.toString("US-ASCII").removeSuffix("\r")
                if (value.isEmpty()) break
                val key = value.substringBefore(':').trim()
                require(value.contains(':'))
                if (key.equals("Content-Length", true)) { require(length == null); length = value.substringAfter(':').trim().toInt() }
            }
            val size = length ?: error("Missing LSP length"); require(size in 1..frameLimit)
            val body = ByteArray(size); var at = 0
            while (at < size) { val count = input.read(body, at, size - at); if (count < 0) throw EOFException(); if (count > 0) at += count }
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            return JSONObject(decoder.decode(java.nio.ByteBuffer.wrap(body)).toString())
        }
    }
}
