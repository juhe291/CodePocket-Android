package com.codepocket.editorlab;

import com.codepocket.ide.*;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentListener;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual production providers and pinned Sora Content/Cursor/history. No mock candidates or View. */
public class SoraAssistanceTest {
    private final AssistOptions options = new AssistOptions();
    private SoraBuffer open(String code) { SoraBuffer b = SoraBuffer.open("scratch:a", code); select(b, code.length(), code.length()); return b; }
    private void select(SoraBuffer b, int start, int end) {
        CharPosition a = b.content.getIndexer().getCharPosition(start), z = b.content.getIndexer().getCharPosition(end);
        b.content.getCursor().setLeft(a.line, a.column); b.content.getCursor().setRight(z.line, z.column);
    }
    private SoraBuffer.Viewport frame(SoraBuffer b) { return b.viewport(0, 0, 0, false, true); }
    private SoraAssistance.Result analyze(SoraBuffer b, boolean cpp, SoraAssistance.Mode mode) { return SoraAssistance.analyze(b.snapshot(), cpp, mode); }
    private int index(SoraAssistance.Result result, String label) {
        for (int i = 0; i < result.completions.getItems().size(); i++) if (result.completions.getItems().get(i).getLabel().equals(label)) return i;
        throw new AssertionError("Missing actual candidate: " + label);
    }
    private SnippetExpansion snippet(SoraBuffer b, boolean cpp) {
        var expansion = CodeSnippets.INSTANCE.expand(cpp ? "cpp.for" : "py.for",
            new CompletionRequest("scratch:a", b.revision(), b.content.toString(), b.content.length(), b.content.length(), cpp), options, 200000);
        assertNotNull(expansion); var c = expansion.getChange();
        assertTrue(b.apply(b.snapshot(), c.getStart(), c.getEnd(), c.getText(), c.getSelectionStart(), c.getSelectionEnd(), false, true, (a, z) -> select(b, a, z)).accepted());
        return expansion;
    }
    @Test public void pythonCandidatesComeFromTheActualSharedProvider() {
        var b = open("pri"); var result = analyze(b, false, SoraAssistance.Mode.COMPLETION);
        assertNotNull(result); assertNotNull(result.insertion(b.snapshot(), false, index(result, "print"), options));
        assertEquals(LexicalCompletion.INSTANCE.complete(result.request, true).getItems(), result.completions.getItems());
    }
    @Test public void cppCandidatesKeepTheirActualTypeMetadata() {
        var b = open("vec"); var result = analyze(b, true, SoraAssistance.Mode.COMPLETION);
        assertEquals(CompletionKind.TYPE, result.completions.getItems().get(index(result, "vector")).getKind());
    }
    @Test public void newBufferWithIdenticalNameTextAndVersionCannotAcceptOldResult() {
        var a = open("pri"); var result = analyze(a, false, SoraAssistance.Mode.COMPLETION); var b = open("pri");
        assertEquals(a.revision(), b.revision()); assertNull(result.insertion(b.snapshot(), false, 0, options));
    }
    @Test public void languageAndSelectionChangesInvalidateResults() {
        var b = open("pri"); var result = analyze(b, false, SoraAssistance.Mode.COMPLETION);
        assertNull(result.insertion(b.snapshot(), true, 0, options)); select(b, 1, 2);
        assertNull(result.insertion(b.snapshot(), false, 0, options));
    }
    @Test public void editAndUndoToSameTextDoNotReviveOldCandidates() {
        var b = open("pri"); var result = analyze(b, false, SoraAssistance.Mode.COMPLETION);
        b.content.insert(0, 3, "x"); b.content.undo(); assertEquals("pri", b.content.toString()); select(b, 3, 3);
        assertNull(result.insertion(b.snapshot(), false, 0, options)); b.close(); assertFalse(result.matches(b.snapshot(), false));
    }
    @Test public void genuineMemberSemanticsAreNotInvented() {
        for (String text : new String[] {"value.pr", "value->pr", "std::vec"}) {
            assertNull(analyze(open(text), true, SoraAssistance.Mode.COMPLETION));
        }
        assertNull(analyze(open("value.pr"), false, SoraAssistance.Mode.COMPLETION));
    }
    @Test public void commentsAndStringsDoNotProduceCandidates() {
        for (String text : new String[] {"# pri", "'pri", "\"pri"}) assertNull(analyze(open(text), false, SoraAssistance.Mode.COMPLETION));
        assertNull(analyze(open("// vec"), true, SoraAssistance.Mode.COMPLETION));
    }
    @Test public void explicitSnippetChooserListsTheExistingLanguageTemplates() {
        for (boolean cpp : new boolean[] {false, true}) {
            var result = analyze(open("  "), cpp, SoraAssistance.Mode.SNIPPETS);
            assertEquals(6, result.completions.getItems().size());
            for (var item : result.completions.getItems()) {
                assertEquals(CompletionKind.SNIPPET, item.getKind()); assertTrue(item.getSnippetId().startsWith(cpp ? "cpp." : "py."));
            }
        }
    }
    @Test public void actualSnippetAcceptanceIsOneRealUndoUnitInBothLanguages() {
        for (boolean cpp : new boolean[] {false, true}) {
            var b = open("fo"); var result = analyze(b, cpp, SoraAssistance.Mode.SNIPPETS);
            int i = 0; while (!result.completions.getItems().get(i).getSnippetId().equals(cpp ? "cpp.for" : "py.for")) i++;
            var edit = result.insertion(b.snapshot(), cpp, i, options); var c = edit.getChange();
            assertTrue(b.apply(result.source, c.getStart(), c.getEnd(), c.getText(), c.getSelectionStart(), c.getSelectionEnd(), false, true, (a, z) -> select(b, a, z)).accepted());
            assertTrue(b.content.toString().contains(cpp ? "const auto& item" : "for item in"));
            b.content.undo(); assertEquals("fo", b.content.toString()); assertFalse(b.content.canUndo());
            b.content.redo(); assertTrue(b.content.toString().contains("item"));
        }
    }
    @Test public void snippetReplacementDoesNotSwallowAnExpression() {
        assertNull(analyze(open("answer = fo"), false, SoraAssistance.Mode.SNIPPETS));
        assertNull(analyze(open("// fo"), true, SoraAssistance.Mode.SNIPPETS));
    }
    @Test public void budgetSelectionAndCancellationHaveNoFakeFallbackResults() {
        assertNull(analyze(open("x".repeat(45001)), false, SoraAssistance.Mode.COMPLETION));
        var b = open("pri"); select(b, 0, 3); assertNull(analyze(b, false, SoraAssistance.Mode.COMPLETION));
        Thread.currentThread().interrupt();
        try { assertNull(analyze(open("pri"), false, SoraAssistance.Mode.COMPLETION)); }
        finally { Thread.interrupted(); }
    }
    @Test public void pythonParameterHelpUsesTheActualBuiltinReferenceAndActiveKeyword() {
        var result = analyze(open("print('hello', end="), false, SoraAssistance.Mode.SIGNATURE);
        assertNotNull(result.signature); assertEquals("print", result.signature.getReference().getName());
        assertEquals(Integer.valueOf(2), result.signature.active(0)); assertTrue(result.signature.getReference().getSource().contains("docs.python.org/3.13"));
    }
    @Test public void cppReferenceDistinguishesItsActualFormsWithoutTypeInference() {
        var result = analyze(open("std::sort(values.begin(), values.end(), "), true, SoraAssistance.Mode.SIGNATURE);
        assertEquals("std::sort", result.signature.getReference().getName()); assertEquals(2, result.signature.getReference().getForms().size());
        assertEquals(Integer.valueOf(2), result.signature.active(1));
    }
    @Test public void unsupportedCallsAreNotPresentedAsSemanticSignatures() {
        for (String text : new String[] {"custom(", "value.print(", "def print("}) assertNull(analyze(open(text), false, SoraAssistance.Mode.SIGNATURE));
    }
    @Test public void fieldInsertionTracksRealContentAndShiftsLaterStops() {
        var b = open("fo"); var expansion = snippet(b, false); var before = frame(b);
        var session = new SoraAssistance.Snippet(before, expansion.session(before.document(), before.revision, before.length));
        int at = session.session.getActive().getStart(); int oldNext = session.session.getStops().get(1).getStart();
        b.content.insert(0, at, "索");
        session = session.edited(frame(b), at, 0, 1, false);
        assertNotNull(session); assertEquals(oldNext + 1, session.session.getStops().get(1).getStart());
    }
    @Test public void realReplaceDeleteInsertEventsCanShareOneDocumentVersion() {
        var b = open("fo"); var expansion = snippet(b, false); var before = frame(b);
        final SoraAssistance.Snippet[] state = {new SoraAssistance.Snippet(before, expansion.session(before.document(), before.revision, before.length))};
        final int[] events = {0}; final long[] versions = new long[2];
        b.content.addContentListener(new ContentListener() {
            @Override public void beforeReplace(Content text) {}
            @Override public void afterInsert(Content text, int a, int z, int endLine, int endColumn, CharSequence added) {
                versions[events[0]++] = b.revision(); state[0] = state[0].edited(frame(b), text.getCharIndex(a, z), 0, added.length(), false);
            }
            @Override public void afterDelete(Content text, int a, int z, int endLine, int endColumn, CharSequence deleted) {
                versions[events[0]++] = b.revision(); state[0] = state[0].edited(frame(b), text.getCharIndex(a, z), deleted.length(), 0, false);
            }
        });
        var active = state[0].session.getActive(); b.content.replace(active.getStart(), active.getEnd(), "索引");
        assertEquals(2, events[0]); assertEquals(versions[0], versions[1]); assertNotNull(state[0]);
        assertEquals(b.content.length(), state[0].session.getLength()); assertEquals(2, state[0].session.getActive().getEnd() - state[0].session.getActive().getStart());
    }
    @Test public void editsOutsideTheFieldAndHistoryEndOnlyTheTemporarySession() {
        var b = open("fo"); var expansion = snippet(b, false); var before = frame(b);
        var session = new SoraAssistance.Snippet(before, expansion.session(before.document(), before.revision, before.length));
        b.content.insert(0, 0, "#"); String actual = b.content.toString();
        assertNull(session.edited(frame(b), 0, 0, 1, false)); assertNull(session.edited(frame(b), 0, 0, 1, true)); assertEquals(actual, b.content.toString());
    }
    @Test public void compositionAndDifferentBufferCannotMoveTheOldFields() {
        var b = open("fo"); var expansion = snippet(b, false); var before = frame(b);
        var session = new SoraAssistance.Snippet(before, expansion.session(before.document(), before.revision, before.length));
        assertFalse(session.matches(b.viewport(0, 0, 0, true, true))); assertFalse(session.matches(frame(open(b.content.toString()))));
        assertNull(session.edited(b.viewport(0, 0, 0, true, true), session.session.getActive().getStart(), 0, 0, false));
    }
    @Test public void skippedTextEventsCannotRetargetAPlaceholder() {
        var b = open("fo"); var expansion = snippet(b, false); var before = frame(b);
        var session = new SoraAssistance.Snippet(before, expansion.session(before.document(), before.revision, before.length));
        int at = session.session.getActive().getStart(); b.content.insert(0, at, "x"); b.content.insert(0, at, "y");
        assertNull(session.edited(frame(b), at, 0, 1, false));
    }
    @Test public void productionFieldNavigationReachesAllActualStopsAndFinalOffset() {
        var b = open("fo"); var expansion = snippet(b, true); var before = frame(b);
        SnippetSession session = expansion.session(before.document(), before.revision, before.length);
        for (int i = 1; i < expansion.getStops().size(); i++) {
            var moved = session.move(false); session = moved.getSession();
            assertEquals(expansion.getStops().get(i).getStart(), moved.getStart()); assertNotNull(session);
        }
        var end = session.move(false); assertNull(end.getSession()); assertEquals(expansion.getFinalOffset(), end.getStart());
    }
}
