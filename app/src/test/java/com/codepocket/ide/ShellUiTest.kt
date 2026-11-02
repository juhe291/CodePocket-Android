package com.codepocket.ide

import com.codepocket.ide.ui.*
import org.junit.Assert.*
import org.junit.Test

class ShellUiTest {
    private val editing = CommandContext(false, true, true, false, false, false, false)

    @Test fun shellTextColorsMeetNormalTextContrastInBothThemes() {
        fun luminance(color: Int): Double {
            val channels = listOf(16, 8, 0).map { shift ->
                val c = ((color ushr shift) and 255) / 255.0
                if (c <= .04045) c / 12.92 else Math.pow((c + .055) / 1.055, 2.4)
            }
            return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722
        }
        for (dark in listOf(true, false)) {
            val palette = DesignTokens.palette(dark)
            for (background in listOf(palette.editor, palette.surface, palette.panel)) {
                for (foreground in listOf(palette.foreground, palette.muted, palette.accentText, palette.success, palette.error)) {
                    val a = luminance(background); val b = luminance(foreground)
                    assertTrue("contrast $dark $foreground/$background", (maxOf(a, b) + .05) / (minOf(a, b) + .05) >= 4.5)
                }
            }
        }
    }

    @Test fun everyNarrowToolbarHasAReachableMenuWithinBudget() {
        for (width in 0..1000) {
            val spec = WorkspaceLayoutPolicy.toolbar(width)
            assertTrue("overflow at $width", spec.controlsWidth <= width)
            assertEquals(minOf(width, 48), spec.control)
            if (spec.title) assertTrue(width - spec.controlsWidth >= 64)
        }
    }

    @Test fun normalPhoneHasTouchSizedExplorerRunMenuAndElasticSingleLineTitleBudget() {
        for (width in listOf(240, 320, 360, 393, 600, 840, 1200)) {
            val spec = WorkspaceLayoutPolicy.toolbar(width)
            assertTrue(spec.explorer); assertTrue(spec.run); assertTrue(spec.terminal); assertEquals(width >= 256, spec.title)
            assertEquals(48, spec.control); assertEquals(if (width >= 408) 240 else 192, spec.controlsWidth)
        }
    }

    @Test fun resizeDegradesControlsToMenuWithoutDependingOnOrientationOrFontWidth() {
        val mini = WorkspaceLayoutPolicy.toolbar(80)
        assertFalse(mini.run); assertFalse(mini.explorer)
        val narrow = WorkspaceLayoutPolicy.toolbar(180)
        assertTrue(narrow.run); assertTrue(narrow.terminal); assertFalse(narrow.explorer); assertFalse(narrow.title)
        // Commands are now organized by context rather than all in one menu
        val fallback = IdeCommands.globalMenu(mini, false)
        assertTrue(IdeCommand.RUN_STOP in fallback)
        assertTrue(IdeCommand.EXPLORER in fallback)
        assertTrue(IdeCommand.SEARCH in fallback)
        assertTrue(IdeCommand.PANEL in fallback)
    }

    @Test fun panelToolbarNeverOverflowsAndKeepsHiddenActionsInItsMenu() {
        for (width in 0..1000) {
            val spec = WorkspaceLayoutPolicy.panelToolbar(width)
            val controls = 1 + (if (spec.preset) 1 else 0) + (if (spec.maximize) 1 else 0) + (if (spec.close) 1 else 0) + (if (spec.clear) 1 else 0)
            assertTrue(controls * minOf(width, 48) <= width)
        }
        assertFalse(WorkspaceLayoutPolicy.panelToolbar(140).preset)
        assertTrue(WorkspaceLayoutPolicy.panelToolbar(240).maximize)
    }

    @Test fun allLegacyActionsAndNewCommandsHaveRealTouchSurfaces() {
        // Validate actual collections used by native entry points, not just enum membership.
        val legacy = listOf(IdeCommand.SETTINGS, IdeCommand.FIND, IdeCommand.REPLACE, IdeCommand.GOTO_LINE,
            IdeCommand.THEME, IdeCommand.HELP, IdeCommand.NEW_FILE, IdeCommand.NEW_FOLDER, IdeCommand.WORKSPACE,
            IdeCommand.IMPORT_COPY, IdeCommand.EXPORT, IdeCommand.RELOAD, IdeCommand.RECOVER, IdeCommand.SAVE,
            IdeCommand.TAB, IdeCommand.UNINDENT, IdeCommand.BRACES, IdeCommand.PARENS, IdeCommand.COMPLETE,
            IdeCommand.SIGNATURE, IdeCommand.SNIPPET)
        val surfaces = listOf(IdeCommands.settingsMenuItems, IdeCommands.editorToolbarItems, IdeCommands.editorMenuItems,
            IdeCommands.explorerToolbarItems, IdeCommands.explorerMenuItems, IdeCommands.terminalToolbarItems,
            IdeCommands.terminalMenuItems, IdeCommands.symbolBarItems, IdeCommands.runMenuItems, IdeCommands.fileMenuItems, IdeCommands.editMenuItems,
            IdeCommands.viewMenuItems,IdeCommands.globalMenu(WorkspaceLayoutPolicy.toolbar(0), false))
        assertEquals(IdeCommand.entries.toSet(), surfaces.flatten().toSet())
        assertTrue(legacy.all { it in surfaces.flatten() })
        for (surface in surfaces) assertEquals(surface.size, surface.distinct().size)
        assertTrue(IdeCommand.IMPORT_COPY.title.contains("副本"))
    }

    @Test fun busyWorkspaceBlocksCommandsButCannotBlockStoppingARun() {
        val busy = editing.copy(busy = true)
        assertTrue(IdeCommand.entries.none { IdeCommands.enabled(it, busy) })
        val runningBusy = busy.copy(running = true)
        assertTrue(IdeCommands.enabled(IdeCommand.RUN_STOP, runningBusy))
        assertFalse(IdeCommands.enabled(IdeCommand.SAVE, runningBusy))
    }

    @Test fun unloadedBufferCannotRunExportOrMutateFiles() {
        val unloaded = editing.copy(loaded = false)
        val allowed = setOf(IdeCommand.THEME, IdeCommand.SETTINGS, IdeCommand.SHORTCUTS, IdeCommand.HELP,
            IdeCommand.NEW_FILE, IdeCommand.NEW_FOLDER, IdeCommand.IMPORT_COPY, IdeCommand.WORKSPACE,
            IdeCommand.CLOSE_WORKSPACE, IdeCommand.EXPLORER, IdeCommand.SEARCH, IdeCommand.SOURCE_CONTROL, IdeCommand.RECOVER,
            IdeCommand.PANEL, IdeCommand.PANEL_SIZE, IdeCommand.MAXIMIZE_TERMINAL, IdeCommand.CLOSE_TERMINAL,
            IdeCommand.CLEAR_TERMINAL, IdeCommand.HIDE_KEYBOARD)
        assertEquals(allowed, IdeCommand.entries.filter { IdeCommands.enabled(it, unloaded) }.toSet())
        assertFalse(IdeCommands.enabled(IdeCommand.CLOSE_WORKSPACE, unloaded.copy(projectOpen=false)))
        assertTrue(listOf(IdeCommand.RUN_STOP,IdeCommand.SAVE,IdeCommand.EXPORT,IdeCommand.RENAME,IdeCommand.DELETE)
            .none{IdeCommands.enabled(it,unloaded)})
    }

    @Test fun editorOnlyCommandsCannotStealStdinFocusOrModifySource() {
        val stdin = editing.copy(editorFocused = false, inputFocused = true)
        assertTrue(IdeCommand.entries.filter { it.editorOnly }.none { IdeCommands.enabled(it, stdin) })
        assertTrue(IdeCommands.enabled(IdeCommand.UNDO, stdin))
        assertTrue(IdeCommands.enabled(IdeCommand.SAVE, stdin))
    }

    @Test fun compositionBlocksEditingCommandsAndNativeHistory() {
        val composing = editing.copy(composing = true)
        assertTrue(IdeCommand.entries.filter { it.editorOnly }.none { IdeCommands.enabled(it, composing) })
        assertFalse(IdeCommands.enabled(IdeCommand.UNDO, composing))
        assertFalse(IdeCommands.enabled(IdeCommand.REDO, composing))
    }

    @Test fun savingOrRunningKeepsDestructiveWorkspaceSwitchesOutOfMenu() {
        for (state in listOf(editing.copy(saving = true), editing.copy(running = true))) {
            for (command in listOf(IdeCommand.NEW_FILE, IdeCommand.NEW_FOLDER, IdeCommand.WORKSPACE, IdeCommand.IMPORT_COPY, IdeCommand.RELOAD))
                assertFalse(command.name, IdeCommands.enabled(command, state))
            assertTrue(IdeCommands.enabled(IdeCommand.EXPORT, state))
        }
        assertFalse(IdeCommands.enabled(IdeCommand.RUN_STOP, editing.copy(saving = true)))
    }

    @Test fun presetIsCollapsedByDefaultAndEditorFocusDoesNotReopenIt() {
        assertFalse(WorkspaceLayoutPolicy.restorePreset(false, "editor", true))
        val layout = WorkspaceLayoutPolicy.preset(false, false, false, 300)
        assertFalse(layout.showInput); assertFalse(layout.showCaption); assertTrue(layout.showTabs)
        assertTrue(WorkspaceLayoutPolicy.preset(false, false, false, 180).showTabs)
    }

    @Test fun savedStdinFocusRevealsItsInputOnlyWhenPanelIsOpen() {
        assertTrue(WorkspaceLayoutPolicy.restorePreset(false, "stdin", true))
        assertFalse(WorkspaceLayoutPolicy.restorePreset(false, "stdin", false))
        assertTrue(WorkspaceLayoutPolicy.restorePreset(true, "editor", false))
    }

    @Test fun activeStdinGetsCompactInputWithoutConsumingHeaderAndOutputTwice() {
        val compact = WorkspaceLayoutPolicy.preset(true, true, true, 180)
        assertTrue(compact.showInput); assertFalse(compact.showCaption); assertFalse(compact.showTabs)
        assertEquals(48, compact.inputHeight)
        val roomy = WorkspaceLayoutPolicy.preset(true, false, false, 320)
        assertTrue(roomy.showCaption); assertTrue(roomy.showTabs); assertEquals(64, roomy.inputHeight)
    }

    @Test fun snippetHintOccupiesSpaceOnlyForAnActiveSessionWithRoom() {
        assertFalse(WorkspaceLayoutPolicy.showSnippet(false, false, 800))
        assertFalse(WorkspaceLayoutPolicy.showSnippet(true, true, 800))
        assertFalse(WorkspaceLayoutPolicy.showSnippet(true, false, 300))
        assertTrue(WorkspaceLayoutPolicy.showSnippet(true, false, 600))
    }
}
