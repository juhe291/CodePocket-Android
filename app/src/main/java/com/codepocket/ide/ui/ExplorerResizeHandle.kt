package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent

/**
 * Vertical drag handle for resizing the explorer sidebar.
 * Placed between explorer and editor, dragging left/right adjusts width.
 */
@SuppressLint("ClickableViewAccessibility")
class ExplorerResizeHandle(context: Context) : View(context) {

    private var dragStart: Pair<Float, Int>? = null
    private var onDrag: ((delta: Float) -> Unit)? = null
    private var onFinish: (() -> Unit)? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "拖动调整资源管理器宽度"

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStart = event.rawX to width
                    parent?.requestDisallowInterceptTouchEvent(true)
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_TOUCH_INTERACTION_START)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    dragStart?.let { (startX, _) ->
                        val delta = event.rawX - startX
                        onDrag?.invoke(delta)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragStart != null) {
                        dragStart = null
                        parent?.requestDisallowInterceptTouchEvent(false)
                        onFinish?.invoke()
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_TOUCH_INTERACTION_END)
                    }
                    true
                }
                else -> false
            }
        }

        // Increase touch target for dragging
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    // Could show visual feedback
                    true
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    true
                }
                else -> false
            }
        }
    }

    fun setCallbacks(onDrag: (delta: Float) -> Unit, onFinish: () -> Unit) {
        this.onDrag = onDrag
        this.onFinish = onFinish
    }

    fun cancelGesture() {
        if (dragStart != null) {
            dragStart = null
            parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
}
