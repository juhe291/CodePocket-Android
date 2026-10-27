package com.codepocket.ide

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Only the four pinned non-executable link inputs. Header extraction keeps its own policy. */
internal object CppBuildSupport {
    val names = setOf("crtbegin_so.o", "crtend_so.o", "libunwind.a", "clang/21/lib/linux/libclang_rt.builtins-aarch64-android.a")
    fun extract(input: InputStream, directory: File, entries: Int, bytes: Long) {
        require(entries == names.size && bytes in 1..(2L * 1024 * 1024))
        require(directory.isDirectory && directory.canonicalFile == directory.absoluteFile)
        val seen = mutableSetOf<String>(); var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in names && seen.add(entry.name))
                val file = File(directory, entry.name)
                require(file.canonicalPath.startsWith(directory.path + File.separator))
                require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()); require(file.createNewFile())
                var length = 0L
                file.outputStream().use { out ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = zip.read(buffer); if (count < 0) break
                        length += count; total += count
                        require(length <= 1024 * 1024 && total <= bytes)
                        out.write(buffer, 0, count)
                    }
                }
                require(length > 0); zip.closeEntry()
            }
        }
        require(seen == names && total == bytes)
    }
}
