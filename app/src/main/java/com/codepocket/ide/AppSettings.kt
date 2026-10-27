package com.codepocket.ide

import org.json.JSONObject

enum class UiTheme { DARK, LIGHT, SYSTEM }
enum class SyntaxTheme { FOLLOW_UI, DARK, LIGHT }
enum class TerminalTheme { FOLLOW_UI, CODEPOCKET_DARK, NORD, DRACULA, LIGHT }
enum class CppBackend { CLOUD, LOCAL }

/** Versioned preferences; unknown values fall back without losing project/session preferences. */
data class AppSettings(
    val theme: UiTheme = UiTheme.DARK,
    val syntax: SyntaxTheme = SyntaxTheme.FOLLOW_UI,
    val fontSize: Int = 14,
    val terminalFontSize: Int = 12,
    val terminalTheme: TerminalTheme = TerminalTheme.FOLLOW_UI,
    override val tabWidth: Int = 4,
    override val useSpaces: Boolean = true,
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
    val cursorMovement: Boolean = true,
    val cursorBlink: Boolean = true,
    override val completionTab: Boolean = true,
    override val completionEnter: Boolean = false,
    override val completionArrows: CompletionArrows = CompletionArrows.WHEN_VISIBLE,
    val signatureHelp: Boolean = true,
    val panelPreferSide: Boolean = false,  // P1-B: Bottom-first strategy
    val cppBackend: CppBackend = CppBackend.CLOUD,
    val cppBuildScope: CppBuildScope = CppBuildScope.CURRENT
) : AssistOptionsContract {
    fun normalized() = copy(fontSize = fontSize.coerceIn(10, 28), terminalFontSize = terminalFontSize.coerceIn(10, 24),
        tabWidth = tabWidth.takeIf { it in listOf(2, 4, 8) } ?: 4, autoSaveDelayMs = autoSaveDelayMs.coerceIn(300, 10000))
    fun isDark(systemDark: Boolean) = when (theme) { UiTheme.DARK -> true; UiTheme.LIGHT -> false; UiTheme.SYSTEM -> systemDark }
    fun syntaxIsDark(uiDark: Boolean) = when (syntax) { SyntaxTheme.DARK -> true; SyntaxTheme.LIGHT -> false; SyntaxTheme.FOLLOW_UI -> uiDark }
    fun toJson(): String = normalized().let { s -> JSONObject().apply {
        put("version", 10); put("terminalTheme", s.terminalTheme.name); put("theme", s.theme.name); put("syntax", s.syntax.name)
        put("cppBackend",s.cppBackend.name);put("cppBuildScope",s.cppBuildScope.name)
        put("fontSize", s.fontSize); put("terminalFontSize", s.terminalFontSize); put("tabWidth", s.tabWidth)
        put("useSpaces", s.useSpaces); put("lineNumbers", s.lineNumbers); put("wordWrap", s.wordWrap)
        put("autoIndent", s.autoIndent); put("autoPairs", s.autoPairs); put("autoCompletion", s.autoCompletion)
        put("symbolBar", s.symbolBar); put("collapsePanelOnIme", s.collapsePanelOnIme)
        put("autoSave", s.autoSave); put("autoSaveDelayMs", s.autoSaveDelayMs); put("animations", s.animations)
        put("signatureHelp", s.signatureHelp); put("cursorMovement", s.cursorMovement); put("cursorBlink", s.cursorBlink)
        put("completionTab", s.completionTab); put("completionEnter", s.completionEnter); put("completionArrows", s.completionArrows.name)
        put("panelPreferSide", s.panelPreferSide)
    }.toString() }
    companion object {
        fun fromJson(json: String?, legacyDark: Boolean = true): AppSettings {
            val defaults = AppSettings(theme = if (legacyDark) UiTheme.DARK else UiTheme.LIGHT)
            val j = runCatching { JSONObject(json ?: "{}") }.getOrNull() ?: return defaults
            return defaults.copy(
                cppBackend = runCatching { CppBackend.valueOf(j.optString("cppBackend")) }.getOrDefault(defaults.cppBackend),
                cppBuildScope = runCatching { CppBuildScope.valueOf(j.optString("cppBuildScope")) }.getOrDefault(defaults.cppBuildScope),
                theme = runCatching { UiTheme.valueOf(j.optString("theme")) }.getOrDefault(defaults.theme),
                syntax = runCatching { SyntaxTheme.valueOf(j.optString("syntax")) }.getOrDefault(defaults.syntax),
                terminalTheme = runCatching { TerminalTheme.valueOf(j.optString("terminalTheme")) }.getOrDefault(defaults.terminalTheme),
                fontSize = j.optInt("fontSize", defaults.fontSize), terminalFontSize = j.optInt("terminalFontSize", defaults.terminalFontSize),
                tabWidth = j.optInt("tabWidth", defaults.tabWidth), useSpaces = j.optBoolean("useSpaces", defaults.useSpaces),
                lineNumbers = j.optBoolean("lineNumbers", defaults.lineNumbers), wordWrap = j.optBoolean("wordWrap", defaults.wordWrap),
                autoIndent = j.optBoolean("autoIndent", defaults.autoIndent), autoPairs = j.optBoolean("autoPairs", defaults.autoPairs),
                autoCompletion = j.optBoolean("autoCompletion", defaults.autoCompletion), symbolBar = j.optBoolean("symbolBar", defaults.symbolBar),
                collapsePanelOnIme = j.optBoolean("collapsePanelOnIme", defaults.collapsePanelOnIme),
                autoSave = j.optBoolean("autoSave", defaults.autoSave), autoSaveDelayMs = j.optInt("autoSaveDelayMs", defaults.autoSaveDelayMs),
                animations = j.optBoolean("animations", defaults.animations),
                cursorMovement = j.optBoolean("cursorMovement", defaults.cursorMovement),
                cursorBlink = j.optBoolean("cursorBlink", defaults.cursorBlink),
                signatureHelp = j.optBoolean("signatureHelp", defaults.signatureHelp),
                completionTab = j.optBoolean("completionTab", defaults.completionTab),
                completionEnter = j.optBoolean("completionEnter", defaults.completionEnter),
                // v8 always serialized EXPLICIT, even when never chosen by the user.
                // Migrate that legacy mode for the corrected default; v9 saves all three choices verbatim.
                completionArrows = runCatching { CompletionArrows.valueOf(j.optString("completionArrows")) }.getOrDefault(defaults.completionArrows)
                    .let { if (j.optInt("version", 0) < 9 && it == CompletionArrows.EXPLICIT) CompletionArrows.WHEN_VISIBLE else it },
                panelPreferSide = j.optBoolean("panelPreferSide", defaults.panelPreferSide)
            ).normalized()
        }
    }
}

object MotionPolicy {
    fun enabled(appEnabled: Boolean, systemScale: Float, powerSave: Boolean) =
        appEnabled && systemScale.isFinite() && systemScale > 0f && !powerSave
}
