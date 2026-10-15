package com.codepocket.ide

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class ServiceException(val status: Int, message: String) : IOException(message)

class RunCancellation {
    private val lock = Any()
    @Volatile var cancelled = false
        private set
    private var connection: HttpURLConnection? = null
    fun check() { if (cancelled || Thread.currentThread().isInterrupted) throw CancellationException("已取消请求") }
    fun attach(value: HttpURLConnection) = synchronized(lock) { check(); connection = value }
    fun release(value: HttpURLConnection) = synchronized(lock) { if (connection === value) connection = null }
    fun cancel() {
        val active = synchronized(lock) { cancelled = true; connection.also { connection = null } }
        active?.disconnect()
    }
}

/** Late completions from a stopped run must never replace the next run. */
class RunTickets {
    private var serial = 0L
    var active: Long? = null
        private set
    fun begin(): Long? { if (active != null) return null; return (++serial).also { active = it } }
    fun finish(id: Long): Boolean { if (active != id) return false; active = null; return true }
    fun accepts(id: Long) = active == id
}

object ExecutionFailure {
    fun classify(cpp: Boolean, error: Exception) = when {
        error is CancellationException -> RunKind.CANCELLED
        error is SocketTimeoutException -> RunKind.TIMEOUT
        error is ServiceException -> RunKind.SERVICE
        cpp && error is IOException -> RunKind.NETWORK
        cpp -> RunKind.SERVICE
        else -> RunKind.RUNTIME
    }
}
