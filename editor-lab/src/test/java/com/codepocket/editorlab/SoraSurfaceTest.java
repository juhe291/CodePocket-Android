package com.codepocket.editorlab;

import com.codepocket.editor.surface.EditorSurface;
import io.github.rosemoe.sora.text.Content;
import org.junit.Test;
import static org.junit.Assert.*;

public class SoraSurfaceTest {
    private final EditorSurface.Bounds visible = new EditorSurface.Bounds(100, 200, 600, 700);
    @Test public void realSoraIndexerKeepsUtf16CrlfOffsetsAtTheSharedBoundary() {
        Content content = new Content("# 😀\r\nprint()");
        var point = content.getIndexer().getCharPosition(6);
        assertEquals(1, point.line); assertEquals(0, point.column);
        var anchor = SoraEditorSurface.fromLayout("scratch", 1, point.index, visible, 100, 200,
            80, 40, 20, 5, 10, 20);
        assertNotNull(anchor); assertEquals(6, anchor.offset);
        assertEquals(155, anchor.x); assertEquals(250, anchor.top); assertEquals(270, anchor.bottom);
    }

    @Test public void soraScrollOffsetsAreAppliedExactlyOnce() {
        var a = SoraEditorSurface.fromLayout("scratch", 1, 4, visible, 100, 200, 180, 80, 30, 10, 50, 24);
        var b = SoraEditorSurface.fromLayout("scratch", 1, 4, visible, 100, 200, 180, 80, 30, 30, 70, 24);
        assertNotNull(a); assertNotNull(b);
        assertEquals(20, a.x - b.x); assertEquals(20, a.bottom - b.bottom);
        assertTrue(b.matches(a.document, a.revision, a.offset));
    }

    @Test public void visualRowBottomIsUsedInsteadOfGuessingLogicalLineHeights() {
        var a = SoraEditorSurface.fromLayout("scratch", 1, 0, visible, 100, 200, 240, 40, 20, 0, 100, 28);
        assertNotNull(a); assertEquals(340, a.bottom); assertEquals(312, a.top);
    }

    @Test public void offscreenAndInvalidSoraMeasurementsAreNotPopupAnchors() {
        assertNull(SoraEditorSurface.fromLayout("scratch", 1, 0, visible, 100, 200, 20, 40, 20, 0, 50, 20));
        assertNull(SoraEditorSurface.fromLayout("scratch", 1, 0, visible, 100, 200, 80, Float.NaN, 20, 0, 0, 20));
        assertNull(SoraEditorSurface.fromLayout("scratch", 1, 0, visible, 100, 200, 80, 40, 20, 0, 0, 0));
    }

    @Test public void aRealContentEditRequiresANewVersionEvenIfTheCaretOffsetIsUnchanged() {
        Content content = new Content("print(1)");
        var before = SoraEditorSurface.fromLayout("scratch", 1, content.length(), visible, 100, 200, 80, 40, 20, 0, 0, 20);
        ScratchEdits.replace(content, 6, 7, "2");
        var after = SoraEditorSurface.fromLayout("scratch", 2, content.length(), visible, 100, 200, 80, 40, 20, 0, 0, 20);
        assertEquals("print(2)", content.toString());
        assertNotNull(before); assertNotNull(after);
        assertFalse(after.matches(before.document, before.revision, before.offset));
    }
}
