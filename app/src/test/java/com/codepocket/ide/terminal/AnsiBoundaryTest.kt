package com.codepocket.ide.terminal

import org.junit.Assert.*
import org.junit.Test

class AnsiBoundaryTest {
    @Test fun splitColorSequenceWaitsWithoutShowingControlBytes() {
        val parser = AnsiParser()
        assertTrue(parser.parse("\u001b[3").isEmpty())
        val result = parser.parse("1mred")
        assertEquals("red", result.single().text); assertEquals(0xFFE74856.toInt(), result.single().foregroundColor)
    }
    @Test fun clearResetsStyleAndPendingSequence() {
        val parser = AnsiParser(); parser.parse("\u001b[31mred\u001b["); parser.reset()
        val result = parser.parse("plain").single()
        assertEquals("plain", result.text); assertNull(result.foregroundColor)
    }
    @Test fun unsupportedCsiDoesNotConsumeFollowingMessage() {
        assertEquals("message", AnsiParser().parse("\u001b[2Kmessage").joinToString("") { it.text })
    }
    @Test fun independentStyleResetsPreserveOtherAttributes() {
        val result = AnsiParser().parse("\u001b[1;3;4;31;44ma\u001b[22;23;24;39;49mb")
        assertTrue(result[0].bold); assertTrue(result[0].italic); assertTrue(result[0].underline)
        assertFalse(result[1].bold); assertFalse(result[1].italic); assertFalse(result[1].underline)
        assertNull(result[1].foregroundColor); assertNull(result[1].backgroundColor)
    }
    @Test fun excessiveEscapeParametersDoNotRetainUnboundedPendingInput() {
        val parser = AnsiParser(); parser.parse("\u001b[" + "1;".repeat(1000))
        assertEquals("plain", parser.parse("plain").single().text)
    }
}
