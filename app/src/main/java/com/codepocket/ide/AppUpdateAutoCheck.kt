package com.codepocket.ide

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in, silent startup update check. It reuses the same bounded read-only GitHub source and the
 * shared result wording as the manual entry, never uses project Git credentials and never installs
 * anything. A missing/old/unreachable release stays quiet; only a genuinely newer release is shown.
 *
 * At most one attempt runs per process so Activity recreation cannot repeat the request.
 */
internal class AppUpdateAutoCheck(
    private val activity: Activity,
    private val repository: AppUpdateRepository? = AppUpdateRepository.parse(BuildConfig.UPDATE_REPOSITORY),
    private val factory: (AppUpdateRepository) -> AppUpdateSource = { GitHubAppUpdateSource(it) }
) : Closeable {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var source: AppUpdateSource? = null
    @Volatile private var closed = false
    private var task: Future<*>? = null
    /** Called on the main thread only for an actually newer release; failure stays silent by design. */
    fun checkOnce(onAvailable: (AppUpdateResult, InstalledAppUpdateVersion) -> Unit) {
        if (closed || !attempted.compareAndSet(false, true)) return
        val repo = repository ?: return
        val installed = InstalledAppUpdateVersion.read(activity)
        val current = AppUpdateVersion.parse(installed.name) ?: return
        task = worker.submit {
            val result = try {
                val next = factory(repo); source = next
                if (closed) { next.cancel(); return@submit }
                next.check(current)
            } catch (_: CancellationException) {
                return@submit
            } catch (_: Exception) {
                AppUpdateResult(AppUpdateKind.FAILED, failure = AppUpdateFailure.NETWORK)
            }
            if (!closed) handler.post { if (!closed) onAvailable(result, installed) }
        }
    }
    override fun close() {
        if (closed) return
        closed = true; task?.cancel(true); runCatching { source?.cancel() }; source = null
        worker.shutdownNow(); handler.removeCallbacksAndMessages(null)
    }
    companion object {
        private val attempted = AtomicBoolean(false)
        internal fun resetAttemptedForTests() { attempted.set(false) }
        /** Robolectric seam for the startup path only; production keeps the real bounded HTTPS source. */
        internal var sourceFactoryForTests: ((AppUpdateRepository) -> AppUpdateSource)? = null
    }
}
