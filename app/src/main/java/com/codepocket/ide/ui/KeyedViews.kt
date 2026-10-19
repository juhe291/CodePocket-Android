package com.codepocket.ide.ui

import android.view.View
import android.widget.LinearLayout
import com.codepocket.ide.R
import com.codepocket.ide.animation.Animations

/** Keep surviving rows attached: focus, scroll position and ongoing input belong to the existing Views. */
object KeyedViews {
    fun update(parent: LinearLayout, keys: List<String>, create: (String) -> View,
        bind: (View, String, Boolean) -> Unit) {
        require(keys.size == keys.toSet().size)
        val wanted = keys.toSet()
        val existing = (0 until parent.childCount).map { parent.getChildAt(it) }
            .associateBy { it.getTag(R.id.workbench_key) as? String }
        for (i in parent.childCount - 1 downTo 0) {
            val view = parent.getChildAt(i)
            if (view.getTag(R.id.workbench_key) !in wanted) {
                Animations.cancelTree(view); parent.removeViewAt(i)
            }
        }
        keys.forEachIndexed { index, key ->
            val previous = existing[key]
            val view = previous ?: create(key).apply { setTag(R.id.workbench_key, key) }
            if (parent.getChildAt(index) !== view) {
                val focused = view.findFocus()
                if (view.parent === parent) parent.removeView(view)
                parent.addView(view, index)
                focused?.requestFocus()
            }
            bind(view, key, previous == null)
        }
    }
}
