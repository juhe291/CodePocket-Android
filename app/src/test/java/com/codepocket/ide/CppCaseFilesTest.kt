package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CppCaseFilesTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun realFiniteInputAndIndependentOutputsUseOnlyNewFilesInTheOwnedBuildDirectory(){
        val build=temp.newFolder().canonicalFile;val sentinel=File(build,"keep").apply{writeText("keep")}
        val text="6 7\u0000中文";val files=CppCaseFiles.create(build,text)
        assertArrayEquals(text.toByteArray(Charsets.UTF_8),files.input.readBytes())
        files.output.writeText("42\n");files.error.writeText("separate warning\n")
        val read=files.read();assertEquals("42\n",read.stdout);assertEquals("separate warning\n",read.stderr);assertFalse(read.limited)
        assertEquals(listOf("runner","program",files.input.path,files.output.path,files.error.path),files.arguments(File("runner"),File("program")))
        val second=CppCaseFiles.create(build,"");assertNotEquals(files.input,second.input);assertEquals("keep",sentinel.readText())
    }
    @Test fun excessiveOutputIsExplicitlyLimitedAndRemovedOutputsAreRejected(){
        val files=CppCaseFiles.create(temp.newFolder().canonicalFile,"")
        files.output.writeText("x".repeat(230000));val read=files.read()
        assertTrue(read.limited);assertEquals(50000,read.stdout.length)
        assertTrue(files.error.delete());assertThrows(IllegalArgumentException::class.java){files.read()}
    }
}
