package com.codepocket.editorlab;

import android.content.Context;
import android.graphics.Rect;
import java.util.function.Supplier;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import java.util.function.Predicate;
import io.github.rosemoe.sora.widget.CodeEditor;

/** Passive invalidation hook. All gestures, keys, accessibility and IME remain Sora's. */
final class SoraInputEditor extends CodeEditor {
    Runnable navigationInput, assistanceRefresh;
    Predicate<KeyEvent> assistanceKeys;
    SoraInputEditor(Context context) { super(context); }
    private boolean suppressFocus;
    private SoraDecorations decorations;
    void installDecorations() { decorations = new SoraDecorations(this); }
    boolean decorate(String source, int[] starts, int[] ends, int[] colors, int first, int second, int background) {
        return decorations != null && decorations.apply(source, starts, ends, colors, first, second, background);
    }
    void clearDecorations() { if (decorations != null) decorations.clear(); }
    @Override public void setStyles(io.github.rosemoe.sora.lang.styling.Styles styles) {
        if (decorations != null && styles != null) decorations.wrap(styles);
        super.setStyles(styles);
    }
    @Override public void updateStyles(io.github.rosemoe.sora.lang.styling.Styles styles,
            io.github.rosemoe.sora.lang.analysis.StyleUpdateRange range) {
        if (decorations != null && styles != null) decorations.wrap(styles);
        super.updateStyles(styles, range);
    }
    <T> T withoutFocusRequests(Supplier<T> action) {
        boolean previous = suppressFocus; suppressFocus = true;
        try { return action.get(); } finally { suppressFocus = previous; }
    }
    @Override public boolean requestFocus(int direction, Rect previous) {
        return !suppressFocus && super.requestFocus(direction, previous);
    }
    private void input() { if (navigationInput != null) navigationInput.run(); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_MOVE) input();
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) input();
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) input();
        if (assistanceKeys != null && assistanceKeys.test(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection connection = super.onCreateInputConnection(info);
        if (connection == null) return null;
        return new InputConnectionWrapper(connection, false) {
            @Override public boolean finishComposingText() {
                boolean wasComposing = SoraInputEditor.this.hasComposingText();
                boolean result = super.finishComposingText();
                if (wasComposing && !SoraInputEditor.this.hasComposingText() && assistanceRefresh != null) assistanceRefresh.run();
                return result;
            }
        };
    }
}
