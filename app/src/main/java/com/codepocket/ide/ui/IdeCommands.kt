package com.codepocket.ide.ui

enum class IdeCommand(val title: String, val editorOnly: Boolean = false) {
    RUN_STOP("运行 / 停止  F5"), EXPLORER("资源管理器  Ctrl+B"), SEARCH("项目搜索  Ctrl+Shift+F"),
    NEW_FILE("新建文件  Ctrl+N"), NEW_FOLDER("新建文件夹"), RENAME("重命名当前文件"), DELETE("删除当前文件"),
    WORKSPACE("打开 / 切换工作区"), IMPORT_COPY("导入副本  Ctrl+O"), EXPORT("导出当前内容"),
    UNDO("撤销  Ctrl+Z"), REDO("重做  Ctrl+Y / Ctrl+Shift+Z"), FIND("当前文件查找  Ctrl+F"),
    REPLACE("替换  Ctrl+H"), GOTO_LINE("跳转行  Ctrl+G"), SAVE("保存  Ctrl+S"),
    RELOAD("重新载入当前文件"), RECOVER("恢复草稿"), COMPLETE("基础词法补全  Ctrl+Space", true),
    SIGNATURE("内置参数参考  Ctrl+Shift+Space", true), SNIPPET("内置代码片段", true),
    TAB("Tab / 下一占位符", true), UNINDENT("反缩进 / 上一占位符", true),
    BRACES("插入 {}", true), BRACKETS("插入 []", true), PARENS("插入 ()", true),
    SEMICOLON("插入 ;", true), EQUALS("插入 =", true), COLON("插入 :", true), LEFT("光标左移", true), RIGHT("光标右移", true),
    PANEL("显示运行输出"), PANEL_SIZE("调整面板尺寸"), CLEAR_TERMINAL("清空输出显示"),
    MAXIMIZE_TERMINAL("最大化 / 还原面板"), CLOSE_TERMINAL("收起运行面板"),
    PRESET_INPUT("编辑预设输入"), CLEAR_INPUT("清空当前文件预设输入"), HIDE_KEYBOARD("收起虚拟键盘"),
    THEME("切换深浅主题"), SETTINGS("设置"), SHORTCUTS("快捷键说明"), HELP("使用说明"),
    FONT_SIZE("代码字号  A±")
}
data class CommandContext(val busy: Boolean, val loaded: Boolean, val editorFocused: Boolean,
    val inputFocused: Boolean, val composing: Boolean, val running: Boolean, val saving: Boolean)

/** These collections are consumed by the actual native toolbars/menus, not a parallel test-only catalogue. */
object IdeCommands {
    val settingsMenuItems = listOf(IdeCommand.THEME, IdeCommand.SETTINGS, IdeCommand.SHORTCUTS, IdeCommand.HELP)
    val editorToolbarItems = listOf(IdeCommand.SAVE, IdeCommand.UNDO, IdeCommand.REDO, IdeCommand.FIND)
    val editorMenuItems = listOf(IdeCommand.FONT_SIZE, IdeCommand.REPLACE, IdeCommand.GOTO_LINE, IdeCommand.COMPLETE,
        IdeCommand.SIGNATURE, IdeCommand.SNIPPET, IdeCommand.HIDE_KEYBOARD)
    val explorerToolbarItems = listOf(IdeCommand.NEW_FILE, IdeCommand.NEW_FOLDER)
    val explorerMenuItems = listOf(IdeCommand.WORKSPACE, IdeCommand.IMPORT_COPY, IdeCommand.EXPORT,
        IdeCommand.RENAME, IdeCommand.DELETE, IdeCommand.RELOAD, IdeCommand.RECOVER)
    val terminalToolbarItems = listOf(IdeCommand.CLEAR_TERMINAL, IdeCommand.MAXIMIZE_TERMINAL, IdeCommand.CLOSE_TERMINAL)
    val terminalMenuItems = listOf(IdeCommand.PRESET_INPUT, IdeCommand.CLEAR_INPUT, IdeCommand.CLEAR_TERMINAL,
        IdeCommand.PANEL_SIZE, IdeCommand.MAXIMIZE_TERMINAL, IdeCommand.CLOSE_TERMINAL, IdeCommand.HIDE_KEYBOARD)
    val symbolBarItems = listOf(IdeCommand.TAB, IdeCommand.UNINDENT, IdeCommand.BRACES, IdeCommand.BRACKETS, IdeCommand.PARENS,
        IdeCommand.SEMICOLON, IdeCommand.EQUALS, IdeCommand.COLON, IdeCommand.LEFT, IdeCommand.RIGHT)
    fun globalMenu(layout: ToolbarLayout, panelVisible: Boolean): List<IdeCommand> = buildList {
        // Width fallbacks and a recovery entry keep hidden controls reachable without a giant command menu.
        if (!layout.run) add(IdeCommand.RUN_STOP)
        if (!layout.explorer) add(IdeCommand.EXPLORER)
        if (!layout.search) add(IdeCommand.SEARCH)
        if (!panelVisible) add(IdeCommand.PANEL)
        addAll(settingsMenuItems)
    }
    fun enabled(command: IdeCommand, state: CommandContext): Boolean {
        if (command == IdeCommand.RUN_STOP && state.running) return true
        if (state.busy) return false
        if (command in settingsMenuItems) return true
        if (!state.loaded) return false
        if (command == IdeCommand.FONT_SIZE) return !state.composing
        if (command.editorOnly && (!state.editorFocused || state.composing)) return false
        if (command in setOf(IdeCommand.UNDO, IdeCommand.REDO) &&
            ((!state.editorFocused && !state.inputFocused) || state.composing)) return false
        if (command == IdeCommand.CLEAR_INPUT && state.inputFocused && state.composing) return false
        if (command in setOf(IdeCommand.NEW_FILE, IdeCommand.NEW_FOLDER, IdeCommand.IMPORT_COPY,
                IdeCommand.WORKSPACE, IdeCommand.RELOAD, IdeCommand.RENAME, IdeCommand.DELETE) &&
            (state.running || state.saving)) return false
        return command != IdeCommand.RUN_STOP || !state.saving
    }
}
