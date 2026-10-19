package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class PanelLayoutTest {
    private fun layout(state: PanelState = PanelState(), width: Int = 400, height: Int = 700,
        visible: Boolean = true, ime: Boolean = false, stdin: Boolean = false, collapse: Boolean = true,
        preferSide: Boolean = false) =
        PanelLayoutPolicy.resolve(state, visible, width, height, ime, stdin, collapse, preferSide)

    @Test fun dockingUsesMeasuredWorkspaceWidthAndKeepsIndependentPreferences() {
        val state = PanelState(bottomFraction = .6f, sideFraction = .3f)
        // P1-B: Bottom-first strategy - width < 840 uses bottom by default
        val bottom = layout(state, width = 599)
        assertEquals(PanelDock.BOTTOM, bottom.dock); assertEquals(420, bottom.extent)
        // Even 1000dp uses bottom unless preferSide is true
        val bottomWide = layout(state, width = 1000)
        assertEquals(PanelDock.BOTTOM, bottomWide.dock); assertEquals(420, bottomWide.extent)
        // With preferSide and width >= 840, uses side
        val side = layout(state, width = 1000, preferSide = true)
        assertEquals(PanelDock.SIDE, side.dock); assertEquals(300, side.extent)
        // A 768dp window: bottom (< 840)
        assertEquals(PanelDock.BOTTOM, layout(state, width = 768 - 205).dock)
        // 600dp: bottom by default
        assertEquals(PanelDock.BOTTOM, layout(state, width = 600).dock)
        // 600dp with preferSide: still bottom (< 840)
        assertEquals(PanelDock.BOTTOM, layout(state, width = 600, preferSide = true).dock)
    }

    @Test fun visibilityAndImeRulesRespectExplicitMaximizeAndStdin() {
        assertFalse(layout(ime = true).showPanel)
        assertTrue(layout(ime = true, collapse = false).showPanel)
        assertTrue(layout(ime = true, stdin = true).showPanel)
        val maximum = layout(PanelState(maximized = true), ime = true)
        assertTrue(maximum.showPanel); assertFalse(maximum.showEditor); assertFalse(maximum.resizable)
        val closed = layout(PanelState(maximized = true), visible = false)
        assertFalse(closed.showPanel); assertTrue(closed.showEditor)
    }

    @Test fun tinyInputWindowsKeepStdinReachableWithoutChangingRememberedMode() {
        val state = PanelState(bottomFraction = .65f)
        val input = layout(state, height = 190, ime = true, stdin = true)
        assertTrue(input.showPanel); assertFalse(input.showEditor); assertEquals(190, input.extent)
        assertFalse(input.resizable); assertFalse(state.maximized)
        val code = layout(state, height = 190, ime = true, collapse = false)
        assertFalse(code.showPanel); assertTrue(code.showEditor)
        val smallWindow = layout(state, height = 190)
        assertTrue(smallWindow.showEditor); assertFalse(smallWindow.showPanel)
        val explicit = layout(state.copy(maximized = true), height = 190)
        assertTrue(explicit.showPanel); assertFalse(explicit.showEditor)
    }

    @Test fun temporaryWindowClampsDoNotOverwriteSizeOrMaximizePreferences() {
        val state = PanelState(.7f, .75f)
        val clamped = layout(state, height = 400)
        assertEquals(248, clamped.extent)
        assertEquals(.7f, state.bottomFraction, .0001f)
        assertEquals(700, layout(state, height = 1000).extent)
        layout(state, height = 200, ime = true, stdin = true)
        assertEquals(700, layout(state, height = 1000).extent)
        // P1-B: 900dp width with preferSide uses side dock
        val side = layout(state, width = 900, preferSide = true)
        assertEquals(PanelDock.SIDE, side.dock)
        // 900 * .75 = 675, but clamped by maximum (900 - HANDLE - editorMinimum = 900 - 24 - 240 = 636)
        assertEquals(636, side.extent)
        assertEquals(.75f, state.sideFraction, .0001f)
    }

    @Test fun draggingMovesInTheExpectedDirectionAndCannotEraseTheEditor() {
        assertEquals(280f, PanelLayoutPolicy.drag(240, -40f, 208, 500), .001f)
        assertEquals(208f, PanelLayoutPolicy.drag(240, 1000f, 208, 500), .001f)
        assertEquals(500f, PanelLayoutPolicy.drag(240, -1000f, 208, 500), .001f)
        assertEquals(240f, PanelLayoutPolicy.drag(240, Float.NaN, 208, 500), .001f)
        assertEquals(240f, PanelLayoutPolicy.drag(240, 1f, 500, 208), .001f)
        val state = PanelState()
        val spec = layout(state)
        val resized = spec.resize(state, 400f)
        assertEquals(400, layout(resized).extent)
        assertEquals(state.sideFraction, resized.sideFraction, .001f)
        val largest = layout(spec.resize(state, Float.MAX_VALUE))
        assertEquals(spec.maximum, largest.extent)
        assertTrue(largest.available - largest.extent - PanelLayoutPolicy.HANDLE >= 128)
        assertEquals(state, spec.resize(state, Float.NaN))
    }

    @Test fun hiddenMaximizedAndUnmeasuredPanelsDoNotAcceptResize() {
        val state = PanelState()
        assertEquals(state, layout(visible = false).resize(state, 300f))
        assertEquals(state, layout(state.copy(maximized = true)).resize(state, 300f))
        for ((width, height) in listOf(0 to 700, 400 to 0, -1 to -1)) {
            val spec = layout(width = width, height = height)
            assertFalse(spec.showPanel); assertTrue(spec.showEditor); assertEquals(0, spec.extent)
            assertEquals(state, spec.resize(state, 300f))
        }
    }

    @Test fun corruptOrFuturePreferencesMigrateWithoutLosingOtherAxis() {
        assertEquals(PanelState(), PanelState.fromJson(null))
        assertEquals(PanelState(), PanelState.fromJson("not json"))
        val state = PanelState(.42f, .63f, true)
        assertEquals(state, PanelState.fromJson(state.toJson()))
        val malformed = PanelState.fromJson("""{"version":99,"bottom":999,"side":-9,"maximized":true}""")
        assertEquals(.9f, malformed.bottomFraction, .001f); assertEquals(.05f, malformed.sideFraction, .001f)
        assertTrue(malformed.maximized)
        assertEquals(.35f, PanelState(Float.NaN, Float.POSITIVE_INFINITY).normalized().bottomFraction, .001f)
        assertEquals(.36f, PanelState(Float.NaN, Float.POSITIVE_INFINITY).normalized().sideFraction, .001f)
    }

    @Test fun hiddenEditorCaptureKeepsItsViewportWhileStdinSelectionStaysLive() {
        val saved = EditorViewState(42, 9, 300, 800, "editor", 1, 2)
        val live = EditorViewState(42, 9, 0, 0, "stdin", 7, 8)
        assertEquals(saved.copy(focus = "stdin", inputStart = 7, inputEnd = 8), PanelViewport.capture(saved, live, false))
        assertEquals(live, PanelViewport.capture(saved, live, true))
        assertEquals(42, PanelViewport.capture(saved, live, false).start)
        assertEquals(9, PanelViewport.capture(saved, live, false).end)
    }

    @Test fun viewportRestoreNeverMovesAnotherFileOrNewlyChangedSelection() {
        val stamp = PanelViewport("project:file", 10, EditorViewState(7, 2, 30, 90, "stdin", 3, 4))
        assertTrue(stamp.canRestore("project:file", 10, 7, 2))
        assertFalse(stamp.canRestore("another:file", 10, 7, 2))
        assertFalse(stamp.canRestore("project:file", 11, 7, 2))
        assertFalse(stamp.canRestore("project:file", 10, 8, 2))
        assertFalse(stamp.canRestore("project:file", 10, 2, 7))
    }

    @Test fun layoutInvariantsHoldAcrossWindowsImeAndRememberedSizes() {
        for (width in listOf(0, 200, 599, 600, 800, 1400)) for (height in listOf(0, 90, 200, 368, 700, 1200))
            for (fraction in listOf(.05f, .35f, .9f, Float.NaN)) for (flags in 0..31) {
                val state = PanelState(fraction, fraction, flags and 1 != 0)
                val spec = layout(state, width, height, flags and 2 != 0, flags and 4 != 0, flags and 8 != 0, flags and 16 != 0)
                assertTrue(spec.showPanel || spec.showEditor)
                assertTrue(spec.extent in 0..spec.available)
                if (!spec.showPanel) { assertEquals(0, spec.extent); assertFalse(spec.resizable) }
                if (spec.resizable) {
                    assertTrue(spec.showEditor); assertTrue(spec.extent in spec.minimum..spec.maximum)
                    assertTrue(spec.extent + PanelLayoutPolicy.HANDLE <= spec.available)
                }
            }
    }
    @Test fun terminalMinimumProtectsRowsWithImeAndRestoresSavedFraction() {
        val state=PanelState(bottomFraction=.2f)
        val normal=PanelLayoutPolicy.resolve(state,true,400,900,false,true,true,false,320)
        assertTrue(normal.showPanel);assertEquals(320,normal.extent)
        val ime=PanelLayoutPolicy.resolve(state,true,400,400,true,true,true,false,240)
        assertTrue(ime.showEditor);assertEquals(240,ime.extent)
        val tiny=PanelLayoutPolicy.resolve(state,true,400,300,true,true,true,false,240)
        assertTrue(tiny.showPanel);assertFalse(tiny.showEditor);assertEquals(300,tiny.extent)
        assertEquals(.2f,state.bottomFraction,.001f)
        val restored=PanelLayoutPolicy.resolve(state,true,400,1800,false,true,true,false,320)
        assertEquals(360,restored.extent)
    }
    @Test fun compactTerminalKeepsShortWindowsVisibleAtDifferentFontSizes() {
        for (expandedMinimum in listOf(240, 264, 320, 400)) {
            val boundary=expandedMinimum+PanelLayoutPolicy.HANDLE+128
            assertTrue(PanelLayoutPolicy.terminalCompact(boundary-1,expandedMinimum,false))
            assertFalse(PanelLayoutPolicy.terminalCompact(boundary,expandedMinimum,false))
            assertTrue(PanelLayoutPolicy.terminalCompact(900,expandedMinimum,true))
            val compact=PanelLayoutPolicy.terminalCompact(400,expandedMinimum,false)
            val minimum=if(compact) expandedMinimum-96 else expandedMinimum
            val spec=PanelLayoutPolicy.resolve(PanelState(),true,400,400,false,false,true,false,minimum)
            assertTrue("Terminal must stay visible at $expandedMinimum",spec.showPanel)
        }
    }
    @Test fun visibleTerminalUsesFullPaneWhenLargeFontCannotFitSplit() {
        val state=PanelState(bottomFraction=.35f)
        for(width in listOf(320,400,599,840))for(height in listOf(160,190,300,400,700)) {
            val spec=PanelLayoutPolicy.resolve(state,true,width,height,false,false,true,false,400)
            assertTrue(spec.showPanel)
            assertEquals(.35f,state.bottomFraction,.001f)
            assertFalse(state.maximized)
            val closed=PanelLayoutPolicy.resolve(state,false,width,height,false,false,true,false,400)
            assertFalse(closed.showPanel)
        }
        assertFalse(PanelLayoutPolicy.resolve(state,true,400,400,true,false,true,false,400).showPanel)
    }
}
