package com.codepocket.ide.editor

/** Shared lexical palette; these colours do not imply semantic type analysis. */
object EditorPalette {
    fun color(kind: String, dark: Boolean): Int? {
        if (kind.startsWith("bracket")) {
            val depth = kind.removePrefix("bracket").toIntOrNull() ?: return null
            val colors = if (dark) intArrayOf(0xFFD9C184.toInt(), 0xFFA99BC8.toInt(), 0xFF8AB5C5.toInt(), 0xFFC799A3.toInt(), 0xFF91BAA2.toInt(), 0xFFB8AC8D.toInt())
                else intArrayOf(0xFF805F16.toInt(), 0xFF71559B.toInt(), 0xFF246D86.toInt(), 0xFF9C4962.toInt(), 0xFF377450.toInt(), 0xFF795D3E.toInt())
            return colors.getOrNull(depth)
        }
        return when (kind) {
        "variable" -> if (dark) 0xFF9DC5DF.toInt() else 0xFF28557D.toInt()
        "parameter" -> if (dark) 0xFFD0BEA0.toInt() else 0xFF775B2E.toInt()
        "property" -> if (dark) 0xFFBAADD0.toInt() else 0xFF75518F.toInt()
        "constant" -> if (dark) 0xFFB4C7A0.toInt() else 0xFF4F6F32.toInt()
        "keyword" -> if (dark) 0xFF569CD6.toInt() else 0xFF7E4E94.toInt()
        "type" -> if (dark) 0xFF4EC9B0.toInt() else 0xFF286B7D.toInt()
        "function" -> if (dark) 0xFFDCDCAA.toInt() else 0xFF795E26.toInt()
        "string" -> if (dark) 0xFFCE9178.toInt() else 0xFF8E4630.toInt()
        "number" -> if (dark) 0xFFB5CEA8.toInt() else 0xFF4F6F47.toInt()
        "comment" -> if (dark) 0xFF6A9955.toInt() else 0xFF617667.toInt()
        "preprocessor" -> if (dark) 0xFFC586C0.toInt() else 0xFF7E4E94.toInt()
        else -> null
        }
    }
}
