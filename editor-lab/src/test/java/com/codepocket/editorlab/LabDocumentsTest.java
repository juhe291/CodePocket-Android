package com.codepocket.editorlab;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import com.codepocket.ide.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

/** Actual Sora Content + Android window + real private disk IO; no fabricated save results. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w411dp-h800dp", shadows=HostAtomicLinux.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LabDocumentsTest {
    private ActivityController<Activity> activity;
    private LabDocuments documents;
    private Window host;
    private final java.util.List<Window> windows = new java.util.ArrayList<>();
    final class Window implements LabDocuments.Host {
        final SoraInputEditor editor = new SoraInputEditor(activity.get());
        final SoraEditorAdapter port = new SoraEditorAdapter(editor);
        LabDocuments.Entry shown;
        Window() {
            windows.add(this); editor.setCursorAnimationEnabled(false); activity.get().setContentView(editor);
            View root = activity.get().findViewById(android.R.id.content);
            int w = activity.get().getResources().getDisplayMetrics().widthPixels, h = activity.get().getResources().getDisplayMetrics().heightPixels;
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h); editor.requestFocus();
        }
        @Override public LabDocuments.Capture capture() {
            SoraBuffer.Snapshot source = port.snapshot(); SoraBuffer.Viewport stamp = port.viewport();
            return source == null || stamp == null ? null : new LabDocuments.Capture(stamp, source.text,
                new EditorViewState(source.start, source.end, editor.getOffsetX(), editor.getOffsetY(), "editor", 0, 0));
        }
        @Override public boolean display(LabDocuments.Entry entry) {
            if (!port.replaceDocument(entry.key, entry.text, entry.view.getStart(), entry.view.getEnd())) return false;
            shown = entry; port.restoreViewport(entry.view.getScrollX(), entry.view.getScrollY()); return true;
        }
        @Override public void status(String text) {}
        String text() { return editor.getText().toString(); }
        void edit(String text) { editor.getText().replace(0, editor.getText().length(), text); }
    }
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().visible(); activity.windowFocusChanged(true);
        host = new Window(); documents = new LabDocuments(activity.get()); documents.attach(host); documents.internal(); settle();
        assertNotNull(documents.current());
    }
    private void settle() throws Exception {
        for (int i=0; i<20; i++) {
            Future<?> last = documents.pending; if (last != null) last.get(10, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!documents.busy() && documents.pending == last && (last == null || last.isDone())) return;
        }
        fail("Document IO did not settle: " + documents.status());
    }
    private File source() { return documents.current().workspace.resolve(documents.current().path); }
    private static String read(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    private String disk() throws Exception { return read(source()); }
    private String other() { return documents.current().files.stream().filter(p -> !p.equals(documents.current().path)).findFirst().orElseThrow(); }
    private CountDownLatch block() throws Exception {
        CountDownLatch start = new CountDownLatch(1), release = new CountDownLatch(1);
        WorkspaceIo.INSTANCE.getExecutor().submit(() -> { start.countDown(); try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Blocked IO timeout"); } catch (InterruptedException e) { throw new AssertionError(e); } });
        assertTrue(start.await(10, TimeUnit.SECONDS)); return release;
    }
    @After public void cleanup() throws Exception {
        if (documents != null) { documents.close(); settle(); }
        for (Window window : windows) { window.port.close(); window.editor.release(); }
        if (activity != null) activity.pause().stop().destroy();
    }
    @Test public void realInternalFileSaveSurvivesANewDocumentOwner() throws Exception {
        String key = documents.current().key; host.edit("print('真实 😀 中文')\n"); documents.save(null); settle();
        assertEquals(documents.status(), host.text(), disk()); assertFalse(documents.current().dirty());
        documents.close(); settle(); documents = new LabDocuments(activity.get()); host = new Window(); documents.attach(host); documents.internal(); settle();
        assertEquals(key, documents.current().key); assertEquals("print('真实 😀 中文')\n", host.text());
    }
    @Test public void checkpointRestoresUnsavedTextWithoutWritingTheSource() throws Exception {
        String original = disk(), key = documents.current().key;
        host.edit("# 恢复草稿\nprint(42)\n"); documents.checkpoint(); settle(); assertEquals(original, disk());
        documents.close(); settle(); documents = new LabDocuments(activity.get()); host = new Window(); documents.attach(host); documents.internal(); settle();
        assertEquals(key, documents.current().key); assertEquals(documents.status(), "# 恢复草稿\nprint(42)\n", host.text()); assertTrue(documents.current().dirty());
        documents.save(null); settle(); assertEquals(documents.status(), host.text(), disk());
    }
    @Test public void externalConflictPreservesTheFileDraftAndPendingNavigation() throws Exception {
        Files.write(source().toPath(), "external edit\n".getBytes(StandardCharsets.UTF_8));
        host.edit("my unsaved edit\n"); String path = documents.current().path; documents.open(other()); settle();
        assertEquals(path, documents.current().path); assertEquals("my unsaved edit\n", host.text()); assertEquals("external edit\n", disk());
        assertTrue(documents.status().contains("失败"));
        documents.close(); settle(); documents = new LabDocuments(activity.get()); host = new Window(); documents.attach(host); documents.internal(); settle();
        assertEquals("my unsaved edit\n", host.text()); documents.save(null); settle(); assertEquals("external edit\n", disk());
    }
    @Test public void typingDuringAnActualBackgroundReadCancelsTheHandoff() throws Exception {
        String path = documents.current().path; CountDownLatch release = block();
        try { documents.open(other()); host.edit("typed while reading\n"); } finally { release.countDown(); }
        settle(); assertEquals(path, documents.current().path); assertEquals("typed while reading\n", host.text());
        assertTrue(documents.status().contains("取消"));
    }
    @Test public void caretMovementDuringReadIsRetainedForThePreviousFile() throws Exception {
        String path = documents.current().path, next = other(); CountDownLatch release = block();
        try { documents.open(next); host.editor.setSelectionRegion(0, 2, 0, 6, false); } finally { release.countDown(); }
        settle(); assertEquals(next, documents.current().path);
        documents.open(path); settle(); assertEquals(2, host.editor.getCursor().getLeft()); assertEquals(6, host.editor.getCursor().getRight());
    }
    @Test public void saveThenNewTypingPreservesANewerDraftAndDoesNotSwitch() throws Exception {
        String path = documents.current().path; host.edit("saved revision\n"); CountDownLatch release = block();
        try { documents.open(other()); host.edit("newer revision\n"); documents.checkpoint(); } finally { release.countDown(); }
        settle(); assertEquals(path, documents.current().path); assertEquals(documents.status(), "saved revision\n", disk()); assertEquals("newer revision\n", host.text());
        documents.close(); settle(); documents = new LabDocuments(activity.get()); host = new Window(); documents.attach(host); documents.internal(); settle();
        assertEquals("newer revision\n", host.text()); documents.save(null); settle(); assertEquals("newer revision\n", disk());
        assertFalse(documents.current().dirty()); // queued checkpoint used the successful save's new base hash
    }
    @Test public void compositionPreventsSaveAndSwitchWithoutChangingText() throws Exception {
        String original = disk(), path = documents.current().path;
        InputConnection input = host.editor.onCreateInputConnection(new EditorInfo()); assertTrue(input.setComposingText("中文", 1));
        documents.save(null); documents.open(other()); assertEquals(path, documents.current().path); assertEquals(original, disk());
        assertTrue(documents.status().contains("组合")); assertTrue(input.finishComposingText()); documents.save(null); settle(); assertEquals(documents.status(), host.text(), disk());
    }
    @Test public void aNewContentWithTheSameKeyCannotReceiveAnOldRead() throws Exception {
        String key = documents.current().key, path = documents.current().path; CountDownLatch release = block();
        try { documents.open(other()); assertTrue(host.port.replaceDocument(key, "replacement owner", 0, 0)); } finally { release.countDown(); }
        settle(); assertEquals(path, documents.current().path); assertEquals("replacement owner", host.text());
    }
    @Test @Config(qualifiers="w1000dp-h700dp") public void tabletWindowReattachmentRetainsEditsAndSelection() throws Exception {
        host.edit("# 中文 😀\nprint(1)\n"); host.editor.setSelectionRegion(1, 0, 1, 5, false);
        String key = documents.current().key; documents.detach(host); host = new Window(); documents.attach(host); settle();
        assertEquals(key, documents.current().key); assertEquals("# 中文 😀\nprint(1)\n", host.text());
        assertEquals("print", host.editor.getText().subSequence(host.editor.getCursor().getLeft(), host.editor.getCursor().getRight()).toString());
    }
    @Test public void oldWindowReadCannotReplaceTheReattachedEditorsText() throws Exception {
        String path = documents.current().path; CountDownLatch release = block();
        try { documents.open(other()); documents.detach(host); host = new Window(); documents.attach(host); host.edit("new window typing"); }
        finally { release.countDown(); }
        settle(); assertEquals(path, documents.current().path); assertEquals("new window typing", host.text());
    }
    @Test public void switchingFilesStartsFreshUndoAndRestoresTheLastActivePath() throws Exception {
        String first = documents.current().path, next = other(); host.edit("saved first\n"); documents.open(next); settle();
        assertEquals(next, documents.current().path); assertFalse(host.editor.getText().canUndo());
        documents.close(); settle(); documents = new LabDocuments(activity.get()); host = new Window(); documents.attach(host); documents.internal(); settle();
        assertEquals(next, documents.current().path); documents.open(first); settle(); assertEquals("saved first\n", host.text());
    }
    /** Android ContentResolver really queries and reads/writes these private files through URI IDs.
        This fixture is a test document provider, not proof about a phone's third-party provider. */
    static final class Provider extends android.content.ContentProvider {
        static final String AUTHORITY = "com.codepocket.editorlab.test.documents";
        final File source, mismatch;
        boolean writable = true, revoked, mismatchReadback, written;
        Provider(File source, File mismatch) { this.source = source; this.mismatch = mismatch; }
        void permission() { if (revoked) throw new SecurityException("Provider permission revoked"); }
        @Override public boolean onCreate() { return true; }
        @Override public String getType(android.net.Uri uri) { return "text/plain"; }
        @Override public android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] args, String order) {
            permission();
            android.database.MatrixCursor result = new android.database.MatrixCursor(projection);
            boolean children = uri.getLastPathSegment().equals("children");
            boolean root = !children && android.provider.DocumentsContract.getDocumentId(uri).equals("root");
            Object[] row = new Object[projection.length];
            for (int i=0; i<projection.length; i++) {
                switch (projection[i]) {
                    case android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID: row[i] = root ? "root" : "opaque:file:1"; break;
                    case android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME: row[i] = root ? "已授权项目" : "external.py"; break;
                    case android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE: row[i] = root ? android.provider.DocumentsContract.Document.MIME_TYPE_DIR : "text/x-python"; break;
                    case android.provider.DocumentsContract.Document.COLUMN_FLAGS: row[i] = writable ? android.provider.DocumentsContract.Document.FLAG_SUPPORTS_WRITE : 0; break;
                    default: row[i] = null;
                }
            }
            result.addRow(row); return result;
        }
        @Override public android.os.ParcelFileDescriptor openFile(android.net.Uri uri, String mode) throws java.io.FileNotFoundException {
            permission();
            if (mode.contains("w")) {
                if (!writable) throw new SecurityException("Read-only provider"); written = true;
                return android.os.ParcelFileDescriptor.open(source, android.os.ParcelFileDescriptor.MODE_WRITE_ONLY | android.os.ParcelFileDescriptor.MODE_TRUNCATE);
            }
            return android.os.ParcelFileDescriptor.open(mismatchReadback && written ? mismatch : source, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        }
        @Override public android.net.Uri insert(android.net.Uri uri, android.content.ContentValues values) { throw new UnsupportedOperationException(); }
        @Override public int delete(android.net.Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
        @Override public int update(android.net.Uri uri, android.content.ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    }
    private Provider provider() throws Exception {
        File dir = new File(activity.get().getFilesDir(), "test-provider"); assertTrue(dir.isDirectory() || dir.mkdirs());
        File file = new File(dir, "source.py"), mismatch = new File(dir, "mismatch.py");
        Files.write(file.toPath(), "print('provider original')\n".getBytes(StandardCharsets.UTF_8));
        Files.write(mismatch.toPath(), "truncated provider readback".getBytes(StandardCharsets.UTF_8));
        Provider provider = new Provider(file, mismatch);
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo(); info.authority = Provider.AUTHORITY;
        provider.attachInfo(activity.get(), info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(Provider.AUTHORITY, provider);
        return provider;
    }
    private android.net.Uri uri() { return android.provider.DocumentsContract.buildTreeDocumentUri(Provider.AUTHORITY, "root"); }
    @Test public void actualSafGatewayReadsAndVerifiesAnAuthorizedProviderWrite() throws Exception {
        Provider provider = provider(); documents.tree(uri()); settle();
        assertTrue(documents.current().workspace.getExternal()); assertEquals("external.py", documents.current().path);
        host.edit("print('SAF 中文 😀')\n"); documents.save(null); settle();
        assertEquals(host.text(), read(provider.source)); assertFalse(documents.current().dirty());
        File recovery = new File(activity.get().getFilesDir(), "provider-recovery/" + documents.current().workspace.getIdentity().substring(4));
        assertEquals("print('provider original')\n", read(new File(recovery, DocumentTree.Companion.digest("opaque:file:1") + ".before.txt")));
    }
    @Test public void readOnlySafProviderPreservesTheBufferAndBlocksSwitching() throws Exception {
        Provider provider = provider(); documents.tree(uri()); settle(); provider.writable = false;
        String key = documents.current().key; host.edit("my read-only draft\n"); documents.internal(); settle();
        assertEquals(key, documents.current().key); assertEquals("my read-only draft\n", host.text()); assertFalse(provider.written);
        provider.writable = true; documents.save(null); settle(); assertEquals(host.text(), read(provider.source));
    }
    @Test public void revokedSafPermissionKeepsTheCurrentDocumentAndDraft() throws Exception {
        Provider provider = provider(); documents.tree(uri()); settle(); host.edit("revoked permission draft\n"); provider.revoked = true;
        documents.save(null); settle(); assertEquals("revoked permission draft\n", host.text()); assertTrue(documents.current().dirty());
        assertEquals("print('provider original')\n", read(provider.source)); assertTrue(documents.status().contains("失败"));
        provider.revoked = false; documents.save(null); settle(); assertEquals(host.text(), read(provider.source));
    }
    @Test public void safReadbackMismatchRetainsRecoveryAndDoesNotNavigateAway() throws Exception {
        Provider provider = provider(); documents.tree(uri()); settle(); provider.mismatchReadback = true;
        host.edit("provider write\n"); String key = documents.current().key; documents.internal(); settle();
        assertEquals(key, documents.current().key); assertEquals("provider write\n", host.text()); assertTrue(documents.current().dirty());
        assertTrue(documents.status().contains("校验失败"));
        File recovery = new File(activity.get().getFilesDir(), "provider-recovery/" + documents.current().workspace.getIdentity().substring(4));
        assertEquals("print('provider original')\n", read(new File(recovery, DocumentTree.Companion.digest("opaque:file:1") + ".before.txt")));
    }
    @Test public void anInvalidSafUtf8FileCannotReplaceTheCurrentInternalBuffer() throws Exception {
        Provider provider = provider(); Files.write(provider.source.toPath(), new byte[]{(byte)0xc3, 0x28});
        String key = documents.current().key, text = host.text(); documents.tree(uri()); settle();
        assertEquals(key, documents.current().key); assertEquals(text, host.text()); assertTrue(documents.status().contains("失败"));
    }

    @Test public void hostAtomicRenameActuallyReplacesTheFileAndRemovesTheTemporary() throws Exception {
        File source = new File(activity.get().getFilesDir(), "rename-source.tmp"), target = new File(activity.get().getFilesDir(), "rename-target.txt");
        Files.write(source.toPath(), "new bytes".getBytes(StandardCharsets.UTF_8)); Files.write(target.toPath(), "old bytes".getBytes(StandardCharsets.UTF_8));
        android.system.Os.rename(source.getPath(), target.getPath()); assertFalse(source.exists()); assertEquals("new bytes", read(target));
    }

    @Test public void sameWorkspaceReloadRetainsCaretMovedDuringTheRead() throws Exception {
        String key = documents.current().key; CountDownLatch release = block();
        try { documents.internal(); host.editor.setSelectionRegion(0, 1, 0, 7, false); } finally { release.countDown(); }
        settle(); assertEquals(key, documents.current().key); assertEquals(1, host.editor.getCursor().getLeft()); assertEquals(7, host.editor.getCursor().getRight());
    }
    @Test public void unknownOrEscapingPathsAreRejectedBeforeStartingFileIO() throws Exception {
        String text = host.text(), key = documents.current().key;
        for (String path : new String[]{"../outside.py", "/absolute.py", "not-in-snapshot.py"}) documents.open(path);
        assertFalse(documents.busy()); assertEquals(key, documents.current().key); assertEquals(text, host.text());
        assertTrue(documents.status().contains("当前项目快照"));
    }

    @Test public void aForeignBufferCannotBeSavedOrCheckpointedUnderTheActiveFileKey() throws Exception {
        String original = disk(), key = documents.current().key;
        assertTrue(host.port.replaceDocument("foreign:key", "foreign source", 0, 0));
        documents.save(null); assertTrue(documents.status().contains("身份不匹配")); documents.open(other());
        assertTrue(documents.status().contains("身份不匹配")); documents.checkpoint(); settle(); assertEquals(original, disk());
        documents.detach(host); host = new Window(); documents.attach(host); assertEquals(key, documents.current().key); assertEquals(original, host.text());
    }
    @Test public void oversizedNativeTypingIsPreservedButCannotTruncateOrOverwriteTheSource() throws Exception {
        String original = disk(), large = "x".repeat(Workspace.MAX_CHARS + 1); host.edit(large); documents.save(null); settle();
        assertEquals(original, disk()); assertEquals(large, host.text()); assertTrue(documents.status().contains("失败"));
    }

}
