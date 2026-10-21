package com.codepocket.editorlab;

import android.app.Activity;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Real Sora Android View/InputConnection + native graphics on the host, not a device result. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class SoraAssistInteractionTest {
    private ActivityController<Activity> window;
    private SoraInputEditor editor;
    private SoraEditorAdapter port;
    private SoraAssistController assist;
    private SoraAssistPopup popup;
    @Before public void setup() {
        window = Robolectric.buildActivity(Activity.class).setup().visible();
        editor = new SoraInputEditor(window.get()); editor.setCursorAnimationEnabled(false);
        port = new SoraEditorAdapter(editor); assist = new SoraAssistController(window.get(), editor, port, ignored -> {});
        popup = ReflectionHelpers.getField(assist, "popup");
        window.get().setContentView(editor); window.windowFocusChanged(true);
        layout(); editor.requestFocus(); assist.resume();
    }
    private void layout() {
        View root = window.get().findViewById(android.R.id.content);
        int width = window.get().getResources().getDisplayMetrics().widthPixels;
        int height = window.get().getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height); root.getViewTreeObserver().dispatchOnGlobalLayout();
    }
    private void load(String code) {
        assertTrue(port.replaceDocument("scratch:test", code, code.length(), code.length()));
        editor.requestFocus(); layout(); assist.resume();
    }
    private void settle() throws Exception {
        for (int i = 0; i < 10; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(160));
            // Await the real query. A sentinel would evict it from the bounded worker queue.
            Future<?> pending = ReflectionHelpers.getField(assist, "pending");
            if (pending != null && !pending.isCancelled()) pending.get(10, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (ReflectionHelpers.getField(assist, "readyRequest") == null) return;
            Thread.sleep(10); // Allow Sora's real layout worker to publish its result.
        }
        fail("Sora layout request did not become ready: " + diagnostic());
    }
    private String diagnostic() {
        return "state=" + assist.state() + ", focus=" + editor.hasFocus() + ", compose=" + editor.hasComposingText() +
            ", source=" + editor.getText() + ", selection=" + editor.getCursor().getLeft() + ":" + editor.getCursor().getRight() +
            ", anchor=" + port.anchor() + ", result=" + ReflectionHelpers.getField(assist, "result");
    }
    private void key(int code) {
        editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }
    @After public void close() {
        if (assist != null) assist.close(); if (port != null) port.close(); if (editor != null) editor.release();
        if (window != null) window.pause().stop().destroy();
    }
    @Test public void actualPopupArrowsAndTabAcceptTheSharedCandidate() throws Exception {
        load("pr"); settle(); assertTrue(diagnostic(), popup.showingCandidates());
        assertTrue(editor.hasFocus()); key(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(1, popup.index());
        key(KeyEvent.KEYCODE_DPAD_UP); assertEquals(0, popup.index()); assertEquals("pr", editor.getText().toString());
        key(KeyEvent.KEYCODE_TAB); assertEquals("print", editor.getText().toString()); assertFalse(popup.showing());
        editor.undo(); assertEquals("pr", editor.getText().toString());
    }
    @Test public void defaultEnterKeepsNativeNewlineAndEscapeDoesNotEdit() throws Exception {
        load("pr"); settle(); assertTrue(diagnostic(), popup.showingCandidates()); key(KeyEvent.KEYCODE_ESCAPE);
        assertEquals("pr", editor.getText().toString()); assertFalse(popup.showing());
        assist.request(SoraAssistance.Mode.COMPLETION); settle(); assertTrue(diagnostic(), popup.showingCandidates());
        key(KeyEvent.KEYCODE_ENTER); assertFalse(editor.getText().toString().contains("print")); assertTrue(editor.getText().toString().contains("\n"));
    }
    @Test public void actualInputConnectionEditsAPlaceholderAndTabMovesToTheNextField() throws Exception {
        editor.setWordwrap(true); load("fo"); assist.request(SoraAssistance.Mode.SNIPPETS);
        assertNotNull("Explicit mode must wait for real Sora layout", ReflectionHelpers.getField(assist, "readyRequest"));
        settle(); assertTrue(diagnostic(), popup.showingCandidates());
        assertTrue(assist.accept(0)); assertTrue(editor.getText().toString().contains("for item in"));
        InputConnection input = editor.onCreateInputConnection(new EditorInfo()); assertNotNull(input);
        assertTrue(input.commitText("索引", 1));
        assertTrue(editor.getText().toString().contains("for 索引 in"));
        assertTrue(assist.field(false));
        assertEquals("range(10)", editor.getText().subSequence(editor.getCursor().getLeft(), editor.getCursor().getRight()).toString());
    }
    @Test @Config(qualifiers="w1000dp-h700dp") public void tabletCompositionAndNewDocumentNeverAcceptAnOldCandidate() throws Exception {
        load("pr"); settle(); assertTrue(diagnostic(), popup.showingCandidates());
        InputConnection input = editor.onCreateInputConnection(new EditorInfo()); assertTrue(input.setComposingText("中", 1));
        settle(); assertFalse(popup.showing()); assertTrue(editor.hasComposingText());
        assertFalse(editor.assistanceKeys.test(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)));
        assertTrue(input.finishComposingText());
        assertTrue(port.replaceDocument("scratch:other", "other", 5, 5)); settle();
        assertFalse(assist.accept(0)); assertEquals("other", editor.getText().toString());
    }
    @Test public void cppCtrlSpaceAndTabUseTheSameNativeCandidatePipeline() throws Exception {
        assist.configure(SyntaxLanguage.CPP, false); editor.setWordwrap(true); load("vec");
        KeyEvent down = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 0, KeyEvent.META_CTRL_ON);
        assertTrue(editor.dispatchKeyEvent(down));
        assertNotNull("Physical Ctrl+Space must also survive asynchronous layout", ReflectionHelpers.getField(assist, "readyRequest"));
        assertTrue(editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)));
        settle(); assertTrue(diagnostic(), popup.showingCandidates()); key(KeyEvent.KEYCODE_TAB);
        assertEquals("vector", editor.getText().toString());
    }
    @Test public void actualParameterWindowSwitchesReferenceFormsWithoutChangingFocusOrSource() throws Exception {
        load("range(1, "); assist.request(SoraAssistance.Mode.SIGNATURE); settle();
        assertTrue(diagnostic(), popup.showing()); assertFalse(popup.showingCandidates());
        android.widget.TextView label = ReflectionHelpers.getField(popup, "signature");
        String first = label.getText().toString();
        android.widget.Button forms = ReflectionHelpers.getField(popup, "forms");
        assertTrue(forms.performClick()); assertNotEquals(first, label.getText().toString());
        assertEquals("range(1, ", editor.getText().toString()); assertTrue(editor.hasFocus());
    }

}
