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
import com.codepocket.ide.animation.Animations
import com.codepocket.ide.editor.EditorAnalysisController
import com.codepocket.ide.editor.NativeEditorPort
import com.codepocket.ide.editor.DocumentEditorPort
import com.codepocket.ide.editor.EditorDocumentRequest
import com.codepocket.ide.editor.EditorDocumentTransitions
import com.codepocket.ide.editor.EditorDocumentSessions
import com.codepocket.ide.editor.DocumentTransitionResult
import com.codepocket.ide.editor.EditorTransaction
import com.codepocket.ide.editor.EditResult
import com.codepocket.ide.editor.HistoryAction
import com.codepocket.ide.editor.NavigatingEditorPort
import com.codepocket.ide.editor.EditorSelectionRequest
import com.codepocket.ide.editor.EditorViewportSnapshot
import com.codepocket.ide.editor.EditorNavigation
import com.codepocket.ide.editor.EditorDiagnostic
import com.codepocket.ide.editor.DiagnosticStatus
import com.codepocket.ide.editor.DiagnosticFiles
import com.codepocket.ide.search.ProjectSearch
import com.codepocket.ide.ui.*
import com.codepocket.ide.terminal.TerminalView
import java.io.File
import org.json.JSONArray

/** VS Code-inspired, responsive native Android learning IDE. */
class MainActivity : Activity() {
    private lateinit var workspace: Workspace
    private val prefs by lazy { getSharedPreferences("codepocket_v2", MODE_PRIVATE) }
    private var dark = true
    private var settings = AppSettings()
    private var toolDialog: AlertDialog? = null
    private val syntaxDark get() = settings.syntaxIsDark(dark)
    private val codeBackground get() = Color.parseColor(if (syntaxDark) "#1E1E1E" else "#FFFFFF")
    private val codeForeground get() = Color.parseColor(if (syntaxDark) "#D4D4D4" else "#242424")
    private var activePath = "main.cpp"
    private var currentDirectory = ""
    private val expanded = mutableSetOf("")
    private val opened = mutableListOf<String>()
    private val running get() = RunSession.running
    private var result: RunResult? = null
    private var selectedOutput = 0
    private var completionPopup: CompletionPopup? = null
    private var completionBatch: CompletionBatch? = null
    private var completionSuppressed: CompletionRequest? = null
    private var completionExplicit = false
    private var completionGeneration = 0L
    private val completionExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var completionTask: java.util.concurrent.Future<*>? = null
    private val completionConsumedKeys = mutableSetOf<Int>()
    private var quietEdit = false
    private var editorPort: DocumentEditorPort? = null
    private val documentTransitions = EditorDocumentTransitions()
    private var editorAnalysis: EditorAnalysisController? = null
    private var snippetSession: SnippetSession? = null
    private var snippetTextChanging = false
    private lateinit var completionHint: TextView
    private var signaturePopup: SignaturePopup? = null
    private var signatureSuppressed: CompletionRequest? = null
    private var signatureGeneration = 0L
    private val signatureExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var signatureTask: java.util.concurrent.Future<*>? = null
    private var explorerDialog: AlertDialog? = null
    private var currentGeometry: WorkspaceGeometry? = null
    private var explorerWidth = 240 // User's explorer width preference in dp
    private var loadedPath: String? = null
    private var dirty = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sessions: EditorSessions
    private var quietInput = false
    private var panelVisible = true
    private var panelState = PanelState()
    private var panelLayout: PanelLayout? = null
    private lateinit var splitHost: LinearLayout
    private lateinit var panelResize: PanelResizeHandle
    private var activityBar: ActivityBar? = null
    private var explorerResizeHandle: ExplorerResizeHandle? = null
    private var projectSearch: ProjectSearch? = null
    private lateinit var panelToolbar: PanelToolbar
    private var presetInputExpanded = false
    private var commandMenu: android.widget.PopupMenu? = null
    private lateinit var panelInputHeading: LinearLayout
    private lateinit var panelOutputScroll: HorizontalScrollView
    private var imeVisible = false
    private var lastFocus = "editor"
    private lateinit var statusBarComponent: StatusBar
    private lateinit var suggestionScroll: HorizontalScrollView
    private lateinit var keyboardToolbar: HorizontalScrollView
    private val saveSession = Runnable { captureSession(); persistSessions() }
    private val autosave = Runnable { if (dirty && settings.autoSave) saveActive() }
    private val refreshEditor = Runnable {
        if (::editor.isInitialized) {
            if (BaseInputConnection.getComposingSpanStart(editor.text) >= 0) { hideCompletion(); return@Runnable }
            editorAnalysis?.request()
            updateTabStops()
            updateSuggestions()
            updateSignatureHelp()
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
            if (value) editorAnalysis?.cancel()
            if (::editor.isInitialized) { editor.isEnabled = !value; if (value) { hideCompletion(); clearSnippet() } }
            if (::input.isInitialized) input.isEnabled = !value
            if (::panelResize.isInitialized) panelResize.isEnabled = !value && panelLayout?.resizable == true
            explorerResizeHandle?.isEnabled = !value
            updateMainToolbarState()
        }
    private var saving = false
        set(value) { field = value; updateMainToolbarState() }
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
    private lateinit var status: TextView
    private lateinit var consoleText: TerminalView
    private lateinit var tabsRow: LinearLayout
    private lateinit var suggestionRow: LinearLayout
    private var sidebarExplorerList: LinearLayout? = null
    private var overlayExplorerList: LinearLayout? = null
    private lateinit var contentHost: LinearLayout
    private lateinit var editorToolbar: EditorToolbar
    private var explorerDragStartWidth = 240
    private var explorerDragViewport: EditorViewportSnapshot? = null
    private val outputDisplay = OutputDisplayState()
    private lateinit var mainToolbar: MainToolbar
    private lateinit var outputTabs: LinearLayout
    private lateinit var outputRegion: LinearLayout
    private lateinit var editorRegion: LinearLayout

    private val palette get() = DesignTokens.palette(dark)
    private val editorBackground get() = palette.editor
    private val surface get() = palette.surface
    private val panel get() = palette.panel
    private val border get() = palette.border
    private val textForeground get() = palette.foreground
    private val muted get() = palette.muted
    private val accent get() = palette.accentText
    private val green get() = palette.success
    private val red get() = palette.error

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = AppSettings.fromJson(prefs.getString("settings", null), prefs.getBoolean("dark", true))
        dark = settings.isDark(isSystemDark())
        setTheme(if (dark) R.style.Theme_CodePocket else R.style.Theme_CodePocket_Light)
        super.onCreate(savedInstanceState)
        drafts = DraftStore(File(filesDir, "drafts")) { a, b -> android.system.Os.rename(a.path, b.path) }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(TextView(this).apply { text = "正在载入 CodePocket…"; setPadding(24, 36, 24, 24) })
        startWorkspace(prefs.getString("selectedTree", null), savedInstanceState)
    }

    private fun startWorkspace(tree: String?, saved: Bundle? = null) {
        if ((running && ::editor.isInitialized) || workspaceBusy || saving) return
        val transition = beginDocumentTransition() ?: return
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
                    commitDocumentTransition(transition) {
                        val liveSessions = if (::sessions.isInitialized) sessions else null
                        sessions = EditorDocumentSessions.choose(liveSessions, ready.sessions)
                        workspace = ready.workspace
                        selectedTree = tree
                        opened.clear(); opened.addAll(ready.paths)
                        activePath = ready.active; loadedPath = null; dirty = false
                        currentDirectory = prefs.getString(prefKey("directory"), "") ?: ""
                        expanded.clear(); expanded.add("")
                        explorerVisible = prefs.getBoolean("explorerVisible", true)
                        panelVisible = prefs.getBoolean("panelVisible", true)
                        presetInputExpanded = prefs.getBoolean(prefKey("presetInputExpanded"), false)
                        panelState = PanelState.fromJson(prefs.getString("panelLayout", null))
                            .let { if (panelVisible) it else it.copy(maximized = false) }
                        explorerWidth = prefs.getInt("explorerWidth", 240)
                        createUi(); loadActive(ready.code, ready.draft)
                        // Bundle checkpoints are merged before reading; loadActive reconciles them once.
                        exportContent = saved?.getString("exportSnapshot")?.let { name ->
                            runCatching { File(filesDir, name).readText() }.getOrNull()
                        }
                        exportPath = saved?.getString("exportPath")
                        if (tree != null) rememberWorkspace(tree, workspace.title)
                        renderRunState()
                    }
                }.onFailure { error ->
                    documentTransitions.cancel(); preservePendingDocumentEdits()
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
                value.onSuccess(done).onFailure { error ->
                    documentTransitions.cancel(); preservePendingDocumentEdits()
                    status.text = getString(R.string.file_operation_failed, error.message.orEmpty())
                    alert("${error.message}\n未保存内容和恢复草稿会保留；可导出或重新选择目录。")
                }
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

    override fun onResume() {
        super.onResume()
        if (::editor.isInitialized) { handler.removeCallbacks(refreshEditor); handler.post(refreshEditor) }
    }

    override fun onPause() {
        super.onPause()
        if (::panelResize.isInitialized) panelResize.cancelGesture()
        explorerResizeHandle?.cancelGesture(); projectSearch?.cancel()
        listOfNotNull(explorerPane, activityBar).forEach(Animations::cancel)
        handler.removeCallbacks(refreshEditor); editorAnalysis?.cancel(); editorPort?.cancelNavigation(); hideCompletion(); clearSnippet(); completionConsumedKeys.clear()
        currentGeometry?.let { updateExplorerLayout(ExplorerLayout.resolve(it, explorerWidth)) }
        persist()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) { editorAnalysis?.cancel(); hideCompletion(); completionConsumedKeys.clear() }
        else if (::editor.isInitialized) { handler.removeCallbacks(refreshEditor); handler.post(refreshEditor) }
    }

    private fun persist() {
        if (!::workspace.isInitialized || !::editor.isInitialized) return
        captureSession(); persistSessions(); queueDraft(); saveActive()
        persistNavigation()
    }

    private fun persistNavigation() {
        // Keep navigation and the stable file IDs in the same preference update.
        // Do not rely on onPause: an abrupt process stop may never deliver it.
        prefs.edit().putString("selectedTree", selectedTree)
            .putString("sessions:${sessions.workspaceId}", sessions.toJson())
            .putString(prefKey("activeFile"), activePath).putString(prefKey("directory"), currentDirectory)
            .putBoolean("dark", dark).putBoolean("explorerVisible", explorerVisible)
            .putString(prefKey("opened"), JSONArray(opened).toString())
            .putBoolean(prefKey("presetInputExpanded"), presetInputExpanded)
            .putBoolean("panelVisible", panelVisible).putString("panelLayout", panelState.toJson()).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::panelResize.isInitialized) panelResize.cancelGesture()
        panelDragViewport = null
        documentTransitions.close()
        editorAnalysis?.close(); editorPort?.close()
        handler.removeCallbacksAndMessages(null)
        projectSearch?.cancel(); explorerResizeHandle?.cancelGesture()
        toolDialog?.dismiss(); commandMenu?.dismiss(); commandMenu = null
        hideCompletion(); completionExecutor.shutdownNow(); signatureExecutor.shutdownNow(); completionConsumedKeys.clear()
        listOfNotNull(explorerPane, if (::tabsRow.isInitialized) tabsRow else null,
            if (::outputRegion.isInitialized) outputRegion else null).forEach { it.animate().cancel() }
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
        commandMenu?.dismiss(); commandMenu = null
        hideCompletion(); clearSnippet(); completionPopup = null; signaturePopup = null
        if (::panelResize.isInitialized) panelResize.cancelGesture()
        explorerResizeHandle?.cancelGesture()
        listOfNotNull(activityBar, explorerPane).forEach(Animations::cancel)
        activityBar = null; explorerPane = null; explorerResizeHandle = null
        sidebarExplorerList = null; overlayExplorerList = null; currentGeometry = null
        panelDragViewport = null; panelLayout = null
        window.statusBarColor = surface
        window.navigationBarColor = panel
        val lightNavigationBar = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or lightNavigationBar
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(editorBackground) }
        setContentView(root)

        mainToolbar = MainToolbar(this, palette,
            { dispatchCommand(IdeCommand.EXPLORER) }, { dispatchCommand(IdeCommand.RUN_STOP) },
            ::showCommandMenu, { dispatchCommand(IdeCommand.WORKSPACE) }, { dispatchCommand(IdeCommand.SEARCH) })
        root.addView(mainToolbar, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))
        updateShellTitle()
        keyboardToolbar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE }
        val keyRow = LinearLayout(this).apply { setBackgroundColor(surface) }
        IdeCommands.symbolBarItems.forEach { command ->
            val name = when (command) {
                IdeCommand.TAB -> "Tab"
                IdeCommand.UNINDENT -> "⇤"
                IdeCommand.BRACES -> "{}"
                IdeCommand.BRACKETS -> "[]"
                IdeCommand.PARENS -> "()"
                IdeCommand.SEMICOLON -> ";"
                IdeCommand.EQUALS -> "="
                IdeCommand.COLON -> ":"
                IdeCommand.LEFT -> "←"
                IdeCommand.RIGHT -> "→"
                else -> command.title.substringBefore("  ")
            }
            keyRow.addView(IdeControls.key(this, palette, name, command.title) { dispatchCommand(command) },
                LinearLayout.LayoutParams(-2, dp(DesignTokens.TOUCH)))
        }
        keyboardToolbar.addView(keyRow)

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        contentHost = content
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        // Responsive layout will be determined after measurement
        val work = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(work, LinearLayout.LayoutParams(0, -1, 1f))
        buildEditor(work)

        val console = buildConsole()
        (editorRegion.parent as? ViewGroup)?.removeView(editorRegion)
        splitHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        panelResize = PanelResizeHandle(this).apply {
            setCallbacks(muted, {
                hideCompletion(); panelDragViewport = capturePanelViewport()
            }, ::resizePanelFraction, ::finishPanelResize)
            setBackgroundColor(surface)
            setOnClickListener { showPanelSizeMenu(this) }
        }
        splitHost.addView(editorRegion, LinearLayout.LayoutParams(-1, 0, 1f))
        splitHost.addView(panelResize, LinearLayout.LayoutParams(-1, dp(PanelLayoutPolicy.HANDLE)))
        splitHost.addView(console, LinearLayout.LayoutParams(-1, dp(225)))
        work.addView(splitHost, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(keyboardToolbar, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))
        statusBarComponent = StatusBar(this, palette)
        statusBarComponent.setText("就绪")
        root.addView(statusBarComponent, LinearLayout.LayoutParams(-1, -2))

        // Keep TextView reference for backward compatibility with existing code
        status = TextView(this).apply {
            text = "就绪"
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setBackgroundColor(surface)
            visibility = View.GONE // Message bridge; the visible status bar must retain save/IO errors.
        }
        status.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { updateStatusBar() }
        })
        applyEditorPreferences()
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
            // Update responsive layout on size changes
            updateResponsiveLayout()
        }
    }

    private fun updateResponsiveLayout() {
        if (!::contentHost.isInitialized || contentHost.width <= 0 || contentHost.height <= 0) return
        val geometry = WorkspaceGeometry.measure(contentHost, imeVisible)
        if (currentGeometry == geometry) return
        currentGeometry = geometry
        updateExplorerLayout(ExplorerLayout.resolve(geometry, explorerWidth))
    }

    private fun updateExplorerLayout(layout: ExplorerLayout) {
        if (layout.mode == ExplorerMode.SIDEBAR) {
            if (activityBar == null) {
                activityBar = ActivityBar(this, palette) { dispatchCommand(IdeCommand.EXPLORER) }.also {
                    contentHost.addView(it, 0, LinearLayout.LayoutParams(dp(48), -1))
                }
            }
            if (explorerPane == null) {
                explorerPane = makeExplorerPane(true).also { contentHost.addView(it, 1, LinearLayout.LayoutParams(dp(layout.width), -1)) }
                explorerResizeHandle = ExplorerResizeHandle(this).also { handle ->
                    handle.setCallbacks(::resizeExplorer, ::finishExplorerResize) {
                        explorerDragStartWidth = ((explorerPane?.layoutParams?.width ?: dp(explorerWidth)) / resources.displayMetrics.density).toInt()
                        explorerDragViewport = capturePanelViewport(); hideCompletion()
                    }
                    handle.setOnClickListener { explorerWidth = 240; finishExplorerResize(); updateExplorerLayout(ExplorerLayout.resolve(currentGeometry!!, explorerWidth)) }
                    handle.setBackgroundColor(surface)
                    contentHost.addView(handle, 2, LinearLayout.LayoutParams(dp(24), -1))
                }
            }
            activityBar?.visibility = View.VISIBLE
            activityBar?.setExplorerActive(explorerVisible)
            explorerPane?.let { pane ->
                if (pane.layoutParams.width != dp(layout.width)) pane.layoutParams = LinearLayout.LayoutParams(dp(layout.width), -1)
                Animations.cancel(pane); pane.visibility = if (explorerVisible) View.VISIBLE else View.GONE
            }
            explorerResizeHandle?.visibility = if (explorerVisible) View.VISIBLE else View.GONE
            explorerResizeHandle?.isEnabled = !workspaceBusy
            if (explorerDialog?.isShowing == true) { explorerDialog?.dismiss(); explorerDialog = null }
        } else {
            // Keep a single attached instance. GONE is not removal: never lose the reference and create duplicates.
            explorerResizeHandle?.cancelGesture()
            listOfNotNull(activityBar, explorerPane, explorerResizeHandle).forEach { Animations.cancel(it); it.visibility = View.GONE }
        }
    }

    private fun resizeExplorer(delta: Float) {
        if (workspaceBusy) return
        val geometry = currentGeometry ?: return
        val width = WorkspaceLayoutPolicy.resizeExplorer(explorerDragStartWidth, delta, geometry.contentWidth, resources.displayMetrics.density) ?: return
        explorerWidth = width
        explorerPane?.let { if (it.layoutParams.width != dp(width)) it.layoutParams = LinearLayout.LayoutParams(dp(width), -1) }
    }

    private fun finishExplorerResize() {
        prefs.edit().putInt("explorerWidth", explorerWidth).apply()
        val viewport = explorerDragViewport; explorerDragViewport = null
        restorePanelViewport(viewport)
    }

    private fun updateStatusBar() {
        if (!::statusBarComponent.isInitialized || !::editor.isInitialized) return

        val language = if (activePath.endsWith(".py", true)) "Python" else "C++"
        val (line, column) = InteractionState.caret(editor.text.toString(), editor.selectionStart)
        val state = InteractionState.status(running, workspaceBusy, saving, dirty, status.text.toString())
        statusBarComponent.setStatus(StatusBarLayout(language, line, column,
            if (settings.useSpaces) "空格 ${settings.tabWidth}" else "Tab ${settings.tabWidth}", "UTF-8", state))
        if (::editorToolbar.isInitialized) editorToolbar.setState(commandContext())
    }

    private fun decorateFileLabel(view: TextView, icon: IdeIcon, fullPath: String) {
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
        val width = (mainToolbar.width / resources.displayMetrics.density).toInt()
        showContextCommands(anchor, IdeCommands.globalMenu(WorkspaceLayoutPolicy.toolbar(width), panelLayout?.showPanel == true))
    }

    private fun showContextCommands(anchor: View, commands: List<IdeCommand>) {
        commandMenu?.dismiss()
        commandMenu = IdeCommandMenu.show(anchor, commandContext(), commands, ::dispatchCommand)
    }

    private fun dispatchCommand(command: IdeCommand) {
        if (isDestroyed || !IdeCommands.enabled(command, commandContext())) return
        when (command) {
            IdeCommand.RUN_STOP -> if (running) RunSession.stop() else runCode()
            IdeCommand.EXPLORER -> showExplorer()
            IdeCommand.SEARCH -> showProjectSearch()
            IdeCommand.FIND -> showSearch(false)
            IdeCommand.NEW_FILE -> askCreate(false)
            IdeCommand.NEW_FOLDER -> askCreate(true)
            IdeCommand.RENAME -> askRename(workspace.resolve(activePath))
            IdeCommand.DELETE -> askDelete(workspace.resolve(activePath))
            IdeCommand.WORKSPACE -> workspaceMenu()
            IdeCommand.IMPORT_COPY -> importFile()
            IdeCommand.EXPORT -> exportFile()
            IdeCommand.SAVE -> saveActive { status.text = "✓ 已保存 $activePath" }
            IdeCommand.RELOAD -> reloadActive()
            IdeCommand.RECOVER -> recoverDrafts()
            IdeCommand.REPLACE -> showSearch(true)
            IdeCommand.GOTO_LINE -> showGoToLine()
            IdeCommand.UNDO, IdeCommand.REDO -> {
                clearSnippet(); hideCompletion()
                val action = if (command == IdeCommand.UNDO) HistoryAction.UNDO else HistoryAction.REDO
                if (input.hasFocus()) input.onTextContextMenuItem(if (action == HistoryAction.UNDO) android.R.id.undo else android.R.id.redo)
                else editorPort?.let { port -> port.snapshot()?.let { port.performHistory(it.buffer, action) } }
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
            IdeCommand.CLEAR_TERMINAL -> { outputDisplay.clear(); showOutput(selectedOutput) }
            IdeCommand.MAXIMIZE_TERMINAL -> setPanelMaximized(!panelState.maximized)
            IdeCommand.CLOSE_TERMINAL -> closePanel()
            IdeCommand.PRESET_INPUT -> setPresetInputExpanded(true)
            IdeCommand.CLEAR_INPUT -> input.setText("")
            IdeCommand.PANEL_SIZE -> showPanelSizeMenu(if (panelToolbar.isShown) panelToolbar.menuAnchor else mainToolbar.menuAnchor)
            IdeCommand.HIDE_KEYBOARD -> hideKeyboard()
            IdeCommand.THEME -> toggleTheme()
            IdeCommand.SETTINGS -> showSettings()
            IdeCommand.SHORTCUTS -> alert(getString(R.string.shortcuts_help))
            IdeCommand.HELP -> alert(getString(R.string.shell_help))
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
        if (changed) { completionPopup?.dismiss(); hideSignatureHelp() }
        imeVisible = visible
        val height = (root.height / resources.displayMetrics.density).toInt()
        val layout = ImeLayoutPolicy.resolve(visible, input.hasFocus(), height, panelVisible, settings.collapsePanelOnIme)
        if (visible) listOfNotNull(explorerPane, tabsRow, outputRegion).forEach(Animations::cancel)
        suggestionScroll.visibility = if (WorkspaceLayoutPolicy.showSnippet(snippetSession != null, visible, height)) View.VISIBLE else View.GONE
        keyboardToolbar.visibility = if (visible && editor.hasFocus() && settings.symbolBar) View.VISIBLE else View.GONE
        statusBarComponent.visibility = if (layout.showDetails) View.VISIBLE else View.GONE
        editorToolbar.visibility = if (keyboardToolbar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        applyPanelLayout()
        if (changed && visible && editor.hasFocus()) editorPort?.revealCaret()
    }

    private var panelDragViewport: EditorViewportSnapshot? = null

    private fun capturePanelViewport(): EditorViewportSnapshot? {
        if (!::sessions.isInitialized || loadedPath != activePath || !::editorRegion.isInitialized) return null
        captureSession()
        val stamp = editorPort?.navigationSnapshot() ?: return null
        val saved = sessions.file(activePath).view
        return stamp.copy(start = saved.start, end = saved.end, x = saved.scrollX, y = saved.scrollY)
    }

    private fun restorePanelViewport(stamp: EditorViewportSnapshot?, focusEditor: Boolean = false) {
        if (stamp == null || isDestroyed || loadedPath != activePath || editorRegion.visibility != View.VISIBLE) return
        val port = editorPort ?: return
        if (!stamp.matches(port.navigationSnapshot(), false)) return
        val focused = focusEditor && !input.hasFocus() && editor.requestFocus()
        val current = port.navigationSnapshot() ?: return
        port.restoreViewport(if (focused) stamp.copy(interaction = current.interaction) else stamp)
    }

    private fun persistPanel() {
        prefs.edit().putBoolean("panelVisible", panelVisible).putString("panelLayout", panelState.toJson()).apply()
    }

    private fun finishPanelResize() {
        val stamp = panelDragViewport; panelDragViewport = null
        persistPanel(); restorePanelViewport(stamp)
    }

    private fun resizePanelFraction(fraction: Float) {
        if (workspaceBusy || !fraction.isFinite()) return
        val spec = panelLayout ?: return
        panelState = spec.resize(panelState, fraction * spec.available)
        applyPanelLayout()
    }

    private fun closePanel() {
        panelResize.cancelGesture(); hideCompletion()
        val stamp = capturePanelViewport()
        if (input.hasFocus()) { hideKeyboard(); editor.requestFocus() }
        panelVisible = false; panelState = panelState.copy(maximized = false)
        updateImeLayout(); persistPanel(); restorePanelViewport(stamp, true)
    }

    private fun togglePanel() {
        if (panelVisible && panelLayout?.showPanel == true) { closePanel(); return }
        panelVisible = true
        updateImeLayout()
        // A narrow/short window has room for one pane; explicit opening must still work.
        if (panelLayout?.showPanel != true) setPanelMaximized(true)
        else { persistPanel(); animateDecoration(outputRegion) }
    }

    private fun setPanelMaximized(maximized: Boolean) {
        if (workspaceBusy) return
        panelResize.cancelGesture(); hideCompletion()
        val stamp = capturePanelViewport()
        if (maximized) clearSnippet()
        if (maximized && !input.hasFocus()) { hideKeyboard(); outputRegion.requestFocus() }
        panelVisible = true; panelState = panelState.copy(maximized = maximized)
        updateImeLayout(); persistPanel()
        if (!maximized) restorePanelViewport(stamp, true)
        animateDecoration(if (maximized) outputRegion else editorRegion)
    }

    private fun revealEditor() {
        if (panelState.maximized) setPanelMaximized(false)
        if (editorRegion.visibility != View.VISIBLE) closePanel()
    }

    private fun showPanelSizeMenu(anchor: View) {
        panelResize.cancelGesture(); commandMenu?.dismiss()
        val menu = android.widget.PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, R.string.panel_smaller).isEnabled = panelLayout?.resizable == true
        menu.menu.add(0, 2, 1, R.string.panel_larger).isEnabled = panelLayout?.resizable == true
        menu.menu.add(0, 3, 2, R.string.panel_reset)
        menu.setOnMenuItemClickListener { item ->
            anchor.post {
                if (!isDestroyed && !workspaceBusy && anchor.isAttachedToWindow) when (item.itemId) {
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
        val stamp = capturePanelViewport(); hideCompletion()
        presetInputExpanded = expanded
        if (expanded) {
            panelVisible = true; outputRegion.visibility = View.VISIBLE
            input.visibility = View.VISIBLE
            input.requestFocus() // Do not force an IME for a hardware-keyboard user.
        } else if (input.hasFocus()) {
            hideKeyboard(); lastFocus = "editor"
            if (editorRegion.visibility == View.VISIBLE) editor.requestFocus() else outputRegion.requestFocus()
        }
        updateImeLayout()
        // Collapsing an IME-only full panel may reveal the editor only after resolving layout.
        if (!expanded && editorRegion.visibility == View.VISIBLE) editor.requestFocus()
        captureSession(); persistNavigation(); restorePanelViewport(stamp)
    }

    private fun applyPanelLayout() {
        if (!::splitHost.isInitialized || !::panelResize.isInitialized || !::input.isInitialized) return
        val density = resources.displayMetrics.density
        val width = (splitHost.width / density).toInt(); val height = (splitHost.height / density).toInt()
        // Before first measure, hiding stdin would make restoreSession.requestFocus() silently fail.
        if (width <= 0 || height <= 0) return
        val spec = PanelLayoutPolicy.resolve(panelState, panelVisible, width, height,
            imeVisible, input.hasFocus(), settings.collapsePanelOnIme, settings.panelPreferSide)
        val previous = panelLayout
        val stamp = if (previous != null && previous.dock != spec.dock) capturePanelViewport() else null
        panelLayout = spec
        val side = spec.dock == PanelDock.SIDE
        val orientation = if (side) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (splitHost.orientation != orientation) splitHost.orientation = orientation
        fun params(view: View, width: Int, height: Int, weight: Float = 0f) {
            val old = view.layoutParams as? LinearLayout.LayoutParams
            if (old == null || old.width != width || old.height != height || old.weight != weight)
                view.layoutParams = LinearLayout.LayoutParams(width, height, weight)
        }
        params(editorRegion, if (side) 0 else -1, if (side) -1 else 0, 1f)
        params(panelResize, if (side) dp(PanelLayoutPolicy.HANDLE) else -1, if (side) -1 else dp(PanelLayoutPolicy.HANDLE))
        val full = spec.showPanel && !spec.showEditor
        params(outputRegion, if (side && !full) dp(spec.extent) else -1, if (!side && !full) dp(spec.extent) else -1)
        outputRegion.visibility = if (spec.showPanel) View.VISIBLE else View.GONE
        if (!spec.showEditor && editor.hasFocus()) outputRegion.requestFocus()
        editorRegion.visibility = if (spec.showEditor) View.VISIBLE else View.GONE
        panelResize.visibility = if (spec.resizable) View.VISIBLE else View.GONE
        panelResize.configure(spec.dock, dp(spec.extent), dp(spec.minimum), dp(spec.maximum), dp(spec.available))
        panelResize.isEnabled = spec.resizable && !workspaceBusy
        val panelHeight = if (side || full) height else spec.extent
        val preset = WorkspaceLayoutPolicy.preset(presetInputExpanded, imeVisible, input.hasFocus(), panelHeight)
        panelInputHeading.visibility = if (preset.showCaption) View.VISIBLE else View.GONE
        panelOutputScroll.visibility = if (preset.showTabs) View.VISIBLE else View.GONE
        input.visibility = if (preset.showInput) View.VISIBLE else View.GONE
        input.layoutParams?.let { old ->
            val target = dp(preset.inputHeight)
            if (old.height != target) input.layoutParams = old.apply { this.height = target }
        }
        panelToolbar.setState(panelState.maximized, presetInputExpanded)
        if (previous != spec) { completionPopup?.dismiss(); hideSignatureHelp() }
        restorePanelViewport(stamp)
    }

    private fun persistSessions() {
        if (::sessions.isInitialized) prefs.edit().putString("sessions:${sessions.workspaceId}", sessions.toJson()).apply()
    }

    private fun captureSession() {
        if (!::editor.isInitialized || !::input.isInitialized || loadedPath == null) return
        sessions.file(loadedPath!!).apply {
            stdin = input.text.toString()
            // A GONE editor can have its native scroll clamped during remeasure. Keep its saved viewport.
            val live = EditorViewState(editor.selectionStart, editor.selectionEnd, editor.scrollX, editor.scrollY,
                lastFocus, input.selectionStart, input.selectionEnd)
            view = PanelViewport.capture(view, live, editorRegion.visibility == View.VISIBLE)
        }
    }

    private fun restoreSession() {
        val session = sessions.file(activePath)
        quietInput = true
        input.setText(session.stdin)
        quietInput = false
        val view = session.view.clamp(editor.length(), input.length())
        val source = editorPort?.snapshot()?.buffer
        if (source != null) {
            val selection = EditorNavigation.restoredSelection(source.text, view.start, view.end)
            if (editorPort?.select(EditorSelectionRequest(source, selection.start, selection.end, reveal = false)) == true)
                session.view = view.copy(start = selection.start, end = selection.end)
        }
        input.setSelection(view.inputStart, view.inputEnd)
        lastFocus = view.focus
        presetInputExpanded = WorkspaceLayoutPolicy.restorePreset(presetInputExpanded, view.focus, panelVisible)
        input.visibility = if (presetInputExpanded) View.VISIBLE else View.GONE
        if (view.focus == "stdin" && panelVisible) input.requestFocus()
        else if (panelState.maximized && panelVisible) outputRegion.requestFocus()
        else editor.requestFocus()
        updateImeLayout()
        editorPort?.navigationSnapshot()?.let { editorPort?.restoreViewport(it.copy(x = view.scrollX, y = view.scrollY)) }
    }

    private fun buildEditor(work: LinearLayout) {
        editorRegion = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        work.addView(editorRegion, LinearLayout.LayoutParams(-1, 0, 1f))
        val tabs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(surface) }
        tabsRow = LinearLayout(this)
        tabs.addView(tabsRow)
        editorRegion.addView(tabs, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))
        editorToolbar = EditorToolbar(this, palette, ::dispatchCommand) { showContextCommands(it, IdeCommands.editorMenuItems) }
        editorRegion.addView(editorToolbar, LinearLayout.LayoutParams(-1, dp(48)))

        val editorBody = LinearLayout(this).apply { setBackgroundColor(editorBackground); orientation = LinearLayout.HORIZONTAL }
        numbers = label("1", 13.5f, muted).apply {
            gravity = Gravity.TOP or Gravity.END; typeface = Typeface.MONOSPACE
            setPadding(dp(3), dp(9), dp(6), 0); setLineSpacing(dp(3).toFloat(), 1f)
        }
        editorBody.addView(numbers, LinearLayout.LayoutParams(dp(42), -1))
        editorAnalysis?.close(); editorPort?.close()
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
        editorPort = NativeEditorPort(editor,
            { if (loadedPath != activePath || !::sessions.isInitialized) null
                else sessions.key(activePath) to editRevision }, ::isCpp, { syntaxDark }, { !workspaceBusy }) {
            if (!isDestroyed && !isFinishing && hasWindowFocus()) {
                handler.removeCallbacks(refreshEditor); handler.postDelayed(refreshEditor, 120)
            }
        }
        editorAnalysis = EditorAnalysisController(editorPort!!, { handler.post(it) })
        editorBody.addView(editor, LinearLayout.LayoutParams(0, -1, 1f))
        editorRegion.addView(editorBody, LinearLayout.LayoutParams(-1, 0, 1f))
        val suggestScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE }
        suggestionScroll = suggestScroll
        suggestionRow = LinearLayout(this).apply { setPadding(dp(5), dp(2), dp(5), dp(2)); setBackgroundColor(surface) }
        suggestScroll.addView(suggestionRow)
        editorRegion.addView(suggestScroll, LinearLayout.LayoutParams(-1, dp(DesignTokens.SNIPPET_HINT)))
        editor.viewTreeObserver.addOnScrollChangedListener {
            numbers.scrollY = editor.scrollY; completionPopup?.let { if (it.isShowing) it.reposition() }
            signaturePopup?.let { if (it.isShowing) it.reposition() }
        }
        editor.viewTreeObserver.addOnGlobalLayoutListener {
            if (settings.wordWrap) updateNumbers()
            completionPopup?.let { if (it.isShowing) it.reposition() }
            signaturePopup?.let { if (it.isShowing) it.reposition() }
        }
        editor.addTextChangedListener(object : TextWatcher {
            private var before = ""
            private var change: TextChange? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!quietEdit) { before = s.toString(); snippetTextChanging = true }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, beforeCount: Int, count: Int) {
                if (!quietEdit) {
                    if (editor.isHistoryEdit) clearSnippet() else trackSnippetEdit(start, beforeCount, count)
                    change = EditorActions.typed(before, start, beforeCount, s.toString().substring(start, start + count), isCpp(),
                        settings.tabWidth, settings.useSpaces, settings.autoIndent, settings.autoPairs, editor.isHistoryEdit)
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (quietEdit) return
                editorAnalysis?.cancel()
                hideCompletion()
                val edit = change
                change = null
                if (edit != null && s != null && BaseInputConnection.getComposingSpanStart(s) < 0) applyChange(edit, keepSnippet = true)
                if (!dirty) { dirty = true; updateShellTitle() }; editRevision++
                snippetSession = snippetSession?.copy(revision = editRevision)
                snippetTextChanging = false; validSnippet(); updateSnippetHint()
                handler.removeCallbacks(checkpointDraft); handler.postDelayed(checkpointDraft, 200)
                updateNumbers(); updateStatusBar()
                handler.removeCallbacks(refreshEditor); handler.postDelayed(refreshEditor, 120)
                handler.removeCallbacks(autosave); if (settings.autoSave) handler.postDelayed(autosave, settings.autoSaveDelayMs.toLong())
            }
        })
        editor.onFocusChangeListener = View.OnFocusChangeListener { _, focused ->
            if (focused) { lastFocus = "editor"; updateImeLayout() } else hideCompletion()
        }
        editor.setOnClickListener { updateSuggestions(); updateSignatureHelp() }
        completionHint = label("", 11f, muted)
        suggestionRow.addView(completionHint)
        editor.onCaretChanged = {
            updateStatusBar()
            if (!quietEdit) {
                editorAnalysis?.cancel()
                if (!snippetTextChanging) { validSnippet(); updateSnippetHint() }
                hideCompletion()
                handler.removeCallbacks(refreshEditor); handler.postDelayed(refreshEditor, 120)
            }
        }
        refreshTabs()
    }

    private fun buildConsole(): LinearLayout {
        outputRegion = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(panel); isFocusableInTouchMode = true
        }
        panelToolbar = PanelToolbar(this, palette, { setPresetInputExpanded(!presetInputExpanded) },
            { dispatchCommand(IdeCommand.MAXIMIZE_TERMINAL) }, { dispatchCommand(IdeCommand.CLOSE_TERMINAL) },
            { showContextCommands(it, IdeCommands.terminalMenuItems) }, { dispatchCommand(IdeCommand.CLEAR_TERMINAL) })
        outputRegion.addView(panelToolbar, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))
        outputTabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        panelOutputScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(outputTabs) }
        outputRegion.addView(panelOutputScroll, LinearLayout.LayoutParams(-1, dp(36)))
        val stdinCaption = label("标准输入 stdin", 11f, muted).apply { setPadding(dp(10), dp(3), 0, dp(3)) }
        val inputHeading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        inputHeading.addView(stdinCaption, LinearLayout.LayoutParams(0, -2, 1f))
        inputHeading.addView(button("清空输入") { input.setText("") })
        panelInputHeading = inputHeading
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
            if (focused) { clearSnippet(); lastFocus = "stdin"; updateImeLayout() }
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
        input.visibility = if (presetInputExpanded) View.VISIBLE else View.GONE
        panelInputHeading.visibility = input.visibility
        outputRegion.addView(input, LinearLayout.LayoutParams(-1, dp(64)))
        val scroll = ScrollView(this)
        consoleText = TerminalView(this).apply {
            textSize = 12f; setTextColor(green)
            typeface = Typeface.MONOSPACE; gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(8), dp(10), dp(8)); setTextIsSelectable(true)
        }
        scroll.addView(consoleText)
        outputRegion.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return outputRegion
    }

    private fun makeExplorerPane(sidebar: Boolean): LinearLayout {
        val panelView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(surface) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, 0, 0) }
        row.addView(label("资源管理器", 12f, muted, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        IdeCommands.explorerToolbarItems.forEach { command ->
            row.addView(IdeControls.icon(this, palette, if (command == IdeCommand.NEW_FILE) IdeIcon.ADD_FILE else IdeIcon.ADD_FOLDER,
                command.title) { dispatchCommand(command) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        row.addView(IdeControls.icon(this, palette, IdeIcon.MORE, "文件与工作区命令") { showContextCommands(row, IdeCommands.explorerMenuItems) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        panelView.addView(row)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val fileList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (sidebar) sidebarExplorerList = fileList else overlayExplorerList = fileList
        scroll.addView(fileList)
        panelView.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderExplorer()
        return panelView
    }

    private fun showExplorer() {
        if (workspaceBusy) return
        if (currentGeometry?.showExplorerSidebar == true) {
            explorerVisible = !explorerVisible
            val pane = explorerPane ?: return
            explorerResizeHandle?.visibility = if (explorerVisible) View.VISIBLE else View.GONE
            activityBar?.setExplorerActive(explorerVisible)
            if (explorerVisible) Animations.slideIn(pane, -pane.width.toFloat(), enabled = motionEnabled())
            else Animations.slideOut(pane, -pane.width.toFloat(), enabled = motionEnabled())
            prefs.edit().putBoolean("explorerVisible", explorerVisible).apply()
            return
        }
        if (explorerDialog?.isShowing == true) { explorerDialog?.dismiss(); return }
        val content = makeExplorerPane(false)
        explorerDialog = AlertDialog.Builder(this).setTitle("项目文件 · CodePocket").setView(content)
            .setPositiveButton("完成", null).create().also { dialog ->
                dialog.setOnDismissListener { overlayExplorerList = null; if (explorerDialog === dialog) explorerDialog = null }
                dialog.show()
                dialog.window?.setLayout(-1, -1)
            }
    }

    private fun renderExplorer() {
        listOfNotNull(sidebarExplorerList, overlayExplorerList).forEach(::renderExplorerList)
    }

    private fun renderExplorerList(explorerList: LinearLayout) {
        explorerList.removeAllViews()
        val rootLabel = label(workspace.title, 12f, muted, true).apply {
            decorateFileLabel(this, IdeIcon.FOLDER_OPEN, workspace.title)
            setPadding(dp(10), dp(8), 0, dp(8))
            setOnClickListener { currentDirectory = ""; persistNavigation(); status.text = "新建位置：项目根目录" }
        }
        explorerList.addView(rootLabel)
        fun visit(directory: File, level: Int) {
            if (level > 20) return
            workspace.children(directory).forEach { file ->
                val relative = workspace.path(file)
                val folder = file.isDirectory
                val open = relative in expanded
                val icon = if (folder) (if (open) IdeIcon.FOLDER_OPEN else IdeIcon.FOLDER) else IdeIconDrawable.file(relative)
                val item = label(file.name, 13f, if (relative == activePath) accent else textForeground).apply {
                    decorateFileLabel(this, icon, relative + if (folder) (if (open) "，已展开" else "，已折叠") else "")
                    minHeight = dp(DesignTokens.TOUCH)
                    setPadding(dp(12 + level * 15), dp(8), dp(4), dp(8))
                    if (relative == activePath) background = shape(editorBackground, 3)
                    isClickable = true; isFocusable = true
                    setOnClickListener {
                        if (workspaceBusy) return@setOnClickListener
                        if (folder) {
                            currentDirectory = relative
                            if (open) expanded.remove(relative) else expanded.add(relative)
                            persistNavigation(); renderExplorer(); animateDecoration(explorerList)
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
                    if (directory) {
                        expanded.add(workspace.path(item)); currentDirectory = workspace.path(item); persistNavigation()
                    }
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
                renderExplorer(); refreshTabs(); updateShellTitle()
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
                        // Deletion is already confirmed/completed. Preserve any code typed during I/O.
                        if (activePath == path || activePath.startsWith("$path/")) queueDraft()
                        sessions.removeTree(path); persistSessions()
                        if (currentDirectory == path || currentDirectory.startsWith("$path/")) currentDirectory = ""
                        opened.removeAll { it == path || it.startsWith("$path/") }
                        if (activePath == path || activePath.startsWith("$path/")) {
                            if (opened.isEmpty()) opened.add(fallback)
                            activePath = opened.last(); loadedPath = null; loadActive()
                        } else persistNavigation()
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
        handler.removeCallbacks(refreshEditor); handler.post(refreshEditor)
        persistNavigation()
    }

    private fun beginDocumentTransition(): EditorDocumentRequest? {
        val frame = editorPort?.captureDocument()
        if (frame?.composing == true) {
            if (::status.isInitialized) status.setText(R.string.editor_edit_composing)
            return null
        }
        return documentTransitions.begin(frame, editRevision)
    }

    private fun commitDocumentTransition(request: EditorDocumentRequest, action: () -> Unit): Boolean {
        val result = documentTransitions.commit(request, editorPort?.captureDocument(), editRevision) {
            // Capture after background reading, before changing the workspace/path or native text.
            captureSession(); if (::sessions.isInitialized) persistSessions()
            action()
        }
        if (result == DocumentTransitionResult.APPLIED) return true
        if (::status.isInitialized) status.setText(if (result == DocumentTransitionResult.COMPOSING)
            R.string.editor_edit_composing else R.string.editor_document_changed)
        preservePendingDocumentEdits()
        return false
    }

    private fun preservePendingDocumentEdits() {
        queueDraft()
        if (dirty && settings.autoSave) {
            handler.removeCallbacks(autosave); handler.postDelayed(autosave, settings.autoSaveDelayMs.toLong())
        }
    }

    private fun switchFile(relative: String, after: (() -> Unit)? = null): Boolean {
        if (workspaceBusy) return false
        revealEditor()
        if (relative == activePath && loadedPath == activePath) { after?.invoke(); return true }
        saveActive {
            val transition = beginDocumentTransition() ?: return@saveActive
            captureSession(); persistSessions()
            val key = sessions.key(relative)
            ioAction("正在打开 $relative…", {
                workspace.read(workspace.resolve(relative)) to drafts.read(key)
            }) { (content, draft) ->
                commitDocumentTransition(transition) {
                    val previousPath = activePath
                    val newlyOpened = relative !in opened
                    activePath = relative
                    if (newlyOpened) opened.add(relative)
                    if (loadActive(content, draft)) {
                        refreshTabs(); renderExplorer(); animateDecoration(tabsRow); after?.invoke()
                    } else {
                        activePath = previousPath
                        if (newlyOpened) opened.remove(relative)
                        refreshTabs(); renderExplorer()
                    }
                }
            }
        }
        return false
    }

    private fun closeTab(relative: String) {
        if (workspaceBusy || relative !in opened) return
        saveActive {
            captureSession(); persistSessions()
            if (relative != activePath) {
                opened.remove(relative); persistNavigation(); refreshTabs(); renderExplorer()
            } else {
                // Keep the active tab until its replacement has actually loaded successfully.
                val closed = {
                    opened.remove(relative); persistNavigation(); refreshTabs(); renderExplorer()
                }
                val next = opened.lastOrNull { it != relative }
                if (next != null) switchFile(next, closed)
                else {
                    val transition = beginDocumentTransition() ?: return@saveActive
                    ioAction("正在打开项目…", { ensureFallback() }) { path ->
                        commitDocumentTransition(transition) {
                            if (path != relative) switchFile(path, closed)
                            else { refreshTabs(); status.setText(R.string.editor_last_tab) }
                        }
                    }
                }
            }
        }
    }

    private fun refreshTabs() {
        if (!::tabsRow.isInitialized) return
        updateShellTitle()
        tabsRow.removeAllViews()
        opened.toList().forEach { path ->
            if (!workspace.resolve(path).isFile) { opened.remove(path); return@forEach }
            val one = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = shape(if (path == activePath) editorBackground else surface, 0) }
            val name = label("${if (path == activePath && dirty) "● " else ""}${path.substringAfterLast('/')}", 12f,
                if (path == activePath) accent else textForeground).apply {
                decorateFileLabel(this, IdeIconDrawable.file(path), path)
                maxWidth = dp(240); setPadding(dp(8), 0, dp(4), 0)
                setOnClickListener { switchFile(path) }
                setOnLongClickListener { alert(path); true }
            }
            one.addView(name, LinearLayout.LayoutParams(-2, dp(DesignTokens.TOUCH)))
            one.addView(IdeControls.icon(this, palette, IdeIcon.CLOSE, "关闭 $path") { closeTab(path) },
                LinearLayout.LayoutParams(dp(DesignTokens.TOUCH), dp(DesignTokens.TOUCH)))
            tabsRow.addView(one)
        }
    }

    private fun loadActive(content: String? = null, restored: Draft? = null): Boolean {
        editorAnalysis?.cancel()
        hideCompletion(); clearSnippet()
        if (content == null) {
            val transition = beginDocumentTransition() ?: return false
            val path = activePath; val key = sessions.key(path)
            ioAction("正在载入…", { workspace.read(workspace.resolve(path)) to drafts.read(key) }) { (text, draft) ->
                commitDocumentTransition(transition) { loadActive(text, draft) }
            }
            return false
        }
        val recovered = DraftRecovery.restore(content, restored)
        val previousQuiet = quietEdit
        quietEdit = true
        try {
            if (editorPort?.replaceDocument(recovered.text) != true) {
                status.setText(R.string.editor_document_rejected); return false
            }
            editRevision++
            loadedPath = activePath
            loadedVersion = recovered.baseHash
            dirty = recovered.dirty
            restoreSession()
        } finally { quietEdit = previousQuiet }
        handler.removeCallbacks(refreshEditor); handler.post(refreshEditor)
        updateNumbers(); updateSuggestions()
        updateShellTitle()
        status.text = if (dirty) "已恢复未写回草稿，请保存或导出" else if (isCpp()) "C++17 · Wandbox 云端" else "Python 3.13 · 本地"
        refreshTabs(); persistNavigation()
        return true
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
        if (!settings.lineNumbers) return
        val value = editor.text.toString()
        val layout = editor.layout
        val labels = if (settings.wordWrap && layout != null && layout.text.length == value.length)
            EditorSearch.gutter(value, (0 until layout.lineCount.coerceAtMost(10000)).map { layout.getLineStart(it) })
        else (1..(value.count { it == '\n' } + 1).coerceAtMost(10000)).joinToString("\n")
        if (numbers.text.toString() != labels) numbers.text = labels
    }

    private fun completionRequest() = CompletionRequest(sessions.key(activePath), editRevision,
        editor.text.toString(), editor.selectionStart, editor.selectionEnd, isCpp())

    private fun hideCompletion(suppress: Boolean = false) {
        hideSignatureHelp(suppress)
        if (suppress && ::sessions.isInitialized && ::editor.isInitialized) completionSuppressed = completionRequest()
        completionGeneration++; completionTask?.cancel(true); completionTask = null
        completionBatch = null
        completionPopup?.dismiss()
    }

    private fun updateSuggestions(force: Boolean = false) {
        if (!::editor.isInitialized || !::sessions.isInitialized) return
        if (isFinishing || !hasWindowFocus() || workspaceBusy || loadedPath != activePath || !editor.hasFocus() || !editor.isShown ||
            toolDialog?.isShowing == true || explorerDialog?.isShowing == true ||
            BaseInputConnection.getComposingSpanStart(editor.text) >= 0 || editor.selectionStart != editor.selectionEnd ||
            (!force && (snippetSession != null || !settings.autoCompletion || editor.length() > 45000))) { hideCompletion(); return }
        val request = completionRequest()
        if (force) completionSuppressed = null
        if (!force && completionSuppressed == request) return
        if (completionBatch?.request == request && completionPopup?.isShowing == true) {
            if (force) completionExplicit = true
            completionPopup?.reposition(); return
        }
        hideCompletion()
        val generation = completionGeneration
        val includeSnippets = snippetSession == null
        completionTask = completionExecutor.submit {
            val batch = LexicalCompletion.complete(request, includeSnippets)
            if (Thread.currentThread().isInterrupted || isDestroyed) return@submit
            runOnUiThread {
                if (isDestroyed || isFinishing || !hasWindowFocus() || generation != completionGeneration || workspaceBusy ||
                    request != completionRequest() || !editor.hasFocus() || !editor.isShown ||
                    BaseInputConnection.getComposingSpanStart(editor.text) >= 0 ||
                    toolDialog?.isShowing == true || explorerDialog?.isShowing == true) return@runOnUiThread
                if (batch == null) {
                    if (force) status.setText(if (LexicalCompletion.memberContext(request))
                        R.string.completion_members_pending else R.string.completion_empty)
                    return@runOnUiThread
                }
                hideSignatureHelp()
                completionBatch = batch; completionExplicit = force
                val popup = completionPopup ?: CompletionPopup(this, editor, editorPort ?: return@runOnUiThread, surface, textForeground, muted, accent,
                    { index -> acceptCompletion(index) }, {
                        completionBatch?.let { completionSuppressed = it.request }
                        completionBatch = null; completionGeneration++; completionTask?.cancel(true)
                    }).also { completionPopup = it }
                if (!popup.show(batch, motionEnabled())) {
                    hideCompletion()
                    if (force) status.setText(R.string.completion_no_space)
                }
            }
        }
    }

    private fun hideSignatureHelp(suppress: Boolean = false) {
        if (suppress && ::sessions.isInitialized && ::editor.isInitialized) signatureSuppressed = completionRequest()
        signatureGeneration++; signatureTask?.cancel(true); signatureTask = null
        signaturePopup?.dismiss()
    }

    private fun updateSignatureHelp(force: Boolean = false) {
        if (!::editor.isInitialized || !::sessions.isInitialized) return
        if (isFinishing || isDestroyed || !hasWindowFocus() || workspaceBusy || loadedPath != activePath ||
            !editor.hasFocus() || !editor.isShown || editor.length() > 45000 ||
            BaseInputConnection.getComposingSpanStart(editor.text) >= 0 || editor.selectionStart != editor.selectionEnd ||
            toolDialog?.isShowing == true || explorerDialog?.isShowing == true ||
            completionPopup?.isShowing == true || (!force && !settings.signatureHelp)) { hideSignatureHelp(); return }
        val request = completionRequest()
        if (force) signatureSuppressed = null
        if (!force && signatureSuppressed == request) return
        hideSignatureHelp()
        val generation = signatureGeneration
        signatureTask = signatureExecutor.submit {
            val found = SignatureHelp.find(request)
            if (Thread.currentThread().isInterrupted || isDestroyed) return@submit
            runOnUiThread {
                if (isDestroyed || isFinishing || !hasWindowFocus() || generation != signatureGeneration || workspaceBusy ||
                    request != completionRequest() || !editor.hasFocus() || !editor.isShown ||
                    BaseInputConnection.getComposingSpanStart(editor.text) >= 0 ||
                    toolDialog?.isShowing == true || explorerDialog?.isShowing == true || completionPopup?.isShowing == true) return@runOnUiThread
                if (found == null || !found.matches(completionRequest())) {
                    if (force) status.setText(R.string.signature_empty)
                    return@runOnUiThread
                }
                val popup = signaturePopup ?: SignaturePopup(this, editor, editorPort ?: return@runOnUiThread, surface, textForeground, muted, accent,
                    { hideSignatureHelp(true) }).also { signaturePopup = it }
                if (!popup.show(found, motionEnabled()) && force) status.setText(R.string.signature_no_space)
            }
        }
    }

    private fun requestSignatureHelp() {
        if (workspaceBusy || !editor.hasFocus()) return
        handler.removeCallbacks(refreshEditor)
        hideCompletion()
        updateSignatureHelp(true)
    }

    private fun requestCompletion() {
        // Requests from stdin must not steal its focus or change the code buffer.
        if (workspaceBusy || !editor.hasFocus()) return
        handler.removeCallbacks(refreshEditor)
        updateSuggestions(true)
    }

    private fun acceptCompletion(index: Int = completionPopup?.selectedIndex ?: 0): Boolean {
        if (workspaceBusy || !editor.hasFocus() || completionPopup?.isShowing != true ||
            BaseInputConnection.getComposingSpanStart(editor.text) >= 0) return false
        val source = completionRequest()
        val insertion = completionBatch?.insertion(source, index, settings, Workspace.MAX_CHARS) ?: run {
            hideCompletion(); return false
        }
        return applyCompletionInsertion(insertion, source)
    }

    private fun updateSnippetHint() {
        if (!::completionHint.isInitialized) return
        val session = snippetSession
        val text = if (session == null) ""
            else getString(R.string.snippet_progress, session.index + 1, session.stops.size, session.active.label)
        if (completionHint.text.toString() != text) completionHint.text = text
        val height = (root.height / resources.displayMetrics.density).toInt()
        suggestionScroll.visibility = if (WorkspaceLayoutPolicy.showSnippet(session != null, imeVisible, height)) View.VISIBLE else View.GONE
    }

    private fun clearSnippet() { snippetSession = null; updateSnippetHint() }

    private fun validSnippet(): Boolean {
        val session = snippetSession ?: return false
        val valid = loadedPath == activePath && session.length == editor.length() &&
            session.matches(sessions.key(activePath), editRevision, editor.selectionStart, editor.selectionEnd)
        if (!valid) clearSnippet()
        return valid
    }

    private fun trackSnippetEdit(start: Int, removed: Int, inserted: Int) {
        val session = snippetSession ?: return
        snippetSession = if (loadedPath == activePath)
            session.edited(sessions.key(activePath), editRevision, start, removed, inserted) else null
    }

    private fun selectEditor(request: EditorSelectionRequest, keepSnippet: Boolean = false): Boolean {
        val previousQuiet = quietEdit
        quietEdit = true
        val accepted = try { editorPort?.select(request) == true } finally { quietEdit = previousQuiet }
        if (!accepted) return false
        hideCompletion()
        if (!keepSnippet) clearSnippet()
        updateStatusBar()
        handler.removeCallbacks(refreshEditor); handler.post(refreshEditor)
        return true
    }

    private fun moveSnippet(reverse: Boolean): Boolean {
        if (workspaceBusy || !editor.hasFocus() || BaseInputConnection.getComposingSpanStart(editor.text) >= 0 || !validSnippet()) return false
        val source = editorPort?.snapshot()?.buffer ?: return false
        val navigation = snippetSession!!.move(reverse)
        if (!selectEditor(EditorSelectionRequest(source, navigation.start, navigation.end), keepSnippet = true)) return false
        snippetSession = navigation.session
        completionSuppressed = completionRequest()
        updateSnippetHint()
        return true
    }

    private fun applyCompletionInsertion(insertion: CompletionEdit, source: CompletionRequest): Boolean {
        hideCompletion()
        val result = applyChange(insertion.change, keepSnippet = insertion.snippet == null,
            source = EditorSnapshot(source.document, source.revision, source.code, source.caret, source.end))
        if (!finishEditorChange(result, insertion.snippet)) return false
        completionSuppressed = completionRequest()
        return true
    }

    private fun showSnippetMenu() {
        if (workspaceBusy || !editor.hasFocus() || loadedPath != activePath ||
            BaseInputConnection.getComposingSpanStart(editor.text) >= 0) return
        val snapshot = completionRequest()
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (validSnippet()) {
            options.add(getString(R.string.snippet_previous) to { moveSnippet(true); Unit })
            options.add(getString(R.string.snippet_next) to { moveSnippet(false); Unit })
            options.add(getString(R.string.snippet_finish) to { clearSnippet() })
        }
        if (CodeSnippets.replacement(snapshot) != null) CodeSnippets.all(isCpp()).forEach { descriptor ->
            options.add("${descriptor.trigger} · ${descriptor.title}" to {
                if (!workspaceBusy && snapshot == completionRequest() &&
                    BaseInputConnection.getComposingSpanStart(editor.text) < 0) {
                    val expanded = CodeSnippets.expand(descriptor.id, snapshot, settings, Workspace.MAX_CHARS)
                    if (expanded == null) status.setText(R.string.snippet_cannot_insert)
                    else applyCompletionInsertion(CompletionEdit(expanded.change, expanded), snapshot)
                }
                Unit
            })
        }
        if (options.isEmpty()) { status.setText(R.string.snippet_line_help); return }
        toolDialog?.dismiss(); hideCompletion(true)
        toolDialog = AlertDialog.Builder(this).setTitle(R.string.snippet_title)
            .setItems(options.map { it.first }.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                if (snapshot.document == completionRequest().document && snapshot.revision == editRevision) options[which].second()
            }.setNegativeButton(R.string.snippet_cancel, null).show()
    }

    private fun insertText(snippet: String, caretInside: Boolean = false) {
        if (workspaceBusy || !editor.hasFocus() || BaseInputConnection.getComposingSpanStart(editor.text) >= 0) return
        val source = editorPort?.snapshot()?.buffer ?: return
        val start = minOf(source.start, source.end)
        val end = maxOf(source.start, source.end)
        val caret = start + snippet.length - if (caretInside) 1 else 0
        if (finishEditorChange(applyChange(TextChange(start, end, snippet, caret), keepSnippet = true, source = source)))
            editor.requestFocus()
    }

    /** Shift+Tab removes one indent. Tab indents selection or accepts a suggestion. */
    private fun indent(reverse: Boolean) {
        if (workspaceBusy || !editor.hasFocus() || BaseInputConnection.getComposingSpanStart(editor.text) >= 0) return
        if (reverse && moveSnippet(true)) return
        val source = editorPort?.snapshot()?.buffer ?: return
        val change = EditorActions.indent(source.text, source.start, source.end, reverse, settings.tabWidth, settings.useSpaces)
        if (finishEditorChange(applyChange(change, source = source))) editor.requestFocus()
    }

    private fun applyChange(change: TextChange, keepSnippet: Boolean = false, source: EditorSnapshot? = null,
        matchSelection: Boolean = true): EditResult {
        val port = editorPort ?: return EditResult.NO_DOCUMENT
        val expected = source ?: port.snapshot()?.buffer ?: return EditResult.NO_DOCUMENT
        val previousQuiet = quietEdit
        quietEdit = true
        val result = try { port.applyEdit(EditorTransaction(expected, change, matchSelection)) }
            finally { quietEdit = previousQuiet }
        // Rejected requests must not invalidate a valid snippet or mark an unchanged buffer dirty.
        if (result.changesText) {
            if (keepSnippet) trackSnippetEdit(change.start, change.end - change.start, change.text.length) else clearSnippet()
        } else if (result == EditResult.SELECTION_ONLY && !keepSnippet) clearSnippet()
        return result
    }

    /** Used only for explicit edits. Native typing/history have their own TextWatcher revision path. */
    private fun finishEditorChange(result: EditResult, expansion: SnippetExpansion? = null): Boolean {
        if (!result.accepted) {
            status.setText(when (result) {
                EditResult.LIMIT_EXCEEDED -> R.string.editor_edit_limit
                EditResult.STALE, EditResult.NO_DOCUMENT -> R.string.editor_edit_stale
                EditResult.COMPOSING -> R.string.editor_edit_composing
                else -> R.string.editor_edit_invalid
            })
            return false
        }
        if (result.changesText) {
            dirty = true; editRevision++
            snippetSession = expansion?.session(sessions.key(activePath), editRevision, editor.length())
                ?: snippetSession?.copy(revision = editRevision)
            handler.removeCallbacks(checkpointDraft); handler.postDelayed(checkpointDraft, 200)
            handler.removeCallbacks(autosave)
            if (settings.autoSave) handler.postDelayed(autosave, settings.autoSaveDelayMs.toLong())
            updateNumbers(); refreshTabs()
        }
        validSnippet(); updateSnippetHint()
        handler.removeCallbacks(refreshEditor); handler.post(refreshEditor)
        return true
    }

    private fun tab() {
        if (!editor.hasFocus() || BaseInputConnection.getComposingSpanStart(editor.text) >= 0) return
        if (settings.completionTab && acceptCompletion()) return
        if (moveSnippet(false)) return
        hideCompletion(); indent(false)
    }

    private fun toggleTheme() = applySettings(settings.copy(theme = if (dark) UiTheme.LIGHT else UiTheme.DARK))

    private fun isSystemDark() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun showSettings() {
        toolDialog?.dismiss()
        hideCompletion(true)
        clearSnippet()
        toolDialog = SettingsDialog.show(this, settings, ::applySettings)
    }

    private fun applySettings(next: AppSettings) {
        if (workspaceBusy) return
        val themeChanged = next.isDark(isSystemDark()) != dark
        if (themeChanged && (dirty || saving)) { saveActive { applySettings(next) }; return }
        hideCompletion()
        captureSession(); persistSessions()
        settings = next.normalized()
        dark = settings.isDark(isSystemDark())
        prefs.edit().putString("settings", settings.toJson()).putBoolean("dark", dark).apply()
        if (themeChanged) { persist(); recreate() }
        else { applyEditorPreferences(); restoreSession(); status.text = "设置已保存" }
    }

    private fun applyEditorPreferences() {
        editor.textSize = settings.fontSize.toFloat(); numbers.textSize = settings.fontSize.toFloat()
        consoleText.textSize = settings.terminalFontSize.toFloat()
        numbers.visibility = if (settings.lineNumbers) View.VISIBLE else View.GONE
        editor.setHorizontallyScrolling(!settings.wordWrap)
        (editor.parent as? View)?.setBackgroundColor(codeBackground)
        editor.setTextColor(codeForeground); numbers.setTextColor(if (syntaxDark) 0xFF9DA3AD.toInt() else 0xFF636A74.toInt())
        if (::sessions.isInitialized && loadedPath == activePath && settings.wordWrap) {
            val file = sessions.file(activePath); file.view = file.view.copy(scrollX = 0)
        }
        if (!settings.animations) listOfNotNull(explorerPane, if (::tabsRow.isInitialized) tabsRow else null,
            if (::outputRegion.isInitialized) outputRegion else null).forEach(Animations::cancel)
        currentGeometry?.let { updateExplorerLayout(ExplorerLayout.resolve(it, explorerWidth)) }
        updateImeLayout(); updateNumbers(); refreshTabs()
        handler.removeCallbacks(autosave)
        if (dirty && settings.autoSave) handler.postDelayed(autosave, settings.autoSaveDelayMs.toLong())
        handler.removeCallbacks(refreshEditor); handler.post(refreshEditor)
    }

    private fun updateTabStops() {
        val value = editor.text
        value.getSpans(0, value.length, android.text.style.TabStopSpan::class.java).forEach { value.removeSpan(it) }
        val width = editor.paint.measureText(" ") * settings.tabWidth
        for (column in 1..64) value.setSpan(android.text.style.TabStopSpan.Standard((column * width).toInt().coerceAtLeast(1)),
            0, value.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }

    private fun animateDecoration(view: View?) {
        if (view == null) return
        view.animate().cancel(); view.alpha = 1f
        if (motionEnabled()) {
            view.alpha = .88f
            view.animate().alpha(1f).setDuration(120).start()
        }
    }

    private fun motionEnabled(): Boolean {
        val scale = runCatching { android.provider.Settings.Global.getFloat(contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(0f)
        val powerSave = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode
        return !imeVisible && MotionPolicy.enabled(settings.animations, scale, powerSave) &&
            (Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())
    }

    private fun searchSnapshot() = EditorSnapshot(sessions.key(activePath), editRevision,
        editor.text.toString(), editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0))

    private fun showProjectSearch() {
        if (workspaceBusy || loadedPath != activePath) return
        projectSearch?.cancel(); toolDialog?.dismiss(); hideCompletion(true); clearSnippet()
        val targetWorkspace = workspace
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val query = EditText(this).apply {
            setSingleLine(); hint = "项目文字搜索（含当前未保存缓冲区）"
            filters = arrayOf(InputFilter.LengthFilter(256))
        }
        val case = android.widget.CheckBox(this).apply { text = "区分大小写" }
        val message = TextView(this).apply { setTextColor(muted); text = getString(R.string.project_search_notice) }
        val list = android.widget.ListView(this)
        val search = android.widget.Button(this).apply { text = "搜索" }
        layout.addView(query); layout.addView(case); layout.addView(search); layout.addView(message)
        layout.addView(list, LinearLayout.LayoutParams(-1, dp(240)))
        val dialog = AlertDialog.Builder(this).setTitle("项目搜索").setView(layout).setNegativeButton("关闭", null).create()
        var request = 0L
        var dialogSearch: ProjectSearch? = null
        var hits = emptyList<ProjectSearch.SearchResult>()
        search.setOnClickListener {
            val needle = query.text.toString()
            if (needle.isEmpty()) { query.error = "请输入搜索内容"; return@setOnClickListener }
            projectSearch?.cancel()
            val engine = ProjectSearch(targetWorkspace); projectSearch = engine; dialogSearch = engine
            val generation = ++request
            val caseSensitive = case.isChecked
            val buffers = mapOf(activePath to editor.text.toString())
            hits = emptyList(); list.adapter = null; message.text = "正在搜索，可修改条件后重新搜索或关闭取消…"
            WorkspaceIo.executor.execute {
                val response = runCatching { engine.search(needle, caseSensitive, 200, buffers) }
                runOnUiThread {
                    if (isDestroyed || !dialog.isShowing || toolDialog !== dialog || workspace !== targetWorkspace || generation != request) return@runOnUiThread
                    response.onSuccess { found ->
                        hits = found
                        list.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1,
                            found.map { "${it.path}:${it.lineNumber}\n${it.lineContent.take(160)}" })
                        message.text = if (engine.wasCancelled) getString(R.string.project_search_cancelled)
                            else getString(R.string.project_search_summary, found.size, engine.skippedFiles,
                                if (engine.limited) getString(R.string.project_search_limited) else "")
                    }.onFailure { message.text = getString(R.string.project_search_failed, it.message.orEmpty()) }
                }
            }
        }
        list.setOnItemClickListener { _, _, index, _ ->
            val hit = hits.getOrNull(index) ?: return@setOnItemClickListener
            if (workspace !== targetWorkspace) return@setOnItemClickListener
            dialog.dismiss()
            switchFile(hit.path) {
                if (workspace !== targetWorkspace) return@switchFile
                val source = editorPort?.snapshot()?.buffer ?: return@switchFile
                val offset = EditorSearch.lineOffset(source.text, hit.lineNumber)
                if (offset == null || source.text.substring(offset).substringBefore('\n').trim() != hit.lineContent ||
                    !selectEditor(EditorSelectionRequest(source, offset, focus = true))) {
                    status.setText(R.string.editor_navigation_stale)
                }
            }
        }
        dialog.setOnDismissListener {
            request++; dialogSearch?.cancel()
            if (projectSearch === dialogSearch) projectSearch = null
            if (toolDialog === dialog) toolDialog = null
        }
        toolDialog = dialog; dialog.show()
    }

    @Deprecated("Native back handling retained for API 24; predictive-back UI still needs device verification")
    override fun onBackPressed() {
        if (!::editor.isInitialized) { super.onBackPressed(); return }
        when (InteractionState.back(imeVisible, currentGeometry?.showExplorerSidebar == true && explorerVisible,
            panelState.maximized && panelVisible, panelLayout?.showPanel == true)) {
            BackAction.HIDE_IME -> hideKeyboard()
            BackAction.CLOSE_EXPLORER -> showExplorer()
            BackAction.RESTORE_PANEL -> setPanelMaximized(false)
            BackAction.CLOSE_PANEL -> closePanel()
            BackAction.EXIT -> {
                if (workspaceBusy || saving) { status.text = "请等待文件操作完成后退出"; return }
                if (!dirty) { finish(); return }
                AlertDialog.Builder(this).setTitle("保存后退出？")
                    .setMessage("当前修改尚未保存。保存失败时会保留编辑内容和恢复草稿，并留在编辑器。")
                    .setPositiveButton("保存并退出") { _, _ -> saveActive { finish() } }
                    .setNeutralButton("导出当前内容") { _, _ -> exportFile() }
                    .setNegativeButton("取消", null).show()
            }
        }
    }

    private fun showSearch(replace: Boolean) {
        if (workspaceBusy || loadedPath != activePath) return
        toolDialog?.dismiss()
        hideCompletion(true)
        revealEditor()
        clearSnippet()
        toolDialog = SearchDialog.show(this, replace, ::searchSnapshot, { source, start, end ->
            selectEditor(EditorSelectionRequest(source, start, end))
        }, { source, change, matchSelection ->
            val result = applyChange(change, source = source, matchSelection = matchSelection)
            val accepted = finishEditorChange(result)
            if (result.changesText) queueDraft()
            accepted
        })
    }

    private fun showGoToLine() {
        if (workspaceBusy || loadedPath != activePath) return
        hideCompletion(true)
        revealEditor()
        clearSnippet()
        val source = editorPort?.snapshot()?.buffer ?: return
        val field = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "从 1 开始的行号" }
        val dialog = AlertDialog.Builder(this).setTitle("跳转行").setView(field)
            .setNegativeButton("取消", null).setPositiveButton("跳转", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val line = field.text.toString().toIntOrNull()
            val offset = line?.let { EditorSearch.lineOffset(source.text, it) }
            if (offset == null) field.error = "行号超出范围"
            else if (!selectEditor(EditorSelectionRequest(source, offset))) field.error = getString(R.string.editor_navigation_stale)
            else { dialog.dismiss(); editor.requestFocus() }
        } }
        toolDialog?.dismiss(); toolDialog = dialog; dialog.show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::editor.isInitialized || toolDialog?.isShowing == true || explorerDialog?.isShowing == true)
            return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP && completionConsumedKeys.remove(event.keyCode)) return true
        if (event.action == KeyEvent.ACTION_DOWN && editor.hasFocus()) {
            val key = when (event.keyCode) {
                KeyEvent.KEYCODE_TAB -> CompletionKey.TAB
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> CompletionKey.ENTER
                KeyEvent.KEYCODE_DPAD_UP -> CompletionKey.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> CompletionKey.DOWN
                KeyEvent.KEYCODE_ESCAPE -> CompletionKey.ESC
                else -> CompletionKey.OTHER
            }
            val composing = BaseInputConnection.getComposingSpanStart(editor.text) >= 0
            val action = CompletionKeys.action(key, settings, completionPopup?.isShowing == true, completionExplicit,
                completionPopup?.explicitSelection == true, composing, editor.selectionStart != editor.selectionEnd,
                event.isCtrlPressed || event.isAltPressed || event.isShiftPressed || event.isMetaPressed,
                event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0)
            val handled = when (action) {
                CompletionAction.ACCEPT -> acceptCompletion()
                CompletionAction.NEXT -> { completionPopup?.move(1); true }
                CompletionAction.PREVIOUS -> { completionPopup?.move(-1); true }
                CompletionAction.DISMISS -> { hideCompletion(true); true }
                CompletionAction.PASS -> false
            }
            if (handled) { completionConsumedKeys.add(event.keyCode); return true }
            val snippetAction = SnippetKeys.action(key, event.isShiftPressed, validSnippet(), handled, composing,
                event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0, event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)
            val snippetHandled = when (snippetAction) {
                SnippetKeyAction.NEXT -> moveSnippet(false)
                SnippetKeyAction.PREVIOUS -> moveSnippet(true)
                SnippetKeyAction.END -> { hideCompletion(true); clearSnippet(); true }
                SnippetKeyAction.PASS -> false
            }
            if (snippetHandled) { completionConsumedKeys.add(event.keyCode); return true }
            if (SignatureKeys.dismiss(key == CompletionKey.ESC, signaturePopup?.isShowing == true, composing,
                event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0,
                event.isCtrlPressed || event.isAltPressed || event.isShiftPressed || event.isMetaPressed)) {
                hideSignatureHelp(true); completionConsumedKeys.add(event.keyCode); return true
            }
            if (key == CompletionKey.TAB && !composing && event.flags and KeyEvent.FLAG_SOFT_KEYBOARD == 0 && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
                hideCompletion(); indent(event.isShiftPressed); completionConsumedKeys.add(event.keyCode); return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            if (event.keyCode in listOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_Y) &&
                !event.isAltPressed && !event.isMetaPressed && event.flags and KeyEvent.FLAG_SOFT_KEYBOARD == 0 &&
                (editor.hasFocus() || input.hasFocus())) {
                dispatchCommand(if (event.keyCode == KeyEvent.KEYCODE_Y || event.isShiftPressed) IdeCommand.REDO else IdeCommand.UNDO)
                completionConsumedKeys.add(event.keyCode)
                return true
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_F -> { if (event.isShiftPressed) showProjectSearch() else showSearch(false); return true }
                KeyEvent.KEYCODE_H -> { showSearch(true); return true }
                KeyEvent.KEYCODE_G -> { showGoToLine(); return true }
                KeyEvent.KEYCODE_S -> { saveActive { status.text = "✓ 已保存 $activePath" }; return true }
                KeyEvent.KEYCODE_N -> { askCreate(false); return true }
                KeyEvent.KEYCODE_O -> { importFile(); return true }
                KeyEvent.KEYCODE_B -> { showExplorer(); return true }
                KeyEvent.KEYCODE_SPACE -> { if (editor.hasFocus()) { if (event.isShiftPressed) requestSignatureHelp() else requestCompletion(); return true } }
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
                val transition = beginDocumentTransition() ?: return@setPositiveButton
                handler.removeCallbacks(autosave); handler.removeCallbacks(checkpointDraft)
                val path = activePath; val key = sessions.key(path); val text = editor.text.toString(); val base = loadedVersion
                ioAction("正在保留草稿并载入…", {
                    drafts.put("$key:backup:${System.currentTimeMillis()}", path, text, base)
                    val fresh = workspace.read(workspace.resolve(path))
                    drafts.removeIfMatches(key, text)
                    fresh
                }) { fresh -> commitDocumentTransition(transition) { loadActive(fresh) } }
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
                val transition = beginDocumentTransition() ?: return@setNeutralButton
                handler.removeCallbacks(autosave); handler.removeCallbacks(checkpointDraft)
                val key = sessions.key(activePath); val path = activePath; val text = editor.text.toString(); val base = loadedVersion
                captureSession(); persistSessions()
                ioAction("正在保留恢复草稿…", { drafts.put(key, path, text, base) }) {
                    commitDocumentTransition(transition) { startWorkspace(tree) }
                }
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
        if (!panelVisible) togglePanel()
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
                    workspace.identity, entry.absolutePath, sessions.key(path))
            }
        } else RunSession.start(applicationContext, code, stdin, cpp, runningFile, projectRoot, workspace.identity,
            entryKey = sessions.key(activePath))
    }

    private fun renderRunState() {
        if (!::mainToolbar.isInitialized) return
        updateMainToolbarState()
        outputDisplay.synchronize(RunSession.result, running)
        result = RunSession.result
        if (result == null && !running) showOutput(selectedOutput)
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
        consoleText.setOutput(when {
            outputDisplay.cleared && type != 2 -> "显示已清空；预设输入和运行结果仍保留，下次运行显示新输出。"
            current == null -> "运行程序后，这里显示输出、编译错误和状态。"
            type == 0 -> current.stdout.ifEmpty { "（没有标准输出）" }
            type == 1 -> current.stderr.ifEmpty { "（没有错误信息）" }
            else -> "执行引擎：${current.engine}\n结果：${current.kind.label}\n退出码：${current.exitCode}\n耗时：${current.elapsedMs}ms" +
                (current.errorLine?.let { "\n错误行：$it" } ?: "")
        })
        consoleText.setTextColor(if (type == 1) red else if (type == 0) green else textForeground)
        if (!outputDisplay.cleared && consoleText.length() > 0 && type == 1 && current?.errorLine != null) {
            val clickable = android.text.SpannableString(consoleText.text)
            clickable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { gotoError() }
            }, 0, clickable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            consoleText.text = clickable
            consoleText.movementMethod = LinkMovementMethod.getInstance()
        } else consoleText.movementMethod = null
    }

    private fun gotoError() {
        val origin = result ?: return
        val line = origin.errorLine ?: return
        val targetWorkspace = workspace
        if (origin.workspaceId != null && origin.workspaceId != targetWorkspace.identity) {
            alert("请先打开运行时的原工作区，再定位错误"); return
        }
        val file = origin.errorFile ?: origin.sourcePath
        val relative = if (file == null) activePath else DiagnosticFiles.relative(targetWorkspace, file, origin.projectRoot)
        if (relative == null) { alert("错误位置不在当前工作区；请打开运行时的原工作区"); return }
        val entry = origin.sourcePath?.let { DiagnosticFiles.relative(targetWorkspace, it, origin.projectRoot) }
        val jump: () -> Unit = jump@{
            if (result !== origin || workspace !== targetWorkspace) {
                status.setText(R.string.editor_navigation_stale); return@jump
            }
            val source = editorPort?.snapshot()?.buffer ?: return@jump
            val located = editorPort?.locateDiagnostic(EditorDiagnostic(source, line,
                if (relative == entry) origin.sourceKey else null, if (relative == entry) origin.sourceHash else null))
                ?: DiagnosticStatus.NO_DOCUMENT
            if (located.located) { hideCompletion(); clearSnippet() }
            when (located) {
                DiagnosticStatus.LOCATED -> status.setText(R.string.editor_diagnostic_verified)
                DiagnosticStatus.LINE_ONLY -> status.setText(R.string.editor_diagnostic_unverified)
                DiagnosticStatus.CHANGED_SOURCE -> status.setText(R.string.editor_diagnostic_changed)
                DiagnosticStatus.INVALID_LINE -> status.setText(R.string.editor_diagnostic_line)
                DiagnosticStatus.COMPOSING -> status.setText(R.string.editor_edit_composing)
                else -> status.setText(R.string.editor_navigation_stale)
            }
        }
        switchFile(relative, jump)
    }

    private fun alert(message: String) { AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定", null).show() }
}
