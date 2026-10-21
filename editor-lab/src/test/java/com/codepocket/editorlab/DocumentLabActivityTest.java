package com.codepocket.editorlab;

import android.content.Intent;
import android.os.Looper;
import android.view.*;
import android.widget.Button;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Actual application Activity entry points and recreation on native host graphics. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w411dp-h800dp", shadows=HostAtomicLinux.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class DocumentLabActivityTest {
    private ActivityController<DocumentLabActivity> window;
    private LabDocuments documents() { return ReflectionHelpers.getField(window.get(), "documents"); }
    private SoraInputEditor editor() { return ReflectionHelpers.getField(window.get(), "editor"); }
    private void create() throws Exception {
        window = Robolectric.buildActivity(DocumentLabActivity.class).setup().visible(); window.windowFocusChanged(true); settle();
    }
    private void settle() throws Exception {
        for (int i=0; i<20; i++) {
            Future<?> last = documents().pending; if (last != null) last.get(10, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!documents().busy() && documents().pending == last && (last == null || last.isDone())) return;
        }
        fail("Activity's real document IO did not settle");
    }
    private void grammarReady() throws Exception {
        ExecutorService worker = ReflectionHelpers.getField(window.get(), "grammarWorker");
        worker.submit(() -> {}).get(15, TimeUnit.SECONDS); // unbounded serial grammar loader, not the bounded completion queue
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        SoraSyntaxController syntax = ReflectionHelpers.getField(window.get(), "syntax");
        assertTrue(syntax.state(), syntax.state().contains("TextMate"));
    }
    private Button button(View view, String label) {
        if (view instanceof Button && ((Button)view).getText().toString().equals(label)) return (Button)view;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
            Button found = button(((ViewGroup)view).getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    @After public void close() throws Exception {
        if (window != null) { window.pause().stop().destroy(); settle(); }
    }
    @Test public void realActivitySaveButtonAndCtrlSWriteTheActualFile() throws Exception {
        create(); editor().getText().replace(0, editor().getText().length(), "print('phone save')\n");
        assertTrue(button(window.get().getWindow().getDecorView(), "保存").performClick()); settle();
        LabDocuments.Entry entry = documents().current();
        assertEquals("print('phone save')\n", new String(Files.readAllBytes(entry.workspace.resolve(entry.path).toPath()), StandardCharsets.UTF_8));
        editor().getText().replace(0, editor().getText().length(), "print('keyboard save')\n");
        assertTrue(window.get().dispatchKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON))); settle();
        assertEquals("print('keyboard save')\n", new String(Files.readAllBytes(entry.workspace.resolve(entry.path).toPath()), StandardCharsets.UTF_8));
    }
    @Test public void directoryButtonUsesTheSystemTreePickerWithScopedGrants() throws Exception {
        create(); assertTrue(button(window.get().getWindow().getDecorView(), "授权目录").performClick());
        Intent intent = Shadows.shadowOf(window.get()).getNextStartedActivity();
        assertNotNull(intent); assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.getAction());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
    }
    @Test @Config(qualifiers="w1000dp-h700dp") public void tabletActivityRecreationRetainsTheOwnerTextAndCaretWithoutSaving() throws Exception {
        create(); grammarReady(); LabDocuments owner = documents(); SoraInputEditor old = editor();
        LabDocuments.Entry entry = owner.current(); String original = new String(Files.readAllBytes(entry.workspace.resolve(entry.path).toPath()), StandardCharsets.UTF_8);
        editor().getText().replace(0, editor().getText().length(), "# tablet 😀\nprint(1)\n"); editor().setSelectionRegion(1, 0, 1, 5, false);
        window.recreate(); window.visible(); window.windowFocusChanged(true); settle();
        assertSame(owner, documents()); assertNotSame(old, editor()); assertEquals("# tablet 😀\nprint(1)\n", editor().getText().toString());
        assertEquals("print", editor().getText().subSequence(editor().getCursor().getLeft(), editor().getCursor().getRight()).toString());
        assertEquals(original, new String(Files.readAllBytes(entry.workspace.resolve(entry.path).toPath()), StandardCharsets.UTF_8));
    }
    @Test public void liveTypingBeyondTheBudgetDisablesGrammarWithoutTruncatingTheFileBuffer() throws Exception {
        create(); grammarReady(); String text = "x".repeat(45001);
        editor().getText().replace(0, editor().getText().length(), text); Shadows.shadowOf(Looper.getMainLooper()).idle();
        SoraSyntaxController syntax = ReflectionHelpers.getField(window.get(), "syntax");
        assertTrue(syntax.state(), syntax.state().contains("4.5万")); assertEquals(text, editor().getText().toString());
        android.widget.TextView status = ReflectionHelpers.getField(window.get(), "status"); assertTrue(status.getText().toString().contains("纯文本"));
    }

}
