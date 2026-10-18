package com.codepocket.editor.surface;

/** Widget-independent UTF-16 edit validation. No document copies or Android dependencies. */
public final class TextEdits {
    private TextEdits() {}
    public enum Result {
        APPLIED, SELECTION_ONLY, UNCHANGED, NO_DOCUMENT, STALE, COMPOSING,
        INVALID_RANGE, INVALID_TEXT, LIMIT_EXCEEDED;
        public boolean accepted() { return this == APPLIED || this == SELECTION_ONLY || this == UNCHANGED; }
    }

    public static Result check(CharSequence code, int oldStart, int oldEnd, int start, int end,
            String text, int selectionStart, int selectionEnd, int maxChars) {
        if (start < 0 || end < start || end > code.length() || !boundary(code, start) || !boundary(code, end))
            return Result.INVALID_RANGE;
        if (text == null) return Result.INVALID_TEXT;
        long size = (long) code.length() - (end - start) + text.length();
        if (maxChars < 0 || size > maxChars) return Result.LIMIT_EXCEEDED;
        if (!validUtf16(text)) return Result.INVALID_TEXT;
        if (selectionStart < 0 || selectionEnd < 0 || selectionStart > size || selectionEnd > size)
            return Result.INVALID_RANGE;
        if (!newBoundary(code, start, end, text, (int) size, selectionStart) ||
                !newBoundary(code, start, end, text, (int) size, selectionEnd)) return Result.INVALID_RANGE;
        boolean same = text.length() == end - start;
        for (int i = 0; same && i < text.length(); i++) same = code.charAt(start + i) == text.charAt(i);
        if (!same) return Result.APPLIED;
        return selectionStart != oldStart || selectionEnd != oldEnd ? Result.SELECTION_ONLY : Result.UNCHANGED;
    }

    public static boolean boundary(CharSequence text, int at) {
        return at >= 0 && at <= text.length() && (at == 0 || at == text.length() || !split(text.charAt(at - 1), text.charAt(at)));
    }
    private static boolean split(char left, char right) {
        return (Character.isHighSurrogate(left) && Character.isLowSurrogate(right)) || (left == '\r' && right == '\n');
    }
    private static boolean newBoundary(CharSequence code, int start, int end, String text, int size, int at) {
        return at == 0 || at == size || !split(character(code, start, end, text, at - 1), character(code, start, end, text, at));
    }
    private static char character(CharSequence code, int start, int end, String text, int at) {
        if (at < start) return code.charAt(at);
        if (at - start < text.length()) return text.charAt(at - start);
        return code.charAt(at - text.length() + (end - start));
    }
    public static boolean validUtf16(CharSequence text) {
        if (text == null) return false;
        int at = 0;
        while (at < text.length()) {
            char ch = text.charAt(at++);
            if (Character.isHighSurrogate(ch)) {
                if (at == text.length() || !Character.isLowSurrogate(text.charAt(at++))) return false;
            } else if (Character.isLowSurrogate(ch)) return false;
        }
        return true;
    }

    /** Restore a saved selection without splitting a pair; direction is retained. */
    public static int[] restoreSelection(CharSequence text, int start, int end) {
        int a = Math.max(0, Math.min(start, text.length())), b = Math.max(0, Math.min(end, text.length()));
        if (a == b) { int floor = boundary(text, a) ? a : a - 1; return new int[] {floor, floor}; }
        if (a < b) return new int[] {boundary(text, a) ? a : a - 1, boundary(text, b) ? b : b + 1};
        return new int[] {boundary(text, a) ? a : a + 1, boundary(text, b) ? b : b - 1};
    }
}
