package com.codepocket.ide

import android.view.KeyEvent

/** Main row (+ is often Shift+=), direct Plus and numpad keys share the code-font command. */
object EditorFontKeys {
    fun delta(key: Int, ctrl: Boolean, alt: Boolean, meta: Boolean, softKeyboard: Boolean): Int? {
        if (!ctrl || alt || meta || softKeyboard) return null
        return when (key) {
            KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> 1
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> -1
            else -> null
        }
    }
}
