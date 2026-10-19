package com.codepocket.ide.terminal.shell;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import java.util.function.BooleanSupplier;

/** Explicit user requests only. Let Android switch the served View from the editor first. */
final class TerminalKeyboard {
    private View target;
    private Runnable pending;

    void cancel() {
        if (target != null && pending != null) target.removeCallbacks(pending);
        target = null;
        pending = null;
    }

    void show(View view, BooleanSupplier allowed) {
        cancel();
        if (!allowed.getAsBoolean() || !view.requestFocus()) return;
        target = view;
        pending = () -> {
            if (target != view) return;
            target = null;
            pending = null;
            if (!view.hasFocus() || !view.isShown() || !allowed.getAsBoolean()) return;
            InputMethodManager ime = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (!ime.isActive(view)) ime.restartInput(view);
            ime.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            if (Build.VERSION.SDK_INT >= 30 && view.getWindowInsetsController() != null)
                view.getWindowInsetsController().show(WindowInsets.Type.ime());
        };
        if (!view.post(pending)) cancel();
    }
}
