package com.codepocket.editorlab;

import android.os.Bundle;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.inputmethod.*;
import io.github.rosemoe.sora.text.Content;

/** Preflight policy over the pinned editor's public input APIs. No truncation, rollback or IME state mirror. */
final class SoraInputGuard {
    private final SoraInputEditor editor;
    final int maximum;
    private final Runnable rejected;
    private long generation;
    private boolean closed;
    void close() { closed=true; generation++; nativeConnection=null; }
    private InputConnection nativeConnection;
    SoraInputGuard(SoraInputEditor editor, int maximum, Runnable rejected) {
        this.editor = editor; this.maximum = maximum; this.rejected = rejected;
    }
    boolean fits(long size) {
        if (closed) return false;
        if (size <= maximum) return true;
        rejected.run(); return false;
    }
    boolean replacement(CharSequence text, int removed) {
        return text != null && fits((long) editor.getText().length() - removed + text.length());
    }
    boolean ime(CharSequence text, boolean composing) {
        if (text == null) return false;
        var cursor = editor.getCursor();
        int selected = cursor.getRight() - cursor.getLeft();
        boolean active = editor.hasComposingText();
        // Fast path: removing a composition can only make the proposed text smaller.
        long upper = (long) editor.getText().length() + text.length() - (composing && active ? 0 : selected);
        if (upper <= maximum) return true;
        if (!active) return fits(upper);
        int[] range = composition();
        if (range == null) { rejected.run(); return false; }
        int removed = range[1] - range[0];
        if (!composing) removed += selected - Math.max(0, Math.min(cursor.getRight(), range[1]) - Math.max(cursor.getLeft(), range[0]));
        return replacement(text, removed);
    }
    private int[] composition() {
        if (nativeConnection == null) return null;
        // 0.23.7 publishes its real composing range as a styled public query, including on API24.
        // Query only on the slow, near-limit composing path. No private fields or inferred cursor range.
        int length = editor.getText().length(), left = editor.getCursor().getLeft(), right = editor.getCursor().getRight();
        int[] result = {length, -1};
        if (!collect(nativeConnection.getTextBeforeCursor(left, InputConnection.GET_TEXT_WITH_STYLES), 0, left, result) ||
            !collect(right == left ? "" : nativeConnection.getSelectedText(InputConnection.GET_TEXT_WITH_STYLES), left, right-left, result) ||
            !collect(nativeConnection.getTextAfterCursor(length-right, InputConnection.GET_TEXT_WITH_STYLES), right, length-right, result)) return null;
        return result[1] >= result[0] && result[0] >= 0 && result[1] <= length ? result : null;
    }
    private static boolean collect(CharSequence text, int offset, int expected, int[] range) {
        if (text == null || text.length() != expected) return expected == 0;
        if (!(text instanceof Spanned)) return true;
        Spanned spans = (Spanned) text;
        for (Object marker : spans.getSpans(0, text.length(), Object.class)) {
            // The pinned version uses Integer(SPAN_COMPOSING); also accept the standard composing flag.
            if ((spans.getSpanFlags(marker) & Spanned.SPAN_COMPOSING) == 0 &&
                    !(marker instanceof Integer && ((Integer) marker) == Spanned.SPAN_COMPOSING)) continue;
            range[0] = Math.min(range[0], offset + spans.getSpanStart(marker));
            range[1] = Math.max(range[1], offset + spans.getSpanEnd(marker));
        }
        return true;
    }
    InputConnection wrap(InputConnection connection) {
        if (closed) return null;
        nativeConnection = connection;
        Content source = editor.getText(); long ticket = ++generation;
        if (android.os.Build.VERSION.SDK_INT >= 34) return new Api34Connection(connection,source,ticket);
        if (android.os.Build.VERSION.SDK_INT >= 33) return new Api33Connection(connection,source,ticket);
        return new GuardedConnection(connection,source,ticket);
    }
    private class GuardedConnection extends InputConnectionWrapper {
        final Content source; final long ticket;
        GuardedConnection(InputConnection target,Content source,long ticket) { super(target,false);this.source=source;this.ticket=ticket; }
        final boolean current() { return !closed && source == editor.getText() && generation == ticket; }
        public boolean commitText(CharSequence text,int position) { return current() && ime(text,false) && super.commitText(text,position); }
        public boolean setComposingText(CharSequence text,int position) { return current() && ime(text,true) && super.setComposingText(text,position); }
        public boolean finishComposingText() {
            if (!current()) return false;
            boolean was=editor.hasComposingText(),result=super.finishComposingText();
            if (was && !editor.hasComposingText() && editor.assistanceRefresh!=null) editor.assistanceRefresh.run();
            return result;
        }
        public boolean setComposingRegion(int start,int end) { return current() && super.setComposingRegion(start,end); }
        public boolean setSelection(int start,int end) { return current() && super.setSelection(start,end); }
        public boolean deleteSurroundingText(int before,int after) { return current() && super.deleteSurroundingText(before,after); }
        public boolean deleteSurroundingTextInCodePoints(int before,int after) { return current() && super.deleteSurroundingTextInCodePoints(before,after); }
        public boolean performContextMenuAction(int id) { return current() && super.performContextMenuAction(id); }
        public boolean sendKeyEvent(KeyEvent event) { return current() && super.sendKeyEvent(event); }
        public boolean beginBatchEdit() { return current() && super.beginBatchEdit(); }
        public boolean endBatchEdit() { return current() && super.endBatchEdit(); }
        public boolean performPrivateCommand(String action,Bundle data) { return current() && super.performPrivateCommand(action,data); }
        public boolean performEditorAction(int action) { return current() && super.performEditorAction(action); }
        public void closeConnection() { if (current()) super.closeConnection(); }
    }
    @android.annotation.TargetApi(33)
    private class Api33Connection extends GuardedConnection {
        Api33Connection(InputConnection target,Content source,long ticket) { super(target,source,ticket); }
        public boolean commitText(CharSequence text,int position,TextAttribute attr) { return current() && ime(text,false) && super.commitText(text,position,attr); }
        public boolean setComposingText(CharSequence text,int position,TextAttribute attr) { return current() && ime(text,true) && super.setComposingText(text,position,attr); }
        public boolean setComposingRegion(int start,int end,TextAttribute attr) { return current() && super.setComposingRegion(start,end,attr); }
    }
    @android.annotation.TargetApi(34)
    private final class Api34Connection extends Api33Connection {
        Api34Connection(InputConnection target,Content source,long ticket) { super(target,source,ticket); }
        public boolean replaceText(int start,int end,CharSequence text,int position,TextAttribute attr) {
            return current() && start>=0 && end>=start && end<=source.length() && replacement(text,end-start) && super.replaceText(start,end,text,position,attr);
        }
    }
}
