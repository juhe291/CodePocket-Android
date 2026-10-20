package com.codepocket.ide.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.inputmethod.BaseInputConnection
import com.codepocket.ide.CodeEditor

/** Decorations only: draw visible waves, never insert text/spans or change selection/layout. */
class EditorIssueLayer(private val editor: CodeEditor) {
    var report = SyntaxReport()
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFE06C75.toInt() }
    private val path = Path()
    fun update(value: SyntaxReport, dark: Boolean) {
        report = value.copy(issues = value.issues.filter { it.start >= 0 && it.end >= it.start && it.end <= editor.length() }.take(40))
        paint.color = if (dark) 0xFFE28C93.toInt() else 0xFFBA303F.toInt()
        editor.invalidate()
    }
    fun clear() { report = SyntaxReport(); editor.invalidate() }
    fun at(offset: Int): InlineIssue? = report.issues.firstOrNull { offset >= it.start && offset <= it.end }
    fun draw(canvas: Canvas) {
        if (report.issues.isEmpty() || BaseInputConnection.getComposingSpanStart(editor.text) >= 0) return
        val layout = editor.layout ?: return
        val density = editor.resources.displayMetrics.density
        val viewportTop = editor.scrollY
        val firstVisible = layout.getLineForVertical((viewportTop - editor.totalPaddingTop).coerceAtLeast(0))
        val lastVisible = layout.getLineForVertical((viewportTop + editor.height - editor.totalPaddingTop).coerceAtLeast(0))
        paint.strokeWidth = density.coerceAtLeast(1f)
        val save = canvas.save()
        canvas.clipRect(editor.scrollX + editor.totalPaddingLeft, viewportTop + editor.totalPaddingTop,
            editor.scrollX + editor.width - editor.totalPaddingRight, viewportTop + editor.height - editor.totalPaddingBottom)
        for (issue in report.issues) {
            val startLine = layout.getLineForOffset(issue.start)
            val lastOffset = if (issue.end > issue.start) issue.end - 1 else issue.start
            val endLine = layout.getLineForOffset(lastOffset)
            for (line in maxOf(startLine, firstVisible)..minOf(endLine, lastVisible)) {
                val start = maxOf(issue.start, layout.getLineStart(line))
                val end = minOf(issue.end, layout.getLineVisibleEnd(line))
                var left = layout.getPrimaryHorizontal(start) + editor.totalPaddingLeft
                var right = layout.getPrimaryHorizontal(maxOf(start, end)) + editor.totalPaddingLeft
                if (right < left) { val swap = left; left = right; right = swap }
                right = maxOf(right, left + 6 * density)
                // Clamp before building the path: a long unwrapped line must not allocate a huge wave.
                left = maxOf(left, (editor.scrollX + editor.totalPaddingLeft).toFloat())
                right = minOf(right, (editor.scrollX + editor.width - editor.totalPaddingRight).toFloat())
                if (right <= left) continue
                val y = minOf(layout.getLineBottom(line) - density, layout.getLineBaseline(line) + 2.5f * density) + editor.totalPaddingTop
                path.rewind(); path.moveTo(left, y)
                var x = left; var up = true
                while (x < right) { x = minOf(right, x + 2 * density); path.lineTo(x, y + if (up) -density else density); up = !up }
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(save)
    }
}
