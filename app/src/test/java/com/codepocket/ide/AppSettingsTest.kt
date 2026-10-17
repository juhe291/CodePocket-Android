package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {
    @Test fun legacyThemeAndCorruptPreferencesRemainUsable() {
        assertEquals(UiTheme.LIGHT, AppSettings.fromJson(null, false).theme)
        assertEquals(UiTheme.DARK, AppSettings.fromJson("{broken", true).theme)
        val unknown = AppSettings.fromJson("""{"version":99,"theme":"future","tabWidth":3,"fontSize":999,"autoSaveDelayMs":-1} """, false)
        assertEquals(UiTheme.LIGHT, unknown.theme)
        assertEquals(4, unknown.tabWidth); assertEquals(28, unknown.fontSize); assertEquals(300, unknown.autoSaveDelayMs)
    }
    @Test fun independentThemesAndDisabledFeaturesSurviveRoundTrip() {
        val original = AppSettings(theme = UiTheme.SYSTEM, syntax = SyntaxTheme.LIGHT, fontSize = 18, terminalFontSize = 16,
            tabWidth = 8, useSpaces = false, lineNumbers = false, wordWrap = true, autoIndent = false, autoPairs = false,
            autoCompletion = false, symbolBar = false, collapsePanelOnIme = false, autoSave = false, autoSaveDelayMs = 3000, animations = false)
        val restored = AppSettings.fromJson(original.toJson())
        assertEquals(original, restored)
        assertTrue(restored.isDark(true)); assertFalse(restored.isDark(false)); assertFalse(restored.syntaxIsDark(true))
        assertTrue(restored.copy(syntax = SyntaxTheme.DARK).syntaxIsDark(false))
    }
    @Test fun motionCannotOverrideSystemDisabledAnimationOrPowerSaving() {
        assertTrue(MotionPolicy.enabled(true, 1f, false))
        assertFalse(MotionPolicy.enabled(false, 1f, false)); assertFalse(MotionPolicy.enabled(true, 0f, false))
        assertFalse(MotionPolicy.enabled(true, 1f, true)); assertFalse(MotionPolicy.enabled(true, Float.NaN, false))
    }
}
