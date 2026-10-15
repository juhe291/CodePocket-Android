package com.codepocket.ide

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WandboxEngineTest {
    private fun parse(json: String) = WandboxEngine.parseResponse(JSONObject(json), 123)

    @Test fun successMayWriteToStderr() {
        val result = parse("""{"status":"0","program_output":"42\n","program_error":"debug"}""")
        assertTrue(result.successful)
        assertEquals("42\n", result.stdout)
    }

    @Test fun compileErrorHasLine() {
        val result = parse("""{"status":"1","compiler_error":"prog.cc:3:8: error: expected ';'"}""")
        assertEquals(RunKind.COMPILE, result.kind)
        assertEquals(3, result.errorLine)
    }

    @Test fun runtimeExitAndSignalDifferFromCompilation() {
        assertEquals(RunKind.RUNTIME, parse("""{"status":"2","program_error":"failed"}""").kind)
        val result = parse("""{"status":"0","signal":"Segmentation fault"}""")
        assertEquals(RunKind.RUNTIME, result.kind)
        assertFalse(result.successful)
        assertNotEquals("0", result.exitCode)
    }

    @Test fun malformedServiceResponseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { parse("{}") }
    }
}
