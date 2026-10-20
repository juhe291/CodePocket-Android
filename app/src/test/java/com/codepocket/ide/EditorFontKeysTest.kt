package com.codepocket.ide

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class EditorFontKeysTest {
    @Test fun mainAndNumpadKeysMapToSingleFontSteps() {
        for (key in listOf(KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_NUMPAD_ADD))
            assertEquals(1, EditorFontKeys.delta(key, true, false, false, false))
        for (key in listOf(KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT))
            assertEquals(-1, EditorFontKeys.delta(key, true, false, false, false))
    }
    @Test fun normalTypingAltGrAndSoftwareKeysDoNotBecomeZoom() {
        assertNull(EditorFontKeys.delta(KeyEvent.KEYCODE_EQUALS, false, false, false, false))
        assertNull(EditorFontKeys.delta(KeyEvent.KEYCODE_PLUS, true, true, false, false))
        assertNull(EditorFontKeys.delta(KeyEvent.KEYCODE_PLUS, true, false, true, false))
        assertNull(EditorFontKeys.delta(KeyEvent.KEYCODE_PLUS, true, false, false, true))
        assertNull(EditorFontKeys.delta(KeyEvent.KEYCODE_A, true, false, false, false))
    }
}
