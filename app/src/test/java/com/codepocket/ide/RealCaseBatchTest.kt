package com.codepocket.ide

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.TimeUnit

/** Real host interpreters/compiler + shipped runner C + production queue/store/verdicts. No Android execution. */
class RealCaseBatchTest {
    @get:Rule val temp=TemporaryFolder()
    private val root get()=CppFixtures.root
    private val windows get()=System.getProperty("os.name").startsWith("Windows")
    private fun process(args:List<String>,dir:File):Pair<Int,String> {
        val log=File.createTempFile("process-",".log",dir)
        val p=ProcessBuilder(args).directory(dir).redirectErrorStream(true).redirectOutput(log).apply {
            if(windows)environment()["PATH"]="C:/mingw64/bin;"+environment()["PATH"]
            environment()["PYTHONIOENCODING"]="utf-8"
        }.start()
        try { assertTrue("Host process timed out: $args",p.waitFor(15,TimeUnit.SECONDS));return p.exitValue() to log.readText() }
        finally { if(p.isAlive)p.destroyForcibly();log.delete() }
    }
    private fun runBatch(cases:List<ProgramCase>,source:String,launch:(ProgramCase)->RunResult):CaseFile {
        val store=CaseStore(temp.newFolder().canonicalFile){a,b->Files.move(a.toPath(),b.toPath(),REPLACE_EXISTING);Unit}
        val key="host:actual-source";store.saveCases(key,cases)
        val batch=CaseBatch(cases,{done->done(Result.success(object:PreparedCaseRun {
            override val contextHash=DocumentTree.digest(source)
            override fun close() {}
            override fun launch(case:ProgramCase,done:(RunResult)->Unit):Boolean { done(launch(case));return true }
        }))},{record,done->done(runCatching{store.record(key,record);Unit})},{fail("Host fixture did not request cancellation")},{})
        batch.start();assertFalse(batch.running);assertNull(batch.unsavedRecord)
        val stored=store.load(key);assertEquals(cases,stored.cases);assertEquals(batch.records.asReversed(),stored.history)
        return stored
    }
    @Test fun actualCppFiniteInputEofSeparateStderrRuntimeFailureAndWrongAnswerFlowThroughTheBatch() {
        val dir=temp.newFolder().canonicalFile
        val cc=if(windows)"C:/mingw64/bin/gcc.exe" else "/usr/bin/gcc"
        val cxx=if(windows)"C:/mingw64/bin/g++.exe" else "/usr/bin/g++"
        val runner=File(dir,if(windows)"case-runner.exe" else "case-runner")
        val program=File(dir,if(windows)"program.dll" else "program.so")
        val code="#include <iostream>\nextern \"C\" int main(){long x=0,sum=0;while(std::cin>>x){if(x<0){std::cerr<<\"negative\\n\";return 7;}sum+=x;}std::cout<<sum<<\"\\n\";std::cerr<<\"diagnostic\\n\";return 0;}\n"
        val source=File(dir,"main.cpp").apply{writeText(code)}
        val compiled=process(listOf(cc,"-std=c11","-Wall","-Wextra",File(root,"app/src/main/jni/cpp_runner.c").path,"-o",runner.path)+(if(windows)emptyList() else listOf("-ldl")),dir)
        assertEquals(compiled.second,0,compiled.first)
        val linked=process(listOf(cxx,"-std=c++17","-shared",source.path,"-o",program.path)+(if(windows)listOf("-Wl,--export-all-symbols") else listOf("-fPIC")),dir)
        assertEquals(linked.second,0,linked.first);if(!windows)assertTrue(program.setWritable(false,false))
        val cases=listOf(ProgramCase(name="no-final-newline",input="6 7",expected="13\n"),ProgramCase(name="empty-eof",input="",expected="0\n"),
            ProgramCase(name="wrong-answer",input="1",expected="9\n"),ProgramCase(name="runtime-exit",input="-1",expected=""),
            ProgramCase(name="after-runtime",input="1\n2\n3\n",expected="6\n"))
        val results=runBatch(cases,code){case->
            val build=File(dir,case.id).apply{assertTrue(mkdir())};val files=CppCaseFiles.create(build,case.input)
            assertEquals(case.input,files.input.readText())
            val began=System.nanoTime();val executed=process(files.arguments(runner,program),dir)
            assertEquals("No host protocol output should contaminate the captured answer","",executed.second)
            val output=files.read();assertFalse(output.limited)
            RunResult(output.stdout,output.stderr,executed.first.toString(),(System.nanoTime()-began)/1000000,engine="actual host C runner",kind=if(executed.first==0)RunKind.SUCCESS else RunKind.RUNTIME)
        }.history.asReversed()
        assertEquals(listOf(CaseVerdict.PASSED,CaseVerdict.PASSED,CaseVerdict.WRONG_ANSWER,CaseVerdict.RUNTIME_ERROR,CaseVerdict.PASSED),results.map{it.verdict})
        assertEquals("diagnostic\n",results.first().result.stderr);assertEquals("negative\n",results[3].result.stderr)
        assertEquals("7",results[3].result.exitCode);assertEquals("",results[3].result.stdout)
    }
    @Test fun actualPythonRunnerFiniteInputExceptionAndExactNewlineComparisonUseTheProductionDecoder() {
        val dir=temp.newFolder().canonicalFile
        val python=HostRemoteGit.python
        val code="import sys\ntext=sys.stdin.read()\nif text == 'fail': raise ValueError('case failure')\nprint(sum(map(int,text.split())))\nprint('diagnostic',file=sys.stderr)\n"
        val source=File(dir,"main.py").apply{writeText(code)}
        val harness=File(dir,"harness.py").apply{writeText("import sys\nfrom pathlib import Path\nsys.path.insert(0,sys.argv[1])\nimport runner\nprint(runner.run_code(Path(sys.argv[2]).read_text(encoding='utf-8'),Path(sys.argv[3]).read_text(encoding='utf-8'),sys.argv[2],sys.argv[4]))\n")}
        val cases=listOf(ProgramCase(name="finite-no-newline",input="6 7",expected="13\n"),ProgramCase(name="empty-eof",input="",expected="0\n"),
            ProgramCase(name="exact-newline",input="1",expected="1"),ProgramCase(name="ignore-final-newlines",input="2",expected="2",comparison=OutputComparison.IGNORE_FINAL_NEWLINES),
            ProgramCase(name="exception",input="fail",expected=""),ProgramCase(name="after-exception",input="3",expected="3\n"))
        val results=runBatch(cases,code){case->
            val input=File(dir,"${case.id}.stdin").apply{writeBytes(case.input.toByteArray(Charsets.UTF_8))}
            val response=process(listOf(python,"-I",harness.path,File(root,"app/src/main/python").path,source.path,input.path,dir.path),dir)
            assertEquals(response.second,0,response.first)
            PythonEngine.decode(JSONObject(response.second.trim()),"actual host Python production runner")
        }.history.asReversed()
        assertEquals(listOf(CaseVerdict.PASSED,CaseVerdict.PASSED,CaseVerdict.WRONG_ANSWER,CaseVerdict.PASSED,CaseVerdict.RUNTIME_ERROR,CaseVerdict.PASSED),results.map{it.verdict})
        assertTrue(results[4].result.stderr.contains("ValueError: case failure"));assertEquals(3,results[4].result.errorLine)
        assertEquals(DocumentTree.digest(code),results[4].result.errorSourceHash);assertEquals("diagnostic\n",results.first().result.stderr)
    }
}
