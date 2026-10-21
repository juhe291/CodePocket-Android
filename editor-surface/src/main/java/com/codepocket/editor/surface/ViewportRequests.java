package com.codepocket.editor.surface;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** UI-thread, latest-only layout work. Never captures or copies document text itself. */
public final class ViewportRequests<S> implements AutoCloseable {
    private final Consumer<Runnable> post;
    private final Predicate<S> valid;
    private final BiPredicate<S, Boolean> matches;
    private final BiConsumer<S, Boolean> commit;
    private long generation;
    private boolean closed;

    public ViewportRequests(Consumer<Runnable> post, Predicate<S> valid,
            BiPredicate<S, Boolean> matches, BiConsumer<S, Boolean> commit) {
        this.post = post; this.valid = valid; this.matches = matches; this.commit = commit;
    }
    public boolean submit(S source, boolean reveal) {
        if (closed || source == null || !valid.test(source) || !matches.test(source, false)) return false;
        final long ticket = ++generation;
        post.accept(() -> {
            if (!closed && ticket == generation) {
                generation++; // A rejected callback must not replay when visibility later changes.
                if (matches.test(source, true)) commit.accept(source, reveal);
            }
        });
        return true;
    }
    public void cancel() { generation++; }
    @Override public void close() { closed = true; cancel(); }
}
