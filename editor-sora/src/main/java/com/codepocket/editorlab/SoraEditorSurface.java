package com.codepocket.editorlab;

import android.graphics.Rect;
import com.codepocket.editor.surface.EditorSurface;
import com.codepocket.editor.surface.PopupGeometry;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Pinned Sora 0.23.7 adapter; uses Sora layout, not TextView or guessed line/column widths. */
final class SoraEditorSurface implements EditorSurface {
    private final CodeEditor editor;
    private final Supplier<String> document;
    private final LongSupplier revision;
    SoraEditorSurface(CodeEditor editor, Supplier<String> document, LongSupplier revision) {
        this.editor = editor; this.document = document; this.revision = revision;
    }

    @Override public Anchor anchor() {
        if (!editor.isAttachedToWindow() || !editor.isShown() || !editor.hasFocus() || editor.getWidth() <= 0 ||
            editor.getHeight() <= 0 || (editor.getEditable() && !editor.isEditable()) || editor.hasComposingText() || editor.getCursor().isSelected() || editor.getLayout() == null) return null;
        Rect clip = new Rect();
        if (!editor.getGlobalVisibleRect(clip)) return null;
        int[] root = new int[2], origin = new int[2];
        editor.getRootView().getLocationOnScreen(root); editor.getLocationOnScreen(origin);
        Rect window = new Rect(); editor.getWindowVisibleDisplayFrame(window);
        Bounds visible = PopupGeometry.visible(new Bounds(clip.left, clip.top, clip.right, clip.bottom), root[0], root[1],
            new Bounds(window.left, window.top, window.right, window.bottom),
            new Bounds(origin[0] + (int) Math.ceil(editor.measureTextRegionOffset()), origin[1],
                origin[0] + editor.getWidth(), origin[1] + editor.getHeight()));
        if (visible == null) return null;
        var cursor = editor.getCursor();
        // The pinned Layout contract returns {row bottom, character left}, including wrapped rows.
        float[] point = editor.getLayout().getCharLayoutOffset(cursor.getRightLine(), cursor.getRightColumn());
        return fromLayout(document.get(), revision.getAsLong(), cursor.getRight(), visible, origin[0], origin[1],
            point[0], point[1], editor.measureTextRegionOffset(), editor.getOffsetX(), editor.getOffsetY(), editor.getRowHeight());
    }

    static Anchor fromLayout(String key, long revision, int offset, Bounds visible, int originX, int originY,
        float rowBottom, float columnX, float textInset, int scrollX, int scrollY, int rowHeight) {
        if (rowHeight <= 0) return null;
        double bottom = (double) originY + rowBottom - scrollY;
        return PopupGeometry.anchor(key, revision, offset, visible,
            (double) originX + textInset + columnX - scrollX, bottom - rowHeight, bottom);
    }
}
