package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class EditorFeaturesTest {
    @Test fun pythonFloorDivisionIsNotAComment() {
        val code = "x = 8 // 2 # comment\nprint(x)"
        val tokens = EditorFeatures.tokens(code, false)
        assertEquals(2, tokens.count { it.kind == "number" })
        assertEquals("# comment", tokens.single { it.kind == "comment" }.let { code.substring(it.start, it.end) })
    }

    @Test fun stringsCommentsAndMultilineLiteralsAreNotCode() {
        val code = "text = \"\"\"# not comment\nprint(42)\"\"\"\n# comment"
        assertEquals(listOf("string", "comment"), EditorFeatures.tokens(code, false).map { it.kind })
        assertEquals(0 to 11, EditorFeatures.matchingBrackets("(\"ignored)\")", 1, true))
        assertNull(EditorFeatures.matchingBrackets("([)]", 1, true))
    }

    @Test fun completionHandlesCaretAndDocumentIdentifiers() {
        assertEquals("pr", EditorFeatures.prefix("pr", 99))
        assertTrue("print" in EditorFeatures.suggestions("pr", 2, false))
        assertTrue("solution" in EditorFeatures.suggestions("solution sol", 12, true))
        assertTrue(EditorFeatures.suggestions("", 0, false).isEmpty())
    }

    @Test fun indentationAtStartPartialIndentAndSelectionEndingOnNextLine() {
        assertEquals(TextChange(0, 0, "    ", 4), EditorActions.indent("\nx", 0, 0, false))
        assertEquals(TextChange(0, 2, "", 0), EditorActions.indent("  x", 2, 2, true))
        assertEquals(TextChange(0, 4, "", 0), EditorActions.indent("    x", 0, 0, true))
        assertEquals(TextChange(0, 1, "    x", 0, 5), EditorActions.indent("x\ny", 0, 2, false))
        assertEquals("x\ny", EditorActions.indent("  x\n\ty", 0, 7, true).text)
    }

    @Test fun newlinePairsAndClosingBracketSkipping() {
        assertEquals("\n    \n", EditorActions.typed("{}", 1, 0, "\n", true)?.text)
        assertEquals("\n    ", EditorActions.typed("if True:", 8, 0, "\n", false)?.text)
        assertEquals("()", EditorActions.typed("", 0, 0, "(", true)?.text)
        assertEquals("", EditorActions.typed("()", 1, 0, ")", true)?.text)
        assertNull(EditorActions.typed("# comment", 9, 0, "(", false))
    }
}
