package com.codepocket.editorlab;

/** Grammar selection is independent of the draft: never replace text on language changes. */
enum SyntaxLanguage {
    PYTHON("Python", "source.python"), CPP("C++17", "source.cpp"), PLAIN("纯文本", null);
    final String label, scope;
    SyntaxLanguage(String label, String scope) { this.label = label; this.scope = scope; }
    static final int MAX_HIGHLIGHT_CHARS = 45_000;
    SyntaxLanguage effective(int length) { return length > MAX_HIGHLIGHT_CHARS ? PLAIN : this; }
}
