package com.codepocket.ide.terminal

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.TextView

/**
 * Terminal view with ANSI escape sequence support.
 * Displays streaming output with colors and formatting.
 */
class TerminalView(context: Context) : TextView(context) {

    private val parser = AnsiParser()
    private val buffer = SpannableStringBuilder()
    private var maxLines = 1000

    init {
        typeface = Typeface.MONOSPACE
        setHorizontallyScrolling(false)
    }

    /**
     * Append text to terminal with ANSI parsing.
     * @param text Text to append (may contain ANSI escape sequences)
     */
    fun appendText(text: String) {
        val segments = parser.parse(text)

        for (segment in segments) {
            val start = buffer.length
            buffer.append(segment.text)
            val end = buffer.length

            // Apply styling
            segment.foregroundColor?.let {
                buffer.setSpan(ForegroundColorSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            segment.backgroundColor?.let {
                buffer.setSpan(BackgroundColorSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (segment.bold) {
                buffer.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (segment.italic) {
                buffer.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (segment.underline) {
                buffer.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // Trim to max lines
        trimToMaxLines()

        // Update view
        setText(buffer, BufferType.SPANNABLE)
    }

    /**
     * Clear terminal content.
     */
    fun setOutput(value: String) { clear(); appendText(value) }

    fun clear() {
        parser.reset(); buffer.clearSpans()
        buffer.clear()
        text = ""
    }

    /**
     * Set maximum number of lines to keep.
     * @param lines Maximum lines (default 1000)
     */
    fun setMaxBufferLines(lines: Int) {
        maxLines = lines.coerceIn(1, 10000)
        trimToMaxLines()
        setText(buffer, BufferType.SPANNABLE)
    }

    private fun trimToMaxLines() {
        if (buffer.length > 200000) {
            var cut = buffer.length - 200000
            if (cut < buffer.length && Character.isLowSurrogate(buffer[cut])) cut++
            buffer.delete(0, cut)
        }
        val text = buffer.toString()
        val lines = text.count { it == '\n' }

        if (lines > maxLines) {
            val toRemove = lines - maxLines
            var removeUpTo = 0
            var count = 0

            for (i in text.indices) {
                if (text[i] == '\n') {
                    count++
                    if (count == toRemove) {
                        removeUpTo = i + 1
                        break
                    }
                }
            }

            if (removeUpTo > 0) {
                buffer.delete(0, removeUpTo)
            }
        }
    }
}
