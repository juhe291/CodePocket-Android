package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/** Compact, native actions; scrolling is only a fallback for extremely narrow windows or large fonts. */
@SuppressLint("ViewConstructor")
class EditorToolbar(context: Context, palette: IdePalette, dispatch: (IdeCommand) -> Unit,
    onMenu: (View) -> Unit) : HorizontalScrollView(context) {
    private val controls = mutableMapOf<IdeCommand, View>()
    init {
        isHorizontalScrollBarEnabled = false; setBackgroundColor(palette.surface)
        val row = LinearLayout(context)
        IdeCommands.editorToolbarItems.forEach { command ->
            val icon = when (command) {
                IdeCommand.SAVE -> IdeIcon.SAVE
                IdeCommand.UNDO -> IdeIcon.UNDO
                IdeCommand.REDO -> IdeIcon.REDO
                else -> IdeIcon.SEARCH
            }
            val button = IdeControls.icon(context, palette, icon, command.title) { dispatch(command) }
            controls[command] = button
            row.addView(button, LinearLayout.LayoutParams(IdeControls.dp(context, 48), -1))
        }
        val more = IdeControls.icon(context, palette, IdeIcon.MORE, "编辑操作") {}
        more.setOnClickListener { onMenu(it) }
        row.addView(more, LinearLayout.LayoutParams(IdeControls.dp(context, 48), -1))
        addView(row, LayoutParams(-2, -1))
    }
    fun setState(state: CommandContext) {
        controls.forEach { (command, view) ->
            val enabled = IdeCommands.enabled(command, state)
            view.isEnabled = enabled; view.alpha = if (enabled) 1f else .4f
        }
    }
}
