package com.codepocket.ide.animation

import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.Transition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import com.codepocket.ide.R

/** Native tree reflow; panel reflow uses a visual snapshot, never per-frame layout of PTY contents. */
object LayoutMotion {
    const val MAX_TREE_ITEMS = 120
    private class Pending(val transition: Transition) : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { finish(view as ViewGroup) }
    }
    fun finish(root: ViewGroup) {
        val pending = root.getTag(R.id.workbench_layout_motion) as? Pending ?: return
        root.setTag(R.id.workbench_layout_motion, null)
        root.removeOnAttachStateChangeListener(pending)
        TransitionManager.endTransitions(root)
    }
    fun begin(root: ViewGroup, enabled: Boolean, boundsTarget: View? = null) {
        finish(root)
        if (boundsTarget != null) { SurfaceMotion.reflow(root, boundsTarget, enabled); return }
        if (root.childCount > MAX_TREE_ITEMS || !root.isLaidOut || !root.isAttachedToWindow || !MotionEnvironment.enabled(root.context, enabled)) return
        val bounds = ChangeBounds().apply { duration = 180 }
        val transition: Transition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(bounds); addTransition(Fade().apply { duration = 140 })
        }
        val pending = Pending(transition)
        transition.addListener(object : Transition.TransitionListener {
            override fun onTransitionStart(t: Transition) = Unit
            override fun onTransitionEnd(t: Transition) {
                if (root.getTag(R.id.workbench_layout_motion) === pending) {
                    root.setTag(R.id.workbench_layout_motion, null); root.removeOnAttachStateChangeListener(pending)
                }
            }
            override fun onTransitionCancel(t: Transition) = onTransitionEnd(t)
            override fun onTransitionPause(t: Transition) = Unit
            override fun onTransitionResume(t: Transition) = Unit
        })
        root.setTag(R.id.workbench_layout_motion, pending); root.addOnAttachStateChangeListener(pending)
        TransitionManager.beginDelayedTransition(root, transition)
    }
}
