package com.codepocket.ide.editor

import android.graphics.Rect
import android.view.inputmethod.BaseInputConnection
import com.codepocket.editor.surface.EditorSurface
import com.codepocket.editor.surface.PopupGeometry
import com.codepocket.ide.CodeEditor

/** Reads native layout only. Never copies text or modifies selection/focus/scroll. */
internal object NativeEditorSurface {
    fun capture(editor: CodeEditor, key: String, revision: Long): EditorSurface.Anchor? {
        if (!editor.isAttachedToWindow || !editor.isShown || !editor.hasFocus() || editor.width <= 0 || editor.height <= 0 ||
            editor.selectionStart < 0 || editor.selectionStart != editor.selectionEnd ||
            BaseInputConnection.getComposingSpanStart(editor.text) >= 0) return null
        val layout = editor.layout ?: return null
        val clip = Rect()
        if (!editor.getGlobalVisibleRect(clip)) return null
        val root = IntArray(2); editor.rootView.getLocationOnScreen(root)
        val origin = IntArray(2); editor.getLocationOnScreen(origin)
        val window = Rect(); editor.getWindowVisibleDisplayFrame(window)
        val visible = PopupGeometry.visible(EditorSurface.Bounds(clip.left, clip.top, clip.right, clip.bottom), root[0], root[1],
            EditorSurface.Bounds(window.left, window.top, window.right, window.bottom),
            // totalPaddingBottom includes the blank space below a short document. The popup belongs
            // to the text viewport, not just the ink bounds of its last line.
            EditorSurface.Bounds(origin[0] + editor.compoundPaddingLeft, origin[1] + editor.compoundPaddingTop,
                origin[0] + editor.width - editor.compoundPaddingRight, origin[1] + editor.height - editor.compoundPaddingBottom)) ?: return null
        val offset = editor.selectionStart
        if (offset > editor.length()) return null
        val line = layout.getLineForOffset(offset)
        val x = origin[0].toDouble() + editor.totalPaddingLeft + layout.getPrimaryHorizontal(offset) - editor.scrollX
        val top = origin[1].toDouble() + editor.totalPaddingTop + layout.getLineTop(line) - editor.scrollY
        val bottom = origin[1].toDouble() + editor.totalPaddingTop + layout.getLineBottom(line) - editor.scrollY
        return PopupGeometry.anchor(key, revision, offset, visible, x, top, bottom)
    }
}
