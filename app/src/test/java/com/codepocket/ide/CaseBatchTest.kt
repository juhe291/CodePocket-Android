package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CaseBatchTest {
    private val cases = List(3) { ProgramCase(name = "用例 $it", input = "$it", expected = "$it\n") }
    private val hash = DocumentTree.digest("source with helper drafts")
    private fun result(text: String = "0\n", kind: RunKind = RunKind.SUCCESS) =
        RunResult(text, "", if (kind == RunKind.SUCCESS) "0" else "1", 8, engine = "controller protocol fixture", kind = kind)
    private inner class Fixture {
        val prepare = mutableListOf<(Result<PreparedCaseRun>) -> Unit>()
        val launches = mutableListOf<ProgramCase>()
        val callbacks = mutableListOf<(RunResult) -> Unit>()
        val saves = mutableListOf<Pair<CaseRecord, (Result<Unit>) -> Unit>>()
        var cancelled = 0; var closed = 0; var accepted = true
        val batch = CaseBatch(cases, { prepare.add(it) }, { r, done -> saves.add(r to done) }, { cancelled++ }, {})
        fun prepared(context: String = hash) {
            prepare.last()(Result.success(object : PreparedCaseRun {
                override val contextHash = context
                override fun close() { closed++ }
                override fun launch(case: ProgramCase, done: (RunResult) -> Unit): Boolean {
                    launches.add(case); callbacks.add(done); return accepted
                }
            }))
        }
        fun saved() { saves.last().second(Result.success(Unit)) }
    }
    @Test fun eachCaseWaitsForPersistenceBeforeTheNextPreparationAndInputIsUnchanged() {
        val f = Fixture(); f.batch.start(); f.prepared()
        assertEquals(listOf(cases[0]), f.launches)
        f.callbacks[0](result()); assertEquals(CaseBatch.Stage.SAVING, f.batch.stage); assertEquals(1, f.prepare.size)
        f.saved(); assertEquals(2, f.prepare.size); f.prepared()
        assertEquals("1", f.launches[1].input); f.callbacks[1](result("wrong")); f.saved(); f.prepared()
        f.callbacks[2](result("2\n")); f.saved()
        assertFalse(f.batch.running); assertEquals(3, f.batch.records.size)
        assertEquals(listOf(CaseVerdict.PASSED, CaseVerdict.WRONG_ANSWER, CaseVerdict.PASSED), f.batch.records.map { it.verdict })
        assertNull(f.batch.unsavedRecord)
    }
    @Test fun staleDuplicateExecutionAndPersistenceCallbacksCannotCompleteANewerCase() {
        val f = Fixture(); f.batch.start(); f.prepared(); f.callbacks[0](result()); val firstSave = f.saves[0].second
        f.callbacks[0](result("late")); assertEquals(1, f.saves.size)
        f.saved(); f.prepared(); firstSave(Result.success(Unit)); f.callbacks[0](result("later"))
        assertEquals(CaseBatch.Stage.RUNNING, f.batch.stage); assertEquals(2, f.launches.size); assertEquals(1, f.batch.records.size)
    }
    @Test fun stopWhilePreparingReleasesItsSnapshotAndDoesNotInventAnExecutedRecord() {
        val f = Fixture(); f.batch.start(); f.batch.stop(); assertTrue(f.batch.running); f.prepared()
        assertFalse(f.batch.running); assertEquals(1, f.closed); assertTrue(f.launches.isEmpty()); assertTrue(f.batch.records.isEmpty())
    }
    @Test fun stopWhileRunningWaitsForTheRealCancellationAndSavesItExactlyOnce() {
        val f = Fixture(); f.batch.start(); f.prepared(); f.batch.stop()
        assertEquals(1, f.cancelled); assertTrue(f.batch.running)
        f.callbacks[0](result(kind = RunKind.CANCELLED)); assertEquals(CaseBatch.Stage.SAVING, f.batch.stage)
        f.saved(); assertFalse(f.batch.running); assertEquals(1, f.prepare.size); assertEquals(CaseVerdict.CANCELLED, f.batch.records.single().verdict)
    }
    @Test fun sourceEditingStopsAfterTheFrozenCurrentCaseWithoutKillingIt() {
        val f = Fixture(); f.batch.start(); f.prepared(); f.batch.stop("源码已变化", cancelCurrent = false)
        assertEquals(0, f.cancelled); f.callbacks[0](result()); f.saved()
        assertFalse(f.batch.running); assertEquals("源码已变化", f.batch.message); assertEquals(1, f.prepare.size)
    }
    @Test fun stopDuringPersistenceDoesNotKillAnotherRunOrLoseTheCompletedResult() {
        val f = Fixture(); f.batch.start(); f.prepared(); f.callbacks[0](result()); f.batch.stop(); f.saved()
        assertFalse(f.batch.running); assertEquals(0, f.cancelled); assertEquals(1, f.prepare.size); assertEquals(1, f.batch.records.size)
    }
    @Test fun changedSourceContextBetweenCasesClosesTheNewJobAndLeavesRemainingCasesUnexecuted() {
        val f = Fixture(); f.batch.start(); f.prepared(); f.callbacks[0](result()); f.saved(); f.prepared(DocumentTree.digest("changed helper"))
        assertFalse(f.batch.running); assertEquals(1, f.closed); assertEquals(1, f.launches.size); assertEquals(1, f.batch.records.size)
        assertTrue(f.batch.message.contains("源码"))
    }
    @Test fun saveFailureRetainsTheRealResultAndStopsWithoutPretendingItWasSaved() {
        val f = Fixture(); f.batch.start(); f.prepared(); f.callbacks[0](result())
        f.saves[0].second(Result.failure(IOException("disk full")))
        assertFalse(f.batch.running); assertSame(f.batch.records.single(), f.batch.unsavedRecord); assertEquals(1, f.prepare.size)
        assertTrue(f.batch.message.contains("保存失败"))
    }
    @Test fun refusedLaunchAndPreparationFailureReleaseOwnershipWithoutCreatingRunResults() {
        val f = Fixture(); f.accepted = false; f.batch.start(); f.prepared()
        assertFalse(f.batch.running); assertEquals(1, f.closed); assertTrue(f.batch.records.isEmpty())
        val g = Fixture(); g.batch.start(); g.prepare[0](Result.failure(IOException("provider unavailable")))
        assertFalse(g.batch.running); assertTrue(g.launches.isEmpty()); assertTrue(g.batch.records.isEmpty()); assertTrue(g.batch.message.contains("provider unavailable"))
    }
    @Test fun compilationServiceAndNetworkFailuresHaltButRuntimeAndTimeoutContinue() {
        for (kind in listOf(RunKind.COMPILE, RunKind.SERVICE, RunKind.NETWORK, RunKind.RUNTIME, RunKind.TIMEOUT)) {
            val f = Fixture(); f.batch.start(); f.prepared(); f.callbacks[0](result(kind = kind)); f.saved()
            val fatal = kind in listOf(RunKind.COMPILE, RunKind.SERVICE, RunKind.NETWORK)
            assertEquals(kind.name, fatal, !f.batch.running); assertEquals(if (fatal) 1 else 2, f.prepare.size)
        }
    }
}
