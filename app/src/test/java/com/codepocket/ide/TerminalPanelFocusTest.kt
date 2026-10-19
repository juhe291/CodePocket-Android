package com.codepocket.ide

import android.widget.EditText
import android.widget.LinearLayout
import com.codepocket.ide.editor.NativeEditorPort
import com.codepocket.ide.editor.EditorViewportSnapshot
import com.codepocket.ide.terminal.shell.ShellRuntime
import com.codepocket.ide.terminal.shell.ShellLaunchPlan
import com.codepocket.ide.terminal.shell.ShellTerminalPane
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalEmulator
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Actual Activity commands and Views, unmeasured PID-0 session; no Android PTY or device IME. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class TerminalPanelFocusTest {
    private lateinit var activity:MainActivity
    private lateinit var runtime:ShellRuntime
    private lateinit var pane:ShellTerminalPane
    private lateinit var editor:CodeEditor
    private lateinit var port:NativeEditorPort
    private lateinit var region:LinearLayout
    private fun field(name:String,value:Any)=ReflectionHelpers.setField(activity,name,value)
    @Before fun prepare() {
        activity=Robolectric.buildActivity(MainActivity::class.java).get();activity.setTheme(R.style.Theme_CodePocket)
        runtime=ShellRuntime.create(activity)
        val plan=ShellLaunchPlan.prepare(activity.filesDir,activity.cacheDir,null)
        val session=TerminalSession(ShellLaunchPlan.EXECUTABLE,plan.directory.path,plan.arguments,plan.environment,2000,runtime)
        ReflectionHelpers.setField(session,"mEmulator",TerminalEmulator(session,60,8,13,15,2000,runtime))
        ReflectionHelpers.setField(session,"mSessionClosed",true)
        ReflectionHelpers.setField(runtime,"session",session);ReflectionHelpers.setField(runtime,"plan",plan)
        ReflectionHelpers.setField(runtime,"state",ShellRuntime.State.OPEN)
        pane=ShellTerminalPane(activity,null,12,false,{},runtime)
        editor=CodeEditor(activity);editor.setText("print('hello')");editor.setSelection(3)
        port=NativeEditorPort(editor,{"buffer" to 0L},{false},{true},{true},{})
        val editorRegion=LinearLayout(activity).apply {addView(editor)}
        region=LinearLayout(activity).apply {isFocusableInTouchMode=true;addView(pane)}
        val root=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;addView(editorRegion);addView(region)}
        activity.setContentView(root) // Deliberately no Activity lifecycle/traversal or JNI.
        field("editor",editor);field("editorRegion",editorRegion);field("outputRegion",region)
        field("shellPane",pane);field("input",EditText(activity));field("panelResize",PanelResizeHandle(activity))
        field("settings",AppSettings(animations=false));field("editorPort",port)
        field("loadedPath","main.cpp");field("activePath","main.cpp")
        // status stays uninitialised so updateImeLayout is outside this focused command fixture.
    }
    @After fun release(){port.close();pane.dispose();runtime.shutdown()}
    private fun maximize(value:Boolean)=ReflectionHelpers.callInstanceMethod<Any?>(activity,"setPanelMaximized",ClassParameter.from(Boolean::class.javaPrimitiveType!!,value))
    private fun restore() {
        val stamp=port.navigationSnapshot()!!
        ReflectionHelpers.callInstanceMethod<Any?>(activity,"restorePanelViewport",
            ClassParameter.from(EditorViewportSnapshot::class.java,stamp),ClassParameter.from(Boolean::class.javaPrimitiveType!!,true))
    }
    @Test fun maximizingKeepsActualTerminalFocusAndEditorSelection(){
        assertTrue(pane.restoreTerminalFocus());maximize(true)
        assertTrue(pane.hasTerminalFocus());assertEquals(3,editor.selectionStart)
        assertTrue(ReflectionHelpers.getField<PanelState>(activity,"panelState").maximized)
    }
    @Test fun restoringEditorViewportMustNotStealTerminalInput(){
        assertTrue(pane.restoreTerminalFocus());restore()
        assertTrue(pane.hasTerminalFocus());assertFalse(editor.hasFocus());assertEquals(3,editor.selectionStart)
    }
    @Test fun maximizeFromEditorStillLeavesHiddenEditorAndRestoreCanFocusIt(){
        editor.requestFocus();maximize(true);assertFalse(editor.hasFocus())
        maximize(false);restore();assertTrue(editor.hasFocus())
    }
    @Test fun cppPrefillFocusIsPreservedDuringViewportRestore(){
        val input=ReflectionHelpers.getField<EditText>(activity,"input");region.addView(input);input.requestFocus()
        restore();assertTrue(input.hasFocus());assertFalse(editor.hasFocus())
    }
    @Test fun closingPanelIntentionallyReturnsFocusToEditor(){
        assertTrue(pane.restoreTerminalFocus())
        ReflectionHelpers.callInstanceMethod<Any?>(activity,"closePanel")
        assertTrue(editor.hasFocus());assertFalse(ReflectionHelpers.getField<Boolean>(activity,"panelVisible"))
    }
    @Test fun explicitCodeNavigationCanStillFocusEditorAfterPanelRestore(){
        assertTrue(pane.restoreTerminalFocus());field("panelState",PanelState(maximized=true))
        ReflectionHelpers.callInstanceMethod<Any?>(activity,"revealEditor")
        assertTrue(editor.hasFocus());assertFalse(pane.hasTerminalFocus())
    }
}
