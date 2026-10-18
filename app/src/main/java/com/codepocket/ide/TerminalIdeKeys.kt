package com.codepocket.ide
import android.view.KeyEvent
import com.codepocket.ide.ui.IdeCommand
/** VS Code-style IDE commands still work when the terminal owns focus. C/D and editing keys stay with the PTY. */
object TerminalIdeKeys {
 fun command(key:Int,ctrl:Boolean,shift:Boolean,alt:Boolean,meta:Boolean):IdeCommand? {
  if(alt||meta||shift)return null
  if(key==KeyEvent.KEYCODE_F5&&!ctrl)return IdeCommand.RUN_STOP
  if(!ctrl)return null
  return when(key) {
   KeyEvent.KEYCODE_S->IdeCommand.SAVE
   KeyEvent.KEYCODE_N->IdeCommand.NEW_FILE
   KeyEvent.KEYCODE_O->IdeCommand.IMPORT_COPY
   KeyEvent.KEYCODE_B->IdeCommand.EXPLORER
   else->null
  }
 }
}
