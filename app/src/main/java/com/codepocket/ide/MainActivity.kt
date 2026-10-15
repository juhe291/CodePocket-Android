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
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
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
    private lateinit var sessions: EditorSessions
    private var quietInput = false
    private var panelVisible = true
    private var imeVisible = false
    private var lastFocus = "editor"
    private lateinit var detailToolbar: HorizontalScrollView
    private lateinit var suggestionScroll: HorizontalScrollView
    private lateinit var keyboardToolbar: HorizontalScrollView
    private val saveSession = Runnable { captureSession(); persistSessions() }
    private val autosave = Runnable { if (dirty) saveActive() }
    private val refreshEditor = Runnable {
        if (::editor.isInitialized) {
            if (BaseInputConnection.getComposingSpanStart(editor.text) >= 0) return@Runnable
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
    private lateinit var drafts: DraftStore
    private var workspaceBusy = false
        set(value) {
            field = value
            if (::editor.isInitialized) editor.isEnabled = !value
            if (::input.isInitialized) input.isEnabled = !value
        }
    private var saving = false
    private var afterSave: (() -> Unit)? = null
    private var selectedTree: String? = null
    private var editRevision = 0L
    private var loadedVersion: String? = null
    private val checkpointDraft = Runnable { queueDraft() }
    private fun prefKey(name: String) = "$name:${workspace.identity}"

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
        drafts = DraftStore(File(filesDir, "drafts")) { a, b -> android.system.Os.rename(a.path, b.path) }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(TextView(this).apply { text = "正在载入 CodePocket…"; setPadding(24, 36, 24, 24) })
        startWorkspace(prefs.getString("selectedTree", null), savedInstanceState)
    }

    private fun startWorkspace(tree: String?, saved: Bundle? = null) {
        if ((running && ::editor.isInitialized) || workspaceBusy || saving) return
        explorerDialog?.dismiss(); explorerDialog = null
        workspaceBusy = true
        if (::editor.isInitialized) { captureSession(); persistSessions(); persistNavigation() }
        handler.removeCallbacks(autosave); handler.removeCallbacks(checkpointDraft)
        WorkspaceIo.executor.execute {
            val prepared = runCatching {
                saved?.getString("draft")?.let { text -> saved.getString("draftKey")?.let { key ->
                    drafts.put(key, saved.getString("draftPath").orEmpty(), text, saved.getString("draftBase"))
                } }
                val ws = if (tree == null) Workspace(this) else Workspace.fromTree(this, Uri.parse(tree))
                fun get(name: String, legacy: String) = prefs.getString("$name:${ws.identity}",
                    if (!ws.external) prefs.getString(name, legacy) else legacy) ?: legacy
                val paths = mutableListOf<String>()
                runCatching { JSONArray(get("opened", "[]")) }.getOrNull()?.let { a ->
                    for (i in 0 until a.length()) {
                        val path = a.optString(i)
                        if (runCatching { ws.resolve(path).isFile }.getOrDefault(false) && path !in paths) paths.add(path)
                    }
                }
                var active = get("activeFile", "main.cpp")
                if (!runCatching { ws.resolve(active).isFile }.getOrDefault(false)) {
                    active = ws.folders().asSequence().flatMap { ws.children(it).asSequence() }
                        .firstOrNull { it.isFile && it.extension.lowercase() in setOf("py", "cpp", "h", "hpp") }?.let(ws::path)
                        ?: ws.create(ws.root, "main.cpp", false).let { ws.write(it, Workspace.CPP_SAMPLE); ws.path(it) }
                }
                if (active !in paths) paths.add(active)
                val state = EditorSessions(ws.identity, prefs.getString("sessions:${ws.identity}", "[]") ?: "[]")
                if (!ws.external && !prefs.contains("sessions:internal")) {
                    state.file(active).stdin = prefs.getString("stdin", "3 5") ?: ""
                    state.file(active).view = EditorViewState(prefs.getInt("caret", 0), prefs.getInt("caret", 0))
                }
                val code = ws.read(ws.resolve(active))
                val draft = drafts.read(state.key(active))
                PreparedWorkspace(ws, state, paths, active, code, draft)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                workspaceBusy = false
                prepared.onSuccess { ready ->
                    workspace = ready.workspace; sessions = ready.sessions
                    selectedTree = tree
                    opened.clear(); opened.addAll(ready.paths)
                    activePath = ready.active; loadedPath = null; dirty = false
                    currentDirectory = prefs.getString(prefKey("directory"), "") ?: ""
                    expanded.clear(); expanded.add("")
                    explorerVisible = prefs.getBoolean("explorerVisible", true)
                    panelVisible = prefs.getBoolean("panelVisible", true)
                    wide = resources.configuration.screenWidthDp >= 720 && resources.configuration.screenHeightDp >= 480
                    consoleSide = resources.configuration.screenWidthDp >= 600
                    createUi(); loadActive(ready.code, ready.draft)
                    saved?.getString("draft")?.takeIf { saved.getString("draftPath") == activePath }?.let {
                        quietEdit = true; editor.setText(it); quietEdit = false; dirty = true
                        restoreSession(); updateNumbers(); handler.post(refreshEditor); queueDraft()
                    }
                    exportContent = saved?.getString("exportSnapshot")?.let { name ->
                        runCatching { File(filesDir, name).readText() }.getOrNull()
                    }
                    exportPath = saved?.getString("exportPath")
                    prefs.edit().putString("selectedTree", tree).apply()
                    if (tree != null) rememberWorkspace(tree, workspace.title)
                    renderRunState()
                }.onFailure { error ->
                    if (!::root.isInitialized) {
                        setContentView(TextView(this).apply { text = "工作区载入失败，请重新授权或打开内部项目。" })
                    }
                    AlertDialog.Builder(this).setTitle("工作区载入失败")
                        .setMessage("${error.message}\n恢复草稿仍保留在应用内。请选择原目录重新授权。")
                        .setPositiveButton("选择目录") { _, _ -> selectDirectory() }
                        .setNegativeButton("内部项目") { _, _ -> startWorkspace(null) }.show()
                }
            }
        }
    }

    private data class PreparedWorkspace(val workspace: Workspace, val sessions: EditorSessions,
        val paths: List<String>, val active: String, val code: String, val draft: Draft?)

    private fun <T> ioAction(message: String, work: () -> T, done: (T) -> Unit) {
        if (workspaceBusy || saving) { if (::status.isInitialized) status.text = "请等待文件操作完成"; return }
        workspaceBusy = true
        status.text = message
        WorkspaceIo.executor.execute {
            val value = runCatching(work)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                workspaceBusy = false
                value.onSuccess(done).onFailure { alert("${it.message}\n未保存内容和恢复草稿会保留；可导出或重新选择目录。") }
            }
        }
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
        if (exportContent != null) outState.putString("exportSnapshot", "pending-export.txt")
        outState.putString("exportPath", exportPath)
        if (dirty) {
            outState.putString("draftPath", activePath)
            outState.putString("draft", editor.text.toString())
            outState.putString("draftKey", sessions.key(activePath))
            outState.putString("draftBase", loadedVersion)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        persist()
    }

    private fun persist() {
        if (!::workspace.isInitialized || !::editor.isInitialized) return
        captureSession(); persistSessions(); queueDraft(); saveActive()
        persistNavigation()
    }

    private fun persistNavigation() {
        prefs.edit().putString(prefKey("activeFile"), activePath).putString(prefKey("directory"), currentDirectory)
            .putBoolean("dark", dark).putBoolean("explorerVisible", explorerVisible)
            .putString(prefKey("opened"), JSONArray(opened).toString())
            .putBoolean("panelVisible", panelVisible).apply()
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
        setOnClickListener {
            if (!workspaceBusy || (running && value.contains("运行"))) onClick()
            else if (::status.isInitialized) status.text = "文件操作中，请稍候"
        }
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
        top.addView(button("收键盘") { hideKeyboard() })
        top.addView(space(3))
        top.addView(button("结果") {
            if (input.hasFocus()) { hideKeyboard(); editor.requestFocus() }
            panelVisible = !panelVisible
            updateImeLayout()
        })
        top.addView(button(if (dark) "☀" else "☾") { toggleTheme() })
        top.addView(space(5))
        runButton = button("▶ 运行") { if (running) RunSession.stop() else runCode() }
        runButton.setTextColor(Color.WHITE); runButton.background = shape(accent, 5)
        top.addView(runButton)
        root.addView(top)

        val toolbar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actions = LinearLayout(this).apply { setPadding(dp(8), dp(5), dp(8), dp(5)) }
        for ((name, command) in listOf<Pair<String, () -> Unit>>(
            "新建文件" to { askCreate(false) }, "新建文件夹" to { askCreate(true) },
            "工作区" to { workspaceMenu() }, "导入副本" to { importFile() }, "导出" to { exportFile() },
            "重新载入文件" to { reloadActive() }, "恢复草稿" to { recoverDrafts() },
            "保存  Ctrl+S" to { saveActive { status.text = "✓ 已保存 $activePath" } },
            "TAB" to { tab() }, "反缩进" to { indent(true) }, "{ }" to { insertText("{}", true) },
            "( )" to { insertText("()", true) }, "补全" to { requestCompletion() }
        )) {
            actions.addView(button(name, command)); actions.addView(space(5))
        }
        detailToolbar = toolbar
        toolbar.addView(actions)
        root.addView(toolbar)
        keyboardToolbar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE }
        val keyRow = LinearLayout(this)
        listOf<Pair<String, () -> Unit>>("TAB" to { tab() }, "反缩进" to { indent(true) },
            "{ }" to { insertText("{}", true) }, "( )" to { insertText("()", true) }).forEach { (name, action) ->
            keyRow.addView(button(name, action)); keyRow.addView(space(3))
        }
        keyboardToolbar.addView(keyRow); root.addView(keyboardToolbar)

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
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                updateImeLayout(insets.isVisible(WindowInsets.Type.ime()))
                insets
            }
        }
        root.viewTreeObserver.addOnGlobalLayoutListener {
            if (Build.VERSION.SDK_INT >= 30) {
                root.rootWindowInsets?.let { updateImeLayout(it.isVisible(WindowInsets.Type.ime())) }
            } else {
                val bounds = android.graphics.Rect()
                window.decorView.getWindowVisibleDisplayFrame(bounds)
                val systemBars = root.rootWindowInsets?.let { it.stableInsetTop + it.stableInsetBottom } ?: 0
                updateImeLayout(window.decorView.height - bounds.height() - systemBars > dp(100))
            }
        }
    }

    private fun hideKeyboard() {
        if (Build.VERSION.SDK_INT >= 30) window.insetsController?.hide(WindowInsets.Type.ime())
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow((currentFocus ?: editor).windowToken, 0)
    }

    private fun updateImeLayout(visible: Boolean = imeVisible) {
        if (!::input.isInitialized || !::status.isInitialized) return
        val changed = visible != imeVisible
        imeVisible = visible
        val height = (root.height / resources.displayMetrics.density).toInt()
        val layout = ImeLayoutPolicy.resolve(visible, input.hasFocus(), height, panelVisible)
        outputRegion.visibility = if (layout.showPanel) View.VISIBLE else View.GONE
        fileTitle.visibility = if (layout.showDetails) View.VISIBLE else View.GONE
        suggestionScroll.visibility = if (layout.showDetails) View.VISIBLE else View.GONE
        keyboardToolbar.visibility = if (visible && editor.hasFocus()) View.VISIBLE else View.GONE
        editorRegion.visibility = if (layout.compactInput && height < 260) View.GONE else View.VISIBLE
        detailToolbar.visibility = if (!visible) View.VISIBLE else View.GONE
        status.visibility = if (layout.showDetails) View.VISIBLE else View.GONE
        if (!consoleSide) {
            val target = dp(if (layout.compactInput) minOf(136, maxOf(116, height - 56)) else minOf(225, maxOf(136, height / 3)))
            input.layoutParams?.let { params ->
                val inputHeight = dp(if (layout.compactInput && height < 260) 44 else 57)
                if (params.height != inputHeight) input.layoutParams = params.apply { this.height = inputHeight }
            }
            if (outputRegion.layoutParams.height != target) {
                outputRegion.layoutParams = outputRegion.layoutParams.apply { this.height = target }
            }
        }
        if (changed && visible) editor.post { if (editor.hasFocus()) editor.bringPointIntoView(editor.selectionEnd) }
    }

    private fun persistSessions() {
        if (::sessions.isInitialized) prefs.edit().putString("sessions:${sessions.workspaceId}", sessions.toJson()).apply()
    }

    private fun captureSession() {
        if (!::editor.isInitialized || !::input.isInitialized || loadedPath == null) return
        sessions.file(loadedPath!!).apply {
            stdin = input.text.toString()
            view = EditorViewState(editor.selectionStart, editor.selectionEnd, editor.scrollX, editor.scrollY,
                lastFocus, input.selectionStart, input.selectionEnd)
        }
    }

    private fun restoreSession() {
        val session = sessions.file(activePath)
        quietInput = true
        input.setText(session.stdin)
        quietInput = false
        val view = session.view.clamp(editor.length(), input.length())
        editor.setSelection(view.start, view.end)
        input.setSelection(view.inputStart, view.inputEnd)
        lastFocus = view.focus
        if (view.focus == "stdin" && panelVisible) input.requestFocus() else editor.requestFocus()
        editor.post {
            editor.scrollTo(view.scrollX, view.scrollY)
            numbers.scrollY = editor.scrollY
            updateImeLayout()
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
            isSaveEnabled = false
            filters = arrayOf(InputFilter.LengthFilter(Workspace.MAX_CHARS))
        }
        editorBody.addView(editor, LinearLayout.LayoutParams(0, -1, 1f))
        editorRegion.addView(editorBody, LinearLayout.LayoutParams(-1, 0, 1f))
        val suggestScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        suggestionScroll = suggestScroll
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
                dirty = true; editRevision++
                handler.removeCallbacks(checkpointDraft); handler.postDelayed(checkpointDraft, 200)
                updateNumbers()
                handler.removeCallbacks(refreshEditor); handler.postDelayed(refreshEditor, 120)
                handler.removeCallbacks(autosave); handler.postDelayed(autosave, 700)
            }
        })
        editor.onFocusChangeListener = View.OnFocusChangeListener { _, focused ->
            if (focused) { lastFocus = "editor"; updateImeLayout() }
        }
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
        val inputHeading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        inputHeading.addView(stdinCaption, LinearLayout.LayoutParams(0, -2, 1f))
        inputHeading.addView(button("清空输入") { input.setText("") })
        outputRegion.addView(inputHeading)
        input = EditText(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 12f
            setTextColor(textForeground); setHintTextColor(muted)
            gravity = Gravity.TOP or Gravity.START; hint = "例如：3 5"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = shape(editorBackground, 4, border)
            setPadding(dp(7), dp(5), dp(7), dp(5))
        }
        input.isSaveEnabled = false
        input.filters = arrayOf(InputFilter.LengthFilter(20000))
        input.onFocusChangeListener = View.OnFocusChangeListener { _, focused ->
            if (focused) { lastFocus = "stdin"; updateImeLayout() }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (quietInput || !::sessions.isInitialized || loadedPath == null) return
                sessions.file(loadedPath!!).stdin = s.toString()
                handler.removeCallbacks(saveSession); handler.postDelayed(saveSession, 200)
            }
        })
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
        row.addView(button("目录") { workspaceMenu() })
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
        if (workspaceBusy) return
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
        val rootLabel = label("▾  ${workspace.title}", 12f, muted, true).apply {
            setPadding(dp(10), dp(8), 0, dp(8))
            setOnClickListener { currentDirectory = ""; status.text = "新建位置：项目根目录" }
        }
        explorerList.addView(rootLabel)
        fun visit(directory: File, level: Int) {
            if (level > 20) return
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
                        if (workspaceBusy) return@setOnClickListener
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
        nameDialog(if (directory) "新建文件夹" else "新建代码文件",
            if (directory) "" else "untitled.${if (isCpp()) "cpp" else "py"}") { name ->
            saveActive {
                ioAction("正在创建…", { workspace.create(parent, name, directory) }) { item ->
                    if (directory) { expanded.add(workspace.path(item)); currentDirectory = workspace.path(item) }
                    else switchFile(workspace.path(item))
                    expanded.add(workspace.path(parent)); renderExplorer()
                }
            }
        }
    }

    private fun askRename(file: File) = nameDialog("重命名", file.name) { newName ->
        if (!allowFileOperation()) return@nameDialog
        saveActive {
            captureSession()
            val old = workspace.path(file)
            ioAction("正在重命名…", { workspace.rename(file, newName) }) { renamed ->
                rebasePaths(old, workspace.path(renamed)); loadedPath = activePath
                renderExplorer(); refreshTabs(); fileTitle.text = "${workspace.title} / $activePath"
            }
        }
    }

    private fun askMove(file: File) {
        if (!allowFileOperation()) return
        val folders = workspace.folders().filter { dest ->
            !(file.isDirectory && (dest == file || dest.path.startsWith(file.path + File.separator)))
        }
        AlertDialog.Builder(this).setTitle("移动到文件夹")
            .setItems(folders.map { workspace.path(it).ifBlank { "/ 工作区" } }.toTypedArray()) { _, which ->
                saveActive {
                    captureSession()
                    val old = workspace.path(file)
                    ioAction("正在移动…", { workspace.move(file, folders[which]) }) { moved ->
                        rebasePaths(old, workspace.path(moved)); loadedPath = activePath
                        expanded.add(workspace.path(folders[which])); renderExplorer(); refreshTabs()
                    }
                }
            }.show()
    }

    private fun askDelete(file: File) {
        if (!allowFileOperation()) return
        AlertDialog.Builder(this).setTitle("确认删除？")
            .setMessage("将永久删除 ${file.name}${if (file.isDirectory) " 及其全部子文件" else ""}。此操作不可撤销。")
            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                saveActive {
                    val path = workspace.path(file)
                    ioAction("正在删除…", { workspace.delete(file); ensureFallback() }) { fallback ->
                        sessions.removeTree(path); persistSessions()
                        if (currentDirectory == path || currentDirectory.startsWith("$path/")) currentDirectory = ""
                        opened.removeAll { it == path || it.startsWith("$path/") }
                        if (activePath == path || activePath.startsWith("$path/")) {
                            if (opened.isEmpty()) opened.add(fallback)
                            activePath = opened.last(); loadedPath = null; loadActive()
                        }
                        expanded.removeAll { it == path || it.startsWith("$path/") }
                        renderExplorer(); refreshTabs()
                    }
                }
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
        sessions.removeTree(next)
        sessions.rebase(previous, next); persistSessions()
        activePath = rebase(activePath)
        for (i in opened.indices) opened[i] = rebase(opened[i])
        val oldExpanded = expanded.toList(); expanded.clear(); expanded.addAll(oldExpanded.map(::rebase))
        currentDirectory = rebase(currentDirectory)
    }

    private fun switchFile(relative: String, after: (() -> Unit)? = null): Boolean {
        if (workspaceBusy) return false
        if (relative == activePath && loadedPath == activePath) { after?.invoke(); return true }
        saveActive {
            captureSession(); persistSessions()
            val key = sessions.key(relative)
            ioAction("正在打开 $relative…", {
                workspace.read(workspace.resolve(relative)) to drafts.read(key)
            }) { (content, draft) ->
                activePath = relative
                if (relative !in opened) opened.add(relative)
                loadActive(content, draft); refreshTabs(); renderExplorer(); after?.invoke()
            }
        }
        return false
    }

    private fun closeTab(relative: String) {
        if (workspaceBusy) return
        saveActive {
            captureSession(); persistSessions()
            opened.remove(relative)
            if (relative == activePath) {
                val next = opened.lastOrNull()
                if (next != null) switchFile(next)
                else ioAction("正在打开项目…", { ensureFallback() }) { path -> opened.add(path); switchFile(path); refreshTabs() }
            }
            refreshTabs(); renderExplorer()
        }
    }

    private fun refreshTabs() {
        if (!::tabsRow.isInitialized) return
        tabsRow.removeAllViews()
        opened.toList().forEach { path ->
            if (!workspace.resolve(path).isFile) { opened.remove(path); return@forEach }
            val one = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = shape(if (path == activePath) editorBackground else surface, 0) }
            val name = label(" ${if (path == activePath && dirty) "● " else ""}${if (path.endsWith(".py")) "🐍" else "◇"} ${path.substringAfterLast('/')} ", 12f,
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

    private fun loadActive(content: String? = null, restored: Draft? = null) {
        if (content == null) {
            val path = activePath; val key = sessions.key(path)
            ioAction("正在载入…", { workspace.read(workspace.resolve(path)) to drafts.read(key) }) { (text, draft) ->
                loadActive(text, draft)
            }
            return
        }
        quietEdit = true
        editor.setText(restored?.text ?: content)
        loadedPath = activePath
        loadedVersion = restored?.baseHash ?: DocumentTree.digest(content)
        dirty = restored != null && restored.text != content
        restoreSession(); quietEdit = false
        EditorFeatures.colorize(editor.text, isCpp(), dark)
        updateNumbers(); updateSuggestions()
        fileTitle.text = "${workspace.title} / $activePath"
        status.text = if (dirty) "已恢复未写回草稿，请保存或导出" else if (isCpp()) "C++17 · Wandbox 云端" else "Python 3.13 · 本地"
        refreshTabs()
    }

    private fun queueDraft() {
        if (!dirty || loadedPath != activePath || !::drafts.isInitialized) return
        val key = sessions.key(activePath); val path = activePath; val text = editor.text.toString(); val base = loadedVersion
        persistSessions()
        WorkspaceIo.executor.execute {
            runCatching { drafts.put(key, path, text, base) }.onFailure { error ->
                runOnUiThread { if (!isDestroyed) status.text = "恢复草稿写入失败：${error.message}，请立即导出" }
            }
        }
    }

    private fun saveActive(onSaved: (() -> Unit)? = null): Boolean {
        if (!::editor.isInitialized || loadedPath == null) return false
        if (workspaceBusy) { status.text = "正在处理文件，请稍候"; return false }
        if (loadedPath != activePath) { status.text = "保存已阻止：文件身份与缓冲区不匹配"; return false }
        if (saving) { if (onSaved != null) afterSave = onSaved; return false }
        if (!dirty) { onSaved?.invoke(); return true }
        saving = true; afterSave = onSaved
        val path = activePath; val key = sessions.key(path); val text = editor.text.toString(); val revision = editRevision
        val base = loadedVersion
        captureSession(); persistSessions()
        status.text = "正在保存…"
        WorkspaceIo.executor.execute {
            val saved = runCatching {
                drafts.put(key, path, text, base)
                workspace.write(workspace.resolve(path), text, base)
                drafts.removeIfMatches(key, text)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                saving = false
                val next = afterSave; afterSave = null
                saved.onSuccess {
                    loadedVersion = DocumentTree.digest(text)
                    if (loadedPath == path && revision == editRevision && editor.text.toString() == text) dirty = false
                    status.text = if (dirty) "已保存上一版，正在保留新编辑" else "✓ 已保存 $path"
                    refreshTabs()
                    if (next != null) saveActive(next)
                }.onFailure { error ->
                    status.text = "保存失败，编辑内容和恢复草稿仍保留：${error.message}"
                    if (next != null) alert("${error.message}\n已阻止切换/关闭/运行。可导出当前草稿，或重新授权后重试。")
                }
            }
        }
        return false
    }

    private fun allowFileOperation(): Boolean {
        if (workspaceBusy || saving) { status.text = "请等待文件操作完成"; return false }
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
        if (workspaceBusy) return
        updateSuggestions()
        if (!showingSuggestion) status.text = "键入词语前缀后，使用 Tab 或点击候选词补全"
        editor.requestFocus()
    }
    private fun acceptSuggestion(value: String) {
        if (workspaceBusy) return
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(start)
        val prefix = EditorFeatures.prefix(editor.text.toString(), start)
        val begin = (start - prefix.length).coerceAtLeast(0)
        editor.text.replace(begin, end, value)
        editor.setSelection((begin + value.length).coerceAtMost(editor.length()))
    }
    private fun insertText(snippet: String, caretInside: Boolean = false) {
        if (workspaceBusy) return
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(start)
        quietEdit = true
        editor.text.replace(start, end, snippet)
        quietEdit = false
        dirty = true; editRevision++
        handler.removeCallbacks(checkpointDraft); handler.postDelayed(checkpointDraft, 200)
        updateNumbers(); handler.post(refreshEditor)
        handler.removeCallbacks(autosave); handler.postDelayed(autosave, 700)
        editor.setSelection((start + snippet.length - if (caretInside) 1 else 0).coerceAtMost(editor.length()))
        editor.requestFocus()
    }

    /** Shift+Tab removes one indent. Tab indents selection or accepts a suggestion. */
    private fun indent(reverse: Boolean) {
        if (workspaceBusy) return
        applyChange(EditorActions.indent(editor.text.toString(), editor.selectionStart, editor.selectionEnd, reverse))
        dirty = true; editRevision++
        handler.removeCallbacks(checkpointDraft); handler.postDelayed(checkpointDraft, 200)
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
        if (dirty || saving) { saveActive { toggleTheme() }; return }
        if (workspaceBusy) return
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
                KeyEvent.KEYCODE_S -> { saveActive { status.text = "✓ 已保存 $activePath" }; return true }
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
        if (!allowFileOperation()) return
        saveActive {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, 201)
        }
    }

    private fun exportFile() {
        // Export must remain available even when saving to the original provider fails.
        if (workspaceBusy || loadedPath != activePath) return
        exportContent = editor.text.toString(); exportPath = activePath
        ioAction("准备导出草稿…", { File(filesDir, "pending-export.txt").writeText(exportContent!!) }) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, activePath.substringAfterLast('/'))
            }, 202)
        }
    }

    private fun reloadActive() {
        if (workspaceBusy || saving || loadedPath != activePath) return
        AlertDialog.Builder(this).setTitle("重新载入原文件？")
            .setMessage("当前内容将先保留为恢复草稿，然后读取原文件。恢复入口可以导出此前的草稿。")
            .setNegativeButton("取消", null).setPositiveButton("保留草稿并载入") { _, _ ->
                handler.removeCallbacks(autosave); handler.removeCallbacks(checkpointDraft)
                val path = activePath; val key = sessions.key(path); val text = editor.text.toString(); val base = loadedVersion
                ioAction("正在保留草稿并载入…", {
                    drafts.put("$key:backup:${System.currentTimeMillis()}", path, text, base)
                    val fresh = workspace.read(workspace.resolve(path))
                    drafts.removeIfMatches(key, text)
                    fresh
                }) { fresh -> loadActive(fresh) }
            }.show()
    }

    private fun recoverDrafts() {
        if (workspaceBusy || saving) return
        ioAction("读取恢复草稿与原文…", {
            val saved = drafts.list("")
            val originals = File(filesDir, "provider-recovery").walkTopDown().filter {
                it.isFile && it.name.endsWith(".before.txt") && it.length() <= 1_000_000
            }.take(1000).mapNotNull { file -> runCatching {
                val info = org.json.JSONObject(File(file.parentFile, file.name.removeSuffix(".before.txt") + ".meta.json").readText())
                Draft(file.name, "${info.getString("project")}（原文备份）/${info.getString("path")}", file.readText())
            }.getOrNull() }.toList()
            saved + originals
        }) { entries ->
            if (entries.isEmpty()) { alert("没有恢复草稿"); return@ioAction }
            AlertDialog.Builder(this).setTitle("导出恢复草稿")
                .setItems(entries.mapIndexed { index, draft -> "${index + 1}. ${draft.path}" }.toTypedArray()) { _, which ->
                    val draft = entries[which]
                    exportContent = draft.text; exportPath = draft.path
                    ioAction("准备恢复导出…", { File(filesDir, "pending-export.txt").writeText(draft.text) }) {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, draft.path.substringAfterLast('/'))
                        }, 202)
                    }
                }.show()
        }
    }

    private fun workspaceMenu() {
        if (!allowFileOperation()) return
        val recent = runCatching { JSONArray(prefs.getString("recentTrees", "[]")) }.getOrDefault(JSONArray())
        val names = mutableListOf("选择手机目录…", "内部项目（卸载会清除）", "重新载入当前目录")
        for (i in 0 until recent.length()) names.add("最近：" + recent.getJSONObject(i).optString("name"))
        AlertDialog.Builder(this).setTitle(workspace.title).setItems(names.toTypedArray()) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this).setTitle("选择真实项目目录")
                        .setMessage("编辑器保存会写回所选目录；权限只限该目录。外部文件卸载后仍由提供方保留。Python 使用私有运行镜像，运行代码产生的文件不会自动同步回外部目录。")
                        .setPositiveButton("选择目录") { _, _ -> selectDirectory() }.setNegativeButton("取消", null).show()
                    1 -> requestWorkspace(null)
                    2 -> requestWorkspace(selectedTree)
                    else -> requestWorkspace(recent.getJSONObject(which - 3).getString("uri"))
                }
        }.show()
    }

    private fun requestWorkspace(tree: String?) {
        if (!::editor.isInitialized || !dirty) { startWorkspace(tree); return }
        if (workspaceBusy || saving) return
        AlertDialog.Builder(this).setTitle("切换工作区")
            .setMessage("当前内容尚未写回。可以先保存，或保留应用内草稿后切换；授权失效时可选择保留草稿。")
            .setPositiveButton("保存后切换") { _, _ -> saveActive { startWorkspace(tree) } }
            .setNeutralButton("保留草稿后切换") { _, _ ->
                handler.removeCallbacks(autosave); handler.removeCallbacks(checkpointDraft)
                val key = sessions.key(activePath); val path = activePath; val text = editor.text.toString(); val base = loadedVersion
                captureSession(); persistSessions()
                ioAction("正在保留恢复草稿…", { drafts.put(key, path, text, base) }) { startWorkspace(tree) }
            }.setNegativeButton("取消", null).show()
    }

    private fun selectDirectory() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }, 203)
    }

    private fun rememberWorkspace(uri: String, name: String) {
        val old = runCatching { JSONArray(prefs.getString("recentTrees", "[]")) }.getOrDefault(JSONArray())
        val recent = JSONArray().put(org.json.JSONObject().put("uri", uri).put("name", name))
        for (i in 0 until old.length()) if (recent.length() < 8 && old.getJSONObject(i).getString("uri") != uri) recent.put(old.getJSONObject(i))
        prefs.edit().putString("recentTrees", recent.toString()).apply()
    }

    @Deprecated("Compatible with Android API 24 without AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            203 -> try {
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 && flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) { "请选择可读写目录" }
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                requestWorkspace(uri.toString())
            } catch (e: Exception) { alert("目录授权失败：${e.message}") }
            201 -> {
                if (!allowFileOperation()) return
                saveActive {
                    val parent = workspace.resolve(currentDirectory).let { if (it.isDirectory) it else workspace.root }
                    ioAction("正在导入副本…", {
                        val content = contentResolver.openInputStream(uri)?.let(Workspace::utf8Reader)?.use { Workspace.readLimited(it, Workspace.MAX_CHARS) }
                            ?.removePrefix("\uFEFF") ?: error("无法读取文件")
                        require(!content.contains('\u0000')) { "仅支持 UTF-8 文本" }
                        val rawName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "import.cpp"
                        val item = workspace.create(parent, rawName.substringAfterLast('/').substringAfterLast('\\'), false)
                        workspace.write(item, content)
                        workspace.path(item)
                    }) { path -> switchFile(path) }
                }
            }
            202 -> {
                val snapshot = exportContent ?: runCatching { File(filesDir, "pending-export.txt").readText() }.getOrNull()
                if (snapshot == null) { alert("导出快照已失效，请重新导出"); return }
                ioAction("正在导出…", {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) } ?: error("无法写入导出文件")
                }) { status.text = "✓ 已导出 $exportPath"; exportContent = null; exportPath = null }
            }
        }
    }

    private fun runCode(consented: Boolean = false) {
        if (running || workspaceBusy) return
        if (dirty || saving) { saveActive { runCode(consented) }; return }
        if (loadedPath != activePath) return
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
        if (!cpp && workspace.external) {
            val path = activePath
            ioAction("正在同步 Python 源码与运行资源…", {
                val snapshot = workspace.preparePythonRun()
                val entry = File(snapshot, path)
                entry.writeText(code)
                snapshot to entry
            }) { (snapshot, entry) ->
                RunSession.start(applicationContext, code, stdin, false, runningFile, snapshot.absolutePath,
                    workspace.identity, entry.absolutePath)
            }
        } else RunSession.start(applicationContext, code, stdin, cpp, runningFile, projectRoot, workspace.identity)
    }

    private fun renderRunState() {
        if (!::runButton.isInitialized) return
        runButton.text = if (running) "■ 停止" else "▶ 运行"
        runButton.isEnabled = true
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
        val jump = {
            val src = editor.text.toString(); var position = 0
            for (i in 1 until line) {
                val next = src.indexOf('\n', position)
                if (next < 0) break
                position = next + 1
            }
            editor.requestFocus(); editor.setSelection(position.coerceAtMost(editor.length()))
        }
        if (file != null) {
            if (result?.workspaceId != null && result?.workspaceId != workspace.identity) {
                alert("请先打开运行时的原工作区，再定位错误"); return
            }
            val relative = runCatching { workspace.path(File(file)) }.getOrNull() ?: runCatching {
                val oldRoot = File(result?.projectRoot ?: error("缺少运行目录")).canonicalPath
                val location = File(file).canonicalPath
                require(location.startsWith(oldRoot + File.separator))
                location.removePrefix(oldRoot + File.separator).replace(File.separatorChar, '/')
                    .also { require(workspace.resolve(it).isFile) }
            }.getOrNull()
            if (relative == null) { alert("错误位置不在当前工作区；请打开运行时的原工作区"); return }
            switchFile(relative, jump)
        } else jump()
    }

    private fun alert(message: String) { AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定", null).show() }
}
