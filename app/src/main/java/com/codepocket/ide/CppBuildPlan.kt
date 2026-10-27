package com.codepocket.ide

import java.io.File

enum class CppBuildScope { CURRENT, PROJECT }

internal data class CppToolchainPaths(val compiler: File, val linker: File, val headers: File,
    val support: File, val cxxLibrary: File, val platformLibraries: File = File("/system/lib64"))

internal data class CppBuildStep(val source: String?, val arguments: List<String>)

/** Command arrays only. No project build scripts/configuration and no writes to source files. */
internal class CppBuildPlan(root: File, output: File, entry: String, paths: Collection<String>,
    scope: CppBuildScope, private val tools: CppToolchainPaths) {
    val directory = root.canonicalFile
    val build = output.canonicalFile
    val program = File(build,"program.so")
    val sources: List<String>
    init {
        require(root.absoluteFile == directory && output.absoluteFile == build)
        require(directory.isDirectory && build.isDirectory)
        require(build != directory && !build.path.startsWith(directory.path + File.separator))
        require(paths.size in 1..128 && paths.distinct().size == paths.size)
        paths.forEach { relative ->
            require(relative.isNotBlank() && !relative.startsWith('/') && !relative.contains('\\') && !relative.contains(':') && relative.none { it<' ' || it=='\u007f' })
            require(relative.split('/').none { it.isEmpty() || it=="." || it==".." })
            val file=File(directory,relative)
            require(file.canonicalFile == file.absoluteFile && file.isFile && file.path.startsWith(directory.path+File.separator))
        }
        require(entry in paths && translationUnit(entry)) { "请选择 C++ 源文件作为入口" }
        sources=listOf(entry)+(if(scope==CppBuildScope.PROJECT)paths.filter{it!=entry && translationUnit(it)}.sorted() else emptyList())
        for(file in listOf(tools.compiler,tools.linker,tools.cxxLibrary,File(tools.support,"crtbegin_so.o"),
            File(tools.support,"crtend_so.o"),File(tools.support,"libunwind.a"),builtins()))require(file.isFile)
        require(File(tools.headers,"sysroot/include/c++/v1/vector").isFile)
        require(File(tools.headers,"clang/include/stddef.h").isFile)
    }
    private fun builtins()=File(tools.support,"clang/21/lib/linux/libclang_rt.builtins-aarch64-android.a")
    private fun objectFile(index:Int)=File(build,"unit-$index.o")
    fun steps():List<CppBuildStep> {
        val common=listOf("--target=aarch64-linux-android24","-x","c++","-std=c++17","-fPIC","-c","-O0","-gline-tables-only","-fno-omit-frame-pointer",
            "--no-default-config","-fno-color-diagnostics","-nostdinc++","--sysroot=${File(tools.headers,"sysroot").path}",
            "-resource-dir=${File(tools.headers,"clang").path}","-isystem",File(tools.headers,"sysroot/include/c++/v1").path,
            "-isystem",File(tools.headers,"sysroot/include").path,"-isystem",File(tools.headers,"sysroot/include/aarch64-linux-android").path,
            "-I${directory.path}","-fdebug-prefix-map=${directory.path}=.","-fdebug-compilation-dir=.")
        // Clang's internal driver reparses leading '-' inputs even after '--'. A relative
        // './' prefix also prevents project names starting with '@' becoming response files.
        val compile=sources.mapIndexed { index,source -> CppBuildStep(source,listOf(tools.compiler.path)+common+listOf("-o",objectFile(index).path,"--","./$source")) }
        val link=listOf(tools.linker.path,"-flavor","gnu","-shared","--eh-frame-hdr","--no-undefined","--hash-style=both","--exclude-libs=ALL",
            "--build-id=sha1","-z","relro","-z","now","-z","noexecstack","-z","max-page-size=16384","-soname","program.so","-o",program.path,
            File(tools.support,"crtbegin_so.o").path)+sources.indices.map{objectFile(it).path}+
            listOf(tools.cxxLibrary.path,"-L${tools.platformLibraries.path}","-lc","-lm","-ldl",builtins().path,
                File(tools.support,"libunwind.a").path,File(tools.support,"crtend_so.o").path)
        return compile+CppBuildStep(null,link)
    }
    companion object {
        fun translationUnit(path:String)=path.substringAfterLast('.',"").lowercase() in setOf("c","cc","cpp","cxx")
    }
}
