package com.codepocket.ide

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.codepocket.ide.terminal.shell.ShellRuntime
import org.json.JSONObject
import java.io.File

/** Process-wide batch owner. Request contains files/application/terminal, never an Activity or View. */
internal object CaseRunSession {
    data class Request(val workspace: Workspace, val drafts: DraftStore, val keys: Map<String, String>,
        val path: String, val key: String, val code: String, val cpp: Boolean, val localCpp: Boolean,
        val scope: CppBuildScope, val terminal: ShellRuntime)
    private val main = Handler(Looper.getMainLooper())
    var batch: CaseBatch? = null; private set
    var key: String? = null; private set
    private var identity: String? = null
    private var cpp = false
    val running get() = batch?.running == true
    var listener: (() -> Unit)? = null
    fun store(context: Context) = CaseStore(File(context.filesDir.canonicalFile, "program-cases")) { from, to ->
        android.system.Os.rename(from.path, to.path)
    }
    fun start(context: Context, request: Request, cases: List<ProgramCase>): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (running || RunSession.running) return false
        val app = context.applicationContext
        val storage = store(app)
        key = request.key; identity = request.workspace.identity; cpp = request.cpp
        batch = CaseBatch(cases, { done ->
            WorkspaceIo.executor.execute {
                val prepared = runCatching { prepare(app, request) }
                main.post { done(prepared) }
            }
        }, { record, done ->
            WorkspaceIo.executor.execute {
                val saved = runCatching { storage.record(request.key, record); Unit }
                main.post { done(saved) }
            }
        }, { RunSession.stop() }, { listener?.invoke() })
        batch!!.start()
        return true
    }
    fun stop() { batch?.stop() }
    fun sourceChanged(workspaceId: String, path: String) {
        if (running && identity == workspaceId && (if (cpp) CppSources.isSource(path) else PythonSources.isSource(path)))
            batch?.stop("源码已编辑，当前项结束后停止；其余用例未执行", cancelCurrent = false)
    }
    private fun context(request: Request): String {
        val saved = when { !request.cpp -> request.workspace.pythonSources()
            request.localCpp -> request.workspace.cppSources()
            else -> mapOf(request.path to request.code) }
        require(request.path in saved) { "入口文件已删除" }
        require(saved[request.path] == request.code) { "入口源码已变化，请重新运行" }
        val drafts = request.drafts.analysisTexts(request.keys.filterKeys { it != request.path && it in saved })
        val texts = saved + drafts + (request.path to request.code)
        val data = JSONObject().put("path", request.path).put("cpp", request.cpp).put("local", request.localCpp)
            .put("scope", request.scope.name).put("files", JSONObject().apply { texts.toSortedMap().forEach { (path, text) -> put(path, text) } })
        return DocumentTree.digest(data.toString())
    }
    private fun prepare(app: Context, request: Request): PreparedCaseRun {
        val hash = context(request)
        var cppJob: CppRunSnapshot? = null
        var pythonJob: PythonRunSnapshot? = null
        try {
            if (request.localCpp) cppJob = CppRunSnapshot.capture(File(app.cacheDir.canonicalFile, "cpp-runs"),
                request.workspace, request.drafts, request.keys, request.path, request.code)
            else if (!request.cpp) pythonJob = PythonRunSnapshot.capture(File(app.cacheDir.canonicalFile, "python-runs"),
                request.workspace, request.drafts, request.keys, request.path, request.code)
            require(context(request) == hash && cppJob?.isCurrent() != false && pythonJob?.isCurrent() != false) {
                "项目或草稿在准备期间发生变化，请重试"
            }
            val native = cppJob; val python = pythonJob
            return object : PreparedCaseRun {
                override val contextHash = hash
                private var transferred = false
                override fun close() { if (!transferred) { native?.close(); python?.close() } }
                override fun launch(case: ProgramCase, done: (RunResult) -> Unit): Boolean {
                    val path = request.workspace.resolve(request.path).absolutePath
                    val accepted = RunSession.start(app, request.code, case.input, request.cpp, path,
                        python?.root?.path ?: request.workspace.root.path, request.workspace.identity,
                        python?.entry?.path ?: path, request.key, request.terminal, false, native, request.scope, python,
                        caseMode = true, onComplete = done)
                    transferred = accepted
                    return accepted
                }
            }
        } catch (failure: Throwable) {
            runCatching { cppJob?.close() }; runCatching { pythonJob?.close() }; throw failure
        }
    }
}
