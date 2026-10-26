package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class EditorViewState(
    val start: Int = 0, val end: Int = 0, val scrollX: Int = 0, val scrollY: Int = 0,
    val focus: String = "editor", val inputStart: Int = 0, val inputEnd: Int = 0
) {
    fun clamp(length: Int, inputLength: Int) = copy(
        start = start.coerceIn(0, length), end = end.coerceIn(0, length),
        scrollX = scrollX.coerceAtLeast(0), scrollY = scrollY.coerceAtLeast(0),
        inputStart = inputStart.coerceIn(0, inputLength), inputEnd = inputEnd.coerceIn(0, inputLength),
        focus = when (focus) { "stdin", "terminal" -> focus; else -> "editor" }
    )
}

data class FileSession(val id: String, var path: String, var stdin: String = "", var view: EditorViewState = EditorViewState())

/** IDs survive in-app rename/move. Workspace identity is separate from the display path. */
class EditorSessions(val workspaceId: String, json: String = "[]") {
    private val files = linkedMapOf<String, FileSession>()
    init {
        runCatching { JSONArray(json) }.getOrNull()?.let { array ->
            for (i in 0 until array.length()) runCatching {
                val item = array.getJSONObject(i)
                val path = item.getString("path")
                if (validPath(path)) files[path] = FileSession(item.getString("id"), path,
                    item.optString("stdin").take(20000), EditorViewState(
                        item.optInt("start"), item.optInt("end"), item.optInt("x"), item.optInt("y"),
                        item.optString("focus", "editor"), item.optInt("inputStart"), item.optInt("inputEnd")))
            }
        }
    }
    fun file(path: String): FileSession {
        require(validPath(path)) { "非法会话路径" }
        return files.getOrPut(path) { FileSession(UUID.randomUUID().toString(), path) }
    }
    fun key(path: String) = workspaceId + ":" + file(path).id
    /** Copy on the UI thread; analysis workers must never traverse mutable sessions. */
    fun analysisKeys(): Map<String, String> = files.values.associate { it.path to "$workspaceId:${it.id}" }
    fun rebase(old: String, new: String) {
        val moving = files.keys.filter { it == old || it.startsWith("$old/") }
        require(moving.map { new + it.removePrefix(old) }.none { it in files && it !in moving }) { "目标会话已存在" }
        moving.map { files.remove(it)!! }.forEach { session ->
            session.path = new + session.path.removePrefix(old)
            files[session.path] = session
        }
    }
    fun removeTree(path: String) { files.keys.removeAll { it == path || it.startsWith("$path/") } }
    fun toJson(): String = JSONArray().apply {
        files.values.forEach { f -> put(JSONObject().apply {
            put("id", f.id); put("path", f.path); put("stdin", f.stdin)
            put("start", f.view.start); put("end", f.view.end); put("x", f.view.scrollX); put("y", f.view.scrollY)
            put("focus", f.view.focus); put("inputStart", f.view.inputStart); put("inputEnd", f.view.inputEnd)
        }) }
    }.toString()
    companion object {
        private fun validPath(path: String) = path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') &&
            path.split('/').none { it == "." || it == ".." || it.isEmpty() }
    }
}

data class ImeLayout(val showPanel: Boolean, val showDetails: Boolean, val compactInput: Boolean)
object ImeLayoutPolicy {
    fun resolve(ime: Boolean, inputFocused: Boolean, heightDp: Int, panelVisible: Boolean, collapsePanel: Boolean = true) = ImeLayout(
        showPanel = panelVisible && (!ime || inputFocused || !collapsePanel),
        showDetails = !ime && heightDp >= 360,
        compactInput = ime && inputFocused
    )
}
