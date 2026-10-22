package com.codepocket.editorlab;

import io.github.rosemoe.sora.lang.styling.*;
import io.github.rosemoe.sora.lang.styling.color.*;
import io.github.rosemoe.sora.lang.styling.span.*;
import io.github.rosemoe.sora.lang.util.PlainTextSpans;
import io.github.rosemoe.sora.text.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read-time lexical overlays. Never mutate or retain pooled TextMate spans, text or selection. */
final class SoraDecorations {
    private static final class Run {
        final int start, end, color;
        Run(int start, int end, int color) { this.start = start; this.end = end; this.color = color; }
    }
    private static final class Line {
        final List<Run> foreground = new ArrayList<>(), background = new ArrayList<>();
    }
    private static final class Snapshot {
        final Content content;
        final long version;
        final Map<Integer, Line> lines;
        Snapshot(Content content, Map<Integer, Line> lines) {
            this.content = content; version = content.getDocumentVersion(); this.lines = lines;
        }
        boolean valid(Content current) { return current == content && content.getDocumentVersion() == version; }
    }
    private volatile Snapshot snapshot;
    private final SoraInputEditor editor;
    SoraDecorations(SoraInputEditor editor) { this.editor = editor; }
    void clear() { snapshot = null; }
    void wrap(Styles styles) {
        if (styles.spans instanceof Layer && ((Layer) styles.spans).owner() == this) {
            ((Layer) styles.spans).cache.clear();
        } else styles.spans = new Layer(styles.spans == null ? new PlainTextSpans(editor.getLineCount()) : styles.spans);
    }
    boolean apply(String source, int[] starts, int[] ends, int[] colors, int first, int second, int background) {
        Content content = editor.getText();
        if ((source.length() > 45_000 && (starts.length > 0 || first >= 0)) || starts.length > 6000 || starts.length != ends.length || starts.length != colors.length ||
                editor.hasComposingText() || !source.contentEquals(content)) return false;
        if (first < -1 || second < -1 || (first < 0) != (second < 0) || first >= source.length() || second >= source.length()) return false;
        Integer[] order = new Integer[starts.length];
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] < 0 || ends[i] <= starts[i] || ends[i] > source.length()) return false;
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt(i -> starts[i]));
        int previous = -1;
        Map<Integer, Line> lines = new HashMap<>();
        for (int i : order) {
            if (starts[i] < previous) return false;
            previous = ends[i];
            add(content, lines, starts[i], ends[i], colors[i], false);
        }
        if (first >= 0) {
            add(content, lines, first, first + 1, background, true);
            if (first != second) add(content, lines, second, second + 1, background, true);
        }
        for (Line line : lines.values()) line.background.sort(Comparator.comparingInt(r -> r.start));
        snapshot = new Snapshot(content, lines);
        Styles styles = editor.getStyles();
        editor.setStyles(styles == null ? new Styles(new PlainTextSpans(content.getLineCount())) : styles);
        return true;
    }
    private static void add(Content content, Map<Integer, Line> lines, int start, int end, int color, boolean background) {
        CharPosition from = content.getIndexer().getCharPosition(start);
        int fromLine = from.line, fromColumn = from.column;
        CharPosition to = content.getIndexer().getCharPosition(end);
        int toLine = to.line, toColumn = to.column;
        for (int line = fromLine; line <= toLine; line++) {
            int a = line == fromLine ? fromColumn : 0, b = line == toLine ? toColumn : content.getColumnCount(line);
            if (a >= b) continue; // Line separators do not produce drawable spans.
            Line target = lines.computeIfAbsent(line, ignored -> new Line());
            (background ? target.background : target.foreground).add(new Run(a, b, color));
        }
    }
    private final class Layer implements Spans {
        final Spans base;
        final Map<Integer, Cached> cache = new ConcurrentHashMap<>();
        Layer(Spans base) { this.base = base; }
        SoraDecorations owner() { return SoraDecorations.this; }
        public void adjustOnInsert(CharPosition start, CharPosition end) { clear(); cache.clear(); base.adjustOnInsert(start, end); }
        public void adjustOnDelete(CharPosition start, CharPosition end) { clear(); cache.clear(); base.adjustOnDelete(start, end); }
        public boolean supportsModify() { return base.supportsModify(); }
        public Modifier modify() { cache.clear(); return base.modify(); }
        public int getLineCount() { return base.getLineCount(); }
        public Reader read() {
            Reader reader = base.read();
            return new Reader() {
                List<Span> current = Collections.emptyList();
                public void moveToLine(int line) {
                    reader.moveToLine(line);
                    current = line < 0 ? Collections.emptyList() : getSpansOnLine(line);
                }
                public int getSpanCount() { return current.size(); }
                public Span getSpanAt(int index) { return current.get(index); }
                public List<Span> getSpansOnLine(int line) {
                    Snapshot state = snapshot;
                    if (state == null || !state.valid(editor.getText()) || !state.lines.containsKey(line)) return reader.getSpansOnLine(line);
                    Cached cached = cache.get(line);
                    if (cached != null && cached.snapshot == state) return cached.spans;
                    List<Span> merged = merge(reader.getSpansOnLine(line), state.lines.get(line));
                    cache.put(line, new Cached(state, merged));
                    return merged;
                }
            };
        }
    }
    private static final class Cached {
        final Snapshot snapshot;
        final List<Span> spans;
        Cached(Snapshot snapshot, List<Span> spans) { this.snapshot = snapshot; this.spans = spans; }
    }
    private static List<Span> merge(List<Span> base, Line line) {
        TreeSet<Integer> boundaries = new TreeSet<>();
        for (Span span : base) boundaries.add(span.getColumn());
        for (Run run : line.foreground) { boundaries.add(run.start); boundaries.add(run.end); }
        for (Run run : line.background) { boundaries.add(run.start); boundaries.add(run.end); }
        List<Span> result = new ArrayList<>();
        int b = 0, f = 0, g = 0;
        for (int column : boundaries) {
            while (b + 1 < base.size() && base.get(b + 1).getColumn() <= column) b++;
            while (f < line.foreground.size() && line.foreground.get(f).end <= column) f++;
            while (g < line.background.size() && line.background.get(g).end <= column) g++;
            int foreground = f < line.foreground.size() && line.foreground.get(f).start <= column ? line.foreground.get(f).color : 0;
            int background = g < line.background.size() && line.background.get(g).start <= column ? line.background.get(g).color : 0;
            Span span = base.isEmpty() ? Span.obtain(0, 0) : base.get(b).copy();
            span.setColumn(column);
            if (foreground != 0 || background != 0) {
                SpanColorResolver original = span.getSpanExt(SpanExtAttrs.EXT_COLOR_RESOLVER);
                ResolvableColor fg = foreground == 0 ? null : new ConstColor(foreground);
                ResolvableColor bg = background == 0 ? null : new ConstColor(background);
                span.setSpanExt(SpanExtAttrs.EXT_COLOR_RESOLVER, new SpanColorResolver() {
                    public ResolvableColor getForegroundColor(Span value) { return fg != null ? fg : original == null ? null : original.getForegroundColor(value); }
                    public ResolvableColor getBackgroundColor(Span value) { return bg != null ? bg : original == null ? null : original.getBackgroundColor(value); }
                });
            }
            result.add(span);
        }
        return Collections.unmodifiableList(result);
    }
}
