package com.codepocket.ide.terminal.shell

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalEmulator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class TerminalControlsClickTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun exercise(withSession:Boolean) {
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val a=c.get()
        a.getSharedPreferences("codepocket_v2",0).edit().putBoolean("terminalControlsExpanded",false).commit()
        val runtime=ShellRuntime.create(a)
        if(withSession){
            val plan=ShellLaunchPlan.prepare(a.filesDir,a.cacheDir,null)
            val session=TerminalSession(ShellLaunchPlan.EXECUTABLE,plan.directory.path,plan.arguments,plan.environment,2000,runtime)
            ReflectionHelpers.setField(session,"mEmulator",TerminalEmulator(session,50,8,10,18,2000,runtime))
            ReflectionHelpers.setField(session,"mSessionClosed",true)
            ReflectionHelpers.setField(runtime,"session",session);ReflectionHelpers.setField(runtime,"plan",plan);ReflectionHelpers.setField(runtime,"state",ShellRuntime.State.OPEN)
        }
        val pane=ShellTerminalPane(a,null,12,false,{},runtime)
        a.setContentView(pane)
        try {
            var updates=0;pane.setOnChromeChanged{updates++}
            val toggle=views(pane).single{it.contentDescription=="展开或收起终端控制键"}
            repeat(12){toggle.performClick();assertEquals(it%2==0,toggle.isSelected)}
            assertEquals(12,updates)
            toggle.performClick()
            for(label in listOf("Ctrl+C","Ctrl+D","Tab","Esc","↑","↓","←","→")) {
                views(pane).single{it.contentDescription=="发送 $label"}.performClick()
            }
            assertEquals(1,views(pane).count{it.contentDescription=="显示终端虚拟键盘"})
        }finally{pane.dispose();runtime.shutdown();c.pause().stop().destroy()}
    }
    @Test fun controlsAndEachShortcutAreSafeBeforeAProcessStarts(){exercise(false)}
    @Test fun controlsAndEachShortcutAreSafeWithAnAttachedTerminal(){exercise(true)}
}
