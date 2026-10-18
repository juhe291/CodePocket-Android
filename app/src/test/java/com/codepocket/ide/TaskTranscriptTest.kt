package com.codepocket.ide

import com.codepocket.ide.terminal.TaskTranscript
import org.junit.Assert.*
import org.junit.Test

class TaskTranscriptTest {
    @Test fun actualResultKeepsBothStreamsAndExitInformation() {
        val result = RunResult("answer: 42\n", "warning from engine\n", "7", 234, engine="Wandbox", sourcePath="/project/main.cpp")
        val text=TaskTranscript.render(result,false,false)
        assertTrue(text.contains("answer: 42"));assertTrue(text.contains("warning from engine"))
        assertTrue(text.contains("main.cpp"));assertTrue(text.contains("Wandbox"));assertTrue(text.contains("退出码 7"));assertTrue(text.contains("234ms"))
        assertTrue(text.contains("不代表时间交错顺序"))
    }
    @Test fun taskDoesNotAdvertiseUnimplementedInteractiveInput() {
        val text=TaskTranscript.render(null,true,false)
        assertTrue(text.contains("预填输入"));assertTrue(text.contains("尚不支持运行中键盘输入"))
    }
    @Test fun clearingDisplayDoesNotMutateExecutorResult() {
        val r=RunResult("retained", "diagnostic", "0",1)
        assertFalse(TaskTranscript.render(r,false,true).contains("retained"))
        assertTrue(TaskTranscript.render(r,false,false).contains("retained"));assertEquals("diagnostic",r.stderr)
    }
    @Test fun ansiAndUnicodeOutputArePassedAsDataRatherThanCommands() {
        val output="\u001b[31m错误🙂\u001b[0m\n$(touch never)"
        assertTrue(TaskTranscript.render(RunResult(output,"","0",0),false,false).contains(output))
    }
}
