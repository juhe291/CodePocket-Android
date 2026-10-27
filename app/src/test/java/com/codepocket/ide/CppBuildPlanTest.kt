package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual production command arrays run with host Clang21/LLD and the pinned Android inputs. */
class CppBuildPlanTest {
    @get:Rule val temporary=TemporaryFolder()
    private val repository get()=CppFixtures.root
    private fun paths():CppToolchainPaths {
        val windows=System.getProperty("os.name").startsWith("Windows",true);val platform=if(windows)"windows" else "linux"
        val sdk=File(repository,".tools/android-sdk").takeIf{it.isDirectory}?:File(requireNotNull(System.getenv("ANDROID_HOME")))
        val ndk=File(sdk,"ndk/27.2.12479018/toolchains/llvm/prebuilt/$platform-x86_64")
        return CppToolchainPaths(File(repository,".tools/cpp-runtime/host-compiler/$platform/bin/clang"+(if(windows)".exe" else "")),File(ndk,"bin/ld.lld"+(if(windows)".exe" else "")),
            File(repository,".tools/cpp-runtime/headers"),File(repository,".tools/cpp-runtime/build-support"),
            File(repository,".tools/cpp-runtime/jni/arm64-v8a/libcpB_______.so"),File(ndk,"sysroot/usr/lib/aarch64-linux-android/24"))
    }
    private fun project():File=temporary.newFolder("项目 with spaces").canonicalFile.also { root ->
        for(name in listOf("main.cpp","helper.cpp","model.hpp"))File(root,name).writeText(File(repository,"verification/cpp-local-probe-2026-10-27/fixtures/$name").readText())
    }
    private fun execute(step:CppBuildStep,root:File):Pair<Int,String> {
        val evidence=File(repository,"verification/cpp-local-integration-2026-10-27/plan-process.log")
        val process=ProcessBuilder(step.arguments).directory(root).redirectErrorStream(true).redirectOutput(evidence).apply {
            environment()["TEMP"]=File(repository,".tools/semantics-tmp").path;environment()["TMP"]=environment()["TEMP"]
        }.start()
        try { assertTrue("Production command deadline",process.waitFor(30,TimeUnit.SECONDS));return process.exitValue() to evidence.readText() }
        finally { if(process.isAlive){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS)} }
    }
    @Test fun actualMultiFileCommandsBuildAnAndroidSharedProgramWithThePinnedRuntime(){
        val root=project();val out=temporary.newFolder().canonicalFile
        val original=File(root,"main.cpp").readText()
        val plan=CppBuildPlan(root,out,"main.cpp",listOf("main.cpp","model.hpp","helper.cpp"),CppBuildScope.PROJECT,paths())
        assertEquals(listOf("main.cpp","helper.cpp"),plan.sources)
        for(step in plan.steps()){val (code,log)=execute(step,root);assertEquals(log,0,code)}
        assertTrue(plan.program.length()>1000);val header=plan.program.inputStream().use{stream->ByteArray(20).also{assertEquals(20,stream.read(it))}}
        assertArrayEquals(byteArrayOf(0x7f,69,76,70),header.take(4).toByteArray());assertEquals(183,header[18].toInt()and 255)
        assertEquals(original,File(root,"main.cpp").readText());assertFalse(File(root,"unit-0.o").exists())
        CppSharedProgram.validate(plan.program)
        val bad=File(out,"invalid.so")
        bad.writeBytes(plan.program.readBytes().also{it[18]=62})
        assertThrows(IllegalArgumentException::class.java){CppSharedProgram.validate(bad)}
        bad.writeBytes(ByteArray(63));assertThrows(IllegalArgumentException::class.java){CppSharedProgram.validate(bad)}
    }
    @Test fun currentFileAndProjectScopesKeepSeparateProgramsUsable(){
        val root=project();val out=temporary.newFolder().canonicalFile
        val plan=CppBuildPlan(root,out,"main.cpp",listOf("main.cpp","helper.cpp","model.hpp"),CppBuildScope.CURRENT,paths())
        assertEquals(listOf("main.cpp"),plan.sources);assertEquals(2,plan.steps().size)
        assertFalse(plan.steps().first().arguments.contains("helper.cpp"))
    }
    @Test fun leadingDashFilenameIsAnInputAfterTheOptionTerminator(){
        val root=project();File(root,"-module.cpp").writeText("int main(){return 0;}\n")
        val plan=CppBuildPlan(root,temporary.newFolder().canonicalFile,"-module.cpp",listOf("-module.cpp"),CppBuildScope.CURRENT,paths())
        val step=plan.steps().first();assertEquals(listOf("--","./-module.cpp"),step.arguments.takeLast(2))
        val(code,log)=execute(step,root);assertEquals(log,0,code)
    }
    @Test fun traversalDuplicateEntriesHeaderEntryAndSourceOutputMixingAreRejected(){
        val root=project();val out=temporary.newFolder().canonicalFile
        for(names in listOf(listOf("../main.cpp"),listOf("/main.cpp"),listOf("main.cpp","main.cpp"),listOf("C:main.cpp")))
            assertThrows(IllegalArgumentException::class.java){CppBuildPlan(root,out,names.first(),names,CppBuildScope.PROJECT,paths())}
        assertThrows(IllegalArgumentException::class.java){CppBuildPlan(root,out,"model.hpp",listOf("model.hpp"),CppBuildScope.CURRENT,paths())}
        assertThrows(IllegalArgumentException::class.java){CppBuildPlan(root,root,"main.cpp",listOf("main.cpp"),CppBuildScope.CURRENT,paths())}
    }
    private fun hostChild(step:CppBuildStep,root:File):ExecutionProcess {
        val process=ProcessBuilder(step.arguments).directory(root).apply {
            environment()["TEMP"]=File(repository,".tools/semantics-tmp").path;environment()["TMP"]=environment()["TEMP"]
        }.start()
        return object:ExecutionProcess {
            override val stdout=process.inputStream;override val stderr=process.errorStream
            override fun poll()=if(process.isAlive)null else ExecutionExit(process.exitValue(),null)
            override fun close(){if(process.isAlive){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS)};stdout.close();stderr.close();process.outputStream.close()}
        }
    }
    @Test fun actualBuilderRetainsCompilerLineAndMissingHelperLinkFailureThenBuildsTheWholeProject(){
        val root=project()
        val workspace=Workspace(root){a,b->java.nio.file.Files.move(a.toPath(),b.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);Unit}
        val store=DraftStore(temporary.newFolder()){a,b->java.nio.file.Files.move(a.toPath(),b.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);Unit}
        val original=File(root,"main.cpp").readText();val builder=CppLocalBuilder(::hostChild,{}, {false})
        val broken="int main(){\n int value = ;\n}\n"
        CppRunSnapshot.capture(temporary.newFolder(),workspace,store,emptyMap(),"main.cpp",broken).use{snapshot->
            val result=builder.build(snapshot,CppBuildScope.CURRENT,paths()){}
            assertNull(result.program);assertFalse(result.exit.successful);assertTrue(result.stderr.contains("error:"))
            assertEquals(CppBuildError("main.cpp",2,DocumentTree.digest(broken)),result.error)
        }
        CppRunSnapshot.capture(temporary.newFolder(),workspace,store,emptyMap(),"main.cpp",original).use{snapshot->
            val result=builder.build(snapshot,CppBuildScope.CURRENT,paths()){}
            assertNull(result.program);assertTrue(result.stderr.contains("undefined symbol"));assertNull(result.error)
        }
        CppRunSnapshot.capture(temporary.newFolder(),workspace,store,emptyMap(),"main.cpp",original).use{snapshot->
            val stages=mutableListOf<String>();val result=builder.build(snapshot,CppBuildScope.PROJECT,paths()){stages.add(it)}
            assertNotNull(result.program);assertTrue(result.exit.successful);assertEquals(3,stages.size)
            CppSharedProgram.validate(result.program!!)
        }
        assertEquals(original,File(root,"main.cpp").readText())
    }
    @Test fun successfulLinkWithoutMainIsRejectedBeforeRunningAndStaleSourcesNeverLaunch(){
        val root=project();val workspace=Workspace(root){a,b->a.renameTo(b);Unit}
        val store=DraftStore(temporary.newFolder()){a,b->a.renameTo(b);Unit}
        CppRunSnapshot.capture(temporary.newFolder(),workspace,store,emptyMap(),"main.cpp","int missing(){return 0;}").use{snapshot->
            val error=assertThrows(IllegalArgumentException::class.java){CppLocalBuilder(::hostChild,{}, {false}).build(snapshot,CppBuildScope.CURRENT,paths()){} }
            assertTrue(error.message.orEmpty().contains("main"))
        }
        CppRunSnapshot.capture(temporary.newFolder(),workspace,store,emptyMap(),"main.cpp","int main(){return 0;}").use{snapshot->
            File(root,"helper.cpp").appendText("\n// changed\n")
            assertThrows(IllegalArgumentException::class.java){CppLocalBuilder({_,_->error("must not launch")},{},{false}).build(snapshot,CppBuildScope.PROJECT,paths()){} }
        }
    }
}
