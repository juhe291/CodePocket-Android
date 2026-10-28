package com.codepocket.ide

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** All inputs come from the installed APK. No project scripts, shell, or network download. */
internal class CppRuntime(private val context: Context) {
    // Context owns this boundary. Android may expose the private root through a legitimate alias.
    private val files = context.filesDir.canonicalFile
    private val native = File(context.applicationInfo.nativeLibraryDir).canonicalFile
    private val manifest by lazy { JSONObject(context.assets.open("cpp-runtime/manifest.json").bufferedReader().use { it.readText() }) }
    private val headers: File by lazy { prepareArchive("headers", "headers.zip", CppHeaderArchive::extract) }
    private val support: File by lazy { prepareArchive("build_support", "build-support.zip", CppBuildSupport::extract) }
    private fun prepareArchive(key: String, asset: String, extract: (java.io.InputStream, File, Int, Long) -> Unit): File {
        require(manifest.getString("engine") == CppSemanticProtocol.ENGINE && manifest.getString("version") == "21.1.8-3")
        val spec = manifest.getJSONObject(key); val hash = spec.getString("sha256")
        require(hash.matches(Regex("[0-9a-f]{64}")) && spec.getString("asset") == "cpp-runtime/$asset")
        val directory = File(files, "cpp-runtime")
        require(directory.isDirectory || directory.mkdirs()) { "无法创建本地 C++ 工具缓存目录" }
        require(directory.canonicalFile == directory) { "本地 C++ 工具缓存目录被重定向" }
        val ready = File(directory, hash)
        if (!File(ready, ".ready").isFile) {
            val pending = File(directory, ".pending-${UUID.randomUUID()}"); require(pending.mkdir())
            try {
                val zip = File(pending, "headers.zip")
                context.assets.open(spec.getString("asset")).use { input -> zip.outputStream().use { input.copyTo(it) } }
                require(zip.length() == spec.getLong("bytes") && CppHeaderArchive.sha256(zip.inputStream()) == hash)
                extract(zip.inputStream(), pending, spec.getInt("entries"), spec.getLong("uncompressed_bytes"))
                require(zip.delete()); File(pending, ".ready").writeText(hash)
                // Analysis and execution services may prepare the same immutable headers.
                // Accept a completed winner; never overwrite it with our pending directory.
                if (!ready.exists()) pending.renameTo(ready)
                require(File(ready, ".ready").isFile && File(ready, ".ready").readText() == hash)
            } finally { if (pending.exists()) pending.deleteRecursively() }
        }
        require(ready.canonicalFile == ready && File(ready, ".ready").readText() == hash)
        return ready
    }
    private fun checkLibraries() {
        val inputs = manifest.getJSONArray("libraries")
        for (i in 0 until inputs.length()) {
            val item = inputs.getJSONObject(i); val name = item.getString("name")
            require(name.matches(Regex("lib[A-Za-z0-9_]+\\.so")))
            val file = File(native, name)
            require(file.canonicalFile.parentFile == native && file.isFile && file.length() == item.getLong("bytes")) {
                "安装包的本地 C++ 工具缺失或大小不符：$name"
            }
        }
    }
    fun toolchain(): CppToolchainPaths {
        checkLibraries()
        val compiler = File(native, manifest.getString("compiler")); val linker = File(native, manifest.getString("linker"))
        require(compiler.name == "libcodepocket_clang.so" && linker.name == "libcodepocket_lld.so")
        require(compiler.canExecute() && linker.canExecute()) { "此系统无法执行安装包中的本地 C++ 编译器或链接器" }
        return CppToolchainPaths(compiler, linker, headers, support, File(native, "libcpB_______.so"))
    }
    fun installedRunner(): File = File(native,"libcodepocket_cpp_runner.so").also {
        require(it.canonicalFile.parentFile == native && it.isFile && it.canExecute()) { "安装包缺少本地 C++ 运行器" }
    }
    fun symbolizer(): File = File(native,manifest.getString("symbolizer")).also {
        require(it.name=="libcodepocket_symbolizer.so" && it.canonicalFile.parentFile==native && it.isFile && it.canExecute())
    }
    fun create(root: File, cancelled: () -> Boolean, own: (AnalysisProcess) -> Unit): CppLspEngine {
        checkLibraries()
        val executable = File(native, manifest.getString("executable")); require(executable.canExecute())
        val trustedHeaders = headers
        require(File(trustedHeaders, "sysroot/include/c++/v1/vector").isFile && File(trustedHeaders, "clang/include/stddef.h").isFile)
        val commands = File(files, "cpp-empty-commands").apply { require(isDirectory || mkdirs()) }
        require(commands.listFiles()?.isEmpty() == true)
        val arguments = listOf(executable.path, "--enable-config=false", "--background-index=false", "--clang-tidy=false",
            "--header-insertion=never", "--function-arg-placeholders=false", "--completion-style=detailed", "--completion-parse=always",
            "--all-scopes-completion=false", "--use-dirty-headers", "-j=2", "--limit-results=30", "--pch-storage=memory", "--log=error", "--compile-commands-dir=${commands.path}")
        val environment = listOf("PATH=/system/bin", "LD_LIBRARY_PATH=${native.path}", "HOME=${files.path}",
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
