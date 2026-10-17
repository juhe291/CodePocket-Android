package com.codepocket.ide.editor

import com.codepocket.ide.DocumentTree
import com.codepocket.ide.EditorSearch
import com.codepocket.ide.EditorSnapshot
import com.codepocket.ide.Workspace
import java.io.File

data class EditorDiagnostic(val source: EditorSnapshot, val line: Int,
    val originKey: String? = null, val originHash: String? = null)
enum class DiagnosticStatus {
    LOCATED, LINE_ONLY, NO_DOCUMENT, STALE, COMPOSING, CHANGED_SOURCE, INVALID_LINE;
    val located get() = this == LOCATED || this == LINE_ONLY
}
data class DiagnosticTarget(val status: DiagnosticStatus, val selection: EditorSelectionRequest? = null)

/** Line diagnostics only; no fabricated semantic analysis. Imported files may have no version proof. */
object EditorDiagnostics {
    fun resolve(current: EditorFrame?, request: EditorDiagnostic): DiagnosticTarget {
        val failure = EditorTransactions.sourceFailure(current, request.source)
        if (failure != null) return DiagnosticTarget(when (failure) {
            EditResult.NO_DOCUMENT -> DiagnosticStatus.NO_DOCUMENT
            EditResult.COMPOSING -> DiagnosticStatus.COMPOSING
            else -> DiagnosticStatus.STALE
        })
        if ((request.originKey != null && request.originKey != request.source.key) ||
            (request.originHash != null && request.originHash != DocumentTree.digest(request.source.text)))
            return DiagnosticTarget(DiagnosticStatus.CHANGED_SOURCE)
        val offset = EditorSearch.lineOffset(request.source.text, request.line)
            ?: return DiagnosticTarget(DiagnosticStatus.INVALID_LINE)
        val verified = request.originKey != null && request.originHash != null
        return DiagnosticTarget(if (verified) DiagnosticStatus.LOCATED else DiagnosticStatus.LINE_ONLY,
            EditorSelectionRequest(request.source, offset, focus = true))
    }
}

/** Resolve actual workspace or SAF execution-snapshot paths, retaining Workspace's file boundary checks. */
object DiagnosticFiles {
    fun relative(workspace: Workspace, file: String, executionRoot: String?): String? = runCatching {
        workspace.path(File(file)).also { require(workspace.resolve(it).isFile) }
    }.getOrNull() ?: runCatching {
        val root = File(executionRoot ?: error("No execution root")).canonicalPath
        val location = File(file).canonicalPath
        require(location.startsWith(root + File.separator))
        location.removePrefix(root + File.separator).replace(File.separatorChar, '/')
            .also { require(workspace.resolve(it).isFile) }
    }.getOrNull()
}
