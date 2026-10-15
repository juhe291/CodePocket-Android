package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class EditorSessionsTest {
    @Test fun stdinIsSeparateForFilesAndWorkspacesAndSurvivesRestore() {
        val sessions = EditorSessions("first")
        sessions.file("main.cpp").stdin = "3 5"
        sessions.file("other.cpp").stdin = "6 7"
        sessions.file("main.py").stdin = "10 20"
        val restored = EditorSessions("first", sessions.toJson())
        assertEquals("3 5", restored.file("main.cpp").stdin)
        assertEquals("6 7", restored.file("other.cpp").stdin)
        assertEquals("10 20", restored.file("main.py").stdin)
        assertEquals("", EditorSessions("second").file("main.cpp").stdin)
        assertEquals(sessions.key("main.py"), restored.key("main.py"))
        restored.file("main.py").stdin = ""
        assertEquals("3 5", restored.file("main.cpp").stdin)
    }
    @Test fun directoryRenameMovesStateWithoutChangingIdentityOrSibling() {
        val sessions = EditorSessions("project")
        val f = sessions.file("src/a.py")
        f.stdin = "你好"
        f.view = EditorViewState(9, 2, 4, 100, "stdin", 1, 2)
        sessions.file("src-other/a.py").stdin = "keep"
        val key = sessions.key("src/a.py")
        sessions.rebase("src", "lib")
        assertEquals(key, sessions.key("lib/a.py"))
        assertEquals(f.view, EditorSessions("project", sessions.toJson()).file("lib/a.py").view)
        assertEquals("keep", sessions.file("src-other/a.py").stdin)
    }
    @Test fun deletionDoesNotResurrectInputAndConflictDoesNotChangeAnything() {
        val sessions = EditorSessions("project")
        sessions.file("a.py").stdin = "old"
        val key = sessions.key("a.py")
        sessions.file("b.py").stdin = "keep"
        assertThrows(IllegalArgumentException::class.java) { sessions.rebase("a.py", "b.py") }
        assertEquals("old", sessions.file("a.py").stdin)
        sessions.removeTree("a.py")
        assertEquals("", sessions.file("a.py").stdin)
        assertNotEquals(key, sessions.key("a.py"))
    }
    @Test fun corruptRecordsAreIgnoredAndReverseSelectionIsPreserved() {
        val sessions = EditorSessions("project", "[{bad]")
        assertEquals("", sessions.file("safe.py").stdin)
        assertThrows(IllegalArgumentException::class.java) { sessions.file("../unsafe.py") }
        val state = EditorViewState(99, 2, -1, 10, "stdin", 9, -1).clamp(5, 3)
        assertEquals(EditorViewState(5, 2, 0, 10, "stdin", 3, 0), state)
    }
    @Test fun keyboardPolicyKeepsStdinReachableAndRestoresPanel() {
        assertFalse(ImeLayoutPolicy.resolve(true, false, 250, true).showPanel)
        assertTrue(ImeLayoutPolicy.resolve(true, true, 250, true).showPanel)
        assertTrue(ImeLayoutPolicy.resolve(true, true, 250, true).compactInput)
        assertFalse(ImeLayoutPolicy.resolve(true, true, 250, true).showDetails)
        assertTrue(ImeLayoutPolicy.resolve(false, false, 600, true).showPanel)
        assertFalse(ImeLayoutPolicy.resolve(false, false, 600, false).showPanel)
    }
}
