package com.codepocket.ide.ui

/**
 * Responsive layout decisions based on measured content dimensions after system bars and IME,
 * independent of device orientation or screen size category.
 */
data class WorkspaceGeometry(
    val contentWidth: Int,
    val contentHeight: Int,
    val imeVisible: Boolean
) {
    val widthCategory: WidthCategory get() = when {
        contentWidth < 600 -> WidthCategory.NARROW
        contentWidth < 840 -> WidthCategory.MEDIUM
        else -> WidthCategory.WIDE
    }

    val showActivityBar get() = widthCategory == WidthCategory.WIDE && contentHeight >= 360
    val showExplorerSidebar get() = widthCategory == WidthCategory.WIDE && contentHeight >= 360
    val explorerCanResize get() = showExplorerSidebar
    val preferBottomPanel get() = true // Always prefer bottom unless user explicitly configures side

    companion object {
        fun fromPixels(width: Int, height: Int, density: Float, imeVisible: Boolean): WorkspaceGeometry {
            val scale = if (density.isFinite() && density > 0f) density else 1f
            return WorkspaceGeometry((width / scale).toInt().coerceAtLeast(0), (height / scale).toInt().coerceAtLeast(0), imeVisible)
        }
        fun measure(view: android.view.View, imeVisible: Boolean): WorkspaceGeometry {
            return fromPixels(view.width, view.height, view.resources.displayMetrics.density, imeVisible)
        }
    }
}

enum class WidthCategory {
    NARROW,  // < 600dp: phone portrait, narrow split
    MEDIUM,  // 600-839dp: phone landscape, medium tablet
    WIDE     // >= 840dp: large tablet, desktop
}

data class ExplorerLayout(
    val mode: ExplorerMode,
    val width: Int,
    val minWidth: Int,
    val maxWidth: Int,
    val resizable: Boolean
) {
    companion object {
        const val ACTIVITY_BAR_WIDTH = 48
        const val DEFAULT_SIDEBAR_WIDTH = 240
        const val MIN_SIDEBAR_WIDTH = 180
        const val MAX_SIDEBAR_WIDTH_FRACTION = 0.4f

        fun resolve(geometry: WorkspaceGeometry, userWidth: Int?): ExplorerLayout {
            if (!geometry.showExplorerSidebar) {
                return ExplorerLayout(ExplorerMode.OVERLAY, 0, 0, 0, false)
            }

            val maxWidth = (geometry.contentWidth * MAX_SIDEBAR_WIDTH_FRACTION).toInt()
            val width = (userWidth ?: DEFAULT_SIDEBAR_WIDTH).coerceIn(MIN_SIDEBAR_WIDTH, maxWidth)

            return ExplorerLayout(
                mode = ExplorerMode.SIDEBAR,
                width = width,
                minWidth = MIN_SIDEBAR_WIDTH,
                maxWidth = maxWidth,
                resizable = true
            )
        }
    }
}

enum class ExplorerMode {
    OVERLAY,   // Narrow: dialog or full-screen overlay
    SIDEBAR    // Wide: resizable sidebar
}

data class StatusBarLayout(
    val language: String,
    val line: Int,
    val column: Int,
    val indent: String,
    val encoding: String,
    val state: String
) {
    fun format(): String {
        val parts = mutableListOf<String>()
        if (language.isNotEmpty()) parts.add(language)
        if (line > 0) parts.add("Ln $line, Col $column")
        if (indent.isNotEmpty()) parts.add(indent)
        if (encoding.isNotEmpty()) parts.add(encoding)
        if (state.isNotEmpty()) parts.add(state)
        return parts.joinToString(" · ")
    }
}
