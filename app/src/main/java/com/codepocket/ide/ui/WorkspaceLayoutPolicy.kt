package com.codepocket.ide.ui

data class ToolbarLayout(val control: Int, val explorer: Boolean, val run: Boolean, val title: Boolean) {
    val controlsWidth get() = control * (1 + (if (explorer) 1 else 0) + (if (run) 1 else 0))
}
data class PanelToolbarLayout(val preset: Boolean, val maximize: Boolean, val close: Boolean)
data class PresetInputLayout(val showInput: Boolean, val showCaption: Boolean, val showTabs: Boolean, val inputHeight: Int)

/** Shell budgets use measured content dimensions, independent of orientation and device name. */
object WorkspaceLayoutPolicy {
    fun toolbar(width: Int): ToolbarLayout {
        val available = width.coerceAtLeast(0)
        val control = minOf(DesignTokens.TOUCH, available)
        val explorer = available >= 240
        val run = available >= 96
        val remaining = available - control * (1 + (if (explorer) 1 else 0) + (if (run) 1 else 0))
        return ToolbarLayout(control, explorer, run, remaining >= 64)
    }
    fun panelToolbar(width: Int) = PanelToolbarLayout(width >= 144, width >= 240, width >= 192)
    fun preset(expanded: Boolean, ime: Boolean, inputFocused: Boolean, height: Int): PresetInputLayout {
        val compact = (ime && inputFocused) || height < 240
        return PresetInputLayout(expanded, expanded && !compact, !expanded || !compact || height >= 192,
            if (compact) 48 else 64)
    }
    fun restorePreset(expanded: Boolean, focus: String, panelVisible: Boolean) =
        expanded || (focus == "stdin" && panelVisible)
    fun showSnippet(active: Boolean, ime: Boolean, height: Int) = active && !ime && height >= 360
}
