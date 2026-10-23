package com.codepocket.ide

import org.json.JSONObject
import kotlin.math.roundToInt

enum class PanelDock { BOTTOM, SIDE }

data class PanelViewport(val document: String, val revision: Long, val view: EditorViewState) {
    fun canRestore(document: String, revision: Long, start: Int, end: Int) =
        this.document == document && this.revision == revision && view.start == start && view.end == end
    companion object {
        fun capture(previous: EditorViewState, live: EditorViewState, sourceVisible: Boolean) =
            if (sourceVisible) live else previous.copy(focus = live.focus, inputStart = live.inputStart, inputEnd = live.inputEnd)
    }
}

/** Sizes are preferences; temporary IME/window clamps must never rewrite them. */
data class PanelState(val bottomFraction: Float = .35f, val sideFraction: Float = .36f, val maximized: Boolean = false) {
    fun normalized() = copy(bottomFraction = fraction(bottomFraction, .35f), sideFraction = fraction(sideFraction, .36f))
    fun fraction(dock: PanelDock) = normalized().let { if (dock == PanelDock.SIDE) it.sideFraction else it.bottomFraction }
    fun withFraction(dock: PanelDock, value: Float) = (if (dock == PanelDock.SIDE)
        copy(sideFraction = value) else copy(bottomFraction = value)).normalized()
    fun toJson(): String = normalized().let { state -> JSONObject().apply {
        put("version", 1); put("bottom", state.bottomFraction.toDouble()); put("side", state.sideFraction.toDouble())
        put("maximized", state.maximized)
    }.toString() }
    companion object {
        private fun fraction(value: Float, fallback: Float) = if (value.isFinite()) value.coerceIn(.05f, .9f) else fallback
        fun fromJson(json: String?): PanelState {
            val obj = runCatching { JSONObject(json ?: "{}") }.getOrNull() ?: return PanelState()
            return PanelState(obj.optDouble("bottom", .35).toFloat(), obj.optDouble("side", .36).toFloat(),
                obj.optBoolean("maximized", false)).normalized()
        }
    }
}

data class PanelLayout(
    val dock: PanelDock, val showPanel: Boolean, val showEditor: Boolean,
    val extent: Int, val available: Int, val minimum: Int = 0, val maximum: Int = 0
) {
    val resizable get() = showPanel && showEditor && maximum > minimum
    fun resize(state: PanelState, requestedExtent: Float): PanelState {
        if (!resizable || available <= 0 || !requestedExtent.isFinite()) return state
        return state.withFraction(dock, requestedExtent.coerceIn(minimum.toFloat(), maximum.toFloat()) / available)
    }
}

/** All values in dp; depends on the measured work area, not device orientation. */
object PanelLayoutPolicy {
    const val HANDLE = 24

    /** Drop optional terminal chrome before its measured minimum would hide the entire panel. */
    fun terminalCompact(height: Int, expandedMinimum: Int, inputFocusedWithIme: Boolean) =
        inputFocusedWithIme || height < expandedMinimum + HANDLE + 128

    /**
     * Resolve panel layout with bottom-first strategy.
     * Panel defaults to bottom; only uses side dock when explicitly configured and width allows.
     * @param preferSide User preference for side docking (default false = bottom priority)
     */
    fun resolve(state: PanelState, visible: Boolean, width: Int, height: Int,
        ime: Boolean, inputFocused: Boolean, collapseOnIme: Boolean, preferSide: Boolean = false, minimumPanel: Int = 0, bottomOnly: Boolean = false): PanelLayout {
        // Bottom-first strategy: only dock to side if user explicitly prefers it AND there's room
        // A short wide window has room for side-by-side panes even when a bottom split cannot fit.
        val compactSplit = width >= 600 && height in 160 until (208 + HANDLE + 128) && !state.maximized
        val dock = if (!bottomOnly && ((preferSide && width >= 840) || compactSplit)) PanelDock.SIDE else PanelDock.BOTTOM
        val available = (if (dock == PanelDock.SIDE) width else height).coerceAtLeast(0)
        fun hidden() = PanelLayout(dock, false, true, 0, available)
        if (width <= 0 || height <= 0 || !visible) return hidden()
        if (state.maximized) return PanelLayout(dock, true, false, available, available)
        if (ime && !inputFocused && collapseOnIme) return hidden()
        val minimum = if (dock == PanelDock.SIDE) 200 else maxOf(minimumPanel,if (ime && inputFocused) 136 else 208)
        val editorMinimum = if (dock == PanelDock.SIDE) 240 else 128
        val maximum = available - HANDLE - editorMinimum
        if (maximum < minimum) {
            // An explicitly visible terminal must stay reachable even at a large font size.
            return if ((ime && inputFocused) || minimumPanel > 0)
                PanelLayout(dock, true, false, available, available) else hidden()
        }
        val extent = (state.fraction(dock) * available).roundToInt().coerceIn(minimum, maximum)
        return PanelLayout(dock, true, true, extent, available, minimum, maximum)
    }

    /** The panel sits after the handle: moving up/left makes it larger. */
    fun drag(startExtent: Int, pointerDelta: Float, minimum: Int, maximum: Int): Float =
        if (!pointerDelta.isFinite() || maximum < minimum) startExtent.toFloat()
        else (startExtent - pointerDelta).coerceIn(minimum.toFloat(), maximum.toFloat())
}
