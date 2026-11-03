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
    onRun: (() -> Unit)? = null, onTerminal: (() -> Unit)? = null, onSourceControl:(()->Unit)?=null,onWeb:(()->Unit)?=null,
    private val onExplorer: () -> Unit) : LinearLayout(context) {
    private val explorerButton: ImageView
    private var searchButton: ImageView? = null
    private var sourceButton:ImageView?=null
    private var webButton:ImageView?=null
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
        onSourceControl?.let{sourceButton=action(IdeIcon.SOURCE_CONTROL,"源代码管理",it)}
        onWeb?.let{webButton=action(IdeIcon.WEB,"网页辅助栏",it)}
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
    fun setSourceActive(active:Boolean) {
        sourceButton?.apply{isSelected=active;setImageDrawable(IdeIconDrawable(context,IdeIcon.SOURCE_CONTROL,if(active)palette.foreground else palette.muted,dp(22)))}
        invalidate()
    }
    fun setWebActive(active:Boolean){webButton?.apply{isSelected=active;setImageDrawable(IdeIconDrawable(context,IdeIcon.WEB,if(active)palette.foreground else palette.muted,dp(22)))};invalidate()}
    override fun dispatchDraw(canvas:Canvas) {
        super.dispatchDraw(canvas)
        val selected=listOfNotNull(explorerButton,searchButton,sourceButton,webButton).filter{it.isSelected&&it.isShown}
        selected.forEach {
            it.getDrawingRect(indicator);offsetDescendantRectToMyCoords(it,indicator)
            canvas.drawRect(0f,indicator.top.coerceAtLeast(0).toFloat(),dp(2).toFloat(),indicator.bottom.coerceAtMost(height-dp(52)).toFloat(),pen)
        }
        pen.color=palette.border;canvas.drawRect(width-dp(1).toFloat(),0f,width.toFloat(),height.toFloat(),pen);pen.color=palette.accentText
    }
}
