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
    private val buttonRow = LinearLayout(context)
    init {
        isHorizontalScrollBarEnabled = false; setBackgroundColor(palette.surface)
        val row = buttonRow
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
        addView(row, LayoutParams(-2, -1))
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val total=(parent as? View)?.width?.takeIf { it>0 } ?: MeasureSpec.getSize(widthMeasureSpec)
        val budget=total/resources.displayMetrics.density
        val shown=if(budget>=650)192 else if(budget>=480)144 else 48
        controls.forEach { (command,view) -> view.visibility=if(command==IdeCommand.SAVE ||
            (shown>=144 && command in listOf(IdeCommand.UNDO,IdeCommand.REDO)) || shown>=192)VISIBLE else GONE }
        super.onMeasure(MeasureSpec.makeMeasureSpec(minOf(IdeControls.dp(context,shown),MeasureSpec.getSize(widthMeasureSpec)),MeasureSpec.EXACTLY),heightMeasureSpec)
    }
    fun setState(state: CommandContext) {
        controls.forEach { (command, view) ->
            val enabled = IdeCommands.enabled(command, state)
            view.isEnabled = enabled; view.alpha = if (enabled) 1f else .4f
        }
    }
}
