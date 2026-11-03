package com.codepocket.editorlab;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntConsumer;

/**
 * Narrow breakpoint gutter over the editor's real line numbers. It never edits text, never owns a
 * document and never pretends a breakpoint was hit: tapping only reports a 1-based line back to the
 * workbench, which persists it in the existing file session.
 */
@SuppressLint("ViewConstructor") // Programmatic overlay created only with its owning real editor.
final class BreakpointGutter extends View implements ViewTreeObserver.OnGlobalLayoutListener, AutoCloseable {
    private final CodeEditor editor;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Set<Integer> lines = new TreeSet<>();
    private final List<SubscriptionReceipt<?>> subscriptions = new ArrayList<>();
    private IntConsumer toggle;
    private boolean closed;
    BreakpointGutter(Context context, CodeEditor editor) {
        super(context);
        this.editor = editor;
        dot.setColor(Color.parseColor("#D16969"));
        setContentDescription("断点栏");
        setClickable(true);
        setFocusable(false);
        subscriptions.add(editor.subscribeEvent(ScrollEvent.class, (event, receipt) -> invalidate()));
        subscriptions.add(editor.subscribeEvent(ContentChangeEvent.class, (event, receipt) -> { prune(); invalidate(); }));
        subscriptions.add(editor.subscribeEvent(SelectionChangeEvent.class, (event, receipt) -> invalidate()));
        editor.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }
    void setToggle(IntConsumer listener) { toggle = listener; }
    void setLines(int[] values) {
        lines.clear();
        if (values != null) for (int line : values) if (line > 0) lines.add(line);
        invalidate();
    }
    int[] lines() { int[] result = new int[lines.size()]; int index = 0; for (int line : lines) result[index++] = line; return result; }
    private void prune() {
        int count = editor.getText().getLineCount();
        lines.removeIf(line -> line > count);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (closed || editor.getLayout() == null || lines.isEmpty()) return;
        float radius = 3.5f * getResources().getDisplayMetrics().density;
        float x = getWidth() / 2f;
        for (int line : lines) {
            float[] offset = editor.getLayout().getCharLayoutOffset(line - 1, 0, null);
            float y = offset[0] - editor.getOffsetY();
            if (y < -radius || y > getHeight() + radius) continue;
            canvas.drawCircle(x, y + radius * 2.2f, radius, dot);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (closed) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
        int line = lineAt(event.getY());
        if (line < 0) return true;
        int count = editor.getText().getLineCount();
        if (line < 1 || line > count || toggle == null) return true;
        performClick();
        toggle.accept(line);
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    /** Real layout mapping (word wrap and scroll included); -1 means outside the text area. */
    private int lineAt(float y) {
        if (editor.getLayout() == null) return -1;
        float contentY = y + editor.getOffsetY();
        if (contentY < 0 || contentY > editor.getLayout().getLayoutHeight()) return -1;
        long position = editor.getLayout().getCharPositionForLayoutOffset(0f, contentY);
        return IntPair.getFirst(position) + 1;
    }
    @Override public void onGlobalLayout() { invalidate(); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        subscriptions.forEach(SubscriptionReceipt::unsubscribe);
        subscriptions.clear();
        editor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        toggle = null;
    }
}
