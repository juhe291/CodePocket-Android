package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*

class DraftStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun store(directory: File) = DraftStore(directory) { a, b -> Files.move(a.toPath(), b.toPath(), ATOMIC_MOVE, REPLACE_EXISTING); Unit }
    @Test fun diskRecoveryPreservesSourceIdentityUnicodeAndConflictBase() {
        val dir = temp.newFolder(); val store = store(dir)
        store.put("workspace-a:id1", "src/main.py", "print('中文')", "base-hash")
        store.put("workspace-b:id1", "main.py", "other")
        val reopened = store(dir)
        assertEquals(Draft("workspace-a:id1", "src/main.py", "print('中文')", "base-hash"), reopened.read("workspace-a:id1"))
        assertEquals(1, reopened.list("workspace-a:").size)
        assertEquals("other", reopened.read("workspace-b:id1")!!.text)
    }
    @Test fun staleSaveDoesNotDeleteNewerDraftAndReplaceFailureKeepsOldDraft() {
        val dir = temp.newFolder(); val store = store(dir)
        store.put("id", "a.py", "old"); store.put("id", "a.py", "new")
        store.removeIfMatches("id", "old")
        assertEquals("new", store.read("id")!!.text)
        val failed = DraftStore(dir) { _, _ -> throw IOException("disk failed") }
        assertThrows(IOException::class.java) { failed.put("id", "a.py", "lost") }
        assertEquals("new", store.read("id")!!.text)
        assertFalse(dir.listFiles()!!.any { it.extension == "tmp" })
        store.removeIfMatches("id", "new"); assertNull(store.read("id"))
    }
    @Test fun oversizedDraftDoesNotReplaceOriginal() {
        val store = store(temp.newFolder()); store.put("id", "a.py", "keep")
        assertThrows(IllegalArgumentException::class.java) { store.put("id", "a.py", "x".repeat(Workspace.MAX_CHARS + 1)) }
        assertEquals("keep", store.read("id")!!.text)
    }
}
