package com.codepocket.ide
import org.junit.Test
import org.junit.Assert.*
class TerminalRunClockTest {
 @Test fun startupHasIndependentBound() { val c=TerminalRunClock(100);assertEquals(30000,c.remaining(100));assertEquals(0,c.remaining(30100)) }
 @Test fun waitingDoesNotSpendExecutionTime() {
  val c=TerminalRunClock(0);c.update(TerminalRunClock.State.RUNNING,1,100)
  c.update(TerminalRunClock.State.WAITING,2,2100);assertEquals(300000,c.remaining(2100))
  c.update(TerminalRunClock.State.RUNNING,3,200000);assertEquals(8000,c.remaining(200000))
  assertEquals(0,c.remaining(208000))
 }
 @Test fun replayCannotResetInputDeadline() {
  val c=TerminalRunClock(0);c.update(TerminalRunClock.State.WAITING,2,100)
  assertFalse(c.update(TerminalRunClock.State.RUNNING,1,1000))
  assertTrue(c.update(TerminalRunClock.State.WAITING,3,1000))
  assertEquals(299100,c.remaining(1000));assertEquals(0,c.remaining(300100))
 }
 @Test fun executionBudgetAccumulatesAcrossMultipleInputs() {
  val c=TerminalRunClock(0);c.update(TerminalRunClock.State.RUNNING,1,0)
  c.update(TerminalRunClock.State.WAITING,2,3000);c.update(TerminalRunClock.State.RUNNING,3,10000)
  c.update(TerminalRunClock.State.WAITING,4,15000);c.update(TerminalRunClock.State.RUNNING,5,50000)
  assertEquals(2000,c.remaining(50000));assertEquals(0,c.remaining(52000))
 }
 @Test fun removedPythonModeDoesNotDiscardOtherLegacySettings() {
  val restored=AppSettings.fromJson("""{"version":5,"pythonInteractive":false,"fontSize":18}""")
  assertEquals(18,restored.fontSize);assertFalse(restored.toJson().contains("pythonInteractive"))
 }
}
