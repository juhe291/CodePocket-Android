package com.codepocket.ide

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** All inputs come from the installed APK. No project scripts, shell, or network download. */
internal class CppRuntime(private val context: Context) {
    private val native = File(context.applicationInfo.nativeLibraryDir).canonicalFile
    private val manifest by lazy { JSONObject(context.assets.open("cpp-runtime/manifest.json").bufferedReader().use { it.readText() }) }
    private val headers: File by lazy { prepareHeaders() }
    private fun prepareHeaders(): File {
        require(manifest.getString("engine") == CppSemanticProtocol.ENGINE && manifest.getString("version") == "21.1.8-3")
        val spec = manifest.getJSONObject("headers"); val hash = spec.getString("sha256")
        require(hash.matches(Regex("[0-9a-f]{64}")) && spec.getString("asset") == "cpp-runtime/headers.zip")
        val directory = File(context.filesDir, "cpp-runtime").absoluteFile
        require(directory.isDirectory || directory.mkdirs()); require(directory.canonicalFile == directory)
        val ready = File(directory, hash)
        if (!File(ready, ".ready").isFile) {
            val pending = File(directory, ".pending-${UUID.randomUUID()}"); require(pending.mkdir())
            try {
                val zip = File(pending, "headers.zip")
                context.assets.open(spec.getString("asset")).use { input -> zip.outputStream().use { input.copyTo(it) } }
                require(zip.length() == spec.getLong("bytes") && CppHeaderArchive.sha256(zip.inputStream()) == hash)
                CppHeaderArchive.extract(zip.inputStream(), pending, spec.getInt("entries"), spec.getLong("uncompressed_bytes"))
                require(zip.delete()); File(pending, ".ready").writeText(hash)
                require(!ready.exists() && pending.renameTo(ready))
            } finally { if (pending.exists()) pending.deleteRecursively() }
        }
        require(ready.canonicalFile == ready && File(ready, ".ready").readText() == hash)
        require(File(ready, "sysroot/include/c++/v1/vector").isFile && File(ready, "clang/include/stddef.h").isFile)
        return ready
    }
    fun create(root: File, cancelled: () -> Boolean, own: (AnalysisProcess) -> Unit): CppLspEngine {
        val inputs = manifest.getJSONArray("libraries")
        for (i in 0 until inputs.length()) {
            val item = inputs.getJSONObject(i); val name = item.getString("name")
            require(name.matches(Regex("lib[A-Za-z0-9_]+\\.so")))
            val file = File(native, name)
            require(file.canonicalFile.parentFile == native && file.isFile && file.length() == item.getLong("bytes"))
        }
        val executable = File(native, manifest.getString("executable")); require(executable.canExecute())
        val trustedHeaders = headers
        val commands = File(context.filesDir, "cpp-empty-commands").apply { require(isDirectory || mkdirs()) }
        require(commands.listFiles()?.isEmpty() == true)
        val arguments = listOf(executable.path, "--enable-config=false", "--background-index=false", "--clang-tidy=false",
            "--header-insertion=never", "--function-arg-placeholders=false", "--completion-style=detailed", "--completion-parse=always",
            "--all-scopes-completion=false", "--use-dirty-headers", "-j=2", "--limit-results=30", "--pch-storage=memory", "--log=error", "--compile-commands-dir=${commands.path}")
        val environment = listOf("PATH=/system/bin", "LD_LIBRARY_PATH=${native.path}", "HOME=${context.filesDir.path}",
            "TMPDIR=${context.cacheDir.path}", "LANG=C.UTF-8")
        val flags = listOf("-xc++", "-std=c++17", "--target=aarch64-linux-android24", "--sysroot=${File(trustedHeaders, "sysroot").path}",
            "-resource-dir=${File(trustedHeaders, "clang").path}", "-isystem", File(trustedHeaders, "sysroot/include/c++/v1").path,
            "-isystem", File(trustedHeaders, "sysroot/include").path, "-isystem", File(trustedHeaders, "sysroot/include/aarch64-linux-android").path,
            "-I${root.path}")
        if (cancelled()) throw java.util.concurrent.CancellationException()
        val process = NativeCppProcess(arguments, root.path, environment)
        own(process)
        if (cancelled()) { process.close(); throw java.util.concurrent.CancellationException() }
        return CppLspEngine(process, root, flags)
    }
}
