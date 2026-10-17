package com.codepocket.ide

import com.codepocket.ide.editor.*
import org.junit.Assert.*
import org.junit.Test

class EditorTransactionsTest {
    private fun frame(text: String = "abc", start: Int = text.length, end: Int = start) =
        EditorFrame(EditorSnapshot("workspace:file", 7, text, start, end), cpp = false, dark = true)

    private fun request(source: EditorFrame, change: TextChange, matchSelection: Boolean = true) =
        EditorTransaction(source.buffer, change, matchSelection)

    private fun rejected(current: EditorFrame?, transaction: EditorTransaction, expected: EditResult, max: Int = 200000) {
        var called = false
        val result = EditorTransactions.apply(current, transaction, max) { _, _ -> called = true }
        assertEquals(expected, result)
        assertFalse(result.accepted); assertFalse(result.changesText); assertFalse(called)
    }

    @Test fun documentSwitchAndDisposalNeverDispatchAnEdit() {
        val source = frame()
        val edit = request(source, TextChange(3, 3, "d", 4))
        rejected(null, edit, EditResult.NO_DOCUMENT)
        rejected(source.copy(buffer = source.buffer.copy(key = "workspace:other")), edit, EditResult.STALE)
        rejected(source.copy(buffer = source.buffer.copy(key = "other-workspace:file")), edit, EditResult.STALE)
    }

    @Test fun aSameContentNewRevisionAndAnExternalReloadBothInvalidateAnEdit() {
        val source = frame()
        val edit = request(source, TextChange(0, 1, "x", 1))
        rejected(source.copy(buffer = source.buffer.copy(revision = 8)), edit, EditResult.STALE)
        rejected(frame("abd"), edit, EditResult.STALE)
    }

    @Test fun compositionAndMissingSelectionCannotBeOverwritten() {
        val source = frame()
        val edit = request(source, TextChange(3, 3, "d", 4))
        rejected(source.copy(composing = true), edit, EditResult.COMPOSING)
        rejected(frame(start = 1), edit, EditResult.STALE)
        rejected(frame(start = -1, end = -1), edit, EditResult.STALE)
        rejected(frame(start = 3, end = 1), edit, EditResult.STALE)
    }

    @Test fun historyUsesTheSameDocumentVersionSelectionAndCompositionGate() {
        val source = frame()
        assertNull(EditorTransactions.sourceFailure(source, source.buffer))
        assertEquals(EditResult.NO_DOCUMENT, EditorTransactions.sourceFailure(null, source.buffer))
        assertEquals(EditResult.COMPOSING, EditorTransactions.sourceFailure(source.copy(composing = true), source.buffer))
        assertEquals(EditResult.STALE, EditorTransactions.sourceFailure(frame(start = 0), source.buffer))
        assertEquals(EditResult.STALE, EditorTransactions.sourceFailure(source.copy(buffer = source.buffer.copy(revision = 9)), source.buffer))
    }

    @Test fun malformedRangesAndSelectionsAreRejectedWithoutClamping() {
        val source = frame()
        for (change in listOf(TextChange(-1, 0, "", 0), TextChange(2, 1, "", 0), TextChange(0, 4, "", 0),
            TextChange(0, 1, "x", -1), TextChange(0, 1, "x", 4), TextChange(0, 1, "x", 0, 4)))
            rejected(source, request(source, change), EditResult.INVALID_RANGE)
    }

    @Test fun exactLengthLimitAppliesButOverflowDoesNotReachTheNativeWidget() {
        val source = frame()
        var calls = 0
        assertEquals(EditResult.APPLIED, EditorTransactions.apply(source, request(source, TextChange(0, 1, "XY", 2)), 4) { _, changed ->
            assertTrue(changed); calls++
        })
        assertEquals(1, calls)
        rejected(source, request(source, TextChange(0, 1, "XYZ", 3)), EditResult.LIMIT_EXCEEDED, 4)
    }

    @Test fun indentationNearTheWorkspaceLimitNeverDispatchesAnEdit() {
        // Reproduces the real toolbar path: EditorActions has no knowledge of the workspace limit.
        val code = "x".repeat(Workspace.MAX_CHARS - 1)
        val source = frame(code)
        val change = EditorActions.indent(code, code.length, code.length, false)
        rejected(source, request(source, change), EditResult.LIMIT_EXCEEDED)
    }

    @Test fun noOpUnindentDoesNotMutateTextOrCreateAnUndoEntry() {
        val source = frame()
        val change = EditorActions.indent(source.buffer.text, 3, 3, true)
        var calls = 0
        val result = EditorTransactions.apply(source, request(source, change), 200000) { _, _ -> calls++ }
        assertEquals(EditResult.UNCHANGED, result)
        assertTrue(result.accepted); assertFalse(result.changesText); assertEquals(0, calls)
    }

    @Test fun matchingTextWithANewSelectionDoesNotReplaceTheBuffer() {
        val source = frame()
        var changesText = true
        var selection = -1 to -1
        val result = EditorTransactions.apply(source, request(source, TextChange(0, 3, "abc", 2, 0)), 200000) { edit, changed ->
            changesText = changed; selection = edit.selectionStart to edit.selectionEnd
        }
        assertEquals(EditResult.SELECTION_ONLY, result)
        assertFalse(changesText); assertEquals(2 to 0, selection)
    }

    @Test fun emojiCanBeReplacedWholeButCannotBeSplit() {
        val source = frame("a😀z")
        rejected(source, request(source, TextChange(2, 3, "x", 3)), EditResult.INVALID_RANGE)
        rejected(source, request(source, TextChange(1, 2, "x", 2)), EditResult.INVALID_RANGE)
        assertEquals(EditResult.APPLIED, EditorTransactions.check(source, request(source, TextChange(1, 3, "🙂", 3)), 20))
    }

    @Test fun newSelectionCannotLandInsideAnInsertedOrExistingEmoji() {
        val source = frame("a😀z")
        rejected(source, request(source, TextChange(0, 0, "x", 3)), EditResult.INVALID_RANGE)
        rejected(source, request(source, TextChange(1, 3, "🙂", 2)), EditResult.INVALID_RANGE)
    }

    @Test fun unpairedSurrogatesCannotEnterThroughProgrammaticEdits() {
        val source = frame()
        for (text in listOf("\uD83D", "\uDE00", "x\uD83Dy", "\uDE00\uD83D"))
            rejected(source, request(source, TextChange(0, 0, text, 0)), EditResult.INVALID_TEXT)
        assertEquals(EditResult.APPLIED, EditorTransactions.check(source, request(source, TextChange(0, 0, "😀", 2)), 20))
    }

    @Test fun crlfEditsAndSelectionsRespectBothCharacters() {
        val source = frame("a\r\nb")
        rejected(source, request(source, TextChange(2, 2, "x", 3)), EditResult.INVALID_RANGE)
        rejected(source, request(source, TextChange(1, 3, "\r\n", 2)), EditResult.INVALID_RANGE)
        assertEquals(EditResult.APPLIED, EditorTransactions.check(source, request(source, TextChange(1, 3, "\n", 2)), 20))
    }

    @Test fun aCaretCannotSplitACrlfCreatedAtAnEditSeam() {
        val source = frame("\rX\n")
        rejected(source, request(source, TextChange(1, 2, "", 1)), EditResult.INVALID_RANGE)
        assertEquals(EditResult.APPLIED, EditorTransactions.check(source, request(source, TextChange(1, 2, "", 2)), 20))
    }

    @Test fun replaceAllCanSurviveACaretMoveButNotAnotherEdit() {
        val source = frame("abc abc", 0)
        val replacement = EditorSearch.replaceAll(source.buffer.text, "abc", "x")!!
        val request = request(source, replacement.change, matchSelection = false)
        val current = frame("abc abc", 5)
        assertEquals(EditResult.APPLIED, EditorTransactions.check(current, request, 20))
        rejected(current.copy(buffer = current.buffer.copy(revision = 8)), request, EditResult.STALE)
        rejected(current.copy(composing = true), request, EditResult.COMPOSING)
    }

    @Test fun replaceSelectionRemainsTiedToTheSelectedMatch() {
        val source = frame("abc abc", 0, 3)
        val change = EditorSearch.replaceSelection(source.buffer.text, "abc", "x", 0, 3)!!
        rejected(frame("abc abc", 4, 7), request(source, change), EditResult.STALE)
    }

    @Test fun identicalSearchReplacementIsAcknowledgedWithoutChangingHistoryOrDirtyState() {
        val source = frame("abc", 0, 3)
        val replacement = EditorSearch.replaceSelection("abc", "abc", "abc", 0, 3)!!
        val result = EditorTransactions.check(source, request(source, replacement), 20)
        assertTrue(result.accepted); assertFalse(result.changesText)
    }

    @Test fun completionOfAnAlreadyCompleteTokenOnlyMovesTheCaret() {
        val source = frame("😀\r\nprint", 7)
        val request = CompletionRequest(source.buffer.key, 7, source.buffer.text, 7, 7, false)
        val batch = LexicalCompletion.complete(request)!!
        val edit = batch.insertion(request, batch.items.indexOfFirst { it.label == "print" }, AppSettings())!!
        val text = source.buffer.text
        var caret = -1
        val result = EditorTransactions.apply(source, EditorTransaction(source.buffer, edit.change), 200000) { change, changed ->
            assertFalse(changed); caret = change.selectionStart
        }
        // Completion of an already complete token is a selection-only operation.
        assertEquals(EditResult.SELECTION_ONLY, result)
        assertEquals("😀\r\nprint", text); assertEquals(9, caret)
    }

    @Test fun realSnippetExpansionPassesValidationAndStaleExpansionNeverDispatches() {
        val source = frame("for")
        val completion = CompletionRequest(source.buffer.key, 7, "for", 3, 3, false)
        val expansion = CodeSnippets.expand("py.for", completion, AppSettings(), 200000)!!
        var actual = "for"
        val result = EditorTransactions.apply(source, request(source, expansion.change), 200000) { change, _ ->
            actual = actual.replaceRange(change.start, change.end, change.text)
        }
        assertEquals(EditResult.APPLIED, result); assertTrue(actual.contains("for "))
        rejected(frame("for", 1), request(source, expansion.change), EditResult.STALE)
    }

    @Test fun nativeTypingCorrectionsUseThePostTypingSnapshot() {
        val change = EditorActions.typed("", 0, 0, "(", cpp = false)!!
        val after = frame("(", 1)
        var text = "("
        assertEquals(EditResult.APPLIED, EditorTransactions.apply(after, request(after, change), 200000) { edit, _ ->
            text = text.replaceRange(edit.start, edit.end, edit.text)
        })
        assertEquals("()", text)
        rejected(frame("(", 1).copy(composing = true), request(after, change), EditResult.COMPOSING)
    }

    @Test fun realLexicalCompletionCommitsExactlyOnce() {
        val source = frame("😀\r\npr")
        val completion = CompletionRequest(source.buffer.key, 7, source.buffer.text, source.buffer.start, source.buffer.end, false)
        val batch = LexicalCompletion.complete(completion)!!
        val insertion = batch.insertion(completion, batch.items.indexOfFirst { it.label == "print" }, AppSettings())!!
        var text = source.buffer.text
        var calls = 0
        assertEquals(EditResult.APPLIED, EditorTransactions.apply(source, request(source, insertion.change), 200000) { edit, changed ->
            assertTrue(changed); calls++; text = text.replaceRange(edit.start, edit.end, edit.text)
        })
        assertEquals(1, calls); assertEquals("😀\r\nprint", text)
    }

    @Test fun historyRestorationDoesNotExpandPairsOrIndentationAgain() {
        // Restoring '(' before ')' must leave the native undo result '()' exactly as supplied.
        assertNotNull(EditorActions.typed(")", 0, 0, "(", cpp = false))
        assertNull(EditorActions.typed(")", 0, 0, "(", cpp = false, fromHistory = true))
        assertNotNull(EditorActions.typed("if True:", 8, 0, "\n", cpp = false))
        assertNull(EditorActions.typed("if True:", 8, 0, "\n", cpp = false, fromHistory = true))
    }

    @Test fun multilineIndentAndUnindentPreserveCrlfAndTheExcludedLastLine() {
        val source = frame("one\r\ntwo\r\nthree", 0, 10)
        val indent = EditorActions.indent(source.buffer.text, 0, 10, false)
        var code = source.buffer.text
        assertEquals(EditResult.APPLIED, EditorTransactions.apply(source, request(source, indent), 200000) { edit, _ ->
            code = code.replaceRange(edit.start, edit.end, edit.text)
        })
        assertEquals("    one\r\n    two\r\nthree", code)
        assertEquals("\r\nthree", code.substring(indent.selectionEnd))
        val selected = frame(code, indent.selectionStart, indent.selectionEnd)
        val unindent = EditorActions.indent(code, selected.buffer.start, selected.buffer.end, true)
        assertEquals(EditResult.APPLIED, EditorTransactions.apply(selected, request(selected, unindent), 200000) { edit, _ ->
            code = code.replaceRange(edit.start, edit.end, edit.text)
        })
        assertEquals(source.buffer.text, code)
    }
}
