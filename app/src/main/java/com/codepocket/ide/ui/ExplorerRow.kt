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
        setImageDrawable(IdeIconDrawable(context, IdeIcon.CHEVRON_RIGHT, palette.muted, dp(16)))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val name = TextView(context).apply {
        textSize = 13f; includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL
        setSingleLine(); ellipsize = TextUtils.TruncateAt.END; compoundDrawablePadding = dp(6)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val more = IdeControls.icon(context, palette, IdeIcon.MORE, "文件操作") {}.apply { visibility = View.GONE }
    fun setMenuAction(action: () -> Unit) { more.setOnClickListener { action() } }
    private var depth = 0
    private var bound = false
    private var directoryRow = false
    private var modifiedFlag = false
    private var baseDescription = ""
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.foreground }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(DesignTokens.TOUCH); isFocusable = true; isClickable = true
        setWillNotDraw(false); foreground = IdeControls.feedback(context, palette)
        addView(arrow, LayoutParams(dp(20), dp(20)))
        addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(more, LayoutParams(dp(48), dp(48)))
    }

    fun bind(path: String, level: Int, directory: Boolean, expanded: Boolean, selected: Boolean, animate: Boolean, displayName: String? = null, modified: Boolean = false) {
        depth = level.coerceIn(0, 20)
        directoryRow = directory
        val markerSpace = if (directory) 0 else dp(16)
        if (name.paddingRight != markerSpace) name.setPadding(0, 0, markerSpace, 0)
        val title = displayName ?: path.substringAfterLast('/')
        if (name.text.toString() != title) name.text = title
        name.setTextColor(if (selected) palette.accentText else palette.foreground)
        val icon = if (directory) (if (expanded) IdeIcon.FOLDER_OPEN else IdeIcon.FOLDER) else IdeIconDrawable.file(path)
        name.setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(context, icon,
            IdeIconDrawable.color(icon, palette), dp(20)), null, null, null)
        Animations.rotate(arrow, if (expanded && directory) 90f else 0f, animate && bound && directory)
        arrow.visibility = if (directory) View.VISIBLE else View.INVISIBLE
        background = if (selected) GradientDrawable().apply { setColor(palette.selection); cornerRadius = dp(3).toFloat() } else null
        baseDescription = (if(path.isEmpty())title else path) + if (directory) (if (expanded) "，已展开" else "，已折叠") else ""
        setModified(modified)
        more.visibility = if (selected && path.isNotEmpty()) View.VISIBLE else View.GONE
        isSelected = selected; bound = true; invalidate()
    }

    /** Presentation only: no tree rebuild, file access, focus change, or layout request while typing. */
    fun setModified(value: Boolean) {
        val next = value && !directoryRow
        val changed = next != modifiedFlag
        modifiedFlag = next
        val description = baseDescription + if (next) " · 未保存" else ""
        if (contentDescription?.toString() != description) IdeControls.describe(this, description)
        if (changed) invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (modifiedFlag) canvas.drawCircle(name.right - dp(8).toFloat(), height / 2f, dp(3).toFloat(), markerPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Deep nesting must still leave room for the file name in a narrow phone drawer or large font.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val budget = (width - dp(192)).coerceAtLeast(0)
        val indent = dp(8) + minOf(dp(depth * 12), dp(96), budget)
        if (paddingLeft != indent) setPadding(indent, 0, dp(8), 0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        val step = dp(12)
        if (step > 0) for (x in dp(8) + step / 2 until paddingLeft step step) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), guide)
        }
        super.onDraw(canvas)
        if(isSelected){guide.color=palette.accent;canvas.drawRect(0f,dp(12).toFloat(),dp(2).toFloat(),height-dp(12).toFloat(),guide);guide.color=palette.border}
    }
}
