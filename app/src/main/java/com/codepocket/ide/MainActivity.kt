package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.InputFilter
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.method.LinkMovementMethod
import android.view.inputmethod.BaseInputConnection
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import org.json.JSONArray

/** VS Code-inspired, responsive native Android learning IDE. */
class MainActivity : Activity() {
    private lateinit var workspace: Workspace
    private val prefs by lazy { getSharedPreferences("codepocket_v2", MODE_PRIVATE) }
    private var dark = true
    private var activePath = "main.cpp"
    private var currentDirectory = ""
    private val expanded = mutableSetOf("")
    private val opened = mutableListOf<String>()
    private val running get() = RunSession.running
    private var result: RunResult? = null
    private var selectedOutput = 0
    private var showingSuggestion = false
    private var quietEdit = false
    private var explorerDialog: AlertDialog? = null
    private var wide = false
    private var consoleSide = false
    private var loadedPath: String? = null
    private var dirty = false
    private val handler = Handler(Looper.getMainLooper())
    private val cursors = mutableMapOf<String, Int>()
    private val autosave = Runnable { if (dirty) saveActive() }
    private val refreshEditor = Runnable {
        if (::editor.isInitialized) {
            EditorFeatures.colorize(editor.text, isCpp(), dark)
            highlightBrackets()
            updateSuggestions()
        }
    }
    private var explorerPane: View? = null
    private var explorerDivider: View? = null
    private var explorerVisible = true
    private var exportContent: String? = null
    private var exportPath: String? = null

    private lateinit var root: LinearLayout
    private lateinit var editor: CodeEditor
    private lateinit var input: EditText
    private lateinit var numbers: TextView
    private lateinit var fileTitle: TextView
    private lateinit var status: TextView
    private lateinit var consoleText: TextView
    private lateinit var tabsRow: LinearLayout
    private lateinit var suggestionRow: LinearLayout
    private lateinit var explorerList: LinearLayout
    private lateinit var runButton: TextView
    private lateinit var outputTabs: LinearLayout
    private lateinit var outputRegion: LinearLayout
    private lateinit var editorRegion: LinearLayout

    private val editorBackground get() = Color.parseColor(if (dark) "#1E1E1E" else "#FFFFFF")
    private val surface get() = Color.parseColor(if (dark) "#252526" else "#F3F3F3")
    private val panel get() = Color.parseColor(if (dark) "#181818" else "#F8F8F8")
    private val border get() = Color.parseColor(if (dark) "#414141" else "#D8D8D8")
    private val textForeground get() = Color.parseColor(if (dark) "#D4D4D4" else "#242424")
    private val muted get() = Color.parseColor(if (dark) "#9DA3AD" else "#636A74")
    private val accent get() = Color.parseColor(if (dark) "#007ACC" else "#0067B8")
    private val green get() = Color.parseColor(if (dark) "#89D185" else "#237A3B")
    private val red get() = Color.parseColor(if (dark) "#F48771" else "#BA1A1A")

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = prefs.getBoolean("dark", true)
        setTheme(if (dark) R.style.Theme_CodePocket else R.style.Theme_CodePocket_Light)
        super.onCreate(savedInstanceState)
        workspace = Workspace(this)
        val first = prefs.getString("activeFile", "main.cpp") ?: "main.cpp"
        activePath = if (runCatching { workspace.read(workspace.resolve(first)) }.isSuccess) first else ensureFallback()
        currentDirectory = prefs.getString("directory", "") ?: ""
        runCatching { JSONArray(prefs.getString("opened", "[]")) }.getOrNull()?.let { paths ->
            for (i in 0 until paths.length()) {
                val path = paths.getString(i)
                if (runCatching { workspace.read(workspace.resolve(path)) }.isSuccess && path !in opened) opened.add(path)
            }
        }
        if (activePath !in opened) opened.add(activePath)
        cursors[activePath] = prefs.getInt("caret", 0)
        explorerVisible = prefs.getBoolean("explorerVisible", true)
        wide = resources.configuration.screenWidthDp >= 720 && resources.configuration.screenHeightDp >= 480
        consoleSide = resources.configuration.screenWidthDp >= 600
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        createUi()
        loadActive()
        savedInstanceState?.getString("draft")?.takeIf { savedInstanceState.getString("draftPath") == activePath }?.let {
            quietEdit = true
            editor.setText(it)
            quietEdit = false
            dirty = true
            updateNumbers(); handler.post(refreshEditor)
            status.text = "已恢复尚未保存的编辑内容，请保存或导出"
        }
        input.setText(prefs.getString("stdin", "3 5") ?: "")
        exportContent = savedInstanceState?.getString("exportContent")
        exportPath = savedInstanceState?.getString("exportPath")
        result = RunSession.result
        showOutput(0)
    }

    override fun onStart() {
        super.onStart()
        RunSession.listener = { renderRunState() }
        renderRunState()
    }

    override fun onStop() {
        RunSession.listener = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persist()
        outState.putString("exportContent", exportContent)
        outState.putString("exportPath", exportPath)
        if (dirty) {
            outState.putString("draftPath", activePath)
            outState.putString("draft", editor.text.toString())
        }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        persist()
    }

    private fun persist() {
        if (::editor.isInitialized) saveActive()
        prefs.edit().putString("activeFile", activePath).putString("directory", currentDirectory)
            .putBoolean("dark", dark)
            .putBoolean("explorerVisible", explorerVisible)
            .putString("opened", JSONArray(opened).toString())
            .putInt("caret", if (::editor.isInitialized) editor.selectionStart.coerceAtLeast(0) else 0)
            .putString("stdin", if (::input.isInitialized) input.text.toString() else "")
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        explorerDialog?.dismiss()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
    private fun shape(color: Int, round: Int = 4, frame: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(round).toFloat()
        if (frame != null) setStroke(dp(1), frame)
    }
    private fun label(value: String, size: Float = 13f, color: Int = textForeground, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }
    private fun button(value: String, onClick: () -> Unit) = label(value, 12f).apply {
        setPadding(dp(11), dp(8), dp(11), dp(8))
        gravity = Gravity.CENTER
        minHeight = dp(36)
        background = shape(surface, 5, border)
        isFocusable = true; isClickable = true
        setOnClickListener { onClick() }
    }
    private fun space(w: Int = 5) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(w), 1) }

    private fun createUi() {
        window.statusBarColor = surface
        window.navigationBarColor = panel
        val lightNavigationBar = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or lightNavigationBar
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(editorBackground) }
        setContentView(root)

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(9), dp(5), dp(9), dp(5)); setBackgroundColor(surface)
        }
        top.addView(button("☰ 文件") { showExplorer() })
        top.addView(space(8))
        top.addView(label("⌘ CodePocket", 16f, accent, true), LinearLayout.LayoutParams(0, dp(38), 1f))
        top.addView(button(if (dark) "☀" else "☾") { toggleTheme() })
        top.addView(space(5))
        runButton = button("▶ 运行") { runCode() }
        runButton.setTextColor(Color.WHITE); runButton.background = shape(accent, 5)
        top.addView(runButton)
        root.addView(top)

        val toolbar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actions = LinearLayout(this).apply { setPadding(dp(8), dp(5), dp(8), dp(5)) }
        for ((name, command) in listOf<Pair<String, () -> Unit>>(
            "新建文件" to { askCreate(false) }, "新建文件夹" to { askCreate(true) },
            "导入" to { importFile() }, "导出" to { exportFile() },
            "保存  Ctrl+S" to { if (saveActive()) status.text = "✓ 已保存 $activePath" },
            "TAB" to { tab() }, "反缩进" to { indent(true) }, "{ }" to { insertText("{}", true) },
            "( )" to { insertText("()", true) }, "补全" to { requestCompletion() }
        )) {
            actions.addView(button(name, command)); actions.addView(space(5))
        }
        toolbar.addView(actions)
        root.addView(toolbar)

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        if (wide) {
            val explorer = makeExplorerPane()
            explorerPane = explorer
            explorer.visibility = if (explorerVisible) View.VISIBLE else View.GONE
            content.addView(explorer, LinearLayout.LayoutParams(dp(if (resources.configuration.screenWidthDp >= 1000) 242 else 205), -1))
            explorerDivider = View(this).apply { setBackgroundColor(border); visibility = explorer.visibility }
            content.addView(explorerDivider, LinearLayout.LayoutParams(dp(1), -1))
        }
        val work = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(work, LinearLayout.LayoutParams(0, -1, 1f))
        buildEditor(work)

        val console = buildConsole()
        if (consoleSide) {
            // On large landscape tablets the console docks to the right of the editor.
            val parent = editorRegion.parent as? ViewGroup
            parent?.removeView(editorRegion)
            val split = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            split.addView(editorRegion, LinearLayout.LayoutParams(0, -1, 1f))
            split.addView(console, LinearLayout.LayoutParams(dp(if (resources.configuration.screenWidthDp < 960) 235 else 290), -1))
            work.addView(split, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            work.addView(console, LinearLayout.LayoutParams(-1, dp(225)))
        }
        status = label("就绪", 11f, muted).apply {
            setPadding(dp(12), dp(5), dp(12), dp(5)); setBackgroundColor(surface)
        }
        root.addView(status)
        // Preserve code editing room when the on-screen keyboard occupies the viewport.
        root.viewTreeObserver.addOnGlobalLayoutListener {
            if (!::editor.isInitialized || !::outputRegion.isInitialized) return@addOnGlobalLayoutListener
            val bounds = android.graphics.Rect()
            root.getWindowVisibleDisplayFrame(bounds)
            val keyboard = root.rootView.height - bounds.bottom > dp(130)
            val editCode = keyboard && editor.hasFocus()
            outputRegion.visibility = if (editCode) View.GONE else View.VISIBLE
            status.visibility = if (keyboard) View.GONE else View.VISIBLE
        }
    }

    private fun buildEditor(work: LinearLayout) {
        editorRegion = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        work.addView(editorRegion, LinearLayout.LayoutParams(-1, 0, 1f))
        val tabs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(surface) }
        tabsRow = LinearLayout(this)
        tabs.addView(tabsRow)
        editorRegion.addView(tabs, LinearLayout.LayoutParams(-1, dp(39)))
        fileTitle = label("main.cpp", 11f, muted).apply { setPadding(dp(9), dp(2), dp(6), dp(2)) }
        editorRegion.addView(fileTitle)

        val editorBody = LinearLayout(this).apply { setBackgroundColor(editorBackground); orientation = LinearLayout.HORIZONTAL }
        numbers = label("1", 13.5f, muted).apply {
            gravity = Gravity.TOP or Gravity.END; typeface = Typeface.MONOSPACE
            setPadding(dp(3), dp(9), dp(6), 0); setLineSpacing(dp(3).toFloat(), 1f)
        }
        editorBody.addView(numbers, LinearLayout.LayoutParams(dp(42), -1))
        editor = CodeEditor(this).apply {
            textSize = 13.5f; typeface = Typeface.MONOSPACE; setTextColor(textForeground)
            setHintTextColor(muted); hint = "在这里输入 C++ 或 Python…"
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false; setHorizontallyScrolling(true)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(6), dp(9), dp(14), dp(9))
            setBackgroundColor(Color.TRANSPARENT)
            setSelectAllOnFocus(false)
            includeFontPadding = false
            filters = arrayOf(InputFilter.LengthFilter(Workspace.MAX_CHARS))
        }
        editorBody.addView(editor, LinearLayout.LayoutParams(0, -1, 1f))
        editorRegion.addView(editorBody, LinearLayout.LayoutParams(-1, 0, 1f))
        val suggestScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        suggestionRow = LinearLayout(this).apply { setPadding(dp(5), dp(2), dp(5), dp(2)); setBackgroundColor(surface) }
        suggestScroll.addView(suggestionRow)
        editorRegion.addView(suggestScroll, LinearLayout.LayoutParams(-1, dp(37)))
        editor.viewTreeObserver.addOnScrollChangedListener { numbers.scrollY = editor.scrollY }
        editor.addTextChangedListener(object : TextWatcher {
            private var before = ""
            private var change: TextChange? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!quietEdit) before = s.toString()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, beforeCount: Int, count: Int) {
                if (!quietEdit) change = EditorActions.typed(before, start, beforeCount, s.toString().substring(start, start + count), isCpp())
            }
            override fun afterTextChanged(s: Editable?) {
                if (quietEdit) return
                val edit = change
                change = null
                if (edit != null && s != null && BaseInputConnection.getComposingSpanStart(s) < 0) applyChange(edit)
                dirty = true
                updateNumbers()
                handler.removeCallbacks(refreshEditor); handler.postDelayed(refreshEditor, 120)
                handler.removeCallbacks(autosave); handler.postDelayed(autosave, 700)
            }
        })
        editor.setOnClickListener { updateSuggestions() }
        editor.onCaretChanged = {
            if (!quietEdit) {
                handler.removeCallbacks(refreshEditor); handler.postDelayed(refreshEditor, 120)
            }
        }
        refreshTabs()
    }

    private fun buildConsole(): LinearLayout {
        outputRegion = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(panel) }
        outputTabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        outputRegion.addView(outputTabs)
        val stdinCaption = label("标准输入 stdin", 11f, muted).apply { setPadding(dp(10), dp(3), 0, dp(3)) }
        outputRegion.addView(stdinCaption)
        input = EditText(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 12f
            setTextColor(textForeground); setHintTextColor(muted)
            gravity = Gravity.TOP or Gravity.START; hint = "例如：3 5"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = shape(editorBackground, 4, border)
            setPadding(dp(7), dp(5), dp(7), dp(5))
        }
        outputRegion.addView(input, LinearLayout.LayoutParams(-1, dp(57)))
        val scroll = ScrollView(this)
        consoleText = label("", 12f, green).apply {
            typeface = Typeface.MONOSPACE; gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(8), dp(10), dp(8)); setTextIsSelectable(true)
        }
        scroll.addView(consoleText)
        outputRegion.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return outputRegion
    }

    private fun makeExplorerPane(): LinearLayout {
        val panelView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(surface) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(7), dp(5), dp(7)) }
        row.addView(label("资源管理器", 12f, muted, true), LinearLayout.LayoutParams(0, dp(26), 1f))
        row.addView(button("+文件") { askCreate(false) })
        row.addView(button("+目录") { askCreate(true) })
        panelView.addView(row)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        explorerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(explorerList)
        panelView.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderExplorer()
        return panelView
    }

    private fun showExplorer() {
        if (wide) {
            explorerVisible = !explorerVisible
            explorerPane?.visibility = if (explorerVisible) View.VISIBLE else View.GONE
            explorerDivider?.visibility = explorerPane?.visibility ?: View.GONE
            prefs.edit().putBoolean("explorerVisible", explorerVisible).apply()
            return
        }
        if (explorerDialog?.isShowing == true) { explorerDialog?.dismiss(); return }
        val content = makeExplorerPane()
        content.minimumHeight = dp(340)
        explorerDialog = AlertDialog.Builder(this).setTitle("项目文件 · CodePocket")
            .setView(content).setPositiveButton("完成", null).create().also { it.show() }
        content.minimumHeight = dp(340)
    }

    private fun renderExplorer() {
        if (!::explorerList.isInitialized) return
        explorerList.removeAllViews()
        val rootLabel = label("▾  CODEPOCKET", 12f, muted, true).apply {
            setPadding(dp(10), dp(8), 0, dp(8))
            setOnClickListener { currentDirectory = ""; status.text = "新建位置：项目根目录" }
        }
        explorerList.addView(rootLabel)
        fun visit(directory: File, level: Int) {
            if (level > 18) return
            workspace.children(directory).forEach { file ->
                val relative = workspace.path(file)
                val folder = file.isDirectory
                val open = relative in expanded
                val glyph = if (folder) (if (open) "▾ 📁 " else "▸ 📁 ") else (if (file.extension == "py") "🐍 " else "◇ ")
                val item = label(glyph + file.name, 13f, if (relative == activePath) accent else textForeground).apply {
                    setPadding(dp(12 + level * 15), dp(8), dp(4), dp(8))
                    if (relative == activePath) background = shape(editorBackground, 3)
                    isClickable = true; isFocusable = true
                    setOnClickListener {
                        if (folder) {
                            currentDirectory = relative
                            if (open) expanded.remove(relative) else expanded.add(relative)
                            renderExplorer()
                        } else { switchFile(relative); explorerDialog?.dismiss(); explorerDialog = null }
                    }
                    setOnLongClickListener { fileMenu(file); true }
                    setOnGenericMotionListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
                            event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
                            fileMenu(file); true
                        } else false
                    }
                }
                explorerList.addView(item)
                if (folder && open) visit(file, level + 1)
            }
        }
        visit(workspace.root, 0)
        val hint = label("点击文件打开 · 长按或鼠标右键管理", 10f, muted).apply {
            setPadding(dp(10), dp(13), dp(4), dp(10))
        }
        explorerList.addView(hint)
    }

    private fun fileMenu(file: File) {
        val entries = if (file.isDirectory)
            arrayOf("在此新建文件", "在此新建文件夹", "重命名", "移动到…", "删除")
        else arrayOf("打开", "重命名", "移动到…", "删除")
        AlertDialog.Builder(this).setTitle(workspace.path(file)).setItems(entries) { _, index ->
            val action = entries[index]
            when (action) {
                "打开" -> { switchFile(workspace.path(file)); explorerDialog?.dismiss() }
                "在此新建文件" -> { currentDirectory = workspace.path(file); askCreate(false) }
                "在此新建文件夹" -> { currentDirectory = workspace.path(file); askCreate(true) }
                "重命名" -> askRename(file)
                "移动到…" -> askMove(file)
                "删除" -> askDelete(file)
            }
        }.show()
    }

    private fun nameDialog(title: String, value: String = "", onConfirm: (String) -> Unit) {
        val field = EditText(this).apply { setSingleLine(); setText(value); setSelection(text.length); setPadding(dp(18), dp(12), dp(18), dp(12)) }
        AlertDialog.Builder(this).setTitle(title).setView(field).setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                try { onConfirm(field.text.toString()) } catch (e: Exception) { alert(e.message ?: "操作失败") }
            }.show()
    }

    private fun askCreate(directory: Boolean) {
        if (!allowFileOperation()) return
        val parent = runCatching { workspace.resolve(currentDirectory) }.getOrDefault(workspace.root)
            .let { if (it.isDirectory) it else workspace.root }
        nameDialog(if (directory) "在 ${workspace.path(parent).ifBlank { "项目根目录" }} 新建文件夹" else "新建代码文件",
            if (directory) "" else "untitled.${if (isCpp()) "cpp" else "py"}") { name ->
            if (!saveActive()) return@nameDialog
            val item = workspace.create(parent, name, directory)
            if (directory) { expanded.add(workspace.path(item)); currentDirectory = workspace.path(item) }
            else switchFile(workspace.path(item))
            expanded.add(workspace.path(parent)); renderExplorer()
        }
    }

    private fun askRename(file: File) = nameDialog("重命名", file.name) { newName ->
        if (!allowFileOperation() || !saveActive()) return@nameDialog
        val oldPath = workspace.path(file)
        val renamed = workspace.rename(file, newName)
        rebasePaths(oldPath, workspace.path(renamed))
        renderExplorer(); refreshTabs(); loadActive()
    }

    private fun askMove(file: File) {
        if (!allowFileOperation()) return
        val folders = workspace.folders().filter { dest ->
            !(file.isDirectory && (dest == file || dest.canonicalPath.startsWith(file.canonicalPath + File.separator)))
        }
        AlertDialog.Builder(this).setTitle("移动到文件夹")
            .setItems(folders.map { workspace.path(it).ifBlank { "/ 工作区" } }.toTypedArray()) { _, which ->
                try {
                    if (!allowFileOperation() || !saveActive()) return@setItems
                    val old = workspace.path(file)
                    val moved = workspace.move(file, folders[which])
                    rebasePaths(old, workspace.path(moved))
                    expanded.add(workspace.path(folders[which]))
                    renderExplorer(); refreshTabs(); loadActive()
                } catch (e: Exception) { alert(e.message ?: "移动失败") }
            }.show()
    }

    private fun askDelete(file: File) {
        if (!allowFileOperation()) return
        AlertDialog.Builder(this).setTitle("确认删除？")
            .setMessage("将永久删除 ${file.name}${if (file.isDirectory) " 及其全部子文件" else ""}。此操作不可撤销。")
            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                try {
                    val path = workspace.path(file)
                    if (!allowFileOperation() || !saveActive()) return@setPositiveButton
                    workspace.delete(file)
                    if (currentDirectory == path || currentDirectory.startsWith("$path/")) currentDirectory = ""
                    opened.removeAll { it == path || it.startsWith("$path/") }
                    if (activePath == path || activePath.startsWith("$path/")) {
                        if (opened.isEmpty()) opened.add(ensureFallback())
                        activePath = opened.last()
                    }
                    expanded.removeAll { it == path || it.startsWith("$path/") }
                    renderExplorer(); refreshTabs(); loadActive()
                } catch (e: Exception) { alert(e.message ?: "删除失败") }
            }.show()
    }

    private fun ensureFallback(): String {
        val existing = workspace.folders().asSequence().flatMap { workspace.children(it).asSequence() }
            .firstOrNull { it.isFile && runCatching { workspace.read(it) }.isSuccess }
        if (existing != null) return workspace.path(existing)
        var name = "main.cpp"
        var suffix = 1
        while (workspace.resolve(name).exists()) name = "main${suffix++}.cpp"
        val file = workspace.create(workspace.root, name, false)
        workspace.write(file, Workspace.CPP_SAMPLE)
        return name
    }

    private fun rebasePaths(previous: String, next: String) {
        fun rebase(path: String): String = if (path == previous) next
            else if (path.startsWith("$previous/")) next + path.removePrefix(previous) else path
        activePath = rebase(activePath)
        for (i in opened.indices) opened[i] = rebase(opened[i])
        val oldExpanded = expanded.toList(); expanded.clear(); expanded.addAll(oldExpanded.map(::rebase))
        currentDirectory = rebase(currentDirectory)
    }

    private fun switchFile(relative: String): Boolean {
        if (relative == activePath && loadedPath == activePath) return true
        val content = try { workspace.read(workspace.resolve(relative)) }
            catch (e: Exception) { alert("打开文件失败：${e.message}"); return false }
        if (!saveActive()) return false
        cursors[activePath] = editor.selectionStart.coerceAtLeast(0)
        activePath = relative
        if (!opened.contains(relative)) opened.add(relative)
        loadActive(content); refreshTabs(); renderExplorer()
        return true
    }

    private fun closeTab(relative: String) {
        if (!saveActive()) return
        opened.remove(relative)
        if (opened.isEmpty()) opened.add(ensureFallback())
        if (relative == activePath) { activePath = opened.last(); loadActive() }
        refreshTabs(); renderExplorer()
    }

    private fun refreshTabs() {
        if (!::tabsRow.isInitialized) return
        tabsRow.removeAllViews()
        opened.toList().forEach { path ->
            if (!workspace.resolve(path).isFile) { opened.remove(path); return@forEach }
            val one = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = shape(if (path == activePath) editorBackground else surface, 0) }
            val name = label(" ${if (path.endsWith(".py")) "🐍" else "◇"} ${path.substringAfterLast('/')} ", 12f,
                if (path == activePath) accent else textForeground).apply {
                setPadding(dp(8), 0, dp(4), 0); setOnClickListener { switchFile(path) }
            }
            one.addView(name, LinearLayout.LayoutParams(-2, dp(38)))
            one.addView(label("×", 17f, muted).apply {
                setPadding(dp(4), 0, dp(11), 0); setOnClickListener { closeTab(path) }
            })
            tabsRow.addView(one)
        }
    }

    private fun loadActive(content: String? = null) {
        try {
            val code = content ?: workspace.read(workspace.resolve(activePath))
            quietEdit = true
            editor.setText(code)
            loadedPath = activePath
            dirty = false
            editor.setSelection((cursors[activePath] ?: 0).coerceIn(0, editor.length()))
            quietEdit = false
            EditorFeatures.colorize(editor.text, isCpp(), dark)
            updateNumbers(); updateSuggestions()
            fileTitle.text = "工作区 / $activePath"
            status.text = if (isCpp()) "C++17 · Wandbox 云端运行" else "Python 3.13 · 本地离线运行"
        } catch (e: Exception) { quietEdit = false; alert("打开文件失败：${e.message}") }
    }

    private fun saveActive(): Boolean {
        if (!::editor.isInitialized || loadedPath == null) return true
        if (loadedPath != activePath) { status.text = "保存已阻止：文件与编辑缓冲区不匹配"; return false }
        if (!dirty) return true
        return try {
            workspace.write(workspace.resolve(activePath), editor.text.toString())
            dirty = false
            true
        } catch (e: Exception) {
            if (::status.isInitialized) status.text = "保存失败，内容仍在编辑器中：${e.message}"
            false
        }
    }

    private fun allowFileOperation(): Boolean {
        if (!running) return true
        alert("程序运行中，请结束后再新建、导入、移动、重命名或删除文件。")
        return false
    }

    private fun isCpp() = !activePath.lowercase().endsWith(".py")
    private fun updateNumbers() {
        if (!::numbers.isInitialized) return
        val lines = editor.text.count { it == '\n' } + 1
        numbers.text = (1..lines.coerceAtMost(10000)).joinToString("\n")
    }

    private fun suggestionOptions(): List<String> = EditorFeatures.suggestions(editor.text.toString(), editor.selectionStart, isCpp())
    private fun updateSuggestions() {
        if (!::suggestionRow.isInitialized || !::editor.isInitialized) return
        val items = suggestionOptions()
        showingSuggestion = items.isNotEmpty()
        suggestionRow.removeAllViews()
        if (items.isEmpty()) suggestionRow.addView(label("TAB = 4 空格   ·   Ctrl+Space = 补全   ·   Ctrl+S = 保存", 11f, muted))
        else {
            suggestionRow.addView(label("提示  ", 11f, muted))
            items.forEach { value -> suggestionRow.addView(button(value) { acceptSuggestion(value) }) }
        }
    }
    private fun requestCompletion() {
        updateSuggestions()
        if (!showingSuggestion) status.text = "键入词语前缀后，使用 Tab 或点击候选词补全"
        editor.requestFocus()
    }
    private fun acceptSuggestion(value: String) {
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(start)
        val prefix = EditorFeatures.prefix(editor.text.toString(), start)
        val begin = (start - prefix.length).coerceAtLeast(0)
        editor.text.replace(begin, end, value)
        editor.setSelection((begin + value.length).coerceAtMost(editor.length()))
    }
    private fun insertText(snippet: String, caretInside: Boolean = false) {
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(start)
        quietEdit = true
        editor.text.replace(start, end, snippet)
        quietEdit = false
        dirty = true
        updateNumbers(); handler.post(refreshEditor)
        handler.removeCallbacks(autosave); handler.postDelayed(autosave, 700)
        editor.setSelection((start + snippet.length - if (caretInside) 1 else 0).coerceAtMost(editor.length()))
        editor.requestFocus()
    }

    /** Shift+Tab removes one indent. Tab indents selection or accepts a suggestion. */
    private fun indent(reverse: Boolean) {
        applyChange(EditorActions.indent(editor.text.toString(), editor.selectionStart, editor.selectionEnd, reverse))
        dirty = true
        updateNumbers(); handler.post(refreshEditor)
        handler.removeCallbacks(autosave); handler.postDelayed(autosave, 700)
        editor.requestFocus()
    }

    private fun applyChange(change: TextChange) {
        quietEdit = true
        try {
            editor.text.replace(change.start, change.end, change.text)
            editor.setSelection(change.selectionStart.coerceIn(0, editor.length()), change.selectionEnd.coerceIn(0, editor.length()))
        } finally { quietEdit = false }
    }

    private fun tab() {
        val options = suggestionOptions()
        if (editor.selectionStart == editor.selectionEnd && options.isNotEmpty()) acceptSuggestion(options.first())
        else indent(false)
    }

    private fun highlightBrackets() {
        val text = editor.text
        text.getSpans(0, text.length, BackgroundColorSpan::class.java).forEach { text.removeSpan(it) }
        val match = EditorFeatures.matchingBrackets(text.toString(), editor.selectionStart, isCpp()) ?: return
        for (position in listOf(match.first, match.second)) {
            text.setSpan(BackgroundColorSpan(if (dark) 0xFF3A3D41.toInt() else 0xFFD6EBFF.toInt()), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun toggleTheme() {
        if (!saveActive()) return
        persist()
        dark = !dark
        prefs.edit().putBoolean("dark", dark).apply()
        recreate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_TAB &&
            ::editor.isInitialized && editor.hasFocus()) {
            if (event.isShiftPressed) indent(true)
            else tab()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_S -> { if (saveActive()) status.text = "✓ 已保存 $activePath"; return true }
                KeyEvent.KEYCODE_N -> { askCreate(false); return true }
                KeyEvent.KEYCODE_O -> { importFile(); return true }
                KeyEvent.KEYCODE_B -> { showExplorer(); return true }
                KeyEvent.KEYCODE_SPACE -> { requestCompletion(); return true }
                KeyEvent.KEYCODE_ENTER -> { runCode(); return true }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_F5) { runCode(); return true }
        return super.dispatchKeyEvent(event)
    }

    private fun importFile() {
        if (!allowFileOperation() || !saveActive()) return
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        }, 201)
    }
    private fun exportFile() {
        if (!saveActive()) return
        exportContent = editor.text.toString()
        exportPath = activePath
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"; putExtra(Intent.EXTRA_TITLE, activePath.substringAfterLast('/'))
        }, 202)
    }

    @Deprecated("Compatible with Android API 24 without AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                201 -> {
                    if (!allowFileOperation() || !saveActive()) return
                    val content = contentResolver.openInputStream(uri)?.let(Workspace::utf8Reader)?.use {
                        Workspace.readLimited(it, Workspace.MAX_CHARS)
                    }?.removePrefix("\uFEFF") ?: error("无法读取文件")
                    require(content.length <= 200000) { "文件过大" }
                    require(!content.contains('\u0000')) { "仅支持 UTF-8 文本文件" }
                    val rawName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "import.cpp"
                    val name = rawName.substringAfterLast('/').substringAfterLast('\\')
                    val parent = workspace.resolve(currentDirectory).let { if (it.isDirectory) it else workspace.root }
                    val item = workspace.create(parent, name, false)
                    try { workspace.write(item, content) }
                    catch (e: Exception) { workspace.delete(item); throw e }
                    switchFile(workspace.path(item))
                    renderExplorer(); status.text = "✓ 已导入 ${item.name}"
                }
                202 -> {
                    val output = contentResolver.openOutputStream(uri, "wt") ?: error("无法写入")
                    output.use { it.write((exportContent ?: error("导出快照已失效，请重新导出")).toByteArray(Charsets.UTF_8)) }
                    status.text = "✓ 已导出 $exportPath"
                    exportContent = null; exportPath = null
                }
            }
        } catch (e: Exception) { alert("导入/导出失败：${e.message}") }
    }

    private fun runCode(consented: Boolean = false) {
        if (running) return
        if (!saveActive() || loadedPath != activePath) return
        val code = editor.text.toString()
        if (code.isBlank()) { alert("请先输入代码"); return }
        if (code.length > 100000 || input.length() > 20000) { alert("代码最多 10 万字符；输入最多 2 万字符"); return }
        val cpp = isCpp(); val stdin = input.text.toString()
        if (cpp && !consented && !prefs.getBoolean("wandboxConsent", false)) {
            AlertDialog.Builder(this).setTitle("C++ 云端运行")
                .setMessage("当前 C++ 源码和标准输入将发送至第三方 Wandbox 服务。请勿提交密码或私人资料。Python 在本地运行。")
                .setNegativeButton("取消", null).setPositiveButton("同意并运行") { _, _ ->
                    prefs.edit().putBoolean("wandboxConsent", true).apply()
                    runCode(true)
                }.show()
            return
        }
        val runningFile = workspace.resolve(activePath).absolutePath
        val projectRoot = workspace.root.absolutePath
        RunSession.start(code, stdin, cpp, runningFile, projectRoot)
    }

    private fun renderRunState() {
        if (!::runButton.isInitialized) return
        runButton.text = if (running) "运行中…" else "▶ 运行"
        runButton.isEnabled = !running
        result = RunSession.result
        if (running) {
            showOutput(0)
            status.text = "正在执行，请稍候…"
        } else result?.let { response ->
            showOutput(if (response.stderr.isNotBlank()) 1 else 0)
            status.text = "${if (response.successful) "✓" else "✕"} ${response.kind.label} (${response.elapsedMs}ms)"
        }
    }

    private fun showOutput(type: Int) {
        selectedOutput = type
        if (!::outputTabs.isInitialized) return
        outputTabs.removeAllViews()
        listOf("输出", "错误", "运行信息").forEachIndexed { index, item ->
            outputTabs.addView(button(item) { showOutput(index) }.apply {
                background = shape(if (index == type) editorBackground else surface, 0)
                if (index == type) setTextColor(accent)
            })
        }
        outputTabs.addView(button("定位错误") { gotoError() }.apply { visibility = if (result?.errorLine != null) View.VISIBLE else View.GONE })
        val current = result
        consoleText.text = when {
            current == null -> "运行程序后，这里显示输出、编译错误和状态。"
            type == 0 -> current.stdout.ifEmpty { "（没有标准输出）" }
            type == 1 -> current.stderr.ifEmpty { "（没有错误信息）" }
            else -> "执行引擎：${current.engine}\n结果：${current.kind.label}\n退出码：${current.exitCode}\n耗时：${current.elapsedMs}ms" +
                (current.errorLine?.let { "\n错误行：$it" } ?: "")
        }
        consoleText.setTextColor(if (type == 1) red else if (type == 0) green else textForeground)
        if (type == 1 && current?.errorLine != null) {
            val clickable = android.text.SpannableString(consoleText.text)
            clickable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { gotoError() }
            }, 0, clickable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            consoleText.text = clickable
            consoleText.movementMethod = LinkMovementMethod.getInstance()
        } else consoleText.movementMethod = null
    }

    private fun gotoError() {
        val line = result?.errorLine ?: return
        val file = result?.errorFile ?: result?.sourcePath
        if (file != null) {
            val relative = runCatching { workspace.path(File(file)) }.getOrNull()
            if (relative == null) { alert("错误位置不在项目工作区中"); return }
            if (!switchFile(relative)) return
        }
        val src = editor.text.toString(); var position = 0
        for (i in 1 until line) {
            val next = src.indexOf('\n', position)
            if (next < 0) break
            position = next + 1
        }
        editor.requestFocus(); editor.setSelection(position.coerceAtMost(editor.length()))
    }
    private fun alert(message: String) { AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定", null).show() }
}
