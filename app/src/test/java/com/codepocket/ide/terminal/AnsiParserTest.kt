package com.codepocket.ide.terminal

import org.junit.Assert.*
import org.junit.Test

class AnsiParserTest {

    private val parser = AnsiParser()

    @Test
    fun parseSimpleText() {
        val segments = parser.parse("Hello World")

        assertEquals(1, segments.size)
        assertEquals("Hello World", segments[0].text)
        assertNull(segments[0].foregroundColor)
        assertFalse(segments[0].bold)
    }

    @Test
    fun parseRedText() {
        val segments = parser.parse("\u001B[31mRed\u001B[0m")

        assertEquals(1, segments.size)
        assertEquals("Red", segments[0].text)
        assertEquals(0xFFE74856.toInt(), segments[0].foregroundColor)
    }

    @Test
    fun parseBoldText() {
        val segments = parser.parse("\u001B[1mBold\u001B[0m")

        assertEquals(1, segments.size)
        assertEquals("Bold", segments[0].text)
        assertTrue(segments[0].bold)
    }

    @Test
    fun parseMultipleStyles() {
        val segments = parser.parse("\u001B[1;31mBold Red\u001B[0m")

        assertEquals(1, segments.size)
        assertEquals("Bold Red", segments[0].text)
        assertTrue(segments[0].bold)
        assertEquals(0xFFE74856.toInt(), segments[0].foregroundColor)
    }

    @Test
    fun parseMixedText() {
        val segments = parser.parse("Normal \u001B[32mGreen\u001B[0m Normal")

        assertEquals(3, segments.size)
        assertEquals("Normal ", segments[0].text)
        assertNull(segments[0].foregroundColor)

        assertEquals("Green", segments[1].text)
        assertEquals(0xFF16C60C.toInt(), segments[1].foregroundColor)

        assertEquals(" Normal", segments[2].text)
        assertNull(segments[2].foregroundColor)
    }

    @Test
    fun parseResetCode() {
        val segments = parser.parse("\u001B[1;31mRed Bold\u001B[0mNormal")

        assertEquals(2, segments.size)
        assertTrue(segments[0].bold)
        assertFalse(segments[1].bold)
        assertNull(segments[1].foregroundColor)
    }

    @Test
    fun unsupportedCsiPreservesFollowingPrintableText() {
        val segments = parser.parse("Test\u001B[Invalid")

        // ESC [ I is an unsupported CSI control. The following printable "nvalid" must survive.
        assertEquals("Testnvalid", segments.joinToString("") { it.text })
    }
}
