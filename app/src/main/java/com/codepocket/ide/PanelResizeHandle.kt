package com.codepocket.ide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/** Only the divider owns this gesture; never intercepts editor or terminal input. */
class PanelResizeHandle @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private var begin: () -> Unit = {}
    private var resize: (Float) -> Unit = {}
    private var finish: () -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.GRAY; strokeCap = Paint.Cap.ROUND }
    fun setCallbacks(color: Int, begin: () -> Unit, resize: (Float) -> Unit, finish: () -> Unit) {
        cancelGesture(); paint.color = color
        this.begin = begin; this.resize = resize; this.finish = finish
    }
    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var side = false
    private var extent = 0
    private var minimum = 0
    private var maximum = 0
    private var available = 0
    private var pointer = MotionEvent.INVALID_POINTER_ID
    private var startPosition = 0f
    private var startExtent = 0
    private var moved = false
    init { isFocusable = true; isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun configure(dock: PanelDock, current: Int, lower: Int, upper: Int, total: Int) {
        val nextSide = dock == PanelDock.SIDE
        if (side != nextSide || available != total) cancelGesture()
        side = nextSide; extent = current; minimum = lower; maximum = upper; available = total
        contentDescription = context.getString(if (side) R.string.panel_resize_width else R.string.panel_resize_height)
        pointerIcon = PointerIcon.getSystemIcon(context, if (side) PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW else PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.strokeWidth = (if (isPressed || hasFocus()) 3f else 2f) * density
        val half = minOf(22 * density, (if (side) height else width) / 3f)
        if (side) canvas.drawLine(width / 2f, height / 2f - half, width / 2f, height / 2f + half, paint)
        else canvas.drawLine(width / 2f - half, height / 2f, width / 2f + half, height / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || available <= 0) { cancelGesture(); return false }
        fun coordinate(index: Int) = if (side) event.getX(index) + event.rawX - event.x else event.getY(index) + event.rawY - event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = event.getPointerId(0); startPosition = coordinate(0); startExtent = extent; moved = false
                isPressed = true; parent.requestDisallowInterceptTouchEvent(true); begin(); invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index < 0) { cancelGesture(); return false }
                val delta = coordinate(index) - startPosition
                if (abs(delta) > slop) moved = true
                if (moved) resize(PanelLayoutPolicy.drag(startExtent, delta, minimum, maximum) / available)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val click = pointer != MotionEvent.INVALID_POINTER_ID && !moved
                cancelGesture(); if (click) performClick(); return true
            }
            MotionEvent.ACTION_CANCEL -> { cancelGesture(); return true }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointer) cancelGesture()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> return true
        }
        return super.onTouchEvent(event)
    }

    fun cancelGesture() {
        if (pointer == MotionEvent.INVALID_POINTER_ID) return
        pointer = MotionEvent.INVALID_POINTER_ID; isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false); finish(); invalidate()
    }

    override fun performClick(): Boolean = super.performClick()
    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }

    private fun adjust(fraction: Float): Boolean {
        if (!isEnabled || !isShown || available <= 0 || !fraction.isFinite()) return false
        begin(); resize(fraction.coerceIn(minimum.toFloat() / available, maximum.toFloat() / available)); finish()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && available > 0) {
            val delta = when (keyCode) {
                KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS -> .05f
                KeyEvent.KEYCODE_MINUS -> -.05f
                KeyEvent.KEYCODE_DPAD_LEFT -> if (side) .05f else 0f
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (side) -.05f else 0f
                KeyEvent.KEYCODE_DPAD_UP -> if (!side) .05f else 0f
                KeyEvent.KEYCODE_DPAD_DOWN -> if (!side) -.05f else 0f
                else -> 0f
            }
            if (delta != 0f && adjust(extent.toFloat() / available + delta)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
        if (available > 0 && maximum >= minimum) {
            val lower = 100f * minimum / available; val upper = 100f * maximum / available; val current = 100f * extent / available
            info.rangeInfo = if (Build.VERSION.SDK_INT >= 30)
                AccessibilityNodeInfo.RangeInfo(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, lower, upper, current)
            else {
                @Suppress("DEPRECATION") // Constructor requires API 30; retain API 24-29 support.
                AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, lower, upper, current)
            }
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (available > 0) {
            val fraction = when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> extent.toFloat() / available + .05f
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> extent.toFloat() / available - .05f
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id ->
                    (arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, Float.NaN) ?: Float.NaN) / 100f
                else -> Float.NaN
            }
            if (fraction.isFinite() && adjust(fraction)) return true
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
