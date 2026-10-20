package com.codepocket.ide

import com.codepocket.ide.terminal.shell.*
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class TerminalPaletteTest {
    @Test fun everyPaletteHasSixteenAnsiColorsAndFollowingLightUsesLightBackground(){
        for(theme in TerminalTheme.values()){
            val p=TerminalAppearance.palette(theme.name,true);assertEquals(16,p.ansi.size);assertNotEquals(p.background,p.foreground)
        }
        assertEquals(TerminalAppearance.palette("LIGHT",true).background,TerminalAppearance.palette("FOLLOW_UI",false).background)
        assertNotEquals(TerminalAppearance.palette("NORD",true).background,TerminalAppearance.palette("DRACULA",true).background)
    }
    @Test fun changingThemeKeepsActualEmulatorTranscriptAndCursor(){
        val activity=Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val runtime=ShellRuntime.create(activity);val plan=ShellLaunchPlan.prepare(activity.filesDir,activity.cacheDir,null)
        val session=TerminalSession(ShellLaunchPlan.EXECUTABLE,plan.directory.path,plan.arguments,plan.environment,2000,runtime)
        ReflectionHelpers.setField(session,"mSessionClosed",true)
        val emulator=TerminalEmulator(session,80,24,10,16,2000,runtime);ReflectionHelpers.setField(session,"mEmulator",emulator)
        val bytes="\u001b[31mred\u001b[0m \u001b[38;2;123;145;167mtruecolor\u001b[0m".toByteArray()
        emulator.append(bytes,bytes.size);val text=emulator.screen.transcriptText;val col=emulator.cursorCol
        TerminalAppearance.apply(session,"NORD",true)
        assertSame(emulator,session.emulator);assertEquals(text,emulator.screen.transcriptText);assertEquals(col,emulator.cursorCol)
        assertEquals(TerminalAppearance.palette("NORD",true).background,emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND])
        emulator.mColors.tryParseColor(1,"#123456")
        TerminalAppearance.apply(session,"NORD",true);assertEquals(0xff123456.toInt(),emulator.mColors.mCurrentColors[1])
        TerminalAppearance.apply(session,"LIGHT",false);assertEquals(text,emulator.screen.transcriptText)
        runtime.shutdown()
    }
    @Test fun coloredPromptKeepsZeroWidthDelimitersAndBothIntegrationMarkers(){
        val p=ShellLaunchPlan.PROMPT
        assertTrue(p.startsWith("\u0001\r\u0001\u001b]133;A\u0007"));assertTrue(p.endsWith("\u001b]133;B\u0007\u0001"))
        assertTrue(p.contains("\u001b[0m"));assertTrue(p.contains("$"+"{PWD}"))
        assertTrue(ShellLaunchPlan.STARTUP.indexOf("/system/etc/mkshrc")<ShellLaunchPlan.STARTUP.indexOf("PS1="))
    }
}
