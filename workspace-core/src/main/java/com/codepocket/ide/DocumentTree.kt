package com.codepocket.ide

import java.io.File
import java.security.MessageDigest

data class DocumentNode(val id: String, val name: String, val directory: Boolean, val flags: Int = 0)

/** Provider-independent operations. IDs are opaque, never interpreted as filesystem paths. */
interface DocumentGateway {
    val root: DocumentNode
    fun children(id: String): List<DocumentNode>
    fun read(id: String): String
    fun readBytes(id: String): ByteArray = read(id).toByteArray(Charsets.UTF_8)
    fun write(id: String, text: String)
    fun create(parent: String, name: String, directory: Boolean): DocumentNode
    fun rename(id: String, name: String): DocumentNode
    fun move(id: String, oldParent: String, newParent: String): DocumentNode
    fun delete(id: String)
}

/** Private mirror for the existing File-based editor and Python imports; all UI mutations write through. */
class DocumentTree(private val gateway: DocumentGateway, private val recovery: File) {
    private val nodes = linkedMapOf("" to gateway.root)
    private val versions = mutableMapOf<String, String>()
    var warning: String = ""
        private set
    fun node(path: String): DocumentNode = nodes[path] ?: error("外部文件不存在，请重新载入工作区")
    fun pathId(path: String) = node(path).id
    /** Enumerate the live provider, including additions/removals since the editor mirror was made. */
    fun pythonSources(): Map<String, String> = PythonSources.provider(gateway)
    fun copyForRun(destination: File) {
        require(destination.mkdirs()) { "无法创建独立运行快照" }
        var total = 0
        nodes.forEach { (path, entry) ->
            if (path.isNotEmpty()) {
                val file = File(destination, path)
                if (entry.directory) require(file.isDirectory || file.mkdirs())
                else {
                    val bytes = gateway.readBytes(entry.id)
                    total += bytes.size
                    require(bytes.size <= 1_000_000 && total <= 8_000_000) { "运行快照最多 8 MB，单个资源最多 1 MB，请选择更小的项目" }
                    require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                    file.writeBytes(bytes)
                }
            }
        }
    }
    fun scan(mirror: File) {
        val found = linkedMapOf("" to gateway.root)
        var count = 0
        var total = 0
        val visited = mutableSetOf(gateway.root.id)
        fun visit(parent: String, depth: Int) {
            require(depth <= 20) { "目录超过 20 层，请选择更小的项目目录" }
            val siblings = mutableSetOf<String>()
            gateway.children(found.getValue(parent).id).forEach { child ->
                require(++count <= 1000) { "工作区超过 1000 项，请选择更小的项目目录" }
                require(visited.add(child.id)) { "提供方返回重复文档或循环目录，已停止同步" }
                Workspace.validateName(child.name)
                require(siblings.add(child.name)) { "提供方返回重名文档，已停止同步以保护文件" }
                val path = if (parent.isEmpty()) child.name else "$parent/${child.name}"
                found[path] = child
                val local = File(mirror, path)
                if (child.directory) {
                    require(local.isDirectory || local.mkdirs()) { "不能创建运行镜像" }
                    visit(path, depth + 1)
                } else {
                    require(local.isFile || local.createNewFile()) { "不能创建运行镜像" }
                    // Only source modules are preloaded. Other text is read on demand, on the IO queue.
                    if (local.extension.lowercase() in setOf("py", "cpp", "h", "hpp")) {
                        try {
                            val text = gateway.read(child.id)
                            total += text.length
                            require(total <= 2_000_000) { "源码总量过大，请缩小工作区" }
                            local.writeText(text)
                            versions[path] = digest(text)
                        } catch (e: Exception) {
                            // Permission/provider failures must not silently turn a real source file into an empty file.
                            if (e is SecurityException || e is java.io.IOException || local.extension.lowercase() == "py") throw e
                            warning = "部分文件未载入：${child.name}（${e.message}）"
                        }
                    }
                }
            }
        }
        visit("", 0)
        nodes.clear(); nodes.putAll(found)
    }
    fun children(path: String) = nodes.keys.filter { it.isNotEmpty() && it.substringBeforeLast('/', "") == path }
    fun read(path: String): String = gateway.read(node(path).id).also { versions[path] = digest(it) }
    fun write(path: String, text: String, expectedBase: String? = null) {
        val entry = node(path)
        require(!entry.directory) { "不能写入目录" }
        val original = gateway.read(entry.id)
        require((expectedBase == null || expectedBase == digest(original)) &&
            (versions[path] == null || versions[path] == digest(original))) {
            "外部文件已被其他应用修改。草稿已保留，请重新打开并比较，或导出为新文件。"
        }
        require(recovery.isDirectory || recovery.mkdirs()) { "无法建立保存恢复目录" }
        // Some SAF providers truncate before writing and cannot atomically replace a document.
        // Retain the original even if the provider partially writes or loses its permission.
        java.io.FileOutputStream(File(recovery, digest(entry.id) + ".before.txt")).use {
            it.write(original.toByteArray(Charsets.UTF_8)); it.fd.sync()
        }
        File(recovery, digest(entry.id) + ".meta.json").writeText(org.json.JSONObject()
            .put("path", path).put("project", gateway.root.name).toString())
        gateway.write(entry.id, text)
        require(gateway.read(entry.id) == text) { "提供方写入后校验失败；原文和草稿已保留" }
        versions[path] = digest(text)
    }
    fun create(parent: String, name: String, directory: Boolean): String {
        Workspace.validateName(name)
        val path = if (parent.isEmpty()) name else "$parent/$name"
        require(path !in nodes) { "同名文件或文件夹已存在" }
        val entry = gateway.create(node(parent).id, name, directory)
        require(entry.name == name) { "提供方改变了名称，请重新载入确认创建结果" }
        nodes[path] = entry
        if (!directory) versions[path] = digest("")
        return path
    }
    fun relocate(path: String, newParent: String, newName: String, moving: Boolean): String {
        Workspace.validateName(newName)
        require(path.isNotEmpty()) { "不能移动或重命名工作区根目录" }
        require(newParent != path && !newParent.startsWith("$path/")) { "不能移动到自身目录" }
        val target = if (newParent.isEmpty()) newName else "$newParent/$newName"
        require(target !in nodes) { "目标名称已存在" }
        val entry = node(path)
        val parent = path.substringBeforeLast('/', "")
        val result = if (moving) gateway.move(entry.id, node(parent).id, node(newParent).id)
            else gateway.rename(entry.id, newName)
        require(result.name == newName) { "提供方改变了名称，请重新载入确认结果" }
        val old = nodes.keys.filter { it == path || it.startsWith("$path/") }
        val moved = old.associate { p -> target + p.removePrefix(path) to nodes.getValue(p) }
        val oldVersions = old.associateWith { versions[it] }
        old.forEach { nodes.remove(it); versions.remove(it) }
        nodes.putAll(moved); nodes[target] = result
        oldVersions.forEach { (p, value) -> if (value != null) versions[target + p.removePrefix(path)] = value }
        // Providers may change descendant IDs on a directory move/rename. Refresh IDs before another write.
        if (entry.directory) {
            val visited = mutableSetOf(result.id)
            var refreshed = 0
            fun refresh(p: String, depth: Int = 0) {
                require(depth <= 20) { "目录过深，请重新载入工作区" }
                gateway.children(node(p).id).forEach { child ->
                    require(++refreshed <= 1000 && visited.add(child.id)) { "目录过大或循环，请重新载入工作区" }
                    Workspace.validateName(child.name)
                    val sub = "$p/${child.name}"
                    nodes[sub] = child
                    if (child.directory) refresh(sub, depth + 1)
                }
            }
            refresh(target)
        }
        return target
    }
    fun delete(path: String) {
        require(path.isNotEmpty()) { "不能删除工作区根目录" }
        gateway.delete(node(path).id)
        nodes.keys.removeAll { it == path || it.startsWith("$path/") }
        versions.keys.removeAll { it == path || it.startsWith("$path/") }
    }
    companion object {
        fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
