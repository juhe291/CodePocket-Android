package com.codepocket.ide

data class SignatureForm(val parts: List<String>, val positional: List<Int>,
    val keywords: Map<String, Int> = emptyMap(), val variadic: Int? = null) {
    fun parameter(argument: Int, keyword: String?): Int? =
        if (keyword != null) keywords[keyword] else variadic ?: positional.getOrNull(argument)
    fun label(name: String) = "$name(${parts.joinToString(", ")})"
    fun range(name: String, index: Int?): Pair<Int, Int>? {
        if (index == null || index !in parts.indices) return null
        val start = name.length + 1 + parts.take(index).sumOf { it.length + 2 }
        return start to start + parts[index].length
    }
}

data class BuiltinSignature(val name: String, val cpp: Boolean, val forms: List<SignatureForm>,
    val description: String, val source: String, val semantic: Boolean = false, val currentFileOnly: Boolean = false)

/** Curated reference only. No symbol lookup, overload resolution, evaluation or network calls. */
object BuiltinSignatures {
    private const val python = "https://docs.python.org/3.13/library/functions.html#"
    private const val cpp17 = "https://timsong-cpp.github.io/cppwp/n4659/"
    private fun form(vararg parts: String) = SignatureForm(parts.toList(), parts.indices.toList())
    val all = listOf(
        BuiltinSignature("print", false, listOf(SignatureForm(
            listOf("*objects", "sep=' '", "end='\\n'", "file=None", "flush=False"), emptyList(),
            mapOf("sep" to 1, "end" to 2, "file" to 3, "flush" to 4), 0)),
            "Python 3.13：输出文本；分隔符等选项须用关键字指定。", python + "print"),
        BuiltinSignature("input", false, listOf(form(), SignatureForm(listOf("prompt", "/"), listOf(0))),
            "Python 3.13：读取一行；运行时在终端输入，Enter 提交。", python + "input"),
        BuiltinSignature("len", false, listOf(SignatureForm(listOf("object", "/"), listOf(0))),
            "Python 3.13：返回元素数量。", python + "len"),
        BuiltinSignature("range", false, listOf(SignatureForm(listOf("stop", "/"), listOf(0)),
            SignatureForm(listOf("start", "stop", "step=1", "/"), listOf(0, 1, 2))),
            "Python 3.13：整数序列，右端点不包含；step 不能为 0。", python + "func-range"),
        BuiltinSignature("enumerate", false, listOf(SignatureForm(listOf("iterable", "start=0"), listOf(0, 1),
            mapOf("iterable" to 0, "start" to 1))),
            "Python 3.13：遍历时附带从 start 开始的序号。", python + "enumerate"),
        BuiltinSignature("sorted", false, listOf(SignatureForm(listOf("iterable", "/", "*", "key=None", "reverse=False"),
            listOf(0), mapOf("key" to 3, "reverse" to 4))),
            "Python 3.13：生成排序后的新列表，不直接修改原容器。", python + "sorted"),
        BuiltinSignature("sum", false, listOf(SignatureForm(listOf("iterable", "/", "start=0"), listOf(0, 2), mapOf("start" to 2))),
            "Python 3.13：从 start 累加可迭代对象中的元素。", python + "sum"),
        BuiltinSignature("std::sort", true, listOf(form("first", "last"), form("first", "last", "comp")),
            "C++17 <algorithm>：排序 [first, last)，需要随机访问迭代器；仅列常用非执行策略形式。", cpp17 + "alg.sort"),
        BuiltinSignature("std::reverse", true, listOf(form("first", "last")),
            "C++17 <algorithm>：反转 [first, last)，需要双向迭代器；未列执行策略形式。", cpp17 + "alg.reverse"),
        BuiltinSignature("std::lower_bound", true, listOf(form("first", "last", "value"), form("first", "last", "value", "comp")),
            "C++17 <algorithm>：在满足分区前提的区间查找下界；须保持比较规则一致。", cpp17 + "lower.bound"),
        BuiltinSignature("std::upper_bound", true, listOf(form("first", "last", "value"), form("first", "last", "value", "comp")),
            "C++17 <algorithm>：在满足分区前提的区间查找上界；须保持比较规则一致。", cpp17 + "upper.bound"),
        BuiltinSignature("std::getline", true, listOf(form("input", "str"), form("input", "str", "delim")),
            "C++17 <string>：读取一行至字符串；此为简化参数名，未展开字符类型或所有重载。", cpp17 + "string.io")
    )
    fun find(name: String, cpp: Boolean) = all.firstOrNull { it.name == name && it.cpp == cpp }
}

data class SignatureResult(val request: CompletionRequest, val reference: BuiltinSignature,
    val openOffset: Int, val argument: Int, val keyword: String?, val actualParameters: List<Int?>? = null) {
    fun matches(current: CompletionRequest) = request == current
    fun active(form: Int) = if(actualParameters!=null)actualParameters.getOrNull(form)
        else reference.forms.getOrNull(form)?.parameter(argument, keyword)
}

/** Bounded lexical call context, deliberately declining ambiguous constructs instead of guessing. */
object SignatureHelp {
    private data class Frame(val bracket: Char, val offset: Int, val call: Boolean,
        val reference: BuiltinSignature?, var argument: Int = 0, var argumentStart: Int = offset + 1,
        var ambiguous: Boolean = false)
    private val keyword = Regex("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=(?!=)")
    private val complexString = Regex("(?:\\b(?:u8|u|U|L)?R\"|\\b(?:[fF][rR]?|[rR][fF])[\"'])")

    fun find(request: CompletionRequest): SignatureResult? {
        val code = request.code; val caret = request.caret
        if (Thread.currentThread().isInterrupted || code.length > 45000 || caret !in 0..code.length || caret != request.end ||
            complexString.containsMatchIn(code.substring(0, caret))) return null
        val ignored = BooleanArray(code.length)
        for (token in EditorFeatures.tokens(code, request.cpp)) {
            if (token.kind !in setOf("comment", "string", "preprocessor")) continue
            if (caret > token.start && (caret < token.end || (caret == token.end &&
                    (token.kind != "string" || !closedString(code.substring(token.start, token.end)))))) return null
            for (i in token.start until token.end) ignored[i] = true
        }
        val stack = mutableListOf<Frame>()
        var i = 0
        while (i < caret) {
            if (Thread.currentThread().isInterrupted) return null
            if (ignored[i]) { i++; continue }
            val ch = code[i]
            if (ch == '_' || ch.isLetter()) {
                val start = i
                while (i < caret && (code[i] == '_' || code[i].isLetterOrDigit())) i++
                if (!request.cpp && code.substring(start, i) in setOf("lambda", "for"))
                    stack.lastOrNull { it.call }?.ambiguous = true
                continue
            }
            when (ch) {
                '(', '[', '{' -> {
                    if (stack.size >= 128) return null
                    var name = ""
                    if (ch == '(') {
                        var end = i
                        while (end > 0 && code[end - 1].isWhitespace()) end--
                        var start = end
                        while (start > 0 && (code[start - 1].isLetterOrDigit() || code[start - 1] in "_:.>-")) start--
                        name = code.substring(start, end)
                        var priorEnd = start
                        while (priorEnd > 0 && code[priorEnd - 1].isWhitespace()) priorEnd--
                        var priorStart = priorEnd
                        while (priorStart > 0 && (code[priorStart - 1] == '_' || code[priorStart - 1].isLetterOrDigit())) priorStart--
                        val prior = code.substring(priorStart, priorEnd)
                        if (code.getOrNull(priorEnd - 1) in listOf('.', '>', ':')) name = "<member-call>"
                        if (!request.cpp && prior in setOf("def", "class")) name = "<declaration>"
                        if (name.isEmpty() && code.getOrNull(end - 1) in listOf(')', ']')) name = "<dynamic-call>"
                    }
                    stack.add(Frame(ch, i, ch == '(' && name.isNotEmpty(), BuiltinSignatures.find(name, request.cpp)))
                }
                ')', ']', '}' -> {
                    val last = stack.lastOrNull() ?: return null
                    if ("([{".indexOf(last.bracket) != ")]}".indexOf(ch)) return null
                    stack.removeAt(stack.lastIndex)
                }
                ',' -> stack.lastOrNull()?.let { it.argument++; it.argumentStart = i + 1 }
                '<', '>' -> if (request.cpp) stack.lastOrNull { it.call }?.ambiguous = true
                '*' -> if (!request.cpp && stack.lastOrNull()?.call == true &&
                    code.substring(stack.last().argumentStart, i).isBlank()) stack.last().ambiguous = true
            }
            i++
        }
        val frame = stack.lastOrNull { it.call } ?: return null
        val reference = frame.reference ?: return null
        if (frame.ambiguous) return null
        val argumentText = (frame.argumentStart until caret).map { if (ignored[it]) ' ' else code[it] }.joinToString("")
        val named = if (request.cpp) null else keyword.find(argumentText)?.groupValues?.get(1)
        return SignatureResult(request, reference, frame.offset, frame.argument, named)
    }

    private fun closedString(text: String): Boolean {
        if (text.startsWith("\"\"\"") || text.startsWith("'''")) return text.length >= 6 && text.endsWith(text.take(3))
        if (text.length < 2 || text.last() != text.first()) return false
        return text.dropLast(1).takeLastWhile { it == '\\' }.length % 2 == 0
    }
}

object SignatureKeys {
    fun dismiss(escape: Boolean, visible: Boolean, composing: Boolean, softKeyboard: Boolean, modified: Boolean) =
        escape && visible && !composing && !softKeyboard && !modified
}
