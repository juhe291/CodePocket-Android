package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File

/** One trusted root/generation and one active document. No project commands, plugins or code execution. */
internal class CppLspEngine(process: AnalysisProcess, private val root: File, flags: List<String>, private val formattingOnly: Boolean = false) : Closeable {
    private val connection = CppLspConnection(process, if (formattingOnly) 8_000_000 else 2_000_000)
    private var activeUri: String? = null
    private var preamble = ""
    private var version = 0
    private var formattingSupported = false
    init {
        try {
            val result = connection.request("initialize", JSONObject().put("processId", JSONObject.NULL).put("rootUri", root.toURI().toASCIIString())
                .put("clientInfo", JSONObject().put("name", "CodePocket").put("version", "0.2.0"))
                .put("capabilities", JSONObject().put("general", JSONObject().put("positionEncodings", JSONArray().put("utf-16")))
                    .put("textDocument", JSONObject().put("completion", JSONObject().put("completionItem", JSONObject().put("snippetSupport", true).put("insertReplaceSupport", true).put("documentationFormat", JSONArray().put("plaintext"))))
                        .put("signatureHelp", JSONObject().put("signatureInformation", JSONObject().put("documentationFormat", JSONArray().put("plaintext")).put("parameterInformation", JSONObject().put("labelOffsetSupport", true))))))
                .put("initializationOptions", JSONObject().put("fallbackFlags", JSONArray(flags))))
                .getJSONObject("result")
            require(result.getJSONObject("serverInfo").getString("version").startsWith("clangd version 21.1.8"))
            val capabilities = result.getJSONObject("capabilities")
            require(capabilities.optString("positionEncoding", "utf-16") == "utf-16")
            formattingSupported = capabilities.optBoolean("documentFormattingProvider", false)
            connection.notify("initialized", JSONObject())
        } catch (error: Exception) { connection.close(); throw error }
    }
    fun analyze(code: String, caret: Int, path: String, mode: String, cancelled: () -> Boolean): String {
        require(!formattingOnly)
        require(code.length <= 45000 && CppSources.isSource(path) && mode in setOf("complete", "signature"))
        val uri = synchronize(code, path)
        // A current parser may still use a stale preamble immediately after didChange. Wait for
        // this document version's completed parse, including changes to #include directives.
        connection.awaitParsed(uri, version, cancelled)
        val reply = connection.request(if (mode == "complete") "textDocument/completion" else "textDocument/signatureHelp",
            JSONObject().put("textDocument", JSONObject().put("uri", uri)).put("position", CppSemanticProtocol.position(code, caret)), cancelled = cancelled)
        val compact = compact(reply, mode)
        return JSONObject().put("engine", CppSemanticProtocol.ENGINE).put("available", true).put("response", compact).toString().also { require(it.length <= 128000) }
    }
    /** clangd formats its draft text without a parsed AST or project include inputs. */
    fun format(code: String, path: String, cancelled: () -> Boolean): JSONArray {
        require(formattingOnly && formattingSupported && code.length <= Workspace.MAX_CHARS && CppSources.isSource(path))
        val uri = synchronize(code, path)
        val reply = connection.request("textDocument/formatting", JSONObject()
            .put("textDocument", JSONObject().put("uri", uri))
            .put("options", JSONObject().put("tabSize", 4).put("insertSpaces", true)), cancelled = cancelled)
        require(!reply.has("error")) { "C++ formatting backend failed" }
        return reply.getJSONArray("result")
    }
    private fun synchronize(code: String, path: String): String {
        require(!path.startsWith('/') && !path.contains('\\') && path.split('/').none { it.isEmpty() || it == "." || it == ".." })
        val file = File(root, path).canonicalFile; require(file.path.startsWith(root.path + File.separator) && file.isFile)
        val uri = file.toURI().toASCIIString(); require(version < Int.MAX_VALUE)
        val nextPreamble = preambleText(code)
        if (activeUri != uri || preamble != nextPreamble) {
            activeUri?.let { connection.notify("textDocument/didClose", JSONObject().put("textDocument", JSONObject().put("uri", it))) }
            activeUri = uri; preamble = nextPreamble; version++
            connection.notify("textDocument/didOpen", JSONObject().put("textDocument", JSONObject().put("uri", uri).put("languageId", "cpp").put("version", version).put("text", code)))
        } else {
            version++
            connection.notify("textDocument/didChange", JSONObject().put("textDocument", JSONObject().put("uri", uri).put("version", version)).put("wantDiagnostics", true).put("contentChanges", JSONArray().put(JSONObject().put("text", code))))
        }
        return uri
    }
    private fun compact(reply: JSONObject, mode: String): JSONObject {
        if (reply.has("error")) return reply
        val result = reply.opt("result")
        if (result == null || result == JSONObject.NULL) return reply
        if (mode == "complete") {
            val values = if (result is JSONArray) result else (result as JSONObject).getJSONArray("items")
            require(values.length() <= 100)
            val items = JSONArray()
            for (i in 0 until minOf(30, values.length())) {
                val value = values.getJSONObject(i)
                val item = JSONObject(value.toString())
                item.remove("data")
                for (key in listOf("detail", "label", "filterText")) if (item.has(key)) item.put(key, item.getString(key).take(600))
                val doc = item.opt("documentation")
                item.put("documentation", when (doc) { is JSONObject -> doc.optString("value").take(600); is String -> doc.take(600); else -> "" })
                items.put(item)
            }
            reply.put("result", JSONObject().put("items", items).put("isIncomplete", false))
        }
        return reply
    }
    override fun close() = connection.close()

    companion object {
        // clangd can serve completion from an earlier preamble even after diagnostics catch up.
        // Reopen only when the initial comments/directives change; ordinary typing keeps its AST.
        internal fun preambleText(code: String): String {
            var i = 0
            while (i < code.length) {
                when {
                    code[i].isWhitespace() -> i++
                    code.startsWith("//", i) -> { val end = code.indexOf('\n', i); i = if (end < 0) code.length else end + 1 }
                    code.startsWith("/*", i) -> { val end = code.indexOf("*/", i + 2); i = if (end < 0) code.length else end + 2 }
                    code[i] == '#' -> {
                        do {
                            val end = code.indexOf('\n', i)
                            if (end < 0) { i = code.length; break }
                            val continued = code.substring(i, end).trimEnd('\r').endsWith('\\')
                            i = end + 1
                        } while (continued && i < code.length)
                    }
                    else -> return code.substring(0, i)
                }
            }
            return code
        }
    }
}
