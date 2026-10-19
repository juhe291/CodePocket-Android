package com.codepocket.ide.ui

import com.codepocket.ide.R
import android.view.View
import android.widget.PopupMenu

object IdeCommandMenu {
    fun show(anchor: View, state: CommandContext, commands: List<IdeCommand>, dispatch: (IdeCommand) -> Unit, animations: Boolean = true): PopupMenu {
        val popup = com.codepocket.ide.animation.DialogMotion.menu(anchor.context, anchor, animations)
        commands.forEach { command ->
            val title = when {
                command == IdeCommand.RUN_STOP && state.running -> "停止程序"
                command == IdeCommand.CLEAR_INPUT -> anchor.context.getString(R.string.panel_clear_input)
                else -> command.title
            }
            popup.menu.add(0, command.ordinal + 1, 0, title).isEnabled = IdeCommands.enabled(command, state)
        }
        popup.setOnMenuItemClickListener { item ->
            val command = IdeCommand.entries.getOrNull(item.itemId - 1) ?: return@setOnMenuItemClickListener false
            anchor.post { if (anchor.isAttachedToWindow) dispatch(command) }
            true
        }
        popup.show()
        return popup
    }
}
