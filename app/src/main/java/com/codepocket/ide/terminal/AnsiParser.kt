package com.codepocket.ide.terminal

/** Read-only SGR styling, not a terminal emulator. Unsupported CSI controls are ignored without eating following text. */
class AnsiParser {
    data class ParsedSegment(val text: String, val foregroundColor: Int? = null, val backgroundColor: Int? = null,
        val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)
    private var fg: Int? = null
    private var bg: Int? = null
    private var bold = false
    private var italic = false
    private var underline = false
    private var pending = ""
    fun reset() { fg = null; bg = null; bold = false; italic = false; underline = false; pending = "" }
    fun parse(text: String): List<ParsedSegment> {
        val input = pending + text; pending = ""
        val segments = mutableListOf<ParsedSegment>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { segments.add(ParsedSegment(plain.toString(), fg, bg, bold, italic, underline)); plain.clear() } }
        var i = 0
        while (i < input.length) {
            if (input[i] != '\u001b') { plain.append(input[i++]); continue }
            flush()
            if (i + 1 >= input.length) { pending = input.substring(i); break }
            if (input[i + 1] != '[') { i++; continue }
            var end = i + 2
            while (end < input.length && input[end] !in '@'..'~' && end - i < 128) end++
            if (end == input.length) {
                if (end - i < 128) pending = input.substring(i)
                break
            }
            if (end - i >= 128) { i = end; continue }
            if (input[end] == 'm') {
                val parameters = input.substring(i + 2, end)
                if (parameters.all { it.isDigit() || it == ';' }) codes(parameters.split(';'))
            }
            i = end + 1
        }
        flush()
        return segments
    }
    private fun codes(values: List<String>) {
        for (text in values) when (val code = if (text.isEmpty()) 0 else text.toIntOrNull()) {
            0 -> { fg = null; bg = null; bold = false; italic = false; underline = false }
            1 -> bold = true
            3 -> italic = true
            4 -> underline = true
            22 -> bold = false
            23 -> italic = false
            24 -> underline = false
            in 30..37 -> fg = colors[code!! - 30]
            in 40..47 -> bg = colors[code!! - 40]
            39 -> fg = null
            49 -> bg = null
        }
    }
    companion object {
        private val colors = intArrayOf(0xFF000000.toInt(), 0xFFE74856.toInt(), 0xFF16C60C.toInt(), 0xFFF9F1A5.toInt(),
            0xFF3B78FF.toInt(), 0xFFB4009E.toInt(), 0xFF61D6D6.toInt(), 0xFFCCCCCC.toInt())
    }
}
