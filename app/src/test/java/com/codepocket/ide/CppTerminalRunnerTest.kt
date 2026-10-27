package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CppTerminalRunnerTest {
    private class Child(output: String, private val status: () -> ExecutionExit?) : TerminalExecutionProcess {
        var faultBytes=ByteArrayInputStream(ByteArray(0));var resumes=AtomicInteger()
        override val faults get()=faultBytes
        override fun faultMapsAndResume():String {resumes.incrementAndGet();return "owned maps"}
        override val stdout = ByteArrayInputStream(output.toByteArray())
        override val stderr = ByteArrayInputStream(ByteArray(0))
        val input = ByteArrayOutputStream(); val closed = AtomicInteger(); val eofCount = AtomicInteger()
        val resized = AtomicInteger(); var writes = 0
        @Synchronized override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
            if (closed.get() != 0) return -1
            writes++
            // Force backpressure and partial writes rather than accepting a complete prefill.
            if (writes % 5 == 0) { Thread.sleep(1); return 0 }
            val count = minOf(127,length); input.write(bytes,offset,count); return count
        }
        @Synchronized fun bytes() = input.toByteArray()
        override fun poll() = status()
        override fun eof() { eofCount.incrementAndGet() }
        override fun resize(rows: Int, columns: Int) { assertEquals(40,rows); assertEquals(120,columns); resized.incrementAndGet() }
        override fun close() { closed.incrementAndGet() }
    }
    private class Endpoint(private val chunks: MutableList<ByteArray?> = mutableListOf()) : CppTerminalEndpoint {
        val visible = ByteArrayOutputStream(); var dimensionsCalls = 0
        var resized = false; var disconnected = false; var onRead: (() -> Unit)? = null
        override fun dimensions(): IntArray { dimensionsCalls++; return if(resized && dimensionsCalls>2)intArrayOf(40,120)else intArrayOf(24,80) }
        override fun read(): ByteArray? { onRead?.invoke(); if(chunks.isNotEmpty())return chunks.removeAt(0);Thread.sleep(5);return ByteArray(0) }
        @Synchronized override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
            if(disconnected)return -1
            val count=minOf(3,length);visible.write(bytes,offset,count);return count
        }
    }
    @Test fun longUtf8PrefillAndInteractiveInputOnlyEnterThePrivateProgramWithBackpressure() {
        val prefill="长输入".repeat(6000)+"\n";val typed="interactive\n"
        val expected=(prefill+typed).toByteArray();val endpoint=Endpoint(mutableListOf(typed.toByteArray()))
        lateinit var child:Child
        child=Child("actual-output\n"){if(child.bytes().size==expected.size)ExecutionExit(0,null)else null}
        val owned=mutableListOf<ExecutionProcess?>()
        val result=CppTerminalRunner({size->assertArrayEquals(intArrayOf(24,80),size);child},endpoint,{false},{owned.add(it)},timeoutMs=3000).run(prefill){}
        assertArrayEquals(expected,child.bytes());assertEquals("actual-output\n",result.stdout)
        assertEquals("actual-output\n",endpoint.visible.toString("UTF-8"));assertTrue(result.exit.successful)
        assertTrue(child.writes>expected.size/127);assertEquals(1,child.closed.get());assertEquals(listOf(child,null),owned)
    }
    @Test fun earlyExitCannotSendUnusedInputToTheShellAndStopsAllInputBeforeReturning() {
        val endpoint=Endpoint();val child=Child("done\n"){ExecutionExit(0,null)}
        CppTerminalRunner({child},endpoint,{false},{},timeoutMs=1000).run("unused-shell-looking-text\n"){}
        assertEquals("done\n",endpoint.visible.toString("UTF-8"));assertEquals(1,child.closed.get())
        val bytes=child.bytes();Thread.sleep(30);assertArrayEquals(bytes,child.bytes())
    }
    @Test fun realTerminalEofAndSizeChangesAreForwardedWithoutClosingTheOuterTerminal() {
        val endpoint=Endpoint(mutableListOf(null)).apply{resized=true};lateinit var child:Child
        child=Child(""){if(child.eofCount.get()==1 && child.resized.get()>0 && child.bytes().contentEquals(byteArrayOf(4)))ExecutionExit(0,null)else null}
        val result=CppTerminalRunner({child},endpoint,{false},{},timeoutMs=1000).run(""){}
        assertTrue(result.exit.successful);assertEquals(1,child.eofCount.get());assertTrue(child.resized.get()>0)
    }
    @Test fun cancellationOutputLimitAndDisconnectReleaseOnlyTheOwnedChild() {
        val cancelled=AtomicBoolean();val endpoint=Endpoint().apply{onRead={cancelled.set(true)}}
        val child=Child(""){null}
        assertThrows(CancellationException::class.java){CppTerminalRunner({child},endpoint,cancelled::get,{},timeoutMs=1000).run(""){} }
        assertEquals(1,child.closed.get())
        for(disconnect in listOf(false,true)) {
            val output=Child("x".repeat(100)){null};val end=Endpoint().apply{disconnected=disconnect}
            assertThrows(IOException::class.java){CppTerminalRunner({output},end,{false},{},limit=if(disconnect)200 else 20,timeoutMs=1000).run(""){} }
            assertEquals(1,output.closed.get())
        }
    }
    @Test fun interruptedCoordinatorStillJoinsItsRelaysBeforeReleasingOwnership() {
        val entered=java.util.concurrent.CountDownLatch(1);val endpoint=Endpoint().apply{onRead={entered.countDown()}}
        val child=Child(""){null};val released=AtomicBoolean();val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val thread=Thread {
            try { CppTerminalRunner({child},endpoint,{false},{if(it==null)released.set(true)},timeoutMs=3000).run(""){} }
            catch(error:Throwable){failure.set(error)}
        }
        thread.start()
        try {
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));thread.interrupt();thread.join(2500)
            assertFalse(thread.isAlive);assertTrue(released.get());assertEquals(1,child.closed.get())
            assertTrue(failure.get() is InterruptedException)
        } finally {if(thread.isAlive){thread.interrupt();thread.join(3000)}}
    }
    @Test fun faultRecordIsDrainedAndResumedBeforeExitAndMalformedReportsAlsoReleaseTheChild() {
        for(valid in listOf(true,false)) {
            lateinit var child:Child
            child=Child("before crash\n"){if(child.resumes.get()>0)ExecutionExit(null,11)else null}
            val bytes=java.nio.ByteBuffer.allocate(528).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                putInt(if(valid)0x43504631 else 0);putInt(11);putInt(1);putInt(0);putLong(0x1234)
            }.array()
            child.faultBytes=ByteArrayInputStream(bytes)
            val result=CppTerminalRunner({child},Endpoint(),{false},{},timeoutMs=1000).run(""){}
            assertEquals(1,child.resumes.get());assertEquals(1,child.closed.get());assertEquals(11,result.exit.signal)
            if(valid)assertEquals(CppRuntimeFault(11,listOf(0x1234),"owned maps"),result.fault)else assertNull(result.fault)
        }
    }
}
