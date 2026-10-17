package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * VS Code-style status bar at the bottom showing file info and IDE state.
 * Displays: language, line/column, indentation, encoding, and current state.
 */
@SuppressLint("ViewConstructor")
class StatusBar(
    context: Context,
    private val palette: IdePalette
) : LinearLayout(context) {

    private val statusText: TextView

    init {
        orientation = HORIZONTAL
        setBackgroundColor(palette.surface)
        gravity = Gravity.CENTER_VERTICAL

        val dp = { v: Int -> IdeControls.dp(context, v) }

        statusText = TextView(context).apply {
            textSize = 11f
            setTextColor(palette.muted)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        addView(statusText, LayoutParams(0, -2, 1f))
    }

    fun setStatus(layout: StatusBarLayout) {
        statusText.text = layout.format()

        // Apply color based on state
        statusText.setTextColor(when {
            layout.state.contains("运行中") -> palette.accent
            layout.state.contains("错误") -> palette.error
            layout.state.contains("成功") -> palette.success
            else -> palette.muted
        })
    }

    fun setText(text: String) {
        statusText.text = text
        statusText.setTextColor(palette.muted)
    }
}
