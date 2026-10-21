package com.codepocket.ide

/** Editing rules independent of Android input methods, so they can be regression tested. */
object EditorActions {
    fun indent(code: String, anchor: Int, caret: Int, reverse: Boolean, tabWidth: Int = 4, useSpaces: Boolean = true): TextChange {
        val width = tabWidth.takeIf { it in listOf(2, 4, 8) } ?: 4
        val unit = if (useSpaces) " ".repeat(width) else "\t"
        val start = minOf(anchor, caret).coerceIn(0, code.length)
        val end = maxOf(anchor, caret).coerceIn(start, code.length)
        if (start == end && !reverse) return TextChange(start, end, unit, start + unit.length)
        val lineStart = if (start == 0) 0 else code.lastIndexOf('\n', start - 1) + 1
        if (start == end && reverse) {
            var count = 0
            if (code.getOrNull(lineStart) == '\t') count = 1
            else while (count < width && code.getOrNull(lineStart + count) == ' ') count++
            return TextChange(lineStart, lineStart + count, "", (start - count).coerceAtLeast(lineStart))
        }
        // A selection ending at the next line's start does not include that line.
        val segmentEnd = if (end > start && code.getOrNull(end - 1) == '\n')
            end - if (end > 1 && code[end - 2] == '\r') 2 else 1 else end
        val old = code.substring(lineStart, segmentEnd)
        fun trim(line: String): String = if (line.startsWith('\t')) line.drop(1)
            else line.drop(line.takeWhile { it == ' ' }.length.coerceAtMost(width))
        val replacement = old.split('\n').joinToString("\n") { if (reverse) trim(it) else "$unit$it" }
        return TextChange(lineStart, segmentEnd, replacement, lineStart, lineStart + replacement.length)
    }

    /** Called after a single typed character; indices of the result refer to the changed text. */
    fun typed(before: String, start: Int, removed: Int, inserted: String, cpp: Boolean, tabWidth: Int = 4, useSpaces: Boolean = true,
        autoIndent: Boolean = true, autoPairs: Boolean = true, fromHistory: Boolean = false): TextChange? {
        // History restores an exact prior buffer; do not treat it as newly typed code.
        if (fromHistory || inserted.length != 1) return null
        val ch = inserted[0]
        if ((ch == '\n' && !autoIndent) || (ch != '\n' && !autoPairs)) return null
        val width = tabWidth.takeIf { it in listOf(2, 4, 8) } ?: 4
        val unit = if (useSpaces) " ".repeat(width) else "\t"
        if (ch !in "\n()[]{}\"'") return null
        if (before.length > 45000 && ch != '\n') return null
        val literal = (if (before.length <= 45000) EditorFeatures.tokens(before, cpp) else emptyList()).firstOrNull {
            start > it.start && start <= it.end && it.kind in setOf("string", "comment")
        }
        val insideLiteral = literal != null
        if (removed == 0 && ch in ")]}\"'" && before.getOrNull(start) == ch &&
            (!insideLiteral || (literal?.kind == "string" && ch in "\"'" && before.getOrNull(start - 1) != '\\'))) {
            return TextChange(start, start + 1, "", start + 1)
        }
        if (ch == '\n') {
            val lineStart = if (start == 0) 0 else before.lastIndexOf('\n', start - 1) + 1
            val left = before.substring(lineStart, start)
            val base = left.takeWhile { it == ' ' || it == '\t' }
            val extra = !insideLiteral && (left.trimEnd().endsWith("{") || (!cpp && left.trimEnd().endsWith(":")))
            val indentation = base + if (extra) unit else ""
            val tail = if (extra && before.getOrNull(start) == '}') "\n$base" else ""
            return TextChange(start, start + 1, "\n$indentation$tail", start + 1 + indentation.length)
        }
        val closing = when (ch) { '(' -> ')'; '[' -> ']'; '{' -> '}'; '\"' -> '\"'; '\'' -> '\''; else -> return null }
        if (insideLiteral || (ch in "\"'" && before.getOrNull(start - 1)?.let { it.isLetterOrDigit() || it == '_' } == true)) return null
        val selected = before.substring(start, start + removed)
        return TextChange(start, start + 1, "$ch$selected$closing", start + 1 + selected.length)
    }
}
