package com.codepocket.ide.editor

import com.codepocket.ide.EditorSnapshot
import com.codepocket.ide.TextChange
import com.codepocket.editor.surface.TextEdits

/** All positions are UTF-16. A background action must retain the snapshot it was computed from. */
data class EditorTransaction(val source: EditorSnapshot, val change: TextChange, val matchSelection: Boolean = true,
    val preserveFocus: Boolean = false)

enum class EditResult(val accepted: Boolean = false, val changesText: Boolean = false) {
    APPLIED(true, true), SELECTION_ONLY(true), UNCHANGED(true),
    NO_DOCUMENT, STALE, COMPOSING, INVALID_RANGE, INVALID_TEXT, LIMIT_EXCEEDED
}

enum class HistoryAction { UNDO, REDO }

/** Optional editing capability; read-only analysis clients need only EditorPort. UI-thread calls only. */
interface EditingEditorPort : EditorPort {
    fun applyEdit(transaction: EditorTransaction): EditResult
    // Native history stays with the widget; this must trigger the normal text/revision/save observers.
    fun performHistory(source: EditorSnapshot, action: HistoryAction): Boolean
}

/** Shared preflight and dispatch used by the production native adapter and host regression tests. */
object EditorTransactions {
    fun sourceFailure(current: EditorFrame?, source: EditorSnapshot, matchSelection: Boolean = true): EditResult? {
        if (current == null) return EditResult.NO_DOCUMENT
        if (current.composing) return EditResult.COMPOSING
        val actual = current.buffer
        if (!source.sameBuffer(actual) || (matchSelection && (source.start != actual.start || source.end != actual.end)))
            return EditResult.STALE
        return null
    }

    fun apply(current: EditorFrame?, transaction: EditorTransaction, maxChars: Int,
        commit: (TextChange, Boolean) -> Unit): EditResult {
        val result = check(current, transaction, maxChars)
        if (result == EditResult.APPLIED || result == EditResult.SELECTION_ONLY)
            commit(transaction.change, result.changesText)
        return result
    }

    fun check(current: EditorFrame?, transaction: EditorTransaction, maxChars: Int): EditResult {
        sourceFailure(current, transaction.source, transaction.matchSelection)?.let { return it }
        val code = current!!.buffer.text
        val change = transaction.change
        return EditResult.valueOf(TextEdits.check(code, current.buffer.start, current.buffer.end,
            change.start, change.end, change.text, change.selectionStart, change.selectionEnd, maxChars).name)
    }

    internal fun boundary(text: String, at: Int): Boolean = TextEdits.boundary(text, at)
}
