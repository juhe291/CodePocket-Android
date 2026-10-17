package com.codepocket.ide.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Decoration only. Callers also pass the App/system/power-save/IME motion policy. */
object Animations {
    const val DURATION_SHORT = 150L
    const val DURATION_MEDIUM = 250L
    const val DURATION_LONG = 350L
    fun cancel(view: View) {
        // Remove the old end callback before cancel: cancelling must not hide a newly reopened view.
        view.animate().setListener(null); view.animate().cancel()
        view.alpha = 1f; view.translationX = 0f
    }
    private fun allowed(enabled: Boolean) = enabled && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
    fun fadeIn(view: View, duration: Long = DURATION_SHORT, enabled: Boolean = true) {
        cancel(view); view.visibility = View.VISIBLE
        if (!allowed(enabled)) return
        view.alpha = .85f
        view.animate().alpha(1f).setDuration(duration).setInterpolator(DecelerateInterpolator()).start()
    }
    fun fadeOut(view: View, duration: Long = DURATION_SHORT, hideOnComplete: Boolean = true, enabled: Boolean = true) {
        cancel(view)
        if (!allowed(enabled)) { if (hideOnComplete) view.visibility = View.GONE; return }
        view.animate().alpha(0f).setDuration(duration).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.animate().setListener(null); if (hideOnComplete) view.visibility = View.GONE; view.alpha = 1f
            }
        }).start()
    }
    fun slideIn(view: View, fromX: Float, duration: Long = DURATION_MEDIUM, enabled: Boolean = true) {
        cancel(view); view.visibility = View.VISIBLE
        if (!allowed(enabled) || !fromX.isFinite()) return
        view.translationX = fromX
        view.animate().translationX(0f).setDuration(duration).setInterpolator(DecelerateInterpolator()).start()
    }
    fun slideOut(view: View, toX: Float, duration: Long = DURATION_MEDIUM,
        hideOnComplete: Boolean = true, enabled: Boolean = true) {
        cancel(view)
        if (!allowed(enabled) || !toX.isFinite()) { if (hideOnComplete) view.visibility = View.GONE; return }
        view.animate().translationX(toX).setDuration(duration).setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate().setListener(null)
                    if (hideOnComplete) view.visibility = View.GONE
                    view.translationX = 0f; view.alpha = 1f
                }
            }).start()
    }
}
