package com.codepocket.editorlab;

import com.codepocket.editor.surface.EditorSurface;
import com.codepocket.editor.surface.TextEdits;
import com.codepocket.editor.surface.TextEdits.Result;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.widget.CodeEditor;

/** Experimental widget adapter. Does not access project files, URI permissions or the runner. */
final class SoraEditorAdapter implements EditorSurface, AutoCloseable {
    private final CodeEditor editor;
    private final SoraEditorSurface surface;
    private SoraBuffer buffer;
    private boolean closed, replacing;
    SoraEditorAdapter(CodeEditor editor) {
        this.editor = editor;
        surface = new SoraEditorSurface(editor, () -> buffer.key, () -> buffer.revision());
    }
    private boolean available() { return !closed && !replacing && buffer != null && editor.getText() == buffer.content; }
    @Override public Anchor anchor() { return available() ? surface.anchor() : null; }
    SoraBuffer.Snapshot snapshot() { return available() ? buffer.snapshot() : null; }

    boolean replaceDocument(String key, String text, int start, int end) {
        if (closed || replacing || editor.hasComposingText()) return false;
        final SoraBuffer next;
        try { next = SoraBuffer.open(key, text); } catch (IllegalArgumentException error) { return false; }
        int[] selection = TextEdits.restoreSelection(text, start, end);
        replacing = true;
        try {
            // The pinned overload reuses our fresh Content, not the old widget Content/history.
            editor.setText(next.content, true, null);
            if (buffer != null) buffer.close();
            buffer = next;
            select(Math.min(selection[0], selection[1]), Math.max(selection[0], selection[1]), false);
            return true;
        } finally { replacing = false; }
    }
    Result apply(SoraBuffer.Snapshot source, int start, int end, String text, int selectionStart, int selectionEnd) {
        if (!available()) return Result.NO_DOCUMENT;
        return buffer.apply(source, start, end, text, selectionStart, selectionEnd, editor.hasComposingText(), true,
            (a, b) -> select(a, b, true));
    }
    boolean history(SoraBuffer.Snapshot source, boolean redo) {
        return available() && buffer.history(source, editor.hasComposingText(), redo, redo ? editor::redo : editor::undo);
    }
    private void select(int start, int end, boolean reveal) {
        CharPosition a = editor.getText().getIndexer().getCharPosition(Math.min(start, end));
        CharPosition b = editor.getText().getIndexer().getCharPosition(Math.max(start, end));
        editor.setSelectionRegion(a.line, a.column, b.line, b.column, reveal);
    }
    @Override public void close() { closed = true; if (buffer != null) buffer.close(); }
}
