package com.codepocket.editorlab;

import com.codepocket.editor.surface.ViewportRequests;
import io.github.rosemoe.sora.text.CharPosition;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real pinned Content/Cursor/Indexer/UndoManager; no mocked Android widget or execution. */
public class SoraNavigationTest {
    private SoraBuffer open(String text) { return SoraBuffer.open("scratch:a", text); }
    private void select(SoraBuffer b, int start, int end) {
        CharPosition a = b.content.getIndexer().getCharPosition(start), z = b.content.getIndexer().getCharPosition(end);
        b.content.getCursor().setLeft(a.line, a.column); b.content.getCursor().setRight(z.line, z.column);
    }
    private boolean select(SoraBuffer b, SoraBuffer.Snapshot s, int a, int z) {
        return b.select(s, a, z, false, true, (left, right) -> select(b, left, right));
    }
    @Test public void actualSelectionUsesUtf16OffsetsAndDoesNotEditTextOrHistory() {
        SoraBuffer b = open("a😀\r\nb"); long revision = b.revision();
        assertTrue(select(b, b.snapshot(), 1, 3));
        assertEquals(1, b.content.getCursor().getLeft()); assertEquals(3, b.content.getCursor().getRight());
        assertEquals(revision, b.revision()); assertEquals("a😀\r\nb", b.content.toString()); assertFalse(b.content.canUndo());
    }
    @Test public void reverseRangeIsNormalizedForThePinnedWidgetCursor() {
        SoraBuffer b = open("abcd"); assertTrue(select(b, b.snapshot(), 3, 1));
        assertEquals(1, b.content.getCursor().getLeft()); assertEquals(3, b.content.getCursor().getRight());
    }
    @Test public void splitEmojiCrlfAndInvalidRangesNeverReachTheWidget() {
        SoraBuffer b = open("a😀\r\nb");
        for (int i : new int[] {-1, 2, 4, 7, Integer.MAX_VALUE})
            assertFalse(b.select(b.snapshot(), i, i, false, true, (a, z) -> fail()));
    }
    @Test public void selectionCannotReplayAfterAnEditOrInAnIdenticalNewBuffer() {
        SoraBuffer a = open("abc"), b = open("abc"); SoraBuffer.Snapshot source = a.snapshot();
        assertFalse(b.select(source, 1, 1, false, true, (x, y) -> fail()));
        a.content.insert(0, 3, "d"); assertFalse(select(a, source, 1, 1));
    }
    @Test public void compositionClosedAndMissingSourceNeverDispatchSelection() {
        SoraBuffer b = open("abc"); SoraBuffer.Snapshot source = b.snapshot();
        assertFalse(b.select(source, 1, 1, true, true, (x, y) -> fail()));
        assertFalse(select(b, null, 1, 1)); b.close(); assertFalse(select(b, source, 1, 1));
    }
    @Test public void explicitSelectionIndependenceStillChecksDocumentProof() {
        SoraBuffer b = open("abc"); SoraBuffer.Snapshot source = b.snapshot(); select(b, 2, 2);
        assertFalse(select(b, source, 1, 1));
        assertTrue(b.select(source, 1, 1, false, false, (x, y) -> select(b, x, y)));
        b.content.insert(0, 3, "d"); assertFalse(b.select(source, 0, 0, false, false, (x, y) -> fail()));
    }
    @Test public void noOpSelectionDoesNotNotifyOrDiscardRedo() {
        SoraBuffer b = open("abc"); b.content.insert(0, 3, "d"); b.content.undo();
        assertTrue(b.content.canRedo()); long revision = b.revision();
        var source = b.snapshot(); assertTrue(b.select(source, source.start, source.end, false, true, (x, y) -> fail()));
        assertEquals(revision, b.revision()); assertTrue(b.content.canRedo());
    }
    @Test public void loadedOversizedDraftCanBeNavigatedWithoutTruncatingIt() {
        SoraBuffer b = open("x".repeat(ScratchEdits.MAX_CHARS + 1));
        assertTrue(select(b, b.snapshot(), b.content.length(), b.content.length()));
        assertEquals(ScratchEdits.MAX_CHARS + 1, b.content.length()); assertFalse(b.content.canUndo());
    }
    @Test public void realIndexerResolvesMixedLineSeparatorsAndTrailingEmptyLine() {
        SoraBuffer b = open("a😀\r\n中文\rb\n"); var source = b.snapshot();
        assertEquals(4, b.content.getLineCount());
        assertEquals(0, b.lineOffset(source, 1, false)); assertEquals(5, b.lineOffset(source, 2, false));
        assertEquals(8, b.lineOffset(source, 3, false)); assertEquals(10, b.lineOffset(source, 4, false));
    }
    @Test public void nonexistentLineIsRejectedRatherThanClampedToTheLastLine() {
        SoraBuffer b = open("x\n");
        for (int line : new int[] {-1, 0, 3, Integer.MAX_VALUE}) assertEquals(-1, b.lineOffset(b.snapshot(), line, false));
        SoraBuffer empty = open(""); assertEquals(0, empty.lineOffset(empty.snapshot(), 1, false));
    }
    @Test public void lineLookupChecksCompositionRevisionAndOriginalSelection() {
        SoraBuffer b = open("one\ntwo"); var source = b.snapshot();
        assertEquals(-1, b.lineOffset(source, 2, true)); select(b, 1, 1);
        assertEquals(-1, b.lineOffset(source, 2, false)); source = b.snapshot(); b.content.insert(0, 0, "x");
        assertEquals(-1, b.lineOffset(source, 2, false));
    }
    @Test public void viewportBindsTheActualContentOwnerNotJustItsNameAndVersion() {
        SoraBuffer a = open("same"), b = open("same"); var source = a.viewport(7, 80, 1, false, true);
        assertFalse(source.matches(b.viewport(7, 80, 1, false, true), false));
        a.close(); assertFalse(source.matches(source, true)); assertNull(a.viewport(0, 0, 0, false, true));
    }
    @Test public void undoToIdenticalTextDoesNotMakeAnOldViewportValidAgain() {
        SoraBuffer b = open("abc"); var source = b.viewport(0, 0, 0, false, true);
        b.content.insert(0, 3, "d"); b.content.undo(); assertEquals("abc", b.content.toString());
        assertFalse(source.matches(b.viewport(0, 0, 0, false, true), true));
    }
    private final class Harness {
        SoraBuffer buffer = open("a😀\nsecond");
        long interaction; boolean composing, visible = true;
        int x = 30, y = 90;
        List<Runnable> tasks = new ArrayList<>(); List<Boolean> commits = new ArrayList<>();
        SoraBuffer.Viewport current() { return buffer.viewport(x, y, interaction, composing, visible); }
        ViewportRequests<SoraBuffer.Viewport> queue = new ViewportRequests<>(tasks::add, s -> s.x >= 0 && s.y >= 0,
            (s, requireVisible) -> s.matches(current(), requireVisible), (s, reveal) -> commits.add(reveal));
        void drain() { for (Runnable action : new ArrayList<>(tasks)) action.run(); tasks.clear(); }
    }
    @Test public void laterRevealWinsAndRepeatedLayoutCallbackCommitsOnlyOnce() {
        Harness h = new Harness(); assertTrue(h.queue.submit(h.current(), false)); assertTrue(h.queue.submit(h.current(), true));
        Runnable last = h.tasks.get(1); h.drain(); last.run(); assertEquals(List.of(true), h.commits);
    }
    @Test public void nativeReflowMayClampScrollWithoutInvalidatingTheSavedPosition() {
        Harness h = new Harness(); var saved = h.current(); assertTrue(h.queue.submit(saved, false));
        h.x = h.y = 0; h.drain(); assertEquals(List.of(false), h.commits);
    }
    @Test public void newInputOrSelectionInvalidatesQueuedScroll() {
        for (boolean edit : new boolean[] {false, true}) {
            Harness h = new Harness(); h.queue.submit(h.current(), false);
            if (edit) h.buffer.content.insert(0, 0, "x"); else select(h.buffer, 1, 1);
            h.drain(); assertTrue(h.commits.isEmpty());
        }
    }
    @Test public void newInteractionInvalidatesEvenWhenTheCursorReturnsToItsOriginalPosition() {
        Harness h = new Harness(); h.queue.submit(h.current(), false);
        select(h.buffer, 1, 1); select(h.buffer, 0, 0); h.interaction++;
        h.drain(); assertTrue(h.commits.isEmpty());
    }
    @Test public void aConsumedHiddenCallbackCannotReplayWhenVisibleAgain() {
        Harness h = new Harness(); h.queue.submit(h.current(), false); Runnable action = h.tasks.get(0);
        h.visible = false; action.run(); h.visible = true; action.run(); assertTrue(h.commits.isEmpty());
    }
    @Test public void compositionAtCommitAndDocumentSwitchSuppressOldScroll() {
        for (boolean compose : new boolean[] {false, true}) {
            Harness h = new Harness(); h.queue.submit(h.current(), false);
            if (compose) h.composing = true; else h.buffer = open("a😀\nsecond");
            h.drain(); assertTrue(h.commits.isEmpty());
        }
    }
    @Test public void hiddenCaptureCanWaitForTheFirstVisibleLayout() {
        Harness h = new Harness(); h.visible = false; assertTrue(h.queue.submit(h.current(), false));
        h.visible = true; h.drain(); assertEquals(List.of(false), h.commits);
    }
    @Test public void cancellationAndClosingInvalidateAlreadyPostedWork() {
        Harness h = new Harness(); h.queue.submit(h.current(), false); h.queue.cancel(); h.drain();
        assertTrue(h.commits.isEmpty()); h.queue.submit(h.current(), false); h.queue.close(); h.drain();
        assertTrue(h.commits.isEmpty()); assertFalse(h.queue.submit(h.current(), false));
    }
    @Test public void invalidCoordinatesAndCompositionNeverScheduleWork() {
        Harness h = new Harness(); h.x = -1; assertFalse(h.queue.submit(h.current(), false)); h.x = 0;
        h.y = -1; assertFalse(h.queue.submit(h.current(), false)); h.y = 0;
        h.composing = true; assertFalse(h.queue.submit(h.current(), false)); assertTrue(h.tasks.isEmpty());
    }
}
