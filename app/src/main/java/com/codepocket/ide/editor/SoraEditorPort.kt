package com.codepocket.ide.editor

import android.os.Looper
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.EditorSnapshot
import com.codepocket.ide.Workspace

/** Main-workbench contracts over the same guarded Sora Content adapter used by the internal tests. */
class SoraEditorPort(
    private val editor: WorkbenchEditor,
    private val document: () -> Pair<String, Long>?,
    private val identity: () -> String,
    private val cpp: () -> Boolean,
    private val dark: () -> Boolean,
    private val available: () -> Boolean
) : DocumentEditorPort {
    var pythonDocument: (() -> Boolean)? = null
    var diagnosticsChanged: (() -> Unit)? = null
    var runtimeDiagnostics: ((EditorFrame) -> List<InlineIssue>)? = null
    var report = SyntaxReport()
        private set
    private var closed = false
    override fun snapshot(): EditorFrame? = if (available()) captureDocument() else null
    override fun captureDocument(): EditorFrame? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return null
        val (key, revision) = document() ?: return null
        return EditorFrame(EditorSnapshot(key, revision, editor.text.toString(), editor.selectionStart,
            editor.selectionEnd), cpp(), dark(), editor.isComposing, pythonDocument?.invoke() ?: !cpp())
    }
    override fun replaceDocument(text: String): Boolean {
        if (closed || text.length > Workspace.MAX_CHARS || editor.isComposing) return false
        return editor.replaceDocument(identity(), text).also { if (it) clearDiagnostics() }
    }
    fun clearDiagnostics() { report = SyntaxReport(); editor.diagnostics(intArrayOf(), intArrayOf()); diagnosticsChanged?.invoke() }
    fun diagnosticAtCaret(): InlineIssue? = report.issues.firstOrNull { editor.selectionStart in it.start..it.end }
    override fun applyEdit(transaction: EditorTransaction): EditResult {
        val checked = EditorTransactions.check(snapshot(), transaction, Workspace.MAX_CHARS)
        if (checked != EditResult.APPLIED && checked != EditResult.SELECTION_ONLY) return checked
        val change = transaction.change
        if (transaction.preserveFocus) return EditResult.valueOf(editor.editWithoutFocus(change.start, change.end,
            change.text, change.selectionStart, change.selectionEnd))
        return EditResult.valueOf(editor.edit(change.start, change.end, change.text, change.selectionStart, change.selectionEnd))
    }
    override fun performHistory(source: EditorSnapshot, action: HistoryAction): Boolean =
        EditorTransactions.sourceFailure(snapshot(), source) == null && editor.hasFocus() && editor.history(action == HistoryAction.REDO)
    override fun select(request: EditorSelectionRequest): Boolean =
        EditorNavigation.canSelect(snapshot(), request) && editor.select(request.start, request.end, request.reveal, request.focus)
    override fun navigationSnapshot(): EditorViewportSnapshot? {
        if (closed || !available()) return null
        val (key, revision) = document() ?: return null
        return EditorViewportSnapshot(key, revision, editor.selectionStart, editor.selectionEnd,
            editor.contentScrollX, editor.contentScrollY, editor.interactionVersion, editor.isComposing,
            editor.isShown && editor.width > 0 && editor.height > 0 && editor.isLayoutReady)
    }
    override fun restoreViewport(snapshot: EditorViewportSnapshot): Boolean =
        snapshot.matches(navigationSnapshot(), false) && editor.restoreViewport(snapshot.x, snapshot.y)
    override fun revealCaret(): Boolean = !closed && available() && editor.revealCaret()
    override fun cancelNavigation() = editor.cancelNavigation()
    override fun locateDiagnostic(request: EditorDiagnostic): DiagnosticStatus {
        val target = EditorDiagnostics.resolve(snapshot(), request)
        val selection = target.selection ?: return target.status
        return if (select(selection)) target.status else DiagnosticStatus.STALE
    }
    override fun anchor(): com.codepocket.editor.surface.EditorSurface.Anchor? {
        if (snapshot() == null) return null
        val actual = editor.anchor() ?: return null
        val (key, revision) = document() ?: return null
        return com.codepocket.editor.surface.PopupGeometry.anchor(key, revision, actual.offset, actual.visible,
            actual.x.toDouble(), actual.top.toDouble(), actual.bottom.toDouble())
    }
    override fun applyDecorations(analysis: EditorAnalysis): Boolean {
        val frame = snapshot() ?: return false
        if (!analysis.canApplyTo(frame)) return false
        // Keep TextMate grammar styles; add only the existing lexical role/bracket layer.
        val extra = analysis.extraTokens.mapNotNull { token -> EditorPalette.color(token.kind, frame.dark)?.let { token to it } }
        val brackets = analysis.brackets
        if (!editor.decorate(frame.buffer.text, extra.map { it.first.start }.toIntArray(),
                extra.map { it.first.end }.toIntArray(), extra.map { it.second }.toIntArray(),
                brackets?.first ?: -1, brackets?.second ?: -1,
                if (frame.dark) 0xFF3A3D41.toInt() else 0xFFD6EBFF.toInt())) return false
        report = analysis.syntax.copy(issues = ((runtimeDiagnostics?.invoke(frame) ?: emptyList()) + analysis.syntax.issues)
            .distinctBy { it.start }.take(40))
        editor.diagnostics(report.issues.map { it.start }.toIntArray(), report.issues.map { it.end }.toIntArray())
        diagnosticsChanged?.invoke()
        return true
    }
    override fun close() { if (!closed) { closed = true; editor.close() } }
}
