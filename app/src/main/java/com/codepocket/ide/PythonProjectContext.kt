package com.codepocket.ide

import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Used only on the serial semantic worker. Sources are immutable while an analysis reads them. */
class PythonProjectContext(private val directory: File) {
    class Snapshot internal constructor(val location: SemanticLocation, private val current: () -> Boolean) {
        fun isCurrent(): Boolean = try { current() } catch (e: InterruptedException) { throw e } catch (_: Exception) { false }
    }
    fun capture(workspace: Workspace, drafts: DraftStore, keys: Map<String, String>, path: String, code: String): Snapshot {
        require(path.endsWith(".py") && code.length <= 45000)
        val projectKeys = keys.filterKeys { PythonSources.isSource(it) && it != path }
        val source = workspace.pythonSources()
        require(path in source) { "当前文件已从项目移除" }
        val unsaved = drafts.analysisTexts(projectKeys).filterKeys { it in source }
        val content = source + unsaved + (path to code)
        require(content.size <= PythonSources.MAX_FILES && content.values.sumOf { it.length } <= PythonSources.MAX_UNITS) { "项目分析快照超过限制" }
        val hash = digest(workspace.identity, content)
        require(directory.isDirectory || directory.mkdirs())
        require(directory.canonicalFile == directory.absoluteFile)
        val destination = File(directory, hash)
        if (!destination.isDirectory || PythonSources.local(destination) != content) {
            if (destination.exists()) remove(destination)
            val staging = File(directory, ".pending-${UUID.randomUUID()}")
            require(staging.mkdir())
            try {
                content.forEach { (relative, text) ->
                    checkInterrupted()
                    require(!relative.startsWith('/') && !relative.contains('\\') && relative.split('/').none { it.isEmpty() || it == "." || it == ".." })
                    val target = File(staging, relative)
                    require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                    require(target.createNewFile()) { "分析快照存在重名文件" }
                    target.writeText(text)
                }
                require(staging.renameTo(destination)) { "无法提交分析快照" }
            } finally { if (staging.exists()) remove(staging) }
        }
        // Never change a published directory. Reuse only byte-for-byte equal contents.
        destination.setLastModified(System.currentTimeMillis())
        directory.listFiles()?.filter { it.isDirectory && it != destination }?.sortedByDescending { it.lastModified() }
            ?.drop(2)?.forEach(::remove)
        return Snapshot(SemanticLocation(destination.path, path, true)) {
            checkInterrupted()
            workspace.pythonSources() == source &&
                drafts.analysisTexts(projectKeys).filterKeys { it in source } == unsaved &&
                PythonSources.local(destination) == content
        }
    }
    private fun checkInterrupted() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun digest(identity: String, content: Map<String, String>): String {
        val hash = MessageDigest.getInstance("SHA-256")
        fun part(text: String) { val bytes = text.toByteArray(Charsets.UTF_8); hash.update(bytes.size.toString().toByteArray()); hash.update(0.toByte()); hash.update(bytes) }
        part(identity)
        content.toSortedMap().forEach { (path, text) -> part(path); part(text) }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    /** Only this cache's direct children may be pruned; never traverse a symbolic link. */
    private fun remove(child: File) {
        require(child.absoluteFile.parentFile == directory.absoluteFile)
        fun delete(file: File) {
            if (file.canonicalFile == file.absoluteFile && file.isDirectory) file.listFiles()?.forEach(::delete)
            require(file.delete() || !file.exists()) { "无法清理分析快照" }
        }
        delete(child)
    }
}
