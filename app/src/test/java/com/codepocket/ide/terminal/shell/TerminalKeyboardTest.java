package com.codepocket.ide.terminal.shell;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.inputmethod.InputMethodManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowInputMethodManager;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

/** Android focus/IME request regression only; no claim about a physical keyboard or Android PTY. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
@LooperMode(LooperMode.Mode.PAUSED)
public class TerminalKeyboardTest {
    private ActivityController<Activity> controller;
    private EditText editor,terminal;
    private TerminalKeyboard keyboard;
    private ShadowInputMethodManager ime;
    @Before public void setUp() {
        controller=Robolectric.buildActivity(Activity.class).setup().visible();
        Activity activity=controller.get();
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);
        editor=new EditText(activity);terminal=new EditText(activity);
        layout.addView(editor);layout.addView(terminal);activity.setContentView(layout);
        shadowOf(Looper.getMainLooper()).idle();editor.requestFocus();
        InputMethodManager manager=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        ime=shadowOf(manager);manager.hideSoftInputFromWindow(editor.getWindowToken(),0);
        keyboard=new TerminalKeyboard();
    }
    @After public void tearDown() {keyboard.cancel();controller.pause().stop().destroy();}
    @Test public void explicitRequestSwitchesFocusThenShowsImeOnNextTurn() {
        keyboard.show(terminal,()->true);
        assertTrue(terminal.hasFocus());assertFalse(ime.isSoftInputVisible());
        shadowOf(Looper.getMainLooper()).idle();assertTrue(ime.isSoftInputVisible());
    }
    @Test public void startupGateKeepsEditorFocusAndDoesNotRequestIme() {
        keyboard.show(terminal,()->false);shadowOf(Looper.getMainLooper()).idle();
        assertTrue(editor.hasFocus());assertFalse(ime.isSoftInputVisible());
    }
    @Test public void focusLostBeforeDeferredRequestDoesNotStealKeyboardBack() {
        keyboard.show(terminal,()->true);editor.requestFocus();
        shadowOf(Looper.getMainLooper()).idle();assertTrue(editor.hasFocus());assertFalse(ime.isSoftInputVisible());
    }
    @Test public void cancellationAndLateGatePreventHiddenPaneFromOpeningIme() {
        keyboard.show(terminal,()->true);keyboard.cancel();shadowOf(Looper.getMainLooper()).idle();
        assertFalse(ime.isSoftInputVisible());
        boolean[] allowed={true};keyboard.show(terminal,()->allowed[0]);allowed[0]=false;
        shadowOf(Looper.getMainLooper()).idle();assertFalse(ime.isSoftInputVisible());
    }
}
