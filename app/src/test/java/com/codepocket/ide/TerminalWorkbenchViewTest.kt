package com.codepocket.ide

import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.codepocket.ide.terminal.TerminalView
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
import com.codepocket.ide.terminal.shell.ShellTerminalPane
import com.codepocket.ide.terminal.shell.TerminalCatalog
import com.codepocket.ide.ui.DesignTokens
import com.codepocket.ide.ui.MainToolbar
import com.codepocket.ide.ui.PanelToolbar
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

/** Real MainActivity panel methods/Views, with an unmeasured hierarchy: no JNI or program execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class TerminalWorkbenchViewTest {
    private lateinit var activity: MainActivity
    private lateinit var registry: ShellSessionRegistry
    private lateinit var tabs: LinearLayout
    private lateinit var region: LinearLayout
    private lateinit var text: TerminalView
    private var savedResult: RunResult? = null
    @Before fun prepare() {
        savedResult = RunSession.result
        ShellSessionRegistry.finishAll()
        activity = Robolectric.buildActivity(MainActivity::class.java).get()
        activity.setTheme(R.style.Theme_CodePocket)
        registry = ShellSessionRegistry.get(activity, null)
        tabs = LinearLayout(activity); region = LinearLayout(activity)
        text = TerminalView(activity)
        val scroll = ScrollView(activity).apply { addView(text) }
        region.addView(scroll)
        val palette = DesignTokens.palette(true)
        fun field(name: String, value: Any) = ReflectionHelpers.setField(activity,name,value)
        field("terminalRegistry",registry);field("outputTabs",tabs);field("outputRegion",region)
        field("consoleResultScroll",scroll);field("consoleText",text)
        field("input",EditText(activity));field("panelInputHeading",LinearLayout(activity))
        field("panelToolbar",PanelToolbar(activity,palette,{},{},{},{},{}))
        field("mainToolbar",MainToolbar(activity,palette,{},{},{}));field("status",TextView(activity))
    }
    @After fun release() {
        ReflectionHelpers.getField<com.codepocket.editorlab.WorkbenchEditor?>(activity,"editor")?.close()
        ReflectionHelpers.getField<ShellTerminalPane?>(activity,"shellPane")?.dispose()
        ShellSessionRegistry.finishAll()
        ReflectionHelpers.setStaticField(RunSession::class.java,"result",savedResult)
    }
    private fun show(type: Int) { ReflectionHelpers.callInstanceMethod<Any?>(activity,"showOutput",ClassParameter.from(Int::class.javaPrimitiveType!!,type)) }
    private fun labels() = (0 until tabs.childCount).map { (tabs.getChildAt(it) as TextView).text.toString() }
    @Test fun topLevelContainsTerminalAndProblemsWithOneEmbeddedShell() {
        show(3)
        assertEquals(listOf("终端","问题"),labels().take(2))
        assertFalse(labels().any { it in listOf("输出","错误","运行信息","Shell 终端") })
        val pane=ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane")
        assertEquals(View.GONE,ReflectionHelpers.getField<LinearLayout>(pane,"heading").visibility)
        assertEquals(1,(0 until region.childCount).count { region.getChildAt(it) is ShellTerminalPane })
        assertNull(registry.catalog.selected()!!.value.runtime.session())
    }
    @Test fun historicalOutputSelectionKeepsRealShellAndCannotReplayResults() {
        show(3)
        val selected=registry.catalog.selected()
        val pane=ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane")
        ReflectionHelpers.setField(activity,"result",RunResult("must not replay stdout","real stderr","0",5))
        show(0)
        assertSame(selected,registry.catalog.selected())
        assertSame(pane,ReflectionHelpers.getField(activity,"shellPane"))
        assertEquals(View.VISIBLE,pane.visibility)
        assertFalse(text.text.contains("must not replay stdout"))
        assertFalse(labels().any { it.contains("最近运行任务") })
        show(1);assertTrue(text.text.contains("real stderr"))
    }
    @Test fun noShellShowsOnlyCreateHintNotAnotherRunConsole() {
        registry.end(registry.catalog.selected()!!.id);show(3)
        assertTrue(text.text.contains("没有打开的终端"))
        assertEquals(3,ReflectionHelpers.getField<Int>(activity,"selectedOutput"))
        assertFalse(labels().any { it.contains("最近运行任务") })
    }
    @Test fun visitingProblemsAndReturningKeepsSelectedShellAndView() {
        val selected=registry.create(null);show(3)
        val pane=ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane")
        show(1);assertSame(selected,registry.catalog.selected());show(3)
        assertSame(pane,ReflectionHelpers.getField(activity,"shellPane"))
        assertSame(selected,registry.catalog.selected())
    }
    @Test fun completedTaskDoesNotOverrideProblemsSelectionOrReopenPanel() {
        show(1)
        ReflectionHelpers.setField(activity,"panelVisible",false)
        ReflectionHelpers.setStaticField(RunSession::class.java,"result",RunResult("finished","","0",2))
        ReflectionHelpers.callInstanceMethod<Any?>(activity,"renderRunState")
        assertEquals(1,ReflectionHelpers.getField<Int>(activity,"selectedOutput"))
        assertFalse(ReflectionHelpers.getField<Boolean>(activity,"panelVisible"))
    }
    @Test fun pythonNeverDisplaysLegacyPrefillControls() {
        ReflectionHelpers.setField(activity,"activePath","main.py")
        ReflectionHelpers.setField(activity,"presetInputExpanded",true)
        show(3)
        assertEquals(View.GONE,ReflectionHelpers.getField<EditText>(activity,"input").visibility)
        assertEquals(View.GONE,ReflectionHelpers.getField<LinearLayout>(activity,"panelInputHeading").visibility)
        val toolbar=ReflectionHelpers.getField<PanelToolbar>(activity,"panelToolbar")
        toolbar.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY))
        assertEquals(View.GONE,ReflectionHelpers.getField<View>(toolbar,"preset").visibility)
    }
    @Test fun cppCloudInputRemainsClearlySeparateFromPythonTerminalInput() {
        ReflectionHelpers.setField(activity,"activePath","main.cpp")
        ReflectionHelpers.setField(activity,"presetInputExpanded",true)
        show(3)
        assertEquals(View.VISIBLE,ReflectionHelpers.getField<EditText>(activity,"input").visibility)
    }

    private fun makeFocusHierarchy():com.codepocket.editorlab.WorkbenchEditor {
        val editor=com.codepocket.editorlab.WorkbenchEditor(activity)
        region.isFocusableInTouchMode=true
        val root=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;addView(editor);addView(region)}
        activity.setContentView(root)
        ReflectionHelpers.setField(activity,"editor",editor)
        ReflectionHelpers.setField(activity,"status",null) // No full layout/Activity lifecycle in this fixture.
        return editor
    }
    private fun installClosedSession(runtime:com.codepocket.ide.terminal.shell.ShellRuntime) {
        val plan=com.codepocket.ide.terminal.shell.ShellLaunchPlan.prepare(activity.filesDir,activity.cacheDir,null)
        val session=com.termux.terminal.TerminalSession(com.codepocket.ide.terminal.shell.ShellLaunchPlan.EXECUTABLE,
            plan.directory.path,plan.arguments,plan.environment,2000,runtime)
        ReflectionHelpers.setField(session,"mEmulator",com.termux.terminal.TerminalEmulator(session,60,8,13,15,2000,runtime))
        ReflectionHelpers.setField(session,"mSessionClosed",true)
        ReflectionHelpers.setField(runtime,"session",session);ReflectionHelpers.setField(runtime,"plan",plan)
        ReflectionHelpers.setField(runtime,"state",com.codepocket.ide.terminal.shell.ShellRuntime.State.OPEN)
    }
    @Test fun choosingExistingSessionTransfersTerminalFocusWithoutFocusingCode() {
        val editor=makeFocusHierarchy();installClosedSession(registry.catalog.selected()!!.value.runtime);show(3)
        val old=ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane");assertTrue(old.restoreTerminalFocus())
        val selected=registry.create(null);installClosedSession(selected.value.runtime);show(3)
        val next=ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane")
        assertNotSame(old,next);assertTrue(next.hasTerminalFocus());assertFalse(editor.hasFocus())
        assertEquals(selected.id,registry.catalog.selectedId())
    }
    @Test fun choosingUnstartedSessionKeepsInputAwayFromEditorWithoutDelayedFocus() {
        val editor=makeFocusHierarchy();installClosedSession(registry.catalog.selected()!!.value.runtime);show(3)
        assertTrue(ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane").restoreTerminalFocus())
        registry.create(null);show(3)
        assertTrue(region.isFocused);assertFalse(editor.hasFocus())
        editor.requestFocus() // Later session state cannot reclaim focus through a queued callback.
        val current=ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane")
        current.changed();assertTrue(editor.hasFocus())
    }
    @Test fun changingSessionWhileEditingCodeDoesNotStealEditorFocus() {
        val editor=makeFocusHierarchy();installClosedSession(registry.catalog.selected()!!.value.runtime);show(3)
        editor.requestFocus();val selected=registry.create(null);installClosedSession(selected.value.runtime);show(3)
        assertTrue(editor.hasFocus());assertFalse(ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane").hasTerminalFocus())
    }
    @Test fun firstOutputRefreshDoesNotConsumeTheTabletLaunchDecision() {
        ReflectionHelpers.setField(activity,"workspaceOpen",true) // Existing project launch, separate from welcome.
        ReflectionHelpers.setField(activity,"startupPanelPending",true)
        show(3)
        assertTrue(ReflectionHelpers.getField<Boolean>(activity,"startupPanelPending"))
        ReflectionHelpers.callInstanceMethod<Any?>(activity,"resolvePanelStartup",
            ClassParameter.from(Int::class.javaPrimitiveType!!,840),ClassParameter.from(Int::class.javaPrimitiveType!!,480))
        assertTrue(ReflectionHelpers.getField<Boolean>(activity,"panelVisible"))
        assertFalse(ReflectionHelpers.getField<Boolean>(activity,"startupPanelPending"))
    }


    @Test fun repeatedTerminalShortcutRevealsSameSessionAndPaneWithoutTogglingClosed(){
        val entry=registry.catalog.selected()!!
        ReflectionHelpers.setField(entry.value.runtime,"state",com.codepocket.ide.terminal.shell.ShellRuntime.State.PREPARING)
        ReflectionHelpers.callInstanceMethod<Any?>(activity,"openTerminal")
        val pane=ReflectionHelpers.getField<ShellTerminalPane>(activity,"shellPane")
        ReflectionHelpers.callInstanceMethod<Any?>(activity,"openTerminal")
        assertTrue(ReflectionHelpers.getField<Boolean>(activity,"panelVisible"));assertFalse(ReflectionHelpers.getField<Boolean>(activity,"startupPanelPending"))
        assertEquals(1,registry.catalog.entries().size);assertSame(entry,registry.catalog.selected());assertSame(pane,ReflectionHelpers.getField(activity,"shellPane"))
    }
}
