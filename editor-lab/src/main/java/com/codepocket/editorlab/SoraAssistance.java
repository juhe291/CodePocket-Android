package com.codepocket.editorlab;

import com.codepocket.ide.*;

/** Uses the exact production lexical/snippet/reference implementations, never a language service. */
final class SoraAssistance {
    enum Mode { AUTOMATIC, COMPLETION, SNIPPETS, SIGNATURE }
    static final int MAX_CHARS = 45_000;
    static final class Result {
        final SoraBuffer.Snapshot source;
        final boolean cpp;
        final CompletionRequest request;
        final CompletionBatch completions;
        final SignatureResult signature;
        Result(SoraBuffer.Snapshot source, boolean cpp, CompletionRequest request, CompletionBatch completions, SignatureResult signature) {
            this.source = source; this.cpp = cpp; this.request = request; this.completions = completions; this.signature = signature;
        }
        boolean matches(SoraBuffer.Snapshot current, boolean cpp) { return this.cpp == cpp && source.matches(current); }
        CompletionEdit insertion(SoraBuffer.Snapshot current, boolean cpp, int index, AssistOptionsContract options) {
            return matches(current, cpp) && completions != null ? completions.insertion(request, index, options, 200000) : null;
        }
    }
    static Result analyze(SoraBuffer.Snapshot source, boolean cpp, Mode mode) {
        if (source == null || source.text.length() > MAX_CHARS || source.start != source.end || Thread.currentThread().isInterrupted()) return null;
        CompletionRequest request = new CompletionRequest(source.key, source.revision, source.text, source.start, source.end, cpp);
        CompletionBatch batch = null;
        if (mode == Mode.SNIPPETS) {
            var range = CodeSnippets.INSTANCE.replacement(request);
            if (range != null) {
                // Empty prefix intentionally lists actual built-in templates on an otherwise empty line.
                var items = CodeSnippets.INSTANCE.candidates(request, "");
                if (!items.isEmpty()) batch = new CompletionBatch(request, range.getFirst(), range.getSecond(), items);
            }
        } else if (mode != Mode.SIGNATURE) batch = LexicalCompletion.INSTANCE.complete(request, true);
        SignatureResult signature = batch == null && mode != Mode.COMPLETION && mode != Mode.SNIPPETS ? SignatureHelp.INSTANCE.find(request) : null;
        return batch == null && signature == null ? null : new Result(source, cpp, request, batch, signature);
    }
    /** No text copies on keystrokes: only the production placeholder state and a lightweight Content stamp. */
    static final class Snippet {
        final SoraBuffer.Viewport owner;
        final SnippetSession session;
        Snippet(SoraBuffer.Viewport owner, SnippetSession session) { this.owner = owner; this.session = session; }
        boolean matches(SoraBuffer.Viewport current) {
            return owner.sameOwner(current) && !current.composing && session.matches(current.document(), current.revision, current.start, current.end);
        }
        Snippet edited(SoraBuffer.Viewport current, int start, int removed, int inserted, boolean history) {
            if (history || !owner.sameOwner(current) || current.composing || current.revision < session.getRevision() ||
                    current.revision - session.getRevision() > 1) return null;
            var next = session.edited(current.document(), session.getRevision(), start, removed, inserted);
            if (next == null || next.getLength() != current.length) return null;
            return new Snippet(current, next.copy(next.getDocument(), current.revision, next.getLength(), next.getStops(), next.getFinalOffset(), next.getIndex()));
        }
        Snippet move(SoraBuffer.Viewport current, boolean reverse) {
            if (!matches(current)) return null;
            var next = session.move(reverse).getSession();
            return next == null ? null : new Snippet(current, next);
        }
    }
}
