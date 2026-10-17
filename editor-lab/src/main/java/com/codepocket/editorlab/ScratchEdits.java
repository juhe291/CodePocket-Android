package com.codepocket.editorlab;

import io.github.rosemoe.sora.text.Content;

/** Shared touch-command guard for the two experiment widgets. UTF-16 offsets; no file access. */
public final class ScratchEdits {
    public static final int MAX_CHARS = 200_000;
    private ScratchEdits() {}

    public static boolean boundary(CharSequence text, int offset) {
        if (offset < 0 || offset > text.length()) return false;
        if (offset == 0 || offset == text.length()) return true;
        char before = text.charAt(offset - 1), after = text.charAt(offset);
        return !(Character.isHighSurrogate(before) && Character.isLowSurrogate(after)) &&
            !(before == '\r' && after == '\n');
    }

    public static boolean valid(CharSequence text, int start, int end, CharSequence replacement) {
        return replacement != null && start <= end && boundary(text, start) && boundary(text, end) &&
            (long) text.length() - (end - start) + replacement.length() <= MAX_CHARS;
    }

    public static boolean replace(Content content, int start, int end, String replacement) {
        if (!valid(content, start, end, replacement)) return false;
        content.beginBatchEdit();
        try { content.replace(start, end, replacement); }
        finally { content.endBatchEdit(); }
        return true;
    }
}
