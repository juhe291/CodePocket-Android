package com.codepocket.ide

import android.content.Context
import android.widget.EditText
import android.graphics.Canvas
import android.graphics.Rect
import com.codepocket.ide.animation.EditorCaretMotion
import com.codepocket.ide.animation.MotionParticipant
import android.view.KeyEvent
import android.view.MotionEvent

class CodeEditor(context: Context) : EditText(context, null, 0, R.style.WorkbenchEditor), MotionParticipant {
    // Native cursor remains visible to insertion/selection controllers; only its drawable is transparent.
    private var caretMotion: EditorCaretMotion? = null
    init { caretMotion = EditorCaretMotion(this) }
    fun configureMotion(enabled: Boolean, move: Boolean, blink: Boolean, color: Int) {
        caretMotion?.configure(enabled, move, blink, color)
    }
    override fun finishMotion() { caretMotion?.reset() }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); caretMotion?.draw(canvas) }
    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        super.onTextChanged(text, start, before, count); caretMotion?.textChanged(start, before, count)
    }
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt); caretMotion?.reset()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); caretMotion?.reset()
    }
    override fun onFocusChanged(focused: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(focused, direction, previous); caretMotion?.reset()
    }
    override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused); caretMotion?.reset()
    }
    override fun onVisibilityAggregated(visible: Boolean) {
        super.onVisibilityAggregated(visible)
        if (visible) caretMotion?.reset() else caretMotion?.stop()
    }
    override fun onDetachedFromWindow() { caretMotion?.stop(); super.onDetachedFromWindow() }

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
        caretMotion?.selectionChanged(selStart, selEnd)
        onCaretChanged?.invoke()
    }
}
