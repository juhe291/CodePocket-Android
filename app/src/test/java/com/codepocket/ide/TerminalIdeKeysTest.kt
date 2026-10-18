package com.codepocket.ide
import android.view.KeyEvent
import com.codepocket.ide.ui.IdeCommand
import org.junit.Assert.*
import org.junit.Test
class TerminalIdeKeysTest {
 @Test fun ideSaveOpenCreateAndExplorerWorkFromTerminal() {
  for((key,command) in listOf(KeyEvent.KEYCODE_S to IdeCommand.SAVE,KeyEvent.KEYCODE_N to IdeCommand.NEW_FILE,
   KeyEvent.KEYCODE_O to IdeCommand.IMPORT_COPY,KeyEvent.KEYCODE_B to IdeCommand.EXPLORER))
    assertEquals(command,TerminalIdeKeys.command(key,true,false,false,false))
  assertEquals(IdeCommand.RUN_STOP,TerminalIdeKeys.command(KeyEvent.KEYCODE_F5,false,false,false,false))
 }
 @Test fun copyStopEofAndTerminalEditingKeysAreNeverStolen() {
  for(key in listOf(KeyEvent.KEYCODE_C,KeyEvent.KEYCODE_D,KeyEvent.KEYCODE_Q,KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_TAB,
   KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_DPAD_UP))
   assertNull(TerminalIdeKeys.command(key,true,false,false,false))
 }
 @Test fun additionalModifiersDoNotAccidentallyLaunchIdeActions() {
  assertNull(TerminalIdeKeys.command(KeyEvent.KEYCODE_S,true,true,false,false))
  assertNull(TerminalIdeKeys.command(KeyEvent.KEYCODE_S,true,false,true,false))
  assertNull(TerminalIdeKeys.command(KeyEvent.KEYCODE_S,true,false,false,true))
 }
}
