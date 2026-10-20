package com.codepocket.ide.editor

import com.codepocket.ide.EditorFeatures

/** UTF-16 range. Source is always displayed; these are not LSP/type-checking claims. */
data class InlineIssue(val start: Int, val end: Int, val message: String, val source: String)
data class SyntaxReport(val issues: List<InlineIssue> = emptyList(), val status: String = "")
data class DecorationScan(val tokens: List<EditorFeatures.Token>, val issues: List<InlineIssue>)

/** Bounded lexical role hints and bracket-depth colours. Strings/comments are opaque. */
object EditorDecorations {
    private val identifiers = Regex("[\\p{L}_][\\p{L}\\p{N}_]*")
    private val templateTypes = setOf("vector", "map", "set", "pair", "tuple", "array", "deque", "list", "queue", "stack",
        "unordered_map", "unordered_set", "multimap", "multiset", "optional", "variant", "shared_ptr", "unique_ptr", "function", "basic_string", "priority_queue", "static_cast", "dynamic_cast", "reinterpret_cast", "const_cast", "template")
    fun scan(code: String, cpp: Boolean, lexical: List<EditorFeatures.Token>): DecorationScan {
        val occupied = BooleanArray(code.length)
        val opaque = BooleanArray(code.length)
        for (token in lexical) {
            for (i in token.start.coerceAtLeast(0) until token.end.coerceAtMost(code.length)) {
                occupied[i] = true
                if (token.kind in listOf("string", "comment", "preprocessor")) opaque[i] = true
            }
        }
        // Multi-line preprocessor directives and C++ raw literals must not create false bracket errors.
        if (cpp) {
            var continuation = false
            var lineStart = 0
            for (line in code.split('\n')) {
                val directive = continuation || line.trimStart().startsWith("#")
                if (directive) for (i in lineStart until lineStart + line.length) { opaque[i] = true; occupied[i] = true }
                continuation = directive && line.trimEnd().endsWith("\\")
                lineStart += line.length + 1
            }
            var from = 0
            while (true) {
                val start = code.indexOf("R\"", from)
                if (start < 0) break
                if (opaque[start]) { from = start + 2; continue }
                val open = code.indexOf('(', start + 2)
                if (open < 0 || open - start > 18) { from = start + 2; continue }
                val delimiter = code.substring(start + 2, open)
                if (delimiter.any { it.isWhitespace() || it in "()\\" }) { from = start + 2; continue }
                val close = code.indexOf(")$delimiter\"", open + 1)
                val end = if (close < 0) code.length else close + delimiter.length + 2
                for (i in start until end) { opaque[i] = true; occupied[i] = true }
                from = end
            }
        }
        val words = identifiers.findAll(code).filter { !opaque[it.range.first] }.toList()
        val declaredTypes = words.zipWithNext().filter { it.first.value in listOf("class", "struct", "enum") }.map { it.second.value }.toSet()
        val typePositions = lexical.filter { it.kind == "type" }.map { it.start }.toSet()
        val parameters = mutableSetOf<Int>()
        // Only declaration-shaped parameter lists, no speculative scope/type resolution.
        for ((index, word) in words.withIndex()) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            var open = word.range.last + 1
            while (open < code.length && code[open].isWhitespace()) open++
            val previous = words.getOrNull(index - 1)
            val declaration = if (!cpp) previous?.value == "def" else previous?.let {
                (it.range.first in typePositions || it.value in declaredTypes) &&
                    code.substring(it.range.last + 1, word.range.first).all { c -> c.isWhitespace() || c in "*&:" }
            } == true
            if (!declaration || code.getOrNull(open) != '(') continue
            var depth = 1; var end = open + 1
            while (end < code.length && depth > 0) {
                if (!opaque[end]) { if (code[end] == '(') depth++; if (code[end] == ')') depth-- }
                if (depth > 0) end++
            }
            if (depth != 0) continue
            var partStart = open + 1; var nested = 0; var wordCursor = index + 1
            for (i in open + 1..end) {
                if (i < end && !opaque[i]) { if (code[i] in "([{<") nested++; if (code[i] in ")]}>" && nested > 0) nested-- }
                if (i == end || (!opaque[i] && code[i] == ',' && nested == 0)) {
                    val segment = ArrayList<MatchResult>()
                    while (wordCursor < words.size && words[wordCursor].range.first < i) {
                        if (words[wordCursor].range.first >= partStart) segment.add(words[wordCursor])
                        wordCursor++
                    }
                    val beforeDefault = segment.takeWhile { code.substring(partStart, it.range.first).indexOf('=') < 0 }
                    val candidate = if (cpp) beforeDefault.lastOrNull { !occupied[it.range.first] && it.value !in declaredTypes }
                        else beforeDefault.firstOrNull()
                    candidate?.let { parameters.add(it.range.first) }
                    partStart = i + 1
                }
            }
        }
        val parameterNames = words.filter { it.range.first in parameters }.map { it.value }.toSet()
        val extra = ArrayList<EditorFeatures.Token>()
        for (word in words) {
            val at = word.range.first
            if (occupied[at]) continue
            val prefix = code.substring((at - 3).coerceAtLeast(0), at).trimEnd()
            val kind = when {
                word.value in declaredTypes -> "type"
                at in parameters || word.value in parameterNames -> "parameter"
                word.value.length > 1 && word.value.all { it.isUpperCase() || it.isDigit() || it == '_' } -> "constant"
                prefix.endsWith('.') || prefix.endsWith("->") -> "property"
                else -> "variable"
            }
            extra.add(EditorFeatures.Token(at, word.range.last + 1, kind))
        }
        val issues = ArrayList<InlineIssue>()
        val stack = ArrayList<Pair<Int, Char>>()
        fun issue(at: Int, message: String) { if (issues.size < 40) issues.add(InlineIssue(at, at + 1, message, "基础括号检查")) }
        val templateNames = templateTypes + declaredTypes
        fun angleStart(at: Int): Boolean {
            var end = at
            while (end > 0 && code[end - 1].isWhitespace()) end--
            var start = end
            while (start > 0 && (code[start - 1].isLetterOrDigit() || code[start - 1] == '_')) start--
            return code.substring(start, end) in templateNames
        }
        for (at in code.indices) {
            if (opaque[at]) continue
            val c = code[at]
            val open = c in "([{" || cpp && c == '<' && angleStart(at)
            val close = c in ")]}" || cpp && c == '>' && stack.lastOrNull()?.second == '<'
            if (open) {
                extra.add(EditorFeatures.Token(at, at + 1, "bracket${stack.size % 6}")); stack.add(at to c)
            } else if (close) {
                val expected = when(c) { ')' -> '('; ']' -> '['; '}' -> '{'; else -> '<' }
                if (stack.lastOrNull()?.second == expected) {
                    stack.removeAt(stack.lastIndex)
                    extra.add(EditorFeatures.Token(at, at + 1, "bracket${stack.size % 6}"))
                } else { issue(at, "多余或不匹配的括号「$c」") }
            }
        }
        for ((at, c) in stack) issue(at, "括号「$c」尚未闭合")
        return DecorationScan(extra, issues)
    }
}
