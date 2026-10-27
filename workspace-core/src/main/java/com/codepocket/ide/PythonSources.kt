package com.codepocket.ide

import java.io.File

/** Bounded source-only reads, shared by local projects and live SAF providers. No writes. */
object PythonSources {
    const val MAX_FILES = 128
    const val MAX_UNITS = 1_000_000
    private val excluded = setOf(".git", ".gradle", "__pycache__")
    fun isSource(path: String) = path.endsWith(".py") || path.endsWith(".pyi")
    private class Scan {
        val text = linkedMapOf<String, String>()
        var entries = 0
        var units = 0
        val deadline = System.nanoTime() + 5_000_000_000L
        fun check() {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            require(System.nanoTime() <= deadline) { "项目分析读取超时" }
        }
        fun entry(depth: Int) {
            check()
            require(depth <= 20 && ++entries <= 1000) { "项目分析目录过大或过深" }
        }
        fun add(path: String, value: String) {
            check()
            units += value.length
            require(value.length <= Workspace.MAX_CHARS && units <= MAX_UNITS && text.size < MAX_FILES) { "项目分析源码超过限制" }
            require(!value.contains('\u0000')) { "项目分析只接受 UTF-8 源码" }
            text[path] = value.removePrefix("\uFEFF")
        }
    }
    fun local(root: File, accepts: (String) -> Boolean = ::isSource): Map<String, String> {
        val scan = Scan()
        fun visit(folder: File, prefix: String, depth: Int) {
            scan.check()
            val children = folder.listFiles() ?: error("无法读取项目分析目录")
            children.sortedBy { it.name }.forEach { child ->
                scan.entry(depth)
                if (child.name in excluded) return@forEach
                require(child.canonicalFile == child.absoluteFile) { "项目分析不跟随符号链接" }
                val path = prefix + child.name
                if (child.isDirectory) visit(child, "$path/", depth + 1)
                else if (accepts(path)) {
                    require(child.isFile && child.length() <= 1_000_000) { "项目分析源码过大或已移除" }
                    val value = child.inputStream().use { stream -> Workspace.utf8Reader(stream).use { Workspace.readLimited(it, Workspace.MAX_CHARS) } }
                    scan.add(path, value)
                }
            }
        }
        require(root.isDirectory && root.canonicalFile == root.absoluteFile)
        visit(root, "", 0)
        return scan.text.toMap()
    }
    fun provider(gateway: DocumentGateway, accepts: (String) -> Boolean = ::isSource): Map<String, String> {
        val scan = Scan()
        val visited = mutableSetOf(gateway.root.id)
        fun visit(id: String, prefix: String, depth: Int) {
            scan.check()
            val names = mutableSetOf<String>()
            gateway.children(id).sortedBy { it.name }.forEach { node ->
                scan.entry(depth)
                Workspace.validateName(node.name)
                require(names.add(node.name) && visited.add(node.id)) { "项目分析遇到重名、重复文档或循环目录" }
                if (node.name in excluded) return@forEach
                val path = prefix + node.name
                if (node.directory) visit(node.id, "$path/", depth + 1)
                else if (accepts(path)) scan.add(path, gateway.read(node.id))
            }
        }
        visit(gateway.root.id, "", 0)
        return scan.text.toMap()
    }
}
