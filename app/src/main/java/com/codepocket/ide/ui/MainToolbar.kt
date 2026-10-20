package com.codepocket.ide.ui

import android.annotation.SuppressLint
import com.codepocket.ide.R
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Width decisions depend on this view's measured budget, never device orientation. */
@SuppressLint("ViewConstructor") // Programmatic shell: requires live command callbacks, never inflated from XML.
class MainToolbar(context: Context, private val palette: IdePalette,
    onExplorer: () -> Unit, onRun: () -> Unit, onMenu: (View) -> Unit,
    onTitle: () -> Unit, onSearch: () -> Unit, onTerminal: () -> Unit = {}) : LinearLayout(context) {
    private fun dp(value: Int) = IdeControls.dp(context, value)
    private val explorer = IdeControls.icon(context, palette, IdeIcon.EXPLORER, "资源管理器 · Ctrl+B", onExplorer)
    private val titleView = TextView(context).apply {
        textSize = 14f; setTextColor(palette.foreground); gravity = Gravity.CENTER_VERTICAL
        setSingleLine(); ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(4), 0, dp(4), 0); setOnClickListener { onTitle() }
    }
    private val search = IdeControls.icon(context, palette, IdeIcon.SEARCH, IdeCommand.SEARCH.title, onSearch)
    private val terminal = IdeControls.icon(context, palette, IdeIcon.TERMINAL, "打开终端", onTerminal)
    private var sidebar = false
    fun setSidebarPresent(present: Boolean) { if (sidebar != present) { sidebar = present; requestLayout() } }
    private val run = IdeControls.icon(context, palette, IdeIcon.RUN, "运行 · F5", onRun)
    val menuAnchor = IdeControls.icon(context, palette, IdeIcon.MORE, "更多命令") {}
    private var running: Boolean? = null
    init {
        menuAnchor.setOnClickListener { onMenu(it) }
        gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(palette.surface)
        addView(explorer, LayoutParams(dp(48), -1))
        addView(titleView, LayoutParams(0, -1, 1f))
        addView(search, LayoutParams(dp(48), -1))
        addView(terminal, LayoutParams(dp(48), -1))
        addView(run, LayoutParams(dp(48), -1)); addView(menuAnchor, LayoutParams(dp(48), -1))
    }
    fun setTitle(title: String, fullPath: String) {
        if (titleView.text.toString() != title) titleView.text = title
        IdeControls.describe(titleView, fullPath)
        titleView.setOnLongClickListener { android.widget.Toast.makeText(context, fullPath, android.widget.Toast.LENGTH_LONG).show(); true }
    }
    fun setRunState(active: Boolean, enabled: Boolean) {
        run.isEnabled = enabled; run.alpha = if (enabled) 1f else .4f
        if (running == active) return
        running = active
        run.setImageDrawable(IdeIconDrawable(context, if (active) IdeIcon.STOP else IdeIcon.RUN,
            if (active) palette.error else palette.success, dp(20)))
        IdeControls.describe(run, if (active) "停止程序" else "运行 · F5")
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val budget = (MeasureSpec.getSize(widthMeasureSpec) / resources.displayMetrics.density).toInt()
        val spec = WorkspaceLayoutPolicy.toolbar(budget)
        explorer.visibility = if (spec.explorer && !sidebar) VISIBLE else GONE
        search.visibility = if (spec.search) VISIBLE else GONE
        terminal.visibility = if (spec.terminal) VISIBLE else GONE
        run.visibility = if (spec.run) VISIBLE else GONE
        titleView.visibility = if (spec.title) VISIBLE else GONE
        listOf(explorer, search, terminal, run, menuAnchor).forEach { it.layoutParams.width = dp(spec.control) }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

@SuppressLint("ViewConstructor") // Programmatic shell; XML construction would have no command callbacks.
class PanelToolbar(context: Context, private val palette: IdePalette,
    onInput: () -> Unit, onMaximize: () -> Unit, onClose: () -> Unit,
    onMenu: (View) -> Unit, onClear: () -> Unit) : LinearLayout(context) {
    private fun dp(value: Int) = IdeControls.dp(context, value)
    private val titleView = TextView(context).apply {
        text = context.getString(R.string.panel_title); textSize = 12f; setTextColor(palette.muted); gravity = Gravity.CENTER_VERTICAL
        setSingleLine(); ellipsize = TextUtils.TruncateAt.END; setPadding(dp(8), 0, 0, 0)
        IdeControls.describe(this, "终端工作台：在同一个真实终端中运行程序")
    }
    private val clear = IdeControls.icon(context, palette, IdeIcon.CLEAR, "清空输出显示；保留结果和预设输入", onClear)
    private val preset = IdeControls.icon(context, palette, IdeIcon.INPUT, "展开预设输入", onInput)
    private val maximize = IdeControls.icon(context, palette, IdeIcon.MAXIMIZE, context.getString(R.string.panel_maximize), onMaximize)
    private val close = IdeControls.icon(context, palette, IdeIcon.CLOSE, context.getString(R.string.panel_close), onClose)
    val menuAnchor = IdeControls.icon(context, palette, IdeIcon.MORE, context.getString(R.string.panel_size_menu)) {}
    private var inputAvailable = true
    fun setInputAvailable(available:Boolean) { inputAvailable=available;requestLayout() }
    private var shellMode = false
    fun setShellMode(shell: Boolean) {
        shellMode = shell
        titleView.text = context.getString(R.string.panel_title)
        IdeControls.describe(titleView, if (shell) "真实 Android Shell 会话" else "终端工作台：在同一个真实终端中运行程序")
        IdeControls.describe(clear, if (shell) "清空当前终端显示（不向程序发送输入）" else "清空输出显示；保留结果和预设输入")
        requestLayout()
    }
    private var maximized: Boolean? = null
    private var expanded: Boolean? = null
    init {
        menuAnchor.setOnClickListener { onMenu(it) }
        gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(palette.panel)
        addView(titleView, LayoutParams(0, -1, 1f))
        listOf(preset, clear, maximize, close, menuAnchor).forEach { addView(it, LayoutParams(dp(48), -1)) }
    }
    fun setState(isMaximized: Boolean, inputExpanded: Boolean) {
        if (maximized != isMaximized) {
            maximized = isMaximized
            maximize.setImageDrawable(IdeIconDrawable(context, if (isMaximized) IdeIcon.RESTORE else IdeIcon.MAXIMIZE,
                palette.foreground, dp(20)))
            IdeControls.describe(maximize, context.getString(if (isMaximized) R.string.panel_restore else R.string.panel_maximize))
        }
        if (expanded != inputExpanded) {
            expanded = inputExpanded; preset.isSelected = inputExpanded
            IdeControls.describe(preset, if (inputExpanded) "收起预设输入" else "展开预设输入")
            preset.setImageDrawable(IdeIconDrawable(context, IdeIcon.INPUT, if (inputExpanded) palette.accent else palette.foreground, dp(20)))
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val budget = (width / resources.displayMetrics.density).toInt()
        val spec = WorkspaceLayoutPolicy.panelToolbar(budget)
        clear.visibility = if (spec.clear) VISIBLE else GONE
        preset.visibility = if (spec.preset && inputAvailable) VISIBLE else GONE
        maximize.visibility = if (spec.maximize) VISIBLE else GONE
        close.visibility = if (spec.close) VISIBLE else GONE
        listOf(preset, clear, maximize, close, menuAnchor).forEach { it.layoutParams.width = minOf(dp(48), width) }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
