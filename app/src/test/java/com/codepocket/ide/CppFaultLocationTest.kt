package com.codepocket.ide

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real Clang21 Android ELF/DWARF + host LLVM lookup; no Android crash execution claimed. */
class CppFaultLocationTest {
    @get:Rule val temporary=TemporaryFolder()
    private val repository get()=CppFixtures.root
    private val windows get()=System.getProperty("os.name").orEmpty().startsWith("Windows",true)
    private val platform get()=if(windows)"windows" else "linux"
    private val suffix get()=if(windows)".exe" else ""
    private val ndk get()=File(File(repository,".tools/android-sdk").takeIf{it.isDirectory}?:File(requireNotNull(System.getenv("ANDROID_HOME"))),
        "ndk/27.2.12479018/toolchains/llvm/prebuilt/$platform-x86_64")
    private fun tool(name:String)=File(ndk,"bin/$name$suffix")
    private fun snapshot():CppRunSnapshot {
        val parent=temporary.newFolder().canonicalFile
        val directory=File(parent,UUID.randomUUID().toString()).apply{mkdir()}
        val source=File(directory,"source").apply{mkdir()};File(directory,"build").mkdir()
        File(source,"main.cpp").writeText("extern \"C\" void crash_here();\nint main(){crash_here();}\n")
        File(source,"lib").mkdir()
        File(source,"lib/helper.cpp").writeText("extern \"C\" __attribute__((noinline)) void crash_here(){\n __builtin_trap();\n}\n")
        return CppRunSnapshot.open(parent,directory,"main.cpp")
    }
    private fun execute(name:String,arguments:List<String>,directory:File):String {
        val log=File(repository,"verification/cpp-local-integration-2026-10-27/fault-$name.log")
        val process=ProcessBuilder(arguments).directory(directory).redirectErrorStream(true).redirectOutput(log).apply {
            environment()["TEMP"]=File(repository,".tools/semantics-tmp").path;environment()["TMP"]=environment()["TEMP"]
            environment()["DEBUGINFOD_URLS"]=""
        }.start()
        try {assertTrue(process.waitFor(30,TimeUnit.SECONDS));val text=log.readText();assertEquals(text,0,process.exitValue());return text}
        finally{if(process.isAlive){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS)}}
    }
    @Test fun actualArm64TrapAddressMapsThroughFileOffsetsToTheHelperSecondLine() {
        snapshot().use { snapshot ->
            val tools=CppToolchainPaths(File(repository,".tools/cpp-runtime/host-compiler/$platform/bin/clang$suffix"),tool("ld.lld"),
                File(repository,".tools/cpp-runtime/headers"),File(repository,".tools/cpp-runtime/build-support"),
                File(repository,".tools/cpp-runtime/jni/arm64-v8a/libcpB_______.so"),File(ndk,"sysroot/usr/lib/aarch64-linux-android/24"))
            val plan=CppBuildPlan(snapshot.source,snapshot.build,snapshot.entry,snapshot.texts.keys,CppBuildScope.PROJECT,tools)
            plan.steps().forEachIndexed{index,step->execute("build-$index",step.arguments,snapshot.source)}
            val disassembly=execute("disassembly",listOf(tool("llvm-objdump").path,"-d","--no-show-raw-insn",plan.program.path),snapshot.source)
            val trap=Regex("(?m)^\\s*([0-9a-f]+):\\s+brk\\s").find(disassembly)!!.groupValues[1].toLong(16)
            val image=ByteBuffer.wrap(plan.program.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val phoff=image.getLong(32).toInt();val count=image.getShort(56).toInt()and 65535
            val segment=(0 until count).map{phoff+56*it}.first{at->image.getInt(at)==1 && image.getInt(at+4)and 1!=0 && trap>=image.getLong(at+16) && trap-image.getLong(at+16)<image.getLong(at+32)}
            val fileOffset=image.getLong(segment+8);val virtual=image.getLong(segment+16);val size=image.getLong(segment+32)
            assertNotEquals("16KiB linker segment virtual/file offsets differ",fileOffset,virtual)
            val base=0x7000000000L;val mapStart=base+virtual-(fileOffset and 4095);val mapOffset=fileOffset and -4096L
            val maps="${mapStart.toString(16)}-${(mapStart+size+4096).toString(16)} r-xp ${mapOffset.toString(16)} 00:01 42 ${plan.program.canonicalPath}\n"
            val fault=CppRuntimeFault(5,listOf(0x100,base+trap,base+trap),maps)
            val addresses=CppFaultLocation.offsets(plan.program,fault)
            assertEquals(listOf(trap),addresses)
            assertTrue(CppFaultLocation.offsets(plan.program,fault.copy(maps=maps.replace(plan.program.canonicalPath,plan.program.path+".other"))).isEmpty())
            val arguments=CppFaultLocation.arguments(tool("llvm-symbolizer"),plan.program,addresses)
            assertTrue(arguments.contains("--no-debuginfod"))
            val response=execute("symbolizer",arguments,snapshot.source)
            assertEquals(CppBuildError("lib/helper.cpp",2,DocumentTree.digest(snapshot.texts.getValue("lib/helper.cpp"))),CppFaultLocation.diagnostic(snapshot,response))
            File(snapshot.source,"lib/helper.cpp").appendText("// changed\n")
            assertNull("Changed copy cannot receive a trusted line",CppFaultLocation.diagnostic(snapshot,response))
        }
    }
    @Test fun boundedFaultRecordsRejectInvalidVersionsCountsSignalsAndIncompleteWrites() {
        val bytes=ByteBuffer.allocate(528).order(ByteOrder.LITTLE_ENDIAN).apply{putInt(0x43504631);putInt(11);putInt(2);putInt(0);putLong(0x1234);putLong(0x5678)}.array()
        assertEquals(CppRuntimeFault(11,listOf(0x1234,0x5678),"maps"),CppRuntimeFault.decode(bytes,"maps"))
        for(pair in listOf(0 to 1,4 to 9,8 to 65,12 to 1)) {
            val bad=bytes.clone();ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(pair.first,pair.second)
            assertNull(CppRuntimeFault.decode(bad,""))
        }
        assertNull(CppRuntimeFault.decode(bytes.copyOf(527),""));assertNull(CppRuntimeFault.decode(bytes,"x".repeat(262145)))
    }
    @Test fun unknownExternalAndOutOfRangeSymbolizerLocationsNeverInventAProjectLine() {
        snapshot().use { snapshot ->
            fun response(file:String,line:Int)=JSONObject().put("Symbol",org.json.JSONArray().put(JSONObject().put("FileName",file).put("Line",line))).toString()
            for(value in listOf("not JSON",response("??",0),response("/system/lib64/libc.so",2),response("../main.cpp",1),response("main.cpp",999)))
                assertNull(value,CppFaultLocation.diagnostic(snapshot,value))
            val text=snapshot.texts.getValue("main.cpp")
            assertEquals(CppBuildError("main.cpp",2,DocumentTree.digest(text)),CppFaultLocation.diagnostic(snapshot,response("./main.cpp",2)))
            assertTrue(CppFaultLocation.offsets(File(snapshot.build,"missing"),CppRuntimeFault(11,listOf(1),"")).isEmpty())
        }
    }
}
