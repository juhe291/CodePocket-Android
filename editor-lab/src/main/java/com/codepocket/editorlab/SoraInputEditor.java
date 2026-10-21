package com.codepocket.editorlab;

import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import io.github.rosemoe.sora.widget.CodeEditor;

/** Passive invalidation hook. All gestures, keys, accessibility and IME remain Sora's. */
final class SoraInputEditor extends CodeEditor {
    Runnable navigationInput;
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
        return super.dispatchKeyEvent(event);
    }
}
