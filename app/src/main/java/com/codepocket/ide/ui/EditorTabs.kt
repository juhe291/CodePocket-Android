package com.codepocket.ide.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import com.codepocket.ide.animation.MotionEnvironment
import com.codepocket.ide.animation.MotionParticipant

/** Animate only the top indicator; all names, buttons and editor geometry stay put. */
@SuppressLint("ViewConstructor")
class EditorTabs(context: Context, private val palette: IdePalette) : LinearLayout(context), MotionParticipant {
    private val pen = Paint().apply { color = palette.accent }
    private var selected: View? = null
    private var pending = false
    private var aimLeft = Float.NaN
    private var aimRight = Float.NaN
    private var leftEdge = 0f
    private var rightEdge = 0f
    private var animator: ValueAnimator? = null
    fun select(tab: View?, animate: Boolean) {
        if (selected !== tab) { selected = tab; pending = animate }
        if (isLaidOut && !isLayoutRequested) updateIndicator()
    }
    private fun updateIndicator() {
        val target = selected?.takeIf { it.parent === this && it.width > 0 } ?: run { finishMotion(); return }
        val nextLeft = target.left.toFloat(); val nextRight = target.right.toFloat()
        if (!pending && animator?.isRunning == true && nextLeft == aimLeft && nextRight == aimRight) return
        aimLeft = nextLeft; aimRight = nextRight
        val fromLeft = leftEdge; val fromRight = rightEdge
        animator?.cancel(); animator = null
        if (pending && rightEdge > leftEdge && MotionEnvironment.enabled(context, true)) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 140; interpolator = DecelerateInterpolator()
                addUpdateListener { val t = it.animatedValue as Float
                    leftEdge = fromLeft + (nextLeft - fromLeft) * t; rightEdge = fromRight + (nextRight - fromRight) * t; invalidate() }
                start()
            }
        } else { leftEdge = nextLeft; rightEdge = nextRight; invalidate() }
        pending = false
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed || pending || animator?.isRunning != true) updateIndicator()
    }
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (selected?.parent === this) canvas.drawRect(leftEdge, 0f, rightEdge,
            IdeControls.dp(context, 2).toFloat(), pen)
    }
    override fun finishMotion() {
        animator?.cancel(); animator = null; pending = false
        selected?.takeIf { it.parent === this }?.let { leftEdge = it.left.toFloat(); rightEdge = it.right.toFloat(); aimLeft = leftEdge; aimRight = rightEdge }
        invalidate()
    }
    override fun onDetachedFromWindow() { finishMotion(); super.onDetachedFromWindow() }
}
