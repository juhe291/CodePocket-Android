package com.codepocket.ide.terminal.shell;

import android.app.Activity;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

/** Actual attached Termux View and IME connection, with an in-memory emulator only.
 * The closed, PID-0 session deliberately prevents JNI, native resize and subprocess creation.
 * These host tests cannot verify a device IME or PTY. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
@LooperMode(LooperMode.Mode.PAUSED)
public class ShellTerminalTouchTest {
    private ActivityController<Activity> controller;
    private ShellRuntime runtime;
    private ShellTerminalPane pane;
    private TerminalView terminal;
    private EditText editor;
    private InputMethodManager ime;
    private TerminalSession session;
    private ShellRunInputTest.Control control;
    @Before public void setUp() throws Exception {
        controller=Robolectric.buildActivity(Activity.class).setup().visible();
        Activity activity=controller.get();
        runtime=ShellRuntime.create(activity);
        ShellLaunchPlan plan=ShellLaunchPlan.prepare(activity.getFilesDir(),activity.getCacheDir(),null);
        session=new TerminalSession(ShellLaunchPlan.EXECUTABLE,plan.directory.getPath(),plan.arguments,plan.environment,2000,runtime);
        ReflectionHelpers.setField(session,"mEmulator",new TerminalEmulator(session,60,8,13,15,2000,runtime));
        ReflectionHelpers.setField(session,"mSessionClosed",true); // No JNI on any host layout.
        ReflectionHelpers.setField(runtime,"session",session);ReflectionHelpers.setField(runtime,"plan",plan);
        ReflectionHelpers.setField(runtime,"state",ShellRuntime.State.OPEN);
        pane=new ShellTerminalPane(activity,null,12,false,()->{},runtime);
        // Robolectric LEGACY graphics returns zero font spacing; supply only deterministic
        // font metrics. The actual View, gesture recognizer and InputConnection are unchanged.
        TerminalView ready=ReflectionHelpers.getField(pane,"terminal");
        Object renderer=ReflectionHelpers.getField(ready,"mRenderer");
        ReflectionHelpers.setField(renderer,"mFontLineSpacing",16);
        ReflectionHelpers.setField(renderer,"mFontLineSpacingAndAscent",4);
        ReflectionHelpers.setField(renderer,"mFontWidth",8f);
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);
        editor=new EditText(activity);layout.addView(editor);
        layout.addView(pane,new LinearLayout.LayoutParams(-1,0,1));activity.setContentView(layout);
        controller.windowFocusChanged(true);shadowOf(Looper.getMainLooper()).idle();
        terminal=ReflectionHelpers.getField(pane,"terminal");
        assertNotNull(runtime.failureDetails(),terminal);
        editor.requestFocus();ime=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        ime.hideSoftInputFromWindow(editor.getWindowToken(),0);
        control=new ShellRunInputTest.Control();assertTrue(runtime.beginForeground(control));
    }
    @After public void tearDown() {
        pane.dispose();runtime.shutdown();controller.pause().stop().destroy();
        assertEquals(-1,session.getPid()); // Closed PID-0 fixture; never a native child.
    }
    private void tap() {
        long time=android.os.SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,20,20,0);
        MotionEvent up=MotionEvent.obtain(time,time+30,MotionEvent.ACTION_UP,20,20,0);
        try {assertTrue(terminal.dispatchTouchEvent(down));assertTrue(terminal.dispatchTouchEvent(up));}
        finally {down.recycle();up.recycle();}
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(android.view.ViewConfiguration.getDoubleTapTimeout()+1));
    }
    private View keyboardButton(View view) {
        if("显示终端虚拟键盘".contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){
            View result=keyboardButton(group.getChildAt(i));if(result!=null)return result;
        }}return null;
    }
    @Test public void startupTapRequestsKeyboardWithoutAllowingProcessInput() {
        assertFalse(runtime.acceptsInput());tap();
        assertTrue(terminal.hasFocus());assertTrue(shadowOf(ime).isSoftInputVisible());
        assertTrue(runtime.filterInput("must not reach shell\n"));
    }
    @Test public void startupKeyboardButtonIsNotSilentlyIgnored() {
        View button=keyboardButton(pane);assertNotNull(button);button.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(terminal.hasFocus());assertTrue(shadowOf(ime).isSoftInputVisible());
    }
    @Test public void runningAndWaitingTapUsesSameActualTerminal() {
        control.input=true;runtime.refreshForeground();tap();
        assertTrue(terminal.hasFocus());assertTrue(shadowOf(ime).isSoftInputVisible());
        assertFalse(runtime.filterInput("42\n"));assertSame(session,terminal.getCurrentSession());
    }
    @Test public void hiddenHostCannotStealImeEvenDuringRunning() {
        control.input=true;pane.hostActive(false);pane.showKeyboard();shadowOf(Looper.getMainLooper()).idle();
        assertTrue(editor.hasFocus());assertFalse(shadowOf(ime).isSoftInputVisible());
    }
    @Test public void actualImeCompositionAndEnterReachOnlyTheReadySessionQueue() {
        tap();android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();
        android.view.inputmethod.InputConnection connection=terminal.onCreateInputConnection(info);
        assertNotNull(connection);
        Object queue=ReflectionHelpers.getField(session,"mTerminalToProcessIOQueue");
        // Only enable the existing in-memory write queue. No process is launched or claimed.
        ReflectionHelpers.setField(session,"mShellPid",1);
        try {
            connection.commitText("blocked",1);
            assertEquals(0,((Number)ReflectionHelpers.getField(queue,"mStoredBytes")).intValue());
            control.input=true;runtime.refreshForeground();
            connection.setComposingText("你",1);connection.commitText("你好",1);connection.commitText("\n",1);
            byte[] bytes=new byte[128];
            int length=ReflectionHelpers.callInstanceMethod(queue,"read",
                ReflectionHelpers.ClassParameter.from(byte[].class,bytes),ReflectionHelpers.ClassParameter.from(boolean.class,false));
            assertEquals("你好\r",new String(bytes,0,length,java.nio.charset.StandardCharsets.UTF_8));
            assertSame(session,terminal.getCurrentSession());
        } finally {ReflectionHelpers.setField(session,"mShellPid",0);}
    }
    private Object queue() {return ReflectionHelpers.getField(session,"mTerminalToProcessIOQueue");}
    private String drain() {
        byte[] bytes=new byte[1024];
        int length=ReflectionHelpers.callInstanceMethod(queue(),"read",ReflectionHelpers.ClassParameter.from(byte[].class,bytes),
            ReflectionHelpers.ClassParameter.from(boolean.class,false));
        return new String(bytes,0,Math.max(0,length),java.nio.charset.StandardCharsets.UTF_8);
    }
    private android.view.inputmethod.InputConnection connection() {
        return terminal.onCreateInputConnection(new android.view.inputmethod.EditorInfo());
    }
    @Test public void hiddenHostRejectsLateImeTextAndControlKeys() {
        control.input=true;tap();android.view.inputmethod.InputConnection old=connection();
        ReflectionHelpers.setField(session,"mShellPid",1);
        try {
            pane.hostActive(false);old.commitText("late",1);
            pane.onKeyDown(android.view.KeyEvent.KEYCODE_C,new android.view.KeyEvent(0,0,android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_C,0,android.view.KeyEvent.META_CTRL_ON),session);
            assertEquals("",drain());assertEquals(0,control.stopped);
        } finally {ReflectionHelpers.setField(session,"mShellPid",0);}
    }
    @Test public void focusLostBeforeCompositionFinishesDoesNotWriteToTerminal() {
        control.input=true;tap();android.view.inputmethod.InputConnection old=connection();
        old.setComposingText("未确认",1);editor.requestFocus();
        ReflectionHelpers.setField(session,"mShellPid",1);
        try {old.finishComposingText();assertEquals("",drain());assertEquals("",editor.getText().toString());}
        finally {ReflectionHelpers.setField(session,"mShellPid",0);}
    }
    @Test public void closedConnectionDropsCompositionAndCannotCommitLater() {
        control.input=true;tap();android.view.inputmethod.InputConnection old=connection();old.setComposingText("pending",1);
        ReflectionHelpers.setField(session,"mShellPid",1);
        try {old.closeConnection();old.commitText("late",1);assertEquals("",drain());}
        finally {ReflectionHelpers.setField(session,"mShellPid",0);}
    }
    @Test public void newerConnectionInvalidatesPreviousBufferOnSameView() {
        control.input=true;tap();android.view.inputmethod.InputConnection old=connection();old.setComposingText("old",1);
        android.view.inputmethod.InputConnection current=connection();
        ReflectionHelpers.setField(session,"mShellPid",1);
        try {old.finishComposingText();old.commitText("late",1);current.commitText("new",1);assertEquals("new",drain());}
        finally {ReflectionHelpers.setField(session,"mShellPid",0);}
    }
    @Test public void refocusingDoesNotReviveOldCompositionConnection() {
        control.input=true;tap();android.view.inputmethod.InputConnection old=connection();old.setComposingText("old",1);
        editor.requestFocus();assertTrue(pane.restoreTerminalFocus());
        ReflectionHelpers.setField(session,"mShellPid",1);
        try {old.finishComposingText();assertEquals("",drain());connection().commitText("fresh",1);assertEquals("fresh",drain());}
        finally {ReflectionHelpers.setField(session,"mShellPid",0);}
    }
    @Test public void replacementSessionCannotReceivePreviousInputConnection() {
        control.input=true;tap();android.view.inputmethod.InputConnection old=connection();old.setComposingText("old",1);
        TerminalSession previous=session;
        com.codepocket.ide.terminal.shell.ShellLaunchPlan plan=runtime.plan();
        session=new TerminalSession(ShellLaunchPlan.EXECUTABLE,plan.directory.getPath(),plan.arguments,plan.environment,2000,runtime);
        ReflectionHelpers.setField(session,"mEmulator",new TerminalEmulator(session,60,8,13,15,2000,runtime));
        ReflectionHelpers.setField(session,"mSessionClosed",true);ReflectionHelpers.setField(runtime,"session",session);previous.finishIfRunning();
        TerminalView same=terminal;pane.changed();assertSame(same,ReflectionHelpers.getField(pane,"terminal"));
        ReflectionHelpers.setField(session,"mShellPid",1);
        try {old.finishComposingText();old.commitText("late",1);assertEquals("",drain());connection().commitText("fresh",1);assertEquals("fresh",drain());}
        finally {ReflectionHelpers.setField(session,"mShellPid",0);}
    }
    @Test public void disposedPaneRejectsLateImeAndExplicitControlShortcut() {
        control.input=true;tap();android.view.inputmethod.InputConnection old=connection();pane.dispose();
        ReflectionHelpers.setField(session,"mShellPid",1);
        try {
            old.commitText("late",1);
            ReflectionHelpers.callInstanceMethod(pane,"sendKey",ReflectionHelpers.ClassParameter.from(String.class,"\u0003"));
            assertEquals("",drain());assertEquals(0,control.stopped);
        } finally {ReflectionHelpers.setField(session,"mShellPid",0);}
    }
}
