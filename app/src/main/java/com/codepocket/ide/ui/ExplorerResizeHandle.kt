package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Owns only its 24dp divider; total deltas are measured from a stable gesture start. */
@SuppressLint("ClickableViewAccessibility")
class ExplorerResizeHandle(context: Context) : View(context) {
    private var origin: Float? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var moved = false
    private var begin: () -> Unit = {}
    private var drag: (Float) -> Unit = {}
    private var finish: () -> Unit = {}
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF777777.toInt() }
    init {
        isFocusable = true; isClickable = true
        contentDescription = "调整资源管理器宽度；左右方向键调整，点击恢复默认"
        pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
    }
    fun setCallbacks(onDrag: (Float) -> Unit, onFinish: () -> Unit, onBegin: () -> Unit = {}) {
        cancelGesture(); drag = onDrag; finish = onFinish; begin = onBegin
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pen.strokeWidth = resources.displayMetrics.density * if (isPressed || hasFocus()) 3f else 1f
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), pen)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) { cancelGesture(); return false }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { origin = event.rawX; moved = false; begin(); isPressed = true
                parent?.requestDisallowInterceptTouchEvent(true); invalidate(); return true }
            MotionEvent.ACTION_MOVE -> { val start = origin ?: return false
                val delta = event.rawX - start
                if (abs(delta) > slop) moved = true
                if (moved) drag(delta)
                return true }
            MotionEvent.ACTION_UP -> { val click = origin != null && !moved; cancelGesture(); if (click) performClick(); return true }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> { cancelGesture(); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled && keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            begin()
            val step = IdeControls.dp(context, 24).toFloat()
            // Round a positive distance before applying direction: dp(-24) truncates to -23 at mdpi.
            drag(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -step else step)
            finish(); return true
        }
        return super.onKeyDown(keyCode, event)
    }
    fun cancelGesture() {
        if (origin != null) { origin = null; finish() }
        isPressed = false; parent?.requestDisallowInterceptTouchEvent(false); invalidate()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }
}
