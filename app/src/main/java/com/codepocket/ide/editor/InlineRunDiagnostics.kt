package com.codepocket.ide.editor

import com.codepocket.ide.DocumentTree
import com.codepocket.ide.EditorSearch
import com.codepocket.ide.RunKind
import com.codepocket.ide.RunResult

object InlineRunDiagnostics {
    /** Never put stale/imported/network errors under the current source. */
    fun resolve(frame: EditorFrame, result: RunResult?, sameFile: Boolean): List<InlineIssue> {
        if (result == null || !sameFile || frame.composing || result.kind !in listOf(RunKind.COMPILE, RunKind.RUNTIME) ||
            result.sourceKey != frame.buffer.key || result.sourceHash != DocumentTree.digest(frame.buffer.text)) return emptyList()
        val line = result.errorLine ?: return emptyList()
        val code = frame.buffer.text
        val start = EditorSearch.lineOffset(code, line) ?: return emptyList()
        val end = code.indexOf('\n', start).let { if (it < 0) code.length else it }
        val first = (start until end).firstOrNull { !code[it].isWhitespace() } ?: start
        val text = result.stderr.take(4000).ifBlank { result.kind.label }
        return listOf(InlineIssue(first, end, text, if (frame.cpp) "C++ 云端编译 / 运行" else "Python 实际运行"))
    }
}
