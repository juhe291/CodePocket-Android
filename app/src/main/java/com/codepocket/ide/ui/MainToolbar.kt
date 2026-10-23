package com.codepocket.ide.ui

import android.annotation.SuppressLint
import com.codepocket.ide.R
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

enum class WorkbenchMenuGroup(val title:String) { FILE("文件"), EDIT("编辑"), VIEW("视图"), RUN("运行"), TERMINAL("终端"), HELP("帮助") }

/** Single-line desktop menus collapse into a structured mobile menu as the measured budget shrinks. */
@SuppressLint("ViewConstructor")
class MainToolbar(context: Context, private val palette: IdePalette,
    onExplorer: () -> Unit, onRun: () -> Unit, onMenu: (View) -> Unit,
    onTitle: () -> Unit, onSearch: () -> Unit, onTerminal: () -> Unit = {},
    onGroup: (View,WorkbenchMenuGroup)->Unit = {_,_->}) : LinearLayout(context) {
    private fun dp(value:Int)=IdeControls.dp(context,value)
    private val menus=LinearLayout(context)
    private val titleView=TextView(context).apply {
        textSize=12f;setTextColor(palette.muted);gravity=Gravity.CENTER_VERTICAL
        setSingleLine();ellipsize=TextUtils.TruncateAt.END;setPadding(dp(12),0,dp(8),0)
        setOnClickListener{onTitle()}
    }
    private val search=IdeControls.icon(context,palette,IdeIcon.SEARCH,IdeCommand.SEARCH.title,onSearch)
    private val terminal=IdeControls.icon(context,palette,IdeIcon.TERMINAL,"打开终端",onTerminal)
    private val run=IdeControls.icon(context,palette,IdeIcon.RUN,"运行 · F5",onRun)
    val menuAnchor=IdeControls.icon(context,palette,IdeIcon.MORE,"工作台菜单"){}
    private val borderPaint=android.graphics.Paint().apply{color=palette.border}
    private var running:Boolean?=null
    private var projectTitle="CodePocket"
    init {
        gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(palette.surface);setWillNotDraw(false)
        WorkbenchMenuGroup.entries.forEach { group ->
            menus.addView(IdeControls.key(context,palette,group.title,"${group.title}菜单"){onGroup(menus.findViewWithTag(group),group)}.apply { tag=group;textSize=12f },LayoutParams(dp(56),-1))
        }
        menuAnchor.setOnClickListener{onMenu(it)}
        addView(menus,LayoutParams(-2,-1));addView(titleView,LayoutParams(0,-1,1f))
        listOf(search,terminal,run,menuAnchor).forEach{addView(it,LayoutParams(dp(48),-1))}
    }
    override fun onDraw(canvas:android.graphics.Canvas){
        super.onDraw(canvas)
        canvas.drawRect(0f,height-dp(1).toFloat(),width.toFloat(),height.toFloat(),borderPaint)
    }
    // Rail owns navigation on every window, no duplicated Explorer button in the title bar.
    fun setSidebarPresent(present:Boolean)=Unit
    fun setTitle(title:String,fullPath:String){projectTitle=title;titleView.text=title;IdeControls.describe(titleView,fullPath)}
    fun setRunState(active:Boolean,enabled:Boolean) {
        run.isEnabled=enabled;run.alpha=if(enabled)1f else .4f
        if(running==active)return
        running=active;run.setImageDrawable(IdeIconDrawable(context,if(active)IdeIcon.STOP else IdeIcon.RUN,if(active)palette.error else palette.success,dp(20)))
        IdeControls.describe(run,if(active)"停止程序" else "运行 · F5")
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val budget=(MeasureSpec.getSize(widthMeasureSpec)/resources.displayMetrics.density).toInt()
        val heading=if(budget<600)"CodePocket" else projectTitle
        if(titleView.text.toString()!=heading)titleView.text=heading
        menus.visibility=if(budget>=1000 && resources.configuration.fontScale<=1.3f)VISIBLE else GONE
        search.visibility=if(budget>=600)VISIBLE else GONE
        terminal.visibility=if(budget>=144)VISIBLE else GONE
        run.visibility=if(budget>=96)VISIBLE else GONE
        titleView.visibility=if(budget>=240)VISIBLE else GONE
        listOf(search,terminal,run,menuAnchor).forEach{it.layoutParams.width=dp(minOf(48,budget.coerceAtLeast(0)))}
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}

@SuppressLint("ViewConstructor") // Programmatic shell; XML construction would have no command callbacks.
class PanelToolbar(context: Context, private val palette: IdePalette,
    onInput: () -> Unit, onMaximize: () -> Unit, onClose: () -> Unit,
    onMenu: (View) -> Unit, onClear: () -> Unit) : LinearLayout(context) {
    private fun dp(value: Int)=IdeControls.dp(context,value)
    private val bar=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
    private val sessionRow=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;visibility=GONE}
    private val inlineSessionParams=LayoutParams(dp(280),dp(48))
    private val stackedSessionParams=LayoutParams(-1,dp(48))
    private var navigation: View?=null
    private var sessions: View?=null
    private var sessionNavigation=true
    private var compact=false
    private var closeSessionAction:(()->Unit)?=null
    private val titleView=TextView(context).apply{
        text=context.getString(R.string.panel_title);textSize=12f;setTextColor(palette.foreground)
        gravity=Gravity.CENTER_VERTICAL;setSingleLine();setPadding(dp(12),0,0,0)
    }
    private val clear=TerminalChrome.action(context,palette,IdeIcon.CLEAR,"清空当前终端显示（不向程序发送输入）",Runnable(onClear))
    private val preset=TerminalChrome.action(context,palette,IdeIcon.INPUT,"展开预设输入",Runnable(onInput))
    private val maximize=TerminalChrome.action(context,palette,IdeIcon.MAXIMIZE,context.getString(R.string.panel_maximize),Runnable(onMaximize))
    private val close=TerminalChrome.action(context,palette,IdeIcon.DOWN,context.getString(R.string.panel_close),Runnable(onClose))
    private val endSession=TerminalChrome.action(context,palette,IdeIcon.DELETE,"关闭当前终端会话",Runnable{closeSessionAction?.invoke()}).apply{visibility=GONE}
    val menuAnchor=TerminalChrome.action(context,palette,IdeIcon.MORE,context.getString(R.string.panel_size_menu),Runnable{})
    private var inputAvailable=true
    private var maximized: Boolean?=null
    private var expanded: Boolean?=null
    init {
        orientation=VERTICAL;setBackgroundColor(palette.panel);setPadding(dp(4),0,dp(4),0)
        bar.addView(titleView,LayoutParams(0,dp(48),1f))
        listOf(preset,clear,endSession,maximize,close,menuAnchor).forEach{bar.addView(it,LayoutParams(dp(48),dp(48)))}
        addView(bar,LayoutParams(-1,dp(48)));addView(sessionRow,LayoutParams(-1,dp(48)))
        menuAnchor.setOnClickListener{onMenu(it)}
    }
    fun bindNavigation(tabs: View, sessionStrip: View) {
        navigation=tabs;sessions=sessionStrip;bar.removeView(titleView)
        bar.addView(tabs,0,LayoutParams(0,dp(48),1f));sessionRow.addView(sessionStrip,stackedSessionParams)
        requestLayout()
    }
    fun setCloseSessionAction(action:()->Unit){closeSessionAction=action;requestLayout()}
    fun setCurrentSessionAvailable(available:Boolean){endSession.isEnabled=available;endSession.alpha=if(available)1f else .35f}
    fun setSessionNavigationVisible(visible: Boolean){if(sessionNavigation!=visible){sessionNavigation=visible;requestLayout()}}
    fun setCompact(value: Boolean){if(compact!=value){compact=value;requestLayout()}}
    fun expandedChromeHeightDp(widthDp: Int): Int = if(navigation!=null&&sessionNavigation&&
        !(widthDp>=600&&resources.configuration.fontScale<=1.3f))96 else 48
    fun chromeHeightDp(widthDp: Int): Int = if(compact)48 else expandedChromeHeightDp(widthDp)
    fun setInputAvailable(available: Boolean){if(inputAvailable!=available){inputAvailable=available;requestLayout()}}
    fun setShellMode(shell: Boolean) {
        IdeControls.describe(titleView,if(shell)"真实 Android Shell 会话" else "终端工作台")
        IdeControls.describe(clear,if(shell)"清空当前终端显示（不向程序发送输入）" else "清空输出显示；保留结果和预设输入")
    }
    fun setState(isMaximized: Boolean,inputExpanded: Boolean) {
        if(maximized!=isMaximized){
            maximized=isMaximized
            maximize.setImageDrawable(IdeIconDrawable(context,if(isMaximized)IdeIcon.RESTORE else IdeIcon.MAXIMIZE,palette.muted,dp(20)))
            IdeControls.describe(maximize,context.getString(if(isMaximized)R.string.panel_restore else R.string.panel_maximize))
        }
        if(expanded!=inputExpanded){
            expanded=inputExpanded;preset.isSelected=inputExpanded
            IdeControls.describe(preset,if(inputExpanded)"收起预设输入" else "展开预设输入")
        }
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        val budget=(width/resources.displayMetrics.density).toInt()
        val spec=WorkspaceLayoutPolicy.panelToolbar(budget)
        val integrated=navigation!=null
        clear.visibility=if(!integrated && spec.clear)VISIBLE else GONE
        preset.visibility=if(inputAvailable&&if(integrated)budget>=840 else spec.preset)VISIBLE else GONE
        maximize.visibility=if(spec.maximize&&(!integrated||!compact||budget>=400))VISIBLE else GONE
        endSession.visibility=if(compact&&sessionNavigation&&closeSessionAction!=null)VISIBLE else GONE
        close.visibility=if(spec.close)VISIBLE else GONE
        sessions?.let { strip ->
            val inline=budget>=600&&resources.configuration.fontScale<=1.3f&&!compact
            val parent=if(inline)bar else sessionRow
            if(strip.parent!==parent){(strip.parent as? android.view.ViewGroup)?.removeView(strip)
                if(inline)bar.addView(strip,1,inlineSessionParams)
                else sessionRow.addView(strip,stackedSessionParams)}
            strip.visibility=if(sessionNavigation&&!compact)VISIBLE else GONE
            sessionRow.visibility=if(!inline&&sessionNavigation&&!compact)VISIBLE else GONE
        }
        listOf(preset,clear,endSession,maximize,close,menuAnchor).forEach{it.layoutParams.width=minOf(dp(48),width)}
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
