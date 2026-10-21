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
        boolean matches(Snapshot other) {
            return other != null && owner == other.owner && !owner.closed && revision == other.revision &&
                start == other.start && end == other.end && text.equals(other.text);
        }
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
    /** Selection changes have no text budget: navigating an oversized loaded draft is lossless. */
    boolean select(Snapshot source, int start, int end, boolean composing, boolean matchSelection,
            BiConsumer<Integer, Integer> select) {
        if (sourceFailure(source, composing, matchSelection) != null ||
                !TextEdits.boundary(content, start) || !TextEdits.boundary(content, end)) return false;
        if (start != content.getCursor().getLeft() || end != content.getCursor().getRight())
            select.accept(Math.min(start, end), Math.max(start, end));
        return true;
    }
    int lineOffset(Snapshot source, int line, boolean composing) {
        if (sourceFailure(source, composing, true) != null || line < 1 || line > content.getLineCount()) return -1;
        return content.getIndexer().getCharIndex(line - 1, 0);
    }
    Viewport viewport(int x, int y, long interaction, boolean composing, boolean visible) {
        return closed ? null : new Viewport(this, x, y, interaction, composing, visible);
    }
    /** Lightweight stamp: Content identity and revision, never Content.toString(). */
    static final class Viewport {
        private final SoraBuffer owner;
        final long revision, interaction;
        final int start, end, x, y, length;
        final boolean composing, visible;
        private Viewport(SoraBuffer owner, int x, int y, long interaction, boolean composing, boolean visible) {
            this.owner = owner; revision = owner.revision(); this.interaction = interaction;
            start = owner.content.getCursor().getLeft(); end = owner.content.getCursor().getRight();
            this.x = x; this.y = y; this.length = owner.content.length(); this.composing = composing; this.visible = visible;
        }
        boolean sameOwner(Viewport other) { return other != null && owner == other.owner && !owner.closed; }
        String document() { return owner.key; }
        boolean matches(Viewport current, boolean requireVisible) {
            return current != null && owner == current.owner && !owner.closed && !composing && !current.composing &&
                (!requireVisible || current.visible) && revision == current.revision &&
                interaction == current.interaction && start == current.start && end == current.end;
        }
    }
    boolean history(Snapshot source, boolean composing, boolean redo, Runnable action) {
        if (sourceFailure(source, composing, true) != null || !(redo ? content.canRedo() : content.canUndo())) return false;
        action.run(); // CodeEditor undo/redo in the app; actual Content undo/redo in host tests.
        return true;
    }
    @Override public void close() { closed = true; }
}
