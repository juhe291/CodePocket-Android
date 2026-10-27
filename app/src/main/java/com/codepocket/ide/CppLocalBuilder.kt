package com.codepocket.ide

import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal data class CppBuildError(val file: String, val line: Int, val hash: String)
internal data class CppBuildResult(val program: File?, val stdout: String, val stderr: String,
    val exit: ExecutionExit, val error: CppBuildError?)

/** Fixed commands, one total compile deadline, bounded logs, and an explicit valid main entry. */
internal class CppLocalBuilder(private val start: (CppBuildStep, File) -> ExecutionProcess,
    private val own: (ExecutionProcess?) -> Unit, private val cancelled: () -> Boolean) {
    fun build(snapshot: CppRunSnapshot, scope: CppBuildScope, tools: CppToolchainPaths,
        stage: (String) -> Unit): CppBuildResult {
        require(snapshot.isCurrent()) { "项目或恢复草稿已改变，请重新运行" }
        val plan = CppBuildPlan(snapshot.source, snapshot.build, snapshot.entry, snapshot.texts.keys, scope, tools)
        val began = System.nanoTime(); val out = StringBuilder(); val error = StringBuilder(); var remaining = 220000
        for (step in plan.steps()) {
            if (cancelled()) throw CancellationException()
            val left = 60000 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began)
            if (left <= 0) throw TimeoutException("C++ 构建超过60秒")
            require(snapshot.build.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= 64L * 1024 * 1024) { "C++ 构建产物超过64MiB" }
            stage(step.source?.let { "编译 $it" } ?: "链接程序")
            val result = CppCommandExecutor({ start(step, plan.directory) }, cancelled, own, minOf(30000, left), remaining).run()
            out.append(result.stdout); error.append(result.stderr)
            remaining -= (result.stdout + result.stderr).toByteArray(Charsets.UTF_8).size
            require(remaining > 0) { "C++ 构建输出达到220000字节限制" }
            if (!result.exit.successful) return CppBuildResult(null, out.toString(), error.toString(), result.exit, diagnostic(snapshot, result.stderr))
        }
        CppSharedProgram.validate(plan.program)
        require(snapshot.isCurrent()) { "项目或恢复草稿在构建期间发生变化，请重新运行" }
        return CppBuildResult(plan.program, out.toString(), error.toString(), ExecutionExit(0, null), null)
    }
    companion object {
        private val location = Regex("^(.+?):([1-9][0-9]*):(?:[1-9][0-9]*:)? (?:fatal )?error:")
        fun diagnostic(snapshot: CppRunSnapshot, stderr: String): CppBuildError? {
            for (line in stderr.lineSequence()) {
                val match = location.find(line) ?: continue
                val path = match.groupValues[1].removePrefix("./")
                val relative = if (path in snapshot.texts) path else runCatching {
                    val file = File(path).canonicalFile
                    require(file.path.startsWith(snapshot.source.path + File.separator))
                    file.path.removePrefix(snapshot.source.path + File.separator).replace(File.separatorChar, '/')
                }.getOrNull() ?: continue
                val text = snapshot.texts[relative] ?: continue
                val number = match.groupValues[2].toIntOrNull() ?: continue
                if (number <= text.count { it == '\n' } + 1) return CppBuildError(relative, number, DocumentTree.digest(text))
            }
            return null
        }
    }
}
