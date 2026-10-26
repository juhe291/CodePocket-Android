package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class PythonProjectContextTest {
    @get:Rule val temp = TemporaryFolder()
    private fun workspace() = Workspace(temp.newFolder()) { a, b -> Files.move(a.toPath(), b.toPath(), REPLACE_EXISTING); Unit }
    private fun drafts() = DraftStore(temp.newFolder()) { a, b -> Files.move(a.toPath(), b.toPath(), REPLACE_EXISTING); Unit }
    private fun add(ws: Workspace, path: String, text: String): File = ws.create(ws.root, path, false).also { ws.write(it, text) }
    private class Provider : DocumentGateway {
        override val root = DocumentNode("opaque:root", "project", true)
        val nodes = linkedMapOf(root.id to root)
        val parents = mutableMapOf<String, String>()
        val text = mutableMapOf<String, String>()
        var revoked = false
        var writes = 0
        var sequence = 0
        override fun children(id: String): List<DocumentNode> { if (revoked) throw SecurityException(); return nodes.values.filter { parents[it.id] == id } }
        override fun read(id: String): String { if (revoked) throw SecurityException(); return text[id] ?: throw IOException("removed") }
        override fun write(id: String, text: String) { writes++; this.text[id] = text }
        override fun create(parent: String, name: String, directory: Boolean): DocumentNode = DocumentNode("opaque:${++sequence}", name, directory).also { nodes[it.id] = it; parents[it.id] = parent; if (!directory) text[it.id] = "" }
        override fun rename(id: String, name: String): DocumentNode = nodes.getValue(id).copy(name = name).also { nodes[id] = it }
        override fun move(id: String, oldParent: String, newParent: String): DocumentNode = nodes.getValue(id).also { parents[id] = newParent }
        override fun delete(id: String) { nodes.remove(id); parents.remove(id); text.remove(id) }
    }
    @Test fun localReadIncludesPackagesAndStubsButNoNonSourceOrVcsData() {
        val ws = workspace(); val pkg = ws.create(ws.root, "pkg", true)
        ws.write(ws.create(pkg, "__init__.py", false), "from .model import Token\n")
        ws.write(ws.create(pkg, "model.pyi", false), "class Token: ...\n")
        add(ws, "notes.txt", "not Python")
        val vcs = ws.create(ws.root, ".git", true); ws.write(ws.create(vcs, "secret.py", false), "private")
        val map = ws.pythonSources()
        assertEquals(setOf("main.py", "pkg/__init__.py", "pkg/model.pyi"), map.keys)
        assertEquals("class Token: ...\n", map["pkg/model.pyi"])
    }
    @Test fun liveProviderReadsAdditionsRemovalAndReplacedOpaqueIdsWithoutChangingMirrorOrSaveBase() {
        val p = Provider(); val old = p.create(p.root.id, "model.py", false); p.text[old.id] = "value=1"
        val tree = DocumentTree(p, temp.newFolder()); val mirror = temp.newFolder(); tree.scan(mirror)
        p.delete(old.id); val replacement = p.create(p.root.id, "model.py", false); p.text[replacement.id] = "value='new'"
        val fresh = p.create(p.root.id, "new.py", false); p.text[fresh.id] = "new=1"
        assertEquals(mapOf("model.py" to "value='new'", "new.py" to "new=1"), tree.pythonSources())
        assertEquals("value=1", File(mirror, "model.py").readText()); assertFalse(File(mirror, "new.py").exists())
        assertEquals(old.id, tree.pathId("model.py")); assertEquals(0, p.writes)
        // The ordinary save still observes its original identity/base; analysis never refreshes it.
        assertThrows(IOException::class.java) { tree.write("model.py", "must not write") }
    }
    @Test fun liveAnalysisCannotResetTheExpectedProviderConflictBase() {
        val p = Provider(); val f = p.create(p.root.id, "model.py", false); p.text[f.id] = "old"
        val tree = DocumentTree(p, temp.newFolder()); tree.scan(temp.newFolder()); p.text[f.id] = "external change"
        assertEquals("external change", tree.pythonSources()["model.py"])
        assertThrows(IllegalArgumentException::class.java) { tree.write("model.py", "mine") }; assertEquals(0, p.writes)
        p.revoked = true; assertThrows(SecurityException::class.java) { tree.pythonSources() }
    }
    @Test fun invalidProviderPathsDuplicateNamesAndCyclesFailClosed() {
        for (name in listOf("../escape.py", "bad/name.py", "..")) {
            val p = Provider(); p.create(p.root.id, name, false)
            assertThrows(IllegalArgumentException::class.java) { PythonSources.provider(p) }
        }
        val duplicates = Provider(); repeat(2) { duplicates.create(duplicates.root.id, "same.py", false) }
        assertThrows(IllegalArgumentException::class.java) { PythonSources.provider(duplicates) }
        val cycle = Provider(); cycle.parents[cycle.root.id] = cycle.root.id
        assertThrows(IllegalArgumentException::class.java) { PythonSources.provider(cycle) }
    }
    @Test fun sourceFileAndAggregateLimitsDoNotReturnPartialProjects() {
        val p = Provider(); repeat(PythonSources.MAX_FILES + 1) { p.create(p.root.id, "m$it.py", false) }
        assertThrows(IllegalArgumentException::class.java) { PythonSources.provider(p) }
        val total = Provider(); repeat(6) { total.create(total.root.id, "m$it.py", false).also { n -> total.text[n.id] = "x".repeat(200000) } }
        assertThrows(IllegalArgumentException::class.java) { PythonSources.provider(total) }
        val huge = Provider(); huge.create(huge.root.id, "large.py", false).also { huge.text[it.id] = "x".repeat(200001) }
        assertThrows(IllegalArgumentException::class.java) { PythonSources.provider(huge) }
    }
    @Test fun snapshotsOverlaySeveralUnsavedFilesAndNeverWriteOriginalsOrDrafts() {
        val ws = workspace(); val saved = ws.read(ws.resolve("main.py"))
        add(ws, "model.py", "value=1"); add(ws, "api.py", "value=2")
        val sessions = EditorSessions("project"); val store = drafts()
        store.put(sessions.key("model.py"), "model.py", "value='😀中文'", "old")
        store.put(sessions.key("api.py"), "api.py", "from model import value", "old")
        store.put("foreign:id", "model.py", "wrong")
        store.put(sessions.key("model.py") + ":backup:1", "model.py", "backup")
        val context = PythonProjectContext(temp.newFolder()); val snap = context.capture(ws, store, sessions.analysisKeys(), "main.py", "from api import value\nvalue.up")
        val root = File(snap.location.root)
        assertNotEquals(ws.root, root); assertTrue(snap.location.project); assertTrue(snap.isCurrent())
        assertEquals("value='😀中文'", File(root, "model.py").readText()); assertEquals("from model import value", File(root, "api.py").readText())
        assertEquals("from api import value\nvalue.up", File(root, "main.py").readText())
        assertEquals(saved, ws.read(ws.resolve("main.py"))); assertEquals("value=1", ws.read(ws.resolve("model.py")))
        assertEquals(4, store.list("").size)
    }
    @Test fun deletedFilesAreNotResurrectedByOldSessionDrafts() {
        val ws = workspace(); val sessions = EditorSessions("project"); val store = drafts()
        store.put(sessions.key("deleted.py"), "deleted.py", "old=1")
        val snap = PythonProjectContext(temp.newFolder()).capture(ws, store, sessions.analysisKeys(), "main.py", "x=1")
        assertFalse(File(snap.location.root, "deleted.py").exists()); assertTrue(snap.isCurrent())
    }
    @Test fun changesToDiskOrOtherDraftInvalidateAnAlreadyAnalyzedSnapshot() {
        val ws = workspace(); val file = add(ws, "model.py", "a=1"); val store = drafts(); val sessions = EditorSessions("p"); val key = sessions.key("model.py")
        val context = PythonProjectContext(temp.newFolder()); val first = context.capture(ws, store, sessions.analysisKeys(), "main.py", "x=1")
        ws.write(file, "a=2"); assertFalse(first.isCurrent())
        val second = context.capture(ws, store, sessions.analysisKeys(), "main.py", "x=1")
        store.put(key, "model.py", "a='unsaved'"); assertFalse(second.isCurrent())
    }
    @Test fun identicalContentReusesCacheAndChangedVersionsAreBoundedAndImmutable() {
        val ws = workspace(); val store = drafts(); val dir = temp.newFolder(); val context = PythonProjectContext(dir)
        val first = context.capture(ws, store, emptyMap(), "main.py", "x=1")
        val same = context.capture(ws, store, emptyMap(), "main.py", "x=1"); assertEquals(first.location, same.location)
        val original = File(first.location.root, "main.py"); assertEquals("x=1", original.readText())
        context.capture(ws, store, emptyMap(), "main.py", "x=2"); assertEquals("x=1", original.readText())
        repeat(6) { context.capture(ws, store, emptyMap(), "main.py", "x=$it") }
        assertEquals(3, dir.listFiles()!!.size); assertTrue(dir.listFiles()!!.all { !it.name.startsWith(".") })
    }
    @Test fun tamperedCacheCannotPublishAndIsRebuiltBeforeReuse() {
        val ws = workspace(); val store = drafts(); val context = PythonProjectContext(temp.newFolder())
        val snap = context.capture(ws, store, emptyMap(), "main.py", "x=1")
        File(snap.location.root, "main.py").writeText("corrupt")
        assertFalse(snap.isCurrent())
        assertEquals("x=1", File(context.capture(ws, store, emptyMap(), "main.py", "x=1").location.root, "main.py").readText())
    }
    @Test fun missingActiveWrongDraftIdentityAndInterruptedScanAreRejected() {
        val ws = workspace(); val store = drafts(); val context = PythonProjectContext(temp.newFolder())
        assertThrows(IllegalArgumentException::class.java) { context.capture(ws, store, emptyMap(), "missing.py", "x=1") }
        add(ws, "model.py", "x=1"); store.put("p:id", "different.py", "wrong")
        assertThrows(IllegalArgumentException::class.java) { context.capture(ws, store, mapOf("model.py" to "p:id"), "main.py", "x=1") }
        Thread.currentThread().interrupt()
        try { assertThrows(InterruptedException::class.java) { ws.pythonSources() } } finally { Thread.interrupted() }
    }
}
