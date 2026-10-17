package com.codepocket.ide

import org.json.JSONObject

enum class UiTheme { DARK, LIGHT, SYSTEM }
enum class SyntaxTheme { FOLLOW_UI, DARK, LIGHT }

/** Versioned preferences; unknown values fall back without losing project/session preferences. */
data class AppSettings(
    val theme: UiTheme = UiTheme.DARK,
    val syntax: SyntaxTheme = SyntaxTheme.FOLLOW_UI,
    val fontSize: Int = 14,
    val terminalFontSize: Int = 12,
    val tabWidth: Int = 4,
    val useSpaces: Boolean = true,
    val lineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val autoIndent: Boolean = true,
    val autoPairs: Boolean = true,
    val autoCompletion: Boolean = true,
    val symbolBar: Boolean = true,
    val collapsePanelOnIme: Boolean = true,
    val autoSave: Boolean = true,
    val autoSaveDelayMs: Int = 700,
    val animations: Boolean = true,
    val completionTab: Boolean = true,
    val completionEnter: Boolean = false,
    val completionArrows: CompletionArrows = CompletionArrows.EXPLICIT,
    val signatureHelp: Boolean = true
) {
    fun normalized() = copy(fontSize = fontSize.coerceIn(10, 28), terminalFontSize = terminalFontSize.coerceIn(10, 24),
        tabWidth = tabWidth.takeIf { it in listOf(2, 4, 8) } ?: 4, autoSaveDelayMs = autoSaveDelayMs.coerceIn(300, 10000))
    fun isDark(systemDark: Boolean) = when (theme) { UiTheme.DARK -> true; UiTheme.LIGHT -> false; UiTheme.SYSTEM -> systemDark }
    fun syntaxIsDark(uiDark: Boolean) = when (syntax) { SyntaxTheme.DARK -> true; SyntaxTheme.LIGHT -> false; SyntaxTheme.FOLLOW_UI -> uiDark }
    fun toJson(): String = normalized().let { s -> JSONObject().apply {
        put("version", 3); put("theme", s.theme.name); put("syntax", s.syntax.name)
        put("fontSize", s.fontSize); put("terminalFontSize", s.terminalFontSize); put("tabWidth", s.tabWidth)
        put("useSpaces", s.useSpaces); put("lineNumbers", s.lineNumbers); put("wordWrap", s.wordWrap)
        put("autoIndent", s.autoIndent); put("autoPairs", s.autoPairs); put("autoCompletion", s.autoCompletion)
        put("symbolBar", s.symbolBar); put("collapsePanelOnIme", s.collapsePanelOnIme)
        put("autoSave", s.autoSave); put("autoSaveDelayMs", s.autoSaveDelayMs); put("animations", s.animations)
        put("signatureHelp", s.signatureHelp)
        put("completionTab", s.completionTab); put("completionEnter", s.completionEnter); put("completionArrows", s.completionArrows.name)
    }.toString() }
    companion object {
        fun fromJson(json: String?, legacyDark: Boolean = true): AppSettings {
            val defaults = AppSettings(theme = if (legacyDark) UiTheme.DARK else UiTheme.LIGHT)
            val j = runCatching { JSONObject(json ?: "{}") }.getOrNull() ?: return defaults
            return defaults.copy(
                theme = runCatching { UiTheme.valueOf(j.optString("theme")) }.getOrDefault(defaults.theme),
                syntax = runCatching { SyntaxTheme.valueOf(j.optString("syntax")) }.getOrDefault(defaults.syntax),
                fontSize = j.optInt("fontSize", defaults.fontSize), terminalFontSize = j.optInt("terminalFontSize", defaults.terminalFontSize),
                tabWidth = j.optInt("tabWidth", defaults.tabWidth), useSpaces = j.optBoolean("useSpaces", defaults.useSpaces),
                lineNumbers = j.optBoolean("lineNumbers", defaults.lineNumbers), wordWrap = j.optBoolean("wordWrap", defaults.wordWrap),
                autoIndent = j.optBoolean("autoIndent", defaults.autoIndent), autoPairs = j.optBoolean("autoPairs", defaults.autoPairs),
                autoCompletion = j.optBoolean("autoCompletion", defaults.autoCompletion), symbolBar = j.optBoolean("symbolBar", defaults.symbolBar),
                collapsePanelOnIme = j.optBoolean("collapsePanelOnIme", defaults.collapsePanelOnIme),
                autoSave = j.optBoolean("autoSave", defaults.autoSave), autoSaveDelayMs = j.optInt("autoSaveDelayMs", defaults.autoSaveDelayMs),
                animations = j.optBoolean("animations", defaults.animations),
                signatureHelp = j.optBoolean("signatureHelp", defaults.signatureHelp),
                completionTab = j.optBoolean("completionTab", defaults.completionTab),
                completionEnter = j.optBoolean("completionEnter", defaults.completionEnter),
                completionArrows = runCatching { CompletionArrows.valueOf(j.optString("completionArrows")) }.getOrDefault(defaults.completionArrows)
            ).normalized()
        }
    }
}

object MotionPolicy {
    fun enabled(appEnabled: Boolean, systemScale: Float, powerSave: Boolean) =
        appEnabled && systemScale.isFinite() && systemScale > 0f && !powerSave
}
