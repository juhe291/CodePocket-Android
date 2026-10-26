package com.codepocket.ide

import java.io.File

/** Parso persists imported modules under hashed paths. Bound that cache independently of snapshots. */
internal object AnalysisDiskCache {
    const val MAX_BYTES = 16L * 1024 * 1024
    const val MAX_FILES = 256
    fun trim(directory: File, maxBytes: Long = MAX_BYTES, maxFiles: Int = MAX_FILES) {
        require(maxBytes >= 0 && maxFiles >= 0)
        if (!directory.exists()) return
        require(directory.isDirectory && directory.canonicalFile == directory.absoluteFile)
        val files = mutableListOf<File>()
        fun scan(folder: File, depth: Int) {
            require(depth <= 8) { "分析缓存目录结构异常" }
            folder.listFiles()?.forEach { file ->
                if (file.canonicalFile != file.absoluteFile) require(file.delete()) { "不能移除分析缓存链接" }
                else if (file.isDirectory) scan(file, depth + 1)
                else files.add(file)
            }
        }
        scan(directory, 0)
        var bytes = files.sumOf { it.length() }
        var count = files.size
        for (file in files.sortedWith(compareBy({ it.lastModified() }, { it.path }))) {
            if (bytes <= maxBytes && count <= maxFiles) break
            val length = file.length()
            require(file.delete() || !file.exists()) { "无法清理分析缓存" }
            bytes -= length; count--
        }
    }
}
