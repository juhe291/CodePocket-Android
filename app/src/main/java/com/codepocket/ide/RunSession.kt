package com.codepocket.ide

import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

/** One process-wide run survives Activity/theme/window recreation without retaining a View. */
object RunSession {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var running = false
        private set
    var result: RunResult? = null
        private set
    var listener: (() -> Unit)? = null

    fun start(code: String, input: String, cpp: Boolean, path: String, root: String): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (running) return false
        running = true
        result = null
        listener?.invoke()
        executor.execute {
            val started = System.nanoTime()
            val response = try {
                (if (cpp) WandboxEngine.run(code, input) else PythonEngine.run(code, input, path, root))
                    .copy(sourcePath = path)
            } catch (exception: Exception) {
                RunResult("", "${exception.javaClass.simpleName}: ${exception.message}", "1", (System.nanoTime() - started) / 1000000,
                    engine = if (cpp) "Wandbox" else "Python", sourcePath = path,
                    kind = when {
                        exception is SocketTimeoutException -> RunKind.TIMEOUT
                        cpp && exception is IOException -> RunKind.NETWORK
                        cpp -> RunKind.SERVICE
                        else -> RunKind.RUNTIME
                    })
            }
            main.post {
                result = response
                running = false
                listener?.invoke()
            }
        }
        return true
    }
}
