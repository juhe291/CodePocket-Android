package com.codepocket.ide.terminal.shell;

import android.content.Context;
import java.io.File;

/** Process-local Shell sessions; keeps only application-owned runtimes and initial cwd, never Views. */
public final class ShellSessionRegistry {
    public static final class Session {
        public final ShellRuntime runtime;
        public final File directory;
        Session(ShellRuntime runtime, File directory) { this.runtime = runtime; this.directory = directory; }
    }
    private static ShellSessionRegistry instance;
    public static ShellSessionRegistry get(Context context, File directory) {
        if (instance == null) instance = new ShellSessionRegistry(context.getApplicationContext(), directory);
        return instance;
    }
    public static void finishAll() {
        if (instance == null) return;
        ShellSessionRegistry old = instance; instance = null;
        for (TerminalCatalog.Entry<Session> e : old.catalog.entries()) old.end(e.id);
    }
    private final Context application;
    public final TerminalCatalog<Session> catalog = new TerminalCatalog<>();
    private ShellSessionRegistry(Context context, File directory) {
        application = context;
        catalog.add(new Session(ShellRuntime.create(context), directory));
    }
    public TerminalCatalog.Entry<Session> create(File directory) {
        // Enforce the bound before allocating the runtime/executor.
        if (catalog.entries().size() >= TerminalCatalog.LIMIT) throw new IllegalStateException("最多保留8个Shell会话，请先结束一个会话");
        return catalog.add(new Session(ShellRuntime.create(application), directory));
    }
    public boolean end(long id) {
        TerminalCatalog.Entry<Session> removed = catalog.remove(id);
        if (removed == null) return false;
        removed.value.runtime.shutdown(); return true;
    }
}
