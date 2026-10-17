package com.codepocket.ide

import com.codepocket.ide.ui.*
import org.junit.Assert.*
import org.junit.Test

class ResponsiveLayoutTest {

    @Test
    fun `narrow width shows overlay explorer`() {
        val geometry = WorkspaceGeometry(400, 800, false)
        assertEquals(WidthCategory.NARROW, geometry.widthCategory)
        assertFalse(geometry.showActivityBar)
        assertFalse(geometry.showExplorerSidebar)

        val explorerLayout = ExplorerLayout.resolve(geometry, null)
        assertEquals(ExplorerMode.OVERLAY, explorerLayout.mode)
        assertFalse(explorerLayout.resizable)
    }

    @Test
    fun `medium width shows sidebar with activity bar`() {
        val geometry = WorkspaceGeometry(650, 500, false)
        assertEquals(WidthCategory.MEDIUM, geometry.widthCategory)
        assertTrue(geometry.showActivityBar)
        assertTrue(geometry.showExplorerSidebar)

        val explorerLayout = ExplorerLayout.resolve(geometry, null)
        assertEquals(ExplorerMode.SIDEBAR, explorerLayout.mode)
        assertTrue(explorerLayout.resizable)
        assertTrue(explorerLayout.width in 180..260) // 650 * 0.4 = 260 max
    }

    @Test
    fun `wide width provides full features`() {
        val geometry = WorkspaceGeometry(1000, 600, false)
        assertEquals(WidthCategory.WIDE, geometry.widthCategory)
        assertTrue(geometry.showActivityBar)
        assertTrue(geometry.showExplorerSidebar)
        assertTrue(geometry.preferBottomPanel)

        val explorerLayout = ExplorerLayout.resolve(geometry, null)
        assertEquals(ExplorerMode.SIDEBAR, explorerLayout.mode)
        assertEquals(240, explorerLayout.width) // Default
        assertEquals(180, explorerLayout.minWidth)
        assertEquals(400, explorerLayout.maxWidth) // 1000 * 0.4
    }

    @Test
    fun `explorer respects user width within bounds`() {
        val geometry = WorkspaceGeometry(900, 600, false)

        // User width within bounds
        val layout1 = ExplorerLayout.resolve(geometry, 300)
        assertEquals(300, layout1.width)

        // User width too small
        val layout2 = ExplorerLayout.resolve(geometry, 100)
        assertEquals(180, layout2.width) // Clamped to min

        // User width too large
        val layout3 = ExplorerLayout.resolve(geometry, 500)
        assertEquals(360, layout3.width) // Clamped to max (900 * 0.4)
    }

    @Test
    fun `insufficient height hides sidebar`() {
        val geometry = WorkspaceGeometry(700, 300, false)
        assertFalse(geometry.showExplorerSidebar)

        val explorerLayout = ExplorerLayout.resolve(geometry, null)
        assertEquals(ExplorerMode.OVERLAY, explorerLayout.mode)
    }

    @Test
    fun `IME affects geometry but not category`() {
        val geometry1 = WorkspaceGeometry(650, 800, false)
        val geometry2 = WorkspaceGeometry(650, 400, true) // IME reduces height

        assertEquals(geometry1.widthCategory, geometry2.widthCategory)
        assertTrue(geometry1.showExplorerSidebar)
        assertTrue(geometry2.showExplorerSidebar) // Width still allows
    }

    @Test
    fun `workspace layout policy explorer calculations`() {
        // Narrow: no sidebar
        assertNull(WorkspaceLayoutPolicy.explorerSidebar(500, 600))

        // Medium: sidebar with default
        val width1 = WorkspaceLayoutPolicy.explorerSidebar(650, 400)
        assertNotNull(width1)
        assertEquals(240, width1)

        // Wide: sidebar constrained by max fraction
        val width2 = WorkspaceLayoutPolicy.explorerSidebar(1200, 600)
        assertNotNull(width2)
        assertTrue(width2!! <= 480) // 1200 * 0.4
    }

    @Test
    fun `explorer resize within valid range`() {
        val density = 2.5f

        // Valid resize
        val newWidth = WorkspaceLayoutPolicy.resizeExplorer(240, 50f, 800, density)
        assertNotNull(newWidth)
        assertTrue(newWidth!! in 180..320) // 800 * 0.4 = 320 max

        // Too narrow
        assertNull(WorkspaceLayoutPolicy.resizeExplorer(240, 0f, 400, density))
    }

    @Test
    fun `status bar layout formats correctly`() {
        val layout = StatusBarLayout("C++", 42, 15, "Tab 4", "UTF-8", "就绪")
        val formatted = layout.format()

        assertTrue(formatted.contains("C++"))
        assertTrue(formatted.contains("Ln 42, Col 15"))
        assertTrue(formatted.contains("Tab 4"))
        assertTrue(formatted.contains("UTF-8"))
        assertTrue(formatted.contains("就绪"))
        assertTrue(formatted.contains("·")) // Separator
    }

    @Test
    fun `status bar handles empty fields`() {
        val layout = StatusBarLayout("", 0, 0, "", "", "运行中")
        val formatted = layout.format()

        assertEquals("运行中", formatted)
        assertFalse(formatted.contains("·"))
    }

    @Test
    fun `panel prefers bottom by default`() {
        val geometry = WorkspaceGeometry(900, 600, false)
        assertTrue(geometry.preferBottomPanel)
    }

    @Test
    fun `bottom first panel strategy`() {
        val state = PanelState()

        // Width 700: should use bottom even though > 600
        val layout1 = PanelLayoutPolicy.resolve(state, true, 700, 500, false, false, false, false)
        assertEquals(PanelDock.BOTTOM, layout1.dock)

        // Width 900 with preferSide: should use side
        val layout2 = PanelLayoutPolicy.resolve(state, true, 900, 500, false, false, false, true)
        assertEquals(PanelDock.SIDE, layout2.dock)

        // Width 900 without preferSide: should still use bottom
        val layout3 = PanelLayoutPolicy.resolve(state, true, 900, 500, false, false, false, false)
        assertEquals(PanelDock.BOTTOM, layout3.dock)
    }
}
