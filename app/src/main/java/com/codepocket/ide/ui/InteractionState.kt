package com.codepocket.ide.ui

enum class BackAction { HIDE_IME, CLOSE_EXPLORER, RESTORE_PANEL, CLOSE_PANEL, EXIT }
object InteractionState {
    fun status(running: Boolean, busy: Boolean, saving: Boolean, dirty: Boolean, message: String): String = when {
        running -> "运行中"
        busy -> message.ifBlank { "文件操作中" }
        saving -> "保存中"
        dirty -> if (listOf("失败", "错误", "冲突", "草稿").any { it in message } || message.startsWith("✕")) "未保存 · $message" else "未保存"
        else -> message.ifBlank { "就绪" }
    }
    fun back(ime: Boolean, explorer: Boolean, maximized: Boolean, panel: Boolean) = when {
        ime -> BackAction.HIDE_IME
        explorer -> BackAction.CLOSE_EXPLORER
        maximized -> BackAction.RESTORE_PANEL
        panel -> BackAction.CLOSE_PANEL
        else -> BackAction.EXIT
    }
    /** Logical source lines, not wrapped visual lines. Caret offsets follow Android's UTF-16 contract. */
    fun caret(source: String, offset: Int): Pair<Int, Int> {
        val end = offset.coerceIn(0, source.length)
        var line = 1; var start = 0
        for (i in 0 until end) if (source[i] == '\n') { line++; start = i + 1 }
        return line to end - start + 1
    }
}

/** Clearing the display never mutates a RunResult or stdin; a new run/result reveals new output. */
class OutputDisplayState {
    private var result: Any? = null
    private var running = false
    var cleared = false
        private set
    fun synchronize(result: Any?, running: Boolean) {
        if (this.result !== result || this.running != running) cleared = false
        this.result = result; this.running = running
    }
    fun clear() { cleared = true }
}
