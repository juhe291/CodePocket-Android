package com.codepocket.ide.editor

import android.os.Looper
import android.text.Editable
import android.text.Selection
import android.view.ViewTreeObserver
import android.text.NoCopySpan
import android.text.Spannable
import android.text.Spanned
import android.text.SpanWatcher
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.BaseInputConnection
import com.codepocket.ide.CodeEditor
import com.codepocket.ide.EditorSnapshot
import com.codepocket.ide.Workspace

/** Native adapter: guards explicit edits; platform typing, history and IME remain native. */
class NativeEditorPort(
    private val editor: CodeEditor,
    private val document: () -> Pair<String, Long>?,
    private val cpp: () -> Boolean,
    private val dark: () -> Boolean,
    private val compositionEnded: () -> Unit
) : NavigatingEditorPort {
    private class SyntaxSpan(color: Int) : ForegroundColorSpan(color), NoCopySpan
    private class BracketSpan(color: Int) : BackgroundColorSpan(color), NoCopySpan
    private var closed = false
    private var interaction = 0L
    private val pendingNavigation = mutableSetOf<Pair<ViewTreeObserver, ViewTreeObserver.OnPreDrawListener>>()
    private val navigation = EditorViewportQueue(::postNavigation, ::navigationSnapshot) { source, reveal ->
        if (reveal) editor.bringPointIntoView(source.end) else editor.restoreViewport(source.x, source.y)
    }
    private val navigationInput: () -> Unit = { interaction++; cancelNavigation() }

    private fun postNavigation(action: () -> Unit) {
        val observer = editor.viewTreeObserver
        lateinit var listener: ViewTreeObserver.OnPreDrawListener
        listener = ViewTreeObserver.OnPreDrawListener {
            removeNavigationListener(observer, listener)
            pendingNavigation.remove(observer to listener)
            action()
            true
        }
        pendingNavigation.add(observer to listener)
        observer.addOnPreDrawListener(listener)
        editor.invalidate()
    }

    private fun removeNavigationListener(observer: ViewTreeObserver, listener: ViewTreeObserver.OnPreDrawListener) {
        if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        val current = editor.viewTreeObserver
        // Android merges a detached view's observer into the attached observer.
        if (current !== observer && current.isAlive) current.removeOnPreDrawListener(listener)
    }

    override fun cancelNavigation() {
        navigation.cancel()
        pendingNavigation.forEach { (observer, listener) -> removeNavigationListener(observer, listener) }
        pendingNavigation.clear()
    }

    override fun navigationSnapshot(): EditorViewportSnapshot? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return null
        val (key, revision) = document() ?: return null
        return EditorViewportSnapshot(key, revision, editor.selectionStart, editor.selectionEnd,
            editor.scrollX, editor.scrollY, interaction, BaseInputConnection.getComposingSpanStart(editor.text) >= 0,
            editor.isShown && editor.width > 0 && editor.height > 0 && editor.layout != null)
    }

    override fun select(request: EditorSelectionRequest): Boolean {
        if (!EditorNavigation.canSelect(snapshot(), request)) return false
        cancelNavigation()
        editor.setSelection(request.start, request.end)
        if (request.focus) editor.requestFocus()
        if (request.reveal) revealCaret()
        return true
    }

    override fun restoreViewport(snapshot: EditorViewportSnapshot): Boolean {
        if (!snapshot.matches(navigationSnapshot(), false)) return false
        cancelNavigation()
        return navigation.submit(snapshot)
    }

    override fun revealCaret(): Boolean {
        val snapshot = navigationSnapshot() ?: return false
        cancelNavigation()
        return navigation.submit(snapshot, reveal = true)
    }

    override fun locateDiagnostic(request: EditorDiagnostic): DiagnosticStatus {
        val target = EditorDiagnostics.resolve(snapshot(), request)
        val selection = target.selection ?: return target.status
        return if (select(selection)) target.status else DiagnosticStatus.STALE
    }

    private var observed: Editable? = null
    private var coloredText: Editable? = null
    private var coloredFrame: EditorFrame? = null
    private var coloredFallback: HighlightFallback? = null
    private val composition = CompositionTransition()
    private val notifyCommit = Runnable {
        if (!closed && BaseInputConnection.getComposingSpanStart(editor.text) < 0) compositionEnded()
    }
    private val spanWatcher = object : SpanWatcher, NoCopySpan {
        private fun changed(text: Spannable, what: Any) {
            if (closed || what === this || what is SyntaxSpan || what is BracketSpan) return
            if (what === Selection.SELECTION_START || what === Selection.SELECTION_END) interaction++
            if (composition.observe(BaseInputConnection.getComposingSpanStart(text) >= 0)) {
                // Wait until the IME has finished its current transaction; never edit from a span callback.
                editor.removeCallbacks(notifyCommit); editor.post(notifyCommit)
            }
        }
        override fun onSpanAdded(text: Spannable, what: Any, start: Int, end: Int) = changed(text, what)
        override fun onSpanRemoved(text: Spannable, what: Any, start: Int, end: Int) = changed(text, what)
        override fun onSpanChanged(text: Spannable, what: Any, oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int) = changed(text, what)
    }
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { observe(s) }
    }

    init {
        editor.onNavigationInput = navigationInput
        editor.addTextChangedListener(textWatcher); observe(editor.text)
    }

    private fun observe(text: Editable?) {
        if (closed || text == null) return
        if (observed !== text) {
            observed?.removeSpan(spanWatcher)
            observed = text
            text.setSpan(spanWatcher, 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        composition.observe(BaseInputConnection.getComposingSpanStart(text) >= 0)
    }

    override fun snapshot(): EditorFrame? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return null
        val (key, revision) = document() ?: return null
        return EditorFrame(EditorSnapshot(key, revision, editor.text.toString(), editor.selectionStart,
            editor.selectionEnd), cpp(), dark(), BaseInputConnection.getComposingSpanStart(editor.text) >= 0)
    }

    override fun applyEdit(transaction: EditorTransaction): EditResult =
        EditorTransactions.apply(snapshot(), transaction, Workspace.MAX_CHARS) { change, changesText ->
            cancelNavigation()
            editor.beginBatchEdit()
            try {
                if (changesText) editor.text.replace(change.start, change.end, change.text)
                editor.setSelection(change.selectionStart, change.selectionEnd)
            } finally { editor.endBatchEdit() }
        }

    override fun performHistory(source: EditorSnapshot, action: HistoryAction): Boolean {
        if (EditorTransactions.sourceFailure(snapshot(), source) != null || !editor.hasFocus()) return false
        cancelNavigation()
        return editor.onTextContextMenuItem(if (action == HistoryAction.UNDO) android.R.id.undo else android.R.id.redo)
    }

    override fun applyDecorations(analysis: EditorAnalysis): Boolean {
        val current = snapshot() ?: return false
        if (!analysis.canApplyTo(current)) return false
        val value = editor.text
        if (analysis.tokens.any { it.start < 0 || it.end <= it.start || it.end > value.length } ||
            analysis.brackets?.let { it.first !in 0 until value.length || it.second !in 0 until value.length } == true) return false
        val previous = coloredFrame
        if (coloredText !== value || previous == null || !previous.buffer.sameBuffer(current.buffer) ||
            previous.cpp != current.cpp || previous.dark != current.dark || coloredFallback != analysis.fallback) {
            value.getSpans(0, value.length, SyntaxSpan::class.java).forEach(value::removeSpan)
            for (token in analysis.tokens) {
                val color = color(token.kind, current.dark) ?: continue
                value.setSpan(SyntaxSpan(color), token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            coloredText = value; coloredFrame = current; coloredFallback = analysis.fallback
        }
        value.getSpans(0, value.length, BracketSpan::class.java).forEach(value::removeSpan)
        analysis.brackets?.let { (first, second) ->
            val color = if (current.dark) 0xFF3A3D41.toInt() else 0xFFD6EBFF.toInt()
            for (position in listOf(first, second)) value.setSpan(BracketSpan(color), position, position + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return true
    }

    override fun close() {
        closed = true; cancelNavigation(); navigation.close()
        if (editor.onNavigationInput === navigationInput) editor.onNavigationInput = null
        editor.removeCallbacks(notifyCommit)
        editor.removeTextChangedListener(textWatcher); observed?.removeSpan(spanWatcher)
        observed = null; coloredText = null; coloredFrame = null; coloredFallback = null
    }

    private fun color(kind: String, dark: Boolean): Int? = when (kind) {
        "keyword" -> if (dark) 0xFF569CD6.toInt() else 0xFF0000FF.toInt()
        "type" -> if (dark) 0xFF4EC9B0.toInt() else 0xFF267F99.toInt()
        "function" -> if (dark) 0xFFDCDCAA.toInt() else 0xFF795E26.toInt()
        "string" -> if (dark) 0xFFCE9178.toInt() else 0xFFA31515.toInt()
        "number" -> if (dark) 0xFFB5CEA8.toInt() else 0xFF098658.toInt()
        "comment" -> if (dark) 0xFF6A9955.toInt() else 0xFF008000.toInt()
        "preprocessor" -> if (dark) 0xFFC586C0.toInt() else 0xFFAF00DB.toInt()
        else -> null
    }
}
