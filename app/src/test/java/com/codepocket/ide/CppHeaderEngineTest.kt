package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Executes production connection/compaction/protocol against the pinned real host clangd. */
class CppHeaderEngineTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun productionEngineReturnsActualFileDirectoryAndMiddlePathEditsWithoutWritingTheBuffer(){
        val cache=File(CppFixtures.root,".tools/cpp-runtime")
        val suffix=if(System.getProperty("os.name").orEmpty().startsWith("Windows",true))".exe" else ""
        val executable=System.getProperty("codepocket.hostClangd")?.let(::File)?:File(cache,"host/clangd_21.1.8/bin/clangd$suffix")
        val sysroot=System.getProperty("codepocket.cppSysroot")?.let(::File)?:File(cache,"headers/sysroot")
        val resource=System.getProperty("codepocket.cppResource")?.let(::File)?:File(cache,"headers/clang")
        assertTrue(executable.isFile);assertTrue(File(sysroot,"include/c++/v1/vector").isFile)
        val root=temp.newFolder().canonicalFile;File(root,"main.cpp").writeText("")
        File(root,"model.hpp").writeText("struct Model {};\n");File(root,"sub").mkdir();File(root,"sub/file.hpp").writeText("struct Sub {};\n")
        val process=ProcessBuilder(executable.path,"--enable-config=false","--background-index=false","--clang-tidy=false",
            "--header-insertion=never","--function-arg-placeholders=false","--completion-style=detailed","--completion-parse=always",
            "--all-scopes-completion=false","--use-dirty-headers","-j=2","--limit-results=30","--pch-storage=memory","--log=verbose")
            .directory(root).redirectError(File(CppFixtures.root,"verification/cpp-header-completion-2026-10-27/production-engine-stderr.log")).start()
        val bridge=object:AnalysisProcess {
            override val input=process.inputStream;override val output=process.outputStream;override val errors=process.errorStream
            override fun close(){process.destroy();if(!process.waitFor(1,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(1,TimeUnit.SECONDS)}}
        }
        val flags=listOf("-xc++","-std=c++17","--target=aarch64-linux-android24","--sysroot=${sysroot.path}","-resource-dir=${resource.path}",
            "-isystem",File(sysroot,"include/c++/v1").path,"-isystem",File(sysroot,"include").path,"-isystem",File(sysroot,"include/aarch64-linux-android").path,"-I${root.path}")
        CppLspEngine(bridge,root,flags).use { engine ->
            for(tag in listOf("system","project","nested","middle","closed-system","directory","directory-next","system-nested","unicode-crlf","closed-project")){
                val request=HeaderFixtures.request(tag);val raw=engine.analyze(request.code,request.caret,"main.cpp","complete"){false}
                val result=CppSemanticProtocol.decode(request,raw,"complete");assertTrue("$tag: $raw",result.available)
                val batch=requireNotNull(result.completion);val expected=HeaderFixtures.case(tag).getString("expected")
                val item=batch.items.firstOrNull{it.semanticInsertion!!.change.text.startsWith(expected)};assertNotNull("$tag: $raw",item)
                assertEquals(if(tag=="directory")CompletionKind.DIRECTORY else CompletionKind.HEADER,item!!.kind)
                val edit=requireNotNull(batch.edit(request,batch.items.indexOf(item)));assertTrue(request.code.replaceRange(edit.start,edit.end,edit.text).contains(expected))
                assertEquals("",File(root,"main.cpp").readText())
            }
        }
    }
}
