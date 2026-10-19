package com.codepocket.ide.terminal.shell;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import java.util.function.BooleanSupplier;

/** One explicit request, waiting for window focus as well as View focus. No background IME retries. */
final class TerminalKeyboard {
    private View target;
    private Runnable pending, expiry;
    private ViewTreeObserver observer;
    private ViewTreeObserver.OnWindowFocusChangeListener windowFocus;
    private ViewTreeObserver.OnGlobalFocusChangeListener viewFocus;
    private View.OnAttachStateChangeListener attachment;

    void cancel() {
        if (target != null) {
            if (pending != null) target.removeCallbacks(pending);
            if (expiry != null) target.removeCallbacks(expiry);
            if (attachment != null) target.removeOnAttachStateChangeListener(attachment);
        }
        if (observer != null && observer.isAlive()) {
            if (windowFocus != null) observer.removeOnWindowFocusChangeListener(windowFocus);
            if (viewFocus != null) observer.removeOnGlobalFocusChangeListener(viewFocus);
        }
        target=null;pending=null;expiry=null;observer=null;windowFocus=null;viewFocus=null;attachment=null;
    }

    void show(View view, BooleanSupplier allowed) {
        cancel();
        if (!allowed.getAsBoolean() || !view.isAttachedToWindow() || !view.isShown() || !view.requestFocus()) return;
        target=view;
        pending=()-> {
            if (target != view) return;
            if (!view.hasFocus() || !view.isShown() || !view.isAttachedToWindow() || !allowed.getAsBoolean()) {
                cancel();return;
            }
            // PopupMenu dismiss and Activity resume can leave View focus before window focus.
            if (!view.hasWindowFocus()) return;
            InputMethodManager ime=(InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (!ime.isActive(view)) ime.restartInput(view);
            ime.showSoftInput(view,0); // Explicit user request; never SHOW_FORCED.
            if (Build.VERSION.SDK_INT >= 30 && view.getWindowInsetsController() != null)
                view.getWindowInsetsController().show(WindowInsets.Type.ime());
            cancel(); // Back must hide the IME without a delayed request reopening it.
        };
        windowFocus=focused->{if(focused && target==view){view.removeCallbacks(pending);view.post(pending);}};
        viewFocus=(oldFocus,newFocus)->{if(target==view && newFocus!=view)cancel();};
        attachment=new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { cancel(); }
        };
        observer=view.getViewTreeObserver();observer.addOnWindowFocusChangeListener(windowFocus);
        observer.addOnGlobalFocusChangeListener(viewFocus);view.addOnAttachStateChangeListener(attachment);
        // Retain adjustResize, but a previous ALWAYS_HIDDEN request must not override an explicit tap.
        Context context=view.getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;
        }
        if (context instanceof Activity) {
            android.view.Window window=((Activity)context).getWindow();
            int mode=window.getAttributes().softInputMode;
            int state=mode & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE;
            if (state==WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN || state==WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                window.setSoftInputMode(mode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE);
        }
        expiry=this::cancel;view.postDelayed(expiry,2000);
        if (!view.post(pending)) cancel();
    }
}
