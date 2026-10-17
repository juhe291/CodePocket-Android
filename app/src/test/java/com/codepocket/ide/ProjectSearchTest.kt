package com.codepocket.ide

import com.codepocket.ide.search.ProjectSearch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ProjectSearchTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun workspace() = Workspace(temporary.newFolder()) { a, b ->
        Files.move(a.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    @Test fun nestedFilesAndCrLfHaveRealSourcePositions() {
        val ws = workspace(); val dir = ws.create(ws.root, "src", true)
        val file = ws.create(dir, "test.py", false); ws.write(file, "zero\r\nNeedle 中文\r\nlast")
        val result = ProjectSearch(ws).search("needle")
        assertEquals(1, result.size); assertEquals("src/test.py", result[0].path)
        assertEquals(2, result[0].lineNumber); assertEquals("Needle 中文", result[0].lineContent)
    }
    @Test fun unsavedCurrentBufferIsSearchedWithoutWritingToDisk() {
        val ws = workspace(); val path = "main.py"; val disk = ws.read(ws.resolve(path))
        val hits = ProjectSearch(ws).search("unsaved_marker", buffers = mapOf(path to "unsaved_marker"))
        assertEquals(path, hits.single().path); assertEquals(disk, ws.read(ws.resolve(path)))
    }
    @Test fun resultLimitStopsInsideAFileAndZeroDoesNotSearch() {
        val ws = workspace(); ws.write(ws.resolve("main.py"), "match\n".repeat(1000))
        val search = ProjectSearch(ws)
        assertEquals(7, search.search("match", maxResults = 7).size); assertTrue(search.limited)
        assertTrue(ProjectSearch(ws).search("match", maxResults = 0).isEmpty())
    }
    @Test fun failedReadIsCountedWhileOtherResultsSurvive() {
        val ws = workspace(); ws.resolve("main.cpp").writeText("\u0000invalid")
        ws.write(ws.resolve("main.py"), "valid_marker")
        val search = ProjectSearch(ws)
        assertEquals(1, search.search("valid_marker").size); assertEquals(1, search.skippedFiles)
    }
    @Test fun cancellationBeforeQueuedWorkIsNotReset() {
        val ws = workspace(); val search = ProjectSearch(ws); search.cancel()
        assertTrue(search.search("print").isEmpty())
    }
    @Test fun caseSensitivityAndFileFilteringAreExplicit() {
        val ws = workspace(); ws.write(ws.resolve("main.py"), "UniqueMarker")
        ws.write(ws.create(ws.root, "ignored.bin", false), "uniquemarker")
        assertTrue(ProjectSearch(ws).search("uniquemarker", caseSensitive = true).isEmpty())
        assertEquals(1, ProjectSearch(ws).search("uniquemarker").size)
        assertTrue(ProjectSearch(ws).search("").isEmpty())
    }
}
