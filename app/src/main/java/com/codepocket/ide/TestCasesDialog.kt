package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.codepocket.ide.animation.DialogMotion
import com.codepocket.ide.ui.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Native case list/editor. IO never owns the Activity; an open editor checkpoints its exact text. */
internal class TestCasesDialog(private val activity: Activity, val key: String, private val path: String,
    private val storage: CaseStore, private val animations: Boolean, restored: String?,
    private val run: (List<ProgramCase>) -> Unit, private val jump: (RunResult) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val palette = DesignTokens.forContext(activity)
    private fun dp(value: Int) = IdeControls.dp(activity, value)
    private var file = CaseFile()
    private var busy = false
    private var loaded = false
    private var error = ""
    private var mode = "list"
    private var editId: String? = null
    private var name = ""; private var input = ""; private var expected = ""
    private var ignoreFinalNewlines = false
    private var nameView: EditText? = null; private var inputView: EditText? = null; private var expectedView: EditText? = null
    private var ruleView: CheckBox? = null
    private var reader: AlertDialog? = null
    private var readerId: String? = null
    private var actionDialog: AlertDialog? = null
    private var scrollY = 0
    private val body = WorkbenchForms.body(activity)
    private val scroll = ScrollView(activity).apply { isFillViewport = false; addView(body) }
    private val heading = WorkbenchForms.header(activity, "测试用例", path, IdeIcon.CODE)
    private val feedback = WorkbenchForms.caption(activity, "正在载入…").apply { setPadding(dp(20), dp(4), dp(20), dp(8)) }
    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(palette.surface)
        addView(heading, LinearLayout.LayoutParams(-1, -2)); addView(feedback, LinearLayout.LayoutParams(-1, -2))
        addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    val dialog: AlertDialog = AlertDialog.Builder(activity).setView(root).setNegativeButton("关闭", null).setPositiveButton("运行全部", null).create()
    init {
        restored?.let { text -> runCatching {
            val state = JSONObject(text)
            if (state.getString("key") == key) {
                mode = state.optString("mode", "list").takeIf { it in setOf("list", "edit", "history") } ?: "list"
                editId = state.optString("editId").takeIf { it.isNotEmpty() }
                name = state.optString("name"); input = state.optString("input"); expected = state.optString("expected")
                ignoreFinalNewlines = state.optBoolean("ignore"); scrollY = state.optInt("scroll", 0).coerceAtLeast(0)
                readerId = state.optString("reader").takeIf { it.isNotEmpty() }
            }
        } }
    }
    fun show(): TestCasesDialog {
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) back()
                true
            } else false
        }
        DialogMotion.show(dialog, animations)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { back() }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!busy && loaded) when (mode) {
                "edit" -> save()
                else -> if (CaseRunSession.running) CaseRunSession.stop() else if (mode == "history") {
                    mode = "list"; scrollY = 0; render()
                } else if (file.cases.isNotEmpty()) run(file.cases.toList())
            }
        }
        // Give the shared native button row a fixed place below a scrollable custom panel.
        dialog.findViewById<ViewGroup>(android.R.id.custom)?.let { custom ->
            custom.layoutParams = custom.layoutParams.apply { height = -1 }
            (custom.parent as? View)?.let { panel ->
                panel.layoutParams = panel.layoutParams.apply {
                    if (this is LinearLayout.LayoutParams) { height = 0; weight = 1f } else height = -1
                }
                (panel.parent as? View)?.let { parent -> parent.layoutParams = parent.layoutParams.apply { height = -1 } }
            }
        }
        val host = activity.findViewById<View>(android.R.id.content)
        fun resize() {
            val width = host.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
            dialog.window?.setLayout(minOf(dp(720), (width - dp(24)).coerceAtLeast(dp(200))), -1)
        }
        val resizeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resize() }
        host.addOnLayoutChangeListener(resizeListener)
        dialog.setOnDismissListener {
            host.removeOnLayoutChangeListener(resizeListener); reader?.dismiss(); actionDialog?.dismiss()
            nameView = null; inputView = null; expectedView = null; ruleView = null
        }
        resize(); render(); load()
        return this
    }
    private fun capture() {
        nameView?.let { name = it.text.toString() }; inputView?.let { input = it.text.toString() }
        expectedView?.let { expected = it.text.toString() }; ruleView?.let { ignoreFinalNewlines = it.isChecked }
    }
    fun checkpoint(): String? {
        if (!dialog.isShowing) return null
        capture()
        return JSONObject().put("key", key).put("mode", mode).put("editId", editId ?: "")
            .put("name", name).put("input", input).put("expected", expected).put("ignore", ignoreFinalNewlines)
            .put("scroll", scroll.scrollY).put("reader", if(reader?.isShowing==true) readerId ?: "" else "").toString()
    }
    private fun <T> io(work: () -> T, done: (T) -> Unit) {
        if (busy) return
        busy = true; updateControls()
        WorkspaceIo.executor.execute {
            val value = runCatching(work)
            main.post {
                if (!dialog.isShowing) return@post
                busy = false
                value.onSuccess(done).onFailure { error = it.message ?: "用例操作失败"; refresh() }
            }
        }
    }
    private fun load() {
        io({ storage.load(key) }) { value ->
            file = value; loaded = true; error = ""; render()
            readerId?.let { id -> records().firstOrNull { it.id == id }?.let(::details) }
        }
    }
    private fun records(): List<CaseRecord> {
        val live = if (CaseRunSession.key == key) CaseRunSession.batch?.records.orEmpty().asReversed() else emptyList()
        return (live + file.history).distinctBy { it.id }.take(CaseStore.MAX_HISTORY)
    }
    private fun updateControls() {
        val active = CaseRunSession.running
        listOf(nameView,inputView,expectedView,ruleView).forEach { it?.isEnabled = !busy && !active }
        val state = CaseRunSession.batch.takeIf { CaseRunSession.key == key }
        feedback.text = when {
            error.isNotBlank() -> error
            !loaded -> "正在载入…"
            state != null -> state.message
            else -> "${file.cases.size}/20 项 · 默认逐字比较 · 最近 40 条记录"
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply { text = if (mode == "list") "关闭" else "返回"; isEnabled = !busy }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            text = when { mode == "edit" -> "保存用例"; active -> "停止批量"; mode == "history" -> "返回用例"; else -> "运行全部" }
            isEnabled = loaded && !busy && if (mode == "edit") !active else active || mode == "history" || (file.cases.isNotEmpty() && !RunSession.running)
        }
    }
    /** Called by Main's lifecycle-bound listener. Never rebuild an in-progress input form. */
    fun refresh() {
        if (!dialog.isShowing) return
        if (mode == "edit") updateControls() else { scrollY = scroll.scrollY; render() }
    }
    private fun row(title: String, detail: String, icon: IdeIcon, enabled: Boolean = true, action: () -> Unit) {
        val adapter = WorkbenchChoiceAdapter(activity, listOf(WorkbenchChoice(title, title, detail, icon)))
        val view = adapter.getView(0, null, body)
        view.isEnabled = enabled; view.alpha = if (enabled) 1f else .45f
        view.isFocusable = enabled
        if (enabled) view.setOnClickListener { action() }
        body.addView(view, LinearLayout.LayoutParams(-1, -2))
    }
    private fun render() {
        nameView = null; inputView = null; expectedView = null; ruleView = null
        body.removeAllViews()
        if (loaded) when (mode) {
            "edit" -> renderEditor()
            "history" -> {
                if (records().isEmpty()) body.addView(WorkbenchForms.caption(activity, "还没有运行记录"))
                records().forEach { record -> row(record.case.name, "${record.verdict.label} · ${record.result.elapsedMs}ms · ${stamp(record)}", IdeIcon.CODE) { recordActions(record) } }
            }
            else -> {
                row("新建用例", "输入与预期输出", IdeIcon.ADD_FILE, !busy && !CaseRunSession.running && file.cases.size < CaseStore.MAX_CASES) { edit(null) }
                row("运行记录", "${records().size} 项 · 编辑或删除用例后仍保留", IdeIcon.REFRESH) { mode = "history"; scrollY = 0; render() }
                if (file.cases.isEmpty()) body.addView(WorkbenchForms.caption(activity, "添加第一项用例，再运行单项或全部。"))
                file.cases.forEach { case ->
                    val batch = CaseRunSession.batch.takeIf { CaseRunSession.key == key }
                    val last = records().firstOrNull { it.case.id == case.id }
                    val state = when {
                        batch?.running == true && batch.current?.id == case.id -> "本批 · ${when(batch.stage){CaseBatch.Stage.PREPARING->"准备中";CaseBatch.Stage.SAVING->"保存中";else->"执行中"}}"
                        batch?.running == true && batch.queue.cases.any { it.id == case.id } && batch.records.none { it.case.id == case.id } -> "本批 · 未执行"
                        batch!=null && batch.queue.cases.any { it == case } && batch.records.none { it.case == case } -> "上批 · 未执行"
                        last == null -> "未运行"
                        !last.matches(case) -> "上次 ${last.verdict.label} · 用例已修改"
                        else -> "上次 ${last.verdict.label} · ${last.result.elapsedMs}ms"
                    }
                    row(case.name, "$state · ${case.comparison.label}", IdeIcon.CODE) { caseActions(case) }
                }
            }
        }
        updateControls()
        scroll.post { scroll.scrollTo(0, scrollY) }
    }
    private fun field(label: String, value: String, limit: Int, lines: Int): EditText {
        body.addView(WorkbenchForms.caption(activity, label).apply { setPadding(0, dp(12), 0, dp(4)) })
        return EditText(activity).apply {
            contentDescription = label; textSize = 14f; setTextColor(palette.foreground); setHintTextColor(palette.muted)
            if (lines == 1) setSingleLine() else {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                minLines = lines; maxLines = 8; typeface = Typeface.MONOSPACE; gravity = android.view.Gravity.TOP
            }
            filters = arrayOf(InputFilter.LengthFilter(limit)); setText(value); minimumHeight = dp(48)
            body.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    private fun renderEditor() {
        nameView = field("用例名称", name, 80, 1)
        inputView = field("标准输入（最多 2 万字符）", input, 20000, 3)
        expectedView = field("预期输出（最多 5 万字符）", expected, 50000, 3)
        ruleView = CheckBox(activity).apply {
            text = "仅忽略末尾换行"; textSize = 13f; setTextColor(palette.foreground); isChecked = ignoreFinalNewlines; minHeight = dp(48)
            body.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        body.addView(WorkbenchForms.caption(activity, "默认逐字比较；空格和中间换行始终参与比较。"))
    }
    private fun edit(case: ProgramCase?) {
        if (CaseRunSession.running || busy) return
        editId = case?.id; name = case?.name ?: "用例 ${file.cases.size + 1}"; input = case?.input ?: ""; expected = case?.expected ?: ""
        ignoreFinalNewlines = case?.comparison == OutputComparison.IGNORE_FINAL_NEWLINES
        mode = "edit"; scrollY = 0; error = ""; render()
    }
    private fun save() {
        capture()
        val value = ProgramCase(id = editId ?: java.util.UUID.randomUUID().toString(), name = name, input = input, expected = expected,
            comparison = if (ignoreFinalNewlines) OutputComparison.IGNORE_FINAL_NEWLINES else OutputComparison.EXACT)
        try { value.validate() } catch (failure: IllegalArgumentException) { error = failure.message ?: "请检查用例内容"; updateControls(); return }
        val values = if (file.cases.none { it.id == value.id }) file.cases + value else file.cases.map { if (it.id == value.id) value else it }
        // A rotation while the atomic write is queued must edit this same id, never create a duplicate.
        editId = value.id
        io({ storage.saveCases(key, values) }) { saved -> file = saved; mode = "list"; editId = null; error = ""; scrollY = 0; render() }
    }
    private fun back() {
        if (busy) return
        if (mode == "edit") {
            capture()
            val original = file.cases.firstOrNull { it.id == editId }
            val changed = original == null || original.name != name || original.input != input || original.expected != expected ||
                (original.comparison == OutputComparison.IGNORE_FINAL_NEWLINES) != ignoreFinalNewlines
            if (changed) {
                actionDialog = AlertDialog.Builder(activity).setTitle("放弃未保存的用例？").setMessage("当前输入尚未保存。")
                    .setNegativeButton("继续编辑", null).setPositiveButton("放弃") { _, _ -> mode = "list"; error = ""; scrollY = 0; render() }.create()
                DialogMotion.show(actionDialog!!, animations)
            } else { mode = "list"; scrollY = 0; render() }
        } else if (mode == "history") { mode = "list"; scrollY = 0; render() } else dialog.dismiss()
    }
    private fun actions(title: String, options: List<WorkbenchChoice>, chosen: (Int) -> Unit) {
        actionDialog?.dismiss()
        actionDialog = AlertDialog.Builder(activity).setTitle(title).setAdapter(WorkbenchChoiceAdapter(activity, options)) { _, index -> chosen(index) }
            .setNegativeButton("返回", null).create()
        DialogMotion.show(actionDialog!!, animations)
        actionDialog?.window?.setLayout(minOf(dp(560), (root.width - dp(16)).coerceAtLeast(dp(200))), -2)
    }
    private fun caseActions(case: ProgramCase) {
        val options = mutableListOf<WorkbenchChoice>()
        val callbacks = mutableListOf<() -> Unit>()
        fun add(label: String, icon: IdeIcon, action: () -> Unit) { options.add(WorkbenchChoice(label, label, "", icon)); callbacks.add(action) }
        if (!CaseRunSession.running && !busy) {
            add("运行此项", IdeIcon.RUN) { run(listOf(case)) }; add("编辑用例", IdeIcon.RENAME) { edit(case) }
            add("删除用例", IdeIcon.DELETE) {
                actionDialog = AlertDialog.Builder(activity).setTitle("删除“${case.name}”？").setMessage("运行记录仍会保留。")
                    .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                        io({ storage.saveCases(key, file.cases.filterNot { it.id == case.id }) }) { saved -> file = saved; refresh() }
                    }.create()
                DialogMotion.show(actionDialog!!, animations)
            }
        }
        records().firstOrNull { it.case.id == case.id }?.let { record -> add("查看上次结果", IdeIcon.CODE) { recordActions(record) } }
        if (options.isNotEmpty()) actions(case.name, options) { callbacks[it]() }
    }
    private fun recordActions(record: CaseRecord) {
        val choices = mutableListOf(WorkbenchChoice("details", "查看记录", record.verdict.label, IdeIcon.CODE))
        if (record.result.errorLine != null) choices.add(WorkbenchChoice("jump", "定位错误", "会核对运行时的源码版本", IdeIcon.LOCATE))
        actions(record.case.name, choices) { if (it == 0) details(record) else { dialog.dismiss(); jump(record.result) } }
    }
    private fun stamp(record: CaseRecord) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.time))
    private fun details(record: CaseRecord) {
        readerId = record.id
        val unsaved = CaseRunSession.batch?.takeIf { it.stage==CaseBatch.Stage.FINISHED }?.unsavedRecord?.id == record.id
        val text = buildString {
            append("${record.verdict.label} · ${record.result.elapsedMs}ms\n退出码 ${record.result.exitCode} · ${record.result.engine}\n${stamp(record)}\n${record.case.comparison.label}\n")
            if (unsaved) append("\n此结果未能保存，关闭应用后可能丢失。\n")
            if (record.outputTruncated) append("\n输出记录已截断：stdout、stderr 各最多 5 万字符。\n")
            append("\n── 标准输入 ──\n${record.case.input}\n── 预期输出 ──\n${record.case.expected}\n── 实际输出 ──\n${record.result.stdout}\n── 错误输出 ──\n${record.result.stderr}")
        }
        reader?.dismiss()
        reader = ReadOnlyDetails.show(activity, "用例记录 · ${record.case.name}", path, text, IdeIcon.CODE, true, animations,
            "运行时用例快照与实际结果", "复制实际输出", Runnable {
                (activity.getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("实际输出", record.result.stdout))
            })
        // Reader owns its resize/dismiss listener. Observe disappearance without replacing it.
        reader!!.setOnCancelListener { readerId = null }
    }
}
