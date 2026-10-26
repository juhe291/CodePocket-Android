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
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.codepocket.ide.animation.Animations
import com.codepocket.ide.animation.MotionEnvironment
import com.codepocket.ide.animation.LayoutMotion
import com.codepocket.ide.animation.SurfaceMotion
import com.codepocket.ide.animation.DialogMotion
import com.codepocket.ide.editor.EditorAnalysisController
import com.codepocket.ide.editor.SoraEditorPort
import com.codepocket.editorlab.WorkbenchEditor
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
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
import com.codepocket.ide.terminal.shell.TerminalCatalog
import com.codepocket.ide.terminal.shell.ShellTerminalPane
import java.io.File
import org.json.JSONArray

/** VS Code-inspired, responsive native Android learning IDE. */
class MainActivity : Activity() {
    private var diagnosticPopup: DiagnosticPopup? = null
    private lateinit var workspace: Workspace
    private val prefs by lazy { getSharedPreferences("codepocket_v2", MODE_PRIVATE) }
    private var motionObserver: MotionEnvironment? = null
    private var motionForeground = false
    private var themeSnapshot: SurfaceMotion.Snapshot? = null
    private var dark = true
    private var settings = AppSettings()
    private var toolDialog: AlertDialog? = null
    private var pendingSettingsState: String? = null
    private var settingsApplyFailure: (() -> Unit)? = null
    private val syntaxDark get() = settings.syntaxIsDark(dark)
    private val codeBackground get() = Color.parseColor(if (syntaxDark) "#1E242E" else "#FFFFFF")
    private val codeForeground get() = Color.parseColor(if (syntaxDark) "#D4D4D4" else "#242424")
    private var activePath = "main.cpp"
    private var currentDirectory = ""
    private var explorerSelection: String? = null
    private var pathBreadcrumb: TextView? = null
    private val expanded = mutableSetOf("")
    private val opened = mutableListOf<String>()
    private val running get() = RunSession.running
    private var result: RunResult? = null
    private var selectedOutput = 3
    private var completionPopup: CompletionPopup? = null
    private var completionTabButton: View? = null
    private var completionBatch: CompletionBatch? = null
    private var completionSuppressed: CompletionRequest? = null
    private var completionExplicit = false
    private var completionGeneration = 0L
    private var assistLayoutTask: Runnable? = null
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
    private var startupPanelPending = true
    private var panelVisible = true
    private var panelState = PanelState()
    private var panelLayout: PanelLayout? = null
    private lateinit var splitHost: LinearLayout
    private lateinit var topWorkspace: LinearLayout
    private lateinit var auxiliarySlot: FrameLayout
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
            if (editor.isComposing) { hideCompletion(); return@Runnable }
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
    private lateinit var editor: WorkbenchEditor
    private lateinit var input: EditText
    private lateinit var numbers: TextView
    private lateinit var status: TextView
    private lateinit var consoleText: TerminalView
    private lateinit var consoleResultScroll: ScrollView
    private var shellPane: ShellTerminalPane? = null
    private var shellPaneId = -1L
    private lateinit var terminalRegistry: ShellSessionRegistry
    private lateinit var tabsRow: LinearLayout
    private lateinit var suggestionRow: LinearLayout
    private var restoredExplorerScroll = 0
    private var sidebarExplorerList: LinearLayout? = null
    private var overlayExplorerList: LinearLayout? = null
    private lateinit var contentHost: LinearLayout
    private lateinit var editorToolbar: EditorToolbar
    private var explorerDragStartWidth = 240
    private var explorerDragViewport: EditorViewportSnapshot? = null
    private val outputDisplay = OutputDisplayState()
    private lateinit var mainToolbar: MainToolbar
    private lateinit var outputTabs: LinearLayout
    private var terminalSessionStrip: LinearLayout? = null
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
        pendingSettingsState = savedInstanceState?.getString("settingsDraft")
        themeSnapshot = lastNonConfigurationInstance as? SurfaceMotion.Snapshot
        startupPanelPending = savedInstanceState?.getBoolean("panelStartupPending", true) ?: true
        selectedOutput = savedInstanceState?.getInt("outputChannel", 3)?.coerceIn(0, 3) ?: 3
        drafts = DraftStore(File(filesDir, "drafts")) { a, b -> android.system.Os.rename(a.path, b.path) }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        applySystemBarPalette()
        setContentView(WorkspacePlaceholder.create(this, palette))
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
                        restoreExplorerViewport(saved)
                        reconcileExplorerSelection()
                        explorerVisible = prefs.getBoolean("explorerVisible", true)
                        if (!::root.isInitialized) {
                            panelVisible = saved?.getBoolean("panelVisible", false) ?: false
                            panelState = PanelState.fromJson(saved?.getString("panelState") ?: prefs.getString("panelLayout", null))
                                .let { if (panelVisible) it else it.copy(maximized = false) }
                        }
                        presetInputExpanded = prefs.getBoolean(prefKey("presetInputExpanded"), false)
                        explorerWidth = prefs.getInt("explorerWidth", 240)
                        createUi(); loadActive(ready.code, ready.draft)
                        // Bundle checkpoints are merged before reading; loadActive reconciles them once.
                        exportContent = saved?.getString("exportSnapshot")?.let { name ->
                            runCatching { File(filesDir, name).readText() }.getOrNull()
                        }
                        exportPath = saved?.getString("exportPath")
                        if (tree != null) rememberWorkspace(tree, workspace.title)
                        renderRunState()
                        if (pendingSettingsState != null) handler.post { if (!isDestroyed) showSettings() }
                        themeSnapshot?.let { snapshot ->
                            themeSnapshot = null
                            if (motionAllowed()) SurfaceMotion.play(root, snapshot,
                                android.graphics.Rect(0, 0, snapshot.width, snapshot.height), matchRootSize = true)
                            else snapshot.release()
                        }
                    }
                }.onFailure { error ->
                    themeSnapshot?.release(); themeSnapshot = null
                    documentTransitions.cancel(); preservePendingDocumentEdits()
                    if (!::root.isInitialized) {
                        setContentView(WorkspacePlaceholder.create(this, palette, unavailable = true))
                    }
                    AlertDialog.Builder(this).setTitle("工作区载入失败")
                        .setMessage("${error.message}\n恢复草稿仍保留在应用内。请选择原目录重新授权。")
                        .setPositiveButton("选择目录") { _, _ -> selectDirectory() }
                        .setNegativeButton("内部项目") { _, _ -> startWorkspace(null) }.showAnimated()
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
        if (motionObserver == null) motionObserver = MotionEnvironment(this, ::refreshMotionPolicy)
        motionObserver?.start()
        RunSession.listener = { renderRunState() }
        renderRunState()
    }

    override fun onStop() {
        motionObserver?.stop()
        RunSession.listener = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persist()
        if (exportContent != null) outState.putString("exportSnapshot", "pending-export.txt")
        outState.putString("exportPath", exportPath)
        outState.putInt("outputChannel", selectedOutput)
        saveExplorerViewport(outState)
        SettingsDialog.checkpoint(toolDialog)?.let { outState.putString("settingsDraft", it) }
        outState.putBoolean("panelVisible", panelVisible)
        outState.putBoolean("panelStartupPending", startupPanelPending)
        outState.putString("panelState", panelState.toJson())
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
        if (::editor.isInitialized) editor.resume()
        motionForeground = true
        shellPane?.hostActive(true)
        refreshMotionPolicy()
        if (::editor.isInitialized) { handler.removeCallbacks(refreshEditor); handler.post(refreshEditor) }
    }

    override fun onPause() {
        super.onPause()
        motionForeground = false
        if (::editor.isInitialized) editor.pause()
        shellPane?.hostActive(false)
        refreshMotionPolicy()
        if (::panelResize.isInitialized) panelResize.cancelGesture()
        explorerResizeHandle?.cancelGesture(); projectSearch?.cancel()
        cancelWorkbenchMotion()
        completionPopup?.dismiss(false); signaturePopup?.dismiss(false)
        diagnosticPopup?.dismiss()
        handler.removeCallbacks(refreshEditor); editorAnalysis?.cancel(); editorPort?.cancelNavigation(); hideCompletion(); clearSnippet(); completionConsumedKeys.clear()
        currentGeometry?.let { updateExplorerLayout(ExplorerLayout.resolve(it, explorerWidth)) }
        persist()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) { diagnosticPopup?.dismiss(); cancelWorkbenchMotion(); completionPopup?.dismiss(false); signaturePopup?.dismiss(false); editorAnalysis?.cancel(); hideCompletion(); completionConsumedKeys.clear() }
        else if (::editor.isInitialized) { refreshMotionPolicy(); handler.removeCallbacks(refreshEditor); handler.post(refreshEditor) }
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
            .putString(prefKey("explorerUi"), explorerUiCheckpoint().toJson())
            .putBoolean(prefKey("presetInputExpanded"), presetInputExpanded)
            .putBoolean("panelVisible", panelVisible).putString("panelLayout", panelState.toJson()).apply()
    }

    override fun onRetainNonConfigurationInstance(): Any? {
        // System night-mode changes recreate this Activity too. Size changes safely discard this bounded image later.
        val snapshot = themeSnapshot ?: if (settings.theme == UiTheme.SYSTEM && ::root.isInitialized)
            SurfaceMotion.capture(root, motionEnabled()) else null
        themeSnapshot = null
        return snapshot
    }

    override fun onDestroy() {
        motionObserver?.stop(); motionObserver = null
        settingsApplyFailure = null
        themeSnapshot?.release(); themeSnapshot = null
        diagnosticPopup?.dismiss(); diagnosticPopup = null
        super.onDestroy()
        shellPane?.dispose(); shellPane = null
        if (isFinishing) ShellSessionRegistry.finishAll()
        if (::panelResize.isInitialized) panelResize.cancelGesture()
        panelDragViewport = null
        documentTransitions.close()
        editorAnalysis?.close(); editorPort?.close()
        handler.removeCallbacksAndMessages(null)
        projectSearch?.cancel(); explorerResizeHandle?.cancelGesture()
        toolDialog?.dismiss(); commandMenu?.dismiss(); commandMenu = null
        hideCompletion(); completionExecutor.shutdownNow(); signatureExecutor.shutdownNow(); completionConsumedKeys.clear()
        cancelWorkbenchMotion()
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

    private fun applySystemBarPalette() {
        window.statusBarColor = surface
        window.navigationBarColor = panel
        val lightNavigationBar = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or lightNavigationBar
    }

    private fun createUi() {
        commandMenu?.dismiss(); commandMenu = null
        diagnosticPopup?.dismiss(); diagnosticPopup = null
        hideCompletion(); clearSnippet(); completionPopup = null; signaturePopup = null
        if (::panelResize.isInitialized) panelResize.cancelGesture()
        explorerResizeHandle?.cancelGesture()
        cancelWorkbenchMotion()
        activityBar = null; explorerPane = null; explorerResizeHandle = null
        sidebarExplorerList = null; overlayExplorerList = null; currentGeometry = null
        panelDragViewport = null; panelLayout = null
        applySystemBarPalette()
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(editorBackground) }
        setContentView(root)

        mainToolbar = MainToolbar(this, palette,
            { dispatchCommand(IdeCommand.RUN_STOP) },
            ::showCommandMenu, { dispatchCommand(IdeCommand.WORKSPACE) }, ::openTerminal, ::showWorkbenchMenu)
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
            val key=IdeControls.key(this,palette,name,command.title) {dispatchCommand(command)}
            if(command==IdeCommand.TAB)completionTabButton=key
            keyRow.addView(key,LinearLayout.LayoutParams(-2,dp(DesignTokens.TOUCH)))
        }
        keyboardToolbar.addView(keyRow)

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        contentHost = content
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        activityBar = ActivityBar(this, palette, { dispatchCommand(IdeCommand.SEARCH) }, ::showSettings,
            { dispatchCommand(IdeCommand.RUN_STOP) }) { dispatchCommand(IdeCommand.EXPLORER) }.also {
            content.addView(it, LinearLayout.LayoutParams(dp(48), -1))
        }
        val work = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(work, LinearLayout.LayoutParams(0, -1, 1f))
        buildEditor(work)

        val console = buildConsole()
        (editorRegion.parent as? ViewGroup)?.removeView(editorRegion)
        splitHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        panelResize = PanelResizeHandle(this).apply {
            setCallbacks(muted, {
                if (::splitHost.isInitialized) Animations.cancelTree(splitHost)
                hideCompletion(); panelDragViewport = capturePanelViewport()
            }, ::resizePanelFraction, ::finishPanelResize)
            setBackgroundColor(surface)
            setOnClickListener { showPanelSizeMenu(this) }
        }
        topWorkspace = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; layoutDirection=View.LAYOUT_DIRECTION_LTR }
        topWorkspace.addView(editorRegion, LinearLayout.LayoutParams(0, -1, 1f))
        // Reserved visual architecture only. Browser/Git/extensions stay deferred; no fake feature surfaces.
        auxiliarySlot = FrameLayout(this).apply { visibility=View.GONE; tag="auxiliary-slot" }
        topWorkspace.addView(auxiliarySlot, LinearLayout.LayoutParams(dp(260), -1))
        splitHost.addView(topWorkspace, LinearLayout.LayoutParams(-1, 0, 1f))
        splitHost.addView(panelResize, LinearLayout.LayoutParams(-1, dp(PanelLayoutPolicy.HANDLE)))
        splitHost.addView(console, LinearLayout.LayoutParams(-1, dp(225)))
        work.addView(splitHost, LinearLayout.LayoutParams(-1, 0, 1f))

        work.addView(keyboardToolbar, LinearLayout.LayoutParams(-1, dp(DesignTokens.TOUCH)))
        statusBarComponent = StatusBar(this, palette)
        statusBarComponent.setText("就绪")
        work.addView(statusBarComponent, LinearLayout.LayoutParams(-1, -2))

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
                updateImeLayout(WorkspaceLayoutPolicy.imeOccludesEditor(insets.isVisible(WindowInsets.Type.ime()), (insets.getInsets(WindowInsets.Type.ime()).bottom / resources.displayMetrics.density).toInt()))
                insets
            }
        }
        root.viewTreeObserver.addOnGlobalLayoutListener {
            if (Build.VERSION.SDK_INT >= 30) {
                root.rootWindowInsets?.let { updateImeLayout(WorkspaceLayoutPolicy.imeOccludesEditor(it.isVisible(WindowInsets.Type.ime()), (it.getInsets(WindowInsets.Type.ime()).bottom / resources.displayMetrics.density).toInt())) }
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
        if (currentGeometry != null) cancelWorkbenchMotion()
        currentGeometry = geometry
        updateExplorerLayout(ExplorerLayout.resolve(geometry, explorerWidth))
    }

    private fun updateExplorerLayout(layout: ExplorerLayout) {
        if (::mainToolbar.isInitialized) mainToolbar.setSidebarPresent(layout.mode == ExplorerMode.SIDEBAR)
        activityBar?.visibility=View.VISIBLE
        if (layout.mode == ExplorerMode.SIDEBAR) {
            if (explorerPane == null) {
                explorerPane = makeExplorerPane(true).also { topWorkspace.addView(it, 0, LinearLayout.LayoutParams(dp(layout.width), -1)) }
                explorerResizeHandle = ExplorerResizeHandle(this).also { handle ->
                    handle.setCallbacks(::resizeExplorer, ::finishExplorerResize) {
                        if (::contentHost.isInitialized) Animations.cancelTree(contentHost)
                        explorerDragStartWidth = ((explorerPane?.layoutParams?.width ?: dp(explorerWidth)) / resources.displayMetrics.density).toInt()
                        explorerDragViewport = capturePanelViewport(); hideCompletion()
                    }
                    handle.setOnClickListener { explorerWidth = 240; finishExplorerResize(); updateExplorerLayout(ExplorerLayout.resolve(currentGeometry!!, explorerWidth)) }
                    handle.setBackgroundColor(surface)
                    topWorkspace.addView(handle, 1, LinearLayout.LayoutParams(dp(12), -1))
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
            listOfNotNull(explorerPane, explorerResizeHandle).forEach { Animations.cancel(it); it.visibility = View.GONE }
            activityBar?.setExplorerActive(explorerDialog?.isShowing == true)
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
        val report = (editorPort as? SoraEditorPort)?.report ?: com.codepocket.ide.editor.SyntaxReport()
        val detailedState = when {
            report.issues.isNotEmpty() -> "$state · ${report.issues.size} 处问题，点击红线查看"
            report.status.isNotEmpty() -> "$state · ${report.status}"
            else -> state
        }
        statusBarComponent.setStatus(StatusBarLayout(language, line, column,
            if (settings.useSpaces) "空格 ${settings.tabWidth}" else "Tab ${settings.tabWidth}", "UTF-8", detailedState))
        if (::editorToolbar.isInitialized) editorToolbar.setState(commandContext())
    }

    private fun decorateFileLabel(view: TextView, icon: IdeIcon, fullPath: String) {
        view.setSingleLine(); view.ellipsize = android.text.TextUtils.TruncateAt.END
        val drawable = IdeIconDrawable(this, icon, muted, dp(DesignTokens.ICON)).apply { setBounds(0, 0, dp(20), dp(20)) }
        view.setCompoundDrawablesRelative(drawable, null, null, null); view.compoundDrawablePadding = dp(6)
        IdeControls.describe(view, fullPath)
    }

    private fun commandContext(): CommandContext {
        val composing = when {
            ::input.isInitialized && input.hasFocus() -> BaseInputConnection.getComposingSpanStart(input.text) >= 0
            ::editor.isInitialized && editor.hasFocus() -> editor.isComposing
            else -> false
        }
        return CommandContext(workspaceBusy, ::editor.isInitialized && loadedPath == activePath,
            ::editor.isInitialized && editor.hasFocus(), ::input.isInitialized && input.hasFocus(),
            composing, running, saving)
    }

    private fun updateMainToolbarState() {
        if (::mainToolbar.isInitialized) mainToolbar.setRunState(running, IdeCommands.enabled(IdeCommand.RUN_STOP, commandContext()))
    }

    private fun updateShellTitle() {
        if (!::mainToolbar.isInitialized) return
        mainToolbar.setTitle(workspace.title.substringBefore("（").substringBefore(" · "), "${workspace.title} / $activePath")
        pathBreadcrumb?.let { breadcrumb ->
            val display = getString(R.string.workbench_breadcrumb, workspace.title.substringBefore(" · ").substringBefore("（"), activePath.replace("/", "  ›  "))
            if (breadcrumb.text.toString() != display) breadcrumb.text = display
            val fullPath = "${workspace.title}\n$activePath"
            if (breadcrumb.contentDescription?.toString() != fullPath) IdeControls.describe(breadcrumb, fullPath)
        }
        updateMainToolbarState()
    }

    private fun showCommandMenu(anchor: View) {
        commandMenu?.dismiss()
        val popup=DialogMotion.menu(this,anchor,motionAllowed())
        WorkbenchMenuGroup.entries.forEach { group ->
            val icon=when(group){
                WorkbenchMenuGroup.FILE->IdeIcon.EXPLORER
                WorkbenchMenuGroup.EDIT->IdeIcon.RENAME
                WorkbenchMenuGroup.VIEW->IdeIcon.MAXIMIZE
                WorkbenchMenuGroup.RUN->IdeIcon.RUN
                WorkbenchMenuGroup.TERMINAL->IdeIcon.TERMINAL
                WorkbenchMenuGroup.HELP->IdeIcon.HELP
            }
            popup.menu.add(0,1000+group.ordinal,0,group.title)
            (popup as? WorkbenchMenu)?.setItemAppearance(1000+group.ordinal,icon)
        }
        popup.menu.add(1,2000,0,"设置")
        (popup as? WorkbenchMenu)?.setItemAppearance(2000,IdeIcon.SETTINGS)
        popup.setOnMenuItemClickListener { item ->
            anchor.post { if(anchor.isAttachedToWindow) {
                if(item.itemId==2000)showSettings()
                else WorkbenchMenuGroup.entries.getOrNull(item.itemId-1000)?.let { showWorkbenchMenu(anchor,it,true) }
            } };true
        }
        commandMenu=popup;popup.show()
    }

    private fun showWorkbenchMenu(anchor: View,group:WorkbenchMenuGroup) = showWorkbenchMenu(anchor,group,false)

    private fun showWorkbenchMenu(anchor: View,group:WorkbenchMenuGroup,returnToCategories:Boolean) {
        val commands=when(group) {
            WorkbenchMenuGroup.FILE->listOf(IdeCommand.WORKSPACE,IdeCommand.NEW_FILE,IdeCommand.NEW_FOLDER,
                IdeCommand.SAVE,IdeCommand.IMPORT_COPY,IdeCommand.EXPORT,IdeCommand.RELOAD,IdeCommand.RECOVER)
            WorkbenchMenuGroup.EDIT->listOf(IdeCommand.UNDO,IdeCommand.REDO,IdeCommand.FIND,IdeCommand.REPLACE,
                IdeCommand.GOTO_LINE,IdeCommand.FONT_SIZE,IdeCommand.COMPLETE,IdeCommand.SIGNATURE,IdeCommand.SNIPPET)
            WorkbenchMenuGroup.VIEW->listOf(IdeCommand.EXPLORER,IdeCommand.SEARCH,IdeCommand.PANEL,IdeCommand.THEME,IdeCommand.HIDE_KEYBOARD)
            WorkbenchMenuGroup.RUN->listOf(IdeCommand.RUN_STOP)
            WorkbenchMenuGroup.TERMINAL->listOf(IdeCommand.PANEL,IdeCommand.MAXIMIZE_TERMINAL,IdeCommand.CLOSE_TERMINAL,IdeCommand.CLEAR_TERMINAL,IdeCommand.PANEL_SIZE)
            WorkbenchMenuGroup.HELP->IdeCommands.settingsMenuItems.filter { it!=IdeCommand.THEME }
        }
        val back: (() -> Unit)? = if(returnToCategories) ({ if(!isDestroyed&&!workspaceBusy&&anchor.isAttachedToWindow)showCommandMenu(anchor) }) else null
        showContextCommands(anchor,commands,group.title,back)
    }

    private fun showContextCommands(anchor: View, commands: List<IdeCommand>) = showContextCommands(anchor,commands,null,null)

    private fun showContextCommands(anchor: View, commands: List<IdeCommand>, title:String?, back:(()->Unit)?) {
        commandMenu?.dismiss()
        commandMenu = IdeCommandMenu.show(anchor, commandContext(), commands.filter { isCpp() || it !in listOf(IdeCommand.PRESET_INPUT,IdeCommand.CLEAR_INPUT) }, ::dispatchCommand, motionAllowed(),title,back)
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
            IdeCommand.FONT_SIZE -> showCodeFontSize()
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
            IdeCommand.CLEAR_TERMINAL -> {
                if (selectedOutput == 3) shellPane?.clearScreen()
                else { outputDisplay.clear(); showOutput(selectedOutput) }
            }
            IdeCommand.MAXIMIZE_TERMINAL -> setPanelMaximized(!panelState.maximized)
            IdeCommand.CLOSE_TERMINAL -> closePanel()
            IdeCommand.PRESET_INPUT -> setPresetInputExpanded(true)
            IdeCommand.CLEAR_INPUT -> input.setText("")
            IdeCommand.PANEL_SIZE -> showPanelSizeMenu(if (panelToolbar.isShown) panelToolbar.menuAnchor else mainToolbar.menuAnchor)
            IdeCommand.HIDE_KEYBOARD -> hideKeyboard()
            IdeCommand.THEME -> toggleTheme()
            IdeCommand.SETTINGS -> showSettings()
            IdeCommand.SHORTCUTS -> showReadOnlyDetails("快捷键说明",getString(R.string.shortcuts_help))
            IdeCommand.HELP -> showReadOnlyDetails("使用说明",getString(R.string.shell_help))
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
        if (changed) { completionPopup?.dismiss(false); signaturePopup?.dismiss(false); hideSignatureHelp() }
        imeVisible = visible
        val height = (root.height / resources.displayMetrics.density).toInt()
        resolvePanelStartup((root.width / resources.displayMetrics.density).toInt(), height)
        val layout = ImeLayoutPolicy.resolve(visible, input.hasFocus() || shellPane?.hasTerminalFocus() == true, height, panelVisible, settings.collapsePanelOnIme)
        if (visible && changed) cancelWorkbenchMotion()
        if (::splitHost.isInitialized && shellPane?.hasTerminalFocus() == true) SurfaceMotion.finish(splitHost)
        suggestionScroll.visibility = if (WorkspaceLayoutPolicy.showSnippet(snippetSession != null, visible, height)) View.VISIBLE else View.GONE
        keyboardToolbar.visibility = if (visible && editor.hasFocus() && settings.symbolBar) View.VISIBLE else View.GONE
        statusBarComponent.visibility = if (layout.showDetails) View.VISIBLE else View.GONE
        // Passive path context yields space to code in short windows, regardless of orientation or floating IME.
        pathBreadcrumb?.visibility = if (height >= 360) View.VISIBLE else View.GONE
        editorToolbar.visibility = View.VISIBLE
        applyPanelLayout()
        if (changed && visible && editor.hasFocus()) editorPort?.revealCaret()
    }

    private fun resolvePanelStartup(width: Int, height: Int) {
        if (!startupPanelPending || width <= 0 || height <= 0 || imeVisible) return
        startupPanelPending = false
        panelVisible = width >= 600 && height >= 480
        panelState = panelState.copy(bottomFraction = .35f, maximized = false)
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
        val focused = focusEditor && !input.hasFocus() && shellPane?.hasTerminalFocus() != true && editor.requestFocus()
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
        startupPanelPending = false
        panelResize.cancelGesture(); hideCompletion()
        if (::splitHost.isInitialized) {
            LayoutMotion.finish(splitHost)
            SurfaceMotion.outgoing(splitHost, outputRegion, motionEnabled(), dy = dp(16))
        }
        val stamp = capturePanelViewport()
        if (input.hasFocus() || shellPane?.hasTerminalFocus() == true) { hideKeyboard(); editor.requestFocus() }
        panelVisible = false; panelState = panelState.copy(maximized = false)
        updateImeLayout(); persistPanel(); restorePanelViewport(stamp, true)
    }

    private fun togglePanel() {
        startupPanelPending = false
        if (panelVisible && panelLayout?.showPanel == true) { closePanel(); return }
        if (::splitHost.isInitialized) SurfaceMotion.finish(splitHost)
        panelVisible = true
        updateImeLayout()
        // A narrow/short window has room for one pane; explicit opening must still work.
        if (panelLayout?.showPanel != true) setPanelMaximized(true)
        else { persistPanel(); Animations.enterPanel(outputRegion, motionEnabled()) }
    }

    private fun setPanelMaximized(maximized: Boolean) {
        startupPanelPending = false
        if (workspaceBusy) return
        panelResize.cancelGesture(); hideCompletion()
        val panelWasShown = outputRegion.isShown
        if (::splitHost.isInitialized) {
            SurfaceMotion.finish(splitHost)
            LayoutMotion.begin(splitHost, motionEnabled(), outputRegion)
        }
        val stamp = capturePanelViewport()
        if (maximized) clearSnippet()
        if (maximized && !input.hasFocus() && shellPane?.hasTerminalFocus() != true) { hideKeyboard(); outputRegion.requestFocus() }
        panelVisible = true; panelState = panelState.copy(maximized = maximized)
        updateImeLayout(); persistPanel()
        if (!maximized) restorePanelViewport(stamp, true)
        if (!panelWasShown) Animations.enterPanel(outputRegion, motionEnabled())
        if (!::splitHost.isInitialized) animateDecoration(if (maximized) outputRegion else editorRegion)
    }

    private fun revealEditor() {
        if (panelState.maximized) {
            setPanelMaximized(false)
            // Explicit code navigation differs from merely restoring the terminal panel size.
            if (editorRegion.visibility == View.VISIBLE) editor.requestFocus()
        }
        if (editorRegion.visibility != View.VISIBLE) closePanel()
    }

    private fun showPanelSizeMenu(anchor: View) = showPanelSizeMenu(anchor,null)

    private fun showPanelSizeMenu(anchor: View,back:(()->Unit)?) {
        panelResize.cancelGesture(); commandMenu?.dismiss()
        val menu = DialogMotion.menu(this, anchor, motionAllowed())
        (menu as? WorkbenchMenu)?.apply{setHeading("面板尺寸","返回布局与输入",back);setItemAppearance(1,IdeIcon.RESTORE);setItemAppearance(2,IdeIcon.MAXIMIZE);setItemAppearance(3,IdeIcon.REFRESH)}
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
                        if (::splitHost.isInitialized) LayoutMotion.begin(splitHost, motionEnabled(), outputRegion)
                        applyPanelLayout(); persistPanel(); restorePanelViewport(stamp)
                    }
                }
            }
            true
        }
        commandMenu = menu; menu.show()
    }

    private fun setPresetInputExpanded(expanded: Boolean) {
        if (workspaceBusy || !::input.isInitialized || !isCpp()) return
        val stamp = capturePanelViewport(); hideCompletion()
        presetInputExpanded = expanded
        if (expanded) {
            showOutput(3)
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
        val headerWidth = (outputRegion.width / density).toInt().takeIf { it > 0 } ?: width
        val headerMinimum = panelToolbar.expandedChromeHeightDp(headerWidth)
        val expandedTerminalMinimum = headerMinimum + (shellPane?.minimumHeightDp(false) ?: 168)
        val terminalCompact = selectedOutput == 3 && PanelLayoutPolicy.terminalCompact(height,
            expandedTerminalMinimum, imeVisible && shellPane?.hasTerminalFocus() == true)
        shellPane?.setCompact(terminalCompact)
        val terminalMinimum=if(selectedOutput==3) (if(terminalCompact)48 else headerMinimum)+(shellPane?.minimumHeightDp(terminalCompact)?:168) else 0
        val spec = PanelLayoutPolicy.resolve(panelState, panelVisible, width, height,
            imeVisible, input.hasFocus() || shellPane?.hasTerminalFocus() == true, settings.collapsePanelOnIme, settings.panelPreferSide,terminalMinimum,bottomOnly=true)
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
        params(topWorkspace, if (side) 0 else -1, if (side) -1 else 0, 1f)
        params(panelResize, if (side) dp(PanelLayoutPolicy.HANDLE) else -1, if (side) -1 else dp(PanelLayoutPolicy.HANDLE))
        val full = spec.showPanel && !spec.showEditor
        params(outputRegion, if (side && !full) dp(spec.extent) else -1, if (!side && !full) dp(spec.extent) else -1)
        outputRegion.visibility = if (spec.showPanel) View.VISIBLE else View.GONE
        if (!spec.showEditor && editor.hasFocus()) outputRegion.requestFocus()
        topWorkspace.visibility = if (spec.showEditor) View.VISIBLE else View.GONE
        editorRegion.visibility = if (spec.showEditor) View.VISIBLE else View.GONE
        panelResize.visibility = if (spec.resizable) View.VISIBLE else View.GONE
        panelResize.configure(spec.dock, dp(spec.extent), dp(spec.minimum), dp(spec.maximum), dp(spec.available))
        panelResize.isEnabled = spec.resizable && !workspaceBusy
        val panelHeight = if (side || full) height else spec.extent
        val preset = WorkspaceLayoutPolicy.preset(presetInputExpanded, imeVisible, input.hasFocus(), panelHeight)
        panelInputHeading.visibility = if (preset.showCaption && selectedOutput == 3 && isCpp()) View.VISIBLE else View.GONE
        panelOutputScroll.visibility = View.VISIBLE
        panelToolbar.setCompact(terminalCompact)
        input.visibility = if (preset.showInput && selectedOutput == 3 && isCpp()) View.VISIBLE else View.GONE
        input.layoutParams?.let { old ->
            val target = dp(preset.inputHeight)
            if (old.height != target) input.layoutParams = old.apply { this.height = target }
        }
        panelToolbar.setState(panelState.maximized, presetInputExpanded)
        // Start an idle selected Shell only when its panel is actually visible; never steal IME focus.
        if (spec.showPanel && selectedOutput == 3 && ::terminalRegistry.isInitialized &&
            terminalRegistry.catalog.selected()?.value?.runtime?.state() == com.codepocket.ide.terminal.shell.ShellRuntime.State.EMPTY)
            shellPane?.ensureSession()
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
            val live = EditorViewState(editor.selectionStart, editor.selectionEnd, editor.contentScrollX, editor.contentScrollY,
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
        presetInputExpanded = isCpp() && WorkspaceLayoutPolicy.restorePreset(presetInputExpanded, view.focus, panelVisible)
        input.visibility = if (presetInputExpanded && isCpp()) View.VISIBLE else View.GONE
        if (view.focus == "terminal" && panelVisible && selectedOutput == 3 &&
            ::terminalRegistry.isInitialized && terminalRegistry.catalog.selected() != null) {
            ensureShellPane()
            if (shellPane?.restoreTerminalFocus() != true) outputRegion.requestFocus()
        } else if (view.focus == "stdin" && panelVisible && isCpp()) input.requestFocus()
        else if (panelState.maximized && panelVisible) outputRegion.requestFocus()
        else editor.requestFocus()
        updateImeLayout()
        editorPort?.navigationSnapshot()?.let { editorPort?.restoreViewport(it.copy(x = view.scrollX, y = view.scrollY)) }
    }

    private fun buildEditor(work: LinearLayout) {
        editorRegion = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        work.addView(editorRegion, LinearLayout.LayoutParams(-1, 0, 1f))
        val tabs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(surface) }
        tabsRow = EditorTabs(this, palette)
        tabs.addView(tabsRow)
        editorToolbar = EditorToolbar(this, palette, ::dispatchCommand) { showContextCommands(it,
            listOf(IdeCommand.SAVE,IdeCommand.UNDO,IdeCommand.REDO,IdeCommand.FIND)+IdeCommands.editorMenuItems) }
        val tabHeader=LinearLayout(this).apply { setBackgroundColor(surface) }
        tabHeader.addView(tabs,LinearLayout.LayoutParams(0,dp(48),1f))
        tabHeader.addView(editorToolbar,LinearLayout.LayoutParams(-2,dp(48)))
        editorRegion.addView(tabHeader,LinearLayout.LayoutParams(-1,dp(48)))
        pathBreadcrumb = label("", 11f, muted).apply {
            setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(dp(14), dp(3), dp(12), dp(3)); minimumHeight = dp(24); gravity = Gravity.CENTER_VERTICAL
        }
        editorRegion.addView(pathBreadcrumb, LinearLayout.LayoutParams(-1, -2))

        val editorBody = LinearLayout(this).apply { setBackgroundColor(editorBackground); orientation = LinearLayout.HORIZONTAL }
        numbers = label("1", 13.5f, muted).apply {
            gravity = Gravity.TOP or Gravity.END; typeface = Typeface.MONOSPACE
            setPadding(dp(3), dp(9), dp(6), 0); setLineSpacing(dp(3).toFloat(), 1f)
        }
        editorBody.addView(numbers, LinearLayout.LayoutParams(dp(42), -1))
        editorAnalysis?.close(); editorPort?.close()
        editor = WorkbenchEditor(this)
        editor.setInputRejected { if (!isDestroyed) status.setText(R.string.editor_input_limit) }
        editorPort = SoraEditorPort(editor,
            { if (loadedPath != activePath || !::sessions.isInitialized) null
                else sessions.key(activePath) to editRevision },
            { sessions.key(activePath) }, ::isCpp, { syntaxDark }, { !workspaceBusy })
        editor.setCompositionEnded {
            if (!isDestroyed && !isFinishing && hasWindowFocus()) {
                handler.removeCallbacks(refreshEditor); handler.postDelayed(refreshEditor, 120)
            }
        }
        (editorPort as SoraEditorPort).apply {
            diagnosticsChanged = { diagnosticPopup?.onDiagnosticsChanged(diagnosticAtCaret()); updateStatusBar() }
            pythonDocument = { activePath.endsWith(".py", true) }
            runtimeDiagnostics = { frame ->
                val origin = result
                val sameWorkspace = origin?.workspaceId == workspace.identity
                val sameFile = origin?.errorFile?.let { DiagnosticFiles.relative(workspace, it, origin.projectRoot) == activePath } ?: true
                com.codepocket.ide.editor.InlineRunDiagnostics.resolve(frame, origin, sameWorkspace && sameFile)
            }
        }
        val analyzer = com.codepocket.ide.editor.EditorAnalyzer(pythonSyntax = com.codepocket.ide.editor.PythonSyntaxChecker(applicationContext)::check)
        editorAnalysis = EditorAnalysisController(editorPort!!, { handler.post(it) }, analyze = analyzer::analyze)
        editorBody.addView(editor, LinearLayout.LayoutParams(0, -1, 1f))
        editorRegion.addView(editorBody, LinearLayout.LayoutParams(-1, 0, 1f))
        val suggestScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE }
        suggestionScroll = suggestScroll
        suggestionRow = LinearLayout(this).apply { setPadding(dp(5), dp(2), dp(5), dp(2)); setBackgroundColor(surface) }
        suggestScroll.addView(suggestionRow)
        editorRegion.addView(suggestScroll, LinearLayout.LayoutParams(-1, dp(DesignTokens.SNIPPET_HINT)))
        editor.setScrolled {
            diagnosticPopup?.dismiss();  completionPopup?.let { if (it.isShowing) it.reposition() }
            signaturePopup?.let { if (it.isShowing) it.reposition() }
        }
        editor.viewTreeObserver.addOnGlobalLayoutListener {
            diagnosticPopup?.onLayoutChanged()
            if (settings.wordWrap) updateNumbers()
            completionPopup?.let { if (it.isShowing) it.reposition() }
            signaturePopup?.let { if (it.isShowing) it.reposition() }
        }
        editor.setChanges { start, removed, inserted, history ->
            diagnosticPopup?.dismiss()
            if (!quietEdit) {
                snippetTextChanging = true
                if (history) clearSnippet() else trackSnippetEdit(start, removed, inserted)
                editorAnalysis?.cancel(); (editorPort as? SoraEditorPort)?.clearDiagnostics(); hideCompletion()
                if (!dirty) { dirty = true; refreshTabs() }; editRevision++
                snippetSession = snippetSession?.copy(revision = editRevision)
                snippetTextChanging = false
                updateSnippetHint()
                handler.removeCallbacks(checkpointDraft); handler.postDelayed(checkpointDraft, 200)
                updateStatusBar()
                handler.removeCallbacks(refreshEditor); handler.postDelayed(refreshEditor, 120)
                handler.removeCallbacks(autosave); if (settings.autoSave) handler.postDelayed(autosave, settings.autoSaveDelayMs.toLong())
            }
        }
        editor.setEditorFocusListener { _, focused ->
            if (focused) { lastFocus = "editor"; updateImeLayout() } else { diagnosticPopup?.dismiss(); hideCompletion() }
        }
        editor.setEditorClickListener {
            val issue = (editorPort as? SoraEditorPort)?.diagnosticAtCaret()
            if (issue != null) {
                hideCompletion(true)
                val popup = diagnosticPopup ?: DiagnosticPopup(this, editor, editorPort!!).also { diagnosticPopup = it }
                editor.finishMotion()
                if(!popup.show(issue))showDiagnosticDetails(issue)
            } else { diagnosticPopup?.dismiss(); updateSuggestions(); updateSignatureHelp() }
        }
        completionHint = label("", 11f, muted)
        suggestionRow.addView(completionHint)
        editor.setCaretChanged {
            updateStatusBar()
            if (!quietEdit) {
                diagnosticPopup?.dismiss()
                editorAnalysis?.cancel()
                if (!snippetTextChanging) handler.post { if (!quietEdit) { validSnippet(); updateSnippetHint() } }
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
            { showTerminalActions(it) }, { dispatchCommand(IdeCommand.CLEAR_TERMINAL) })
        outputRegion.addView(panelToolbar, LinearLayout.LayoutParams(-1, -2))
        outputTabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        panelOutputScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(outputTabs) }
        terminalSessionStrip = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        panelToolbar.bindNavigation(panelOutputScroll, terminalSessionStrip!!)
        panelToolbar.setCloseSessionAction(::closeCurrentShell)
        val stdinCaption = label("C++ 云端标准输入 · 随请求一起发送", 11f, muted).apply { setPadding(dp(10), dp(3), 0, dp(3)) }
        val inputHeading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        inputHeading.addView(stdinCaption, LinearLayout.LayoutParams(0, -2, 1f))
        inputHeading.addView(button("清空输入") { input.setText("") })
        panelInputHeading = inputHeading
        outputRegion.addView(inputHeading)
        input = EditText(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 12f
            setTextColor(textForeground); setHintTextColor(muted)
            gravity = Gravity.TOP or Gravity.START; hint = "C++ 云端预填输入；Python 请直接在终端输入"
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
        input.visibility = if (presetInputExpanded && isCpp()) View.VISIBLE else View.GONE
        panelInputHeading.visibility = input.visibility
        outputRegion.addView(input, LinearLayout.LayoutParams(-1, dp(64)))
        val scroll = ScrollView(this)
        consoleResultScroll = scroll
        consoleText = TerminalView(this).apply {
            textSize = 12f; setTextColor(green)
            typeface = Typeface.MONOSPACE; gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(8), dp(10), dp(8)); setTextIsSelectable(true)
        }
        scroll.addView(consoleText)
        outputRegion.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shellPane?.dispose(); shellPane = null; shellPaneId = -1L
        terminalRegistry = ShellSessionRegistry.get(applicationContext, if (workspace.external) null else workspace.root)
        return outputRegion
    }

    private fun saveExplorerViewport(outState: Bundle) {
        val paths = ArrayList<String>()
        var characters = 0
        expanded.take(512).forEach { path ->
            if (characters + path.length <= 32768) { paths.add(path); characters += path.length }
        }
        outState.putStringArrayList("expandedDirectories", paths)
        outState.putString("explorerSelected", explorerSelection)
        val visible = if (explorerDialog?.isShowing == true) overlayExplorerList else sidebarExplorerList
        outState.putInt("explorerScroll", (visible?.parent as? ScrollView)?.scrollY ?: restoredExplorerScroll)
    }

    private fun explorerUiCheckpoint(): com.codepocket.ide.ui.ExplorerUiState {
        val visible = if (explorerDialog?.isShowing == true) overlayExplorerList else sidebarExplorerList
        val scroll = (visible?.parent as? ScrollView)?.scrollY ?: restoredExplorerScroll
        return com.codepocket.ide.ui.ExplorerUiState(expanded.toSet(), explorerSelection, scroll).normalized()
    }

    private fun reconcileExplorerSelection() {
        val wanted = explorerSelection ?: activePath
        val item = runCatching { workspace.resolve(wanted) }.getOrNull()?.takeIf { it.exists() }
        explorerSelection = if (item == null) activePath else wanted
        currentDirectory = if (item?.isDirectory == true) wanted else explorerSelection!!.substringBeforeLast('/', "")
    }

    private fun expandExplorerParents(path: String) {
        expanded.add("")
        val parts = path.split('/')
        for (i in 1 until parts.size) expanded.add(parts.take(i).joinToString("/"))
    }

    private fun revealCurrentFileInTree() {
        expandExplorerParents(activePath)
        explorerSelection = activePath
        currentDirectory = activePath.substringBeforeLast('/', "")
        renderExplorer(); persistNavigation()
        listOfNotNull(sidebarExplorerList, overlayExplorerList).forEach { rows ->
            rows.post { (0 until rows.childCount).map { rows.getChildAt(it) }
                .firstOrNull { it.getTag(R.id.workbench_key) == "entry:$activePath" }?.let { item ->
                    (rows.parent as? ScrollView)?.smoothScrollTo(0, item.top)
                } }
        }
    }

    private fun showExplorerCommands(anchor: View) {
        val menu = DialogMotion.menu(this, anchor, motionAllowed())
        menu.menu.add(0, 100, 0, "定位当前文件")
        menu.menu.add(0, 101, 1, "折叠全部目录")
        menu.menu.add(1,102,2,IdeCommand.NEW_FOLDER.title).isEnabled=IdeCommands.enabled(IdeCommand.NEW_FOLDER,commandContext())
        IdeCommands.explorerMenuItems.forEachIndexed { index, command -> menu.menu.add(1, index, index + 2, command.title).isEnabled = IdeCommands.enabled(command, commandContext()) }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                100 -> revealCurrentFileInTree()
                101 -> { expanded.clear(); renderExplorer(animate = true); persistNavigation() }
                102 -> dispatchCommand(IdeCommand.NEW_FOLDER)
                else -> IdeCommands.explorerMenuItems.getOrNull(item.itemId)?.let(::dispatchCommand)
            }; true
        }; menu.show()
    }

    private fun restoreExplorerViewport(saved: Bundle?) {
        val stored = com.codepocket.ide.ui.ExplorerUiState.fromJson(if (::workspace.isInitialized) prefs.getString(prefKey("explorerUi"), null) else null)
        expanded.clear()
        val paths = saved?.getStringArrayList("expandedDirectories")
        expanded.addAll(paths?.take(512) ?: stored.expanded)
        explorerSelection = saved?.getString("explorerSelected") ?: stored.selected
        restoredExplorerScroll = saved?.getInt("explorerScroll", stored.scroll)?.coerceAtLeast(0) ?: stored.scroll
    }

    private fun makeExplorerPane(sidebar: Boolean): LinearLayout {
        val panelView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(surface) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, 0, 0) }
        row.addView(label("资源管理器", 12f, muted, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        IdeCommands.explorerToolbarItems.filter { it==IdeCommand.NEW_FILE }.forEach { command ->
            row.addView(IdeControls.icon(this, palette, if (command == IdeCommand.NEW_FILE) IdeIcon.ADD_FILE else IdeIcon.ADD_FOLDER,
                command.title) { dispatchCommand(command) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        row.addView(IdeControls.icon(this, palette, IdeIcon.MORE, "文件与工作区命令") { showExplorerCommands(row) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        if (!sidebar) row.addView(IdeControls.icon(this, palette, IdeIcon.CLOSE, "关闭资源管理器") { explorerDialog?.dismiss() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        panelView.addView(row)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val fileList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (sidebar) sidebarExplorerList = fileList else overlayExplorerList = fileList
        scroll.addView(fileList)
        if (restoredExplorerScroll > 0) {
            val desired = restoredExplorerScroll
            scroll.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(view: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                    if (r <= l || b <= t || fileList.height <= 0) return
                    scroll.removeOnLayoutChangeListener(this)
                    scroll.scrollTo(0, desired)
                }
            })
        }
        panelView.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderExplorer()
        return panelView
    }

    private fun showExplorer() {
        if (workspaceBusy) return
        if (panelState.maximized && panelVisible) setPanelMaximized(false)
        if (currentGeometry?.showExplorerSidebar == true) {
            explorerVisible = !explorerVisible
            val pane = explorerPane ?: return
            explorerResizeHandle?.visibility = if (explorerVisible) View.VISIBLE else View.GONE
            activityBar?.setExplorerActive(explorerVisible)
            SurfaceMotion.finish(contentHost)
            if (explorerVisible) Animations.slideIn(pane, -pane.width.toFloat(), enabled = motionEnabled())
            else {
                SurfaceMotion.outgoing(contentHost, pane, motionEnabled(), dx = -dp(24))
                Animations.cancelTree(pane); pane.visibility = View.GONE
            }
            prefs.edit().putBoolean("explorerVisible", explorerVisible).apply()
            return
        }
        if (explorerDialog?.isShowing == true) { explorerDialog?.dismiss(); return }
        val content = makeExplorerPane(false)
        explorerDialog = AlertDialog.Builder(this).setView(content).create().also { dialog ->
                dialog.setOnDismissListener { overlayExplorerList = null; if (explorerDialog === dialog) explorerDialog = null
                    activityBar?.setExplorerActive(currentGeometry?.showExplorerSidebar==true && explorerVisible) }
                DialogMotion.show(dialog, motionAllowed(), drawer = true)
                val available = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                dialog.window?.setLayout(minOf(dp(360), (available-dp(48)).coerceAtLeast(dp(180))),
                    topWorkspace.height.takeIf { it>dp(192) } ?: contentHost.height)
                dialog.window?.attributes=dialog.window?.attributes?.apply { x=dp(48);y=mainToolbar.height }
                activityBar?.setExplorerActive(true)
                dialog.window?.setGravity(Gravity.START or Gravity.TOP)
                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(surface))
                dialog.window?.setDimAmount(.12f)
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            }
    }

    private fun renderExplorer(animate: Boolean = false) {
        listOfNotNull(sidebarExplorerList, overlayExplorerList).forEach { renderExplorerList(it, animate) }
    }

    private fun renderExplorerList(explorerList: LinearLayout, animate: Boolean) {
        data class Entry(val file: File, val path: String, val depth: Int, val folder: Boolean, val open: Boolean)
        val entries = linkedMapOf<String, Entry>()
        fun visit(directory: File, level: Int) {
            if (level > 20) return
            workspace.children(directory).forEach { file ->
                val path = workspace.path(file)
                val folder = file.isDirectory
                val open = path in expanded
                entries["entry:$path"] = Entry(file, path, level, folder, open)
                if (folder && open) visit(file, level + 1)
            }
        }
        if ("" in expanded) visit(workspace.root, 0)
        val moving = animate && entries.size + 2 <= LayoutMotion.MAX_TREE_ITEMS && motionEnabled()
        LayoutMotion.begin(explorerList, moving)
        KeyedViews.update(explorerList, listOf("root") + entries.keys, { key ->
            when (key) {
                "root" -> ExplorerRow(this, palette)
                "hint" -> label("点击文件打开 · 长按或鼠标右键管理", 10f, muted).apply {
                    setPadding(dp(10), dp(13), dp(4), dp(10))
                }
                else -> ExplorerRow(this, palette)
            }
        }) { view, key, _ ->
            if (key == "root") {
                (view as ExplorerRow).bind("", 0, true, "" in expanded, explorerSelection == "", moving,
                    workspace.title.substringBefore(" · ").substringBefore("（"))
                view.setOnClickListener {
                    if (!workspaceBusy) {
                        explorerSelection = ""; currentDirectory = ""
                        if (!expanded.remove("")) expanded.add("")
                        persistNavigation(); renderExplorer(animate = true)
                    }
                }
                view.setMenuAction { showExplorerCommands(view) }
                view.setOnLongClickListener { showExplorerCommands(view); true }
                view.setOnGenericMotionListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) { showExplorerCommands(view); true } else false
                }
                return@update
            }
            if (key == "hint") {
                (view as TextView).text = getString(R.string.explorer_creation_hint, currentDirectory.ifBlank { "项目根目录" })
                return@update
            }
            val entry = entries[key] ?: return@update
            (view as ExplorerRow).bind(entry.path, entry.depth + 1, entry.folder, entry.open,
                entry.path == (explorerSelection ?: activePath), moving, modified = entry.path == activePath && dirty)
            view.setMenuAction { fileMenu(entry.file) }
            view.setOnClickListener {
                if (workspaceBusy) return@setOnClickListener
                explorerSelection = entry.path
                if (entry.folder) {
                    currentDirectory = entry.path
                    if (!expanded.remove(entry.path)) expanded.add(entry.path)
                    persistNavigation(); renderExplorer(animate = true)
                } else {
                    currentDirectory = entry.path.substringBeforeLast('/', ""); persistNavigation()
                    switchFile(entry.path); explorerDialog?.dismiss(); explorerDialog = null
                }
            }
            view.setOnLongClickListener { fileMenu(entry.file); true }
            view.setOnGenericMotionListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
                    fileMenu(entry.file); true
                } else false
            }

        }
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
        }.showAnimated()
    }

    private fun sizeForm(dialog:AlertDialog,maximumDp:Int=560) {
        val available=if(::root.isInitialized && root.width>0)root.width else resources.displayMetrics.widthPixels
        dialog.window?.setLayout(minOf((available-dp(32)).coerceAtLeast(dp(48)),dp(maximumDp)),WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun nameDialog(title: String, value: String = "", contextPath:String?=null, onConfirm: (String, () -> Unit) -> Unit) {
        val field = EditText(this).apply { setSingleLine(); setText(value); setSelection(text.length); setPadding(dp(18), dp(12), dp(18), dp(12)) }
        val body=WorkbenchForms.body(this)
        contextPath?.let{body.addView(WorkbenchForms.location(this,"位置",it),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(16)})}
        body.addView(WorkbenchForms.caption(this,"名称"));field.hint="输入名称";field.contentDescription="名称"
        body.addView(field,LinearLayout.LayoutParams(-1,-2))
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(WorkbenchForms.scroll(this,body)).setNegativeButton("取消", null)
            .setPositiveButton("确定", null).showAnimated()
        sizeForm(dialog,480)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            field.error = null
            try { onConfirm(field.text.toString()) { dialog.dismiss() } }
            catch (e: Exception) { field.error = e.message ?: "操作失败" }
        }
    }

    private fun askCreate(directory: Boolean) {
        if (!allowFileOperation()) return
        val parent = runCatching { workspace.resolve(currentDirectory) }.getOrDefault(workspace.root)
            .let { if (it.isDirectory) it else workspace.root }
        nameDialog(if (directory) "新建文件夹" else "新建代码文件",
            if (directory) "" else "untitled.${if (isCpp()) "cpp" else "py"}",workspace.path(parent).ifBlank { "项目根目录" }) { name, dismiss ->
            Workspace.validateName(name)
            saveActive {
                ioAction("正在创建…", { workspace.create(parent, name, directory) }) { item ->
                    if (directory) {
                        expanded.add(workspace.path(item)); currentDirectory = workspace.path(item); persistNavigation()
                    }
                    else switchFile(workspace.path(item))
                    expandExplorerParents(workspace.path(item)); expanded.add(workspace.path(parent)); explorerSelection = workspace.path(item)
                    renderExplorer(); persistNavigation(); dismiss()
                }
            }
        }
    }

    private fun askRename(file: File) = nameDialog("重命名", file.name,workspace.path(file)) { newName, dismiss ->
        Workspace.validateName(newName)
        if (!allowFileOperation()) return@nameDialog
        saveActive {
            captureSession()
            val old = workspace.path(file)
            ioAction("正在重命名…", { workspace.rename(file, newName) }) { renamed ->
                rebasePaths(old, workspace.path(renamed)); loadedPath = activePath
                renderExplorer(); refreshTabs(); updateShellTitle(); dismiss()
            }
        }
    }

    private fun askMove(file: File) {
        if (!allowFileOperation()) return
        var destination = workspace.root
        val content = WorkbenchForms.body(this)
        val origin = WorkbenchForms.location(this,"来源",workspace.path(file),if(file.isDirectory)IdeIcon.FOLDER else IdeIconDrawable.file(file.name))
        val path = label("", 13f, textForeground, true)
        val directories = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(directories) }
        content.addView(origin); content.addView(path, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(scroll, LinearLayout.LayoutParams(-1, dp(if(root.height/resources.displayMetrics.density<480)96 else 240)))
        val dialog = AlertDialog.Builder(this).setTitle("移动到文件夹").setView(content)
            .setNegativeButton("取消", null).setPositiveButton("移动到此处", null).showAnimated()
        sizeForm(dialog)
        fun render() {
            path.text = getString(R.string.move_target_path, workspace.path(destination).ifBlank { "项目根目录" })
            directories.removeAllViews()
            if (destination != workspace.root) directories.addView(WorkbenchStyle.menuRow(this, "返回上一级").apply {
                setOnClickListener { if (!workspaceBusy) { destination = destination.parentFile!!; render() } }
            })
            val folders = workspace.children(destination).filter { it.isDirectory &&
                !(file.isDirectory && (it == file || it.path.startsWith(file.path + File.separator))) }
            folders.forEach { directory -> directories.addView(WorkbenchStyle.menuRow(this, directory.name + "/").apply {
                setOnClickListener { if (!workspaceBusy) { destination = directory; render() } }
            }) }
            if (folders.isEmpty()) directories.addView(label("此目录没有可选子文件夹", 12f, muted).apply { setPadding(dp(12), dp(12), 0, dp(12)) })
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = destination != file.parentFile
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (allowFileOperation()) {
                val target = destination
                saveActive {
                    captureSession(); val old = workspace.path(file)
                    ioAction("正在移动…", { workspace.move(file, target) }) { moved ->
                        rebasePaths(old, workspace.path(moved)); loadedPath = activePath
                        expandExplorerParents(workspace.path(moved)); expanded.add(workspace.path(target)); explorerSelection = workspace.path(moved)
                        renderExplorer(); refreshTabs(); persistNavigation(); dialog.dismiss()
                    }
                }
            }
        }
        render()
    }

    private fun askDelete(file: File) {
        if (!allowFileOperation()) return
        val body=WorkbenchForms.body(this)
        body.addView(WorkbenchForms.location(this,"删除项目",workspace.path(file),if(file.isDirectory)IdeIcon.FOLDER else IdeIconDrawable.file(file.name)))
        body.addView(label("将永久删除${if(file.isDirectory)"此目录及其全部子文件" else "此文件"}，此操作不可撤销。",12f,palette.error).apply{tag=SurfaceTextTone.DANGER;setPadding(0,dp(16),0,dp(8))})
        val dialog=AlertDialog.Builder(this).setTitle("确认删除？")
            .setView(WorkbenchForms.scroll(this,body))
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
                        if (explorerSelection == path || explorerSelection?.startsWith("$path/") == true) explorerSelection = currentDirectory
                        renderExplorer(); refreshTabs()
                    }
                }
            }.showAnimated()
        sizeForm(dialog,480)
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
        explorerSelection = explorerSelection?.let(::rebase)
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
        if (relative == activePath && loadedPath == activePath) {
            (tabsRow as? EditorTabs)?.revealSelection()
            after?.invoke(); return true
        }
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
                        refreshTabs(); renderExplorer(); after?.invoke()
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

    private fun refreshExplorerModifiedState() {
        val modifiedKey = if (dirty) "entry:$activePath" else null
        listOfNotNull(sidebarExplorerList, overlayExplorerList).forEach { rows ->
            for (index in 0 until rows.childCount) (rows.getChildAt(index) as? ExplorerRow)?.let { row ->
                row.setModified(modifiedKey != null && row.getTag(R.id.workbench_key) == modifiedKey)
            }
        }
    }

    private fun refreshTabs() {
        if (!::tabsRow.isInitialized) return
        updateShellTitle()
        refreshExplorerModifiedState()
        opened.toList().forEach { if (!workspace.resolve(it).isFile) opened.remove(it) }
        val existing = tabsRow.childCount > 0
        KeyedViews.update(tabsRow, opened.toList(), { path ->
            EditorTab(this, palette, path, { switchFile(path) }, { closeTab(path) }, { alert(path) })
        }) { view, path, added ->
            (view as EditorTab).bind(path == activePath, path == activePath && dirty)
            if (added && existing && motionEnabled()) Animations.fadeIn(view, 120)
        }
        (tabsRow as? EditorTabs)?.select((0 until tabsRow.childCount).map { tabsRow.getChildAt(it) }
            .firstOrNull { (it as? EditorTab)?.path == activePath }, motionEnabled())
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
            var checkpointed = false
            val saved = runCatching {
                drafts.put(key, path, text, base)
                checkpointed = true
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
                    settingsApplyFailure?.invoke(); settingsApplyFailure = null
                    val reason = if (text.length > Workspace.MAX_CHARS) "内容超过20万字符，请缩减或复制当前代码" else error.message
                    status.text = if (checkpointed) "保存失败，编辑内容和恢复草稿仍保留：$reason"
                        else "保存失败，当前代码仍在编辑器中；本次恢复草稿未写入：$reason"
                    if (next != null) alert("$reason\n已阻止切换/关闭/运行。可导出当前草稿，或重新授权后重试。")
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
        // Sora renders its own wrapped-line gutter. The old TextView remains hidden for layout compatibility.
        if (::numbers.isInitialized) numbers.visibility = View.GONE
    }

    private fun completionRequest() = CompletionRequest(sessions.key(activePath), editRevision,
        editor.text.toString(), editor.selectionStart, editor.selectionEnd, isCpp())

    private fun cancelAssistLayout() {
        assistLayoutTask?.let { handler.removeCallbacks(it) }
        assistLayoutTask = null
    }

    /** Small, bounded readiness checks; do not copy/parse text or request editor/IME focus here. */
    private fun waitForAssistLayout(signature: Boolean, explicit: Boolean): Boolean {
        if (editor.isLayoutReady) return false
        hideCompletion()
        val owner = editor
        val port = editorPort ?: return true
        val source = port.navigationSnapshot() ?: return true
        val deadline = android.os.SystemClock.uptimeMillis() + 2000
        val task = object : Runnable {
            override fun run() {
                if (assistLayoutTask !== this) return
                if (isDestroyed || isFinishing || !hasWindowFocus() || workspaceBusy ||
                    editor !== owner || editorPort !== port || loadedPath != activePath ||
                    !owner.hasFocus() || !owner.isShown || !owner.isEnabled || owner.isComposing ||
                    toolDialog?.isShowing == true || explorerDialog?.isShowing == true ||
                    !source.matches(port.navigationSnapshot(), false)) {
                    cancelAssistLayout(); return
                }
                if (owner.isLayoutReady) {
                    assistLayoutTask = null
                    if (signature) updateSignatureHelp(explicit) else {
                        updateSuggestions(explicit)
                        if (!explicit) updateSignatureHelp()
                    }
                } else if (android.os.SystemClock.uptimeMillis() < deadline) {
                    handler.postDelayed(this, 32)
                } else {
                    assistLayoutTask = null
                    if (explicit) status.setText(R.string.assist_layout_pending)
                }
            }
        }
        assistLayoutTask = task
        handler.postDelayed(task, 32)
        return true
    }

    private fun hideCompletion(suppress: Boolean = false) {
        if (suppress) diagnosticPopup?.dismiss()
        hideSignatureHelp(suppress)
        if (suppress && ::sessions.isInitialized && ::editor.isInitialized) completionSuppressed = completionRequest()
        completionGeneration++; completionTask?.cancel(true); completionTask = null
        completionBatch = null
        completionPopup?.dismiss(animated = suppress && motionEnabled())
    }

    private fun updateSuggestions(force: Boolean = false) {
        if (!force && assistLayoutTask != null) return
        if (!::editor.isInitialized || !::sessions.isInitialized) return
        if (diagnosticPopup?.isShowing == true) { if (!force) return else diagnosticPopup?.dismiss() }
        if (isFinishing || !hasWindowFocus() || workspaceBusy || loadedPath != activePath || !editor.hasFocus() || !editor.isShown ||
            toolDialog?.isShowing == true || explorerDialog?.isShowing == true ||
            editor.isComposing || editor.selectionStart != editor.selectionEnd ||
            (!force && (snippetSession != null || !settings.autoCompletion || editor.length() > 45000))) { hideCompletion(); return }
        if (waitForAssistLayout(signature = false, explicit = force)) return
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
                    editor.isComposing ||
                    toolDialog?.isShowing == true || explorerDialog?.isShowing == true) return@runOnUiThread
                if (waitForAssistLayout(signature = false, explicit = force)) return@runOnUiThread
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
                editor.finishMotion() // Popup and drawn caret must agree on the real insertion point immediately.
                if (!popup.show(batch, motionEnabled())) {
                    hideCompletion()
                    if (force) status.setText(R.string.completion_no_space)
                }
            }
        }
    }

    private fun hideSignatureHelp(suppress: Boolean = false) {
        cancelAssistLayout()
        if (suppress && ::sessions.isInitialized && ::editor.isInitialized) signatureSuppressed = completionRequest()
        signatureGeneration++; signatureTask?.cancel(true); signatureTask = null
        signaturePopup?.dismiss(animated = suppress && motionEnabled())
    }

    private fun updateSignatureHelp(force: Boolean = false) {
        if (!force && assistLayoutTask != null) return
        if (diagnosticPopup?.isShowing == true) { if (!force) return else diagnosticPopup?.dismiss() }
        if (!::editor.isInitialized || !::sessions.isInitialized) return
        if (isFinishing || isDestroyed || !hasWindowFocus() || workspaceBusy || loadedPath != activePath ||
            !editor.hasFocus() || !editor.isShown || editor.length() > 45000 ||
            editor.isComposing || editor.selectionStart != editor.selectionEnd ||
            toolDialog?.isShowing == true || explorerDialog?.isShowing == true ||
            completionPopup?.isShowing == true || (!force && !settings.signatureHelp)) { hideSignatureHelp(); return }
        if (waitForAssistLayout(signature = true, explicit = force)) return
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
                    editor.isComposing ||
                    toolDialog?.isShowing == true || explorerDialog?.isShowing == true || completionPopup?.isShowing == true) return@runOnUiThread
                if (waitForAssistLayout(signature = true, explicit = force)) return@runOnUiThread
                if (found == null || !found.matches(completionRequest())) {
                    if (force) status.setText(R.string.signature_empty)
                    return@runOnUiThread
                }
                val popup = signaturePopup ?: SignaturePopup(this, editor, editorPort ?: return@runOnUiThread, surface, textForeground, muted, accent,
                    { hideSignatureHelp(true) }).also { signaturePopup = it }
                editor.finishMotion()
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
            editor.isComposing) return false
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
        if (workspaceBusy || !editor.hasFocus() || editor.isComposing || !validSnippet()) return false
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
            editor.isComposing) return
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
                    !editor.isComposing) {
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
            }.setNegativeButton(R.string.snippet_cancel, null).showAnimated()
    }

    private fun insertText(snippet: String, caretInside: Boolean = false) {
        if (workspaceBusy || !editor.hasFocus() || editor.isComposing) return
        val source = editorPort?.snapshot()?.buffer ?: return
        val start = minOf(source.start, source.end)
        val end = maxOf(source.start, source.end)
        val caret = start + snippet.length - if (caretInside) 1 else 0
        if (finishEditorChange(applyChange(TextChange(start, end, snippet, caret), keepSnippet = true, source = source)))
            editor.requestFocus()
    }

    /** Shift+Tab removes one indent. Tab indents selection or accepts a suggestion. */
    private fun indent(reverse: Boolean) {
        if (workspaceBusy || !editor.hasFocus() || editor.isComposing) return
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

    /** Used only for explicit edits. Sora typing/history have their own ContentChangeEvent revision path. */
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
        if (!editor.hasFocus() || editor.isComposing) return
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
        val restored = pendingSettingsState; pendingSettingsState = null
        toolDialog = SettingsDialog.show(this, settings, { applySettings(it) }, restored) { next, done -> applySettings(next, done) }
    }

    private fun applySettings(next: AppSettings, onApplied: (Boolean) -> Unit = {}) {
        if (workspaceBusy || isDestroyed) { onApplied(false); return }
        val themeChanged = next.isDark(isSystemDark()) != dark
        if (themeChanged && (dirty || saving)) {
            if (loadedPath != activePath) { onApplied(false); return }
            settingsApplyFailure = { onApplied(false) }
            saveActive { settingsApplyFailure = null; applySettings(next, onApplied) }; return
        }
        hideCompletion()
        captureSession(); persistSessions()
        if (themeChanged && ::root.isInitialized) {
            themeSnapshot?.release()
            themeSnapshot = SurfaceMotion.capture(root, motionEnabled() && next.animations)
        }
        settings = next.normalized()
        dark = settings.isDark(isSystemDark())
        prefs.edit().putString("settings", settings.toJson()).putBoolean("dark", dark).apply()
        if (themeChanged) { onApplied(true); persist(); recreate() }
        else { applyEditorPreferences(); restoreSession(); status.text = "设置已保存"; onApplied(true) }
    }

    private fun showCodeFontSize() {
        hideCompletion(true)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val value = label(getString(R.string.editor_font_value, settings.fontSize), 22f, textForeground).apply { gravity = Gravity.CENTER }
        val minus = IdeControls.key(this, palette, "A−", getString(R.string.editor_font_smaller)) {}
        val plus = IdeControls.key(this, palette, "A+", getString(R.string.editor_font_larger)) {}
        val preview=TextView(this).apply{
            text="Aa 0123 你好";typeface=Typeface.MONOSPACE;gravity=Gravity.CENTER
            setPadding(dp(16),dp(18),dp(16),dp(18));setBackgroundColor(editorBackground)
            contentDescription="代码字号预览"
        }
        fun refresh() {
            preview.textSize=settings.fontSize.toFloat()
            value.text = getString(R.string.editor_font_value, settings.fontSize)
            minus.isEnabled = settings.fontSize > 10; plus.isEnabled = settings.fontSize < 28
            listOf(minus, plus).forEach { it.alpha = if (it.isEnabled) 1f else .4f }
        }
        minus.setOnClickListener { zoomEditor(-1, requireEditorFocus = false); refresh() }
        plus.setOnClickListener { zoomEditor(1, requireEditorFocus = false); refresh() }
        controls.addView(minus, LinearLayout.LayoutParams(dp(56), dp(48)))
        controls.addView(value, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(plus, LinearLayout.LayoutParams(dp(56), dp(48)))
        toolDialog?.dismiss()
        val body=WorkbenchForms.body(this)
        body.addView(preview,LinearLayout.LayoutParams(-1,-2));body.addView(controls)
        body.addView(WorkbenchForms.caption(this,getString(R.string.editor_font_touch_help)))
        toolDialog = AlertDialog.Builder(this).setTitle(R.string.editor_font_title)
            .setView(WorkbenchForms.scroll(this,body)).setPositiveButton("完成", null).showAnimated()
        sizeForm(toolDialog!!,400);refresh()
    }

    /** Change only font metrics: retain Editable, undo, document revision, focus and terminal sessions. */
    private fun zoomEditor(delta: Int, requireEditorFocus: Boolean = true) {
        if (workspaceBusy || loadedPath != activePath || (requireEditorFocus && !editor.hasFocus()) ||
            editor.isComposing) return
        val size = (settings.fontSize + delta).coerceIn(10, 28)
        if (size == settings.fontSize) { status.text = getString(R.string.editor_font_limit, size); return }
        hideCompletion(true)
        editorPort?.cancelNavigation()
        editor.finishMotion()
        settings = settings.copy(fontSize = size)
        prefs.edit().putString("settings", settings.toJson()).apply()
        editor.textSize = size.toFloat(); numbers.textSize = size.toFloat()
        updateTabStops(); updateNumbers()
        // Layout is asynchronous; the existing cancellable pre-draw queue reveals the real caret.
        if (editor.hasFocus()) editorPort?.revealCaret()
        status.text = getString(R.string.editor_font_size, size)
    }

    private fun applyEditorPreferences() {
        editor.textSize = settings.fontSize.toFloat(); numbers.textSize = settings.fontSize.toFloat()
        consoleText.textSize = settings.terminalFontSize.toFloat()
        if (::terminalRegistry.isInitialized) terminalRegistry.catalog.entries().forEach {
            com.codepocket.ide.terminal.shell.TerminalAppearance.apply(it.value.runtime.session(), settings.terminalTheme.name, dark)
        }
        shellPane?.setAppearance(settings.terminalTheme.name, dark)
        shellPane?.setPreferences(settings.terminalFontSize, motionForeground && motionAllowed() && settings.cursorBlink)
        refreshMotionPolicy()
        numbers.visibility = View.GONE
        editor.setLineNumbers(settings.lineNumbers)
        editor.setHorizontallyScrolling(!settings.wordWrap)
        (editor.parent as? View)?.setBackgroundColor(codeBackground)
        editor.setTextColor(codeForeground); numbers.setTextColor(if (syntaxDark) 0xFF9DA3AD.toInt() else 0xFF636A74.toInt())
        if (::sessions.isInitialized && loadedPath == activePath && settings.wordWrap) {
            val file = sessions.file(activePath); file.view = file.view.copy(scrollX = 0)
        }
        if (!motionEnabled()) cancelWorkbenchMotion()
        currentGeometry?.let { updateExplorerLayout(ExplorerLayout.resolve(it, explorerWidth)) }
        updateImeLayout(); updateNumbers(); refreshTabs()
        handler.removeCallbacks(autosave)
        if (dirty && settings.autoSave) handler.postDelayed(autosave, settings.autoSaveDelayMs.toLong())
        handler.removeCallbacks(refreshEditor); handler.post(refreshEditor)
    }

    private fun updateTabStops() {
        editor.configureIndent(settings.tabWidth, settings.useSpaces, settings.autoIndent, settings.autoPairs)
        editor.configureLanguage(isCpp(), activePath.endsWith(".py", true), syntaxDark)
    }

    private fun cancelWorkbenchMotion() {
        if (::root.isInitialized) { Animations.cancelTree(root); return }
        // Unmeasured command fixtures/early startup can have individual panes before a root is published.
        listOfNotNull(explorerPane, activityBar, sidebarExplorerList, overlayExplorerList,
            if (::tabsRow.isInitialized) tabsRow else null,
            if (::editorRegion.isInitialized) editorRegion else null,
            if (::outputRegion.isInitialized) outputRegion else null).forEach(Animations::cancelTree)
    }

    private fun refreshMotionPolicy() {
        if (isDestroyed) return
        val allowed = motionForeground && motionAllowed()
        if (!allowed) cancelWorkbenchMotion()
        if (::editor.isInitialized) editor.configureMotion(allowed, settings.cursorMovement, settings.cursorBlink,
            if (syntaxDark) 0xFFAECDFF.toInt() else 0xFF005FB8.toInt())
        shellPane?.setAnimationsEnabled(allowed && settings.cursorBlink)
    }

    private fun animateDecoration(view: View?) {
        if (view != null) Animations.fadeIn(view, 120, enabled = motionEnabled())
    }

    private fun motionAllowed() = MotionEnvironment.enabled(this, settings.animations)
    private fun motionEnabled() = !imeVisible && motionAllowed()

    private fun AlertDialog.Builder.showAnimated(): AlertDialog = DialogMotion.show(create(), motionAllowed())

    private fun searchSnapshot() = EditorSnapshot(sessions.key(activePath), editRevision,
        editor.text.toString(), editor.selectionStart.coerceAtLeast(0), editor.selectionEnd.coerceAtLeast(0))

    private fun showProjectSearch() {
        if (workspaceBusy || loadedPath != activePath) return
        projectSearch?.cancel(); toolDialog?.dismiss(); hideCompletion(true); clearSnippet()
        val targetWorkspace = workspace
        val layout = WorkbenchForms.body(this)
        val query = EditText(this).apply {
            setSingleLine(); hint = "在项目中查找";contentDescription="项目查找文本"
            filters = arrayOf(InputFilter.LengthFilter(256))
        }
        val case = android.widget.CheckBox(this).apply { text = "区分大小写" }
        val message = TextView(this).apply { textSize=12f;setTextColor(muted);tag=SurfaceTextTone.MUTED;setPadding(0,dp(8),0,dp(8));visibility=View.GONE }
        val list = android.widget.ListView(this).apply{visibility=View.GONE}
        val search = android.widget.Button(this).apply { text = "搜索" }
        val searchRow=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        searchRow.addView(query,LinearLayout.LayoutParams(0,dp(48),1f));searchRow.addView(search,LinearLayout.LayoutParams(-2,dp(48)).apply{marginStart=dp(8)})
        layout.addView(searchRow);layout.addView(case);layout.addView(message)
        list.divider=android.graphics.drawable.ColorDrawable(palette.border);list.dividerHeight=dp(1)
        layout.addView(list,LinearLayout.LayoutParams(-1,dp(if(root.height/resources.displayMetrics.density<480)96 else 240)))
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
            hits = emptyList(); list.adapter = null;list.visibility=View.VISIBLE;message.visibility=View.VISIBLE;message.text = "正在搜索…"
            WorkspaceIo.executor.execute {
                val response = runCatching { engine.search(needle, caseSensitive, 200, buffers) }
                runOnUiThread {
                    if (isDestroyed || !dialog.isShowing || toolDialog !== dialog || workspace !== targetWorkspace || generation != request) return@runOnUiThread
                    response.onSuccess { found ->
                        hits = found
                        list.adapter = object:android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                            found.map { "${it.path}:${it.lineNumber}\n${it.lineContent.take(160)}" }) {
                            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                                val hit=found[position]
                                return WorkbenchForms.searchResult(this@MainActivity,hit.path,hit.lineNumber,hit.lineContent)
                            }
                        }
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
        toolDialog = dialog; DialogMotion.show(dialog, motionAllowed());sizeForm(dialog,760)
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
                    .setNegativeButton("取消", null).showAnimated()
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
        }, animations = motionAllowed(), displayName = activePath)
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
        toolDialog?.dismiss(); toolDialog = dialog; DialogMotion.show(dialog, motionAllowed());sizeForm(dialog,760)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN&&completionPopup?.isShowing==true) {
            val tab=completionTabButton
            val location=IntArray(2)
            val touchesTab=tab!=null&&tab.isShown&&run {
                tab.getLocationOnScreen(location)
                event.rawX>=location[0]&&event.rawX<location[0]+tab.width&&
                    event.rawY>=location[1]&&event.rawY<location[1]+tab.height
            }
            if(!touchesTab)hideCompletion()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (shellPane?.hasTerminalFocus() == true) {
            if(event.action==KeyEvent.ACTION_DOWN && toolDialog?.isShowing!=true && explorerDialog?.isShowing!=true) {
                val command=TerminalIdeKeys.command(event.keyCode,event.isCtrlPressed,event.isShiftPressed,event.isAltPressed,event.isMetaPressed)
                if(command!=null) {
                    if(event.repeatCount==0 && (command!=IdeCommand.RUN_STOP || !running))dispatchCommand(command)
                    return true
                }
            }
            return super.dispatchKeyEvent(event) // Ctrl+C/D, Tab, arrows, text and IME go to the real PTY.
        }
        if (!::editor.isInitialized || toolDialog?.isShowing == true || explorerDialog?.isShowing == true)
            return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP && completionConsumedKeys.remove(event.keyCode)) return true
        if (event.action == KeyEvent.ACTION_DOWN && editor.hasFocus()) {
            val zoom = EditorFontKeys.delta(event.keyCode, event.isCtrlPressed, event.isAltPressed,
                event.isMetaPressed, event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0)
            if (zoom != null) {
                zoomEditor(zoom); completionConsumedKeys.add(event.keyCode); return true
            }
            val key = when (event.keyCode) {
                KeyEvent.KEYCODE_TAB -> CompletionKey.TAB
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> CompletionKey.ENTER
                KeyEvent.KEYCODE_DPAD_UP -> CompletionKey.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> CompletionKey.DOWN
                KeyEvent.KEYCODE_ESCAPE -> CompletionKey.ESC
                else -> CompletionKey.OTHER
            }
            val composing = editor.isComposing
            if (assistLayoutTask != null && key == CompletionKey.ESC && !composing &&
                !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && !event.isMetaPressed &&
                event.flags and KeyEvent.FLAG_SOFT_KEYBOARD == 0) {
                hideCompletion(true); completionConsumedKeys.add(event.keyCode); return true
            }
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
            }.showAnimated()
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
            val picker=AlertDialog.Builder(this).setCustomTitle(WorkbenchForms.header(this,"导出恢复草稿","${entries.size} 个草稿 · 选择后导出",IdeIcon.EXPORT))
                .setItems(entries.mapIndexed { index, draft -> "${index + 1}. ${draft.path}" }.toTypedArray()) { _, which ->
                    val draft = entries[which]
                    exportContent = draft.text; exportPath = draft.path
                    ioAction("准备恢复导出…", { File(filesDir, "pending-export.txt").writeText(draft.text) }) {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, draft.path.substringAfterLast('/'))
                        }, 202)
                    }
                }.showAnimated()
            sizeForm(picker)
        }
    }

    private fun workspaceMenu() {
        if (!allowFileOperation()) return
        val recent = runCatching { JSONArray(prefs.getString("recentTrees", "[]")) }.getOrDefault(JSONArray())
        val names = mutableListOf("选择手机目录…", "内部项目（卸载会清除）", "重新载入当前目录")
        for (i in 0 until recent.length()) names.add("最近：" + recent.getJSONObject(i).optString("name"))
        val picker=AlertDialog.Builder(this).setCustomTitle(WorkbenchForms.header(this,"工作区",workspace.title,IdeIcon.FOLDER_OPEN)).setItems(names.toTypedArray()) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this).setTitle("选择真实项目目录")
                        .setMessage("编辑器保存会写回所选目录；权限只限该目录。外部文件卸载后仍由提供方保留。Python 使用私有运行镜像，运行代码产生的文件不会自动同步回外部目录。")
                        .setPositiveButton("选择目录") { _, _ -> selectDirectory() }.setNegativeButton("取消", null).showAnimated()
                    1 -> requestWorkspace(null)
                    2 -> requestWorkspace(selectedTree)
                    else -> requestWorkspace(recent.getJSONObject(which - 3).getString("uri"))
                }
        }.showAnimated()
        sizeForm(picker)
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
            }.setNegativeButton("取消", null).showAnimated()
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
                }.showAnimated()
            return
        }
        val runningFile = workspace.resolve(activePath).absolutePath
        startupPanelPending = false
        // Run in the selected real terminal. Background messages never change the selected session.
        if (terminalRegistry.catalog.selected() == null) {
            try { terminalRegistry.create(if (workspace.external) null else workspace.root) }
            catch (failure: IllegalStateException) { alert(failure.message ?: "无法创建终端"); return }
        }
        showOutput(3)
        shellPane?.ensureSession()
        val owner = terminalRegistry.catalog.selected()!!.value.runtime
        val interactive = true
        fun launch(entry: String, root: String, key: String?) {
            if (!RunSession.start(applicationContext,code,stdin,cpp,runningFile,root,workspace.identity,
                entry,key,owner,interactive)) alert("当前终端正在执行命令。请先停止它或新建终端后运行。")
        }
        // A remembered-open panel can still be temporarily GONE under the editor IME.
        // Explicit Run must reveal it too; hiding the IME does not focus/show the terminal keyboard.
        hideKeyboard()
        if (panelLayout?.showPanel != true) togglePanel() else updateImeLayout()
        val projectRoot = workspace.root.absolutePath
        if (!cpp && workspace.external) {
            val path = activePath
            ioAction("正在同步 Python 源码与运行资源…", {
                val snapshot = workspace.preparePythonRun()
                val entry = File(snapshot, path)
                entry.writeText(code)
                snapshot to entry
            }) { (snapshot, entry) ->
                launch(entry.absolutePath,snapshot.absolutePath,sessions.key(path))
            }
        } else launch(runningFile,projectRoot,sessions.key(activePath))
    }

    private fun renderRunState() {
        if (!::mainToolbar.isInitialized) return
        updateMainToolbarState()
        outputDisplay.synchronize(RunSession.result, running)
        result = RunSession.result
        editorAnalysis?.request()
        showOutput(selectedOutput)
        if (running) status.text = if (RunSession.waitingForInput) "等待终端输入 · Enter 提交 / Ctrl+D 结束输入" else "正在执行，请稍候…"
        else result?.let { response ->
            status.text = "${if (response.successful) "✓" else "✕"} ${response.kind.label} (${response.elapsedMs}ms)"
        }
    }

    private fun ensureShellPane() {
        val selected = terminalRegistry.catalog.selected() ?: return
        if (shellPaneId == selected.id && shellPane != null) return
        val hadTerminalFocus = shellPane?.hasTerminalFocus() == true
        shellPane?.let { it.dispose(); outputRegion.removeView(it) }
        shellPaneId = selected.id
        com.codepocket.ide.terminal.shell.TerminalAppearance.apply(selected.value.runtime.session(), settings.terminalTheme.name, dark)
        shellPane = ShellTerminalPane(this, selected.value.directory, settings.terminalFontSize, motionForeground && motionAllowed() && settings.cursorBlink,
            { hideCompletion(); clearSnippet(); lastFocus = "terminal"; updateImeLayout() }, selected.value.runtime).also { pane ->
                pane.setAppearance(settings.terminalTheme.name, dark)
                pane.setOnChromeChanged { applyPanelLayout() }
                pane.setEmbedded(true)
                outputRegion.addView(pane, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        // Publish the pane first: its focus callback resolves IME layout through shellPane.
        if (hadTerminalFocus && shellPane?.restoreTerminalFocus() != true) {
            // No View yet: keep keys away from code without a delayed focus/IME request.
            hideKeyboard(); outputRegion.requestFocus()
        }
    }

    private fun showOutput(type: Int) {
        if (!::outputTabs.isInitialized || !::terminalRegistry.isInitialized) return
        // Historical output/info selections now resolve to the same real terminal, never a second task console.
        val target = if (type == 1) 1 else 3
        selectedOutput = target
        val shell = target == 3 && terminalRegistry.catalog.selected() != null
        if (shellPaneId != -1L && terminalRegistry.catalog.find(shellPaneId) == null) {
            shellPane?.let { it.dispose(); outputRegion.removeView(it) }; shellPane = null; shellPaneId = -1L
        }
        if (shell) ensureShellPane()
        consoleResultScroll.visibility = if (shell) View.GONE else View.VISIBLE
        shellPane?.visibility = if (shell) View.VISIBLE else View.GONE
        shellPane?.hostActive(shell)
        panelToolbar.setShellMode(shell)
        panelToolbar.setInputAvailable(isCpp())
        input.visibility = if (target == 3 && presetInputExpanded && isCpp()) View.VISIBLE else View.GONE
        panelInputHeading.visibility = input.visibility
        outputTabs.removeAllViews()
        outputTabs.addView(TerminalChrome.channel(this,palette,"终端",IdeIcon.TERMINAL,target!=1) {
            showOutput(3); shellPane?.ensureSession()
        })
        outputTabs.addView(TerminalChrome.channel(this,palette,"问题",IdeIcon.INFO,target==1) { showOutput(1) })
        panelToolbar.setSessionNavigationVisible(target!=1)
        panelToolbar.setCurrentSessionAvailable(terminalRegistry.catalog.selected()!=null)
        terminalSessionStrip?.apply {
            removeAllViews()
            if(target!=1) {
                val entry=terminalRegistry.catalog.selected()
                addView(TerminalChrome.session(this@MainActivity,palette,entry?.name() ?: "选择终端",
                    if(entry==null)"暂无终端，点击新建终端" else "选择终端会话，当前${entry.name()}，初始目录${entry.value.directory?.path ?: "应用终端主目录"}") {
                    showTerminalChoices(this)
                },LinearLayout.LayoutParams(0,dp(48),1f))
                addView(TerminalChrome.action(this@MainActivity,palette,IdeIcon.ADD,"新建终端，初始目录为当前项目",Runnable {newShellSession()}),LinearLayout.LayoutParams(dp(48),dp(48)))
                addView(TerminalChrome.action(this@MainActivity,palette,IdeIcon.DELETE,"关闭当前终端会话",Runnable {closeCurrentShell()}).apply {
                    isEnabled=entry!=null;alpha=if(isEnabled)1f else .35f
                },LinearLayout.LayoutParams(dp(48),dp(48)))
            }
        }
        if(target==1)outputTabs.addView(TerminalChrome.channel(this,palette,"定位",IdeIcon.LOCATE,false) {gotoError()}.apply {isEnabled=result?.errorLine!=null})
        if (shell) return
        val current = result
        consoleText.setOutput(if (target == 1) current?.stderr?.ifBlank { "没有错误诊断。" } ?: "尚无错误诊断。"
            else "没有打开的终端。点击 ＋ 新建终端，或点击运行。")
        consoleText.setTextColor(if (target == 1) red else textForeground)
        if (target == 1 && current?.errorLine != null && current.stderr.isNotEmpty()) {
            val clickable = android.text.SpannableString(consoleText.text)
            clickable.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { gotoError() } }, 0, clickable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            consoleText.text = clickable; consoleText.movementMethod = LinkMovementMethod.getInstance()
        } else consoleText.movementMethod = null
    }

    private fun openTerminal() {
        if (!::terminalRegistry.isInitialized || !::outputRegion.isInitialized) return
        startupPanelPending = false; panelVisible = true
        if (terminalRegistry.catalog.selected() == null) newShellSession()
        else { showOutput(3); shellPane?.ensureSession() }
        applyPanelLayout()
        if (shellPane?.restoreTerminalFocus() != true) outputRegion.requestFocus()
        if (::root.isInitialized) updateImeLayout()
    }

    private fun showTerminalChoices(anchor: View) {
        val menu = DialogMotion.menu(this, anchor, motionAllowed())
        val choices = terminalRegistry.catalog.entries().map { e -> e.id to e.name() }
        choices.forEachIndexed { index, choice -> menu.menu.add(0, index, index, choice.second).apply {
            isCheckable = true; isChecked = choice.first == terminalRegistry.catalog.selectedId()
        } }
        menu.setOnMenuItemClickListener { item ->
            val id = choices.getOrNull(item.itemId)?.first
            if (id != null && terminalRegistry.catalog.select(id)) {
                showOutput(3)
                shellPane?.ensureSession()
                updateImeLayout()
            }
            true
        }; menu.show()
    }

    private fun newShellSession() {
        if (workspaceBusy) return
        try {
            terminalRegistry.create(if (workspace.external) null else workspace.root)
            showOutput(3); shellPane?.ensureSession(); updateImeLayout()
        } catch (failure: IllegalStateException) { alert(failure.message ?: "无法新建终端") }
    }

    private fun showTerminalPalette(anchor: View): android.widget.PopupMenu {
        val menu = DialogMotion.menu(this, anchor, motionAllowed())
        val themes = TerminalTheme.values()
        val names = listOf("跟随界面配色", "CodePocket Dark 配色", "Nord 配色", "Dracula 配色", "Light 配色")
        themes.forEachIndexed { index, theme -> menu.menu.add(0, index, index, names[index]).apply {
            isCheckable = true; isChecked = settings.terminalTheme == theme
        } }
        menu.setOnMenuItemClickListener { item ->
            themes.getOrNull(item.itemId)?.let { applySettings(settings.copy(terminalTheme = it)) }; true
        }
        menu.show(); return menu
    }

    private enum class TerminalMenuSection(val title:String,val icon:IdeIcon) {
        ACTIONS("终端操作",IdeIcon.TERMINAL), SESSIONS("会话管理",IdeIcon.EXPLORER),
        LAYOUT("布局与输入",IdeIcon.MOVE), APPEARANCE("配色与帮助",IdeIcon.PALETTE)
    }

    private fun showTerminalActions(anchor: View) = showTerminalActionGroup(anchor,-1)

    private fun showTerminalActionGroup(anchor:View,section:Int) {
        val entry = terminalRegistry.catalog.selected()
        val menu = DialogMotion.menu(this, anchor, motionAllowed())
        if (entry == null) {
            menu.menu.add(0,1,0,"新建终端").setOnMenuItemClickListener {newShellSession();true}
            menu.menu.add(0,2,1,"收起运行面板").setOnMenuItemClickListener {togglePanel();true}
            menu.show();return
        }
        val titles = listOf("显示虚拟键盘", "复制终端记录", "粘贴", "清空终端显示", "重命名会话", "结束会话", "查看上次终端错误", "启动 / 重新启动会话", "终端说明与许可证", "终端配色",
            ) + if(isCpp())listOf("编辑 C++ 云端预填输入")else emptyList()
        if(section<0){
            TerminalMenuSection.entries.forEach { category ->
                menu.menu.add(0,100+category.ordinal,category.ordinal,category.title)
                (menu as? WorkbenchMenu)?.setItemAppearance(100+category.ordinal,category.icon)
            }
            menu.setOnMenuItemClickListener { item->
                if(terminalRegistry.catalog.selected()===entry)showTerminalActionGroup(anchor,item.itemId-100)
                true
            }
            menu.show();return
        }
        val category=TerminalMenuSection.entries.getOrNull(section)?:return
        (menu as? WorkbenchMenu)?.setHeading(category.title,"返回终端菜单") {
            if(!isDestroyed&&anchor.isAttachedToWindow&&terminalRegistry.catalog.selected()===entry)showTerminalActionGroup(anchor,-1)
        }
        val ids=when(section){
            0->listOf(0,1,2,3)
            1->listOf(20,4,7,5)
            2->listOf(21,22)+(if(isCpp())listOf(10)else emptyList())
            else->listOf(9,6,8)
        }
        ids.forEachIndexed{order,id->
            val title=when(id){20->"新建终端";21->"调整面板尺寸";22->"收起运行面板";else->titles[id]}
            val group=when(id){1,2,7,10,6,8->1;3,5->2;else->0}
            menu.menu.add(group,id,order,title)
            val icon=when(id){0,10->IdeIcon.INPUT;1->IdeIcon.COPY;2->IdeIcon.PASTE;3->IdeIcon.CLEAR;4->IdeIcon.RENAME;5->IdeIcon.DELETE;6->IdeIcon.INFO;7->IdeIcon.REFRESH;8->IdeIcon.HELP;9->IdeIcon.PALETTE;20->IdeIcon.ADD;21->IdeIcon.MOVE;else->IdeIcon.DOWN}
            (menu as? WorkbenchMenu)?.setItemAppearance(id,icon,id==5)
        }
        menu.setOnMenuItemClickListener { item ->
            if (terminalRegistry.catalog.selected() === entry) when (item.itemId) {
                0 -> shellPane?.showKeyboard()
                1 -> entry.value.runtime.copyTranscript()
                2 -> entry.value.runtime.paste()
                3 -> entry.value.runtime.clearScreen()
                4 -> nameDialog("重命名终端会话", entry.name()) { name, dismiss -> terminalRegistry.catalog.rename(entry.id, name); showOutput(selectedOutput); dismiss() }
                5 -> closeCurrentShell()
                6 -> ShellTerminalPane.showFailureDetails(this, entry.value.runtime, motionAllowed())
                7 -> shellPane?.ensureSession()
                8 -> shellPane?.showAbout()
                9 -> showTerminalPalette(anchor)
                10 -> setPresetInputExpanded(true)
                20 -> newShellSession()
                21 -> showPanelSizeMenu(anchor) { if(!isDestroyed&&anchor.isAttachedToWindow&&terminalRegistry.catalog.selected()===entry)showTerminalActionGroup(anchor,2) }
                22 -> togglePanel()
            }
            true
        }; menu.show()
    }

    private fun closeCurrentShell() {
        val entry=terminalRegistry.catalog.selected() ?: return
        AlertDialog.Builder(this).setTitle("关闭 ${entry.name()}？")
            .setMessage("将结束当前 Shell 及其正在运行的程序。编辑文件和其他终端会话保留。")
            .setNegativeButton("取消",null).setPositiveButton("关闭终端") {_,_->
                terminalRegistry.end(entry.id)
                showOutput(3);updateImeLayout()
            }.showAnimated()
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

    private fun showDiagnosticDetails(issue: com.codepocket.ide.editor.InlineIssue) {
        val content=WorkbenchForms.details(this,getString(R.string.editor_diagnostic_content,issue.source,issue.message),true)
        toolDialog?.dismiss()
        toolDialog=AlertDialog.Builder(this).setTitle(R.string.editor_diagnostic_details).setView(content)
            .setNegativeButton(R.string.shell_failure_close,null).setPositiveButton(R.string.editor_copy_diagnostic){_,_->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText(issue.source,issue.message))
            }.showAnimated()
        sizeForm(toolDialog!!,760)
    }

    private fun showReadOnlyDetails(title:String,text:String) {
        toolDialog?.dismiss()
        toolDialog=AlertDialog.Builder(this).setTitle(title).setView(WorkbenchForms.details(this,text))
            .setPositiveButton("关闭",null).showAnimated()
        sizeForm(toolDialog!!,760)
    }

    private fun alert(message: String) { AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定", null).showAnimated() }
}
