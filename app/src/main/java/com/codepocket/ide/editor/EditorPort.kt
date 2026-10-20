package com.codepocket.ide.editor

import com.codepocket.ide.EditorFeatures
import com.codepocket.ide.EditorSnapshot

/** Read/decorate boundary; EditingEditorPort adds guarded edits/history. Offsets are UTF-16. */
interface EditorPort {
    // Called only on the UI thread. A missing frame means that a document is being switched.
    fun snapshot(): EditorFrame?
    // Decorations must not change text, selection, scroll, focus, undo history or composition.
    fun applyDecorations(analysis: EditorAnalysis): Boolean
}

data class EditorFrame(val buffer: EditorSnapshot, val cpp: Boolean, val dark: Boolean, val composing: Boolean = false, val python: Boolean = !cpp)

enum class HighlightFallback { NONE, TEXT_LIMIT, SPAN_LIMIT, ANALYSIS_FAILED }

data class EditorAnalysis(
    val source: EditorFrame,
    val tokens: List<EditorFeatures.Token>,
    val brackets: Pair<Int, Int>?,
    val fallback: HighlightFallback = HighlightFallback.NONE,
    val extraTokens: List<EditorFeatures.Token> = emptyList(),
    val syntax: SyntaxReport = SyntaxReport()
) {
    fun canApplyTo(current: EditorFrame): Boolean = !source.composing && !current.composing &&
        source.buffer.sameBuffer(current.buffer) && source.cpp == current.cpp && source.python == current.python && source.dark == current.dark &&
        source.buffer.start == current.buffer.start && source.buffer.end == current.buffer.end
}

/** Worker-owned single-document cache. Caret/theme changes reuse the lexical scan. Not semantic analysis. */
class EditorAnalyzer(private val pythonSyntax: ((String) -> SyntaxReport)? = null, private val tokenize: (String, Boolean) -> List<EditorFeatures.Token> = EditorFeatures::tokens) {
    private var cached: EditorFrame? = null
    private var cachedTokens = emptyList<EditorFeatures.Token>()
    private var decorations = DecorationScan(emptyList(), emptyList())
    private var syntax = SyntaxReport()

    fun analyze(frame: EditorFrame): EditorAnalysis {
        val code = frame.buffer.text
        if (code.length > MAX_HIGHLIGHT_CHARS) {
            cached = null; cachedTokens = emptyList()
            return EditorAnalysis(frame, emptyList(), null, HighlightFallback.TEXT_LIMIT, syntax = SyntaxReport(status = "文件超过 4.5 万字符，已暂停编辑诊断"))
        }
        val previous = cached
        if (previous == null || !previous.buffer.sameBuffer(frame.buffer) || previous.cpp != frame.cpp || previous.python != frame.python) {
            cachedTokens = tokenize(code, frame.cpp)
            decorations = EditorDecorations.scan(code, frame.cpp, cachedTokens)
            syntax = if (frame.python && pythonSyntax != null) pythonSyntax.invoke(code)
                else SyntaxReport(status = if (frame.cpp) "C++ 基础括号检查 · 完整诊断需运行编译" else "")
            syntax = syntax.copy(issues = (syntax.issues + decorations.issues).distinctBy { it.start }.take(40))
            cached = frame
        }
        val brackets = if (frame.buffer.start == frame.buffer.end)
            EditorFeatures.matchingBrackets(code, frame.buffer.start, cachedTokens) else null
        // Applying tens of thousands of spans on the UI thread can still stall input.
        return if (cachedTokens.size + decorations.tokens.size > MAX_HIGHLIGHT_SPANS)
            EditorAnalysis(frame, emptyList(), brackets, HighlightFallback.SPAN_LIMIT, syntax = syntax)
        else EditorAnalysis(frame, cachedTokens, brackets, extraTokens = decorations.tokens, syntax = syntax)
    }

    companion object {
        const val MAX_HIGHLIGHT_CHARS = 45000
        const val MAX_HIGHLIGHT_SPANS = 6000
    }
}

/** A composition can finish without changing text or the selection. */
class CompositionTransition {
    private var composing = false
    fun observe(active: Boolean): Boolean {
        val ended = composing && !active
        composing = active
        return ended
    }
}
