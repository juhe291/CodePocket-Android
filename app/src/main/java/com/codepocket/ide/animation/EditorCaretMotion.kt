package com.codepocket.ide.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.inputmethod.BaseInputConnection
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.codepocket.ide.CodeEditor
import kotlin.math.abs
import kotlin.math.cos

/** Cursor pixels only. Android EditText keeps text, handles, hit tests, IME and its real insertion position. */
class EditorCaretMotion(private val editor: CodeEditor) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var enabled = false
    private var move = true
    private var blink = true
    private var snap = true
    private var target: RectF? = null
    private var current: RectF? = null
    private var lastDrawn: RectF? = null
    private var movement: ValueAnimator? = null
    private var blinking: ValueAnimator? = null
    private var opacity = 1f
    private val beginBlink = Runnable {
        if (eligible() && enabled && blink && !composing()) {
            blinking = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { opacity = (.15 + .85 * (.5 + .5 * cos(2 * Math.PI * (it.animatedValue as Float)))).toFloat(); redraw() }
                start()
            }
        }
    }
    fun configure(enabled: Boolean, move: Boolean, blink: Boolean, color: Int) {
        this.enabled = enabled; this.move = move; this.blink = blink; paint.color = color
        reset()
    }
    private fun composing() = BaseInputConnection.getComposingSpanStart(editor.text) >= 0
    private fun eligible() = editor.isAttachedToWindow && editor.isShown && editor.hasFocus() && editor.hasWindowFocus() &&
        editor.isCursorVisible && editor.selectionStart >= 0 && editor.selectionStart == editor.selectionEnd && editor.isEnabled
    private fun damageBounds(): RectF? {
        val area = current ?: lastDrawn ?: return null
        return RectF(area).apply { target?.let(::union); lastDrawn?.let(::union) }
    }
    private fun redraw() {
        val area = damageBounds() ?: return
        // Include the LAST drawn pixels as well as the new position: otherwise a moving caret can leave a trail.
        editor.postInvalidateOnAnimation((area.left - editor.scrollX - 3).toInt(),
            (area.top - editor.scrollY - 3).toInt(),
            (area.right - editor.scrollX + 3).toInt(),
            (area.bottom - editor.scrollY + 3).toInt())
    }
    private fun stopBlink() {
        editor.removeCallbacks(beginBlink); blinking?.cancel(); blinking = null; opacity = 1f
    }
    fun reset() {
        snap = true; movement?.cancel(); movement = null; stopBlink()
        target?.let { current = RectF(it) }; redraw()
        if (eligible() && enabled && blink && !composing()) editor.postDelayed(beginBlink, 600)
    }
    fun selectionChanged(start: Int, end: Int) {
        stopBlink()
        if (start != end) { reset(); return }
        if (eligible() && enabled && blink && !composing()) editor.postDelayed(beginBlink, 600)
        redraw()
    }
    fun textChanged() { reset() }
    fun stop() {
        movement?.cancel(); movement = null; stopBlink(); snap = true
        if (lastDrawn != null) { redraw(); lastDrawn = null }
    }

    /** Content coordinates, same Layout/UTF16 offset used by native drawing; independent of animation. */
    fun logicalBounds(): RectF? {
        val layout = editor.layout ?: return null
        val offset = editor.selectionStart
        if (offset !in 0..editor.length() || offset != editor.selectionEnd) return null
        val line = layout.getLineForOffset(offset)
        val available = editor.height - editor.extendedPaddingTop - editor.extendedPaddingBottom
        val extra = (available - layout.height).coerceAtLeast(0)
        val vertical = when (editor.gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.CENTER_VERTICAL -> extra / 2
            Gravity.BOTTOM -> extra
            else -> 0
        }
        val x = editor.compoundPaddingLeft + layout.getPrimaryHorizontal(offset)
        val top = editor.extendedPaddingTop + vertical + layout.getLineTop(line)
        val bottom = editor.extendedPaddingTop + vertical + layout.getLineBottom(line)
        if (!x.isFinite() || bottom <= top) return null
        val width = maxOf(1f, 2 * editor.resources.displayMetrics.density)
        return RectF(x, top.toFloat(), x + width, bottom.toFloat())
    }
    fun draw(canvas: Canvas) {
        if (!eligible()) { stop(); return }
        val next = logicalBounds() ?: return
        if (target != next) {
            val previous = current
            target = RectF(next)
            movement?.cancel(); movement = null
            val shortMove = previous != null && abs(previous.top - next.top) <= next.height() * 2 &&
                abs(previous.left - next.left) <= editor.width
            if (!snap && enabled && move && shortMove && !composing()) {
                val from = RectF(previous!!)
                movement = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 80; interpolator = DecelerateInterpolator()
                    addUpdateListener { val t = it.animatedValue as Float
                        current = RectF(from.left + (next.left - from.left) * t, from.top + (next.top - from.top) * t,
                            from.right + (next.right - from.right) * t, from.bottom + (next.bottom - from.bottom) * t); redraw() }
                    start()
                }
            } else current = RectF(next)
            snap = false
        }
        val caret = current ?: next
        val checkpoint = canvas.save()
        canvas.clipRect(editor.scrollX + editor.compoundPaddingLeft, editor.scrollY + editor.extendedPaddingTop,
            editor.scrollX + editor.width - editor.compoundPaddingRight, editor.scrollY + editor.height - editor.extendedPaddingBottom)
        paint.alpha = (255 * opacity).toInt()
        canvas.drawRect(caret, paint); lastDrawn = RectF(caret); canvas.restoreToCount(checkpoint)
    }
}
