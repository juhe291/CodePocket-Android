package com.codepocket.editorlab;

import android.content.Context;
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
