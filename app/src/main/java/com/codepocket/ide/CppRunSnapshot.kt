package com.codepocket.ide

import java.io.Closeable
import java.io.File
import java.util.UUID

/** A run owns its own copy. Neither the editor mirror nor saved-base hashes are changed. */
internal class CppRunSnapshot private constructor(val directory: File, val source: File, val build: File,
    val entry: String, val texts: Map<String, String>, private val validate: () -> Boolean) : Closeable {
    fun isCurrent() = validate() && PythonSources.local(source, CppSources::isSource) == texts
    override fun close() {
        fun remove(file: File) {
            require(file.absolutePath.startsWith(directory.path + File.separator) || file == directory)
            if (file.isDirectory && file.canonicalFile == file.absoluteFile) file.listFiles()?.forEach(::remove)
            require(file.delete() || !file.exists())
        }
        remove(directory)
    }
    companion object {
        /** The private service reopens only a direct, canonical job under its own cache. */
        fun open(parent: File, directory: File, entry: String): CppRunSnapshot {
            require(parent.canonicalFile == parent.absoluteFile && directory.canonicalFile == directory.absoluteFile)
            require(directory.parentFile == parent && directory.name.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
            val source = File(directory,"source"); val build = File(directory,"build")
            require(source.isDirectory && build.isDirectory && source.canonicalFile == source && build.canonicalFile == build)
            require(build.listFiles()?.isEmpty() == true)
            val content = PythonSources.local(source,CppSources::isSource)
            require(entry in content && CppBuildPlan.translationUnit(entry))
            return CppRunSnapshot(directory,source,build,entry,content){true}
        }
        fun capture(parent: File, workspace: Workspace, drafts: DraftStore, keys: Map<String, String>,
            path: String, code: String): CppRunSnapshot {
            require(CppBuildPlan.translationUnit(path) && code.length <= Workspace.MAX_CHARS && !code.contains('\u0000'))
            val saved = workspace.cppSources(); require(path in saved)
            val projectKeys = keys.filterKeys { CppSources.isSource(it) && it != path }
            val unsaved = drafts.analysisTexts(projectKeys).filterKeys { it in saved }
            val content = saved + unsaved + (path to code)
            require(content.size in 1..PythonSources.MAX_FILES && content.values.sumOf { it.length } <= PythonSources.MAX_UNITS)
            require(parent.isDirectory || parent.mkdirs()); require(parent.canonicalFile == parent.absoluteFile)
            val directory = File(parent, UUID.randomUUID().toString()); require(directory.mkdir())
            val source = File(directory, "source"); val build = File(directory, "build")
            val snapshot = CppRunSnapshot(directory, source, build, path, content) {
                workspace.cppSources() == saved && drafts.analysisTexts(projectKeys).filterKeys { it in saved } == unsaved
            }
            try {
                require(source.mkdir() && build.mkdir())
                content.forEach { (relative, text) ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    require(!relative.startsWith('/') && !relative.contains('\\') && !relative.contains(':') &&
                        relative.none { it < ' ' || it == '\u007f' } && relative.split('/').none { it.isEmpty() || it == "." || it == ".." })
                    val file = File(source, relative)
                    require(file.canonicalFile == file.absoluteFile && file.path.startsWith(source.path + File.separator))
                    require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()); require(file.createNewFile())
                    file.writeText(text)
                }
                require(snapshot.isCurrent()) { "项目或恢复草稿在准备运行期间发生变化，请重试" }
                return snapshot
            } catch (failure: Throwable) { snapshot.close(); throw failure }
        }
    }
}
