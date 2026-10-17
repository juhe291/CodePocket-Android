package com.codepocket.ide

import android.graphics.Color
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/** Lexical highlighting, not a full semantic language server. */
object EditorFeatures {
    private val cppWords = "int long short double float bool void char auto return for while do if else break continue class struct const using namespace public private protected template typename new delete true false include switch case default static nullptr virtual override final constexpr noexcept try catch throw enum union unsigned signed typedef sizeof volatile mutable inline friend this operator decltype".split(' ')
    private val cppTypes = "int double float bool void char long short string vector map set queue stack pair deque unordered_map unordered_set size_t".split(' ')
    private val pythonWords = "def class return for while if elif else break continue import from as with try except finally raise lambda in and or not True False None pass yield async await global nonlocal assert del is".split(' ')
    private val cppFunctions = "main cout cin endl printf scanf size push_back emplace_back begin end sort reverse max min swap".split(' ')
    private val pythonFunctions = "print input range len int str float list dict set tuple enumerate zip map filter sum min max sorted open type isinstance".split(' ')
    private val strings = "\\\"(?:\\\\.|[^\\\"\\\\\\n])*\\\"?|'(?:\\\\.|[^'\\\\\\n])*'?"
    private val numeric = "\\b(?:0[xX][0-9a-fA-F]+|0[bB][01]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)[uUlLfF]*\\b"
    private val cppPattern = Regex("(?m)//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)|^[ \\t]*#[^\\n]*|$strings|$numeric|\\b[A-Za-z_][A-Za-z_0-9]*\\b")
    private val pythonPattern = Regex("(?m)#[^\\n]*|\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)|'''[\\s\\S]*?(?:'''|\\z)|$strings|$numeric|\\b[A-Za-z_][A-Za-z_0-9]*\\b")
    private val token = Regex("[A-Za-z_][A-Za-z0-9_]*$")
    private val word = Regex("[A-Za-z_][A-Za-z0-9_]*")

    data class Token(val start: Int, val end: Int, val kind: String)

    fun tokens(code: String, cpp: Boolean): List<Token> = (if (cpp) cppPattern else pythonPattern).findAll(code).mapNotNull { match ->
        val value = match.value.trimStart()
        val kind = when {
            (cpp && (value.startsWith("//") || value.startsWith("/*"))) || (!cpp && value.startsWith("#")) -> "comment"
            cpp && value.startsWith("#") -> "preprocessor"
            value.startsWith("\"") || value.startsWith("'") -> "string"
            value.firstOrNull()?.isDigit() == true -> "number"
            cpp && value in cppTypes -> "type"
            value in (if (cpp) cppWords else pythonWords) -> "keyword"
            value in (if (cpp) cppFunctions else pythonFunctions) || followedByCall(code, match.range.last + 1) -> "function"
            else -> return@mapNotNull null
        }
        Token(match.range.first, match.range.last + 1, kind)
    }.toList()

    private fun followedByCall(code: String, end: Int): Boolean {
        var next = end
        while (next < code.length && code[next].isWhitespace()) next++
        return code.getOrNull(next) == '('
    }

    fun matchingBrackets(code: String, caret: Int, cpp: Boolean): Pair<Int, Int>? {
        if (code.length > 45000) return null
        val ignored = BooleanArray(code.length)
        tokens(code, cpp).filter { it.kind in setOf("string", "comment", "preprocessor") }.forEach { token ->
            for (i in token.start until token.end) ignored[i] = true
        }
        val pairs = "()[]{}"
        val index = listOf(caret - 1, caret).firstOrNull { it in code.indices && !ignored[it] && code[it] in pairs } ?: return null
        val tokenIndex = pairs.indexOf(code[index])
        val forward = tokenIndex % 2 == 0
        val stack = mutableListOf<Char>()
        val indices = if (forward) index until code.length else index downTo 0
        for (i in indices) {
            if (ignored[i] || code[i] !in pairs) continue
            val part = pairs.indexOf(code[i])
            if ((part % 2 == 0) == forward) stack.add(code[i])
            else {
                if (stack.isEmpty() || (pairs.indexOf(stack.removeAt(stack.lastIndex)) xor part) != 1) return null
                if (stack.isEmpty()) return index to i
            }
        }
        return null
    }

    fun prefix(code: String, caret: Int): String = token.find(code.substring(0, caret.coerceIn(0, code.length)))?.value.orEmpty()

    fun completionWords(cpp: Boolean): List<String> = if (cpp)
        (cppWords + cppTypes + cppFunctions + listOf("std", "iostream", "algorithm")).distinct()
        else (pythonWords + pythonFunctions).distinct()

    fun completionKind(value: String, cpp: Boolean): CompletionKind = when {
        cpp && value in cppTypes -> CompletionKind.TYPE
        value in (if (cpp) cppWords else pythonWords) -> CompletionKind.KEYWORD
        value in completionWords(cpp) -> CompletionKind.BUILTIN
        else -> CompletionKind.IDENTIFIER
    }

    fun suggestions(code: String, caret: Int, cpp: Boolean): List<String> {
        val partial = prefix(code, caret)
        if (partial.isBlank()) return emptyList()
        val common = if (cpp) cppWords + cppTypes + cppFunctions + listOf("#include", "std", "iostream", "algorithm")
                     else pythonWords + pythonFunctions
        val identifiers = if (code.length <= 45000) word.findAll(code).map { it.value }.filter { it.length >= 3 }.toList() else emptyList()
        return (common + identifiers).distinct().filter { it.startsWith(partial) && it != partial }
            .sortedWith(compareBy<String> { !common.contains(it) }.thenBy { it.length }).take(6)
    }

    fun colorize(editable: Editable?, cpp: Boolean, dark: Boolean) {
        if (editable == null) return
        val length = editable.length
        editable.getSpans(0, length, ForegroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }
        if (length > 45000) return
        val code = editable.toString()
        val keywordColor = Color.parseColor(if (dark) "#569CD6" else "#0000FF")
        val typeColor = Color.parseColor(if (dark) "#4EC9B0" else "#267F99")
        val functionColor = Color.parseColor(if (dark) "#DCDCAA" else "#795E26")
        val stringColor = Color.parseColor(if (dark) "#CE9178" else "#A31515")
        val numberColor = Color.parseColor(if (dark) "#B5CEA8" else "#098658")
        val commentColor = Color.parseColor(if (dark) "#6A9955" else "#008000")
        val preprocessorColor = Color.parseColor(if (dark) "#C586C0" else "#AF00DB")
        for (token in tokens(code, cpp)) {
            val selectedColor = when (token.kind) {
                "comment" -> commentColor
                "preprocessor" -> preprocessorColor
                "string" -> stringColor
                "number" -> numberColor
                "type" -> typeColor
                "keyword" -> keywordColor
                "function" -> functionColor
                else -> null
            }
            if (selectedColor != null) editable.setSpan(ForegroundColorSpan(selectedColor),
                token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
