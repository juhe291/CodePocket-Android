package com.codepocket.ide

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Files are private to CodePocket and don't require storage permissions. */
class Workspace(directory: File, private val replaceFile: (File, File) -> Unit) {
    constructor(context: Context) : this(File(context.filesDir, "workspace"), { source, target ->
        // POSIX rename atomically replaces the target on all supported Android versions (API 21+).
        android.system.Os.rename(source.path, target.path)
    })

    val root: File = directory.canonicalFile.apply {
        require(isDirectory || mkdirs()) { "无法创建工作区" }
    }

    init {
        val sampleCpp = File(root, "main.cpp")
        val samplePy = File(root, "main.py")
        if (root.listFiles().isNullOrEmpty()) {
            sampleCpp.writeText(CPP_SAMPLE)
            samplePy.writeText(PY_SAMPLE)
        }
    }

    // java.nio.file.Path requires API 26; the app supports API 24.
    fun path(file: File): String = safe(file).path.removePrefix(root.path).trimStart(File.separatorChar)
        .replace(File.separatorChar, '/')

    fun resolve(relative: String): File {
        require(!File(relative).isAbsolute && !relative.startsWith('/') && !relative.startsWith('\\') &&
            relative.split('/', '\\').none { it == ".." || it == "." }) { "非法的文件路径" }
        return safe(File(root, relative))
    }

    private fun safe(candidate: File): File {
        val canonical = candidate.canonicalFile
        require(canonical == root || canonical.path.startsWith(root.path + File.separator)) { "非法的文件路径" }
        var component = candidate.absoluteFile
        while (component != root) {
            require(component.canonicalFile == component) { "工作区不支持符号链接或非规范路径" }
            component = component.parentFile ?: error("非法的文件路径")
        }
        return canonical
    }

    private fun validName(name: String) {
        require(name.isNotBlank() && name == name.trim() && name != "." && name != ".." &&
            name.length <= 120 && !name.contains('/') && !name.contains('\\') &&
            name.none { it.isISOControl() }) { "文件名不能为空或包含 /、\\、控制字符等非法字符" }
    }

    fun children(dir: File): List<File> = safe(dir).listFiles()?.filter {
        runCatching { safe(it) }.isSuccess
    }?.sortedWith(
        compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })
    ) ?: emptyList()

    fun folders(): List<File> {
        val output = mutableListOf(root)
        fun scan(folder: File, depth: Int) {
            if (depth > 18) return
            children(folder).filter { it.isDirectory }.forEach { output.add(it); scan(it, depth + 1) }
        }
        scan(root, 0)
        return output
    }

    fun create(parent: File, name: String, directory: Boolean): File {
        validName(name)
        val location = safe(parent)
        require(location.isDirectory) { "父目录不存在" }
        val child = safe(File(location, name))
        require(!child.exists()) { "同名文件或文件夹已经存在" }
        if (directory) require(child.mkdir()) { "创建文件夹失败" }
        else require(child.createNewFile()) { "创建文件失败" }
        return child
    }

    fun rename(file: File, name: String): File {
        validName(name)
        val old = safe(file)
        require(old != root && old.exists()) { "无法重命名此项目" }
        val target = safe(File(old.parentFile, name))
        require(!target.exists()) { "目标名称已存在" }
        require(old.renameTo(target)) { "重命名失败" }
        return target
    }

    fun move(file: File, targetFolder: File): File {
        val original = safe(file)
        val parent = safe(targetFolder)
        require(original != root && parent.isDirectory) { "不能移动根文件夹" }
        require(!(original.isDirectory && (parent == original || parent.path.startsWith(original.path + File.separator)))) {
            "不能将文件夹移动到它自己里面"
        }
        val target = safe(File(parent, original.name))
        require(!target.exists()) { "目标目录已有同名项" }
        require(original.renameTo(target)) { "移动失败" }
        return target
    }

    fun delete(file: File) {
        val item = safe(file)
        require(item != root && item.exists()) { "不能删除工作区根目录" }
        // Preflight the entire tree: recursive deletion must never follow a link.
        fun checkTree(entry: File) {
            safe(entry)
            if (entry.isDirectory) entry.listFiles()?.forEach(::checkTree)
        }
        checkTree(item)
        require(item.deleteRecursively()) { "删除失败" }
    }

    fun read(file: File): String {
        val item = safe(file)
        require(item.isFile && item.length() <= 1024 * 1024) { "仅支持打开最大 1 MB 的文本文件" }
        return item.inputStream().use { stream ->
            utf8Reader(stream).use { readLimited(it, MAX_CHARS) }
        }.also { require(!it.contains('\u0000')) { "仅支持 UTF-8 文本文件" } }.removePrefix("\uFEFF")
    }

    fun write(file: File, content: String) {
        require(content.length <= MAX_CHARS) { "文件内容超过 20 万字符" }
        val item = safe(file)
        require(item.isFile) { "文件不存在" }
        // Write and sync a sibling first. A failed replacement preserves the old file.
        val temporary = File.createTempFile(".codepocket-save-", ".tmp", item.parentFile)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            replaceFile(temporary, item)
        } finally {
            temporary.delete()
        }
    }

    companion object {
        const val MAX_CHARS = 200000

        fun utf8Reader(stream: java.io.InputStream): java.io.Reader = java.io.InputStreamReader(stream,
            Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT))

        fun readLimited(reader: java.io.Reader, limit: Int): String {
            val buffer = CharArray(4096)
            val result = StringBuilder()
            while (true) {
                val count = reader.read(buffer, 0, minOf(buffer.size, limit + 1 - result.length))
                if (count < 0) return result.toString()
                result.append(buffer, 0, count)
                require(result.length <= limit) { "文件内容超过 $limit 字符" }
            }
        }
        const val CPP_SAMPLE = """#include <iostream>
#include <vector>
using namespace std;

int main() {
    int a, b;
    cin >> a >> b;
    cout << a + b << '\n';
    return 0;
}
"""
        const val PY_SAMPLE = """a, b = map(int, input().split())
print(a + b)
"""
    }
}
