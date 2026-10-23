package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.ide.ui.*
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Compact literal find/replace. Every result is checked against the immutable source buffer. */
object SearchDialog {
    fun show(activity: Activity, replace: Boolean, snapshot: () -> EditorSnapshot,
        select: (EditorSnapshot, Int, Int) -> Boolean,
        apply: (EditorSnapshot, TextChange, Boolean) -> Boolean, animations: Boolean = true, displayName: String? = null): AlertDialog {
        val palette = DesignTokens.forContext(activity)
        fun dp(v: Int) = IdeControls.dp(activity, v)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12))
            background = GradientDrawable().apply { setColor(palette.overlay); cornerRadius = dp(6).toFloat(); setStroke(dp(1), palette.border) }
        }
        val initial = snapshot()
        val query = EditText(activity).apply {
            hint = "查找"; contentDescription = "查找文本"; setSingleLine(); textSize = 14f
            setTextColor(palette.foreground); setHintTextColor(palette.muted)
            filters = arrayOf(InputFilter.LengthFilter(10000))
            if (initial.start != initial.end) setText(initial.text.substring(minOf(initial.start, initial.end), maxOf(initial.start, initial.end)).take(10000))
        }
        val replacement = EditText(activity).apply {
            hint = "替换为"; contentDescription = "替换文本"; setSingleLine(); textSize = 14f
            setTextColor(palette.foreground); setHintTextColor(palette.muted)
            filters = arrayOf(InputFilter.LengthFilter(Workspace.MAX_CHARS))
        }
        val feedback = TextView(activity).apply { textSize = 12f; setTextColor(palette.muted); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, 0, 0); contentDescription = "匹配数量"; minHeight=dp(32); maxLines=3; ellipsize=android.text.TextUtils.TruncateAt.END }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(activity).apply {
            text = activity.getString(R.string.search_current_file, displayName?.substringAfterLast('/') ?: activity.getString(R.string.search_document))
            textSize = 11f; setTextColor(palette.muted); setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.MIDDLE; setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.getChildAt(0).let { label -> (label as TextView).apply {
            setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(activity,IdeIconDrawable.file(displayName ?: "main.py"),palette.muted,dp(16)),null,null,null)
            compoundDrawablePadding=dp(8)
        } }
        content.addView(header)
        val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val replaceRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; visibility = if (replace) View.VISIBLE else View.GONE }
        val options = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        content.addView(row)
        val matchBar=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
        matchBar.addView(options,LinearLayout.LayoutParams(-2,-2))
        feedback.gravity=Gravity.END or Gravity.CENTER_VERTICAL
        matchBar.addView(feedback,LinearLayout.LayoutParams(0,-2,1f))
        content.addView(matchBar,LinearLayout.LayoutParams(-1,-2)); content.addView(replaceRow)
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        fun icon(parent: LinearLayout, glyph: IdeIcon, label: String, action: () -> Unit) =
            IdeControls.icon(activity, palette, glyph, label, action).also { parent.addView(it, LinearLayout.LayoutParams(dp(48), dp(48))) }
        icon(header, IdeIcon.CLOSE, "关闭查找") { dialog.dismiss() }
        val disclosure = icon(row, IdeIcon.CHEVRON_RIGHT, "展开或收起替换") { replaceRow.visibility = if (replaceRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        disclosure.rotation = if (replace) 90f else 0f
        disclosure.setOnClickListener { replaceRow.visibility = if (replaceRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE; disclosure.rotation = if (replaceRow.visibility == View.VISIBLE) 90f else 0f }
        row.addView(query, LinearLayout.LayoutParams(0, dp(48), 1f))
        replaceRow.addView(replacement, LinearLayout.LayoutParams(0, dp(48), 1f))
        var matchCase = false
        var wholeWord = false
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "CodePocket-find").apply { isDaemon = true } }
        val handler = Handler(Looper.getMainLooper())
        var pending: Future<*>? = null
        var request = 0L
        var closed = false
        lateinit var schedule: Runnable
        fun operation(kind: Int) {
            if (closed) return
            handler.removeCallbacks(schedule); pending?.cancel(true)
            val id = ++request
            val needle = query.text.toString()
            if (needle.isEmpty()) { feedback.setText(R.string.search_type_to_find); return }
            val state = snapshot(); val value = replacement.text.toString()
            val ignore = !matchCase; val word = wholeWord
            feedback.text = "查找中…"
            pending = executor.submit {
                val answer = runCatching {
                    val hits = EditorSearch.matches(state.text, needle, ignore, word)
                    val change = when (kind) {
                        2 -> EditorSearch.replaceNext(state, needle, value, ignore, word)
                        3 -> EditorSearch.replaceAll(state.text, needle, value, ignore, word)?.change
                        else -> null
                    }
                    val hit = if (kind in 2..3) null else EditorSearch.find(state.text, needle,
                        if (kind == 1) minOf(state.start, state.end) - 1 else if (kind == 4) minOf(state.start, state.end) else maxOf(state.start, state.end),
                        kind == 1, ignore, word)
                    Triple(hits, change, hit)
                }
                handler.post {
                    if (closed || activity.isDestroyed || !dialog.isShowing || id != request) return@post
                    val now = snapshot()
                    if (!state.sameBuffer(now) || now.start != state.start || now.end != state.end ||
                        query.text.toString() != needle || matchCase == ignore || wholeWord != word ||
                        (kind in 2..3 && replacement.text.toString() != value)) {
                        feedback.text = "内容已变化，请重新查找"; return@post
                    }
                    answer.onSuccess { (hits, change, hit) ->
                        when {
                            hits.isEmpty() -> feedback.text = "无匹配结果"
                            kind in 2..3 && change != null -> {
                                if (apply(state, change, true)) {
                                    feedback.text = activity.getString(R.string.search_replace_undo, activity.getString(R.string.search_replaced_count, if (kind == 3) hits.size else 1))
                                    if (kind == 2) handler.postDelayed(schedule, 250)
                                } else feedback.text = "当前无法替换，内容已保留"
                            }
                            hit != null -> feedback.text = if (select(state, hit.first, hit.last + 1))
                                "${hits.indexOf(hit).let { if (it < 0) 1 else it + 1 }} / ${hits.size}" else activity.getString(R.string.editor_navigation_stale)
                        }
                    }.onFailure { if (it !is InterruptedException) feedback.text = it.message ?: "查找失败，原文未修改" }
                }
            }
        }
        schedule = Runnable { operation(4) }
        icon(row, IdeIcon.BACK, "上一个匹配 · Shift+Enter") { operation(1) }
        icon(row, IdeIcon.NEXT, "下一个匹配 · Enter") { operation(0) }
        fun option(label: String, description: String, changed: (Boolean) -> Unit) =
            IdeControls.key(activity, palette, label, description) {}.also { view ->
                options.addView(view, LinearLayout.LayoutParams(-2, dp(48)))
                view.setOnClickListener {
                    view.isSelected = !view.isSelected
                    view.setTextColor(if (view.isSelected) palette.accentText else palette.foreground)
                    view.background = if (view.isSelected) GradientDrawable().apply { setColor(palette.selection); cornerRadius = dp(4).toFloat() } else IdeControls.feedback(activity, palette)
                    changed(view.isSelected); handler.removeCallbacks(schedule); handler.postDelayed(schedule, 120)
                }
            }
        option("Aa", "区分大小写") { matchCase = it }
        option("ab|", "全字匹配") { wholeWord = it }
        replaceRow.addView(IdeControls.key(activity, palette, "替换", "替换当前或下一个匹配") { operation(2) }, LinearLayout.LayoutParams(-2, dp(48)))
        replaceRow.addView(IdeControls.key(activity, palette, "全部", "替换全部匹配，可撤销") { operation(3) }, LinearLayout.LayoutParams(-2, dp(48)))
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { request++; pending?.cancel(true); handler.removeCallbacks(schedule); handler.postDelayed(schedule, 140) }
        }
        query.addTextChangedListener(watcher)
        query.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        query.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { operation(0); true } else false
        }
        replacement.setOnEditorActionListener { _, _, _ -> operation(2); true }
        query.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_ENTER) { if (event.action == KeyEvent.ACTION_DOWN) operation(if (event.isShiftPressed) 1 else 0); true }
            else if (key == KeyEvent.KEYCODE_ESCAPE) { if (event.action == KeyEvent.ACTION_UP) dialog.dismiss(); true } else false
        }
        dialog.setOnDismissListener { closed = true; request++; handler.removeCallbacksAndMessages(null); pending?.cancel(true); executor.shutdownNow() }
        com.codepocket.ide.animation.DialogMotion.show(dialog, animations)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent); clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            setLayout(minOf(activity.resources.displayMetrics.widthPixels - dp(16), dp(600)), WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        query.requestFocus(); query.setSelection(query.length())
        handler.post(schedule)
        return dialog
    }
}
