package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject

/** Validates real LSP edits before they can reach the existing Sora transaction. UTF-16 throughout. */
object CppSemanticProtocol {
    const val ENGINE = "clangd 21.1.8"
    fun boundary(code: String, at: Int) = at in 0..code.length &&
        !(at > 0 && at < code.length && code[at - 1].isHighSurrogate() && code[at].isLowSurrogate())
    fun position(code: String, offset: Int): JSONObject {
        require(boundary(code, offset))
        val lineStart = code.lastIndexOf('\n', offset - 1).let { it + 1 }
        return JSONObject().put("line", code.take(offset).count { it == '\n' }).put("character", offset - lineStart)
    }
    fun offset(code: String, position: JSONObject): Int {
        val line = position.getInt("line"); val column = position.getInt("character")
        require(line >= 0 && column >= 0)
        var start = 0
        repeat(line) { start = code.indexOf('\n', start).also { require(it >= 0) } + 1 }
        val end = code.indexOf('\n', start).let { if (it < 0) code.length else if (it > start && code[it - 1] == '\r') it - 1 else it }
        require(column <= end - start)
        return (start + column).also { require(boundary(code, it)) }
    }
    private fun identifier(ch: Char) = ch == '_' || ch.isLetterOrDigit() || Character.getType(ch) == Character.NON_SPACING_MARK.toInt()

    /** The pinned backend is configured without argument placeholders: only its final $0 stop is emitted. */
    fun snippet(text: String, format: Int): Pair<String, Int> {
        require(text.length <= 2048 && !text.contains('\u0000') && format in 1..2)
        if (format == 1) return text to text.length
        val out = StringBuilder(); var caret: Int? = null; var index = 0
        while (index < text.length) {
            when (val ch = text[index++]) {
                '\\' -> { require(index < text.length && text[index] in "\\$}"); out.append(text[index++]) }
                '$' -> {
                    require(caret == null)
                    index = when {
                        text.startsWith("0", index) -> index + 1
                        text.startsWith("{0}", index) -> index + 3
                        else -> throw IllegalArgumentException("Unsupported semantic snippet")
                    }
                    require(index == text.length || !text[index].isDigit())
                    caret = out.length
                }
                else -> out.append(ch)
            }
        }
        return out.toString() to (caret ?: out.length)
    }
    private fun document(value: Any?): String = when (value) {
        is String -> value
        is JSONObject -> value.optString("value")
        else -> ""
    }.take(600)

    fun decode(request: CompletionRequest, raw: String?, mode: String): SemanticAnalysis = try {
        require(request.cpp && request.code.length <= 45000 && request.caret == request.end && boundary(request.code, request.caret))
        require(raw != null && raw.length <= 128000)
        val envelope = JSONObject(raw)
        require(envelope.optString("engine") == ENGINE && envelope.optBoolean("available"))
        val reply = envelope.getJSONObject("response")
        require(!reply.has("error"))
        val result = reply.opt("result")
        if (result == null || result == JSONObject.NULL) SemanticAnalysis(true)
        else if (mode == "complete") completion(request, result)
        else { require(mode == "signature"); signature(request, result as JSONObject) }
    } catch (_: Exception) { SemanticAnalysis(false, error = "cpp_unavailable") }

    private fun completion(request: CompletionRequest, result: Any): SemanticAnalysis {
        val array = if (result is JSONArray) result else (result as JSONObject).getJSONArray("items")
        require(array.length() <= 100)
        val items = mutableListOf<CompletionItem>()
        val header = CppHeaderCompletion.context(request)
        var filtered = 0
        for (i in 0 until array.length()) {
            val parsed = runCatching {
                val item = array.getJSONObject(i)
                require(item.optJSONArray("additionalTextEdits")?.length().let { it == null || it == 0 })
                val edit = item.getJSONObject("textEdit")
                val range = edit.optJSONObject("replace") ?: edit.getJSONObject("range")
                val start = offset(request.code, range.getJSONObject("start"))
                var end = offset(request.code, range.getJSONObject("end"))
                require(start <= request.caret && end >= request.caret)
                if (header != null || item.optInt("kind") in setOf(17, 19)) {
                    val found = CppHeaderCompletion.item(requireNotNull(header), request, item, start, end)
                    if (found == null) filtered++
                    return@runCatching found
                }
                require(request.code.substring(start, end).all(::identifier))
                val prefix = request.code.substring(start, request.caret)
                val filter = item.optString("filterText", item.getString("label").trim())
                require(filter.length in 1..256 && filter.none { it == '\u0000' || it == '\n' || it == '\r' })
                if (!filter.startsWith(prefix)) { filtered++; return@runCatching null }
                if (end == request.caret) while (end < request.code.length && identifier(request.code[end])) end++
                val (insert, caret) = snippet(edit.getString("newText"), item.optInt("insertTextFormat", 1))
                require(insert.isNotEmpty() && insert.startsWith(prefix) && boundary(insert, caret))
                require(insert != request.code.substring(start, end) || caret != insert.length)
                val label = item.getString("label").trim().take(256)
                val kind = when (item.optInt("kind")) {
                    2, 3, 4 -> CompletionKind.FUNCTION
                    7, 8, 13, 22, 25 -> CompletionKind.TYPE
                    9 -> CompletionKind.MODULE
                    14 -> CompletionKind.KEYWORD
                    else -> CompletionKind.VARIABLE
                }
                CompletionItem(label, kind, (item.optString("detail").take(600) + "\n" + document(item.opt("documentation"))).trim(),
                    semanticInsertion = CompletionEdit(TextChange(start, end, insert, start + caret)))
            }.getOrNull()
            if (parsed != null) items.add(parsed)
            if (items.size == 30) break
        }
        if (items.isEmpty()) return if (array.length() == filtered) SemanticAnalysis(true) else SemanticAnalysis(false, error = "cpp_edits_unsupported")
        val first = items.first().semanticInsertion!!.change
        return SemanticAnalysis(true, CompletionBatch(request, first.start, first.end, items, ENGINE))
    }

    private fun signature(request: CompletionRequest, result: JSONObject): SemanticAnalysis {
        val values = result.getJSONArray("signatures"); require(values.length() <= 8)
        if (values.length() == 0) return SemanticAnalysis(true)
        val selected = result.optInt("activeSignature", 0); require(selected in 0 until values.length())
        val forms = mutableListOf<SignatureForm>(); val active = mutableListOf<Int?>()
        var name = ""; var description = ""
        for (i in listOf(selected) + (0 until values.length()).filter { it != selected }) {
            val item = values.getJSONObject(i); val label = item.getString("label")
            require(label.length <= 4096 && !label.contains('\u0000'))
            val opened = label.indexOf('('); require(opened in 1..256)
            val nextName = label.take(opened).trim()
            if (name.isEmpty()) { name = nextName; description = label.take(600) + "\n" + document(item.opt("documentation")) }
            if (name != nextName) continue
            val params = item.optJSONArray("parameters") ?: JSONArray(); require(params.length() <= 40)
            val parts = (0 until params.length()).map { at ->
                when (val value = params.getJSONObject(at).get("label")) {
                    is JSONArray -> { require(value.length() == 2); val start = value.getInt(0); val end = value.getInt(1)
                        require(start <= end && boundary(label, start) && boundary(label, end)); label.substring(start, end) }
                    is String -> value
                    else -> error("Invalid parameter label")
                }.also { require(it.length <= 512) }
            }
            forms.add(SignatureForm(parts, parts.indices.toList()))
            active.add((if (item.has("activeParameter")) item.optInt("activeParameter", -1) else result.optInt("activeParameter", -1)).takeIf { it in parts.indices })
        }
        require(forms.isNotEmpty())
        val opened = callOffset(request) ?: error("Ambiguous call")
        return SemanticAnalysis(true, signature = SignatureResult(request,
            BuiltinSignature(name, true, forms, description.take(1200), ENGINE, semantic = true), opened, 0, null, active))
    }

    private fun callOffset(request: CompletionRequest): Int? {
        val ignored = BooleanArray(request.code.length)
        EditorFeatures.tokens(request.code, true).filter { it.kind in setOf("string", "comment", "preprocessor") }
            .forEach { token -> for (i in token.start until token.end) ignored[i] = true }
        val stack = mutableListOf<Pair<Char, Int>>()
        for (i in 0 until request.caret) {
            if (ignored[i]) continue
            when (val ch = request.code[i]) {
                '(', '[', '{' -> { require(stack.size < 128); stack.add(ch to i) }
                ')', ']', '}' -> { val expected = when (ch) { ')' -> '('; ']' -> '['; else -> '{' }
                    require(stack.isNotEmpty() && stack.last().first == expected); stack.removeAt(stack.lastIndex) }
            }
        }
        return stack.lastOrNull { it.first == '(' }?.second
    }
}
