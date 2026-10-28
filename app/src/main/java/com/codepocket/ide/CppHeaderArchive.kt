package com.codepocket.ide

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Pinned headers still obey extraction bounds and never escape the private destination. */
internal object CppHeaderArchive {
    fun extract(input: InputStream, destination: File, expectedEntries: Int, expectedBytes: Long) {
        require(expectedEntries in 1..20000 && expectedBytes in 1..(64L * 1024 * 1024))
        require(destination.isDirectory && destination.canonicalFile == destination.absoluteFile)
        var count = 0; var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && ++count <= expectedEntries)
                val name = entry.name
                require(!name.startsWith('/') && !name.contains('\\') && name.split('/').none { it.isEmpty() || it == "." || it == ".." })
                require(name.startsWith("sysroot/include/") || name.startsWith("clang/include/"))
                val file = File(destination, name)
                require(file.canonicalPath.startsWith(destination.path + File.separator))
                require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "无法创建 C++ 头文件目录：$name" }
                require(file.createNewFile()) { "无法创建 C++ 头文件（重名或文件系统限制）：$name" }
                var bytes = 0L
                file.outputStream().use { out ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val n = zip.read(buffer); if (n < 0) break
                        bytes += n; total += n
                        require(bytes <= 4L * 1024 * 1024 && total <= expectedBytes)
                        out.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
            }
        }
        require(count == expectedEntries && total == expectedBytes)
    }
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val bytes = ByteArray(65536)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val n = stream.read(bytes); if (n < 0) break
                digest.update(bytes, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
