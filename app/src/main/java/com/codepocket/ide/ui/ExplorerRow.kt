package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.ide.animation.Animations

/** A full 48dp touch row; only the chevron rotates. Text and hit targets stay at their final coordinates. */
// Theme and file actions are injected by the workbench; this component is never inflated from XML.
@SuppressLint("ViewConstructor")
class ExplorerRow(context: Context, private val palette: IdePalette) : LinearLayout(context) {
    private fun dp(value: Int) = IdeControls.dp(context, value)
    private val guide = Paint().apply { color = palette.border; strokeWidth = dp(1).toFloat() }
    private val arrow = ImageView(context).apply {
        setImageDrawable(IdeIconDrawable(IdeIcon.CHEVRON_RIGHT, palette.muted, dp(16)))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val name = TextView(context).apply {
        textSize = 13f; includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
        setSingleLine(); ellipsize = TextUtils.TruncateAt.END; compoundDrawablePadding = dp(6)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var depth = 0
    private var bound = false

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(DesignTokens.TOUCH); isFocusable = true; isClickable = true
        setWillNotDraw(false); foreground = IdeControls.feedback(context, palette)
        addView(arrow, LayoutParams(dp(20), dp(20)))
        addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    fun bind(path: String, level: Int, directory: Boolean, expanded: Boolean, selected: Boolean, animate: Boolean) {
        depth = level.coerceIn(0, 20)
        val title = path.substringAfterLast('/')
        if (name.text.toString() != title) name.text = title
        name.setTextColor(if (selected) palette.accentText else palette.foreground)
        val icon = if (directory) (if (expanded) IdeIcon.FOLDER_OPEN else IdeIcon.FOLDER) else IdeIconDrawable.file(path)
        name.setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(icon,
            if (selected) palette.accentText else palette.muted, dp(20)), null, null, null)
        Animations.rotate(arrow, if (expanded && directory) 90f else 0f, animate && bound && directory)
        arrow.visibility = if (directory) View.VISIBLE else View.INVISIBLE
        background = if (selected) GradientDrawable().apply { setColor(palette.editor); cornerRadius = dp(3).toFloat() } else null
        IdeControls.describe(this, path + if (directory) (if (expanded) "，已展开" else "，已折叠") else "")
        isSelected = selected; bound = true; invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Deep nesting must still leave room for the file name in a narrow phone drawer or large font.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val budget = (width - dp(144)).coerceAtLeast(0)
        val indent = dp(8) + minOf(dp(depth * 12), dp(96), budget)
        if (paddingLeft != indent) setPadding(indent, dp(6), dp(8), dp(6))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        val step = dp(12)
        if (step > 0) for (x in dp(8) + step / 2 until paddingLeft step step) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), guide)
        }
        super.onDraw(canvas)
    }
}
