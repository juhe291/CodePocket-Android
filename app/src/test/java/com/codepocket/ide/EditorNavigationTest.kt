package com.codepocket.ide

import com.codepocket.ide.editor.*
import org.junit.Assert.*
import org.junit.Test

class EditorNavigationTest {
    private fun frame(text: String = "a😀\r\nb", start: Int = 0, end: Int = start) =
        EditorFrame(EditorSnapshot("workspace:file", 4, text, start, end), false, true)

    @Test fun selectionChecksDocumentRevisionTextAndOriginalSelection() {
        val source = frame()
        val request = EditorSelectionRequest(source.buffer, 1, 3)
        assertTrue(EditorNavigation.canSelect(source, request))
        for (buffer in listOf(source.buffer.copy(key = "other:file"), source.buffer.copy(revision = 5),
            source.buffer.copy(text = "different"), source.buffer.copy(start = 1), source.buffer.copy(end = 1)))
            assertFalse(EditorNavigation.canSelect(source.copy(buffer = buffer), request))
    }

    @Test fun selectionDoesNotInterruptCompositionOrAnUnavailableDocument() {
        val source = frame()
        val request = EditorSelectionRequest(source.buffer, 0)
        assertFalse(EditorNavigation.canSelect(null, request))
        assertFalse(EditorNavigation.canSelect(source.copy(composing = true), request))
    }

    @Test fun selectionRejectsSplitEmojiCrlfAndOutOfBoundsWithoutClamping() {
        val source = frame()
        for ((start, end) in listOf(2 to 2, 4 to 4, -1 to 0, 0 to 7, 1 to 2, 4 to 6))
            assertFalse(EditorNavigation.canSelect(source, EditorSelectionRequest(source.buffer, start, end)))
        assertTrue(EditorNavigation.canSelect(source, EditorSelectionRequest(source.buffer, 6, 0)))
    }

    @Test fun savedSelectionClampsAndExpandsSurrogatesWithoutReversingDirection() {
        val text = "a😀b"
        assertEquals(EditorSelectionRange(1, 1), EditorNavigation.restoredSelection(text, 2, 2))
        assertEquals(EditorSelectionRange(1, 4), EditorNavigation.restoredSelection(text, 2, 99))
        assertEquals(EditorSelectionRange(4, 1), EditorNavigation.restoredSelection(text, 99, 2))
        assertEquals(EditorSelectionRange(0, 0), EditorNavigation.restoredSelection("", 99, -4))
    }

    @Test fun savedSelectionRespectsCompleteCrlf() {
        assertEquals(EditorSelectionRange(1, 1), EditorNavigation.restoredSelection("a\r\nb", 2, 2))
        assertEquals(EditorSelectionRange(0, 3), EditorNavigation.restoredSelection("a\r\nb", 0, 2))
        assertEquals(EditorSelectionRange(3, 0), EditorNavigation.restoredSelection("a\r\nb", 2, 0))
    }

    @Test fun literalFindProducesAValidSelectionButCannotOverrideANewCaret() {
        val source = frame("print(1)\nprint(2)")
        val hit = EditorSearch.find(source.buffer.text, "print", 1)!!
        val request = EditorSelectionRequest(source.buffer, hit.first, hit.last + 1)
        assertTrue(EditorNavigation.canSelect(source, request))
        assertFalse(EditorNavigation.canSelect(source.copy(buffer = source.buffer.copy(start = 2, end = 2)), request))
    }

    private class ViewportHarness {
        val tasks = ArrayDeque<() -> Unit>()
        var current: EditorViewportSnapshot? = EditorViewportSnapshot("workspace:file", 4, 1, 1, 10, 80, 2)
        val applied = mutableListOf<Pair<EditorViewportSnapshot, Boolean>>()
        val queue = EditorViewportQueue({ tasks.addLast(it) }, { current }) { value, reveal -> applied.add(value to reveal) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst()() }
    }

    @Test fun restorationActuallyWaitsUntilTheScheduledLayoutCallback() {
        val h = ViewportHarness()
        val source = h.current!!
        assertTrue(h.queue.submit(source))
        assertTrue(h.applied.isEmpty())
        h.drain()
        assertEquals(listOf(source to false), h.applied)
    }

    @Test fun aLaterCursorRevealWinsOverAnEarlierSessionRestore() {
        val h = ViewportHarness()
        assertTrue(h.queue.submit(h.current!!))
        assertTrue(h.queue.submit(h.current!!, reveal = true))
        h.drain()
        assertEquals(listOf(h.current!! to true), h.applied)
    }

    @Test fun aLateRestoreCannotAffectAnotherFileOrWorkspace() {
        for (key in listOf("workspace:other", "other-workspace:file")) {
            val h = ViewportHarness()
            assertTrue(h.queue.submit(h.current!!))
            h.current = h.current!!.copy(key = key)
            h.drain(); assertTrue(h.applied.isEmpty())
        }
    }

    @Test fun editsAndSelectionChangesInvalidateDelayedScrolls() {
        for (replacement in listOf<EditorViewportSnapshot.() -> EditorViewportSnapshot>(
            { copy(revision = 5) }, { copy(start = 2) }, { copy(end = 3) })) {
            val h = ViewportHarness()
            assertTrue(h.queue.submit(h.current!!))
            h.current = replacement(h.current!!)
            h.drain(); assertTrue(h.applied.isEmpty())
        }
    }

    @Test fun userInteractionInvalidatesEvenWhenTheCaretReturnsToTheSamePosition() {
        val h = ViewportHarness()
        assertTrue(h.queue.submit(h.current!!))
        h.current = h.current!!.copy(interaction = 3)
        h.drain(); assertTrue(h.applied.isEmpty())
    }

    @Test fun nativeLayoutScrollClampingDoesNotInvalidateTheSavedViewport() {
        val h = ViewportHarness()
        val saved = h.current!!
        assertTrue(h.queue.submit(saved))
        h.current = saved.copy(x = 0, y = 0)
        h.drain()
        assertEquals(listOf(saved to false), h.applied)
    }

    @Test fun compositionHiddenEditorAndDisposalDoNotReceiveDelayedScrolls() {
        for (replacement in listOf<EditorViewportSnapshot.() -> EditorViewportSnapshot?>(
            { copy(composing = true) }, { copy(visible = false) }, { null })) {
            val h = ViewportHarness()
            assertTrue(h.queue.submit(h.current!!))
            h.current = replacement(h.current!!)
            h.drain(); assertTrue(h.applied.isEmpty())
        }
    }

    @Test fun cancellationAndClosingInvalidateQueuedCallbacks() {
        val h = ViewportHarness()
        h.queue.submit(h.current!!); h.queue.cancel(); h.drain()
        assertTrue(h.applied.isEmpty())
        h.queue.submit(h.current!!); h.queue.close(); h.drain()
        assertTrue(h.applied.isEmpty()); assertFalse(h.queue.submit(h.current!!))
    }

    @Test fun corruptNegativeCoordinatesAndStaleRequestsAreRejectedBeforeScheduling() {
        val h = ViewportHarness()
        assertFalse(h.queue.submit(h.current!!.copy(x = -1)))
        assertFalse(h.queue.submit(h.current!!.copy(y = -1)))
        assertFalse(h.queue.submit(h.current!!.copy(revision = 3)))
        assertFalse(h.queue.submit(h.current!!.copy(composing = true)))
        assertTrue(h.tasks.isEmpty())
    }

    @Test fun aRepeatedLayoutCallbackCommitsAtMostOnce() {
        val h = ViewportHarness()
        h.queue.submit(h.current!!)
        val callback = h.tasks.removeFirst()
        callback(); callback()
        assertEquals(1, h.applied.size)
    }

    @Test fun aRejectedLayoutCallbackCannotReplayAfterTheViewBecomesVisible() {
        val h = ViewportHarness()
        h.queue.submit(h.current!!)
        val callback = h.tasks.removeFirst()
        h.current = h.current!!.copy(visible = false)
        callback()
        h.current = h.current!!.copy(visible = true)
        callback()
        assertTrue(h.applied.isEmpty())
    }

    @Test fun aHiddenCaptureCanBeRestoredAfterTheEditorBecomesVisible() {
        val h = ViewportHarness()
        h.current = h.current!!.copy(visible = false)
        val saved = h.current!!
        assertTrue(h.queue.submit(saved))
        h.current = saved.copy(visible = true)
        h.drain(); assertEquals(listOf(saved to false), h.applied)
    }
}
