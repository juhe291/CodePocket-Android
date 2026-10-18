package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

object SettingsDialog {
    fun show(activity: Activity, current: AppSettings, save: (AppSettings) -> Unit): AlertDialog {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 12, 32, 12) }
        fun heading(title: String) { content.addView(TextView(activity).apply { text = title; textSize = 16f; setPadding(0, 24, 0, 12) }) }
        fun choice(title: String, values: List<String>, selected: Int): Spinner {
            content.addView(TextView(activity).apply { text = title })
            return Spinner(activity).apply {
                adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
                setSelection(selected.coerceIn(0, values.lastIndex)); content.addView(this)
            }
        }
        fun toggle(title: String, checked: Boolean) = Switch(activity).apply {
            text = title; isChecked = checked; minHeight = (48 * activity.resources.displayMetrics.density).toInt()
            content.addView(this)
        }
        heading("外观")
        val theme = choice("界面主题", listOf("深色", "浅色", "跟随系统"), current.theme.ordinal)
        val syntax = choice("编辑器语法主题（词法配色）", listOf("跟随界面", "Dark+ 风格", "Light+ 风格"), current.syntax.ordinal)
        val sizes = (10..28).toList()
        val font = choice("代码字号 sp", sizes.map { it.toString() }, sizes.indexOf(current.fontSize))
        val terminalSizes = (10..24).toList()
        val terminal = choice("运行输出字号 sp", terminalSizes.map { it.toString() }, terminalSizes.indexOf(current.terminalFontSize))
        val animations = toggle("工作区过渡动画（遵从系统减少动画）", current.animations)
        heading("编辑与输入")
        val tabs = listOf(2, 4, 8)
        val indent = choice("Tab 宽度", tabs.map { "$it 列" }, tabs.indexOf(current.tabWidth))
        val spaces = toggle("使用空格缩进（关闭时插入 Tab）", current.useSpaces)
        val numbers = toggle("显示行号", current.lineNumbers)
        val wrap = toggle("自动换行", current.wordWrap)
        val autoIndent = toggle("自动缩进", current.autoIndent)
        val pairs = toggle("自动补齐括号和引号", current.autoPairs)
        val completion = toggle("自动显示基础词法补全", current.autoCompletion)
        val signatures = toggle(activity.getString(R.string.signature_setting), current.signatureHelp)
        heading("补全按键（实体键盘）")
        val completionTab = toggle("Tab 接受当前候选（关闭时缩进）", current.completionTab)
        val completionEnter = toggle("Enter 接受方向键明确选中的候选", current.completionEnter)
        val completionArrows = choice("上下方向键导航候选", listOf("仅主动触发后（Ctrl+Space/补全按钮）", "候选可见时", "始终移动代码光标"), current.completionArrows.ordinal)
        content.addView(TextView(activity).apply { setText(R.string.completion_key_help) })
        val symbols = toggle("显示编程快捷键栏", current.symbolBar)
        val collapse = toggle("输入代码时自动收起运行面板", current.collapsePanelOnIme)
        heading("终端与运行")
        content.addView(TextView(activity).apply {
            setText(R.string.python_terminal_help)
        })
        heading("文件保存")
        val autoSave = toggle("编辑停顿后自动保存", current.autoSave)
        val delays = listOf(700, 1500, 3000, current.autoSaveDelayMs).distinct().sorted()
        val delay = choice("停顿时间", delays.map { "$it ms" }, delays.indexOf(current.autoSaveDelayMs))
        content.addView(TextView(activity).apply {
            text = activity.getString(R.string.settings_save_help)
        })
        val dialog = AlertDialog.Builder(activity).setTitle("CodePocket 设置")
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("取消", null).setPositiveButton("保存设置") { _, _ ->
                save(current.copy(theme = UiTheme.values()[theme.selectedItemPosition], syntax = SyntaxTheme.values()[syntax.selectedItemPosition],
                    fontSize = sizes[font.selectedItemPosition], terminalFontSize = terminalSizes[terminal.selectedItemPosition],
                    tabWidth = tabs[indent.selectedItemPosition], useSpaces = spaces.isChecked, lineNumbers = numbers.isChecked,
                    wordWrap = wrap.isChecked, autoIndent = autoIndent.isChecked, autoPairs = pairs.isChecked,
                    autoCompletion = completion.isChecked, signatureHelp = signatures.isChecked, symbolBar = symbols.isChecked, collapsePanelOnIme = collapse.isChecked,
                    autoSave = autoSave.isChecked, autoSaveDelayMs = delays[delay.selectedItemPosition], animations = animations.isChecked,
                    completionTab = completionTab.isChecked, completionEnter = completionEnter.isChecked,
                    completionArrows = CompletionArrows.values()[completionArrows.selectedItemPosition]).normalized())
            }.create()
        if (!current.animations) dialog.window?.setWindowAnimations(0)
        dialog.show()
        return dialog
    }
}
