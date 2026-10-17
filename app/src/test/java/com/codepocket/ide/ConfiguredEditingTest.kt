package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class ConfiguredEditingTest {
    @Test fun configurableIndentationAndReverseSelectionsPreserveOtherLines() {
        assertEquals("  ", EditorActions.indent("x", 0, 0, false, 2, true).text)
        assertEquals("\t", EditorActions.indent("x", 0, 0, false, 8, false).text)
        assertEquals("        a\n        b", EditorActions.indent("a\nb", 3, 0, false, 8, true).text)
        assertEquals("a\nb", EditorActions.indent("        a\n\tb", 0, 12, true, 8, false).text)
    }
    @Test fun newlineUsesConfiguredUnitAndRespectsIndependentSwitches() {
        assertEquals("\n\t", EditorActions.typed("if True:", 8, 0, "\n", false, 2, false)?.text)
        assertNull(EditorActions.typed("if True:", 8, 0, "\n", false, autoIndent = false))
        assertNull(EditorActions.typed("", 0, 0, "(", true, autoPairs = false))
        assertEquals("\n  ", EditorActions.typed("  x", 3, 0, "\n", true, 2, true, autoPairs = false)?.text)
        assertNull(EditorActions.typed("# comment", 9, 0, "(", false, 8, false))
    }
    @Test fun keepingPanelWithImeIsAnExplicitChoiceAndDoesNotHideStdin() {
        assertTrue(ImeLayoutPolicy.resolve(true, false, 350, true, false).showPanel)
        assertTrue(ImeLayoutPolicy.resolve(true, true, 200, true, true).showPanel)
        assertFalse(ImeLayoutPolicy.resolve(true, false, 350, false, false).showPanel)
    }
}
