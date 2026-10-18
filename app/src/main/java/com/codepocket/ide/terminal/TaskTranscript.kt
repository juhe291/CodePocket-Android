package com.codepocket.ide.terminal

import com.codepocket.ide.RunResult

/** Formats real executor results. Batch output cannot promise stdout/stderr interleaving or live input. */
object TaskTranscript {
    fun render(result: RunResult?, running: Boolean, cleared: Boolean): String {
        if (cleared) return "任务显示已清空；运行结果与每文件预填输入仍保留。"
        if (running) return "任务运行中 · 预填输入，执行完成后返回结果\n输入已在启动时提交；此任务尚不支持运行中键盘输入。"
        if (result == null) return "尚无运行任务。点击运行将使用真实Python本地执行器或Wandbox云端服务。\n当前任务使用预填输入，执行完成后返回结果。"
        return buildString {
            append("任务：").append(result.sourcePath?.substringAfterLast('/') ?: "程序").append('\n')
            append(result.engine).append(" · ").append(result.kind.label)
            append(" · 退出码 ").append(result.exitCode).append(" · ").append(result.elapsedMs).append("ms\n")
            append("预填输入 · 完成后返回结果；stdout/stderr分别展示，不代表时间交错顺序。\n\n")
            append("── 标准输出 ──\n").append(result.stdout.ifEmpty { "（没有标准输出）" })
            if (!endsWith("\n")) append('\n')
            if (result.stderr.isNotEmpty()) append("\n── 标准错误 / 诊断 ──\n").append(result.stderr)
        }
    }
}
