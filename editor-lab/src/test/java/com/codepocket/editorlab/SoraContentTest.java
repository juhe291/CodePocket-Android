package com.codepocket.editorlab;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.CharPosition;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executes the actual Sora AAR's text/index/history classes. No View mocks or fake editor backend. */
public class SoraContentTest {
    @Test public void unicodeAndCrLfRoundTripWithoutNormalization() {
        String source = "# 中文 😀\r\nprint('你好')\r\n";
        Content content = new Content(source);
        assertEquals(source, content.toString()); assertEquals(source.length(), content.length());
        assertEquals(3, content.getLineCount());
    }
    @Test public void everyUtf16IndexRoundTripsThroughTheRealIndexer() {
        Content content = new Content("a😀\r\n中\nlast");
        for (int i = 0; i <= content.length(); i++) {
            CharPosition point = content.getIndexer().getCharPosition(i);
            assertEquals(i, content.getCharIndex(point.line, point.column));
        }
    }
    @Test public void replacementUsesUtf16RatherThanCodePointOffsets() {
        Content content = new Content("😀value");
        assertTrue(ScratchEdits.replace(content, 2, 7, "result"));
        assertEquals("😀result", content.toString());
    }
    @Test public void supplementaryCharacterCannotBeSplitByATouchCommand() {
        Content content = new Content("a😀b");
        assertFalse(ScratchEdits.replace(content, 2, 3, "x"));
        assertFalse(ScratchEdits.replace(content, 1, 2, "x"));
        assertEquals("a😀b", content.toString());
    }
    @Test public void crLfCannotBeSplitByATouchCommand() {
        Content content = new Content("a\r\nb");
        assertFalse(ScratchEdits.replace(content, 2, 2, "x"));
        assertTrue(ScratchEdits.replace(content, 1, 3, "\n"));
        assertEquals("a\nb", content.toString());
    }
    @Test public void replacementUndoAndRedoRestoreRealContent() {
        Content content = new Content("print(1)");
        assertTrue(ScratchEdits.replace(content, 6, 7, "42"));
        assertEquals("print(42)", content.toString()); assertTrue(content.canUndo());
        content.undo(); assertEquals("print(1)", content.toString()); assertTrue(content.canRedo());
        content.redo(); assertEquals("print(42)", content.toString());
    }
    @Test public void batchInsertionIsOneUndoUnit() {
        Content content = new Content("abc");
        content.beginBatchEdit();
        content.insert(0, 3, "()"); content.insert(0, 4, "value"); content.endBatchEdit();
        assertEquals("abc(value)", content.toString());
        content.undo(); assertEquals("abc", content.toString()); assertFalse(content.canUndo());
    }
    @Test public void newEditAfterUndoInvalidatesRedo() {
        Content content = new Content("a");
        ScratchEdits.replace(content, 1, 1, "b"); content.undo();
        ScratchEdits.replace(content, 1, 1, "c");
        assertEquals("ac", content.toString()); assertFalse(content.canRedo());
    }
    @Test public void multilineReplacementPreservesUnaffectedTextAndLineIndices() {
        Content content = new Content("head\r\nold\r\ntail");
        ScratchEdits.replace(content, 6, 9, "new\nline");
        assertEquals("head\r\nnew\nline\r\ntail", content.toString());
        CharPosition tail = content.getIndexer().getCharPosition(content.length() - 4);
        assertEquals(3, tail.line); assertEquals(0, tail.column);
    }
    @Test public void denseLongContentEditsAndUndoesWithoutTruncating() {
        String source = "x ".repeat(22000);
        Content content = new Content(source);
        ScratchEdits.replace(content, source.length(), source.length(), "😀");
        assertEquals(source + "😀", content.toString());
        content.undo(); assertEquals(source, content.toString());
    }
    @Test public void overBudgetCommandIsRejectedBeforeChangingContentOrHistory() {
        Content content = new Content("x".repeat(ScratchEdits.MAX_CHARS));
        assertFalse(ScratchEdits.replace(content, 0, 0, "1"));
        assertEquals(ScratchEdits.MAX_CHARS, content.length()); assertFalse(content.canUndo());
        assertTrue(ScratchEdits.replace(content, 0, 1, "y"));
    }
    @Test public void invalidRangesDoNotMutateTheScratchDocument() {
        Content content = new Content("text");
        assertFalse(ScratchEdits.replace(content, -1, 0, ""));
        assertFalse(ScratchEdits.replace(content, 3, 2, ""));
        assertFalse(ScratchEdits.replace(content, 0, 5, ""));
        assertEquals("text", content.toString()); assertFalse(content.canUndo());
    }
    @Test public void emptyDocumentAndEndOfFileInsertionsAreSupported() {
        Content content = new Content("");
        assertTrue(ScratchEdits.replace(content, 0, 0, "\n😀"));
        assertEquals("\n😀", content.toString());
        content.undo(); assertEquals("", content.toString());
    }
    @Test public void headerAndPythonSamplesKeepTheirLanguageIndependentText() {
        for (String source : new String[]{"#include <vector>\nint main() { return 0; }\n", "def solve():\n    print('中')\n"}) {
            Content content = new Content(source);
            assertEquals(source, content.toString());
            assertFalse(content.canUndo());
        }
    }
}
