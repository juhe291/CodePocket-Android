package com.codepocket.ide

data class RestoredEditorBuffer(val text: String, val baseHash: String, val dirty: Boolean)

/** Reconcile a durable checkpoint with the file actually read, independent from Android lifecycle. */
object DraftRecovery {
    fun restore(savedText: String, draft: Draft?): RestoredEditorBuffer {
        val text = draft?.text ?: savedText
        val dirty = text != savedText
        // A checkpoint can outlive a successful save (for example a rotation Bundle).
        // Matching text proves this buffer is current; keep the old base only for unwritten edits.
        val base = if (dirty) draft?.baseHash ?: DocumentTree.digest(savedText)
            else DocumentTree.digest(savedText)
        return RestoredEditorBuffer(text, base, dirty)
    }
}
