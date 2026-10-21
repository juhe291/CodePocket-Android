package com.codepocket.editorlab;

import android.view.KeyEvent;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import com.codepocket.ide.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Opt-in UI bridge, sharing production assistance. No project access, semantic backend or execution. */
final class SoraAssistController implements AutoCloseable {
    private final SoraInputEditor editor;
    private final SoraEditorAdapter port;
    private final SoraAssistPopup popup;
    private final Consumer<String> status;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardOldestPolicy());
    private final List<SubscriptionReceipt<?>> subscriptions = new ArrayList<>();
    private final Set<Integer> consumed = new HashSet<>();
    private final AssistOptions options = new AssistOptions();
    private Future<?> pending;
    private Runnable readyRequest;
    private long generation;
    private boolean resumed, closed, applying, dark = true;
    private SyntaxLanguage language = SyntaxLanguage.PYTHON;
    private SoraAssistance.Result result;
    private SoraAssistance.Snippet snippet;
    private SoraBuffer.Viewport lastSelection;
    private String state = "共享基础词法 · 非语义";
    private final Runnable refresh = () -> request(SoraAssistance.Mode.AUTOMATIC);
    SoraAssistController(android.app.Activity activity, SoraInputEditor editor, SoraEditorAdapter port, Consumer<String> status) {
        this.editor = editor; this.port = port; this.status = status;
        popup = new SoraAssistPopup(activity, editor, port, this::accept, this::queryDismissed);
        editor.getComponent(EditorAutoCompletion.class).setEnabled(false); // one provider/list only
        editor.assistanceKeys = this::key;
        editor.assistanceRefresh = () -> editor.post(this::changed);
        subscriptions.add(editor.subscribeEvent(ContentChangeEvent.class, (event, receipt) -> {
            if (!applying && snippet != null) {
                int removed = event.getAction() == ContentChangeEvent.ACTION_DELETE ? event.getChangedText().length() : 0;
                int inserted = event.getAction() == ContentChangeEvent.ACTION_INSERT ? event.getChangedText().length() : 0;
                snippet = event.getAction() == ContentChangeEvent.ACTION_SET_NEW_TEXT ? null :
                    snippet.edited(port.viewport(), event.getChangeStart().index, removed, inserted, event.isCausedByUndoManager());
            }
            lastSelection = port.viewport(); changed();
        }));
        subscriptions.add(editor.subscribeEvent(SelectionChangeEvent.class, (event, receipt) -> {
            var current = port.viewport();
            boolean same = lastSelection != null && lastSelection.sameOwner(current) && lastSelection.revision == current.revision &&
                lastSelection.start == current.start && lastSelection.end == current.end && lastSelection.composing == current.composing;
            lastSelection = current;
            if (!same) {
                if (!applying && snippet != null && !snippet.matches(current)) snippet = null;
                changed();
            }
        }));
        subscriptions.add(editor.subscribeEvent(ScrollEvent.class, (event, receipt) -> reposition()));
    }
    String state() { return state; }
    private void state(String text) { state = text; status.accept(text); }
    void configure(SyntaxLanguage language, boolean dark) {
        boolean languageChanged = this.language != language;
        this.language = language; this.dark = dark;
        if (languageChanged) snippet = null;
        changed();
    }
    void resume() { if (!closed && !resumed) { resumed = true; changed(); } }
    void pause() { resumed = false; consumed.clear(); snippet = null; cancel(); }
    void cancel() {
        editor.removeCallbacks(refresh);
        if (readyRequest != null) { editor.removeCallbacks(readyRequest); readyRequest = null; }
        queryDismissed(); popup.dismiss();
    }
    private void queryDismissed() {
        generation++; result = null;
        if (pending != null) { pending.cancel(true); pending = null; }
        worker.getQueue().clear();
    }
    void changed() {
        cancel();
        if (!closed && resumed && !applying) editor.postDelayed(refresh, 120);
    }
    private boolean baseEligible() {
        return !closed && resumed && language != SyntaxLanguage.PLAIN && editor.isShown() && editor.hasFocus() && !editor.hasComposingText();
    }
    private boolean eligible() { return baseEligible() && editor.isEditable(); }
    private void waitForLayout(SoraAssistance.Mode mode) {
        var source = port.viewport();
        if (source == null || source.start != source.end || !editor.getEditable()) return;
        lastSelection = source;
        long deadline = android.os.SystemClock.uptimeMillis() + 2000;
        readyRequest = new Runnable() {
            @Override public void run() {
                if (readyRequest != this) return;
                var current = port.viewport();
                if (!baseEligible() || !source.sameOwner(current) || source.revision != current.revision ||
                        source.start != current.start || source.end != current.end) { readyRequest = null; return; }
                if (editor.isEditable()) { readyRequest = null; request(mode); }
                else if (android.os.SystemClock.uptimeMillis() < deadline) editor.postDelayed(this, 32);
                else { readyRequest = null; state("布局尚未就绪，请稍后重试代码辅助"); }
            }
        };
        // Only small readiness checks here; no text capture, parsing, focus change or forced frame loop.
        editor.postDelayed(readyRequest, 32);
    }
    void request(SoraAssistance.Mode mode) {
        cancel();
        if (!baseEligible()) return;
        if (!editor.isEditable()) { waitForLayout(mode); return; }
        lastSelection = port.viewport();
        if (lastSelection != null && lastSelection.length > SoraAssistance.MAX_CHARS) { state("超过4.5万字符，实验补全暂停"); return; }
        SoraBuffer.Snapshot source = port.snapshot();
        if (source == null || source.start != source.end) return;
        if (source.text.length() > SoraAssistance.MAX_CHARS) { state("超过4.5万字符，实验补全暂停"); return; }
        boolean cpp = language == SyntaxLanguage.CPP; final long ticket = ++generation;
        try {
            pending = worker.submit(() -> {
                try {
                    SoraAssistance.Result found = SoraAssistance.analyze(source, cpp, mode);
                    editor.post(() -> {
                        if (closed || ticket != generation || !eligible() || !source.matches(port.snapshot()) || cpp != (language == SyntaxLanguage.CPP)) return;
                        result = found;
                        if (found != null) {
                            editor.getCursorAnimator().cancel(); // align logical caret before placing the popup
                            if (!popup.show(found, dark)) result = null;
                            state(found.completions != null ? "基础词法补全 · 非语义" : "内置参数参考 · 非语义");
                        } else if (mode != SoraAssistance.Mode.AUTOMATIC) state("无可用结果 · 基础词法/内置参考，不推断成员类型");
                    });
                } catch (RuntimeException | LinkageError error) {
                    editor.post(() -> { if (!closed && ticket == generation) { cancel(); state("代码辅助失败，原始编辑仍可使用"); } });
                }
            });
        } catch (RejectedExecutionException ignored) { if (!closed) state("代码辅助忙，请稍后重试"); }
    }
    void reposition() { if (popup.showing()) popup.reposition(); }
    boolean accept(int index) {
        if (!eligible() || result == null) return false;
        var source = result;
        CompletionEdit insertion = source.insertion(port.snapshot(), language == SyntaxLanguage.CPP, index, options);
        if (insertion == null) { cancel(); return false; }
        var change = insertion.getChange(); cancel(); snippet = null; applying = true;
        boolean accepted;
        try { accepted = port.apply(source.source, change.getStart(), change.getEnd(), change.getText(), change.getSelectionStart(), change.getSelectionEnd()).accepted(); }
        finally { applying = false; }
        if (accepted && insertion.getSnippet() != null) {
            var frame = port.viewport();
            if (frame != null) snippet = new SoraAssistance.Snippet(frame, insertion.getSnippet().session(frame.document(), frame.revision, frame.length));
        }
        changed(); return accepted;
    }
    boolean field(boolean reverse) {
        var current = port.viewport();
        if (!eligible() || snippet == null || !snippet.matches(current)) { snippet = null; return false; }
        var navigation = snippet.session.move(reverse);
        var next = navigation.getSession(); var source = port.snapshot();
        applying = true; cancel(); boolean accepted;
        try { accepted = port.select(source, navigation.getStart(), navigation.getEnd(), true, false, true); }
        finally { applying = false; }
        snippet = accepted && next != null ? new SoraAssistance.Snippet(port.viewport(), next) : null;
        changed(); return accepted;
    }
    private boolean key(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP) return consumed.remove(code);
        if (event.getAction() != KeyEvent.ACTION_DOWN || !baseEligible() || (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0) return false;
        boolean plainModifiers = event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed();
        boolean handled = false;
        if (code == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed() && !event.isAltPressed() && !event.isMetaPressed()) {
            request(event.isShiftPressed() ? SoraAssistance.Mode.SIGNATURE : SoraAssistance.Mode.COMPLETION); handled = true;
        } else {
            if (!editor.isEditable()) return false;
            CompletionKey key = code == KeyEvent.KEYCODE_TAB ? CompletionKey.TAB : code == KeyEvent.KEYCODE_ENTER ? CompletionKey.ENTER :
                code == KeyEvent.KEYCODE_DPAD_UP ? CompletionKey.UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? CompletionKey.DOWN : code == KeyEvent.KEYCODE_ESCAPE ? CompletionKey.ESC : CompletionKey.OTHER;
            CompletionAction action = CompletionKeys.INSTANCE.action(key, options, popup.showingCandidates(), true, popup.explicitSelection(),
                editor.hasComposingText(), editor.getCursor().isSelected(), plainModifiers || event.isShiftPressed(), false);
            if (action == CompletionAction.ACCEPT) handled = accept(popup.index());
            else if (action == CompletionAction.PREVIOUS || action == CompletionAction.NEXT) { popup.move(action == CompletionAction.NEXT ? 1 : -1); handled = true; }
            else if (action == CompletionAction.DISMISS) { cancel(); handled = true; }
            else if (!plainModifiers && key == CompletionKey.TAB) handled = field(event.isShiftPressed());
            else if (!plainModifiers && !event.isShiftPressed() && key == CompletionKey.ESC && (popup.showing() || snippet != null)) { snippet = null; cancel(); handled = true; }
        }
        if (handled) consumed.add(code);
        return handled;
    }
    @Override public void close() {
        if (closed) return;
        pause(); closed = true; editor.assistanceKeys = null; editor.assistanceRefresh = null;
        for (var receipt : subscriptions) receipt.unsubscribe(); subscriptions.clear(); worker.shutdownNow();
    }
}
