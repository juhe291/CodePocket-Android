package com.codepocket.editorlab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.codepocket.editor.surface.EditorSurface;
import com.codepocket.editor.surface.PopupGeometry;

/** Visible geometry probe only. No fake completion candidates, analysis or execution results. */
final class SurfaceProbe {
    private final Activity activity;
    private final View parent;
    private final EditorSurface surface;
    private final TextView content;
    private final PopupWindow popup;
    private EditorSurface.Anchor source;
    SurfaceProbe(Activity activity, View parent, EditorSurface surface) {
        this.activity = activity; this.parent = parent; this.surface = surface;
        content = new TextView(activity); content.setTextSize(12); content.setPadding(dp(10), dp(8), dp(10), dp(8));
        popup = new PopupWindow(content, 1, 1, false);
        popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setElevation(dp(6)); popup.setAnimationStyle(0);
        if (Build.VERSION.SDK_INT >= 29) popup.setIsLaidOutInScreen(true);
        popup.setOnDismissListener(() -> source = null);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    boolean show(boolean dark) {
        source = surface.anchor();
        if (source == null) { dismiss(); return false; }
        content.setText(activity.getString(R.string.surface_probe_info, source.offset, source.revision));
        content.setTextColor(dark ? 0xffd4d4d4 : 0xff242424);
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xff252526 : 0xfff3f3f3); background.setStroke(dp(1), 0xff808080);
        popup.setBackgroundDrawable(background);
        return reposition();
    }
    @SuppressLint("RtlHardcoded") // Shared anchors are absolute screen coordinates, independent of UI direction.
    boolean reposition() {
        if (source == null) return false;
        EditorSurface.Anchor current = surface.anchor();
        if (activity.isDestroyed() || activity.isFinishing() || current == null ||
            !current.matches(source.document, source.revision, source.offset)) { dismiss(); return false; }
        EditorSurface.Placement placed = PopupGeometry.place(current.visible, current.x, current.top, current.bottom,
            dp(280), dp(96), dp(64), dp(3));
        if (placed == null) { dismiss(); return false; }
        int x = placed.x, y = placed.y;
        if (Build.VERSION.SDK_INT < 29) {
            int[] screen = new int[2], window = new int[2];
            activity.getWindow().getDecorView().getLocationOnScreen(screen);
            activity.getWindow().getDecorView().getLocationInWindow(window);
            x -= screen[0] - window[0]; y -= screen[1] - window[1];
        }
        if (popup.isShowing()) popup.update(x, y, placed.width, placed.height);
        else {
            popup.setWidth(placed.width); popup.setHeight(placed.height);
            try { popup.showAtLocation(parent, Gravity.TOP | Gravity.LEFT, x, y); }
            catch (WindowManager.BadTokenException error) { dismiss(); return false; }
        }
        return true;
    }
    void dismiss() { source = null; popup.dismiss(); }
}
