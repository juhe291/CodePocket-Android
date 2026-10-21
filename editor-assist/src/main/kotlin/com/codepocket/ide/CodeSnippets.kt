package com.codepocket.ide

data class SnippetStop(val label: String, val start: Int, val end: Int)
data class SnippetExpansion(val change: TextChange, val stops: List<SnippetStop>, val finalOffset: Int) {
    fun session(document: String, revision: Long, length: Int) = SnippetSession(document, revision, length, stops, finalOffset)
}
data class SnippetDescriptor(val id: String, val trigger: String, val title: String, val cpp: Boolean)

/** Small, structured built-in templates. No external snippet grammar or language execution. */
object CodeSnippets {
    private val templates = listOf(
        SnippetDescriptor("cpp.for", "for", "范围遍历", true),
        SnippetDescriptor("cpp.if", "if", "条件分支", true),
        SnippetDescriptor("cpp.while", "while", "循环", true),
        SnippetDescriptor("cpp.func", "func", "函数定义", true),
        SnippetDescriptor("cpp.cout", "cout", "输出一行", true),
        SnippetDescriptor("cpp.main", "main", "主函数", true),
        SnippetDescriptor("py.for", "for", "遍历循环", false),
        SnippetDescriptor("py.if", "if", "条件分支", false),
        SnippetDescriptor("py.while", "while", "循环", false),
        SnippetDescriptor("py.def", "def", "函数定义", false),
        SnippetDescriptor("py.print", "print", "输出", false),
        SnippetDescriptor("py.main", "main", "主模块入口", false)
    )
    fun all(cpp: Boolean) = templates.filter { it.cpp == cpp }

    /** Only replace a token on an otherwise empty line; never swallow surrounding expressions. */
    fun replacement(request: CompletionRequest): Pair<Int, Int>? {
        val code = request.code
        if (code.length > 45000 || request.caret != request.end || request.caret !in 0..code.length) return null
        fun identifier(ch: Char) = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '_'
        var start = request.caret; var end = request.caret
        while (start > 0 && identifier(code[start - 1])) start--
        while (end < code.length && identifier(code[end])) end++
        val lineStart = if (start == 0) 0 else code.lastIndexOf('\n', start - 1) + 1
        val lineEnd = code.indexOf('\n', end).let { if (it < 0) code.length else it }
        if (code.substring(lineStart, start).any { it != ' ' && it != '\t' } ||
            code.substring(end, lineEnd).any { it != ' ' && it != '\t' && it != '\r' }) return null
        if (EditorFeatures.tokens(code, request.cpp).any {
            it.kind in setOf("comment", "string", "preprocessor") && start > it.start && start <= it.end
        }) return null
        return start to end
    }

    fun candidates(request: CompletionRequest, prefix: String): List<CompletionItem> {
        if (replacement(request) == null) return emptyList()
        return all(request.cpp).filter { it.trigger.startsWith(prefix) }.map {
            val note = if (it.id == "cpp.for") "需要可迭代的 values 对象。" else if (it.id == "cpp.cout") "需要 <iostream>。" else ""
            CompletionItem("${it.trigger} · ${it.title}", CompletionKind.SNIPPET,
                "内置模板：${it.title}。${note}Tab/Shift+Tab 跳转占位符，Esc 结束；不推断语义。", it.id)
        }
    }

    fun expand(id: String, request: CompletionRequest, settings: AssistOptionsContract, maxChars: Int = 200000): SnippetExpansion? {
        val descriptor = templates.firstOrNull { it.id == id && it.cpp == request.cpp } ?: return null
        val (start, end) = replacement(request) ?: return null
        val lineStart = if (start == 0) 0 else request.code.lastIndexOf('\n', start - 1) + 1
        val base = request.code.substring(lineStart, start)
        val width = settings.tabWidth.takeIf { it in listOf(2, 4, 8) } ?: 4
        val unit = if (settings.useSpaces) " ".repeat(width) else "\t"
        val builder = TemplateBuilder(base, unit, if (request.code.contains("\r\n")) "\r\n" else "\n")
        with(builder) {
            when (descriptor.id) {
                "cpp.for" -> {
                    text("for (const auto& "); field("循环变量", "item"); text(" : "); field("可迭代对象", "values"); text(") {")
                    line(1); field("代码体", ""); line(); text("}")
                }
                "cpp.if" -> { text("if ("); field("条件", "true"); text(") {"); line(1); field("代码体", ""); line(); text("}") }
                "cpp.while" -> { text("while ("); field("条件", "false"); text(") {"); line(1); field("代码体", ""); line(); text("}") }
                "cpp.func" -> {
                    text("void "); field("函数名", "solve"); text("("); field("参数", ""); text(") {")
                    line(1); field("代码体", ""); line(); text("}")
                }
                "cpp.cout" -> { text("std::cout << "); field("输出内容", "0"); text(" << '\\n';") }
                "cpp.main" -> { text("int main() {"); line(1); field("代码体", ""); line(1); text("return 0;"); line(); text("}") }
                "py.for" -> {
                    text("for "); field("循环变量", "item"); text(" in "); field("可迭代对象", "range(10)"); text(":")
                    line(1); field("代码体", "pass")
                }
                "py.if" -> { text("if "); field("条件", "True"); text(":"); line(1); field("代码体", "pass") }
                "py.while" -> { text("while "); field("条件", "False"); text(":"); line(1); field("代码体", "pass") }
                "py.def" -> { text("def "); field("函数名", "solve"); text("("); field("参数", "values"); text("):"); line(1); field("代码体", "pass") }
                "py.print" -> { text("print("); field("输出内容", "0"); text(")") }
                "py.main" -> { text("if __name__ == \"__main__\":"); line(1); field("代码体", "pass") }
            }
        }
        val body = builder.value.toString()
        if (request.code.length.toLong() - (end - start) + body.length > maxChars) return null
        val stops = builder.stops.map { it.copy(start = start + it.start, end = start + it.end) }
        if (stops.isEmpty()) return null
        val first = stops.first()
        return SnippetExpansion(TextChange(start, end, body, first.start, first.end), stops, start + body.length)
    }

    private class TemplateBuilder(val base: String, val unit: String, val eol: String) {
        val value = StringBuilder()
        val stops = mutableListOf<SnippetStop>()
        fun text(text: String) { value.append(text) }
        fun line(depth: Int = 0) { value.append(eol).append(base).append(unit.repeat(depth)) }
        fun field(label: String, default: String) {
            val start = value.length; value.append(default); stops.add(SnippetStop(label, start, value.length))
        }
    }
}
