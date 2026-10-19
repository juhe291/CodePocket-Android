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
    public interface RunControl {
        boolean control(int code);
        boolean acceptsInput();
        boolean output(int bytes);
        String label();
        void closed();
    }
    private RunControl foreground;
    private boolean promptSeen, promptReady;
    private int historyTopRow;
    public int historyTopRow() { return historyTopRow; }
    public void saveHistoryTopRow(int row) { historyTopRow=Math.min(0,row); }
    private String pendingCommand;
    private Runnable commandDispatched;
    private java.util.function.Consumer<String> pendingCommandFailure;
    private final StringBuilder externalOutput = new StringBuilder();
    public enum State { EMPTY, PREPARING, OPEN, EXITED, FAILED }
    private static ShellRuntime instance;
    public static ShellRuntime get(Context context) {
        if (instance == null) instance = new ShellRuntime(context.getApplicationContext());
        return instance;
    }
    public static ShellRuntime create(Context context) { return new ShellRuntime(context.getApplicationContext()); }
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
        if(plan!=null)plan.startup.delete();
        final long request = ++generation;
        state = State.PREPARING; error = null; promptSeen=false;promptReady=false;notifyObserver();
        io.execute(() -> {
            try {
                ShellLaunchPlan ready = ShellLaunchPlan.prepare(context.getFilesDir(), context.getCacheDir(), directory);
                // Loading the bundled library does not execute a shell or any user commands.
                try { System.loadLibrary("termux"); }
                catch (LinkageError failure) { ready.startup.delete();throw failure; }
                main.post(() -> {
                    if (generation != request) { ready.startup.delete();return; }
                    // A worker's try/catch cannot catch an exception thrown later by a main-thread Runnable.
                    try {
                        plan = ready;
                        session = new TerminalSession(ShellLaunchPlan.EXECUTABLE, ready.directory.getPath(),
                            ready.arguments, ready.environment, 2000, this);
                        // A GONE/IME-collapsed terminal has no measured View. Start its real PTY anyway.
                        // A later View resize updates the existing PTY; it must not own process creation.
                        session.updateSize(80,24,8,16);
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
        // Stop/EOF policy must also work while a real PTY is still starting.
        if (filterInput(text)) return;
        if (session == null || state != State.OPEN || session.getPid() <= 0) return;
        writeRaw(text);
    }
    private void writeRaw(String text) {
        if(session==null||session.getPid()<=0)return;
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);session.write(bytes,0,bytes.length);
    }
    public boolean beginForeground(RunControl control) {
        if(foreground!=null)return false;
        if(session!=null&&session.getPid()>0&&promptSeen&&(!promptReady||!session.isShellForeground()))return false;
        foreground=control;notifyObserver();return true;
    }
    public void clearForeground(RunControl control) {
        if(foreground!=control)return;
        foreground=null;pendingCommand=null;pendingCommandFailure=null;commandDispatched=null;notifyObserver();
    }
    public boolean filterInput(String text) {
        RunControl control=foreground;if(control==null)return false;
        if(text.equals("\u0003")||text.equals("\u0004"))return control.control(text.charAt(0));
        return !control.acceptsInput();
    }
    public boolean acceptsInput() { return foreground==null||foreground.acceptsInput(); }
    public String foregroundLabel() { return foreground==null?"":foreground.label(); }
    public void refreshForeground() { notifyObserver(); }
    public void queueCommand(String command,java.util.function.Consumer<String> failure) {
        queueCommand(command,failure,()->{});
    }
    public void queueCommand(String command,java.util.function.Consumer<String> failure,Runnable dispatched) {
        if(pendingCommand!=null)throw new IllegalStateException("A launch is already queued");
        commandDispatched=dispatched;
        pendingCommand=command;pendingCommandFailure=failure;pumpCommand();
    }
    private void pumpCommand() {
        if(pendingCommand==null||!promptReady||session==null||session.getPid()<=0)return;
        String command=pendingCommand;java.util.function.Consumer<String> failure=pendingCommandFailure;
        Runnable dispatched=commandDispatched;commandDispatched=null;
        pendingCommand=null;pendingCommandFailure=null;
        if(!session.isShellForeground()) { failure.accept("当前终端有前台命令，请先结束该命令或新建终端");return; }
        // Clear both halves of a partially edited mksh line without interrupting a foreground job.
        promptReady=false;writeRaw("\u0015\u000b"+command+"\n");
        if(dispatched!=null)dispatched.run();
    }
    public void appendExternal(String text) {
        String normalized=text.replace("\r\n","\n").replace("\n","\r\n");
        externalOutput.append(normalized);
        if(externalOutput.length()>220000)externalOutput.delete(0,externalOutput.length()-220000);
        flushExternal();
    }
    private void flushExternal() {
        if(session==null||session.getEmulator()==null||externalOutput.length()==0)return;
        String text=externalOutput.toString();externalOutput.setLength(0);session.appendExternalOutput(text);
    }
    public void clearScreen() {
        externalOutput.setLength(0);
        if(session!=null)session.appendExternalOutput("\u001b[2J\u001b[H\u001b[3J");
        historyTopRow=0;redrawPrompt();
    }
    public void redrawPrompt() { if(promptReady&&foreground==null)writeRaw("\u000c"); }
    public void copyText(String text) { copy(text); }

    public void paste() {
        if(!acceptsInput())return;
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
        RunControl control=foreground;foreground=null;pendingCommand=null;pendingCommandFailure=null;commandDispatched=null;externalOutput.setLength(0);
        TerminalSession old = session;
        if(plan!=null)plan.startup.delete();
        session = null; plan = null; state = State.EMPTY;promptSeen=false;promptReady=false;
        if(control!=null)control.closed();
        if (old != null) old.finishIfRunning();
        notifyObserver();
    }
    public void shutdown() { close(); io.shutdownNow(); }
    public void failIfCurrent(TerminalSession expected, Throwable failure) {
        if (expected != null && expected == session) fail(failure);
    }
    public void fail(Throwable failure) {
        generation++;
        RunControl control=foreground;foreground=null;pendingCommand=null;pendingCommandFailure=null;commandDispatched=null;
        TerminalSession old = session;
        if(plan!=null)plan.startup.delete();
        session = null; plan = null; state = State.FAILED;promptSeen=false;promptReady=false;
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
        if(control!=null)control.closed();
        notifyObserver();
    }
    public String failureDetails() {
        try { return ShellFailureLog.read(context.getFilesDir()); }
        catch (java.io.IOException | SecurityException failure) { return "无法读取本机终端错误记录：" + failure.getMessage(); }
    }
    public void copyFailureDetails() { copy(failureDetails()); }
    @Override public void onTextChanged(TerminalSession s) { if (s == session) { flushExternal();pumpCommand();notifyObserver(); } }
    @Override public void onShellIntegration(TerminalSession s,String marker) {
        if(s!=session)return;
        if(marker.equals("B")) { promptSeen=true;promptReady=true; }
        else if(marker.equals("A")||marker.equals("C"))promptReady=false;
    }
    @Override public void onTerminalInput(TerminalSession s,byte[] bytes,int offset,int count) {
        if(s!=session)return;
        for(int i=offset;i<offset+count;i++)if(bytes[i]=='\n'||bytes[i]=='\r')promptReady=false;
    }
    @Override public boolean onTerminalOutput(TerminalSession s,int bytes) { return s!=session||foreground==null||foreground.output(bytes); }
    @Override public void onTitleChanged(TerminalSession s) { if (s == session) notifyObserver(); }
    @Override public void onSessionFinished(TerminalSession s) {
        if (s == session) {
            if(plan!=null)plan.startup.delete();
            state = State.EXITED;RunControl control=foreground;foreground=null;pendingCommand=null;pendingCommandFailure=null;commandDispatched=null;
            if(control!=null)control.closed();notifyObserver();
        }
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
