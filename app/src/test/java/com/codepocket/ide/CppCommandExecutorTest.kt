package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CppCommandExecutorTest {
    private class Child(out: InputStream = ByteArrayInputStream(ByteArray(0)),
        error: InputStream = ByteArrayInputStream(ByteArray(0)), val status: () -> ExecutionExit?) : ExecutionProcess {
        override val stdout = out; override val stderr = error
        var closed = 0
        override fun poll() = status()
        override fun close() { closed++ }
    }
    @Test fun drainsBothPipesAfterExitAndRetainsNonzeroStatus() {
        val child = Child(ByteArrayInputStream("输出\n".toByteArray()), ByteArrayInputStream("source.cpp:2: error\n".toByteArray())) { ExecutionExit(1, null) }
        val owned = mutableListOf<ExecutionProcess?>()
        val result = CppCommandExecutor({ child }, { false }, { owned.add(it) }, 2000).run()
        assertEquals("输出\n", result.stdout); assertEquals("source.cpp:2: error\n", result.stderr)
        assertEquals(1, result.exit.code); assertFalse(result.exit.successful)
        assertEquals(listOf(child, null), owned); assertEquals(1, child.closed)
    }
    @Test fun cancelledBeforeLaunchCreatesNoChildAndLateCancellationClosesItsOwnChild() {
        assertThrows(CancellationException::class.java) { CppCommandExecutor({ error("must not launch") }, { true }, {}, 1000).run() }
        val cancelled = AtomicBoolean(); val child = Child { cancelled.set(true); null }
        assertThrows(CancellationException::class.java) { CppCommandExecutor({ child }, cancelled::get, {}, 1000).run() }
        assertEquals(1, child.closed)
    }
    @Test fun deadlineAndCombinedOutputBudgetAlwaysReleaseOwnership() {
        val timeout = Child { null }
        assertThrows(TimeoutException::class.java) { CppCommandExecutor({ timeout }, { false }, {}, 20).run() }
        assertEquals(1, timeout.closed)
        val oversized = Child(ByteArrayInputStream(ByteArray(80)), ByteArrayInputStream(ByteArray(80))) { ExecutionExit(0, null) }
        assertThrows(IOException::class.java) { CppCommandExecutor({ oversized }, { false }, {}, 1000, 100).run() }
        assertEquals(1, oversized.closed)
    }
    @Test fun nativeStatusDecoderKeepsRunningExitAndSignalSeparate() {
        assertNull(ExecutionExit.decode(-1)); assertTrue(ExecutionExit.decode(0)!!.successful)
        assertEquals(ExecutionExit(130, null), ExecutionExit.decode(130))
        assertEquals(ExecutionExit(null, 11), ExecutionExit.decode(267))
        assertThrows(IOException::class.java) { ExecutionExit.decode(1024) }
    }
    @Test fun memoryEventsAreExplicitAndCompilerCrashesAreNotSyntaxErrors(){
        val memory=ExecutionExit.decode(512)!!;assertTrue(memory.memoryExceeded);assertFalse(memory.successful)
        assertFalse(ExecutionExit.decode(265)!!.memoryExceeded)
        fun kind(exit:ExecutionExit)=CppBuildResult(null,"","",exit,null).failureKind
        assertEquals(RunKind.SERVICE,kind(ExecutionExit(null,6)));assertEquals(RunKind.SERVICE,kind(memory))
        assertEquals(RunKind.TIMEOUT,kind(ExecutionExit(null,24)));assertEquals(RunKind.COMPILE,kind(ExecutionExit(1,null)))
        assertEquals(RunKind.SERVICE,kind(ExecutionExit(127,null)))
    }
}
