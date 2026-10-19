package com.codepocket.ide

import com.codepocket.ide.ui.*
import org.junit.Assert.*
import org.junit.Test

class InteractionTest {
    @Test fun shortWideWindowsKeepEditorAndOutputInASideSplit() {
        val compact = PanelLayoutPolicy.resolve(PanelState(), true, 700, 300, false, false, false)
        assertEquals(PanelDock.SIDE, compact.dock); assertTrue(compact.showEditor); assertTrue(compact.showPanel)
        assertEquals(PanelDock.BOTTOM, PanelLayoutPolicy.resolve(PanelState(), true, 700, 600, false, false, false).dock)
        assertEquals(PanelDock.BOTTOM, PanelLayoutPolicy.resolve(PanelState(), true, 400, 300, false, false, false).dock)
    }
    @Test fun dirtyStatusKeepsFailuresButDoesNotClaimTheCurrentBufferIsSaved() {
        assertEquals("未保存", InteractionState.status(false, false, false, true, "✓ 已保存 main.py"))
        assertTrue(InteractionState.status(false, false, false, true, "保存失败：权限失效").contains("权限失效"))
        assertEquals("运行中", InteractionState.status(true, false, false, true, "旧消息"))
        assertEquals("提供方读取失败", InteractionState.status(false, false, false, false, "提供方读取失败"))
    }
    @Test fun globalMenuStaysShortAndRecoversEveryHiddenNavigationControl() {
        for (width in 0..1200) {
            val toolbar = WorkspaceLayoutPolicy.toolbar(width)
            val items = IdeCommands.globalMenu(toolbar, false)
            assertTrue(items.size <= 8)
            assertEquals(items.size, items.distinct().size)
            assertEquals(!toolbar.run, IdeCommand.RUN_STOP in items)
            assertEquals(!toolbar.explorer, IdeCommand.EXPLORER in items)
            assertEquals(!toolbar.search, IdeCommand.SEARCH in items)
            assertTrue(IdeCommand.PANEL in items)
            assertFalse(IdeCommand.SAVE in items)
        }
        assertEquals(IdeCommands.settingsMenuItems, IdeCommands.globalMenu(WorkspaceLayoutPolicy.toolbar(400), true))
    }
    @Test fun contextMenusAreShortAndDataActionsAreDistinct() {
        for (items in listOf(IdeCommands.editorMenuItems, IdeCommands.explorerMenuItems, IdeCommands.terminalMenuItems))
            assertTrue(items.size <= 7)
        assertTrue(IdeCommand.CLEAR_INPUT in IdeCommands.terminalMenuItems)
        assertTrue(IdeCommand.CLEAR_TERMINAL in IdeCommands.terminalMenuItems)
        val busy = CommandContext(true, true, true, false, false, true, false)
        assertTrue(IdeCommands.enabled(IdeCommand.RUN_STOP, busy))
        assertFalse(IdeCommands.enabled(IdeCommand.DELETE, busy))
    }
    @Test fun logicalCaretDoesNotDependOnWrappingOrInvalidNativeSelection() {
        assertEquals(1 to 1, InteractionState.caret("abc", -1))
        assertEquals(1 to 4, InteractionState.caret("abc", 100))
        assertEquals(2 to 3, InteractionState.caret("a\r\n😀x", 5))
        assertEquals(3 to 1, InteractionState.caret("a\nb\n", 4))
    }
    @Test fun pixelGeometryUsesDensityAndActuallyAvailableHeight() {
        val geometry = WorkspaceGeometry.fromPixels(1950, 1500, 3f, true)
        assertEquals(650, geometry.contentWidth); assertEquals(500, geometry.contentHeight)
        assertEquals(ExplorerMode.OVERLAY, ExplorerLayout.resolve(geometry, 240).mode)
        val wide = WorkspaceGeometry.fromPixels(2520, 1500, 3f, true)
        assertEquals(ExplorerMode.SIDEBAR, ExplorerLayout.resolve(wide, 240).mode)
        val short = WorkspaceGeometry.fromPixels(1950, 900, 3f, true)
        assertEquals(ExplorerMode.OVERLAY, ExplorerLayout.resolve(short, 240).mode)
    }
    @Test fun resizeUsesGestureStartAndRejectsInvalidInput() {
        val widths = listOf(10f, 20f, 30f).map { WorkspaceLayoutPolicy.resizeExplorer(240, it, 900, 2f) }
        assertEquals(listOf(245, 250, 255), widths)
        assertNull(WorkspaceLayoutPolicy.resizeExplorer(240, Float.NaN, 900, 2f))
        assertNull(WorkspaceLayoutPolicy.resizeExplorer(240, 30f, 900, 0f))
        assertEquals(360, WorkspaceLayoutPolicy.resizeExplorer(240, 9999f, 900, 2f))
    }
    @Test fun backPrioritizesTransientUiBeforeExit() {
        assertEquals(BackAction.HIDE_IME, InteractionState.back(true, true, true, true))
        assertEquals(BackAction.CLOSE_EXPLORER, InteractionState.back(false, true, true, true))
        assertEquals(BackAction.RESTORE_PANEL, InteractionState.back(false, false, true, true))
        assertEquals(BackAction.CLOSE_PANEL, InteractionState.back(false, false, false, true))
        assertEquals(BackAction.EXIT, InteractionState.back(false, false, false, false))
    }
    @Test fun clearingOutputDoesNotEraseResultAndSurvivesRenderingSameRun() {
        val state = OutputDisplayState(); val result = Any()
        state.synchronize(result, false); state.clear(); assertTrue(state.cleared)
        state.synchronize(result, false); assertTrue(state.cleared)
        state.synchronize(Any(), false); assertFalse(state.cleared)
        state.clear(); state.synchronize(result, true); assertFalse(state.cleared)
    }
}
