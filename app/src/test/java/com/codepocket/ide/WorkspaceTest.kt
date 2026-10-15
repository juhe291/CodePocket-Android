package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.StringReader

class WorkspaceTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun workspace() = Workspace(temporary.newFolder("workspace")) { source, target ->
        java.nio.file.Files.move(source.toPath(), target.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    @Test fun nestedCreateRenameMoveAndReadWrite() {
        val ws = workspace()
        val a = ws.create(ws.root, "algorithms", true)
        val b = ws.create(a, "nested", true)
        val source = ws.create(b, "sum.py", false)
        ws.write(source, "print('你好')")
        ws.write(source, "print(42)")
        val renamed = ws.rename(source, "answer.py")
        val moved = ws.move(renamed, a)
        assertEquals("algorithms/answer.py", ws.path(moved))
        assertEquals("print(42)", ws.read(moved))
        assertFalse(source.exists())
        assertTrue(ws.children(a).first().isDirectory)
        ws.delete(a)
        assertFalse(a.exists())
    }

    @Test fun rejectsTraversalAbsoluteAndSiblingPaths() {
        val ws = workspace()
        for (path in listOf("../escape.py", "a/../../escape.py", "/tmp/x", "a/../main.py")) {
            assertThrows(IllegalArgumentException::class.java) { ws.resolve(path) }
        }
        assertThrows(IllegalArgumentException::class.java) { ws.read(File(ws.root.parentFile, "workspace-sibling/x")) }
        assertThrows(IllegalArgumentException::class.java) { ws.delete(ws.root) }
    }

    @Test fun rejectsBadNamesConflictsAndRecursiveMove() {
        val ws = workspace()
        for (name in listOf("", "..", " a", "a/b", "a\\b", "a\n")) {
            assertThrows(IllegalArgumentException::class.java) { ws.create(ws.root, name, false) }
        }
        val folder = ws.create(ws.root, "src", true)
        val child = ws.create(folder, "nested", true)
        assertThrows(IllegalArgumentException::class.java) { ws.move(folder, child) }
        assertThrows(IllegalArgumentException::class.java) { ws.create(ws.root, "src", false) }
        assertThrows(IllegalArgumentException::class.java) { ws.rename(ws.root, "other") }
    }

    @Test fun oversizedReadAndWritePreserveOriginal() {
        val ws = workspace()
        val source = ws.resolve("main.py")
        val original = ws.read(source)
        assertThrows(IllegalArgumentException::class.java) { ws.write(source, "x".repeat(Workspace.MAX_CHARS + 1)) }
        assertEquals(original, ws.read(source))
        source.writeText("x".repeat(Workspace.MAX_CHARS + 1))
        assertThrows(IllegalArgumentException::class.java) { ws.read(source) }
        assertThrows(IllegalArgumentException::class.java) { Workspace.readLimited(StringReader("abcd"), 3) }
        assertEquals("abc", Workspace.readLimited(StringReader("abc"), 3))
    }

    @Test fun rejectsLinksDuringRecursiveDeletion() {
        // Creating symlinks on Windows may require privileges; CI/Linux exercises this case.
        org.junit.Assume.assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val ws = workspace()
        val folder = ws.create(ws.root, "folder", true)
        val outside = temporary.newFolder("outside")
        val valuable = File(outside, "valuable.txt").apply { writeText("keep") }
        java.nio.file.Files.createSymbolicLink(File(folder, "link").toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) { ws.delete(folder) }
        assertEquals("keep", valuable.readText())
        assertTrue(ws.children(folder).isEmpty())
    }

    @Test fun failedReplacementKeepsOldContentAndCleansTemporary() {
        val ws = Workspace(temporary.newFolder("failure")) { _, _ -> throw java.io.IOException("simulated rename failure") }
        val original = ws.read(ws.resolve("main.py"))
        assertThrows(java.io.IOException::class.java) { ws.write(ws.resolve("main.py"), "replacement") }
        assertEquals(original, ws.read(ws.resolve("main.py")))
        assertFalse(ws.children(ws.root).any { it.name.startsWith(".codepocket-save-") })
    }

    @Test fun invalidUtf8AndBinaryAreRejectedAndBomIsAccepted() {
        val ws = workspace()
        val file = ws.resolve("main.py")
        file.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        assertThrows(java.nio.charset.MalformedInputException::class.java) { ws.read(file) }
        file.writeText("a\u0000b")
        assertThrows(IllegalArgumentException::class.java) { ws.read(file) }
        file.writeText("\uFEFFprint(42)")
        assertEquals("print(42)", ws.read(file))
    }
}
