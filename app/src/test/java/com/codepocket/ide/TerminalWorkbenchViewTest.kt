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
        field("mainToolbar",MainToolbar(activity,palette,{},{},{},{},{}));field("status",TextView(activity))
    }
    @After fun release() {
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
}
