package com.codepocket.editorlab;

import android.view.View;
import android.view.ViewTreeObserver;
import com.codepocket.editor.surface.EditorSurface;
import com.codepocket.editor.surface.ViewportRequests;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import java.util.ArrayList;
import java.util.List;
import com.codepocket.editor.surface.TextEdits;
import com.codepocket.editor.surface.TextEdits.Result;
import io.github.rosemoe.sora.text.CharPosition;

/** Experimental widget adapter. Does not access project files, URI permissions or the runner. */
final class SoraEditorAdapter implements EditorSurface, AutoCloseable {
    private final SoraInputEditor editor;
    private final SoraEditorSurface surface;
    private SoraBuffer buffer;
    private boolean closed, replacing;
    private long interaction;
    private final List<SubscriptionReceipt<?>> subscriptions = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private final Runnable inputChanged = this::interactionChanged;
    private final ViewportRequests<SoraBuffer.Viewport> navigation;
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) { interactionChanged(); }
    };
    SoraEditorAdapter(SoraInputEditor editor) {
        this.editor = editor;
        surface = new SoraEditorSurface(editor, () -> buffer.key, () -> buffer.revision());
        navigation = new ViewportRequests<>(this::postNavigation, s -> s.x >= 0 && s.y >= 0,
            (s, visible) -> s.matches(viewport(), visible), this::commitViewport);
        subscriptions.add(editor.subscribeEvent(ContentChangeEvent.class, (event, receipt) -> interactionChanged()));
        subscriptions.add(editor.subscribeEvent(SelectionChangeEvent.class, (event, receipt) -> interactionChanged()));
        subscriptions.add(editor.subscribeEvent(ScrollEvent.class, (event, receipt) -> {
            // Sora also emits USER_DRAG for createLayout/WordwrapLayout scrollBy(0, 0).
            // Physical drags/wheel/keys are observed before dispatch; do not mistake reflow for input.
            if (event.getCause() == ScrollEvent.CAUSE_USER_FLING ||
                    event.getCause() == ScrollEvent.CAUSE_TEXT_SELECTING || event.getCause() == ScrollEvent.CAUSE_SCALE_TEXT)
                interactionChanged();
        }));
        editor.navigationInput = inputChanged;
        editor.addOnAttachStateChangeListener(attachment);
    }
    private boolean available() { return !closed && !replacing && buffer != null && editor.getText() == buffer.content; }
    @Override public Anchor anchor() { return available() ? surface.anchor() : null; }
    SoraBuffer.Snapshot snapshot() { return available() ? buffer.snapshot() : null; }

    boolean replaceDocument(String key, String text, int start, int end) {
        if (closed || replacing || editor.hasComposingText()) return false;
        final SoraBuffer next;
        try { next = SoraBuffer.open(key, text); } catch (IllegalArgumentException error) { return false; }
        int[] selection = TextEdits.restoreSelection(text, start, end);
        cancelNavigation();
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
        cancelNavigation();
        return buffer.apply(source, start, end, text, selectionStart, selectionEnd, editor.hasComposingText(), true,
            (a, b) -> select(a, b, true));
    }
    boolean history(SoraBuffer.Snapshot source, boolean redo) {
        cancelNavigation();
        return available() && editor.hasFocus() && buffer.history(source, editor.hasComposingText(), redo, redo ? editor::redo : editor::undo);
    }
    private void select(int start, int end, boolean reveal) {
        CharPosition a = editor.getText().getIndexer().getCharPosition(Math.min(start, end));
        CharPosition b = editor.getText().getIndexer().getCharPosition(Math.max(start, end));
        editor.setSelectionRegion(a.line, a.column, b.line, b.column, reveal);
    }
    boolean select(SoraBuffer.Snapshot source, int start, int end, boolean reveal, boolean focus, boolean matchSelection) {
        // Pinned Sora setSelectionRegion requests focus itself. Refuse implicit background selection.
        if (!available() || (!focus && !editor.hasFocus())) return false;
        boolean accepted = buffer.select(source, start, end, editor.hasComposingText(), matchSelection, (a, b) -> {
            cancelNavigation(); select(a, b, false);
        });
        if (!accepted) return false;
        if (focus) editor.requestFocus();
        if (reveal) revealCaret();
        return true;
    }
    boolean jumpToLine(SoraBuffer.Snapshot source, int line) {
        if (!available()) return false;
        int offset = buffer.lineOffset(source, line, editor.hasComposingText());
        return offset >= 0 && select(source, offset, offset, true, true, true);
    }
    SoraBuffer.Viewport viewport() {
        if (!available()) return null;
        return buffer.viewport(editor.getOffsetX(), editor.getOffsetY(), interaction, editor.hasComposingText(),
            editor.isAttachedToWindow() && editor.isShown() && editor.getWidth() > 0 && editor.getHeight() > 0 && editor.getLayout() != null && !layoutBusy());
    }
    /** Restore a loaded file's saved coordinates, guarded by the new real Content identity. */
    boolean restoreViewport(int x, int y) {
        if (!available()) return false;
        SoraBuffer.Viewport source = buffer.viewport(Math.max(0, x), Math.max(0, y), interaction,
            editor.hasComposingText(), false);
        return restoreViewport(source);
    }

    boolean restoreViewport(SoraBuffer.Viewport source) {
        if (source == null || !source.matches(viewport(), false)) return false;
        cancelNavigation(); return navigation.submit(source, false);
    }
    boolean revealCaret() {
        SoraBuffer.Viewport source = viewport();
        cancelNavigation(); return navigation.submit(source, true);
    }
    void cancelNavigation() {
        navigation.cancel();
        for (Pending item : new ArrayList<>(pending)) item.remove();
        pending.clear();
    }
    void interactionChanged() { interaction++; cancelNavigation(); }
    // In this editable experiment, isEditable also includes Sora's asynchronous layout/formatter gate.
    private boolean layoutBusy() { return editor.getEditable() && !editor.isEditable(); }
    private void postNavigation(Runnable action) {
        Pending item = new Pending(action); pending.add(item);
        item.observer.addOnPreDrawListener(item); editor.postDelayed(item.timeout, 2000); editor.invalidate();
    }
    private final class Pending implements ViewTreeObserver.OnPreDrawListener {
        final ViewTreeObserver observer = editor.getViewTreeObserver();
        final Runnable action;
        final Runnable timeout = this::finish;
        boolean finished;
        Pending(Runnable action) { this.action = action; }
        void remove() {
            finished = true; editor.removeCallbacks(timeout);
            if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            ViewTreeObserver now = editor.getViewTreeObserver();
            if (now != observer && now.isAlive()) now.removeOnPreDrawListener(this);
        }
        void finish() {
            if (finished) return;
            remove(); pending.remove(this); action.run();
        }
        @Override public boolean onPreDraw() {
            // Wait without a frame-by-frame loop. Sora invalidates after its layout worker completes.
            if (!finished && editor.isAttachedToWindow() && editor.isShown() && layoutBusy()) return true;
            finish(); return true;
        }
    }
    private void commitViewport(SoraBuffer.Viewport source, boolean reveal) {
        // No focus request, InputConnection replacement or guessed logical-line pixel positions.
        if (reveal) {
            CharPosition point = editor.getText().getIndexer().getCharPosition(source.end);
            editor.ensurePositionVisible(point.line, point.column, true);
        } else {
            int x = Math.max(0, Math.min(source.x, editor.getScrollMaxX()));
            int y = Math.max(0, Math.min(source.y, editor.getScrollMaxY()));
            int oldX = editor.getOffsetX(), oldY = editor.getOffsetY();
            var scroller = editor.getScroller(); scroller.forceFinished(true);
            scroller.startScroll(editor.getOffsetX(), editor.getOffsetY(), x - editor.getOffsetX(), y - editor.getOffsetY(), 0);
            scroller.abortAnimation();
            editor.dispatchEvent(new ScrollEvent(editor, oldX, oldY, x, y, ScrollEvent.CAUSE_MAKE_POSITION_VISIBLE));
            editor.invalidate();
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; cancelNavigation(); navigation.close();
        for (SubscriptionReceipt<?> receipt : subscriptions) receipt.unsubscribe();
        subscriptions.clear(); editor.removeOnAttachStateChangeListener(attachment);
        if (editor.navigationInput == inputChanged) editor.navigationInput = null;
        if (buffer != null) buffer.close();
    }
}
