package com.codepocket.ide.editor

import com.codepocket.ide.EditorSnapshot
import com.codepocket.ide.TextChange
import com.codepocket.ide.Workspace

data class EditorSelectionRequest(val source: EditorSnapshot, val start: Int, val end: Int = start,
    val reveal: Boolean = true, val focus: Boolean = false, val matchSelection: Boolean = true)
data class EditorSelectionRange(val start: Int, val end: Int)

/** Lightweight viewport stamp: never copies the document during panel resizing or scrolling. */
data class EditorViewportSnapshot(val key: String, val revision: Long, val start: Int, val end: Int,
    val x: Int, val y: Int, val interaction: Long, val composing: Boolean = false, val visible: Boolean = true) {
    fun matches(current: EditorViewportSnapshot?, requireVisible: Boolean): Boolean = current != null &&
        !composing && !current.composing && (!requireVisible || current.visible) &&
        key == current.key && revision == current.revision && start == current.start && end == current.end &&
        interaction == current.interaction
}

interface NavigatingEditorPort : EditingEditorPort, AutoCloseable {
    fun navigationSnapshot(): EditorViewportSnapshot?
    fun select(request: EditorSelectionRequest): Boolean
    // Deferred until layout; never restores focus. Native reflow may change the current scroll meanwhile.
    fun restoreViewport(snapshot: EditorViewportSnapshot): Boolean
    fun revealCaret(): Boolean
    fun locateDiagnostic(request: EditorDiagnostic): DiagnosticStatus
    fun cancelNavigation()
}

object EditorNavigation {
    fun canSelect(current: EditorFrame?, request: EditorSelectionRequest): Boolean =
        EditorTransactions.check(current, EditorTransaction(request.source,
            TextChange(0, 0, "", request.start, request.end), request.matchSelection), Workspace.MAX_CHARS).accepted

    /** Saved offsets may predate an external edit. Preserve direction without splitting emoji/CRLF. */
    fun restoredSelection(text: String, start: Int, end: Int): EditorSelectionRange {
        val a = start.coerceIn(0, text.length)
        val b = end.coerceIn(0, text.length)
        fun floor(at: Int) = if (EditorTransactions.boundary(text, at)) at else at - 1
        fun ceil(at: Int) = if (EditorTransactions.boundary(text, at)) at else at + 1
        return when {
            a == b -> EditorSelectionRange(floor(a), floor(a))
            a < b -> EditorSelectionRange(floor(a), ceil(b))
            else -> EditorSelectionRange(ceil(a), floor(b))
        }
    }
}

/** Latest navigation wins; interaction, edits, selection changes and disposal invalidate delayed work. */
class EditorViewportQueue(private val post: (() -> Unit) -> Unit,
    private val current: () -> EditorViewportSnapshot?,
    private val commit: (EditorViewportSnapshot, Boolean) -> Unit) : AutoCloseable {
    private val requests = com.codepocket.editor.surface.ViewportRequests<EditorViewportSnapshot>(
        { action -> post { action.run() } },
        { source -> source.x >= 0 && source.y >= 0 },
        { source, visible -> source.matches(current(), visible) },
        { source, reveal -> commit(source, reveal) }
    )

    fun submit(source: EditorViewportSnapshot, reveal: Boolean = false): Boolean = requests.submit(source, reveal)
    fun cancel() = requests.cancel()
    override fun close() = requests.close()
}
