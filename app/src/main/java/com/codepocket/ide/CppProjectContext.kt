package com.codepocket.ide

import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Stable active URI for typing; a different other-file generation requires a different clangd process. */
class CppProjectContext(private val directory: File) {
    class Snapshot internal constructor(val location: SemanticLocation, private val validate: () -> Boolean) {
        fun isCurrent(): Boolean = try { validate() } catch (e: InterruptedException) { throw e } catch (_: Exception) { false }
    }
    fun capture(workspace: Workspace, drafts: DraftStore, keys: Map<String, String>, path: String, code: String): Snapshot {
        require(CppSources.isSource(path) && code.length <= 45000)
        val projectKeys = keys.filterKeys { CppSources.isSource(it) && it != path }
        val source = workspace.cppSources(); require(path in source)
        val unsaved = drafts.analysisTexts(projectKeys).filterKeys { it in source }
        // didOpen/didChange supplies the complete active buffer. Never rewrite its on-disk snapshot
        // while a parser is using it, and never invalidate its URI for each ordinary keystroke.
        val content = source + unsaved + (path to "")
        require(content.size <= PythonSources.MAX_FILES && content.values.sumOf { it.length } <= PythonSources.MAX_UNITS)
        val hash = digest(workspace.identity, path, content)
        require(directory.isDirectory || directory.mkdirs()); require(directory.canonicalFile == directory.absoluteFile)
        val destination = File(directory, hash)
        if (!destination.isDirectory || PythonSources.local(destination, CppSources::isSource) != content) {
            if (destination.exists()) remove(destination)
            val staging = File(directory, ".pending-${UUID.randomUUID()}"); require(staging.mkdir())
            try {
                content.forEach { (relative, text) ->
                    interrupted()
                    require(!relative.startsWith('/') && !relative.contains('\\') && relative.split('/').none { it.isEmpty() || it == "." || it == ".." })
                    val file = File(staging, relative)
                    require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()); require(file.createNewFile())
                    file.writeText(text)
                }
                require(staging.renameTo(destination))
            } finally { if (staging.exists()) remove(staging) }
        }
        destination.setLastModified(System.currentTimeMillis())
        directory.listFiles()?.filter { it.isDirectory && it != destination }?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach(::remove)
        return Snapshot(SemanticLocation(destination.path, path, true)) {
            interrupted()
            workspace.cppSources() == source && drafts.analysisTexts(projectKeys).filterKeys { it in source } == unsaved &&
                PythonSources.local(destination, CppSources::isSource) == content
        }
    }
    private fun interrupted() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun digest(identity: String, path: String, content: Map<String, String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun part(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); digest.update(bytes.size.toString().toByteArray()); digest.update(0.toByte()); digest.update(bytes) }
        part(identity); part(path); content.toSortedMap().forEach { (name, text) -> part(name); part(text) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun remove(child: File) {
        require(child.absoluteFile.parentFile == directory.absoluteFile)
        fun delete(file: File) {
            if (file.canonicalFile == file.absoluteFile && file.isDirectory) file.listFiles()?.forEach(::delete)
            require(file.delete() || !file.exists())
        }
        delete(child)
    }
}
