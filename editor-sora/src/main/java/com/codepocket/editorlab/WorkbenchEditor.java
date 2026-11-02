package com.codepocket.editorlab;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import com.codepocket.editor.surface.EditorSurface;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Production Java boundary. No Sora or Kotlin-2 types in the public API. No file or runner ownership.
 * The historical adapter package is retained so the internal harness tests the identical implementation.
 */
public final class WorkbenchEditor extends FrameLayout implements EditorSurface, AutoCloseable {
    public interface Changes { void changed(int start, int removed, int inserted, boolean history); }
    private final SoraInputEditor editor;
    private final SoraEditorAdapter adapter;
    private final SoraSyntaxController syntax;
    private final ExecutorService syntaxLoader = Executors.newSingleThreadExecutor();
    private Changes changes;
    private Runnable caretChanged, scrolled, compositionEnded, inputRejected;
    private boolean closed, replacing;
    private String key = "unloaded";
    private int previousLength;

    public WorkbenchEditor(Context context) {
        super(context);
        setSaveEnabled(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        editor = new SoraInputEditor(context);
        editor.installDecorations();
        editor.installCaretMotion();
        float density = getResources().getDisplayMetrics().density;
        editor.setLineNumberMarginLeft(10 * density);
        editor.setDividerMargin(8 * density, 12 * density);
        editor.setDividerWidth(density);
        editor.limitInput(200_000, () -> { if (!closed && inputRejected != null) inputRejected.run(); });
        editor.setSaveEnabled(false);
        editor.setTypefaceText(Typeface.MONOSPACE);
        editor.setTypefaceLineNumber(Typeface.MONOSPACE);
        editor.setTextSize(14);
        editor.getProps().allowFullscreen = false;
        // Keep composing transactions enabled: Sora disallowSuggestions commits CJK preedit immediately.
        editor.getProps().disallowSuggestions = false;
        editor.getComponent(EditorAutoCompletion.class).setEnabled(false);
        addView(editor, new LayoutParams(-1, -1));
        adapter = new SoraEditorAdapter(editor);
        syntax = new SoraSyntaxController(editor, ignored -> {});
        adapter.replaceDocument(key, "", 0, 0);
        editor.subscribeEvent(ContentChangeEvent.class, (event, receipt) -> {
            int now = editor.getText().length();
            int start = event.getChangeStart().index;
            int removed = event.getAction() == ContentChangeEvent.ACTION_DELETE ? event.getChangedText().length() : 0;
            int inserted = event.getAction() == ContentChangeEvent.ACTION_INSERT ? event.getChangedText().length() : 0;
            if (event.getAction() == ContentChangeEvent.ACTION_SET_NEW_TEXT) { start = 0; removed = previousLength; inserted = now; }
            previousLength = now;
            editor.clearDecorations();
            editor.setDiagnostics(null);
            syntax.request();
            if (!closed && !replacing && changes != null) changes.changed(start, removed, inserted, event.isCausedByUndoManager());
        });
        editor.subscribeEvent(SelectionChangeEvent.class, (event, receipt) -> {
            if (!closed && !replacing && caretChanged != null) caretChanged.run();
        });
        editor.subscribeEvent(ScrollEvent.class, (event, receipt) -> {
            if (!closed && scrolled != null) scrolled.run();
        });
        editor.assistanceRefresh = () -> { if (!closed && compositionEnded != null) compositionEnded.run(); };
        // Do not retain an Activity on the grammar worker. Dispose late completion even after detach/destroy.
        android.content.res.AssetManager assets = context.getApplicationContext().getAssets();
        syntaxLoader.execute(() -> {
            try {
                TextMateBundle bundle = TextMateBundle.load(assets::open);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> syntax.ready(bundle));
            } catch (Exception | LinkageError error) {
                android.util.Log.e("CodePocket", "Offline editor grammar loading failed", error);
                post(() -> syntax.failed());
            }
        });
    }
    public CharSequence getText() { return editor.getText(); }
    public int length() { return editor.getText().length(); }
    public int getSelectionStart() { return editor.getCursor().getLeft(); }
    public int getSelectionEnd() { return editor.getCursor().getRight(); }
    public boolean isComposing() { return editor.hasComposingText(); }
    public boolean isLayoutReady() { return !editor.getEditable() || editor.isEditable(); }
    public int getContentScrollX() { return editor.getOffsetX(); }
    public int getContentScrollY() { return editor.getOffsetY(); }
    public long getInteractionVersion() { SoraBuffer.Viewport s = adapter.viewport(); return s == null ? -1 : s.interaction; }
    public View inputView() { return editor; }
    public void setChanges(Changes value) { changes = value; }
    public void setCaretChanged(Runnable value) { caretChanged = value; }
    public void setScrolled(Runnable value) { scrolled = value; }
    public void setCompositionEnded(Runnable value) { compositionEnded = value; }
    public void setInputRejected(Runnable value) { inputRejected = value; }
    public void setEditorFocusListener(OnFocusChangeListener listener) { editor.setOnFocusChangeListener(listener); }
    public void setEditorClickListener(Runnable listener) { editor.setOnClickListener(v -> listener.run()); }
    @Override public boolean requestFocus(int direction, Rect previous) { return editor != null && editor.requestFocus(direction, previous); }
    @Override public void setEnabled(boolean enabled) { super.setEnabled(enabled); if (editor != null) editor.setEditable(enabled); }
    @Override public InputConnection onCreateInputConnection(EditorInfo info) { return editor.onCreateInputConnection(info); }
    @Override public boolean onCheckIsTextEditor() { return true; }
    public boolean replaceDocument(String document, String text) {
        if (closed || text.length() > 200_000) return false;
        replacing = true;
        editor.alignCaret();
        try {
            if (!editor.withoutFocusRequests(() -> adapter.replaceDocument(document, text, 0, 0))) return false;
            key = document; previousLength = text.length(); return true;
        } finally { replacing = false; editor.alignCaret(); }
    }
    /** Explicit whole-buffer command for tests/internal tools; normal project loading uses replaceDocument. */
    public void setText(String text) {
        int old = length();
        if (!replaceDocument(key, text)) throw new IllegalStateException("Cannot replace editor document");
        if (changes != null) changes.changed(0, old, length(), false);
    }
    public void setSelection(int at) { setSelection(at, at); }
    public void setSelection(int start, int end) { select(start, end, false, true); }
    public boolean select(int start, int end, boolean reveal, boolean focus) {
        if (!focus && !editor.hasFocus())
            return editor.withoutFocusRequests(() -> adapter.select(adapter.snapshot(), start, end, reveal, true, true));
        return adapter.select(adapter.snapshot(), start, end, reveal, focus, true);
    }
    public String edit(int start, int end, String text, int selectionStart, int selectionEnd) {
        return adapter.apply(adapter.snapshot(), start, end, text, selectionStart, selectionEnd).name();
    }
    public String editWithoutFocus(int start, int end, String text, int selectionStart, int selectionEnd) {
        return editor.withoutFocusRequests(() -> edit(start, end, text, selectionStart, selectionEnd));
    }
    public boolean history(boolean redo) { return adapter.history(adapter.snapshot(), redo); }
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.undo || id == android.R.id.redo) return history(id == android.R.id.redo);
        return false;
    }
    public void cancelNavigation() { adapter.cancelNavigation(); }
    public boolean restoreViewport(int x, int y) { return adapter.restoreViewport(x, y); }
    public boolean revealCaret() { return adapter.revealCaret(); }
    @Override public Anchor anchor() { return adapter.anchor(); }
    public void setTextSize(float sp) { editor.setTextSize(sp); }
    public float getTextSize() { return editor.getTextSizePx(); }
    public void setLineNumbers(boolean value) { editor.setLineNumberEnabled(value); }
    public void setHorizontallyScrolling(boolean value) { editor.setWordwrap(!value); }
    public void setTextColor(int value) { editor.getColorScheme().setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_NORMAL, value); }
    public void configureLanguage(boolean cpp, boolean python, boolean dark) {
        syntax.configure(python ? SyntaxLanguage.PYTHON : cpp ? SyntaxLanguage.CPP : SyntaxLanguage.PLAIN, dark);
    }
    public String syntaxState() { return syntax.state(); }
    public void configureIndent(int width, boolean spaces, boolean autoIndent, boolean autoPairs) {
        editor.setTabWidth(width); editor.getProps().autoIndent = autoIndent;
        editor.getProps().symbolPairAutoCompletion = autoPairs;
        syntax.indentation(width, spaces);
    }
    public void configureMotion(boolean enabled, boolean move, boolean blink, int color) {
        editor.configureCaretMotion(enabled, move, blink);
        editor.setCaretColor(color);
        if (!enabled) finishMotion();
    }
    public void finishMotion() { editor.alignCaret(); }
    public boolean decorate(String source, int[] starts, int[] ends, int[] colors, int first, int second, int background) {
        return !closed && editor.decorate(source, starts, ends, colors, first, second, background);
    }
    public void diagnostics(int[] starts, int[] ends) {
        DiagnosticsContainer result = new DiagnosticsContainer();
        for (int i = 0; i < starts.length; i++)
            if (starts[i] >= 0 && starts[i] < ends[i] && ends[i] <= length())
                result.addDiagnostic(new DiagnosticRegion(starts[i], ends[i], DiagnosticRegion.SEVERITY_ERROR));
        editor.setDiagnostics(result);
    }
    public void pause() { syntax.pause(); cancelNavigation(); editor.pauseCaret(); }
    public void resume() { syntax.resume(); editor.resumeCaret(); }
    @Override public void close() {
        if (closed) return;
        closed = true; changes = null; caretChanged = null; scrolled = null; compositionEnded = null; inputRejected = null;
        syntaxLoader.shutdownNow(); adapter.close(); editor.release(); syntax.close();
    }
}
