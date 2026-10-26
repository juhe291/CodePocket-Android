package com.codepocket.ide

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class Draft(val key: String, val path: String, val text: String, val baseHash: String? = null)

/** Small durable recovery files, independent from whether a provider write succeeds. Access on WorkspaceIo. */
class DraftStore(private val directory: File, private val replace: (File, File) -> Unit) {
    private fun location(key: String) = File(directory, DocumentTree.digest(key) + ".json")
    @Synchronized fun put(key: String, path: String, text: String, baseHash: String? = null) {
        require(text.length <= Workspace.MAX_CHARS)
        require(directory.isDirectory || directory.mkdirs()) { "无法保存恢复草稿" }
        val target = location(key)
        val temp = File.createTempFile("draft-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { out ->
                out.write(JSONObject().put("key", key).put("path", path).put("text", text).put("baseHash", baseHash).toString().toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            replace(temp, target)
        } finally { temp.delete() }
    }
    @Synchronized fun read(key: String): Draft? {
        val file = location(key)
        if (!file.exists()) return null
        require(file.length() <= 2_000_000) { "恢复草稿损坏，请导出恢复目录后处理" }
        val data = JSONObject(file.readText())
        require(data.getString("key") == key) { "恢复草稿身份不匹配" }
        return Draft(key, data.getString("path"), data.getString("text"), if (data.isNull("baseHash")) null else data.getString("baseHash"))
    }
    @Synchronized fun removeIfMatches(key: String, text: String) {
        if (read(key)?.text == text) require(location(key).delete()) { "文件已保存，但无法清理恢复草稿" }
    }
    /** One consistent set, excluding backup/export entries and drafts from other projects. */
    @Synchronized fun analysisTexts(keys: Map<String, String>): Map<String, String> = buildMap {
        keys.forEach { (path, key) -> read(key)?.let { draft ->
            require(draft.path == path) { "恢复草稿路径与会话不符" }
            put(path, draft.text)
        } }
    }
    @Synchronized fun list(prefix: String): List<Draft> = directory.listFiles()?.filter { it.extension == "json" }
        ?.sortedByDescending { it.lastModified() }?.mapNotNull { file ->
            runCatching {
                require(file.length() <= 2_000_000)
                val data = JSONObject(file.readText())
                read(data.getString("key"))?.takeIf { it.key.startsWith(prefix) }
            }.getOrNull()
        }.orEmpty()
}
