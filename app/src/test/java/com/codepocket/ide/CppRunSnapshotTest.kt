package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class CppRunSnapshotTest {
    @get:Rule val temp = TemporaryFolder()
    private fun workspace() = Workspace(temp.newFolder()) { a, b -> Files.move(a.toPath(), b.toPath(), REPLACE_EXISTING); Unit }
    private fun drafts() = DraftStore(temp.newFolder()) { a, b -> Files.move(a.toPath(), b.toPath(), REPLACE_EXISTING); Unit }
    @Test fun currentBufferAndHeaderDraftAreActualBuildInputsWithoutWritingOriginals() {
        val ws = workspace(); val store = drafts(); val header = ws.create(ws.root, "model.hpp", false)
        ws.write(header, "int disk;"); val original = ws.read(ws.resolve("main.cpp"))
        val sessions = EditorSessions("run"); val key = sessions.key("model.hpp")
        store.put(key, "model.hpp", "int draft;")
        val snapshot = CppRunSnapshot.capture(temp.newFolder(), ws, store, sessions.analysisKeys(), "main.cpp", "int main(){return 7;}")
        assertEquals("int main(){return 7;}", File(snapshot.source, "main.cpp").readText())
        assertEquals("int draft;", File(snapshot.source, "model.hpp").readText()); assertTrue(snapshot.isCurrent())
        assertEquals(original, ws.read(ws.resolve("main.cpp"))); assertEquals("int disk;", ws.read(header))
        assertFalse(File(snapshot.source, "main.py").exists()); assertNotEquals(snapshot.source, snapshot.build)
        snapshot.close(); assertFalse(snapshot.directory.exists()); assertEquals("int draft;", store.analysisTexts(sessions.analysisKeys())["model.hpp"])
    }
    @Test fun diskDraftAndSnapshotChangesInvalidateOnlyTheOwnedRunCopy() {
        val ws = workspace(); val store = drafts(); val header = ws.create(ws.root, "model.hpp", false); ws.write(header, "int version1;")
        val parent = temp.newFolder()
        val first = CppRunSnapshot.capture(parent, ws, store, emptyMap(), "main.cpp", "int main(){}")
        ws.write(header, "int version2;"); assertFalse(first.isCurrent())
        val second = CppRunSnapshot.capture(parent, ws, store, emptyMap(), "main.cpp", "int main(){}")
        assertNotEquals(first.directory, second.directory); assertTrue(second.isCurrent())
        first.close(); assertTrue(second.directory.isDirectory); assertEquals("int version2;", File(second.source, "model.hpp").readText())
        File(second.source, "model.hpp").writeText("changed"); assertFalse(second.isCurrent()); second.close()
    }
    @Test fun projectConfigurationIsNeverCopiedOrExecutedAndHeaderCannotBeAnEntry() {
        val ws = workspace(); val store = drafts(); val script = ws.create(ws.root, "compile_commands.json", false); ws.write(script, "malicious build command")
        val snapshot = CppRunSnapshot.capture(temp.newFolder(), ws, store, emptyMap(), "main.cpp", "int main(){}")
        assertFalse(File(snapshot.source, script.name).exists()); snapshot.close()
        assertThrows(IllegalArgumentException::class.java) { CppRunSnapshot.capture(temp.newFolder(), ws, store, emptyMap(), "header.hpp", "") }
    }
    @Test fun executionServiceCanReopenOnlyAnUnusedCanonicalJobUnderItsOwnParent() {
        val ws=workspace();val store=drafts();val parent=temp.newFolder()
        val snapshot=CppRunSnapshot.capture(parent,ws,store,emptyMap(),"main.cpp","int main(){return 0;}")
        val reopened=CppRunSnapshot.open(parent,snapshot.directory,"main.cpp")
        assertEquals(snapshot.texts,reopened.texts);assertTrue(reopened.isCurrent())
        assertThrows(IllegalArgumentException::class.java){CppRunSnapshot.open(temp.newFolder(),snapshot.directory,"main.cpp")}
        assertThrows(IllegalArgumentException::class.java){CppRunSnapshot.open(parent,snapshot.directory,"../main.cpp")}
        File(snapshot.build,"old-output.o").writeText("previous build")
        assertThrows(IllegalArgumentException::class.java){CppRunSnapshot.open(parent,snapshot.directory,"main.cpp")}
        snapshot.close();reopened.close()
    }
}
