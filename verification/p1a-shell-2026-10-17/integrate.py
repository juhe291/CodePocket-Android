from pathlib import Path
import hashlib
import json

repo = Path(r'C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android')
stage = Path(__file__).parent
main = repo / 'app/src/main/java/com/codepocket/ide/MainActivity.kt'
s = main.read_text(encoding='utf-8')
assert hashlib.sha256(main.read_bytes()).hexdigest() == 'd4413e34b92729f7c795193fabfd7a8779b8e7e2184a2b660d9260ac4097ddf8'
def replace(a, b):
    global s
    assert s.count(a) == 1, (s.count(a), a[:90])
    s = s.replace(a, b)
def region(a, b, text):
    global s
    start = s.index(a); end = s.index(b, start)
    s = s[:start] + text + s[end:]

replace('import java.io.File', 'import com.codepocket.ide.ui.*\nimport java.io.File')
replace('    private lateinit var panelMaxButton: TextView\n    private lateinit var panelSizeButton: TextView', '    private lateinit var panelToolbar: PanelToolbar\n    private var presetInputExpanded = false\n    private var commandMenu: android.widget.PopupMenu? = null')
replace('    private lateinit var detailToolbar: HorizontalScrollView\n', '')
replace('    private lateinit var fileTitle: TextView\n', '')
replace('    private lateinit var runButton: TextView', '    private lateinit var mainToolbar: MainToolbar')
region('    private val editorBackground get()', '\n    override fun onCreate', '''    private val palette get() = DesignTokens.palette(dark)
    private val editorBackground get() = palette.editor
    private val surface get() = palette.surface
    private val panel get() = palette.panel
    private val border get() = palette.border
    private val textForeground get() = palette.foreground
    private val muted get() = palette.muted
    private val accent get() = palette.accent
    private val green get() = palette.success
    private val red get() = palette.error
''')
replace('            if (::panelResize.isInitialized) panelResize.isEnabled = !value && panelLayout?.resizable == true', '            if (::panelResize.isInitialized) panelResize.isEnabled = !value && panelLayout?.resizable == true\n            updateMainToolbarState()')
replace('                    panelVisible = prefs.getBoolean("panelVisible", true)', '                    panelVisible = prefs.getBoolean("panelVisible", true)\n                    presetInputExpanded = prefs.getBoolean(prefKey("presetInputExpanded"), false)')
replace('            .putBoolean("panelVisible", panelVisible).putString("panelLayout", panelState.toJson()).apply()', '            .putBoolean(prefKey("presetInputExpanded"), presetInputExpanded)\n            .putBoolean("panelVisible", panelVisible).putString("panelLayout", panelState.toJson()).apply()')
replace('        toolDialog?.dismiss()\n        hideCompletion();', '        toolDialog?.dismiss(); commandMenu?.dismiss(); commandMenu = null\n        hideCompletion();')
replace('        hideCompletion(); clearSnippet(); completionPopup = null; signaturePopup = null', '        commandMenu?.dismiss(); commandMenu = null\n        hideCompletion(); clearSnippet(); completionPopup = null; signaturePopup = null')
region('        val top = LinearLayout(this)', '        val content = LinearLayout(this)', '''        mainToolbar = MainToolbar(this, palette,
            { dispatchCommand(IdeCommand.EXPLORER) }, { dispatchCommand(IdeCommand.RUN_STOP) },
            ::showCommandMenu, { alert("${workspace.title} / $activePath") })
        root.addView(mainToolbar, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))
        updateShellTitle()
        keyboardToolbar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE }
        val keyRow = LinearLayout(this).apply { setBackgroundColor(surface) }
        listOf("Tab" to IdeCommand.TAB, "⇤" to IdeCommand.UNINDENT,
            "{}" to IdeCommand.BRACES, "[]" to IdeCommand.BRACKETS, "()" to IdeCommand.PARENS,
            ";" to IdeCommand.SEMICOLON, "=" to IdeCommand.EQUALS, ":" to IdeCommand.COLON,
            "←" to IdeCommand.LEFT, "→" to IdeCommand.RIGHT, "补全" to IdeCommand.COMPLETE,
            "参数" to IdeCommand.SIGNATURE, "片段" to IdeCommand.SNIPPET).forEach { (name, command) ->
            keyRow.addView(IdeControls.key(this, palette, name, command.title) { dispatchCommand(command) },
                LinearLayout.LayoutParams(-2, dp(DesignTokens.TOUCH)))
        }
        keyboardToolbar.addView(keyRow)
        root.addView(keyboardToolbar, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))

''')
replace('        fileTitle.visibility = if (layout.showDetails) View.VISIBLE else View.GONE\n        suggestionScroll.visibility = if (layout.showDetails) View.VISIBLE else View.GONE', '        suggestionScroll.visibility = if (WorkspaceLayoutPolicy.showSnippet(snippetSession != null, visible, height)) View.VISIBLE else View.GONE')
replace('        detailToolbar.visibility = if (!visible) View.VISIBLE else View.GONE\n', '')
region('    private fun showPanelSizeMenu(anchor: View)', '    private fun applyPanelLayout()', '''    private fun showPanelSizeMenu(anchor: View) {
        panelResize.cancelGesture(); commandMenu?.dismiss()
        val menu = android.widget.PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, R.string.panel_smaller).isEnabled = panelLayout?.resizable == true
        menu.menu.add(0, 2, 1, R.string.panel_larger).isEnabled = panelLayout?.resizable == true
        menu.menu.add(0, 3, 2, R.string.panel_reset)
        menu.menu.add(0, 4, 3, R.string.panel_clear_input)
        menu.menu.add(0, 5, 4, if (presetInputExpanded) "收起预设输入" else "编辑预设输入")
        menu.menu.add(0, 6, 5, if (panelState.maximized) "还原运行面板" else "最大化运行面板")
        menu.menu.add(0, 7, 6, "收起运行面板")
        menu.setOnMenuItemClickListener { item ->
            anchor.post {
                if (!isDestroyed && !workspaceBusy && anchor.isAttachedToWindow) when (item.itemId) {
                    4 -> input.setText("")
                    5 -> setPresetInputExpanded(!presetInputExpanded)
                    6 -> setPanelMaximized(!panelState.maximized)
                    7 -> closePanel()
                    else -> {
                        val stamp = capturePanelViewport(); hideCompletion()
                        if (item.itemId == 3) panelState = PanelState(maximized = panelState.maximized)
                        else panelLayout?.let { spec ->
                            panelState = spec.resize(panelState, spec.extent + spec.available * if (item.itemId == 2) .05f else -.05f)
                        }
                        applyPanelLayout(); persistPanel(); restorePanelViewport(stamp)
                    }
                }
            }
            true
        }
        commandMenu = menu; menu.show()
    }

    private fun setPresetInputExpanded(expanded: Boolean) {
        if (workspaceBusy || !::input.isInitialized) return
        captureSession(); hideCompletion()
        presetInputExpanded = expanded
        if (expanded) {
            panelVisible = true; outputRegion.visibility = View.VISIBLE
            input.visibility = View.VISIBLE
            input.requestFocus() // Do not force an IME for a hardware-keyboard user.
        } else if (input.hasFocus()) {
            hideKeyboard(); lastFocus = "editor"
            if (editorRegion.visibility == View.VISIBLE) editor.requestFocus() else outputRegion.requestFocus()
        }
        updateImeLayout(); persistNavigation()
    }

''')
region('        val compact = (imeVisible && input.hasFocus())', '        if (previous != spec)', '''        val preset = WorkspaceLayoutPolicy.preset(presetInputExpanded, imeVisible, input.hasFocus(), panelHeight)
        panelInputHeading.visibility = if (preset.showCaption) View.VISIBLE else View.GONE
        panelOutputScroll.visibility = if (preset.showTabs) View.VISIBLE else View.GONE
        input.visibility = if (preset.showInput) View.VISIBLE else View.GONE
        input.layoutParams?.let { old ->
            val target = dp(preset.inputHeight)
            if (old.height != target) input.layoutParams = old.apply { this.height = target }
        }
        panelToolbar.setState(panelState.maximized, presetInputExpanded)
''')
replace('        if (view.focus == "stdin" && panelVisible) input.requestFocus()', '''        presetInputExpanded = WorkspaceLayoutPolicy.restorePreset(presetInputExpanded, view.focus, panelVisible)
        input.visibility = if (presetInputExpanded) View.VISIBLE else View.GONE
        if (view.focus == "stdin" && panelVisible) input.requestFocus()''')
replace('        editorRegion.addView(tabs, LinearLayout.LayoutParams(-1, dp(39)))\n        fileTitle = label("main.cpp", 11f, muted).apply { setPadding(dp(9), dp(2), dp(6), dp(2)) }\n        editorRegion.addView(fileTitle)', '        editorRegion.addView(tabs, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))')
replace('        val suggestScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }', '        val suggestScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE }')
replace('        editorRegion.addView(suggestScroll, LinearLayout.LayoutParams(-1, dp(37)))', '        editorRegion.addView(suggestScroll, LinearLayout.LayoutParams(-1, dp(DesignTokens.SNIPPET_HINT)))')
replace('        completionHint = label(getString(R.string.completion_hint), 11f, muted)', '        completionHint = label("", 11f, muted)')
region('        val panelHeading = LinearLayout(this)', '        outputTabs = LinearLayout(this)', '''        panelToolbar = PanelToolbar(this, palette, { setPresetInputExpanded(!presetInputExpanded) },
            { setPanelMaximized(!panelState.maximized) }, ::closePanel, ::showPanelSizeMenu)
        outputRegion.addView(panelToolbar, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))
''')
replace('        outputRegion.addView(input, LinearLayout.LayoutParams(-1, dp(57)))', '        input.visibility = if (presetInputExpanded) View.VISIBLE else View.GONE\n        panelInputHeading.visibility = input.visibility\n        outputRegion.addView(input, LinearLayout.LayoutParams(-1, dp(64)))')
region('        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(7)', '        val scroll = ScrollView(this).apply { isFillViewport = true }', '''        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, 0, 0) }
        row.addView(label("资源管理器", 12f, muted, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(IdeControls.icon(this, palette, IdeIcon.MORE, "文件与工作区命令") { showCommandMenu(row) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        panelView.addView(row)
''')
replace('        val rootLabel = label("▾  ${workspace.title}", 12f, muted, true).apply {', '        val rootLabel = label(workspace.title, 12f, muted, true).apply {\n            decorateFileLabel(this, IdeIcon.FOLDER_OPEN, workspace.title)')
replace('                val glyph = if (folder) (if (open) "▾ 📁 " else "▸ 📁 ") else (if (file.extension == "py") "🐍 " else "◇ ")\n                val item = label(glyph + file.name, 13f, if (relative == activePath) accent else textForeground).apply {', '''                val icon = if (folder) (if (open) IdeIcon.FOLDER_OPEN else IdeIcon.FOLDER) else IdeIconDrawable.file(relative)
                val item = label(file.name, 13f, if (relative == activePath) accent else textForeground).apply {
                    decorateFileLabel(this, icon, relative + if (folder) (if (open) "，已展开" else "，已折叠") else "")
                    minHeight = dp(DesignTokens.TOUCH)''')
replace('                renderExplorer(); refreshTabs(); fileTitle.text = "${workspace.title} / $activePath"', '                renderExplorer(); refreshTabs(); updateShellTitle()')
replace('        fileTitle.text = "${workspace.title} / $activePath"', '        updateShellTitle()')
region('            val name = label(" ${if (path == activePath && dirty)', '            tabsRow.addView(one)', '''            val name = label("${if (path == activePath && dirty) "● " else ""}${path.substringAfterLast('/')}", 12f,
                if (path == activePath) accent else textForeground).apply {
                decorateFileLabel(this, IdeIconDrawable.file(path), path)
                maxWidth = dp(240); setPadding(dp(8), 0, dp(4), 0)
                setOnClickListener { switchFile(path) }
                setOnLongClickListener { alert(path); true }
            }
            one.addView(name, LinearLayout.LayoutParams(-2, dp(DesignTokens.TOUCH)))
            one.addView(IdeControls.icon(this, palette, IdeIcon.CLOSE, "关闭 $path") { closeTab(path) },
                LinearLayout.LayoutParams(dp(DesignTokens.TOUCH), dp(DesignTokens.TOUCH)))
''')
replace('        tabsRow.removeAllViews()\n        opened.toList()', '        updateShellTitle()\n        tabsRow.removeAllViews()\n        opened.toList()')
replace('        val text = if (session == null) getString(R.string.completion_hint)', '        val text = if (session == null) ""')
replace('        if (completionHint.text.toString() != text) completionHint.text = text', '''        if (completionHint.text.toString() != text) completionHint.text = text
        val height = (root.height / resources.displayMetrics.density).toInt()
        suggestionScroll.visibility = if (WorkspaceLayoutPolicy.showSnippet(session != null, imeVisible, height)) View.VISIBLE else View.GONE''')
replace('        if (!::runButton.isInitialized) return\n        runButton.text = if (running) "■ 停止" else "▶ 运行"\n        runButton.isEnabled = true', '        if (!::mainToolbar.isInitialized) return\n        updateMainToolbarState()')

# Original file/save/engine calls remain the sole side-effect implementations.
insert = '''    private fun decorateFileLabel(view: TextView, icon: IdeIcon, fullPath: String) {
        view.setSingleLine(); view.ellipsize = android.text.TextUtils.TruncateAt.END
        val drawable = IdeIconDrawable(icon, muted, dp(DesignTokens.ICON)).apply { setBounds(0, 0, dp(20), dp(20)) }
        view.setCompoundDrawablesRelative(drawable, null, null, null); view.compoundDrawablePadding = dp(6)
        IdeControls.describe(view, fullPath)
    }

    private fun commandContext(): CommandContext {
        val focused = when {
            ::input.isInitialized && input.hasFocus() -> input
            ::editor.isInitialized && editor.hasFocus() -> editor
            else -> null
        }
        return CommandContext(workspaceBusy, ::editor.isInitialized && loadedPath == activePath,
            ::editor.isInitialized && editor.hasFocus(), ::input.isInitialized && input.hasFocus(),
            focused != null && BaseInputConnection.getComposingSpanStart(focused.text) >= 0, running, saving)
    }

    private fun updateMainToolbarState() {
        if (::mainToolbar.isInitialized) mainToolbar.setRunState(running, IdeCommands.enabled(IdeCommand.RUN_STOP, commandContext()))
    }

    private fun updateShellTitle() {
        if (!::mainToolbar.isInitialized) return
        mainToolbar.setTitle("${if (dirty) "● " else ""}${activePath.substringAfterLast('/')}", "${workspace.title} / $activePath")
        updateMainToolbarState()
    }

    private fun showCommandMenu(anchor: View) {
        commandMenu?.dismiss()
        commandMenu = IdeCommandMenu.show(anchor, commandContext(), ::dispatchCommand)
    }

    private fun dispatchCommand(command: IdeCommand) {
        if (isDestroyed || !IdeCommands.enabled(command, commandContext())) return
        when (command) {
            IdeCommand.RUN_STOP -> if (running) RunSession.stop() else runCode()
            IdeCommand.EXPLORER -> showExplorer()
            IdeCommand.NEW_FILE -> askCreate(false)
            IdeCommand.NEW_FOLDER -> askCreate(true)
            IdeCommand.WORKSPACE -> workspaceMenu()
            IdeCommand.IMPORT_COPY -> importFile()
            IdeCommand.EXPORT -> exportFile()
            IdeCommand.SAVE -> saveActive { status.text = "✓ 已保存 $activePath" }
            IdeCommand.RELOAD -> reloadActive()
            IdeCommand.RECOVER -> recoverDrafts()
            IdeCommand.FIND -> showSearch(false)
            IdeCommand.REPLACE -> showSearch(true)
            IdeCommand.GOTO_LINE -> showGoToLine()
            IdeCommand.UNDO, IdeCommand.REDO -> {
                clearSnippet(); hideCompletion()
                val target = if (input.hasFocus()) input else editor
                target.onTextContextMenuItem(if (command == IdeCommand.UNDO) android.R.id.undo else android.R.id.redo)
            }
            IdeCommand.TAB -> tab()
            IdeCommand.UNINDENT -> if (!moveSnippet(true)) indent(true)
            IdeCommand.BRACES -> insertText("{}", true)
            IdeCommand.BRACKETS -> insertText("[]", true)
            IdeCommand.PARENS -> insertText("()", true)
            IdeCommand.SEMICOLON -> insertText(";")
            IdeCommand.EQUALS -> insertText("=")
            IdeCommand.COLON -> insertText(":")
            IdeCommand.LEFT, IdeCommand.RIGHT -> {
                hideCompletion(); clearSnippet()
                val key = if (command == IdeCommand.LEFT) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); editor.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            }
            IdeCommand.COMPLETE -> requestCompletion()
            IdeCommand.SIGNATURE -> requestSignatureHelp()
            IdeCommand.SNIPPET -> showSnippetMenu()
            IdeCommand.PANEL -> togglePanel()
            IdeCommand.PRESET_INPUT -> setPresetInputExpanded(true)
            IdeCommand.PANEL_SIZE -> showPanelSizeMenu(panelToolbar.menuAnchor)
            IdeCommand.HIDE_KEYBOARD -> hideKeyboard()
            IdeCommand.THEME -> toggleTheme()
            IdeCommand.SETTINGS -> showSettings()
            IdeCommand.HELP -> alert(getString(R.string.shell_help))
        }
    }

'''
replace('    private fun hideKeyboard() {', insert + '    private fun hideKeyboard() {')
main.write_text(s, encoding='utf-8', newline='\n')
ui = main.parent / 'ui'; ui.mkdir(exist_ok=True)
for f in stage.glob('*.kt'):
    target = repo / 'app/src/test/java/com/codepocket/ide' / f.name if f.name.endswith('Test.kt') else ui / f.name
    target.write_bytes(f.read_bytes())
strings = repo / 'app/src/main/res/values/strings.xml'
xml = strings.read_text(encoding='utf-8')
assert 'name="shell_help"' not in xml
xml = xml.replace('</resources>', '''    <string name="shell_help">Python 在设备本地运行；C++ 源码和 stdin 会发送至 Wandbox。当前为预设输入、结束后展示输出，不是系统 Shell。\n\n左上角管理文件，更多菜单提供文件、编辑、显示和设置命令。运行时原位置可停止；面板键盘图标展开当前文件预设输入，面板菜单可清空。\n\nCtrl+S 保存；Ctrl+N 新建；Ctrl+O 导入副本；Ctrl+B 文件管理器；F5 运行；Ctrl+F/H/G 查找、替换、跳转行；Ctrl+Space 词法补全；Ctrl+Shift+Space 内置参数参考。补全、参数和片段尚不具备语义分析能力。撤销/重做调用系统文本历史，仍待设备联合验收。</string>
</resources>''')
strings.write_text(xml, encoding='utf-8', newline='\n')
evidence = repo / 'verification/p1a-shell-2026-10-17'; evidence.mkdir(exist_ok=True)
script = (repo / 'verification/ui-audit-2026-10-17/verify-ui-audit.ps1').read_text(encoding='utf-8')
script = script.replace('verification/ui-audit-2026-10-17', 'verification/p1a-shell-2026-10-17').replace('CodePocket-UI-audit-baseline-debug.apk', 'CodePocket-P1A-shell-debug.apk')
(evidence / 'verify-p1a.ps1').write_text(script, encoding='utf-8-sig')
print('Applied P1-A shell integration')
