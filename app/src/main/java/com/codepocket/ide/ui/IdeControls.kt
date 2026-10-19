package com.codepocket.ide.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.widget.ImageButton
import android.widget.TextView

object IdeControls {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun feedback(context: Context, palette: IdePalette) = RippleDrawable(
        ColorStateList.valueOf((palette.foreground and 0x00FFFFFF) or 0x28000000), null,
        GradientDrawable().apply { setColor(-1); cornerRadius = dp(context, 4).toFloat() })
    fun describe(view: android.view.View, text: String) {
        view.contentDescription = text
        if (Build.VERSION.SDK_INT >= 26) view.tooltipText = text
    }
    fun icon(context: Context, palette: IdePalette, icon: IdeIcon, description: String, action: () -> Unit) =
        ImageButton(context).apply {
            background = feedback(context, palette)
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            minimumWidth = 0; minimumHeight = 0
            setImageDrawable(IdeIconDrawable(icon, palette.foreground, dp(context, DesignTokens.ICON)))
            describe(this, description)
            setOnClickListener { action() }
        }
    fun key(context: Context, palette: IdePalette, text: String, description: String, action: () -> Unit) =
        TextView(context).apply {
            this.text = text; textSize = 13f; setTextColor(palette.foreground); gravity = Gravity.CENTER
            minWidth = dp(context, DesignTokens.TOUCH); minHeight = dp(context, DesignTokens.TOUCH)
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
            background = feedback(context, palette); isFocusable = true; isClickable = true
            describe(this, description); setOnClickListener { action() }
        }
}
