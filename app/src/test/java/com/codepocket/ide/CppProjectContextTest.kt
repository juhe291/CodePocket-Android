package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class CppProjectContextTest {
    @get:Rule val temp = TemporaryFolder()
    private fun workspace() = Workspace(temp.newFolder()) { a,b -> Files.move(a.toPath(), b.toPath(), REPLACE_EXISTING); Unit }
    private fun drafts() = DraftStore(temp.newFolder()) { a,b -> Files.move(a.toPath(), b.toPath(), REPLACE_EXISTING); Unit }
    @Test fun activeTypingUsesStableUriAndNeverWritesItsSourceSnapshotOrOriginal() {
        val ws = workspace(); val store = drafts(); val context = CppProjectContext(temp.newFolder())
        val original = ws.read(ws.resolve("main.cpp"))
        val first = context.capture(ws, store, emptyMap(), "main.cpp", "int current=1;")
        val second = context.capture(ws, store, emptyMap(), "main.cpp", "int current=2;")
        assertEquals(first.location, second.location); assertTrue(first.isCurrent() && second.isCurrent())
        assertEquals("", File(first.location.root, "main.cpp").readText()); assertEquals(original, ws.read(ws.resolve("main.cpp")))
        assertFalse(File(first.location.root, "main.py").exists())
    }
    @Test fun unsavedHeaderVersionsAndTheirRemovalChangeGenerationWithoutOverwritingFiles() {
        val ws = workspace(); val header = ws.create(ws.root, "model.hpp", false); ws.write(header, "struct Token { void old_disk(); };")
        val sessions = EditorSessions("cpp"); val key = sessions.key("model.hpp"); val store = drafts(); val context = CppProjectContext(temp.newFolder())
        store.put(key, "model.hpp", "struct Token { void run(); };")
        val first = context.capture(ws, store, sessions.analysisKeys(), "main.cpp", "code")
        assertTrue(File(first.location.root, "model.hpp").readText().contains("run"))
        store.put(key, "model.hpp", "struct Token { void save(); };")
        assertFalse(first.isCurrent())
        val second = context.capture(ws, store, sessions.analysisKeys(), "main.cpp", "code"); assertNotEquals(first.location.root, second.location.root)
        store.removeIfMatches(key, "struct Token { void save(); };"); assertFalse(second.isCurrent())
        val third = context.capture(ws, store, sessions.analysisKeys(), "main.cpp", "code")
        assertNotEquals(second.location.root, third.location.root); assertTrue(File(third.location.root, "model.hpp").readText().contains("old_disk"))
        assertEquals("struct Token { void old_disk(); };", ws.read(header))
    }
    @Test fun additionsDeletionAndUnsafeConfigurationCannotBecomeStaleContext() {
        val ws = workspace(); val context = CppProjectContext(temp.newFolder()); val store = drafts()
        val config = ws.create(ws.root, ".clangd", false); ws.write(config, "CompileFlags: {Add: [-Xclang, -load, plugin]}")
        val first = context.capture(ws, store, emptyMap(), "main.cpp", "code")
        assertFalse(File(first.location.root, ".clangd").exists())
        val header = ws.create(ws.root, "new.h", false); ws.write(header, "int added;"); assertFalse(first.isCurrent())
        val second = context.capture(ws, store, emptyMap(), "main.cpp", "code")
        ws.delete(header); assertFalse(second.isCurrent())
    }
    @Test fun snapshotCacheRemainsBoundedAcrossHeaderVersionsAndRejectsTampering() {
        val ws = workspace(); val header = ws.create(ws.root, "model.hpp", false); val dir = temp.newFolder(); val context = CppProjectContext(dir); val store = drafts()
        repeat(6) { ws.write(header, "int version_$it;"); context.capture(ws, store, emptyMap(), "main.cpp", "code") }
        assertEquals(3, dir.listFiles()!!.size)
        val current = context.capture(ws, store, emptyMap(), "main.cpp", "code")
        File(current.location.root, "model.hpp").writeText("tampered"); assertFalse(current.isCurrent())
    }
}
