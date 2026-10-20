package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Retained across dirty-state updates; the active indicator never moves or transforms the editor. */
// Theme and file actions are injected by the workbench; this component is never inflated from XML.
@SuppressLint("ViewConstructor")
class EditorTab(context: Context, private val palette: IdePalette, val path: String,
    open: () -> Unit, close: () -> Unit, describe: () -> Unit) : LinearLayout(context) {
    private fun dp(value: Int) = IdeControls.dp(context, value)
    private val pen = Paint().apply { color = palette.accent }
    private var activeTab = false
    private val name = TextView(context).apply {
        textSize = 12f; gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false
        maxWidth = dp(240); setSingleLine(); ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(10), 0, dp(6), 0); compoundDrawablePadding = dp(6)
        setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(context, IdeIconDrawable.file(path), IdeIconDrawable.color(IdeIconDrawable.file(path), palette), dp(20)), null, null, null)
        background = IdeControls.feedback(context, palette)
        isFocusable = true; setOnClickListener { open() }; setOnLongClickListener { describe(); true }
        IdeControls.describe(this, path)
    }
    init {
        gravity = Gravity.CENTER_VERTICAL; setWillNotDraw(false)
        addView(name, LayoutParams(LayoutParams.WRAP_CONTENT, dp(DesignTokens.TOUCH)))
        addView(IdeControls.icon(context, palette, IdeIcon.CLOSE, "关闭 $path", close),
            LayoutParams(dp(DesignTokens.TOUCH), dp(DesignTokens.TOUCH)))
    }
    fun bind(active: Boolean, dirty: Boolean) {
        activeTab = active; name.isSelected = active
        val title = "${if (dirty) "● " else ""}${path.substringAfterLast('/')}"
        if (name.text.toString() != title) name.text = title
        name.setTextColor(if (active) palette.foreground else palette.muted)
        setBackgroundColor(if (active) palette.editor else palette.surface); invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeTab && parent !is EditorTabs) canvas.drawRect(0f, 0f, width.toFloat(), dp(2).toFloat(), pen)
    }
}
