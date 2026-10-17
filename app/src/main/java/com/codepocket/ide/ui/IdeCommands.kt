package com.codepocket.ide.ui

enum class IdeCommand(val title: String, val editorOnly: Boolean = false) {
    RUN_STOP("运行 / 停止  F5"), EXPLORER("资源管理器  Ctrl+B"),
    NEW_FILE("新建文件  Ctrl+N"), NEW_FOLDER("新建文件夹"), WORKSPACE("选择 / 切换工作区"),
    IMPORT_COPY("导入副本  Ctrl+O"), EXPORT("导出当前内容"), SAVE("保存  Ctrl+S"), RELOAD("重新载入文件"), RECOVER("恢复草稿"),
    FIND("查找  Ctrl+F"), REPLACE("替换  Ctrl+H"), GOTO_LINE("跳转行  Ctrl+G"), UNDO("撤销  Ctrl+Z"), REDO("重做  Ctrl+Y / Ctrl+Shift+Z"),
    TAB("Tab / 下一占位符", true), UNINDENT("反缩进 / 上一占位符", true),
    BRACES("插入 {}", true), BRACKETS("插入 []", true), PARENS("插入 ()", true),
    SEMICOLON("插入 ;", true), EQUALS("插入 =", true), COLON("插入 :", true),
    LEFT("光标左移", true), RIGHT("光标右移", true),
    COMPLETE("基础词法补全  Ctrl+Space", true), SIGNATURE("内置参数参考  Ctrl+Shift+Space", true), SNIPPET("内置代码片段", true),
    PANEL("展开 / 收起运行面板"), PRESET_INPUT("编辑当前文件的预设输入"), PANEL_SIZE("面板尺寸与操作"),
    HIDE_KEYBOARD("收起虚拟键盘"), THEME("切换深浅主题"), SETTINGS("设置"), HELP("使用说明与快捷键")
}
data class CommandGroup(val title: String, val items: List<IdeCommand>)
data class CommandContext(val busy: Boolean, val loaded: Boolean, val editorFocused: Boolean,
    val inputFocused: Boolean, val composing: Boolean, val running: Boolean, val saving: Boolean)

object IdeCommands {
    val groups = listOf(
        CommandGroup("文件", listOf(IdeCommand.NEW_FILE, IdeCommand.NEW_FOLDER, IdeCommand.WORKSPACE, IdeCommand.IMPORT_COPY,
            IdeCommand.EXPORT, IdeCommand.SAVE, IdeCommand.RELOAD, IdeCommand.RECOVER)),
        CommandGroup("编辑", listOf(IdeCommand.FIND, IdeCommand.REPLACE, IdeCommand.GOTO_LINE, IdeCommand.UNDO, IdeCommand.REDO,
            IdeCommand.COMPLETE, IdeCommand.SIGNATURE, IdeCommand.SNIPPET, IdeCommand.TAB, IdeCommand.UNINDENT,
            IdeCommand.BRACES, IdeCommand.BRACKETS, IdeCommand.PARENS, IdeCommand.SEMICOLON, IdeCommand.EQUALS,
            IdeCommand.COLON, IdeCommand.LEFT, IdeCommand.RIGHT)),
        CommandGroup("显示", listOf(IdeCommand.EXPLORER, IdeCommand.PANEL, IdeCommand.PRESET_INPUT, IdeCommand.PANEL_SIZE,
            IdeCommand.HIDE_KEYBOARD, IdeCommand.THEME)),
        CommandGroup("设置与帮助", listOf(IdeCommand.SETTINGS, IdeCommand.HELP))
    )
    val menuCommands get() = listOf(IdeCommand.RUN_STOP) + groups.flatMap { it.items }
    fun enabled(command: IdeCommand, state: CommandContext): Boolean {
        if (command == IdeCommand.RUN_STOP && state.running) return true
        if (state.busy) return false
        if (command in setOf(IdeCommand.SETTINGS, IdeCommand.HELP)) return true
        if (!state.loaded) return false
        if (command.editorOnly && (!state.editorFocused || state.composing)) return false
        if (command in setOf(IdeCommand.UNDO, IdeCommand.REDO) &&
            ((!state.editorFocused && !state.inputFocused) || state.composing)) return false
        if (command in setOf(IdeCommand.NEW_FILE, IdeCommand.NEW_FOLDER, IdeCommand.IMPORT_COPY, IdeCommand.WORKSPACE, IdeCommand.RELOAD) &&
            (state.running || state.saving)) return false
        return command != IdeCommand.RUN_STOP || !state.saving
    }
}
