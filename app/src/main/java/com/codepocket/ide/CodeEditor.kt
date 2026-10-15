package com.codepocket.ide

import android.content.Context
import android.widget.EditText

class CodeEditor(context: Context) : EditText(context) {
    var onCaretChanged: (() -> Unit)? = null
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCaretChanged?.invoke()
    }
}
