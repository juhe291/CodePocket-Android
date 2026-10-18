package com.codepocket.ide

import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import kotlin.system.measureTimeMillis

enum class RunKind(val label: String) {
    CANCELLED("已中止"), SUCCESS("运行完成"), COMPILE("编译错误"), RUNTIME("运行错误"), NETWORK("网络错误"), TIMEOUT("运行超时"), SERVICE("服务错误")
}

data class RunResult(
    val stdout: String,
    val stderr: String,
    val exitCode: String,
    val elapsedMs: Long,
    val errorLine: Int? = null,
    val engine: String = "",
    val kind: RunKind = if (exitCode == "0") RunKind.SUCCESS else RunKind.RUNTIME,
    val errorFile: String? = null,
    val sourcePath: String? = null,
    val projectRoot: String? = null,
    val workspaceId: String? = null,
    val sourceKey: String? = null,
    val sourceHash: String? = null
) {
    val successful: Boolean get() = kind == RunKind.SUCCESS
}

object PythonEngine {
    fun run(context: android.content.Context, code: String, input: String, filename: String = "main.py", projectDir: String = ""): RunResult {
        if (!Python.isStarted()) Python.start(com.chaquo.python.android.AndroidPlatform(context.applicationContext))
        val result = JSONObject(
            Python.getInstance().getModule("runner")
                .callAttr("run_code", code, input, filename, projectDir).toString()
        )
        return RunResult(
            stdout = result.optString("stdout"),
            stderr = result.optString("stderr"),
            exitCode = result.optInt("exit_code", 1).toString(),
            elapsedMs = result.optLong("elapsed_ms", 0),
            errorLine = if (result.isNull("error_line")) null else result.optInt("error_line"),
            engine = "Python 3.13 · 本地",
            kind = when (result.optString("error_kind")) {
                "success" -> RunKind.SUCCESS
                "timeout" -> RunKind.TIMEOUT
                "compile" -> RunKind.COMPILE
                else -> RunKind.RUNTIME
            },
            errorFile = if (result.isNull("error_file")) null else result.optString("error_file")
        )
    }
    fun runTerminal(context: android.content.Context, code: String, input: String, filename: String, projectDir: String,
        interactive: Boolean, events: Any): RunResult {
        if (!Python.isStarted()) Python.start(com.chaquo.python.android.AndroidPlatform(context.applicationContext))
        val data = JSONObject(Python.getInstance().getModule("runner").callAttr("run_terminal", code, input, filename,
            projectDir, 0, 1, 2, interactive, events).toString())
        return RunResult(data.optString("stdout"),data.optString("stderr"),data.optInt("exit_code",1).toString(),data.optLong("elapsed_ms",0),
            if(data.isNull("error_line"))null else data.optInt("error_line"),"Python 3.13 · 终端 / 本地",
            when(data.optString("error_kind")){"success"->RunKind.SUCCESS;"timeout"->RunKind.TIMEOUT;"compile"->RunKind.COMPILE;else->RunKind.RUNTIME},
            if(data.isNull("error_file"))null else data.optString("error_file"))
    }

}

object WandboxEngine {
    private const val BASE = "https://wandbox.org/api"
    @Volatile private var cachedCompiler: String? = null

    /** Discover a real C++ compiler. Compiler IDs can change. */
    private fun chooseCompiler(cancel: RunCancellation): String {
        cancel.check()
        cachedCompiler?.let { return it }
        val (status, content) = request("GET", "$BASE/list.json", null, cancel)
        if (status !in 200..299) throw ServiceException(status, "获取 C++ 编译器列表失败 (HTTP $status)")
        val list = JSONArray(content)
        val candidates = (0 until list.length()).map { list.getJSONObject(it) }
            .filter { it.optString("language") == "C++" &&
                it.optBoolean("compiler-option-raw") &&
                it.optString("name").contains("gcc", ignoreCase = true) }
        if (candidates.isEmpty()) error("Wandbox 暂未提供支持 C++17 的 GCC 编译器")
        // Prefer stable GCC 14/13/15/12 over GCC HEAD; fall back to any available GCC.
        val preferred = listOf("gcc-14", "gcc-15", "gcc-13", "gcc-12", "gcc-16")
        for (prefix in preferred) {
            val matched = candidates.firstOrNull { it.optString("name").startsWith(prefix) }
            if (matched != null) {
                val chosen = matched.getString("name")
                cachedCompiler = chosen
                return chosen
            }
        }
        val chosen = candidates.first().getString("name")
        cachedCompiler = chosen
        return chosen
    }

    fun run(code: String, input: String, cancel: RunCancellation = RunCancellation()): RunResult {
        var response = JSONObject()
        val ms = measureTimeMillis {
            val compiler = chooseCompiler(cancel)
            val payload = JSONObject()
                .put("compiler", compiler)
                .put("code", code)
                .put("stdin", input)
                .put("compiler-option-raw", "-std=c++17")
                .put("save", false)
            val (status, content) = request("POST", "$BASE/compile.json", payload.toString(), cancel)
            if (status !in 200..299) {
                val detail = if (content.length > 500) content.take(500) else content
                if (status == 400 || status == 404) cachedCompiler = null
                throw ServiceException(status, "云端服务失败 (HTTP $status)：$detail")
            }
            response = JSONObject(content)
            response.put("_chosen_compiler", compiler)
        }

        return parseResponse(response, ms)
    }

    internal fun parseResponse(response: JSONObject, ms: Long): RunResult {
        require(response.has("status") || response.has("compiler_status") || response.optString("signal").isNotBlank()) {
            "Wandbox 返回了不含执行状态的响应"
        }
        val compilerError = response.optString("compiler_error", response.optString("compiler_message"))
        val programError = response.optString("program_error")
        val diagnostics = buildString {
            if (compilerError.isNotBlank()) append(compilerError.trimEnd())
            if (programError.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(programError.trimEnd())
            }
        }
        val stdout = response.optString("program_output")
        val codeValue = response.optString("status", "1")
        val compilerStatus = response.optString("compiler_status", "0")
        val exitCode = if (compilerStatus != "0") compilerStatus else codeValue
        val signal = response.optString("signal")
        val kind = when {
            compilerStatus != "0" || (exitCode != "0" && Regex("(?i)(?:fatal )?error:|undefined reference|collect2:").containsMatchIn(compilerError)) -> RunKind.COMPILE
            signal.isNotBlank() || exitCode != "0" -> RunKind.RUNTIME
            else -> RunKind.SUCCESS
        }
        val line = Regex("(?:prog\\.cc|prog\\.cpp|main\\.cpp):([0-9]+):")
            .find(diagnostics)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return RunResult(
            stdout = stdout,
            stderr = diagnostics + if (signal.isNotBlank()) "\n终止信号：$signal" else "",
            exitCode = if (signal.isNotBlank() && exitCode == "0") "1" else exitCode,
            elapsedMs = ms,
            errorLine = line,
            engine = "C++17 · ${response.optString("_chosen_compiler")} · Wandbox",
            kind = kind
        )
    }

    private fun request(method: String, endpoint: String, json: String?, cancel: RunCancellation): Pair<Int, String> {
        cancel.check()
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
        cancel.attach(connection)
        connection.apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CodePocket/0.2 (Android; personal educational IDE)")
            if (json != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { Workspace.readLimited(it, if (method == "GET") 2000000 else 250000) }.orEmpty()
            cancel.check()
            return status to body
        } finally {
            cancel.release(connection)
            connection.disconnect()
        }
    }
}
