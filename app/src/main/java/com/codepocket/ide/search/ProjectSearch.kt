package com.codepocket.ide.search

import com.codepocket.ide.Workspace
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple project-wide text search.
 * Searches file contents for a pattern.
 */
class ProjectSearch(private val workspace: Workspace) {

    data class SearchResult(
        val path: String,
        val lineNumber: Int,
        val lineContent: String
    )

    private val cancelled = AtomicBoolean(false)

    /**
     * Search for a pattern in all project files.
     * @param pattern Search pattern
     * @param caseSensitive Whether to match case (default false)
     * @param maxResults Maximum results to return (default 500)
     * @return List of search results
     */
    fun search(
        pattern: String,
        caseSensitive: Boolean = false,
        maxResults: Int = 500
    ): List<SearchResult> {
        if (pattern.isEmpty()) return emptyList()

        cancelled.set(false)
        val results = mutableListOf<SearchResult>()
        val files = collectTextFiles()

        for (filePath in files) {
            if (cancelled.get() || results.size >= maxResults) break

            val fileResults = searchFile(filePath, pattern, caseSensitive)
            results.addAll(fileResults)

            if (results.size > maxResults) {
                return results.take(maxResults)
            }
        }

        return results
    }

    private fun collectTextFiles(): List<String> {
        val files = mutableListOf<String>()
        collectFromDirectory(workspace.root, "", files)
        return files
    }

    private fun collectFromDirectory(dir: File, relativePath: String, files: MutableList<String>) {
        val children = dir.listFiles() ?: return

        for (child in children) {
            val childPath = if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}"

            if (child.isDirectory) {
                collectFromDirectory(child, childPath, files)
            } else if (child.isFile && isTextFile(child.name)) {
                files.add(childPath)
            }
        }
    }

    private fun isTextFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("cpp", "hpp", "h", "c", "cc", "cxx", "py", "pyx", "txt", "md")
    }

    private fun searchFile(
        filePath: String,
        pattern: String,
        caseSensitive: Boolean
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        try {
            val file = workspace.resolve(filePath)
            val content = workspace.read(file)
            val lines = content.split('\n')

            val searchPattern = if (caseSensitive) pattern else pattern.lowercase()

            lines.forEachIndexed { index, line ->
                val searchLine = if (caseSensitive) line else line.lowercase()

                if (searchLine.contains(searchPattern)) {
                    results.add(
                        SearchResult(
                            path = filePath,
                            lineNumber = index + 1,
                            lineContent = line.trim()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Skip files that can't be read
        }

        return results
    }

    fun cancel() {
        cancelled.set(true)
    }
}
