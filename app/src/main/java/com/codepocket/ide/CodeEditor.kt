package com.codepocket.ide

import android.content.Context
import android.widget.EditText

class CodeEditor(context: Context) : EditText(context) {
    var onCaretChanged: (() -> Unit)? = null
    var isHistoryEdit: Boolean = false
        private set

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id != android.R.id.undo && id != android.R.id.redo) return super.onTextContextMenuItem(id)
        val previous = isHistoryEdit
        isHistoryEdit = true
        try { return super.onTextContextMenuItem(id) } finally { isHistoryEdit = previous }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCaretChanged?.invoke()
    }
}
