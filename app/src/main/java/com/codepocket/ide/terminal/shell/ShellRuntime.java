package com.codepocket.ide.terminal.shell;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One process-owned PTY session, with no Activity or View retained. */
public final class ShellRuntime implements TerminalSessionClient {
    public interface Observer { void changed(); }
    public enum State { EMPTY, PREPARING, OPEN, EXITED, FAILED }
    private static ShellRuntime instance;
    public static ShellRuntime get(Context context) {
        if (instance == null) instance = new ShellRuntime(context.getApplicationContext());
        return instance;
    }
    private final android.app.Application context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WeakReference<Observer> observer = new WeakReference<>(null);
    private TerminalSession session;
    private ShellLaunchPlan plan;
    private State state = State.EMPTY;
    private String error;
    private long generation;

    private ShellRuntime(Context context) { this.context = (android.app.Application) context; }
    public TerminalSession session() { return session; }
    public ShellLaunchPlan plan() { return plan; }
    public State state() { return state; }
    public String error() { return error; }
    public void attach(Observer listener) { observer = new WeakReference<>(listener); }
    public void detach(Observer listener) { if (observer.get() == listener) observer.clear(); }
    private void notifyObserver() { Observer listener = observer.get(); if (listener != null) listener.changed(); }

    public void start(File directory) {
        if (state == State.PREPARING || (session != null && session.isRunning())) return;
        final long request = ++generation;
        state = State.PREPARING; error = null; notifyObserver();
        io.execute(() -> {
            try {
                ShellLaunchPlan ready = ShellLaunchPlan.prepare(context.getFilesDir(), context.getCacheDir(), directory);
                // Loading the bundled library does not execute a shell or any user commands.
                System.loadLibrary("termux");
                main.post(() -> {
                    if (generation != request) return;
                    // A worker's try/catch cannot catch an exception thrown later by a main-thread Runnable.
                    try {
                        plan = ready;
                        session = new TerminalSession(ShellLaunchPlan.EXECUTABLE, ready.directory.getPath(),
                            ready.arguments, ready.environment, 2000, this);
                        state = State.OPEN;
                        notifyObserver();
                    } catch (RuntimeException | LinkageError failure) {
                        if (generation == request) fail(failure);
                    }
                });
            } catch (Exception | LinkageError failure) {
                main.post(() -> { if (generation == request) fail(failure); });
            }
        });
    }

    public void send(String text) {
        if (session == null || state != State.OPEN || session.getPid() <= 0) return;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        session.write(bytes, 0, bytes.length);
    }
    public void paste() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || session == null || session.getEmulator() == null) return;
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        if (text != null) session.getEmulator().paste(text.toString());
    }
    public void copyTranscript() {
        if (session == null || session.getEmulator() == null) return;
        copy(session.getEmulator().getScreen().getTranscriptText());
    }
    private void copy(String text) {
        ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
            .setPrimaryClip(ClipData.newPlainText("终端记录", text));
    }
    public void close() {
        generation++;
        TerminalSession old = session;
        session = null; plan = null; state = State.EMPTY;
        if (old != null) old.finishIfRunning();
        notifyObserver();
    }
    public void failIfCurrent(TerminalSession expected, Throwable failure) {
        if (expected != null && expected == session) fail(failure);
    }
    public void fail(Throwable failure) {
        generation++;
        TerminalSession old = session;
        session = null; plan = null; state = State.FAILED;
        error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        Log.e("CodePocket-Shell", "Terminal unavailable", failure);
        try {
            ShellFailureLog.write(context.getFilesDir(),
                "Android SDK " + android.os.Build.VERSION.SDK_INT + " / " +
                    java.util.Arrays.toString(android.os.Build.SUPPORTED_ABIS), failure);
        } catch (java.io.IOException | SecurityException loggingFailure) {
            Log.w("CodePocket-Shell", "Cannot save terminal diagnostic", loggingFailure);
        }
        if (old != null) old.finishIfRunning();
        notifyObserver();
    }
    public String failureDetails() {
        try { return ShellFailureLog.read(context.getFilesDir()); }
        catch (java.io.IOException | SecurityException failure) { return "无法读取本机终端错误记录：" + failure.getMessage(); }
    }
    public void copyFailureDetails() { copy(failureDetails()); }
    @Override public void onTextChanged(TerminalSession s) { if (s == session) notifyObserver(); }
    @Override public void onTitleChanged(TerminalSession s) { if (s == session) notifyObserver(); }
    @Override public void onSessionFinished(TerminalSession s) {
        if (s == session) { state = State.EXITED; notifyObserver(); }
    }
    @Override public void onCopyTextToClipboard(TerminalSession s, String text) { if (s == session) copy(text); }
    @Override public void onPasteTextFromClipboard(TerminalSession s) { if (s == session) paste(); }
    @Override public void onBell(TerminalSession s) { }
    @Override public void onColorsChanged(TerminalSession s) { if (s == session) notifyObserver(); }
    @Override public void onTerminalCursorStateChange(boolean enabled) { notifyObserver(); }
    @Override public Integer getTerminalCursorStyle() { return 0; }
    @Override public void logError(String tag, String message) { Log.e(tag, message); }
    @Override public void logWarn(String tag, String message) { Log.w(tag, message); }
    @Override public void logInfo(String tag, String message) { Log.i(tag, message); }
    @Override public void logDebug(String tag, String message) { Log.d(tag, message); }
    @Override public void logVerbose(String tag, String message) { Log.v(tag, message); }
    @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { Log.e(tag, message, e); }
    @Override public void logStackTrace(String tag, Exception e) { Log.e(tag, "Terminal error", e); }
}
