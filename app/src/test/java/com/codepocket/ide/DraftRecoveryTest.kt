package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class DraftRecoveryTest {
    @get:Rule val temp = TemporaryFolder()
    private fun replace(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }
    private fun workspace() = Workspace(temp.newFolder(), ::replace)
    private fun store() = DraftStore(temp.newFolder(), ::replace)

    @Test fun alreadySavedCheckpointDoesNotCauseFalseConflictOnNextEdit() {
        val ws = workspace()
        val file = ws.resolve("main.py")
        val previousHash = DocumentTree.digest(ws.read(file))
        val saved = "print('已保存')\n"
        ws.write(file, saved, previousHash)
        val drafts = store()
        // Rotation may reinsert its Bundle checkpoint after the old Activity's save completes.
        drafts.put("internal:file1", "main.py", saved, previousHash)
        val restored = DraftRecovery.restore(ws.read(file), drafts.read("internal:file1"))
        assertFalse(restored.dirty)
        ws.write(file, "print('继续编辑')\n", restored.baseHash)
        assertEquals("print('继续编辑')\n", ws.read(file))
    }

    @Test fun unwrittenCheckpointStillSavesAgainstItsOriginalVersion() {
        val ws = workspace()
        val file = ws.resolve("main.py")
        val original = ws.read(file)
        val drafts = store()
        drafts.put("internal:file1", "main.py", "print('草稿')\n", DocumentTree.digest(original))
        val restored = DraftRecovery.restore(ws.read(file), drafts.read("internal:file1"))
        assertTrue(restored.dirty)
        ws.write(file, restored.text, restored.baseHash)
        assertEquals("print('草稿')\n", ws.read(file))
    }

    @Test fun realExternalConflictKeepsDraftAndDoesNotOverwriteTheFile() {
        val ws = workspace()
        val file = ws.resolve("main.py")
        val originalHash = DocumentTree.digest(ws.read(file))
        val drafts = store()
        drafts.put("internal:file1", "main.py", "print('未保存编辑')\n", originalHash)
        ws.write(file, "print('外部修改')\n")
        val restored = DraftRecovery.restore(ws.read(file), drafts.read("internal:file1"))
        assertTrue(restored.dirty)
        assertEquals(originalHash, restored.baseHash)
        assertThrows(IllegalArgumentException::class.java) { ws.write(file, restored.text, restored.baseHash) }
        assertEquals("print('外部修改')\n", ws.read(file))
        assertEquals("print('未保存编辑')\n", drafts.read("internal:file1")!!.text)
    }

    @Test fun noCheckpointStartsFromCurrentFileWithoutDirtyState() {
        val ws = workspace()
        val saved = ws.read(ws.resolve("main.py"))
        val restored = DraftRecovery.restore(saved, null)
        assertEquals(saved, restored.text)
        assertEquals(DocumentTree.digest(saved), restored.baseHash)
        assertFalse(restored.dirty)
    }
}
