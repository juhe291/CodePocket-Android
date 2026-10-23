package com.codepocket.editorlab;

import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.style.CursorAnimator;

/** Pixels only, using Sora 0.23.7's public CursorAnimator/layout contract. Never edits Content/selection.
 * One cancellable tween; a new operation starts from the current visual position, with no input throttle.
 * Current-line backgrounds deliberately use the real destination, not an animated line rectangle.
 */
final class SoraCaretMotion implements CursorAnimator, AutoCloseable {
    private final SoraInputEditor editor;
    private final float[] point = new float[2];
    private ValueAnimator movement, blinking;
    private boolean enabled, move, blink, foreground = true, closed, dragging;
    private boolean mutation, textStart, selectedStart, marked, blinkPending;
    private Content owner;
    private Object layoutOwner;
    private float textSize, regionOffset;
    private int rowHeight;
    private int startIndex, startLength, offsetX, offsetY, width, height;
    private float fromX, fromY, x, y, targetX, targetY, lineHeight, lineBottom, opacity = 1f;
    private long lastActivity;
    private final Runnable beginBlink = () -> {
        blinkPending = false;
        if (!canBlink()) return;
        blinking = ValueAnimator.ofFloat(0f, 1f);
        blinking.setDuration(1000); blinking.setRepeatCount(ValueAnimator.INFINITE);
        blinking.setInterpolator(new LinearInterpolator());
        blinking.addUpdateListener(value -> {
            if (!canBlink()) { stopBlink(); return; }
            opacity = (float) (.15 + .85 * (.5 + .5 * Math.cos(2 * Math.PI * (float) value.getAnimatedValue())));
            invalidateCaret();
        });
        blinking.start();
    };
    SoraCaretMotion(SoraInputEditor editor) { this.editor = editor; }
    void configure(boolean enabled, boolean move, boolean blink) {
        if (this.enabled == enabled && this.move == move && this.blink == blink) return;
        this.enabled = enabled; this.move = move; this.blink = blink; align();
    }
    void contentChanging() { mutation = true; }
    private boolean ready() { return !closed && editor.getLayout() != null && editor.isEditable(); }
    private boolean eligible() {
        return foreground && ready() && editor.isAttachedToWindow() && editor.isShown() && editor.hasFocus() &&
            editor.hasWindowFocus() && !editor.getCursor().isSelected() && !editor.hasComposingText() &&
            !dragging && !editor.getEventHandler().hasAnyHeldHandle();
    }
    private boolean visible() {
        float px = isRunning() ? x : targetX, py = isRunning() ? y : targetY;
        return px >= editor.getOffsetX() + editor.measureTextRegionOffset() && px <= editor.getOffsetX() + editor.getWidth() &&
            py >= editor.getOffsetY() && py - editor.getRowHeight() <= editor.getOffsetY() + editor.getHeight();
    }
    private boolean canBlink() { return enabled && blink && eligible() && visible(); }
    private boolean destination() {
        if (!ready()) return false;
        var cursor = editor.getCursor();
        editor.getLayout().getCharLayoutOffset(cursor.getLeftLine(), cursor.getLeftColumn(), point);
        targetX = editor.measureTextRegionOffset() + point[1];
        targetY = point[0] - (editor.getProps().textBackgroundWrapTextOnly ? editor.getLineSpacingPixels() / 2f : 0f);
        lineHeight = editor.getRowHeight() * editor.getLayout().getRowCountForLine(cursor.getLeftLine());
        editor.getLayout().getCharLayoutOffset(cursor.getLeftLine(), editor.getText().getColumnCount(cursor.getLeftLine()), point);
        lineBottom = point[0];
        return Float.isFinite(targetX) && Float.isFinite(targetY);
    }
    private boolean sameGeometry() {
        return layoutOwner == editor.getLayout() && width == editor.getWidth() && height == editor.getHeight() &&
            offsetX == editor.getOffsetX() && offsetY == editor.getOffsetY() && rowHeight == editor.getRowHeight() &&
            textSize == editor.getTextSizePx() && regionOffset == editor.measureTextRegionOffset();
    }
    @Override public void markStartPos() {
        textStart = mutation; mutation = false;
        marked = destination();
        if (!marked) { cancel(); return; }
        fromX = isRunning() ? x : targetX; fromY = isRunning() ? y : targetY;
        cancel();
        owner = editor.getText(); startLength = owner.length(); startIndex = editor.getCursor().getLeft();
        selectedStart = editor.getCursor().isSelected();
        offsetX = editor.getOffsetX(); offsetY = editor.getOffsetY(); width = editor.getWidth(); height = editor.getHeight();
        layoutOwner = editor.getLayout(); rowHeight = editor.getRowHeight(); textSize = editor.getTextSizePx(); regionOffset = editor.measureTextRegionOffset();
    }
    @Override public void markEndPos() {
        if (!destination()) { marked = false; cancel(); return; }
        boolean smallEdit = !textStart || (Math.abs(editor.getText().length() - startLength) <= 2 &&
            Math.abs(editor.getCursor().getLeft() - startIndex) <= 2 && !editor.getText().isUndoManagerWorking());
        boolean nearby = Math.abs(targetY - fromY) <= editor.getRowHeight() * 2f &&
            Math.abs(targetX - fromX) <= Math.min(editor.getWidth() / 2f, editor.getRowHeight() * 8f);
        marked &= enabled && move && eligible() && owner == editor.getText() && !selectedStart && smallEdit && nearby &&
            sameGeometry();
        if (!marked) { x = targetX; y = targetY; }
    }
    @Override public void start() {
        wake();
        if (!marked || (fromX == targetX && fromY == targetY)) { marked = false; return; }
        marked = false; x = fromX; y = fromY;
        final float ax = fromX, ay = fromY, bx = targetX, by = targetY;
        movement = ValueAnimator.ofFloat(0f, 1f);
        movement.setDuration(textStart ? 60 : 80); movement.setInterpolator(new DecelerateInterpolator());
        movement.addUpdateListener(value -> {
            if (!eligible() || owner != editor.getText() || !sameGeometry()) { align(); return; }
            float t = (float) value.getAnimatedValue(); x = ax + (bx - ax) * t; y = ay + (by - ay) * t;
            editor.postInvalidateOnAnimation();
        });
        movement.start();
    }
    @Override public void cancel() {
        if (movement != null) { boolean running = movement.isRunning(); movement.cancel(); movement.removeAllUpdateListeners(); movement = null;
            if (running && !closed) editor.postInvalidateOnAnimation(); }
    }
    @Override public boolean isRunning() { return movement != null && movement.isRunning(); }
    @Override public float animatedX() { return x; }
    @Override public float animatedY() { return y; }
    @Override public float animatedLineHeight() { return lineHeight; }
    @Override public float animatedLineBottom() { return lineBottom; }
    private void stopBlink() {
        editor.removeCallbacks(beginBlink); blinkPending = false;
        if (blinking != null) { blinking.cancel(); blinking.removeAllUpdateListeners(); blinking = null; }
        opacity = 1f;
    }
    private void armBlink() {
        if (blinkPending || blinking != null || !canBlink()) return;
        blinkPending = true;
        editor.postDelayed(beginBlink, Math.max(1, 600 - (SystemClock.uptimeMillis() - lastActivity)));
    }
    private void wake() { stopBlink(); lastActivity = SystemClock.uptimeMillis(); armBlink(); invalidateCaret(); }
    void selectionChanged() {
        if (!eligible()) cancel();
        wake();
    }
    void align() { cancel(); marked = false; mutation = false; destination(); x = targetX; y = targetY; wake(); }
    void dragging(boolean value) { dragging = value; if (value) align(); else wake(); }
    void pause() { foreground = false; cancel(); stopBlink(); invalidateCaret(); }
    void resume() { foreground = true; align(); }
    /** Invoked immediately before Sora paints. Composition/geometry always wins over a visual tween. */
    float frameOpacity() {
        if (!eligible()) { cancel(); stopBlink(); return 1f; }
        if (isRunning() && !sameGeometry()) align();
        if (!destination() || !visible()) { stopBlink(); return 1f; }
        if (!isRunning()) { x = targetX; y = targetY; }
        armBlink(); return opacity;
    }
    private void invalidateCaret() {
        if (closed) return;
        int margin = Math.max(3, (int) Math.ceil(editor.getInsertSelectionWidth()));
        float left = isRunning() ? Math.min(x, targetX) : targetX;
        float right = isRunning() ? Math.max(x, targetX) : targetX;
        float top = (isRunning() ? Math.min(y, targetY) : targetY) - editor.getRowHeight();
        float bottom = isRunning() ? Math.max(y, targetY) : targetY;
        editor.postInvalidateOnAnimation((int) left - editor.getOffsetX() - margin, (int) top - editor.getOffsetY() - margin,
            (int) right - editor.getOffsetX() + margin, (int) bottom - editor.getOffsetY() + margin);
    }
    @Override public void close() { pause(); closed = true; }
}
