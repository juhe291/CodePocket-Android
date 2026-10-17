package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class EditorSearchTest {
    @Test fun literalSearchAndBidirectionalWrapDoNotInterpretRegex() {
        val text = "[x] first [x]"
        assertEquals(0..2, EditorSearch.find(text, "[x]", 0))
        assertEquals(10..12, EditorSearch.find(text, "[x]", 3))
        assertEquals(0..2, EditorSearch.find(text, "[x]", 13))
        assertEquals(10..12, EditorSearch.find(text, "[x]", -1, backwards = true))
        assertNull(EditorSearch.find(text, "", 0)); assertNull(EditorSearch.find(text, ".*", 0))
    }
    @Test fun caseWholeWordsAndUnicodeOffsetsRemainAccurate() {
        val text = "xx foo_1 Foo foo 变量😀"
        assertEquals(9..11, EditorSearch.find(text, "foo", 0, ignoreCase = true, wholeWord = true))
        assertEquals(13..15, EditorSearch.find(text, "foo", 0, wholeWord = true))
        assertEquals(19..20, EditorSearch.find(text, "😀", 0))
        assertNull(EditorSearch.replaceSelection("food", "foo", "x", 0, 3, wholeWord = true))
    }
    @Test fun replacementsRequireARealSelectionAndPreserveLiteralDollarAndSlashes() {
        val text = "a.a a.a"
        assertNull(EditorSearch.replaceSelection(text, "a.a", "x", 0, 0))
        assertEquals(TextChange(0, 3, "$1\\", 3), EditorSearch.replaceSelection(text, "a.a", "$1\\", 3, 0))
        val replaced = EditorSearch.replaceAll(text, "a.a", "$1\\")!!
        assertEquals(2, replaced.count); assertEquals("$1\\ $1\\", replaced.change.text)
        assertEquals("bb", EditorSearch.replaceAll("aaaa", "aa", "b")!!.change.text)
    }
    @Test fun oversizedReplacementIsRejectedBeforeAllocatingOrChangingOriginal() {
        val text = "a".repeat(1000)
        assertThrows(IllegalArgumentException::class.java) { EditorSearch.replaceAll(text, "a", "x".repeat(1000)) }
        assertThrows(IllegalArgumentException::class.java) { EditorSearch.replaceSelection(text, "a", "x".repeat(Workspace.MAX_CHARS), 0, 1) }
        assertEquals(1000, text.length)
    }
    @Test fun lineOffsetsPreserveCrLfAndTheFinalEmptyLine() {
        val text = "a\r\n😀\n"
        assertEquals(0, EditorSearch.lineOffset(text, 1)); assertEquals(3, EditorSearch.lineOffset(text, 2))
        assertEquals(6, EditorSearch.lineOffset(text, 3)); assertNull(EditorSearch.lineOffset(text, 4))
        assertNull(EditorSearch.lineOffset(text, 0))
        assertEquals("1\n\n2\n3", EditorSearch.gutter("abcd\nx\n", listOf(0, 2, 5, 7)))
    }
    @Test fun backgroundResultsAreRejectedAfterSwitchEditOrExternalReload() {
        val request = EditorSnapshot("workspace-a:file1", 7, "original", 0, 0)
        assertTrue(request.sameBuffer(request.copy(start = 2, end = 4)))
        assertFalse(request.sameBuffer(request.copy(key = "workspace-b:file1")))
        assertFalse(request.sameBuffer(request.copy(revision = 8)))
        assertFalse(request.sameBuffer(request.copy(text = "external reload")))
    }
}
