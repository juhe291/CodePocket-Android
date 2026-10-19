package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class TerminalFocusStateTest {
    @Test fun terminalFocusSurvivesSavedSessionAndBoundsWithoutChangingOtherState() {
        val sessions=EditorSessions("workspace")
        val file=sessions.file("main.py")
        file.stdin="preserved legacy input\n"
        file.view=EditorViewState(20,9,100,200,"terminal",5,3)
        val restored=EditorSessions("workspace",sessions.toJson()).file("main.py")
        val view=restored.view.clamp(8,2)
        assertEquals(file.id,restored.id)
        assertEquals(file.stdin,restored.stdin)
        assertEquals(EditorViewState(8,8,100,200,"terminal",2,2),view)
        assertEquals("editor",view.copy(focus="unknown").clamp(8,2).focus)
    }
}
