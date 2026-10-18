package com.codepocket.editorlab;

import com.codepocket.editor.surface.TextEdits;
import com.codepocket.editor.surface.TextEdits.Result;
import io.github.rosemoe.sora.text.Content;
import java.util.function.BiConsumer;

/** Real Sora Content transactions. Calls on the UI thread; snapshots only at explicit commands. */
final class SoraBuffer implements AutoCloseable {
    final String key;
    final Content content;
    private boolean closed;
    static final class Snapshot {
        private final SoraBuffer owner;
        final String key, text;
        final long revision;
        final int start, end;
        private Snapshot(SoraBuffer owner) {
            this.owner = owner; key = owner.key; revision = owner.revision(); text = owner.content.toString();
            start = owner.content.getCursor().getLeft(); end = owner.content.getCursor().getRight();
        }
    }
    private SoraBuffer(String key, String text) { this.key = key; content = new Content(text); }
    static SoraBuffer open(String key, String text) {
        if (key == null || key.isEmpty() || text == null)
            throw new IllegalArgumentException("Invalid scratch document");
        // Loading is lossless, including existing oversized/ill-formed scratch text. Explicit edits
        // remain bounded and reject malformed replacement text; never truncate retained drafts.
        // A fresh Content owns a fresh UndoManager; never reuse another document's history.
        return new SoraBuffer(key, text);
    }
    long revision() { return content.getDocumentVersion(); }
    Snapshot snapshot() { return closed ? null : new Snapshot(this); }
    Result sourceFailure(Snapshot source, boolean composing, boolean matchSelection) {
        if (closed) return Result.NO_DOCUMENT;
        if (composing) return Result.COMPOSING;
        if (source == null || source.owner != this || source.revision != revision() || !source.text.contentEquals(content) ||
                (matchSelection && (source.start != content.getCursor().getLeft() || source.end != content.getCursor().getRight())))
            return Result.STALE;
        return null;
    }
    Result apply(Snapshot source, int start, int end, String text, int selectionStart, int selectionEnd,
            boolean composing, boolean matchSelection, BiConsumer<Integer, Integer> select) {
        Result failure = sourceFailure(source, composing, matchSelection);
        if (failure != null) return failure;
        Result result = TextEdits.check(content, content.getCursor().getLeft(), content.getCursor().getRight(),
            start, end, text, selectionStart, selectionEnd, ScratchEdits.MAX_CHARS);
        if (result == Result.APPLIED) {
            content.beginBatchEdit();
            try { content.replace(start, end, text); } finally { content.endBatchEdit(); }
        }
        // Widget adapter must dispatch its actual selection/IME notifications; do not change Cursor silently.
        if (result == Result.APPLIED || result == Result.SELECTION_ONLY) select.accept(selectionStart, selectionEnd);
        return result;
    }
    boolean history(Snapshot source, boolean composing, boolean redo, Runnable action) {
        if (sourceFailure(source, composing, true) != null || !(redo ? content.canRedo() : content.canUndo())) return false;
        action.run(); // CodeEditor undo/redo in the app; actual Content undo/redo in host tests.
        return true;
    }
    @Override public void close() { closed = true; }
}
