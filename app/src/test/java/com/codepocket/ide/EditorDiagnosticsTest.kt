package com.codepocket.ide

import com.codepocket.ide.editor.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EditorDiagnosticsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun frame(text: String = "print(1)\nraise ValueError()") =
        EditorFrame(EditorSnapshot("workspace:file", 8, text, 0, 0), false, true)
    private fun diagnostic(source: EditorFrame, line: Int = 2) =
        EditorDiagnostic(source.buffer, line, source.buffer.key, DocumentTree.digest(source.buffer.text))

    @Test fun matchingRunSourceFindsTheActualErrorLine() {
        val source = frame()
        val target = EditorDiagnostics.resolve(source, diagnostic(source))
        assertEquals(DiagnosticStatus.LOCATED, target.status)
        val selection = target.selection!!
        assertEquals(9, selection.start)
        assertTrue(selection.focus)
        assertTrue(EditorNavigation.canSelect(source, selection))
    }

    @Test fun editingAfterRunningRejectsTheOldLocationEvenAtTheSameLineCount() {
        val original = frame()
        val current = frame("print(2)\nraise ValueError()")
        val request = diagnostic(original).copy(source = current.buffer)
        val target = EditorDiagnostics.resolve(current, request)
        assertEquals(DiagnosticStatus.CHANGED_SOURCE, target.status)
        assertNull(target.selection)
    }

    @Test fun replacingAFileWithTheSameContentStillRejectsTheOldFileIdentity() {
        val original = frame()
        val current = original.copy(buffer = original.buffer.copy(key = "workspace:new-file"))
        val target = EditorDiagnostics.resolve(current, diagnostic(original).copy(source = current.buffer))
        assertEquals(DiagnosticStatus.CHANGED_SOURCE, target.status); assertNull(target.selection)
    }

    @Test fun missingImportedFileProofIsClearlyLineOnly() {
        val source = frame()
        val result = EditorDiagnostics.resolve(source, EditorDiagnostic(source.buffer, 2))
        assertEquals(DiagnosticStatus.LINE_ONLY, result.status)
        assertTrue(result.status.located); assertNotNull(result.selection)
    }

    @Test fun partialProofNeverClaimsAVerifiedRunVersion() {
        val source = frame()
        for (request in listOf(diagnostic(source).copy(originKey = null), diagnostic(source).copy(originHash = null)))
            assertEquals(DiagnosticStatus.LINE_ONLY, EditorDiagnostics.resolve(source, request).status)
    }

    @Test fun outOfRangeErrorsNeverFallBackToTheLastLine() {
        val source = frame()
        for (line in listOf(-1, 0, 3, Int.MAX_VALUE)) {
            val target = EditorDiagnostics.resolve(source, diagnostic(source, line))
            assertEquals(DiagnosticStatus.INVALID_LINE, target.status); assertNull(target.selection)
        }
    }

    @Test fun aTrailingNewlineHasARealEmptyFinalLine() {
        val source = frame("x\n")
        val target = EditorDiagnostics.resolve(source, diagnostic(source, 2))
        assertEquals(DiagnosticStatus.LOCATED, target.status); assertEquals(2, target.selection!!.start)
        val empty = frame("")
        assertEquals(0, EditorDiagnostics.resolve(empty, diagnostic(empty, 1)).selection!!.start)
    }

    @Test fun unicodeAndCrlfDiagnosticOffsetsRemainUtf16() {
        val source = frame("# 😀\r\nraise ValueError()")
        val target = EditorDiagnostics.resolve(source, diagnostic(source))
        val selection = target.selection!!
        assertEquals(6, selection.start)
        assertTrue(EditorNavigation.canSelect(source, selection))
    }

    @Test fun compositionUnavailableAndChangedSelectionDoNotMoveTheCaret() {
        val source = frame()
        val request = diagnostic(source)
        assertEquals(DiagnosticStatus.NO_DOCUMENT, EditorDiagnostics.resolve(null, request).status)
        assertEquals(DiagnosticStatus.COMPOSING, EditorDiagnostics.resolve(source.copy(composing = true), request).status)
        assertEquals(DiagnosticStatus.STALE, EditorDiagnostics.resolve(source.copy(buffer = source.buffer.copy(start = 1)), request).status)
        assertEquals(DiagnosticStatus.STALE, EditorDiagnostics.resolve(source.copy(buffer = source.buffer.copy(revision = 9)), request).status)
    }

    private fun workspace(): Workspace = Workspace(temporary.newFolder("workspace")) { from, to ->
        java.nio.file.Files.move(from.toPath(), to.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    @Test fun realInternalPathsResolveThroughTheWorkspaceBoundary() {
        val workspace = workspace()
        val source = workspace.create(workspace.root, "error.py", false)
        workspace.write(source, "raise ValueError()")
        assertEquals("error.py", DiagnosticFiles.relative(workspace, source.absolutePath, workspace.root.absolutePath))
    }

    @Test fun safExecutionSnapshotPathsMapBackToNestedWorkspaceFiles() {
        val workspace = workspace()
        val folder = workspace.create(workspace.root, "src", true)
        val source = workspace.create(folder, "error.py", false)
        workspace.write(source, "raise ValueError()")
        val runRoot = temporary.newFolder("run-snapshot")
        val executed = File(runRoot, "src/error.py").apply { parentFile.mkdirs(); writeText("raise ValueError()") }
        assertEquals("src/error.py", DiagnosticFiles.relative(workspace, executed.absolutePath, runRoot.absolutePath))
    }

    @Test fun neighboringRootTraversalAndUnmappedFilesAreRejected() {
        val workspace = workspace()
        val runRoot = temporary.newFolder("run")
        val neighbor = temporary.newFolder("run-other")
        val source = File(neighbor, "main.py").apply { writeText("x") }
        assertNull(DiagnosticFiles.relative(workspace, source.absolutePath, runRoot.absolutePath))
        assertNull(DiagnosticFiles.relative(workspace, File(runRoot, "../run-other/main.py").path, runRoot.absolutePath))
        assertNull(DiagnosticFiles.relative(workspace, File(runRoot, "missing.py").path, runRoot.absolutePath))
        assertNull(DiagnosticFiles.relative(workspace, runRoot.path, runRoot.absolutePath))
    }
}
