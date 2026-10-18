package com.codepocket.ide

import com.codepocket.ide.editor.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EditorDocumentsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun frame(text: String = "print(1)", key: String = "workspace:file", revision: Long = 4) =
        EditorFrame(EditorSnapshot(key, revision, text, 0, 0), false, true)

    @Test fun aSuccessfulHandoffCommitsExactlyOnce() {
        val gate = EditorDocumentTransitions()
        val source = frame()
        val request = gate.begin(source, 4)!!
        var commits = 0
        assertEquals(DocumentTransitionResult.APPLIED, gate.commit(request, source, 4) { commits++ })
        assertEquals(DocumentTransitionResult.STALE, gate.commit(request, source, 4) { commits++ })
        assertEquals(1, commits)
    }

    @Test fun aRealBackgroundReadCannotReplaceCodeTypedWhileItWasPending() {
        val file = temporary.newFile("target.py").apply { writeText("print(2)") }
        val gate = EditorDocumentTransitions()
        var current = frame()
        val request = gate.begin(current, 4)!!
        val started = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        val tabs = mutableListOf("original.py", "target.py")
        try {
            val reading = worker.submit<String> {
                started.countDown()
                check(resume.await(5, TimeUnit.SECONDS))
                file.readText()
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            current = frame("print(1)\n# new input", revision = 5)
            resume.countDown()
            val loaded = reading.get(5, TimeUnit.SECONDS)
            val result = gate.commit(request, current, 5) {
                tabs.remove("original.py")
                current = frame(loaded, "workspace:target", 6)
            }
            assertEquals(DocumentTransitionResult.SOURCE_CHANGED, result)
            assertEquals("print(1)\n# new input", current.buffer.text)
            assertEquals(listOf("original.py", "target.py"), tabs)
        } finally { resume.countDown(); worker.shutdownNow() }
    }

    @Test fun changedContentAtTheSameRevisionIsStillRejected() {
        val gate = EditorDocumentTransitions()
        val request = gate.begin(frame(), 4)!!
        assertEquals(DocumentTransitionResult.SOURCE_CHANGED,
            gate.commit(request, frame("print(9)"), 4) { fail("Must retain the buffer") })
    }

    @Test fun anEditThenUndoStillInvalidatesTheEarlierRead() {
        val gate = EditorDocumentTransitions()
        val request = gate.begin(frame(), 4)!!
        assertEquals(DocumentTransitionResult.SOURCE_CHANGED,
            gate.commit(request, frame(revision = 6), 6) { fail("Same text is a new revision") })
    }

    @Test fun switchingFileOrWorkspaceInvalidatesTheSource() {
        for (key in listOf("workspace:other", "other-workspace:file")) {
            val gate = EditorDocumentTransitions()
            val request = gate.begin(frame(), 4)!!
            assertEquals(DocumentTransitionResult.SOURCE_CHANGED,
                gate.commit(request, frame(key = key), 4) { fail("Wrong document") })
        }
    }

    @Test fun movedCaretAndLatestStdinAreCapturedAtCommitInsteadOfDiscarded() {
        val gate = EditorDocumentTransitions()
        val source = frame()
        val request = gate.begin(source, 4)!!
        val sessions = EditorSessions("project")
        sessions.file("old.py").stdin = "before"
        val current = source.copy(buffer = source.buffer.copy(start = 3, end = 1))
        val latestInput = "typed during reading"
        assertEquals(DocumentTransitionResult.APPLIED, gate.commit(request, current, 4) {
            sessions.file("old.py").apply {
                stdin = latestInput
                view = EditorViewState(current.buffer.start, current.buffer.end, 8, 90, "stdin", 4, 4)
            }
        })
        val restored = EditorSessions("project", sessions.toJson()).file("old.py")
        assertEquals(latestInput, restored.stdin)
        assertEquals(EditorViewState(3, 1, 8, 90, "stdin", 4, 4), restored.view)
    }

    @Test fun sameWorkspaceReloadKeepsInputAndIdentityUpdatedDuringPreparation() {
        val current = EditorSessions("project")
        val id = current.key("main.py")
        current.file("main.py").stdin = "before"
        val prepared = EditorSessions("project", current.toJson())
        current.file("main.py").stdin = "latest"
        current.file("main.py").view = EditorViewState(6, 2, 4, 50, "stdin", 3, 1)
        val chosen = EditorDocumentSessions.choose(current, prepared)
        assertSame(current, chosen)
        assertEquals(id, chosen.key("main.py"))
        assertEquals("latest", chosen.file("main.py").stdin)
        assertEquals(EditorViewState(6, 2, 4, 50, "stdin", 3, 1), chosen.file("main.py").view)
        assertEquals("before", prepared.file("main.py").stdin)
    }

    @Test fun startupAndOtherWorkspacesUseTheirPreparedState() {
        val current = EditorSessions("first")
        current.file("main.py").stdin = "private input"
        val prepared = EditorSessions("second")
        prepared.file("main.py").stdin = "other input"
        assertSame(prepared, EditorDocumentSessions.choose(null, prepared))
        assertSame(prepared, EditorDocumentSessions.choose(current, prepared))
        assertEquals("other input", prepared.file("main.py").stdin)
        assertNotEquals(current.key("main.py"), prepared.key("main.py"))
    }

    @Test fun themeOrLanguagePreferenceDoesNotPretendTheCodeChanged() {
        val gate = EditorDocumentTransitions()
        val source = frame()
        val request = gate.begin(source, 4)!!
        assertEquals(DocumentTransitionResult.APPLIED,
            gate.commit(request, source.copy(dark = false, cpp = true), 4) {})
    }

    @Test fun compositionAtTheBeginningOrEndPreventsReplacement() {
        for (beginComposing in listOf(false, true)) {
            val gate = EditorDocumentTransitions()
            val request = gate.begin(frame().copy(composing = beginComposing), 4)!!
            assertEquals(DocumentTransitionResult.COMPOSING,
                gate.commit(request, frame().copy(composing = !beginComposing), 4) { fail("IME owns input") })
        }
    }

    @Test fun startupWithNoLoadedDocumentCanCommitOnce() {
        val gate = EditorDocumentTransitions()
        val request = gate.begin(null, 0)!!
        var created = false
        assertEquals(DocumentTransitionResult.APPLIED, gate.commit(request, null, 0) { created = true })
        assertTrue(created)
    }

    @Test fun aDocumentAppearingOrDisappearingDuringTheReadIsRejected() {
        for (source in listOf(null, frame())) {
            val gate = EditorDocumentTransitions()
            val request = gate.begin(source, 4)!!
            val current = if (source == null) frame() else null
            assertEquals(DocumentTransitionResult.SOURCE_CHANGED,
                gate.commit(request, current, 4) { fail("Document availability changed") })
        }
    }

    @Test fun missingDocumentStillChecksTheActivityEditEpoch() {
        val gate = EditorDocumentTransitions()
        val request = gate.begin(null, 4)!!
        assertEquals(DocumentTransitionResult.SOURCE_CHANGED,
            gate.commit(request, null, 5) { fail("Unbound input must not disappear") })
    }

    @Test fun aNewRequestSupersedesAnEarlierRequestForIdenticalContent() {
        val gate = EditorDocumentTransitions()
        val source = frame()
        val first = gate.begin(source, 4)!!
        val second = gate.begin(source, 4)!!
        assertEquals(DocumentTransitionResult.STALE, gate.commit(first, source, 4) { fail("Old request") })
        assertEquals(DocumentTransitionResult.APPLIED, gate.commit(second, source, 4) {})
    }

    @Test fun requestsCannotBeCommittedByAnotherOwner() {
        val first = EditorDocumentTransitions()
        val second = EditorDocumentTransitions()
        val source = frame()
        val foreign = first.begin(source, 4)!!
        val own = second.begin(source, 4)!!
        assertEquals(DocumentTransitionResult.STALE, second.commit(foreign, source, 4) { fail("Wrong owner") })
        assertEquals(DocumentTransitionResult.APPLIED, second.commit(own, source, 4) {})
    }

    @Test fun cancelAndCloseNeverRunDestructiveCallbacks() {
        val gate = EditorDocumentTransitions()
        val source = frame()
        val cancelled = gate.begin(source, 4)!!
        gate.cancel()
        assertEquals(DocumentTransitionResult.STALE, gate.commit(cancelled, source, 4) { fail("Cancelled") })
        val closing = gate.begin(source, 4)!!
        gate.close(); gate.close()
        assertNull(gate.begin(source, 4))
        assertEquals(DocumentTransitionResult.CLOSED, gate.commit(closing, source, 4) { fail("Destroyed") })
    }

    @Test fun rejectedRequestsCannotReplayAfterTheBufferReturnsToItsOldContent() {
        val gate = EditorDocumentTransitions()
        val request = gate.begin(frame(), 4)!!
        assertEquals(DocumentTransitionResult.SOURCE_CHANGED,
            gate.commit(request, frame("changed"), 5) { fail("Changed") })
        assertEquals(DocumentTransitionResult.STALE,
            gate.commit(request, frame(), 4) { fail("Already consumed") })
    }

    @Test fun throwingCommitIsConsumedAndDoesNotRunAgain() {
        val gate = EditorDocumentTransitions()
        val source = frame()
        val request = gate.begin(source, 4)!!
        var commits = 0
        assertThrows(IllegalStateException::class.java) {
            gate.commit(request, source, 4) { commits++; error("Native load failed") }
        }
        assertEquals(DocumentTransitionResult.STALE, gate.commit(request, source, 4) { commits++ })
        assertEquals(1, commits)
    }

    @Test fun aCommitMayBeginTheNextStageWithoutLosingItsRequest() {
        val gate = EditorDocumentTransitions()
        val source = frame()
        val first = gate.begin(source, 4)!!
        var next: EditorDocumentRequest? = null
        assertEquals(DocumentTransitionResult.APPLIED, gate.commit(first, source, 4) { next = gate.begin(source, 4) })
        assertEquals(DocumentTransitionResult.APPLIED, gate.commit(next!!, source, 4) {})
    }

    @Test fun aFailedRealReadDoesNotCloseTheActiveTabOrInstallAnotherBuffer() {
        val gate = EditorDocumentTransitions()
        val source = frame()
        val request = gate.begin(source, 4)!!
        val tabs = mutableListOf("old.py", "missing.py")
        val read = runCatching { temporary.root.resolve("missing.py").readText() }
        assertTrue(read.isFailure)
        read.onSuccess { text -> gate.commit(request, source, 4) { tabs.remove("old.py"); fail(text) } }
            .onFailure { gate.cancel() }
        assertEquals(listOf("old.py", "missing.py"), tabs)
        assertEquals(DocumentTransitionResult.STALE, gate.commit(request, source, 4) { fail("Failed read") })
    }
}
