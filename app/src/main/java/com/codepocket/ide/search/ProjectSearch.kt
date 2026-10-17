package com.codepocket.ide.search

import com.codepocket.ide.Workspace
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Literal project search. Call on WorkspaceIo's serial executor, never the UI thread. */
class ProjectSearch(private val workspace: Workspace) {
    data class SearchResult(val path: String, val lineNumber: Int, val lineContent: String)
    private val cancelled = AtomicBoolean(false)
    var skippedFiles = 0
        private set
    var limited = false
        private set
    private fun stopped() = cancelled.get() || Thread.currentThread().isInterrupted
    val wasCancelled get() = cancelled.get()
    fun cancel() { cancelled.set(true) }

    fun search(pattern: String, caseSensitive: Boolean = false, maxResults: Int = 500,
        buffers: Map<String, String> = emptyMap()): List<SearchResult> {
        val limit = maxResults.coerceIn(0, 500)
        if (pattern.isEmpty() || pattern.length > 256 || limit == 0 || stopped()) return emptyList()
        val results = mutableListOf<SearchResult>()
        var visited = 0
        var characters = 0L
        skippedFiles = 0; limited = false
        fun visit(directory: File, depth: Int) {
            if (stopped()) return
            if (depth >= 20) { limited = true; return }
            val children = runCatching { workspace.children(directory) }.getOrElse { skippedFiles++; return }
            for (child in children) {
                if (stopped()) return
                if (++visited > 1000 || results.size >= limit || characters >= 8L * 1024 * 1024) { limited = true; return }
                // Workspace.children/resolve enforce path, symbolic-link and SAF document boundaries.
                if (child.isDirectory) { visit(child, depth + 1); continue }
                if (child.extension.lowercase() !in EXTENSIONS) continue
                val path = try { workspace.path(child) } catch (_: Exception) { skippedFiles++; continue }
                val content = try { buffers[path] ?: workspace.read(workspace.resolve(path)) }
                    catch (_: Exception) { skippedFiles++; continue }
                characters += content.length
                var line = 1
                var start = 0
                while (start <= content.length) {
                    if (stopped()) return
                    val end = content.indexOf('\n', start).let { if (it < 0) content.length else it }
                    val text = content.substring(start, end).removeSuffix("\r")
                    if (text.contains(pattern, ignoreCase = !caseSensitive)) {
                        results.add(SearchResult(path, line, text.trim()))
                        if (results.size >= limit) { limited = true; return }
                    }
                    if (end == content.length) break
                    start = end + 1; line++
                }
            }
        }
        visit(workspace.root, 0)
        return results
    }
    companion object { private val EXTENSIONS = setOf("cpp", "hpp", "h", "c", "cc", "cxx", "py", "pyx", "txt", "md") }
}
