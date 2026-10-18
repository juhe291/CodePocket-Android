package com.codepocket.editorlab;

import com.codepocket.editor.surface.TextEdits;
import io.github.rosemoe.sora.text.Content;

/** Shared touch-command guard for the two experiment widgets. UTF-16 offsets; no file access. */
public final class ScratchEdits {
    public static final int MAX_CHARS = 200_000;
    private ScratchEdits() {}

    public static boolean boundary(CharSequence text, int offset) { return TextEdits.boundary(text, offset); }

    public static boolean valid(CharSequence text, int start, int end, CharSequence replacement) {
        if (replacement == null) return false;
        long caret = (long) start + replacement.length();
        if (caret > Integer.MAX_VALUE || caret < 0) return false;
        return TextEdits.check(text, -1, -1, start, end, replacement.toString(), (int) caret, (int) caret, MAX_CHARS).accepted();
    }
    public static boolean replace(Content content, int start, int end, String replacement) {
        if (!valid(content, start, end, replacement)) return false;
        content.beginBatchEdit();
        try { content.replace(start, end, replacement); }
        finally { content.endBatchEdit(); }
        return true;
    }
}
