package com.codepocket.ide

import java.text.Normalizer
import java.util.Locale

/** Search the existing setting identities; never create a second set of controls. */
internal object SettingsSearch {
    private val aliases = mapOf(
        "theme" to "appearance ui theme 界面 配色",
        "syntax" to "syntax highlight code theme 语法 高亮 配色",
        "fontSize" to "editor code font size 代码 字体",
        "terminalFontSize" to "terminal shell font size 终端 字体",
        "terminalTheme" to "terminal shell theme color 终端 配色",
        "tabWidth" to "tab indent indentation width 制表符 缩进",
        "useSpaces" to "spaces indent indentation 空格 缩进",
        "lineNumbers" to "line numbers gutter 行号",
        "wordWrap" to "word wrap wrapping 折行",
        "autoIndent" to "auto indent indentation 自动 缩进",
        "autoPairs" to "auto pairs brackets quotes 括号 引号",
        "autoCompletion" to "autocomplete completion suggestions 补全 候选",
        "completionTab" to "tab completion accept header include 头文件 补全",
        "completionEnter" to "enter completion accept 回车 补全",
        "completionArrows" to "arrows completion navigation 方向键 候选",
        "signatureHelp" to "signature parameters help 参数 提示",
        "symbolBar" to "symbol keyboard toolbar 符号栏",
        "autoSave" to "auto save autosave 自动 保存",
        "autoSaveDelayMs" to "auto save autosave delay 自动 保存 延迟",
        "formatOnSave" to "format on save formatter 格式化 保存",
        "cppBackend" to "c++ cpp local cloud backend 本地 云端 运行",
        "cppBuildScope" to "c++ cpp local build scope project 本地 构建 项目",
        "collapsePanelOnIme" to "keyboard ime collapse panel 键盘 收起 终端",
        "animations" to "animation motion reduced 动画",
        "cursorMovement" to "cursor caret motion 光标 移动",
        "cursorBlink" to "cursor caret blink 光标 闪烁"
    )
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    fun matches(query: String, key: String, vararg fields: String): Boolean {
        val words = normalize(query).split(Regex("\\s+")).filter(String::isNotEmpty)
        val text = normalize(listOf(key, aliases[key].orEmpty(), *fields).joinToString(" "))
        return words.all { text.contains(it) }
    }
}
