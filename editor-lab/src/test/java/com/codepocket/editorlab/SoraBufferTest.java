package com.codepocket.editorlab;

import com.codepocket.editor.surface.TextEdits;
import com.codepocket.editor.surface.TextEdits.Result;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.TextRange;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual pinned Content, Cursor and UndoManager. Android View/InputConnection are not mocked. */
public class SoraBufferTest {
    private SoraBuffer open(String text) { return SoraBuffer.open("scratch:a", text); }
    private void select(SoraBuffer b, int start, int end) {
        CharPosition a=b.content.getIndexer().getCharPosition(start), z=b.content.getIndexer().getCharPosition(end);
        b.content.getCursor().setLeft(a.line,a.column); b.content.getCursor().setRight(z.line,z.column);
    }
    private Result edit(SoraBuffer b, SoraBuffer.Snapshot source, int start, int end, String text, int a, int z) {
        return b.apply(source,start,end,text,a,z,false,true,(left,right)->select(b,left,right));
    }
    private boolean history(SoraBuffer b, SoraBuffer.Snapshot source, boolean composing, boolean redo) {
        return b.history(source,composing,redo,()->{
            if (redo) b.content.redo();
            else { TextRange range=b.content.undo(); if(range!=null) select(b,range.getStart().index,range.getEnd().index); }
        });
    }
    @Test public void actualContentAndCursorAreCapturedWithTheirRevision() {
        SoraBuffer b=open("a😀\r\nb"); select(b,1,3);
        SoraBuffer.Snapshot s=b.snapshot();
        assertEquals(b.content.toString(),s.text); assertEquals(b.revision(),s.revision);
        assertEquals(1,s.start); assertEquals(3,s.end); assertEquals("scratch:a",s.key);
    }
    @Test public void commandsUseActualContentAndSelectTheRequestedRange() {
        SoraBuffer b=open("a😀\r\nb"); select(b,1,3); long revision=b.revision();
        assertEquals(Result.APPLIED,edit(b,b.snapshot(),1,3,"中文",1,3));
        assertEquals("a中文\r\nb",b.content.toString()); assertEquals(1,b.content.getCursor().getLeft());
        assertEquals(3,b.content.getCursor().getRight()); assertTrue(b.revision()>revision);
    }
    @Test public void replacementIsOneRealUndoUnitAndRedoWorks() {
        SoraBuffer b=open("print(1)");
        assertEquals(Result.APPLIED,edit(b,b.snapshot(),6,7,"42",8,8));
        assertTrue(history(b,b.snapshot(),false,false)); assertEquals("print(1)",b.content.toString());
        assertFalse(b.content.canUndo()); assertTrue(b.content.canRedo());
        assertTrue(history(b,b.snapshot(),false,true)); assertEquals("print(42)",b.content.toString());
    }
    @Test public void staleRevisionCannotOverwriteNewInput() {
        SoraBuffer b=open("abc"); SoraBuffer.Snapshot source=b.snapshot();
        b.content.insert(0,3,"d"); String actual=b.content.toString();
        assertEquals(Result.STALE,edit(b,source,0,3,"old",3,3)); assertEquals(actual,b.content.toString());
    }
    @Test public void editingThenUndoingToSameTextStillInvalidatesOldSnapshot() {
        SoraBuffer b=open("abc"); SoraBuffer.Snapshot source=b.snapshot();
        b.content.insert(0,3,"d"); b.content.undo(); assertEquals(source.text,b.content.toString());
        assertEquals(Result.STALE,edit(b,source,0,1,"z",1,1));
    }
    @Test public void identicalTextAndKeyInAnotherDocumentCannotReplayAnEdit() {
        SoraBuffer a=open("abc"), b=open("abc");
        assertEquals(a.revision(),b.revision());
        assertEquals(Result.STALE,edit(b,a.snapshot(),0,1,"z",1,1)); assertFalse(b.content.canUndo());
    }
    @Test public void cursorMovementInvalidatesSelectionBasedCommand() {
        SoraBuffer b=open("abc"); SoraBuffer.Snapshot source=b.snapshot(); select(b,1,2);
        assertEquals(Result.STALE,edit(b,source,0,0,"z",1,1)); assertEquals("abc",b.content.toString());
    }
    @Test public void explicitSelectionIndependentEditStillChecksDocumentRevision() {
        SoraBuffer b=open("abc"); SoraBuffer.Snapshot source=b.snapshot(); select(b,1,1);
        assertEquals(Result.APPLIED,b.apply(source,0,1,"z",1,1,false,false,(a,z)->select(b,a,z)));
        assertEquals(Result.STALE,b.apply(source,0,1,"x",1,1,false,false,(a,z)->fail()));
    }
    @Test public void compositionRejectsEditsAndHistoryWithoutCallbacks() {
        SoraBuffer b=open("abc"); SoraBuffer.Snapshot source=b.snapshot();
        assertEquals(Result.COMPOSING,b.apply(source,0,0,"z",1,1,true,true,(a,z)->fail()));
        b.content.insert(0,3,"d");
        assertFalse(b.history(b.snapshot(),true,false,()->fail())); assertEquals("abcd",b.content.toString());
    }
    @Test public void closedDocumentHasNoSnapshotAndNeverDispatchesWork() {
        SoraBuffer b=open("abc"); SoraBuffer.Snapshot source=b.snapshot(); b.close();
        assertNull(b.snapshot()); assertEquals(Result.NO_DOCUMENT,edit(b,source,0,1,"z",1,1));
        assertFalse(b.history(source,false,false,()->fail()));
    }
    @Test public void emptyHistoryAndStaleHistoryDoNotCallTheWidget() {
        SoraBuffer b=open("abc"); assertFalse(b.history(b.snapshot(),false,false,()->fail()));
        SoraBuffer.Snapshot source=b.snapshot(); b.content.insert(0,3,"d");
        assertFalse(b.history(source,false,false,()->fail())); assertTrue(b.content.canUndo());
        assertFalse(b.history(b.snapshot(),false,true,()->fail()));
    }
    @Test public void newDocumentStartsWithoutPreviousUndoOrRedo() {
        SoraBuffer old=open("one"); edit(old,old.snapshot(),0,3,"two",3,3);
        history(old,old.snapshot(),false,false); assertTrue(old.content.canRedo()); old.close();
        SoraBuffer next=SoraBuffer.open("scratch:b","next");
        assertFalse(next.content.canUndo()); assertFalse(next.content.canRedo());
        assertFalse(history(next,next.snapshot(),false,false)); assertEquals("next",next.content.toString());
        assertEquals(Result.STALE,edit(next,old.snapshot(),0,0,"x",1,1));
    }
    @Test public void noOpPreservesRevisionAndRedoHistory() {
        SoraBuffer b=open("abc"); edit(b,b.snapshot(),3,3,"d",4,4); history(b,b.snapshot(),false,false);
        long revision=b.revision(); SoraBuffer.Snapshot s=b.snapshot();
        assertEquals(Result.UNCHANGED,edit(b,s,0,0,"",s.start,s.end));
        assertEquals(revision,b.revision()); assertTrue(b.content.canRedo());
    }
    @Test public void selectionOnlyDoesNotCreateAnUndoEntryOrChangeVersion() {
        SoraBuffer b=open("abc"); long revision=b.revision();
        assertEquals(Result.SELECTION_ONLY,edit(b,b.snapshot(),0,0,"",1,2));
        assertEquals(revision,b.revision()); assertFalse(b.content.canUndo());
        assertEquals(1,b.content.getCursor().getLeft()); assertEquals(2,b.content.getCursor().getRight());
    }
    @Test public void splittingEmojiAndCrLfIsRejectedWithoutHistory() {
        SoraBuffer b=open("a😀\r\nb");
        assertEquals(Result.INVALID_RANGE,edit(b,b.snapshot(),2,3,"z",2,2));
        assertEquals(Result.INVALID_RANGE,edit(b,b.snapshot(),4,4,"z",5,5)); assertFalse(b.content.canUndo());
    }
    @Test public void newlyCreatedCrLfSeamCannotContainTheSelection() {
        SoraBuffer b=open("\rX\n");
        assertEquals(Result.INVALID_RANGE,edit(b,b.snapshot(),1,2,"",1,1));
        assertEquals("\rX\n",b.content.toString()); assertFalse(b.content.canUndo());
        assertEquals(Result.APPLIED,edit(b,b.snapshot(),1,2,"",2,2)); assertEquals("\r\n",b.content.toString());
    }
    @Test public void malformedReplacementAndInvalidNewSelectionDoNotMutate() {
        SoraBuffer b=open("abc");
        assertEquals(Result.INVALID_TEXT,edit(b,b.snapshot(),0,0,"\ud800",1,1));
        assertEquals(Result.INVALID_TEXT,edit(b,b.snapshot(),0,0,"\udc00",1,1));
        assertEquals(Result.INVALID_RANGE,edit(b,b.snapshot(),0,0,"😀",1,1));
        assertEquals(Result.INVALID_RANGE,edit(b,b.snapshot(),0,0,"x",5,5)); assertFalse(b.content.canUndo());
    }
    @Test public void overLimitCommandIsRejectedButAnInBudgetReplacementWorks() {
        SoraBuffer b=open("x".repeat(ScratchEdits.MAX_CHARS)); long rev=b.revision();
        assertEquals(Result.LIMIT_EXCEEDED,edit(b,b.snapshot(),0,0,"y",1,1)); assertEquals(rev,b.revision());
        assertFalse(b.content.canUndo()); assertEquals(Result.APPLIED,edit(b,b.snapshot(),0,1,"y",1,1));
    }
    @Test public void restoringOversizedOrMalformedExistingScratchIsLossless() {
        String source="x".repeat(ScratchEdits.MAX_CHARS+1)+"\ud800";
        SoraBuffer b=open(source); assertEquals(source,b.content.toString()); assertFalse(b.content.canUndo());
        assertEquals(Result.LIMIT_EXCEEDED,edit(b,b.snapshot(),0,0,"z",1,1));
        assertEquals(Result.APPLIED,edit(b,b.snapshot(),ScratchEdits.MAX_CHARS-1,source.length(),"",0,0));
    }
    @Test public void savedSelectionRestorationUsesSafeActualIndexerPositions() {
        SoraBuffer b=open("a😀\r\nb"); int[] s=TextEdits.restoreSelection(b.content,2,4);
        assertArrayEquals(new int[]{1,5},s); select(b,s[0],s[1]);
        assertEquals(1,b.snapshot().start); assertEquals(5,b.snapshot().end);
        assertArrayEquals(new int[]{1,1},TextEdits.restoreSelection(b.content,2,2));
    }
    @Test public void ordinaryTypingIsSeenWithoutAnActivityRevisionCounter() {
        SoraBuffer b=open("abc"); SoraBuffer.Snapshot old=b.snapshot(); b.content.insert(0,3,"😀\r\n");
        SoraBuffer.Snapshot typed=b.snapshot(); assertTrue(typed.revision>old.revision);
        assertEquals("abc😀\r\n",typed.text); assertEquals(Result.STALE,b.sourceFailure(old,false,true));
        assertNull(b.sourceFailure(typed,false,true));
    }
    @Test public void editAfterUndoInvalidatesRealRedo() {
        SoraBuffer b=open("abc"); edit(b,b.snapshot(),3,3,"d",4,4); history(b,b.snapshot(),false,false);
        assertTrue(b.content.canRedo()); assertEquals(Result.APPLIED,edit(b,b.snapshot(),0,1,"z",1,1));
        assertFalse(b.content.canRedo()); assertEquals("zbc",b.content.toString());
    }
}
