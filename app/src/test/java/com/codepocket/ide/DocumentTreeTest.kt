package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class DocumentTreeTest {
    @get:Rule val temp = TemporaryFolder()
    private class Provider : DocumentGateway {
        override val root = DocumentNode("root-id", "project", true)
        val nodes = linkedMapOf(root.id to root)
        val parents = mutableMapOf<String, String>()
        val content = mutableMapOf<String, String>()
        var revoked = false
        var readFailure = false
        var partialFailure = false
        var writes = 0
        private fun access() { if (revoked) throw SecurityException("revoked") }
        override fun children(id: String): List<DocumentNode> { access(); return nodes.values.filter { parents[it.id] == id } }
        override fun read(id: String): String { access(); if (readFailure) throw IOException("read disconnected"); return content.getValue(id) }
        override fun write(id: String, text: String) {
            access(); writes++
            if (partialFailure) { content[id] = "partial"; throw IOException("provider disconnected") }
            content[id] = text
        }
        override fun create(parent: String, name: String, directory: Boolean): DocumentNode {
            access(); val id = "opaque-${nodes.size}-${System.nanoTime()}"
            return DocumentNode(id, name, directory).also { nodes[id] = it; parents[id] = parent; if (!directory) content[id] = "" }
        }
        override fun rename(id: String, name: String): DocumentNode { access(); return nodes.getValue(id).copy(name = name).also { nodes[id] = it } }
        override fun move(id: String, oldParent: String, newParent: String): DocumentNode { access(); parents[id] = newParent; return nodes.getValue(id) }
        override fun delete(id: String) { access(); children(id).forEach { delete(it.id) }; nodes.remove(id); parents.remove(id); content.remove(id) }
    }
    @Test fun editsWriteToRealProviderAndNestedCrudKeepsOpaqueIds() {
        val p = Provider()
        val f = p.create(p.root.id, "main.py", false); p.content[f.id] = "print(1)"
        val tree = DocumentTree(p, temp.newFolder("recovery")); tree.scan(temp.newFolder("mirror"))
        assertEquals("print(1)", tree.read("main.py"))
        tree.write("main.py", "print(2)")
        assertEquals("print(2)", p.content[f.id])
        val folder = tree.create("", "src", true)
        tree.relocate("main.py", folder, "main.py", true)
        assertEquals(f.id, tree.pathId("src/main.py"))
        tree.relocate("src", "", "lib", false)
        assertEquals(f.id, tree.pathId("lib/main.py"))
        tree.delete("lib")
        assertFalse(f.id in p.nodes)
        assertThrows(IllegalArgumentException::class.java) { tree.delete("") }
    }
    @Test fun externalChangesAndRestoredDraftBaseCannotBeOverwritten() {
        val p = Provider(); val f = p.create(p.root.id, "a.py", false); p.content[f.id] = "original"
        val tree = DocumentTree(p, temp.newFolder()); tree.scan(temp.newFolder())
        tree.read("a.py"); p.content[f.id] = "external edit"
        assertThrows(IllegalArgumentException::class.java) { tree.write("a.py", "my edit") }
        assertEquals(0, p.writes)
        val reopened = DocumentTree(p, temp.newFolder()); reopened.scan(temp.newFolder()); reopened.read("a.py")
        assertThrows(IllegalArgumentException::class.java) { reopened.write("a.py", "draft", DocumentTree.digest("original")) }
        assertEquals("external edit", p.content[f.id])
    }
    @Test fun partialProviderWritePreservesOriginalAndRevocationDoesNotWrite() {
        val p = Provider(); val f = p.create(p.root.id, "a.py", false); p.content[f.id] = "valuable"
        val recovery = temp.newFolder(); val tree = DocumentTree(p, recovery); tree.scan(temp.newFolder())
        tree.read("a.py"); p.partialFailure = true
        assertThrows(IOException::class.java) { tree.write("a.py", "new") }
        assertEquals("valuable", recovery.listFiles()!!.single { it.name.endsWith(".before.txt") }.readText())
        p.revoked = true
        assertThrows(SecurityException::class.java) { tree.read("a.py") }
        assertEquals(1, p.writes)
    }
    @Test fun traversalNamesCollisionsAndRecursiveMovesAreRejected() {
        val p = Provider(); val tree = DocumentTree(p, temp.newFolder()); tree.scan(temp.newFolder())
        tree.create("", "folder", true); tree.create("folder", "sub", true)
        assertThrows(IllegalArgumentException::class.java) { tree.create("", "../escape", false) }
        assertThrows(IllegalArgumentException::class.java) { tree.create("", "folder", false) }
        assertThrows(IllegalArgumentException::class.java) { tree.relocate("folder", "folder/sub", "folder", true) }
        assertEquals(2, p.parents.size)
    }
    @Test fun pythonModuleReadFailureAbortsInsteadOfRunningAnEmptyPlaceholder() {
        val p = Provider(); p.create(p.root.id, "module.py", false)
        p.readFailure = true
        val tree = DocumentTree(p, temp.newFolder())
        assertThrows(IOException::class.java) { tree.scan(temp.newFolder()) }
    }
    @Test fun cyclicProviderTreeIsRejectedBeforeCreatingAnyChild() {
        val p = Provider(); p.parents[p.root.id] = p.root.id
        val mirror = temp.newFolder(); val tree = DocumentTree(p, temp.newFolder())
        assertThrows(IllegalArgumentException::class.java) { tree.scan(mirror) }
        assertTrue(mirror.listFiles()!!.isEmpty())
    }
    @Test fun runSnapshotContainsActualDataAndBinaryResourcesNotEditorPlaceholders() {
        val p = Provider()
        val source = p.create(p.root.id, "main.py", false); p.content[source.id] = "print(open('data.txt').read())"
        val data = p.create(p.root.id, "data.txt", false); p.content[data.id] = "42 中文"
        val binary = p.create(p.root.id, "data.bin", false); p.content[binary.id] = "\u0000\u0001"
        val mirror = temp.newFolder(); val tree = DocumentTree(p, temp.newFolder()); tree.scan(mirror)
        assertEquals("", java.io.File(mirror, "data.txt").readText())
        val run = java.io.File(temp.root, "run-snapshot"); tree.copyForRun(run)
        assertEquals("42 中文", java.io.File(run, "data.txt").readText())
        assertArrayEquals(byteArrayOf(0, 1), java.io.File(run, "data.bin").readBytes())
        p.content[data.id] = "new external value"
        val next = java.io.File(temp.root, "run-snapshot-next"); tree.copyForRun(next)
        assertEquals("new external value", java.io.File(next, "data.txt").readText())
    }

    @Test fun matchingRecoveryCheckpointUsesCurrentProviderVersion() {
        val p = Provider()
        val file = p.create(p.root.id, "main.py", false)
        p.content[file.id] = "print('already saved')"
        val tree = DocumentTree(p, temp.newFolder()); tree.scan(temp.newFolder())
        val checkpoint = Draft("saf:file1", "main.py", "print('already saved')", DocumentTree.digest("old content"))
        val restored = DraftRecovery.restore(tree.read("main.py"), checkpoint)
        assertFalse(restored.dirty)
        tree.write("main.py", "print('next edit')", restored.baseHash)
        assertEquals("print('next edit')", p.content[file.id])
        assertEquals(1, p.writes)
    }

    @Test fun recoveryDoesNotAdoptAnExternalProvidersVersionForDifferentText() {
        val p = Provider()
        val file = p.create(p.root.id, "main.py", false)
        p.content[file.id] = "external edit"
        val tree = DocumentTree(p, temp.newFolder()); tree.scan(temp.newFolder())
        val checkpoint = Draft("saf:file1", "main.py", "my unsaved edit", DocumentTree.digest("original content"))
        val restored = DraftRecovery.restore(tree.read("main.py"), checkpoint)
        assertTrue(restored.dirty)
        assertThrows(IllegalArgumentException::class.java) { tree.write("main.py", restored.text, restored.baseHash) }
        assertEquals("external edit", p.content[file.id])
        assertEquals(0, p.writes)
    }
}
