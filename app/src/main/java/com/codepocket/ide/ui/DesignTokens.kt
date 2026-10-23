package com.codepocket.ide.ui

import android.content.Context
import com.codepocket.ide.AppSettings

data class IdePalette(val editor: Int, val surface: Int, val panel: Int, val foreground: Int,
    val muted: Int, val border: Int, val accent: Int, val accentText: Int, val success: Int, val error: Int) {
    val overlay: Int get() = if (editor == 0xFFFFFFFF.toInt()) 0xFFFFFFFF.toInt() else 0xFF252D38.toInt()
    val selection: Int get() = if (editor == 0xFFFFFFFF.toInt()) 0xFFE2ECF7.toInt() else 0xFF2A3B50.toInt()
}
object DesignTokens {
    const val TOUCH = 48
    const val ICON = 20
    const val GAP = 4
    const val SNIPPET_HINT = 28
    fun palette(dark: Boolean) = if (dark) IdePalette(0xFF1F2228.toInt(), 0xFF181B20.toInt(),
        0xFF1B1E24.toInt(), 0xFFD7DFEA.toInt(), 0xFF969EAC.toInt(), 0xFF30353D.toInt(),
        0xFF6189B7.toInt(), 0xFF9ABCE5.toInt(), 0xFFA4C8AC.toInt(), 0xFFE7A09F.toInt())
    else IdePalette(0xFFFFFFFF.toInt(), 0xFFF1F2F4.toInt(), 0xFFF7F8FA.toInt(),
        0xFF263445.toInt(), 0xFF596A7D.toInt(), 0xFFD8E0EA.toInt(),
        0xFF376797.toInt(), 0xFF315F90.toInt(), 0xFF326B46.toInt(), 0xFFAB3B3B.toInt())
    fun forContext(context: Context): IdePalette {
        val prefs = context.getSharedPreferences("codepocket_v2", Context.MODE_PRIVATE)
        val dark = context.resources.configuration.uiMode and 0x30 == 0x20
        return palette(AppSettings.fromJson(prefs.getString("settings", null), prefs.getBoolean("dark", true)).isDark(dark))
    }
}
