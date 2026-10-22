package com.codepocket.editorlab;

import io.github.rosemoe.sora.lang.styling.*;
import io.github.rosemoe.sora.lang.styling.color.*;
import io.github.rosemoe.sora.lang.styling.span.*;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Shared production overlay on the actual pinned Sora style API, not a second editor implementation. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class SoraDecorationsTest {
    private SoraInputEditor editor;
    private MappedSpans base;
    private Styles styles;
    @Before public void setup() {
        editor = new SoraInputEditor(RuntimeEnvironment.getApplication());
        editor.installDecorations(); editor.setText("abcdef");
        MappedSpans.Builder builder = new MappedSpans.Builder();
        builder.add(0, Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, true, false)));
        builder.add(0, Span.obtain(3, TextStyle.makeStyle(EditorColorScheme.COMMENT)));
        base = builder.build(); styles = new Styles(base); editor.setStyles(styles);
    }
    @After public void release() { editor.release(); }
    private Span at(int column) {
        var spans = editor.getStyles().spans.read().getSpansOnLine(0);
        Span result = spans.get(0); for (Span span : spans) if (span.getColumn() <= column) result = span;
        return result;
    }
    private int color(Span span, boolean background) {
        SpanColorResolver resolver = span.getSpanExt(SpanExtAttrs.EXT_COLOR_RESOLVER);
        ResolvableColor value = resolver == null ? null : background ? resolver.getBackgroundColor(span) : resolver.getForegroundColor(span);
        return value == null ? 0 : value.resolve(editor.getColorScheme());
    }
    @Test public void splittingDoesNotMutatePooledGrammarSpansOrStyleBits() {
        long original = base.read().getSpansOnLine(0).get(0).getStyle();
        assertTrue(editor.decorate("abcdef",new int[]{1},new int[]{2},new int[]{0xff123456},-1,-1,0));
        assertEquals(0xff123456,color(at(1),false));assertEquals(0,color(at(2),false));
        assertEquals(original,at(1).getStyle());
        assertEquals(2,base.read().getSpansOnLine(0).size());
        assertFalse(base.read().getSpansOnLine(0).get(0).hasSpanExt(SpanExtAttrs.EXT_COLOR_RESOLVER));
    }
    @Test public void foregroundAndBracketBackgroundPreserveExistingResolver() {
        Span original=base.read().getSpansOnLine(0).get(0);
        original.setSpanExt(SpanExtAttrs.EXT_COLOR_RESOLVER,new SpanConstColorResolver(0xff345678,0xff234567));
        assertTrue(editor.decorate("abcdef",new int[]{1},new int[]{3},new int[]{0xff123456},1,5,0xff456789));
        assertEquals(0xff123456,color(at(1),false));assertEquals(0xff456789,color(at(1),true));
        assertEquals(0xff234567,color(at(2),true));assertEquals(0xff345678,color(at(0),false));
        assertEquals(0xff234567,color(original,true));
    }
    @Test public void styleRefreshAndUnderlyingModifiersInvalidateMergedCache() {
        assertTrue(editor.decorate("abcdef",new int[]{1},new int[]{2},new int[]{0xff123456},-1,-1,0));
        assertEquals(0xff123456,color(at(1),false));
        editor.getStyles().spans.modify().setSpansOnLine(0,java.util.List.of(Span.obtain(0,TextStyle.makeStyle(EditorColorScheme.LITERAL))));
        editor.updateStyles(styles,null);
        assertEquals(EditorColorScheme.LITERAL,at(1).getForegroundColorId());assertEquals(0xff123456,color(at(1),false));
        assertEquals(0,color(at(2),false));
    }
    @Test public void actualContentMutationImmediatelyDropsOldOffsets() {
        assertTrue(editor.decorate("abcdef",new int[]{1},new int[]{2},new int[]{0xff123456},-1,-1,0));
        assertEquals(0xff123456,color(at(1),false));editor.getText().insert(0,0,"x");
        assertEquals(0,color(at(1),false));assertEquals(0,color(at(2),false));
        assertFalse(editor.decorate("abcdef",new int[]{1},new int[]{2},new int[]{0xff123456},-1,-1,0));
    }
    @Test public void malformedOrOverlappingRangesDoNotReplaceLastGoodSnapshot() {
        assertTrue(editor.decorate("abcdef",new int[]{1},new int[]{2},new int[]{0xff123456},-1,-1,0));
        assertFalse(editor.decorate("abcdef",new int[]{1,2},new int[]{3,4},new int[]{1,2},-1,-1,0));
        assertFalse(editor.decorate("abcdef",new int[]{-1},new int[]{2},new int[]{1},-1,-1,0));
        assertFalse(editor.decorate("abcdef",new int[]{1},new int[]{2},new int[]{},-1,-1,0));
        assertEquals(0xff123456,color(at(1),false));
    }
    @Test public void utf16CrLfAndAdjacentRangesHaveCorrectBoundaries() {
        editor.setText("a\r\n中😀x");
        assertTrue(editor.decorate(editor.getText().toString(),new int[]{3,4,6},new int[]{4,6,7},new int[]{0xff123456,0xff234567,0xff345678},-1,-1,0));
        var spans=editor.getStyles().spans.read().getSpansOnLine(1);
        assertEquals(0,spans.get(0).getColumn());assertEquals(0xff123456,color(spans.get(0),false));
        assertEquals(1,spans.get(1).getColumn());assertEquals(0xff234567,color(spans.get(1),false));
        assertEquals(3,spans.get(2).getColumn());assertEquals(0xff345678,color(spans.get(2),false));
    }
}
