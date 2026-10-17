package com.codepocket.ide.editor

import com.codepocket.ide.EditorSnapshot
import com.codepocket.ide.TextChange

/** All positions are UTF-16. A background action must retain the snapshot it was computed from. */
data class EditorTransaction(val source: EditorSnapshot, val change: TextChange, val matchSelection: Boolean = true)

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
        if (change.start < 0 || change.end < change.start || change.end > code.length ||
            !boundary(code, change.start) || !boundary(code, change.end)) return EditResult.INVALID_RANGE
        // Long arithmetic prevents overflow before the platform LengthFilter sees the edit.
        val size = code.length.toLong() - (change.end - change.start) + change.text.length
        if (maxChars < 0 || size > maxChars) return EditResult.LIMIT_EXCEEDED
        if (!validUtf16(change.text)) return EditResult.INVALID_TEXT
        if (change.selectionStart !in 0..size.toInt() || change.selectionEnd !in 0..size.toInt()) return EditResult.INVALID_RANGE
        fun character(at: Int): Char = when {
            at < change.start -> code[at]
            at < change.start + change.text.length -> change.text[at - change.start]
            else -> code[at - change.text.length + (change.end - change.start)]
        }
        fun newBoundary(at: Int): Boolean = at == 0 || at == size.toInt() || !split(character(at - 1), character(at))
        if (!newBoundary(change.selectionStart) || !newBoundary(change.selectionEnd)) return EditResult.INVALID_RANGE
        val sameText = change.text.length == change.end - change.start && code.regionMatches(change.start, change.text, 0, change.text.length)
        return if (!sameText) EditResult.APPLIED
            else if (change.selectionStart != current.buffer.start || change.selectionEnd != current.buffer.end) EditResult.SELECTION_ONLY
            else EditResult.UNCHANGED
    }

    internal fun boundary(text: String, at: Int): Boolean = at == 0 || at == text.length || !split(text[at - 1], text[at])
    private fun split(left: Char, right: Char): Boolean =
        (Character.isHighSurrogate(left) && Character.isLowSurrogate(right)) || (left == '\r' && right == '\n')

    private fun validUtf16(text: String): Boolean {
        var at = 0
        while (at < text.length) {
            val ch = text[at++]
            if (Character.isHighSurrogate(ch)) {
                if (at == text.length || !Character.isLowSurrogate(text[at++])) return false
            } else if (Character.isLowSurrogate(ch)) return false
        }
        return true
    }
}
