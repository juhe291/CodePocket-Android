package com.codepocket.ide.ui

import android.view.View
import android.widget.PopupMenu

object IdeCommandMenu {
    fun show(anchor: View, state: CommandContext, dispatch: (IdeCommand) -> Unit): PopupMenu {
        val popup = PopupMenu(anchor.context, anchor)
        fun title(command: IdeCommand) = if (command == IdeCommand.RUN_STOP)
            (if (state.running) "停止程序" else "运行  F5") else command.title
        popup.menu.add(0, IdeCommand.RUN_STOP.ordinal + 1, 0, title(IdeCommand.RUN_STOP)).isEnabled =
            IdeCommands.enabled(IdeCommand.RUN_STOP, state)
        for (group in IdeCommands.groups) {
            val sub = popup.menu.addSubMenu(group.title)
            group.items.forEach { command -> sub.add(0, command.ordinal + 1, 0, title(command)).isEnabled = IdeCommands.enabled(command, state) }
        }
        popup.setOnMenuItemClickListener { item ->
            val command = IdeCommand.entries.getOrNull(item.itemId - 1) ?: return@setOnMenuItemClickListener false
            // Restore window focus before completion/signature helpers inspect it.
            anchor.post { if (anchor.isAttachedToWindow) dispatch(command) }
            true
        }
        popup.show()
        return popup
    }
}
