package com.codepocket.ide.ui

import com.codepocket.ide.R
import android.view.View
import android.widget.PopupMenu

object IdeCommandMenu {
    fun show(anchor: View, state: CommandContext, commands: List<IdeCommand>, dispatch: (IdeCommand) -> Unit, animations: Boolean = true, heading: String? = null, onBack: (() -> Unit)? = null): PopupMenu {
        val popup = com.codepocket.ide.animation.DialogMotion.menu(anchor.context, anchor, animations)
        if(heading!=null)(popup as? WorkbenchMenu)?.setHeading(heading,"返回工作台菜单",onBack)
        commands.forEach { command ->
            val title = when {
                command == IdeCommand.RUN_STOP && state.running -> "停止程序"
                command == IdeCommand.CLEAR_INPUT -> anchor.context.getString(R.string.panel_clear_input)
                else -> command.title
            }
            popup.menu.add(section(command), command.ordinal + 1, 0, title).isEnabled = IdeCommands.enabled(command, state)
            (popup as? WorkbenchMenu)?.setItemAppearance(command.ordinal+1,icon(command,state.running),command==IdeCommand.DELETE)
        }
        popup.setOnMenuItemClickListener { item ->
            val command = IdeCommand.entries.getOrNull(item.itemId - 1) ?: return@setOnMenuItemClickListener false
            anchor.post { if (anchor.isAttachedToWindow) dispatch(command) }
            true
        }
        popup.show()
        return popup
    }
    /** Presentation follows command identity, including translated or state-dependent titles. */
    internal fun icon(command:IdeCommand,running:Boolean):IdeIcon=when(command){
        IdeCommand.RUN_STOP->if(running)IdeIcon.STOP else IdeIcon.RUN
        IdeCommand.EXPLORER->IdeIcon.EXPLORER
        IdeCommand.SEARCH,IdeCommand.FIND->IdeIcon.SEARCH
        IdeCommand.NEW_FILE->IdeIcon.ADD_FILE
        IdeCommand.NEW_FOLDER->IdeIcon.ADD_FOLDER
        IdeCommand.RENAME->IdeIcon.RENAME
        IdeCommand.DELETE->IdeIcon.DELETE
        IdeCommand.WORKSPACE->IdeIcon.FOLDER_OPEN
        IdeCommand.IMPORT_COPY->IdeIcon.IMPORT
        IdeCommand.EXPORT->IdeIcon.EXPORT
        IdeCommand.UNDO->IdeIcon.UNDO
        IdeCommand.REDO->IdeIcon.REDO
        IdeCommand.REPLACE,IdeCommand.RELOAD,IdeCommand.RECOVER->IdeIcon.REFRESH
        IdeCommand.GOTO_LINE->IdeIcon.LOCATE
        IdeCommand.SAVE->IdeIcon.SAVE
        IdeCommand.COMPLETE,IdeCommand.SNIPPET,IdeCommand.SEMICOLON,IdeCommand.EQUALS,IdeCommand.COLON,IdeCommand.TEST_CASES->IdeIcon.CODE
        IdeCommand.SIGNATURE,IdeCommand.BRACES,IdeCommand.BRACKETS,IdeCommand.PARENS->IdeIcon.BRACKETS
        IdeCommand.TAB,IdeCommand.RIGHT->IdeIcon.NEXT
        IdeCommand.UNINDENT,IdeCommand.LEFT->IdeIcon.BACK
        IdeCommand.PANEL->IdeIcon.TERMINAL
        IdeCommand.PANEL_SIZE->IdeIcon.MOVE
        IdeCommand.CLEAR_TERMINAL,IdeCommand.CLEAR_INPUT->IdeIcon.CLEAR
        IdeCommand.MAXIMIZE_TERMINAL->IdeIcon.MAXIMIZE
        IdeCommand.CLOSE_TERMINAL->IdeIcon.DOWN
        IdeCommand.PRESET_INPUT,IdeCommand.HIDE_KEYBOARD,IdeCommand.SHORTCUTS->IdeIcon.INPUT
        IdeCommand.THEME->IdeIcon.PALETTE
        IdeCommand.SETTINGS->IdeIcon.SETTINGS
        IdeCommand.HELP->IdeIcon.HELP
        IdeCommand.FONT_SIZE->IdeIcon.CASE
    }
    internal fun section(command:IdeCommand):Int=when(command){
        IdeCommand.WORKSPACE,IdeCommand.IMPORT_COPY->1
        IdeCommand.NEW_FILE,IdeCommand.NEW_FOLDER->2
        IdeCommand.SAVE,IdeCommand.EXPORT->3
        IdeCommand.RELOAD,IdeCommand.RECOVER->4
        IdeCommand.RENAME->5
        IdeCommand.DELETE->6
        IdeCommand.UNDO,IdeCommand.REDO->7
        IdeCommand.FIND,IdeCommand.REPLACE,IdeCommand.GOTO_LINE->8
        IdeCommand.FONT_SIZE->9
        IdeCommand.COMPLETE,IdeCommand.SIGNATURE,IdeCommand.SNIPPET->10
        IdeCommand.EXPLORER,IdeCommand.SEARCH->11
        IdeCommand.PANEL,IdeCommand.PANEL_SIZE,IdeCommand.MAXIMIZE_TERMINAL,IdeCommand.CLOSE_TERMINAL->12
        IdeCommand.CLEAR_TERMINAL->13
        IdeCommand.PRESET_INPUT,IdeCommand.CLEAR_INPUT->14
        IdeCommand.HIDE_KEYBOARD->15
        IdeCommand.THEME,IdeCommand.SETTINGS->16
        IdeCommand.SHORTCUTS,IdeCommand.HELP->17
        IdeCommand.RUN_STOP->18
        else->19
    }

}
