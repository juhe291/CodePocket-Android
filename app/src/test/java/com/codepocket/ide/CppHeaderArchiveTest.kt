package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CppHeaderArchiveTest {
    @get:Rule val temp=TemporaryFolder()
    private fun zip(vararg entries:Pair<String,String>):ByteArray=ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach{(name,text)->zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()} }
    }.toByteArray()
    @Test fun boundedExtractionPreservesActualHeaderText(){
        val root=temp.newFolder();CppHeaderArchive.extract(ByteArrayInputStream(zip("sysroot/include/c++/v1/vector" to "class vector {};","clang/include/stddef.h" to "// 类型")),root,2,25)
        assertEquals("class vector {};",File(root,"sysroot/include/c++/v1/vector").readText());assertEquals("// 类型",File(root,"clang/include/stddef.h").readText())
    }
    @Test fun traversalUnexpectedPathsAndSizeOrCountMismatchesFailClosed(){
        for(name in listOf("../escape","/absolute","sysroot/include/../../escape","sysroot\\include\\escape","config/compile_commands.json"))
            assertThrows(IllegalArgumentException::class.java){CppHeaderArchive.extract(ByteArrayInputStream(zip(name to "x")),temp.newFolder(),1,1)}
        assertThrows(IllegalArgumentException::class.java){CppHeaderArchive.extract(ByteArrayInputStream(zip("clang/include/a" to "abc")),temp.newFolder(),1,2)}
        assertThrows(IllegalArgumentException::class.java){CppHeaderArchive.extract(ByteArrayInputStream(zip("clang/include/a" to "x")),temp.newFolder(),2,1)}
    }
    @Test fun duplicateEntriesCannotReplaceAnExistingPrivateFile(){
        val root=temp.newFolder();File(root,"clang/include").mkdirs();File(root,"clang/include/a").writeText("keep")
        assertThrows(IllegalArgumentException::class.java){CppHeaderArchive.extract(ByteArrayInputStream(zip("clang/include/a" to "x")),root,1,1)}
        assertEquals("keep",File(root,"clang/include/a").readText())
    }
}
