package com.codepocket.ide

import java.io.Closeable

/** Callbacks run on the owner thread. Preparation and persistence may finish after Stop. */
internal interface PreparedCaseRun : Closeable {
    val contextHash: String
    fun launch(case: ProgramCase, done: (RunResult) -> Unit): Boolean
}

internal class CaseBatch(
    cases: List<ProgramCase>,
    private val prepare: ((Result<PreparedCaseRun>) -> Unit) -> Unit,
    private val persist: (CaseRecord, (Result<Unit>) -> Unit) -> Unit,
    private val cancelRun: () -> Unit,
    private val changed: () -> Unit
) {
    enum class Stage { READY, PREPARING, RUNNING, SAVING, FINISHED }
    val queue = CaseQueue(cases)
    var stage = Stage.READY; private set
    var current: ProgramCase? = null; private set
    var message = ""; private set
    var contextHash: String? = null; private set
    var unsavedRecord: CaseRecord? = null; private set
    private val results = mutableListOf<CaseRecord>()
    val records: List<CaseRecord> get() = results.toList()
    val running get() = stage != Stage.READY && stage != Stage.FINISHED
    private var stopReason: String? = null

    fun start() { check(stage == Stage.READY); next() }
    fun stop(reason: String = "已中止批量运行", cancelCurrent: Boolean = true) {
        if (!running) return
        stopReason = reason; queue.requestStop(); message = reason
        if (stage == Stage.RUNNING && cancelCurrent) cancelRun()
        changed()
    }
    private fun finish(reason: String) {
        queue.stop(); current = null; message = reason; stage = Stage.FINISHED; changed()
    }
    private fun next() {
        val ticket = queue.next() ?: run {
            finish(stopReason ?: "已完成 ${results.size}/${queue.cases.size} 项"); return
        }
        current = ticket.case; stage = Stage.PREPARING; message = "正在准备 ${ticket.index + 1}/${queue.cases.size} · ${ticket.case.name}"
        changed()
        prepare { response ->
            if (stage != Stage.PREPARING || !queue.accepts(ticket)) { response.getOrNull()?.close(); return@prepare }
            val prepared = response.getOrElse { finish("准备失败：${it.message ?: it.javaClass.simpleName}；其余用例未执行"); return@prepare }
            if (stopReason != null) { prepared.close(); finish(stopReason!!); return@prepare }
            val previous = contextHash
            if (previous != null && previous != prepared.contextHash) {
                prepared.close(); finish("源码或恢复草稿已变化，剩余用例未执行。请重新运行。"); return@prepare
            }
            contextHash = prepared.contextHash
            stage = Stage.RUNNING; message = "正在运行 ${ticket.index + 1}/${queue.cases.size} · ${ticket.case.name}"
            changed()
            try {
                val launched = prepared.launch(ticket.case) { result ->
                    if (stage != Stage.RUNNING || !queue.accepts(ticket)) return@launch
                    val record = CaseRecord.create(ticket.case, prepared.contextHash, result)
                    results.add(record); unsavedRecord = record
                    stage = Stage.SAVING; message = "正在保存 ${ticket.case.name} 的结果"; changed()
                    persist(record) { saved ->
                        if (stage != Stage.SAVING || !queue.accepts(ticket)) return@persist
                        saved.exceptionOrNull()?.let {
                            finish("结果保存失败：${it.message ?: it.javaClass.simpleName}；本次结果仍可查看，其余用例未执行"); return@persist
                        }
                        unsavedRecord = null
                        check(queue.complete(ticket, record))
                        if (queue.finished) finish(stopReason ?: if (queue.completed == queue.cases.size)
                            "已完成 ${results.size}/${queue.cases.size} 项" else "${record.verdict.label}；剩余用例未执行")
                        else next()
                    }
                }
                if (!launched && stage == Stage.RUNNING) {
                    prepared.close(); finish("终端正在执行其他命令，剩余用例未执行")
                }
            } catch (failure: Exception) {
                prepared.close(); finish("无法启动用例：${failure.message ?: failure.javaClass.simpleName}；剩余用例未执行")
            }
        }
    }
}
