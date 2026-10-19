package com.codepocket.ide.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import com.codepocket.ide.R
import kotlin.math.sqrt

/** A bounded, memory-only visual snapshot. ViewOverlay has no input or accessibility target. */
object SurfaceMotion {
    class Snapshot internal constructor(internal val bitmap: Bitmap, val width: Int, val height: Int) {
        fun release() { if (!bitmap.isRecycled) bitmap.recycle() }
    }
    fun capture(view: View, enabled: Boolean): Snapshot? {
        if (!MotionEnvironment.enabled(view.context, enabled) || !view.isLaidOut || view.width <= 0 || view.height <= 0) return null
        val pixels = view.width.toLong() * view.height
        val scale = minOf(1.0, sqrt(2_000_000.0 / pixels))
        return runCatching {
            val bitmap = Bitmap.createBitmap((view.width * scale).toInt().coerceAtLeast(1),
                (view.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap).apply { scale(scale.toFloat(), scale.toFloat()); translate(-view.scrollX.toFloat(), -view.scrollY.toFloat()) })
                Snapshot(bitmap, view.width, view.height)
            } catch (error: Throwable) { bitmap.recycle(); throw error }
        }.getOrNull()
    }
    private class Flight(val root: ViewGroup, val snapshot: Snapshot, val drawable: BitmapDrawable,
        val bounds: Rect, val dx: Int, val dy: Int, val matchRootSize: Boolean, val destination: View?) : View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        var animator: ValueAnimator? = null
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { finish(root) }
        override fun onPreDraw(): Boolean {
            root.viewTreeObserver.removeOnPreDrawListener(this)
            if (root.getTag(R.id.workbench_surface_motion) !== this) return true
            if (matchRootSize && (root.width != snapshot.width || root.height != snapshot.height)) { finish(root); return true }
            var shiftX = dx; var shiftY = dy
            destination?.takeIf { it.isShown }?.let { target ->
                val next = Rect(0, 0, target.width, target.height)
                root.offsetDescendantRectToMyCoords(target, next)
                shiftX += next.left - bounds.left; shiftY += next.top - bounds.top
            }
            root.overlay.add(drawable)
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180; interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    drawable.alpha = ((1 - t) * 255).toInt()
                    drawable.bounds = Rect(bounds).apply { offset((shiftX * t).toInt(), (shiftY * t).toInt()) }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { if (root.getTag(R.id.workbench_surface_motion) === this@Flight) finish(root) }
                })
                start()
            }
            return true
        }
    }
    fun finish(root: ViewGroup) {
        val flight = root.getTag(R.id.workbench_surface_motion) as? Flight ?: return
        root.setTag(R.id.workbench_surface_motion, null)
        root.removeOnAttachStateChangeListener(flight)
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(flight)
        flight.animator?.apply { removeAllListeners(); cancel() }
        root.overlay.remove(flight.drawable); flight.snapshot.release()
    }
    fun play(root: ViewGroup, snapshot: Snapshot?, bounds: Rect = Rect(0, 0, root.width, root.height), dx: Int = 0, dy: Int = 0, matchRootSize: Boolean = false, destination: View? = null) {
        finish(root)
        if (snapshot == null) return
        if (!root.isAttachedToWindow || !MotionEnvironment.enabled(root.context, true)) { snapshot.release(); return }
        val drawable = BitmapDrawable(root.resources, snapshot.bitmap).apply { this.bounds = bounds }
        val flight = Flight(root, snapshot, drawable, Rect(bounds), dx, dy, matchRootSize, destination)
        root.setTag(R.id.workbench_surface_motion, flight); root.addOnAttachStateChangeListener(flight)
        root.viewTreeObserver.addOnPreDrawListener(flight); root.invalidate()
    }
    fun reflow(root: ViewGroup, target: View, enabled: Boolean) {
        finish(root)
        if (!target.isShown) return
        val snapshot = capture(target, enabled) ?: return
        val rect = Rect(0, 0, target.width, target.height)
        root.offsetDescendantRectToMyCoords(target, rect)
        play(root, snapshot, rect, destination = target)
    }
    fun outgoing(root: ViewGroup, target: View, enabled: Boolean, dx: Int = 0, dy: Int = 0) {
        finish(root)
        val snapshot = capture(target, enabled) ?: return
        val rect = Rect(0, 0, target.width, target.height)
        root.offsetDescendantRectToMyCoords(target, rect)
        play(root, snapshot, rect, dx, dy)
    }
}
