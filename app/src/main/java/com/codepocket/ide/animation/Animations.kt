package com.codepocket.ide.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.codepocket.ide.R

/** Only decoration; never animates editor coordinates, layout parameters or PTY dimensions. */
object Animations {
    const val DURATION_SHORT = 150L
    const val DURATION_MEDIUM = 250L
    const val DURATION_LONG = 350L

    private class Motion(val visibility: Int, val rotation: Float) : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { cancel(view) }
    }

    /** Finish at the requested state, including a pending hide; old callbacks cannot hide a reopened view. */
    fun cancel(view: View) {
        val motion = view.getTag(R.id.workbench_motion) as? Motion ?: return
        view.setTag(R.id.workbench_motion, null)
        view.removeOnAttachStateChangeListener(motion)
        view.animate().setListener(null).withEndAction(null).withStartAction(null).cancel()
        view.alpha = 1f; view.translationX = 0f; view.rotation = motion.rotation
        view.visibility = motion.visibility
    }

    fun cancelTree(view: View) {
        cancel(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) cancelTree(view.getChildAt(i))
    }

    private fun allowed(enabled: Boolean) = enabled &&
        (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())

    private fun run(view: View, visibility: Int, rotation: Float, duration: Long,
        configure: (android.view.ViewPropertyAnimator) -> Unit) {
        val motion = Motion(visibility, rotation)
        view.setTag(R.id.workbench_motion, motion)
        view.addOnAttachStateChangeListener(motion)
        val animator = view.animate().setStartDelay(0).setDuration(duration.coerceAtLeast(0))
            .setInterpolator(DecelerateInterpolator()).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (view.getTag(R.id.workbench_motion) === motion) cancel(view)
                }
            })
        configure(animator); animator.start()
    }

    fun fadeIn(view: View, duration: Long = DURATION_SHORT, enabled: Boolean = true) {
        cancel(view); view.visibility = View.VISIBLE
        if (!allowed(enabled)) return
        view.alpha = .85f
        run(view, View.VISIBLE, view.rotation, duration) { it.alpha(1f) }
    }

    fun fadeOut(view: View, duration: Long = DURATION_SHORT, hideOnComplete: Boolean = true, enabled: Boolean = true) {
        cancel(view)
        val target = if (hideOnComplete) View.GONE else view.visibility
        if (!allowed(enabled) || view.visibility != View.VISIBLE) { view.visibility = target; return }
        run(view, target, view.rotation, duration) { it.alpha(0f) }
    }

    fun slideIn(view: View, fromX: Float, duration: Long = DURATION_MEDIUM, enabled: Boolean = true) {
        cancel(view); view.visibility = View.VISIBLE
        if (!allowed(enabled) || !fromX.isFinite()) return
        view.translationX = fromX
        run(view, View.VISIBLE, view.rotation, duration) { it.translationX(0f) }
    }

    fun slideOut(view: View, toX: Float, duration: Long = DURATION_MEDIUM,
        hideOnComplete: Boolean = true, enabled: Boolean = true) {
        cancel(view)
        val target = if (hideOnComplete) View.GONE else view.visibility
        if (!allowed(enabled) || !toX.isFinite() || view.visibility != View.VISIBLE) { view.visibility = target; return }
        run(view, target, view.rotation, duration) { it.translationX(toX) }
    }

    fun rotate(view: View, degrees: Float, enabled: Boolean) {
        cancel(view)
        if (!degrees.isFinite() || view.rotation == degrees) return
        if (!allowed(enabled)) { view.rotation = degrees; return }
        run(view, view.visibility, degrees, DURATION_SHORT) { it.rotation(degrees) }
    }

    /** New candidate batches update an open popup in place, without restarting its entrance on every key. */
    fun popup(view: View, alreadyShowing: Boolean, enabled: Boolean) {
        if (!enabled) cancel(view)
        else if (!alreadyShowing) fadeIn(view, 90, true)
    }
}
