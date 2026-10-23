package com.codepocket.editorlab;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
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
    private int tabWidth = 4;
    private boolean spaces = true;
    void indentation(int width, boolean useSpaces) {
        if (tabWidth == width && spaces == useSpaces) return;
        tabWidth = width; spaces = useSpaces; applied = null; request();
    }
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
                attach(dark ? new SchemeVS2019() : new SchemeGitHub()); fallbackDark = dark;
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
                    language.setAutoCompleteEnabled(false); language.setTabSize(tabWidth); language.useTab(!spaces);
                    if (language.getAnalyzeManager() == null) throw new IllegalStateException("TextMate analyzer unavailable");
                    editor.setEditorLanguage(language);
                }
            }
            TextMateColorScheme scheme = new TextMateColorScheme(bundle.themes, model);
            scheme.setTheme(model); attach(scheme);
            applied = effective; appliedDark = dark;
            describe(effective);
        } catch (Exception | LinkageError error) {
            android.util.Log.e("CodePocketEditorLab", "TextMate attachment failed", error);
            editor.setEditorLanguage(null); applied = SyntaxLanguage.PLAIN; broken = true;
            attach(dark ? new SchemeVS2019() : new SchemeGitHub());
            state = "语法适配失败 · 纯文本"; changed.accept(state);
        }
    }
    private void attach(EditorColorScheme scheme) {
        editor.setColorScheme(scheme);
        // Attachment can initialize/reset base colors. Apply workbench overrides afterwards.
        chrome(scheme);
    }
    /** Keep fallback/loaded/failed grammar chrome stable; VS2019's stock teal gutter is too strong here. */
    private EditorColorScheme chrome(EditorColorScheme scheme) {
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, dark ? 0xff1e1e1e : 0xffffffff);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, dark ? 0xff1e1e1e : 0xffffffff);
        scheme.setColor(EditorColorScheme.LINE_NUMBER, dark ? 0xff858585 : 0xff707070);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, dark ? 0xffc5cedb : 0xff334155);
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, dark ? 0xff303846 : 0xffdbe2eb);
        scheme.setColor(EditorColorScheme.CURRENT_LINE, dark ? 0xff262626 : 0xfff3f3f3);
        scheme.setColor(EditorColorScheme.BLOCK_LINE, dark ? 0xff3a414c : 0xffd6dce5);
        scheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, dark ? 0xff6b778a : 0xff8190a4);
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, dark ? 0xffaecdff : 0xff005fb8);
        // Stock GitHub fallback uses saturated green/magenta. Keep loading/failure states
        // legible in the same restrained palette. TextMate dynamic token colors remain authoritative.
        if (!dark) {
            scheme.setColor(EditorColorScheme.TEXT_NORMAL, 0xff263445);
            scheme.setColor(EditorColorScheme.KEYWORD, 0xff7e4e94);
            scheme.setColor(EditorColorScheme.OPERATOR, 0xff536277);
            scheme.setColor(EditorColorScheme.LITERAL, 0xff8e4630);
            scheme.setColor(EditorColorScheme.COMMENT, 0xff617667);
            scheme.setColor(EditorColorScheme.FUNCTION_NAME, 0xff795e26);
            scheme.setColor(EditorColorScheme.IDENTIFIER_NAME, 0xff263445);
            scheme.setColor(EditorColorScheme.IDENTIFIER_VAR, 0xff315f79);
            scheme.setColor(EditorColorScheme.ANNOTATION, 0xff7e4e94);
        }
        return scheme;
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
