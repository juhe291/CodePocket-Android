package com.codepocket.ide

data class EditorSnapshot(val key: String, val revision: Long, val text: String, val start: Int, val end: Int) {
    // Text is checked as well: reloading an external file may keep the same revision counter.
    fun sameBuffer(other: EditorSnapshot) = key == other.key && revision == other.revision && text == other.text
}

data class Replacement(val change: TextChange, val count: Int)

/** Literal search: user input is never executed as a regular expression. UTF-16 editor offsets. */
object EditorSearch {
    private fun boundary(text: String, start: Int, end: Int): Boolean {
        fun word(cp: Int) = Character.isLetterOrDigit(cp) || cp == '_'.code
        return (start == 0 || !word(Character.codePointBefore(text, start))) &&
            (end == text.length || !word(Character.codePointAt(text, end)))
    }
    fun find(text: String, query: String, from: Int, backwards: Boolean = false,
        ignoreCase: Boolean = false, wholeWord: Boolean = false): IntRange? {
        if (query.isEmpty() || query.length > text.length) return null
        fun scan(begin: Int, end: Int): IntRange? {
            var at = begin
            while (if (backwards) at >= end else at <= end) {
                val hit = if (backwards) text.lastIndexOf(query, at, ignoreCase) else text.indexOf(query, at, ignoreCase)
                if (hit < 0 || (if (backwards) hit < end else hit > end)) return null
                val limit = hit + query.length
                if (!wholeWord || boundary(text, hit, limit)) return hit until limit
                at = hit + if (backwards) -1 else 1
            }
            return null
        }
        val start = from.coerceIn(-1, text.length)
        return if (backwards) scan(start, 0) ?: scan(text.length, start + 1)
            else scan(start.coerceAtLeast(0), text.length) ?: scan(0, start - 1)
    }
    fun replaceSelection(text: String, query: String, replacement: String, start: Int, end: Int,
        ignoreCase: Boolean = false, wholeWord: Boolean = false): TextChange? {
        val left = minOf(start, end); val right = maxOf(start, end)
        if (left < 0 || right > text.length || query.isEmpty() || right - left != query.length ||
            !text.regionMatches(left, query, 0, query.length, ignoreCase) || (wholeWord && !boundary(text, left, right))) return null
        require(text.length.toLong() - (right - left) + replacement.length <= Workspace.MAX_CHARS) { "替换后超过 20 万字符，未修改内容" }
        return TextChange(left, right, replacement, left + replacement.length)
    }
    fun replaceAll(text: String, query: String, replacement: String, ignoreCase: Boolean = false,
        wholeWord: Boolean = false): Replacement? {
        if (query.isEmpty()) return null
        val hits = mutableListOf<Int>()
        var at = 0
        while (at <= text.length - query.length) {
            val hit = text.indexOf(query, at, ignoreCase)
            if (hit < 0) break
            if (!wholeWord || boundary(text, hit, hit + query.length)) hits.add(hit)
            at = hit + query.length
        }
        if (hits.isEmpty()) return null
        val length = text.length.toLong() + hits.size.toLong() * (replacement.length.toLong() - query.length)
        require(length <= Workspace.MAX_CHARS) { "替换后超过 20 万字符，未修改内容" }
        val changed = buildString(length.toInt()) {
            var cursor = 0
            for (hit in hits) { append(text, cursor, hit); append(replacement); cursor = hit + query.length }
            append(text, cursor, text.length)
        }
        return Replacement(TextChange(0, text.length, changed, 0), hits.size)
    }
    fun lineOffset(text: String, oneBasedLine: Int): Int? {
        if (oneBasedLine < 1) return null
        var cursor = 0
        repeat(oneBasedLine - 1) {
            val next = text.indexOf('\n', cursor)
            if (next < 0) return null
            cursor = next + 1
        }
        return cursor
    }
    fun gutter(text: String, visualStarts: List<Int>): String {
        var logical = 1
        return visualStarts.take(10000).mapIndexed { index, offset ->
            if (index == 0) "1" else if (offset > 0 && text.getOrNull(offset - 1) == '\n') (++logical).toString() else ""
        }.joinToString("\n")
    }
}
