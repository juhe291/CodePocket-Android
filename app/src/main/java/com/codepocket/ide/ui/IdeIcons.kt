package com.codepocket.ide.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Original 20dp line icons; no external icon font or emoji dependency. */
enum class IdeIcon { SEARCH, SAVE, UNDO, REDO, ADD_FILE, ADD_FOLDER, CLEAR, EXPLORER, MORE, RUN, STOP, CLOSE, MAXIMIZE, RESTORE, INPUT, FOLDER, FOLDER_OPEN, FILE, PYTHON, CPP }

class IdeIconDrawable(private val icon: IdeIcon, color: Int, private val size: Int) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.4f
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun setAlpha(alpha: Int) { pen.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { pen.colorFilter = filter; invalidateSelf() }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 20f, bounds.height() / 20f)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = canvas.drawLine(x, y, x2, y2, pen)
        fun path(vararg points: Float) {
            val p = Path(); p.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1])
            canvas.drawPath(p, pen)
        }
        when (icon) {
            IdeIcon.SEARCH -> { canvas.drawCircle(8f, 8f, 5f, pen); line(12f, 12f, 17f, 17f) }
            IdeIcon.SAVE -> { canvas.drawRect(3f, 3f, 17f, 17f, pen); canvas.drawRect(7f, 3f, 13f, 8f, pen); canvas.drawRect(6f, 12f, 14f, 17f, pen) }
            IdeIcon.UNDO -> { path(7f, 4f, 3f, 8f, 7f, 12f); path(3f, 8f, 12f, 8f, 16f, 11f, 16f, 16f) }
            IdeIcon.REDO -> { path(13f, 4f, 17f, 8f, 13f, 12f); path(17f, 8f, 8f, 8f, 4f, 11f, 4f, 16f) }
            IdeIcon.CLEAR -> { path(4f, 13f, 12f, 3f, 18f, 8f, 10f, 17f, 7f, 17f, 4f, 13f); line(10f, 17f, 18f, 17f) }
            IdeIcon.EXPLORER -> { canvas.drawRect(6f, 5f, 17f, 17f, pen); path(13f, 5f, 13f, 2f, 3f, 2f, 3f, 14f, 6f, 14f) }
            IdeIcon.MORE -> { pen.style = Paint.Style.FILL; for (y in listOf(4f, 10f, 16f)) canvas.drawCircle(10f, y, 1.2f, pen); pen.style = Paint.Style.STROKE }
            IdeIcon.RUN -> path(5f, 3f, 17f, 10f, 5f, 17f, 5f, 3f)
            IdeIcon.STOP -> canvas.drawRect(4f, 4f, 16f, 16f, pen)
            IdeIcon.CLOSE -> { line(5f, 5f, 15f, 15f); line(5f, 15f, 15f, 5f) }
            IdeIcon.MAXIMIZE -> canvas.drawRect(4f, 4f, 16f, 16f, pen)
            IdeIcon.RESTORE -> { canvas.drawRect(3f, 7f, 13f, 17f, pen); path(7f, 7f, 7f, 3f, 17f, 3f, 17f, 13f, 13f, 13f) }
            IdeIcon.INPUT -> { canvas.drawRoundRect(2f, 5f, 18f, 16f, 1f, 1f, pen); path(5f, 8f, 8f, 10.5f, 5f, 13f); line(10f, 13f, 15f, 13f) }
            IdeIcon.ADD_FOLDER, IdeIcon.FOLDER, IdeIcon.FOLDER_OPEN -> {
                path(2f, 16f, 2f, 5f, 8f, 5f, 10f, 7f, 18f, 7f, 18f, 16f, 2f, 16f)
                if (icon == IdeIcon.ADD_FOLDER) { line(10f, 10f, 10f, 14f); line(8f, 12f, 12f, 12f) }
                if (icon == IdeIcon.FOLDER_OPEN) path(2f, 16f, 5f, 10f, 18f, 10f)
            }
            else -> {
                path(4f, 18f, 4f, 2f, 12f, 2f, 16f, 6f, 16f, 18f, 4f, 18f)
                path(12f, 2f, 12f, 6f, 16f, 6f)
                when (icon) {
                    IdeIcon.ADD_FILE -> { line(10f, 9f, 10f, 15f); line(7f, 12f, 13f, 12f) }
                    IdeIcon.CPP -> { path(8f, 10f, 6f, 12f, 8f, 14f); path(12f, 10f, 14f, 12f, 12f, 14f) }
                    IdeIcon.PYTHON -> { path(7f, 10f, 11f, 10f, 11f, 12f, 8f, 12f, 8f, 14f, 12f, 14f) }
                    else -> { line(7f, 10f, 13f, 10f); line(7f, 14f, 13f, 14f) }
                }
            }
        }
        canvas.restoreToCount(checkpoint)
    }
    companion object {
        fun file(path: String) = when (path.substringAfterLast('.', "").lowercase()) {
            "py" -> IdeIcon.PYTHON
            "cpp", "h", "hpp", "cc", "cxx" -> IdeIcon.CPP
            else -> IdeIcon.FILE
        }
    }
}
