package com.codepocket.editorlab;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Canvas;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.util.ClipDataUtils;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import java.util.function.Supplier;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import java.util.function.Predicate;
import io.github.rosemoe.sora.widget.CodeEditor;

/** Sora event/focus hooks and the production input budget; the component still owns editing and IME. */
final class SoraInputEditor extends CodeEditor {
    Runnable navigationInput, assistanceRefresh;
    Predicate<KeyEvent> assistanceKeys;
    SoraInputEditor(Context context) { super(context); }
    private SoraCaretMotion caretMotion;
    @Override public float measureTextRegionOffset() {
        float offset = super.measureTextRegionOffset();
        return caretMotion != null && !isLineNumberEnabled() ? Math.max(offset, 12 * getResources().getDisplayMetrics().density) : offset;
    }
    private boolean transientCaretColor;
    private Integer caretColor;
    void setCaretColor(int color) { caretColor = color; getColorScheme().setColor(EditorColorScheme.SELECTION_INSERT, color); }
    @Override public void setColorScheme(EditorColorScheme scheme) {
        super.setColorScheme(scheme);
        if (caretColor != null) getColorScheme().setColor(EditorColorScheme.SELECTION_INSERT, caretColor);
    }
    void installCaretMotion() {
        // An owned scheme avoids changing any other editor's palette during the scoped caret paint.
        setColorScheme(new EditorColorScheme());
        caretMotion = new SoraCaretMotion(this); setCursorAnimator(caretMotion); setCursorBlinkPeriod(0);
        subscribeEvent(SelectionChangeEvent.class, (event, receipt) -> caretMotion.selectionChanged());
        subscribeEvent(ScrollEvent.class, (event, receipt) -> {
            if (event.getStartX() != event.getEndX() || event.getStartY() != event.getEndY()) caretMotion.align();
        });
    }
    void configureCaretMotion(boolean enabled, boolean move, boolean blink) {
        setCursorAnimationEnabled(enabled && move); caretMotion.configure(enabled, move, blink);
    }
    void alignCaret() { if (caretMotion != null) caretMotion.align(); }
    void pauseCaret() { if (caretMotion != null) caretMotion.pause(); }
    void resumeCaret() { if (caretMotion != null) caretMotion.resume(); }
    @Override public void beforeModification(io.github.rosemoe.sora.text.Content content) {
        if (caretMotion != null) caretMotion.contentChanging();
        super.beforeModification(content);
    }
    @Override public void onColorUpdated(int type) {
        // Native setColor normally invalidates every cached text RenderNode. The scoped fade must not.
        if (!transientCaretColor || type != EditorColorScheme.SELECTION_INSERT) super.onColorUpdated(type);
    }
    @Override protected void onDraw(Canvas canvas) {
        float alpha = caretMotion == null ? 1f : caretMotion.frameOpacity();
        if (alpha >= 1f) { super.onDraw(canvas); return; }
        EditorColorScheme scheme = getColorScheme(); int original = scheme.getColor(EditorColorScheme.SELECTION_INSERT);
        transientCaretColor = true;
        try {
            scheme.setColor(EditorColorScheme.SELECTION_INSERT, (original & 0x00ffffff) | Math.round((original >>> 24) * alpha) << 24);
            super.onDraw(canvas);
        } finally {
            scheme.setColor(EditorColorScheme.SELECTION_INSERT, original); transientCaretColor = false;
        }
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH); if (caretMotion != null) caretMotion.align();
    }
    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous); if (caretMotion != null) { if (focused) setCursorBlinkPeriod(0); caretMotion.align(); }
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (caretMotion != null) caretMotion.align();
    }
    @Override protected void onDetachedFromWindow() {
        if (caretMotion != null) caretMotion.pause(); super.onDetachedFromWindow();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); if (caretMotion != null) caretMotion.resume();
    }
    private boolean suppressFocus;
    private SoraInputGuard inputGuard;
    void limitInput(int maximum, Runnable rejected) { inputGuard = new SoraInputGuard(this, maximum, rejected); }
    @Override public void commitText(CharSequence text, boolean indent, boolean pairs) {
        if (inputGuard != null) {
            var cur = getCursor();
            if (!inputGuard.replacement(text, cur.getRight() - cur.getLeft())) return;
            long rawLength = (long) getText().length() - (cur.getRight() - cur.getLeft()) + text.length();
            // Delegate generation to Sora. Omit only automatic extras which cannot fit; never cut user text.
            if (pairs && getProps().symbolPairAutoCompletion && text.length() > 0) {
                var pair = languageSymbolPairs.matchBestPair(this, cur.left(), text.length() > 1 ? text.toString().toCharArray() : null, text.charAt(text.length()-1));
                if (pair != null && pair != SymbolPairMatch.SymbolPair.EMPTY_SYMBOL_PAIR) {
                    long replacing = (long)getText().length()-(cur.getRight()-pair.getInsertOffset())+pair.open.length()+pair.close.length();
                    // Surround-vs-replace is private in 0.23.7: reserve the larger possible native result.
                    long paired = cur.isSelected() ? Math.max(replacing,(long)getText().length()+pair.open.length()+pair.close.length()) : replacing;
                    if (paired > inputGuard.maximum || (cur.isSelected() && getEditorLanguage().getQuickQuoteHandler() != null)) pairs = false;
                }
            }
            if (indent && getProps().autoIndent && !cur.isSelected() && text.length() > 0 && (text.charAt(0) == '\n' || text.charAt(0) == '\r')) {
                String line = getText().getLineString(cur.getLeftLine()); int spaces = 0, tabs = 0;
                for (int i=0; i<cur.getLeftColumn(); i++) {
                    if (line.charAt(i) == ' ') spaces++; else if (line.charAt(i) == '\t') tabs++; else break;
                }
                try {
                    int count = spaces + tabs * getTabWidth() + getEditorLanguage().getIndentAdvance(
                        new ContentReference(getText()), cur.getLeftLine(), cur.getLeftColumn(), spaces, tabs);
                    if (rawLength + TextUtils.createIndent(count, getTabWidth(), getEditorLanguage().useTab()).length() > inputGuard.maximum) indent = false;
                } catch (Exception failure) { indent = false; }
            }
        }
        super.commitText(text, indent, pairs);
    }
    @Override public void pasteText(CharSequence text) {
        if (inputGuard == null || inputGuard.ime(text,false)) super.pasteText(text);
    }
    @Override protected void commitTab() {
        if (inputGuard == null || inputGuard.ime(createTabString(),false)) super.commitTab();
    }
    @Override public void insertText(String text, int offset) {
        if (inputGuard == null || inputGuard.replacement(text,getCursor().getRight()-getCursor().getLeft())) super.insertText(text,offset);
    }
    @Override public void setText(CharSequence text, boolean reuse, Bundle arguments) {
        if (inputGuard == null || text == null || inputGuard.fits(text.length())) super.setText(text,reuse,arguments);
    }
    @Override public void indentLines(boolean selectedOnly) {
        if (inputGuard != null) {
            var cur = getCursor(); if (selectedOnly && !cur.isSelected()) return;
            long projected = getText().length(); int width = getTabWidth(), tabLength = createTabString().length();
            // Budget the pinned native operation before its multi-line batch starts (no partial indentation).
            for (int i=cur.getLeftLine(); i<=cur.getRightLine(); i++) {
                long leading = TextUtils.countLeadingSpacesAndTabs(getText().getLine(i));
                int spaces=IntPair.getFirst(leading), tabs=IntPair.getSecond(leading), columns=spaces+tabs*width;
                int needed=width-columns%width;
                projected += spaces>0 && tabs>0 ? (long)((columns+needed)/width)*tabLength-spaces-tabs : needed==0 ? tabLength : needed;
            }
            if (!inputGuard.fits(projected)) return;
        }
        super.indentLines(selectedOnly);
    }
    @Override public void unindentSelection() {
        if (inputGuard!=null) {
            long projected=getText().length();int width=getTabWidth(),tabLength=createTabString().length();
            for(int line=getCursor().getLeftLine();line<=getCursor().getRightLine();line++) {
                long leading=TextUtils.countLeadingSpacesAndTabs(getText().getLine(line));
                int spaces=IntPair.getFirst(leading),tabs=IntPair.getSecond(leading),columns=spaces+tabs*width;
                if(columns==0)continue;
                int removed=columns%width==0 ? width : columns%width;
                projected+=spaces>0 && tabs>0 ? (long)(Math.abs(columns-removed)/width)*tabLength-spaces-tabs : -(tabs>0 ? 1 : removed);
            }
            if(!inputGuard.fits(projected))return;
        }
        super.unindentSelection();
    }
    @Override public void duplicateLine() {
        if (inputGuard != null && !getCursor().isSelected() && !inputGuard.fits((long)getText().length()+getText().getColumnCount(getCursor().getLeftLine())+1)) return;
        super.duplicateLine();
    }
    @Override public void duplicateSelection(String prefix, boolean selected) {
        if (inputGuard == null || inputGuard.fits((long)getText().length()+prefix.length()+getCursor().getRight()-getCursor().getLeft())) super.duplicateSelection(prefix,selected);
    }
    @Override public boolean onDragEvent(DragEvent event) {
        if (inputGuard != null && event.getAction()==DragEvent.ACTION_DROP && event.getClipData()!=null &&
            !inputGuard.fits((long)getText().length()+ClipDataUtils.clipDataToString(event.getClipData()).length())) return false;
        return super.onDragEvent(event);
    }
    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (inputGuard != null && action==AccessibilityNodeInfo.ACTION_SET_TEXT && args!=null) {
            CharSequence text=args.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE);
            if (text!=null && !inputGuard.fits(text.length())) return false;
        }
        return super.performAccessibilityAction(action,args);
    }
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
    private io.github.rosemoe.sora.text.Content mouseSelectionOwner;
    private boolean mouseStartedInSelection;
    private boolean insideSelection(MotionEvent event) {
        long point=getPointPositionOnScreen(event.getX(),event.getY());
        var position=getText().getIndexer().getCharPosition(IntPair.getFirst(point),IntPair.getSecond(point));
        return getCursorRange().isPositionInside(position);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (inputGuard != null && event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            if (action==MotionEvent.ACTION_DOWN) {
                mouseStartedInSelection=getCursor().isSelected() && insideSelection(event);mouseSelectionOwner=getText();
            } else if (action==MotionEvent.ACTION_UP) {
                boolean copying=mouseStartedInSelection && mouseSelectionOwner==getText() && getKeyMetaStates().isCtrlPressed() && !insideSelection(event);
                mouseStartedInSelection=false;mouseSelectionOwner=null;
                if (copying && !inputGuard.fits((long)getText().length()+getCursor().getRight()-getCursor().getLeft())) {
                    MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
                    try { super.dispatchTouchEvent(cancel); } finally { cancel.recycle(); }
                    return true;
                }
            } else if(action==MotionEvent.ACTION_CANCEL) { mouseStartedInSelection=false;mouseSelectionOwner=null; }
        }
        if (caretMotion != null) {
            if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_DOWN) caretMotion.dragging(true);
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) caretMotion.dragging(false);
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_MOVE) input();
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) input();
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            input();
            // The pinned native Ctrl+Alt+Enter path inserts a separator directly into Content.
            if(inputGuard!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.isCtrlPressed() && event.isAltPressed() &&
                !event.isShiftPressed() && !inputGuard.fits((long)getText().length()+getLineSeparator().getContent().length())) return true;
        }
        if (assistanceKeys != null && assistanceKeys.test(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public void release() { if(caretMotion!=null) caretMotion.close();if(inputGuard!=null) inputGuard.close();super.release(); }
    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection connection = super.onCreateInputConnection(info);
        if (connection == null) return null;
        if (inputGuard != null) return inputGuard.wrap(connection);
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
