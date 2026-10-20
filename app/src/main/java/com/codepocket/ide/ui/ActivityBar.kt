package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * VS Code-style activity bar: vertical icon strip on the far left.
 * Shows primary navigation items in wide layouts.
 */
@SuppressLint("ViewConstructor")
class ActivityBar(
    context: Context,
    private val palette: IdePalette,
    onSearch: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    private val onExplorer: () -> Unit
) : LinearLayout(context) {

    private val explorerButton: ImageView

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.surface)

        explorerButton = IdeControls.icon(
            context,
            palette,
            IdeIcon.EXPLORER,
            "资源管理器 · Ctrl+B",
            onExplorer
        ).apply {
            isSelected = true // Explorer active by default
        }

        onSearch?.let { action -> addView(IdeControls.icon(context,palette,IdeIcon.SEARCH,"项目搜索",action),LayoutParams(IdeControls.dp(context,48),IdeControls.dp(context,48))) }
        addView(explorerButton, LayoutParams(
            IdeControls.dp(context, DesignTokens.TOUCH),
            IdeControls.dp(context, DesignTokens.TOUCH)
        ))
        addView(android.view.View(context),LayoutParams(1,0,1f))
        onSettings?.let { action -> addView(IdeControls.icon(context,palette,IdeIcon.SETTINGS,"设置",action),LayoutParams(IdeControls.dp(context,48),IdeControls.dp(context,48))) }
    }

    fun setExplorerActive(active: Boolean) {
        explorerButton.isSelected = active
    }
}
