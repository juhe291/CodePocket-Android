package com.codepocket.ide.ui

data class IdePalette(val editor: Int, val surface: Int, val panel: Int, val foreground: Int,
    val muted: Int, val border: Int, val accent: Int, val accentText: Int, val success: Int, val error: Int)

/** Shared shell colors and dimensions. Syntax colors still belong to the language theme. */
object DesignTokens {
    const val TOUCH = 48
    const val ICON = 20
    const val GAP = 4
    const val SNIPPET_HINT = 28
    fun palette(dark: Boolean) = if (dark) IdePalette(0xFF1F1F1F.toInt(), 0xFF181818.toInt(),
        0xFF181818.toInt(), 0xFFCCCCCC.toInt(), 0xFF9D9D9D.toInt(), 0xFF2B2B2B.toInt(),
        0xFF0078D4.toInt(), 0xFF4DAAFC.toInt(), 0xFF89D185.toInt(), 0xFFF48771.toInt())
    else IdePalette(0xFFFFFFFF.toInt(), 0xFFF8F8F8.toInt(), 0xFFF8F8F8.toInt(),
        0xFF1F1F1F.toInt(), 0xFF616161.toInt(), 0xFFE5E5E5.toInt(),
        0xFF005FB8.toInt(), 0xFF005FB8.toInt(), 0xFF237A3B.toInt(), 0xFFBA1A1A.toInt())
}
