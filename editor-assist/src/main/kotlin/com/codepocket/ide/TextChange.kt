package com.codepocket.ide

data class TextChange(val start: Int, val end: Int, val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)
