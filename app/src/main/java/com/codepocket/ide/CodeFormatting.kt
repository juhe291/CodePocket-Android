package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.security.MessageDigest

internal data class FormatOptions(val indent: Int = 4, val spaces: Boolean = true) {
    init { require(indent in listOf(2, 4, 8)) }
    fun style(): String = JSONObject().put("BasedOnStyle", "LLVM")
        .put("IndentWidth", indent).put("TabWidth", indent).put("ContinuationIndentWidth", indent)
        .put("UseTab", if (spaces) "Never" else "ForIndentation").put("ColumnLimit", 100)
        .put("SortIncludes", "Never").put("SortUsingDeclarations", "Never").put("ReflowComments", "Never")
        .put("BreakStringLiterals", false).put("RawStringFormats", JSONArray()).put("FixNamespaceComments", false)
        .put("InsertBraces", false).put("RemoveBracesLLVM", false).put("RemoveParentheses", "Leave")
        .put("RemoveSemicolon", false).put("QualifierAlignment", "Leave").put("Standard", "c++17").toString()
}

internal data class FormatResult(val text: String? = null, val start: Int = 0, val end: Int = 0, val error: String? = null) {
    val available get() = text != null
}

/** A separate transport instance binds only :formatting, never the running program or analysis service. */
internal class CodeFormattingClient(private val transport: SemanticTransport) : Closeable {
    fun format(source: EditorSnapshot, python: Boolean, options: FormatOptions): FormatResult = try {
        val payload = CodeFormatting.payload(source, python, options)
        CodeFormatting.decode(source.text, python, transport.analyze(payload))
    } catch (error: InterruptedException) { throw error }
    catch (_: Exception) { FormatResult(error = "transport") }
    override fun close() = transport.close()
}

internal object CodeFormatting {
    const val MAX_JSON = 450_000
    const val PYTHON = "autopep8 2.3.2"
    const val CPP = "clang-format 21.1.8"
    fun supported(path: String) = path.endsWith(".py", true) || CppSources.isSource(path)
    fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun boundary(text: String, at: Int) = at in 0..text.length &&
        !(at > 0 && at < text.length && text[at - 1].isHighSurrogate() && text[at].isLowSurrogate())
    private fun valid(text: String): Boolean {
        if (text.length > Workspace.MAX_CHARS || '\u0000' in text) return false
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (c.isHighSurrogate()) { if (i >= text.length || !text[i++].isLowSurrogate()) return false }
            else if (c.isLowSurrogate()) return false
        }
        return true
    }
    fun payload(source: EditorSnapshot, python: Boolean, options: FormatOptions): String {
        require(valid(source.text) && boundary(source.text, source.start) && boundary(source.text, source.end))
        return JSONObject().put("code", source.text).put("start", source.start).put("end", source.end)
            .put("python", python).put("indent", options.indent).put("spaces", options.spaces).toString()
            .also { require(it.length <= MAX_JSON) }
    }
    fun failure(error: String) = JSONObject().put("available", false).put("error", error).toString()
    fun response(source: String, python: Boolean, result: FormatResult): String {
        require(result.available && valid(result.text!!) && boundary(result.text, result.start) && boundary(result.text, result.end))
        return JSONObject().put("available", true).put("engine", if (python) PYTHON else CPP)
            .put("text", result.text).put("start", result.start).put("end", result.end).put("source", digest(source))
            .toString().also { require(it.length <= MAX_JSON) }
    }
    fun decode(source: String, python: Boolean, raw: String?): FormatResult = try {
        require(raw != null && raw.length <= MAX_JSON)
        val json = JSONObject(raw)
        if (!json.optBoolean("available")) FormatResult(error = json.optString("error", "unavailable").take(80))
        else {
            require(json.getString("engine") == if (python) PYTHON else CPP)
            require(json.getString("source") == digest(source))
            require(json.get("text") is String && json.get("start") is Int && json.get("end") is Int)
            val text = json.getString("text"); val start = json.getInt("start"); val end = json.getInt("end")
            require(valid(text) && boundary(text, start) && boundary(text, end))
            FormatResult(text, start, end)
        }
    } catch (_: Exception) { FormatResult(error = "invalid_response") }

    /** LSP uses UTF-16 columns. Rebuild once; neither service nor client writes the original document. */
    fun cppEdits(source: String, start: Int, end: Int, values: JSONArray): FormatResult {
        require(valid(source) && boundary(source, start) && boundary(source, end) && values.length() <= Workspace.MAX_CHARS)
        val lines = ArrayList<Int>().apply { add(0); source.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }
        fun offset(position: JSONObject): Int {
            require(position.get("line") is Int && position.get("character") is Int)
            val line = position.getInt("line"); val column = position.getInt("character")
            require(line in lines.indices && column >= 0)
            val from = lines[line]; var limit = if (line + 1 < lines.size) lines[line + 1] - 1 else source.length
            if (limit > from && source[limit - 1] == '\r') limit--
            require(column <= limit - from)
            return (from + column).also { require(boundary(source, it)) }
        }
        data class Edit(val from: Int, val to: Int, val text: String)
        val edits = (0 until values.length()).map { i ->
            val item = values.getJSONObject(i); val range = item.getJSONObject("range")
            Edit(offset(range.getJSONObject("start")), offset(range.getJSONObject("end")), item.getString("newText"))
                .also { require(it.from <= it.to && valid(it.text)) }
        }.sortedWith(compareBy<Edit> { it.from }.thenBy { it.to })
        var last: Edit? = null; var length = source.length.toLong()
        for (edit in edits) {
            last?.let { require(edit.from >= it.to && edit.from != it.from) }
            length += edit.text.length - (edit.to - edit.from); last = edit
        }
        require(length in 0..Workspace.MAX_CHARS.toLong())
        val result = StringBuilder(length.toInt()); var at = 0
        for (edit in edits) { result.append(source, at, edit.from).append(edit.text); at = edit.to }
        result.append(source, at, source.length)
        val text = result.toString(); require(valid(text))
        fun mapped(position: Int): Int {
            var delta = 0
            for (edit in edits) {
                if (position < edit.from) break
                if (start != end && position == maxOf(start, end) && position == edit.from && edit.from == edit.to) break
                if (position < edit.to) return (edit.from + delta + minOf(position - edit.from, edit.text.length))
                    .let { if (boundary(text, it)) it else it - 1 }
                delta += edit.text.length - (edit.to - edit.from)
            }
            return (position + delta).also { require(boundary(text, it)) }
        }
        return FormatResult(text, mapped(start), mapped(end))
    }
}
