package com.codepocket.ide.terminal.shell;

import android.app.Application;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Runs the actual Android View/renderer through Robolectric, without invoking JNI or a fake shell. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShellTerminalInitializationTest {
    private Application application;
    private ShellRuntime runtime;
    private ShellTerminalPane pane;

    @Before public void setUp() {
        application = RuntimeEnvironment.getApplication();
        runtime = ShellRuntime.get(application);
        runtime.close();
        pane = new ShellTerminalPane(application, null, 12, false, () -> {});
    }
    @After public void tearDown() {
        if (pane != null) pane.dispose();
        if (runtime != null) runtime.close();
        ReflectionHelpers.setStaticField(ShellRuntime.class, "instance", null);
    }
    private TerminalSession installUnmeasuredSession() throws Exception {
        ShellLaunchPlan plan = ShellLaunchPlan.prepare(application.getFilesDir(), application.getCacheDir(), null);
        TerminalSession session = new TerminalSession(ShellLaunchPlan.EXECUTABLE, plan.directory.getPath(), plan.arguments, plan.environment, 2000, runtime);
        ReflectionHelpers.setField(runtime, "plan", plan);
        ReflectionHelpers.setField(runtime, "session", session);
        ReflectionHelpers.setField(runtime, "state", ShellRuntime.State.OPEN);
        return session;
    }
    @Test public void reproducesLegacyTypefaceBeforeTextSizeCrash() {
        TerminalView view = new TerminalView(application, null);
        NullPointerException failure = assertThrows(NullPointerException.class, () -> view.setTypeface(Typeface.MONOSPACE));
        assertTrue(java.util.Arrays.stream(failure.getStackTrace()).anyMatch(frame -> frame.getClassName().equals(TerminalView.class.getName()) && frame.getMethodName().equals("setTypeface")));
    }
    @Test public void firstSessionCreatesTerminalViewWithoutCrashing() throws Exception {
        TerminalSession session = installUnmeasuredSession();
        pane.changed();
        TerminalView terminal = ReflectionHelpers.getField(pane, "terminal");
        assertNotNull(terminal);
        assertNotNull(ReflectionHelpers.getField(terminal, "mRenderer"));
        assertSame(session, ReflectionHelpers.getField(terminal, "mTermSession"));
        FrameLayout viewport = ReflectionHelpers.getField(pane, "viewport");
        assertSame(viewport, terminal.getParent());
        assertEquals(0, session.getPid()); // No dimensions: actual Android subprocess creation is deliberately not invoked.
    }
    @Test public void repeatedNotificationsReuseTerminalAndSession() throws Exception {
        TerminalSession session = installUnmeasuredSession();
        pane.changed();
        TerminalView first = ReflectionHelpers.getField(pane, "terminal");
        pane.changed(); pane.setPreferences(24, false); pane.changed();
        assertSame(first, ReflectionHelpers.getField(pane, "terminal"));
        assertSame(session, runtime.session());
        assertEquals(0, session.getPid());
    }
    @Test public void lateFailureFromPreviousSessionCannotCloseReplacement() throws Exception {
        TerminalSession old = installUnmeasuredSession();
        TerminalSession replacement = installUnmeasuredSession();
        runtime.failIfCurrent(old, new IllegalStateException("old session failure"));
        assertSame(replacement, runtime.session());
        assertEquals(ShellRuntime.State.OPEN, runtime.state());
        assertNull(runtime.error());
    }
    @Test public void currentFailureIsOneFailedStateAndReadableWithoutUsb() throws Exception {
        TerminalSession current = installUnmeasuredSession();
        pane.changed();
        java.util.List<ShellRuntime.State> states = new java.util.ArrayList<>();
        ShellRuntime.Observer observer = () -> states.add(runtime.state());
        runtime.attach(observer);
        runtime.failIfCurrent(current, new IllegalStateException("view initialization failed"));
        assertEquals(java.util.Collections.singletonList(ShellRuntime.State.FAILED), states);
        assertNull(runtime.session());
        assertTrue(runtime.failureDetails().contains("IllegalStateException: view initialization failed"));
        pane.changed();
        assertNull(ReflectionHelpers.getField(pane, "terminal"));
        android.widget.TextView placeholder = ReflectionHelpers.getField(pane, "placeholder");
        assertTrue(placeholder.getText().toString().contains("view initialization failed"));
        runtime.detach(observer);
    }

    private TerminalView installTranscript() throws Exception {
        TerminalSession session=installUnmeasuredSession();
        com.termux.terminal.TerminalEmulator emulator=new com.termux.terminal.TerminalEmulator(session,60,6,13,15,2000,runtime);
        ReflectionHelpers.setField(session,"mEmulator",emulator);
        for(int i=0;i<30;i++) {
            byte[] line=("line-"+i+"\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            emulator.append(line,line.length);
        }
        pane.changed();return ReflectionHelpers.getField(pane,"terminal");
    }
    @Test public void actualEmulatorOutputDoesNotSnapBrowsingHistoryToBottom() throws Exception {
        TerminalView view=installTranscript();view.setTopRow(-3);
        runtime.appendExternal("new-output\n");pane.changed();
        assertTrue(view.getTopRow()<=-3);
        assertTrue(runtime.session().getEmulator().getScreen().getTranscriptText().contains("new-output"));
    }
    @Test public void switchingPaneKeepsHistoryOffsetWithoutHoldingOldView() throws Exception {
        TerminalView old=installTranscript();old.setTopRow(-4);pane.dispose();
        ShellTerminalPane replacement=new ShellTerminalPane(application,null,12,false,()->{},runtime);
        try {
            TerminalView current=ReflectionHelpers.getField(replacement,"terminal");
            assertNotSame(old,current);assertEquals(-4,current.getTopRow());
        } finally {replacement.dispose();}
    }
    @Test public void clearDisplayUsesEmulatorNotProgramStdin() throws Exception {
        installTranscript();ShellRunInputTest.Control control=new ShellRunInputTest.Control();
        assertTrue(runtime.beginForeground(control));runtime.clearScreen();
        assertFalse(runtime.session().getEmulator().getScreen().getTranscriptText().contains("line-29"));
        assertEquals(0,control.stopped);assertEquals("Python",runtime.foregroundLabel());
        assertEquals(0,runtime.session().getPid()); // No JNI/PT Y or native process executed by this test.
    }

    @Test public void closingSessionDeletesOnlyItsStartupFile() throws Exception {
        installUnmeasuredSession();
        java.io.File startup=runtime.plan().startup;
        assertTrue(startup.isFile());
        runtime.close();
        assertFalse(startup.exists());
    }
    @Test public void failedSessionDeletesStartupFile() throws Exception {
        TerminalSession current=installUnmeasuredSession();
        java.io.File startup=runtime.plan().startup;
        runtime.failIfCurrent(current,new IllegalStateException("startup failed"));
        assertFalse(startup.exists());
    }
    @Test public void realTerminalExposesTextInputAndKeyboardButton() throws Exception {
        installUnmeasuredSession();pane.changed();
        TerminalView view=ReflectionHelpers.getField(pane,"terminal");
        assertTrue(view.onCheckIsTextEditor());
        android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();
        assertNotNull(view.onCreateInputConnection(info));
        assertTrue((info.imeOptions & android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN)!=0);
        assertTrue(findKeyboardButton(pane));
        assertTrue(pane.minimumHeightDp(false)>pane.minimumHeightDp(true));
        pane.setCompact(true);
        android.widget.TextView label=ReflectionHelpers.getField(pane,"runCaption");
        assertEquals(android.view.View.GONE,label.getVisibility());
    }
    private boolean findKeyboardButton(android.view.View view) {
        if("显示终端虚拟键盘".contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return true;
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(findKeyboardButton(group.getChildAt(i)))return true;
        }
        return false;
    }
    @Test public void restoringFocusTargetsActualTerminalWithoutShowingKeyboard() throws Exception {
        installUnmeasuredSession();pane.changed();
        assertTrue(pane.restoreTerminalFocus());
        assertTrue(pane.hasTerminalFocus());
        android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)application.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        assertFalse(org.robolectric.Shadows.shadowOf(ime).isSoftInputVisible());
        pane.hostActive(false);
        assertFalse(pane.restoreTerminalFocus());
        assertEquals(0,runtime.session().getPid()); // No Android subprocess or deferred request is introduced.
    }
}
