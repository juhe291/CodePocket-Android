package com.codepocket.editorlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.codepocket.ide.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/** UI-thread document owner. Real project operations use the IDE's serial storage executor. */
final class LabDocuments implements AutoCloseable {
    interface Host {
        Capture capture();
        boolean display(Entry entry);
        void status(String message);
    }
    static final class Capture {
        final SoraBuffer.Viewport stamp;
        final String text;
        final EditorViewState view;
        Capture(SoraBuffer.Viewport stamp, String text, EditorViewState view) {
            this.stamp = stamp; this.text = text; this.view = view;
        }
        boolean sameDocument(Capture current) {
            // Caret, scroll and focus may move during reading; text/owner/composition may not.
            return current != null && stamp != null && current.stamp != null && !stamp.composing &&
                !current.stamp.composing && stamp.sameOwner(current.stamp) && stamp.revision == current.stamp.revision &&
                text.equals(current.text);
        }
    }
    static final class Entry {
        final Workspace workspace;
        final EditorSessions sessions;
        final String path, key;
        final List<String> files;
        String text;
        EditorViewState view;
        // Only the serial IO worker changes these after a verified write. A queued checkpoint
        // therefore uses the new base even if Android has not delivered the save callback yet.
        volatile String base, savedText;
        Entry(Workspace workspace, EditorSessions sessions, String path, String disk, Draft draft, List<String> files) {
            this.workspace = workspace; this.sessions = sessions; this.path = path; this.files = List.copyOf(files);
            key = sessions.key(path);
            RestoredEditorBuffer recovered = DraftRecovery.INSTANCE.restore(disk, draft);
            text = recovered.getText(); base = recovered.getBaseHash(); savedText = disk;
            view = sessions.file(path).getView().clamp(text.length(), 0);
        }
        boolean dirty() { return !text.equals(savedText); }
    }
    private final Context context;
    private final SharedPreferences preferences;
    private final DraftStore drafts;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Host host;
    private long attachment;
    private boolean closed, busy;
    private Entry current;
    private String message = "正在载入独立实验项目…";
    Future<?> pending;

    LabDocuments(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("document-lab-sessions", Context.MODE_PRIVATE);
        drafts = new DraftStore(new File(this.context.getFilesDir(), "document-lab/drafts"), LabDocuments::replace);
    }
    private static kotlin.Unit replace(File from, File to) {
        try { android.system.Os.rename(from.getPath(), to.getPath()); return kotlin.Unit.INSTANCE; }
        catch (android.system.ErrnoException error) { throw new IllegalStateException("原子替换失败", error); }
    }
    void attach(Host next) {
        if (closed) throw new IllegalStateException("Document session is closed");
        host = next; attachment++;
        if (current != null && !next.display(current)) status("编辑器尚不能恢复文档；原文与草稿已保留");
        else next.status(message);
    }
    void detach(Host old) {
        if (host != old) return;
        checkpoint(); host = null; attachment++;
    }
    Entry current() { return current; }
    boolean busy() { return busy; }
    String status() { return message; }
    void internal() {
        workspace(() -> new Workspace(new File(context.getFilesDir(), "document-lab/workspace"), LabDocuments::replace));
    }
    void tree(android.net.Uri uri) { workspace(() -> Workspace.Companion.fromTree(context, uri)); }
    private void workspace(Callable<Workspace> prepare) {
        afterSave(() -> {
            Capture source = capture(); long ticket = attachment;
            submit("正在读取项目…", () -> {
                Workspace workspace = prepare.call();
                EditorSessions sessions = new EditorSessions(workspace.getIdentity(), preferences.getString(sessionKey(workspace), "[]"));
                List<String> files = files(workspace);
                if (files.isEmpty()) throw new IllegalArgumentException("目录中没有文件；请先在主 IDE 或系统文件管理器中创建文件");
                String path = preferences.getString("active:" + workspace.getIdentity(), files.get(0));
                if (!files.contains(path)) path = files.get(0);
                return read(workspace, sessions, path, files);
            }, entry -> install(entry, source, ticket));
        });
    }
    void open(String path) {
        if (current == null) { status("请先打开项目"); return; }
        if (!current.files.contains(path)) { status("文件不在当前项目快照中，请重新打开项目"); return; }
        if (path.equals(current.path)) return;
        afterSave(() -> {
            Entry previous = current; Capture source = capture(); long ticket = attachment;
            previous.sessions.file(path); // stable ID is persisted before a recoverable read or write
            submit("正在打开 " + path + "…", () -> read(previous.workspace, previous.sessions, path, previous.files),
                entry -> install(entry, source, ticket));
        });
    }
    private Entry read(Workspace workspace, EditorSessions sessions, String path, List<String> files) {
        String disk = workspace.read(workspace.resolve(path));
        return new Entry(workspace, sessions, path, disk, drafts.read(sessions.key(path)), files);
    }
    private void install(Entry entry, Capture source, long ticket) {
        if (host == null || (ticket != attachment && (source != null || current != null)) ||
                (source == null ? capture() != null : !source.sameDocument(capture()))) {
            status("打开已取消：窗口、文档或组合输入发生变化；当前内容已保留"); checkpoint(); return;
        }
        checkpoint(); // Capture the old file's latest caret/scroll before replacing its Content.
        if (current != null && current.workspace.getIdentity().equals(entry.workspace.getIdentity())) {
            // A same-workspace mirror refresh must not overwrite caret changes made during IO.
            entry.sessions.file(current.path).setView(current.view);
            if (entry.path.equals(current.path)) entry.view = current.view.clamp(entry.text.length(), 0);
        }
        if (!host.display(entry)) { status("文档更换被拒绝；原文与草稿已保留"); return; }
        current = entry;
        checkpoint();
        status(entry.dirty() ? "已恢复未保存草稿；请保存或复制备份" : "已打开 " + entry.path);
    }
    void save(Runnable after) {
        if (closed || busy) { status("文件操作进行中，请稍候"); return; }
        Entry entry = current; Capture source = capture();
        if (entry == null || source == null) { status("尚未载入文件或缓冲区身份不匹配，已阻止保存"); return; }
        if (source.stamp.composing) { status("请先完成输入法组合输入"); return; }
        remember(entry, source);
        if (!entry.dirty()) { checkpoint(); if (after != null) after.run(); else status("文件已保存"); return; }
        final long ticket = attachment;
        final String sessionsJson = entry.sessions.toJson();
        submit("正在保存 " + entry.path + "…", () -> {
            persist(entry, sessionsJson);
            drafts.put(entry.key, entry.path, source.text, entry.base);
            entry.workspace.write(entry.workspace.resolve(entry.path), source.text, entry.base);
            entry.base = DocumentTree.Companion.digest(source.text); entry.savedText = source.text;
            drafts.removeIfMatches(entry.key, source.text);
            return entry;
        }, saved -> {
            Capture latest = capture();
            if (current == entry && latest != null) remember(entry, latest);
            status(entry.dirty() ? "已保存上一版；新编辑已保留，请再次保存" : "已保存 " + entry.path);
            checkpoint();
            if (after != null && ticket == attachment && source.sameDocument(latest)) after.run();
            else if (after != null) status("保存期间有新编辑或窗口变化，已取消切换；当前内容已保留");
        });
    }
    private void afterSave(Runnable action) {
        if (closed || busy) { status("文件操作进行中，请稍候"); return; }
        Capture source = capture();
        if (current != null && source == null) { status("缓冲区身份不匹配，已阻止切换；当前内容已保留"); return; }
        if (source != null && source.stamp.composing) { status("请先完成输入法组合输入"); return; }
        if (current != null && source != null) remember(current, source);
        if (current != null && current.dirty()) save(action); else { checkpoint(); action.run(); }
    }
    /** Durable recovery is separate from saving. Never writes source files on pause/rotation. */
    void checkpoint() {
        if (closed || current == null) return;
        Entry entry = current; Capture source = capture();
        if (source != null) remember(entry, source);
        String text = entry.text, json = entry.sessions.toJson();
        pending = WorkspaceIo.INSTANCE.getExecutor().submit(() -> {
            try {
                persist(entry, json);
                if (!text.equals(entry.savedText)) drafts.put(entry.key, entry.path, text, entry.base);
                else drafts.removeIfMatches(entry.key, text);
            } catch (Exception error) { main.post(() -> { if (!closed) status("恢复草稿失败，请立即复制备份：" + error.getMessage()); }); }
        });
    }
    private void remember(Entry entry, Capture source) {
        entry.text = source.text; entry.view = source.view;
        entry.sessions.file(entry.path).setView(source.view);
    }
    private Capture capture() {
        Capture source = host == null ? null : host.capture();
        return source != null && (current == null || current.key.equals(source.stamp.document())) ? source : null;
    }
    private String sessionKey(Workspace workspace) { return "sessions:" + workspace.getIdentity(); }
    private void persist(Entry entry, String sessionsJson) {
        if (!preferences.edit().putString(sessionKey(entry.workspace), sessionsJson)
                .putString("active:" + entry.workspace.getIdentity(), entry.path).commit())
            throw new IllegalStateException("无法保存文件会话；已阻止源文件写入");
    }
    private static List<String> files(Workspace workspace) {
        List<String> paths = new ArrayList<>();
        for (File folder : workspace.folders()) for (File child : workspace.children(folder))
            if (child.isFile()) paths.add(workspace.path(child));
        paths.sort(String.CASE_INSENSITIVE_ORDER); return paths;
    }
    private <T> void submit(String label, Callable<T> task, java.util.function.Consumer<T> success) {
        busy = true; status(label);
        pending = WorkspaceIo.INSTANCE.getExecutor().submit(() -> {
            T result = null; Exception failure = null;
            try { result = task.call(); } catch (Exception error) { failure = error; }
            T value = result; Exception error = failure;
            main.post(() -> {
                busy = false; if (closed) return;
                if (error != null) { status("文件操作失败，当前内容和恢复草稿已保留：" + error.getMessage()); checkpoint(); }
                else success.accept(value);
            });
        });
    }
    private void status(String text) { message = text; if (host != null) host.status(text); }
    @Override public void close() { if (closed) return; checkpoint(); host = null; attachment++; closed = true; }
}
