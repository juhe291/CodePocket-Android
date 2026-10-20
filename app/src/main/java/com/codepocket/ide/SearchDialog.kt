package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Search work stays off the input thread; revision checks prevent edits being applied to another buffer. */
object SearchDialog {
    fun show(activity: Activity, replace: Boolean, snapshot: () -> EditorSnapshot,
        select: (EditorSnapshot, Int, Int) -> Boolean, apply: (EditorSnapshot, TextChange, Boolean) -> Boolean, animations: Boolean = true): AlertDialog {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(com.codepocket.ide.ui.IdeControls.dp(activity,16), 8, com.codepocket.ide.ui.IdeControls.dp(activity,16), 8) }
        val query = EditText(activity).apply { hint = "查找文本（非正则）"; setSingleLine(); filters = arrayOf(InputFilter.LengthFilter(10000)) }
        val replacement = EditText(activity).apply { hint = "替换为"; setSingleLine(); filters = arrayOf(InputFilter.LengthFilter(Workspace.MAX_CHARS)) }
        val initial = snapshot()
        if (initial.start != initial.end) query.setText(initial.text.substring(minOf(initial.start, initial.end), maxOf(initial.start, initial.end)).take(10000))
        val matchCase = CheckBox(activity).apply { text = "区分大小写"; compoundDrawablePadding=com.codepocket.ide.ui.IdeControls.dp(activity,8)
            setCompoundDrawablesWithIntrinsicBounds(com.codepocket.ide.ui.IdeIconDrawable(activity,com.codepocket.ide.ui.IdeIcon.CASE,com.codepocket.ide.ui.DesignTokens.forContext(activity).muted,com.codepocket.ide.ui.IdeControls.dp(activity,18)),null,null,null) }
        val wholeWord = CheckBox(activity).apply { text = "全字匹配" }
        val feedback = TextView(activity)
        content.addView(query); if (replace) content.addView(replacement)
        content.addView(matchCase); content.addView(wholeWord); content.addView(feedback)
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(buttons)
        val executor = Executors.newSingleThreadExecutor()
        var pending: Future<*>? = null
        var request = 0L
        var busy = false
        val dialog = AlertDialog.Builder(activity).setTitle(if (replace) "查找与替换" else "查找")
            .setView(content).setPositiveButton("完成", null).create()
        fun operation(kind: Int) {
            if (busy || query.length() == 0) { feedback.text = if (busy) "正在查找…" else "请输入查找文本"; return }
            val state = snapshot(); val needle = query.text.toString(); val value = replacement.text.toString()
            val ignore = !matchCase.isChecked; val word = wholeWord.isChecked; val id = ++request
            busy = true; feedback.text = "正在处理…"
            pending = executor.submit {
                val answer = runCatching {
                    when (kind) {
                        2 -> EditorSearch.replaceSelection(state.text, needle, value, state.start, state.end, ignore, word)
                        3 -> EditorSearch.replaceAll(state.text, needle, value, ignore, word)
                        else -> EditorSearch.find(state.text, needle, if (kind == 1) minOf(state.start, state.end) - 1 else maxOf(state.start, state.end), kind == 1, ignore, word)
                    }
                }
                activity.runOnUiThread {
                    if (activity.isDestroyed || !dialog.isShowing || id != request) return@runOnUiThread
                    busy = false
                    val now = snapshot()
                    if (!state.sameBuffer(now) || query.text.toString() != needle ||
                        matchCase.isChecked == ignore || wholeWord.isChecked != word ||
                        (kind >= 2 && replacement.text.toString() != value) ||
                        (kind == 2 && (now.start != state.start || now.end != state.end))) {
                        feedback.text = "文件、选区或查找条件已变化，请重试"; return@runOnUiThread
                    }
                    answer.onSuccess { result -> when (result) {
                        is IntRange -> { feedback.text = if (select(state, result.first, result.last + 1)) "已定位；到末尾后会循环查找" else activity.getString(R.string.editor_navigation_stale) }
                        is TextChange -> { if (apply(state, result, true)) feedback.text = "已替换选中项" else feedback.text = "当前无法编辑" }
                        is Replacement -> { if (apply(state, result.change, false)) feedback.text = activity.getString(R.string.search_replaced_count, result.count) else feedback.text = "当前无法编辑" }
                        else -> feedback.text = if (kind == 2) "请先查找并选中匹配项" else "未找到匹配项"
                    } }.onFailure { feedback.text = it.message ?: "查找失败，原文未修改" }
                }
            }
        }
        for ((text, kind) in listOf("下一个" to 0, "上一个" to 1) + if (replace) listOf("替换选中项" to 2, "全部替换" to 3) else emptyList()) {
            if (kind == 0 || kind == 2) buttons.addView(LinearLayout(activity))
            (buttons.getChildAt(buttons.childCount - 1) as LinearLayout).addView(Button(activity).apply {
                this.text = text; setOnClickListener { operation(kind) }
            }, LinearLayout.LayoutParams(0, com.codepocket.ide.ui.IdeControls.dp(activity,48), 1f))
        }
        dialog.setOnDismissListener { request++; pending?.cancel(true); executor.shutdownNow() }
        com.codepocket.ide.animation.DialogMotion.show(dialog, animations)
        return dialog
    }
}
