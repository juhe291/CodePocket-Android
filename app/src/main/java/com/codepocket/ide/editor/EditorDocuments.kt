package com.codepocket.ide.editor

import com.codepocket.ide.EditorSessions
import com.codepocket.editor.surface.EditorSurface

/** Document replacement is separate from edits/undo. Only the owner may invoke it after a handoff. */
interface DocumentEditorPort : NavigatingEditorPort, EditorSurface {
    // Read the loaded buffer even while file I/O suspends interactive commands.
    fun captureDocument(): EditorFrame?
    fun replaceDocument(text: String): Boolean
}

class EditorDocumentRequest internal constructor(internal val source: EditorFrame?, internal val revision: Long)

enum class DocumentTransitionResult { APPLIED, STALE, SOURCE_CHANGED, COMPOSING, CLOSED }

/** UI-thread handoff gate used by file/workspace loading. It never owns or saves document text. */
class EditorDocumentTransitions : AutoCloseable {
    private var pending: EditorDocumentRequest? = null
    private var closed = false

    fun begin(source: EditorFrame?, revision: Long): EditorDocumentRequest? {
        if (closed) return null
        return EditorDocumentRequest(source, revision).also { pending = it }
    }

    fun commit(request: EditorDocumentRequest, current: EditorFrame?, revision: Long,
        apply: () -> Unit): DocumentTransitionResult {
        if (closed) return DocumentTransitionResult.CLOSED
        if (pending !== request) return DocumentTransitionResult.STALE
        pending = null // Consume before validation or callbacks, including exceptions and reentrancy.
        if (request.source?.composing == true || current?.composing == true) return DocumentTransitionResult.COMPOSING
        if (request.revision != revision || when {
            request.source == null -> current != null
            current == null -> true
            else -> !request.source.buffer.sameBuffer(current.buffer)
        }) return DocumentTransitionResult.SOURCE_CHANGED
        // Selection, scrolling, focus and stdin may have changed: capture their latest state in apply.
        apply()
        return DocumentTransitionResult.APPLIED
    }

    fun cancel() { pending = null }
    override fun close() { closed = true; cancel() }
}

/** Same-workspace reloads retain state captured after the background read began. */
object EditorDocumentSessions {
    fun choose(current: EditorSessions?, prepared: EditorSessions): EditorSessions =
        current?.takeIf { it.workspaceId == prepared.workspaceId } ?: prepared
}
