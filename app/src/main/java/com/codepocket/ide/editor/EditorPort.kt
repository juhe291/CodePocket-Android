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

data class EditorFrame(val buffer: EditorSnapshot, val cpp: Boolean, val dark: Boolean, val composing: Boolean = false)

enum class HighlightFallback { NONE, TEXT_LIMIT, SPAN_LIMIT, ANALYSIS_FAILED }

data class EditorAnalysis(
    val source: EditorFrame,
    val tokens: List<EditorFeatures.Token>,
    val brackets: Pair<Int, Int>?,
    val fallback: HighlightFallback = HighlightFallback.NONE
) {
    fun canApplyTo(current: EditorFrame): Boolean = !source.composing && !current.composing &&
        source.buffer.sameBuffer(current.buffer) && source.cpp == current.cpp && source.dark == current.dark &&
        source.buffer.start == current.buffer.start && source.buffer.end == current.buffer.end
}

/** Worker-owned single-document cache. Caret/theme changes reuse the lexical scan. Not semantic analysis. */
class EditorAnalyzer(private val tokenize: (String, Boolean) -> List<EditorFeatures.Token> = EditorFeatures::tokens) {
    private var cached: EditorFrame? = null
    private var cachedTokens = emptyList<EditorFeatures.Token>()

    fun analyze(frame: EditorFrame): EditorAnalysis {
        val code = frame.buffer.text
        if (code.length > MAX_HIGHLIGHT_CHARS) {
            cached = null; cachedTokens = emptyList()
            return EditorAnalysis(frame, emptyList(), null, HighlightFallback.TEXT_LIMIT)
        }
        val previous = cached
        if (previous == null || !previous.buffer.sameBuffer(frame.buffer) || previous.cpp != frame.cpp) {
            cachedTokens = tokenize(code, frame.cpp)
            cached = frame
        }
        val brackets = if (frame.buffer.start == frame.buffer.end)
            EditorFeatures.matchingBrackets(code, frame.buffer.start, cachedTokens) else null
        // Applying tens of thousands of spans on the UI thread can still stall input.
        return if (cachedTokens.size > MAX_HIGHLIGHT_SPANS)
            EditorAnalysis(frame, emptyList(), brackets, HighlightFallback.SPAN_LIMIT)
        else EditorAnalysis(frame, cachedTokens, brackets)
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
