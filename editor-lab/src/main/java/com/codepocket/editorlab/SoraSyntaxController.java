package com.codepocket.editorlab;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;
import java.util.function.Consumer;

/** UI-thread attachment only. Loading/regex priming happen before handing a bundle to this class. */
final class SoraSyntaxController implements AutoCloseable {
    private final CodeEditor editor;
    private final Consumer<String> changed;
    private TextMateBundle bundle;
    private SyntaxLanguage requested = SyntaxLanguage.PYTHON, applied;
    private boolean dark = true, appliedDark, closed, paused, broken;
    private Boolean fallbackDark;
    private String state = "语法加载中 · 纯文本";
    private final Runnable update = this::reconcile;
    SoraSyntaxController(CodeEditor editor, Consumer<String> changed) { this.editor = editor; this.changed = changed; }
    void ready(TextMateBundle value) {
        if (closed) { value.close(); return; }
        bundle = value; request();
    }
    void failed() { if (!closed) { state = "语法加载失败 · 纯文本"; changed.accept(state); } }
    String state() { return state; }
    void configure(SyntaxLanguage language, boolean isDark) { requested = language; dark = isDark; broken = false; request(); }
    void request() { if (!closed && !paused) { editor.removeCallbacks(update); editor.post(update); } }
    void pause() { paused = true; editor.removeCallbacks(update); }
    void resume() { paused = false; request(); }
    private void reconcile() {
        if (closed || paused) return;
        SyntaxLanguage effective = requested.effective(editor.getText().length());
        if (bundle == null) {
            if (fallbackDark == null || fallbackDark != dark) {
                editor.setColorScheme(dark ? new SchemeVS2019() : new SchemeGitHub()); fallbackDark = dark;
            }
            return;
        }
        if (broken) return;
        if (applied == effective && appliedDark == dark) { describe(effective); return; }
        if (editor.hasComposingText()) { editor.postDelayed(update, 120); return; }
        try {
            var model = bundle.theme(dark);
            if (applied != effective) {
                if (effective == SyntaxLanguage.PLAIN) editor.setEditorLanguage(null);
                else {
                    TextMateLanguage language = TextMateLanguage.create(effective.scope, bundle.grammars, bundle.themes, false);
                    // TextMate identifiers are lexical, not IntelliSense. Existing IDE completion is integrated separately.
                    language.setAutoCompleteEnabled(false); language.setTabSize(4); language.useTab(false);
                    if (language.getAnalyzeManager() == null) throw new IllegalStateException("TextMate analyzer unavailable");
                    editor.setEditorLanguage(language);
                }
            }
            TextMateColorScheme scheme = new TextMateColorScheme(bundle.themes, model);
            scheme.setTheme(model); editor.setColorScheme(scheme);
            applied = effective; appliedDark = dark;
            describe(effective);
        } catch (Exception | LinkageError error) {
            android.util.Log.e("CodePocketEditorLab", "TextMate attachment failed", error);
            editor.setEditorLanguage(null); applied = SyntaxLanguage.PLAIN; broken = true;
            editor.setColorScheme(dark ? new SchemeVS2019() : new SchemeGitHub());
            state = "语法适配失败 · 纯文本"; changed.accept(state);
        }
    }
    private void describe(SyntaxLanguage effective) {
        String next = effective == SyntaxLanguage.PLAIN ? (requested == SyntaxLanguage.PLAIN ? "纯文本" : "超出4.5万字符着色预算 · 纯文本") :
            effective.label + " · TextMate语法（非语义）";
        if (!next.equals(state)) { state = next; changed.accept(state); }
    }
    @Override public void close() {
        closed = true; editor.removeCallbacks(update);
        // Caller releases the editor (analyzer/scheme listeners) before disposing registries.
        if (bundle != null) bundle.close();
    }
}
