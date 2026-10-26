package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ProjectReadExecutorTest {
    @Test fun uninterruptibleProviderTimesOutWithoutGrowingThreadsAndRecoversWhenReleased() {
        val reader = ProjectReadExecutor(100)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        try {
            assertThrows(TimeoutException::class.java) { reader.read {
                entered.countDown()
                var done = false
                while (!done) try { release.await(); done = true } catch (_: InterruptedException) { /* broken provider ignores cancellation */ }
            } }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            repeat(4) { assertThrows(TimeoutException::class.java) { reader.read { error("cancelled queued reads must not run") } } }
            release.countDown()
            assertEquals("recovered", reader.read { "recovered" })
        } finally { release.countDown(); reader.close() }
    }
    @Test fun interruptionAndProviderFailuresReachTheCallerAndLeaveTheReaderUsable() {
        ProjectReadExecutor(1000).use { reader ->
            assertThrows(SecurityException::class.java) { reader.read { throw SecurityException("revoked") } }
            Thread.currentThread().interrupt()
            try { assertThrows(InterruptedException::class.java) { reader.read { "cancelled" } } } finally { Thread.interrupted() }
            assertEquals(7, reader.read { 7 })
        }
    }
}
