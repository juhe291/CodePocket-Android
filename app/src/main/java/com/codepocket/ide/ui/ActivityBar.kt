package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/** Persistent workbench rail. Overflow scrolls inside the rail, settings stays at the bottom. */
@SuppressLint("ViewConstructor")
class ActivityBar(context: Context, private val palette: IdePalette,
    onSearch: (() -> Unit)? = null, onSettings: (() -> Unit)? = null,
    onRun: (() -> Unit)? = null, onTerminal: (() -> Unit)? = null,
    private val onExplorer: () -> Unit) : LinearLayout(context) {
    private val explorerButton: ImageView
    private var searchButton: ImageView? = null
    private val pen=Paint().apply { color=palette.accentText }
    private val indicator=Rect()
    init {
        orientation=VERTICAL;setBackgroundColor(palette.surface)
        val actions=LinearLayout(context).apply { orientation=VERTICAL }
        fun action(icon:IdeIcon,description:String,callback:()->Unit):ImageView =
            IdeControls.icon(context,palette,icon,description,callback).apply {
                setPadding(dp(13),dp(13),dp(13),dp(13))
                setImageDrawable(IdeIconDrawable(context,icon,palette.muted,dp(22)))
                actions.addView(this,LayoutParams(dp(48),dp(52)))
            }
        explorerButton=action(IdeIcon.EXPLORER,"资源管理器 · Ctrl+B",onExplorer)
        onSearch?.let { searchButton=action(IdeIcon.SEARCH,"项目搜索",it) }
        onRun?.let { action(IdeIcon.RUN,"运行 / 停止 · F5",it) }
        onTerminal?.let { action(IdeIcon.TERMINAL,"打开终端",it) }
        addView(ScrollView(context).apply { isVerticalScrollBarEnabled=false;addView(actions) },LayoutParams(dp(48),0,1f))
        onSettings?.let { addView(IdeControls.icon(context,palette,IdeIcon.SETTINGS,"设置",it),LayoutParams(dp(48),dp(52))) }
    }
    private fun dp(value:Int)=IdeControls.dp(context,value)
    fun setExplorerActive(active:Boolean) {
        explorerButton.isSelected=active
        explorerButton.setImageDrawable(IdeIconDrawable(context,IdeIcon.EXPLORER,if(active)palette.foreground else palette.muted,dp(22)))
        invalidate()
    }
    fun setSearchActive(active:Boolean) {
        searchButton?.apply {
            isSelected=active
            setImageDrawable(IdeIconDrawable(context,IdeIcon.SEARCH,if(active)palette.foreground else palette.muted,dp(22)))
        }
        invalidate()
    }
    override fun dispatchDraw(canvas:Canvas) {
        super.dispatchDraw(canvas)
        val selected=listOfNotNull(explorerButton,searchButton).firstOrNull{it.isSelected&&it.isShown}
        if(selected!=null) {
            selected.getDrawingRect(indicator);offsetDescendantRectToMyCoords(selected,indicator)
            canvas.drawRect(0f,indicator.top.coerceAtLeast(0).toFloat(),dp(2).toFloat(),indicator.bottom.coerceAtMost(height-dp(52)).toFloat(),pen)
        }
        pen.color=palette.border;canvas.drawRect(width-dp(1).toFloat(),0f,width.toFloat(),height.toFloat(),pen);pen.color=palette.accentText
    }
}
