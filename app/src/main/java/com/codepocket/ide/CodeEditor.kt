package com.codepocket.ide

import android.content.Context
import android.widget.EditText
import android.view.KeyEvent
import android.view.MotionEvent

class CodeEditor(context: Context) : EditText(context) {
    var onCaretChanged: (() -> Unit)? = null
    var onNavigationInput: (() -> Unit)? = null

    // Observe input before native touch dispatch; EditText detects and performs clicks.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) onNavigationInput?.invoke()
        return super.dispatchTouchEvent(event)
    }

    // Accessibility click actions also supersede a pending viewport restoration.
    // EditText retains its native click handling for both touch and accessibility.
    override fun performClick(): Boolean {
        onNavigationInput?.invoke()
        return super.performClick()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) onNavigationInput?.invoke()
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) onNavigationInput?.invoke()
        return super.dispatchKeyEvent(event)
    }

    fun restoreViewport(x: Int, y: Int) {
        val maxX = (computeHorizontalScrollRange() - computeHorizontalScrollExtent()).coerceAtLeast(0)
        val maxY = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
        scrollTo(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    var isHistoryEdit: Boolean = false
        private set

    override fun onTextContextMenuItem(id: Int): Boolean {
        onNavigationInput?.invoke()
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
