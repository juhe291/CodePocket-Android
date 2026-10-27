package com.codepocket.ide

import com.codepocket.editor.surface.EditorSurface
import com.codepocket.editor.surface.PopupGeometry

enum class CompletionKind(val icon: String, val title: String) {
    KEYWORD("K", "关键字"), TYPE("T", "类型名称"), BUILTIN("ƒ", "内置/常用名称"), IDENTIFIER("abc", "文件中出现的名称"), SNIPPET("{}", "内置代码片段"),
    FUNCTION("ƒ", "函数/方法"), MODULE("M", "模块"), VARIABLE("x", "变量"), PARAMETER("p", "参数")
}
enum class CompletionArrows { EXPLICIT, WHEN_VISIBLE, OFF }
data class CompletionItem(val label: String, val kind: CompletionKind, val detail: String, val snippetId: String? = null,
    val semanticInsertion: CompletionEdit? = null)
data class CompletionEdit(val change: TextChange, val snippet: SnippetExpansion? = null)
data class CompletionRequest(val document: String, val revision: Long, val code: String, val caret: Int, val end: Int, val cpp: Boolean)
data class CompletionBatch(val request: CompletionRequest, val start: Int, val end: Int, val items: List<CompletionItem>,
    val source: String? = null, val currentFileOnly: Boolean = false) {
    fun edit(current: CompletionRequest, index: Int, maxChars: Int = 200000): TextChange? =
        insertion(current, index, AssistOptions(), maxChars)?.change

    fun insertion(current: CompletionRequest, index: Int, settings: AssistOptionsContract, maxChars: Int = 200000): CompletionEdit? {
        if (request != current || current.caret != current.end) return null
        val item = items.getOrNull(index) ?: return null
        if (item.snippetId != null) {
            val expanded = CodeSnippets.expand(item.snippetId, current, settings, maxChars) ?: return null
            return CompletionEdit(expanded.change, expanded)
        }
        item.semanticInsertion?.let { insertion ->
            val edit = insertion.change
            if (edit.start !in 0..current.caret || edit.end !in current.caret..current.code.length) return null
            val length = current.code.length.toLong() - (edit.end - edit.start) + edit.text.length
            if (length !in 0..maxChars.toLong() || edit.selectionStart !in 0..length.toInt() ||
                edit.selectionEnd !in edit.selectionStart..length.toInt()) return null
            return insertion
        }
        if (current.code.length.toLong() - (end - start) + item.label.length > maxChars) return null
        return CompletionEdit(TextChange(start, end, item.label, start + item.label.length))
    }
}

/** A lexical provider only: never executes code, resolves members or infers symbol types. */
object LexicalCompletion {
    private val identifier = Regex("[A-Za-z_][A-Za-z_0-9]*")
    // Small curated reference, not signatures inferred from the user's code.
    // Source: https://docs.python.org/3.13/library/functions.html#print
    private val pythonHelp = mapOf(
        "print" to "print(*objects, sep=' ', end='\\n', file=None, flush=False)\nPython 3.13 内置参考：向文本流输出。局部同名对象的签名可能不同。",
        "len" to "len(object, /)\nPython 3.13 内置参考：返回对象的元素个数。局部同名对象的签名可能不同。",
        "input" to "input() / input(prompt, /)\nPython 3.13 内置参考：读取一行并去掉末尾换行。运行时在终端输入，Enter 提交。"
    )
    private fun part(ch: Char) = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '_'

    fun memberContext(request: CompletionRequest): Boolean {
        if (request.caret !in 0..request.code.length) return false
        var start = request.caret
        while (start > 0 && part(request.code[start - 1])) start--
        val before = request.code.substring(0, start).trimEnd()
        return before.endsWith('.') || (request.cpp && (before.endsWith("->") || before.endsWith("::")))
    }

    fun complete(request: CompletionRequest, includeSnippets: Boolean = true): CompletionBatch? {
        val code = request.code
        if (request.caret != request.end || request.caret !in 0..code.length || memberContext(request)) return null
        var start = request.caret
        while (start > 0 && part(code[start - 1])) start--
        val prefix = code.substring(start, request.caret)
        if (prefix.isEmpty() || prefix.first() in '0'..'9') return null
        var end = request.caret
        while (end < code.length && part(code[end])) end++
        val ignored = if (code.length <= 45000) EditorFeatures.tokens(code, request.cpp)
            .filter { it.kind in setOf("comment", "string", "preprocessor") } else emptyList()
        if (ignored.any { start >= it.start && start < it.end }) return null
        val excluded = if (code.length <= 45000) BooleanArray(code.length).apply {
            ignored.forEach { token -> for (i in token.start until token.end) this[i] = true }
        } else null
        val common = EditorFeatures.completionWords(request.cpp)
        val names = if (code.length <= 45000) identifier.findAll(code).filter { match ->
            match.value.length >= 3 && excluded?.get(match.range.first) != true
        }.map { it.value }.toList() else emptyList()
        val items = (common + names).distinct().asSequence()
            .filter { it.startsWith(prefix) && (it != prefix || end != request.caret) }
            .sortedWith(compareBy<String> { it !in common }.thenBy { it.length }.thenBy { it })
            .take(30).map { name ->
                val kind = EditorFeatures.completionKind(name, request.cpp)
                val detail = if (!request.cpp) pythonHelp[name] else null
                CompletionItem(name, kind, detail ?: when (kind) {
                    CompletionKind.SNIPPET -> "内置代码模板；不是语义分析结果。"
                    CompletionKind.IDENTIFIER -> "当前文件中出现过此名称；未分析作用域、类型或成员。"
                    CompletionKind.TYPE -> "常用 C++ 类型名称；未验证头文件、命名空间或声明。"
                    CompletionKind.KEYWORD -> "语言关键字参考；当前为基础词法补全。"
                    CompletionKind.BUILTIN -> "常用名称参考；未进行类型分析或校验当前作用域。"
                    else -> "基础词法候选。"
                })
            }.toList()
        val snippets = if (includeSnippets) CodeSnippets.candidates(request, prefix) else emptyList()
        val combined = if (snippets.isEmpty()) items else items.take(24) + snippets
        return combined.takeIf { it.isNotEmpty() }?.let { CompletionBatch(request, start, end, it) }
    }
}

enum class CompletionKey { TAB, ENTER, UP, DOWN, ESC, OTHER }
enum class CompletionAction { PASS, ACCEPT, PREVIOUS, NEXT, DISMISS }
object CompletionKeys {
    fun action(key: CompletionKey, settings: AssistOptionsContract, visible: Boolean, explicitRequest: Boolean,
        explicitSelection: Boolean, composing: Boolean, selectedText: Boolean, modified: Boolean, softKeyboard: Boolean = false): CompletionAction {
        if (!visible || composing || selectedText || modified || softKeyboard) return CompletionAction.PASS
        val arrows = settings.completionArrows == CompletionArrows.WHEN_VISIBLE ||
            (settings.completionArrows == CompletionArrows.EXPLICIT && explicitRequest)
        return when (key) {
            CompletionKey.TAB -> if (settings.completionTab) CompletionAction.ACCEPT else CompletionAction.PASS
            CompletionKey.ENTER -> if (settings.completionEnter && explicitSelection) CompletionAction.ACCEPT else CompletionAction.PASS
            CompletionKey.UP -> if (arrows) CompletionAction.PREVIOUS else CompletionAction.PASS
            CompletionKey.DOWN -> if (arrows) CompletionAction.NEXT else CompletionAction.PASS
            CompletionKey.ESC -> CompletionAction.DISMISS
            CompletionKey.OTHER -> CompletionAction.PASS
        }
    }
}

data class CompletionBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
data class CompletionPlacement(val x: Int, val y: Int, val width: Int, val height: Int)
object CompletionGeometry {
    /** Coordinates share the same screen space; keep the popup off both IME and caret line. */
    fun place(bounds: CompletionBounds, caretX: Int, caretTop: Int, caretBottom: Int,
        desiredWidth: Int, desiredHeight: Int, minimumHeight: Int, gap: Int): CompletionPlacement? {
        val placed = PopupGeometry.place(EditorSurface.Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
            caretX, caretTop, caretBottom, desiredWidth, desiredHeight, minimumHeight, gap) ?: return null
        return CompletionPlacement(placed.x, placed.y, placed.width, placed.height)
    }
}
