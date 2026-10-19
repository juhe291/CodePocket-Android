package com.codepocket.ide.ui

data class ToolbarLayout(val control: Int, val explorer: Boolean, val run: Boolean, val title: Boolean, val search: Boolean = false) {
    val controlsWidth get() = control * (1 + (if (explorer) 1 else 0) + (if (run) 1 else 0) + (if (search) 1 else 0))
}
data class PanelToolbarLayout(val preset: Boolean, val maximize: Boolean, val close: Boolean, val clear: Boolean = false)
data class PresetInputLayout(val showInput: Boolean, val showCaption: Boolean, val showTabs: Boolean, val inputHeight: Int)

/** Shell budgets use measured content dimensions, independent of orientation and device name. */
object WorkspaceLayoutPolicy {
    fun toolbar(width: Int): ToolbarLayout {
        val available = width.coerceAtLeast(0)
        val control = minOf(DesignTokens.TOUCH, available)
        val explorer = available >= 240
        val run = available >= 96
        val search = available >= 288
        val remaining = available - control * (1 + (if (explorer) 1 else 0) + (if (run) 1 else 0) + (if (search) 1 else 0))
        return ToolbarLayout(control, explorer, run, remaining >= 64, search)
    }
    fun panelToolbar(width: Int) = PanelToolbarLayout(width >= 144, width >= 240, width >= 192, width >= 288)
    fun preset(expanded: Boolean, ime: Boolean, inputFocused: Boolean, height: Int): PresetInputLayout {
        val compact = (ime && inputFocused) || height < 240
        return PresetInputLayout(expanded, expanded && !compact, !expanded || !compact || height >= 192,
            if (compact) 48 else 64)
    }
    fun restorePreset(expanded: Boolean, focus: String, panelVisible: Boolean) =
        expanded || (focus == "stdin" && panelVisible)
    fun showSnippet(active: Boolean, ime: Boolean, height: Int) = active && !ime && height >= 360

    /**
     * Determines explorer layout based on actual measured content dimensions.
     * Returns null when explorer should be hidden or use overlay mode.
     */
    fun explorerSidebar(width: Int, height: Int): Int? {
        return if (width >= 840 && height >= 360) {
            val maxWidth = (width * 0.4).toInt()
            240.coerceIn(180, maxWidth)
        } else null
    }

    /**
     * Calculates constrained explorer width during resize.
     * @param currentWidth The explorer's current width in dp
     * @param delta The drag delta in pixels
     * @param contentWidth Total available content width in dp
     * @return New explorer width in dp, or null if not resizable
     */
    fun resizeExplorer(currentWidth: Int, delta: Float, contentWidth: Int, density: Float): Int? {
        if (contentWidth < 840 || !delta.isFinite() || !density.isFinite() || density <= 0f) return null
        val minWidth = 180
        val maxWidth = (contentWidth * 0.4).toInt()
        if (maxWidth <= minWidth) return null

        val deltaDp = (delta / density).toInt()
        return (currentWidth + deltaDp).coerceIn(minWidth, maxWidth)
    }
}
