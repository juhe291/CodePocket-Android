package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class RunControlTest {
    @Test fun duplicateRunsAndLateResultsAfterStopAreRejected() {
        val tickets = RunTickets(); val first = tickets.begin()!!
        assertNull(tickets.begin()); assertTrue(tickets.finish(first))
        val next = tickets.begin()!!
        assertFalse(tickets.finish(first)); assertTrue(tickets.accepts(next)); assertTrue(tickets.finish(next))
    }
    @Test fun httpFailuresAreServiceErrorsAndOfflineIsNetworkError() {
        for (status in listOf(400, 429, 503)) assertEquals(RunKind.SERVICE, ExecutionFailure.classify(true, ServiceException(status, "HTTP $status")))
        assertEquals(RunKind.NETWORK, ExecutionFailure.classify(true, IOException("offline")))
        assertEquals(RunKind.TIMEOUT, ExecutionFailure.classify(true, SocketTimeoutException()))
        assertEquals(RunKind.CANCELLED, ExecutionFailure.classify(true, CancellationException()))
        assertEquals(RunKind.RUNTIME, ExecutionFailure.classify(false, IOException("python")))
    }
    @Test fun cancelledRequestCannotAttachOrStartAnotherNetworkOperation() {
        val cancellation = RunCancellation(); cancellation.cancel()
        assertThrows(CancellationException::class.java) { cancellation.check() }
    }
    @Test fun cancellingDisconnectsOnlyItsOwnActiveConnection() {
        val connection = object : java.net.HttpURLConnection(java.net.URL("https://example.invalid")) {
            var closed = false
            override fun connect() = Unit
            override fun disconnect() { closed = true }
            override fun usingProxy() = false
        }
        val cancellation = RunCancellation()
        cancellation.attach(connection); cancellation.cancel()
        assertTrue(connection.closed)
        assertThrows(CancellationException::class.java) { cancellation.attach(connection) }
    }
}
