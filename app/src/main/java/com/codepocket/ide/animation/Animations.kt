package com.codepocket.ide.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Animation utilities for CodePocket UI transitions.
 * All animations respect system settings and app preferences.
 */
object Animations {

    const val DURATION_SHORT = 150L
    const val DURATION_MEDIUM = 250L
    const val DURATION_LONG = 350L

    /**
     * Fade in a view.
     * @param enabled Whether animations are enabled
     */
    fun fadeIn(view: View, duration: Long = DURATION_SHORT, enabled: Boolean = true) {
        if (!enabled) {
            view.alpha = 1f
            view.visibility = View.VISIBLE
            return
        }

        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .setListener(null)
            .start()
    }

    /**
     * Fade out a view.
     * @param hideOnComplete Set visibility to GONE after animation
     * @param enabled Whether animations are enabled
     */
    fun fadeOut(view: View, duration: Long = DURATION_SHORT,
                hideOnComplete: Boolean = true, enabled: Boolean = true) {
        if (!enabled) {
            view.alpha = 0f
            if (hideOnComplete) view.visibility = View.GONE
            return
        }

        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (hideOnComplete) view.visibility = View.GONE
                }
            })
            .start()
    }

    /**
     * Slide a view in from the side.
     * @param fromX Starting X offset (negative = from left, positive = from right)
     * @param enabled Whether animations are enabled
     */
    fun slideIn(view: View, fromX: Float, duration: Long = DURATION_MEDIUM, enabled: Boolean = true) {
        if (!enabled) {
            view.translationX = 0f
            view.visibility = View.VISIBLE
            return
        }

        view.translationX = fromX
        view.visibility = View.VISIBLE
        view.animate()
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .setListener(null)
            .start()
    }

    /**
     * Slide a view out to the side.
     * @param toX Target X offset
     * @param hideOnComplete Hide view after animation
     * @param enabled Whether animations are enabled
     */
    fun slideOut(view: View, toX: Float, duration: Long = DURATION_MEDIUM,
                 hideOnComplete: Boolean = true, enabled: Boolean = true) {
        if (!enabled) {
            view.translationX = toX
            if (hideOnComplete) view.visibility = View.GONE
            return
        }

        view.animate()
            .translationX(toX)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (hideOnComplete) view.visibility = View.GONE
                }
            })
            .start()
    }

    /**
     * Cancel all running animations on a view.
     */
    fun cancel(view: View) {
        view.animate().cancel()
    }
}
