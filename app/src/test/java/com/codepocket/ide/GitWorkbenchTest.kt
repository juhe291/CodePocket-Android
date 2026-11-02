package com.codepocket.ide

import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real production Python module and pure wheels; no mocked success responses. */
internal object HostGitBackend {
    fun run(root:File,request:String):String {
        val repository=CppFixtures.root
        val windows=System.getProperty("os.name").orEmpty().startsWith("Windows",true)
        val python=System.getProperty("codepocket.hostPython")?:if(windows)File(repository,".tools/python313-full/tools/python.exe").path else "python3"
        val output=File(root.parentFile,"host-${UUID.randomUUID()}.json");val errors=File(root.parentFile,"host-${UUID.randomUUID()}.log")
        val child=ProcessBuilder(python,"-B","-c","import sys,git_workspace;print(git_workspace.run(sys.argv[1],sys.stdin.read()))",root.absolutePath)
            .apply{environment()["PYTHONUTF8"]="1";environment()["PYTHONPATH"]=listOf(File(repository,".tools/git-probe/site"),
                File(repository,"app/src/main/python")).filter{it.isDirectory}.joinToString(File.pathSeparator){it.path}}
            .redirectOutput(output).redirectError(errors).start()
        try {
            child.outputStream.bufferedWriter(Charsets.UTF_8).use{it.write(request)}
            check(child.waitFor(25,TimeUnit.SECONDS)) { "Pinned host Git backend timed out" }
            check(child.exitValue()==0){errors.readText()};return output.readText(Charsets.UTF_8).trim()
        } finally {if(child.isAlive){child.destroyForcibly();child.waitFor(1,TimeUnit.SECONDS)}}
    }
    fun transport(observer:(JSONObject)->Unit={}):SemanticTransport=object:SemanticTransport {
        override fun analyze(payload:String):String {
            val packet=JSONObject(payload);observer(packet)
            return run(File(packet.getString("root")),packet.getJSONObject("request").toString())
        }
        override fun close(){}
    }
}

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
class GitWorkbenchTest {
    private fun paths(path:String)=JSONObject().put("paths",JSONArray().put(path))
    private fun commitFields()=JSONObject().put("name","CodePocket 测试").put("email","probe@example.invalid").put("message","real commit 中文 😀")
    private fun <T> background(work:()->T):T {
        val worker=Executors.newSingleThreadExecutor()
        return try{worker.submit<T>{assertNotSame(Looper.getMainLooper(),Looper.myLooper());work()}.get(40,TimeUnit.SECONDS)}finally{worker.shutdownNow()}
    }
    private fun run(backend:GitWorkbench,workspace:Workspace,action:String,fields:JSONObject=JSONObject(),expected:String?=null,
        saved:Map<String,String> = emptyMap())=background{backend.perform(workspace,action,fields,expected,saved)}.also{assertTrue(it.toString(),it.ok)}
    @Test fun actualInternalCaptureStageCommitAndReadOnlyDiffUseTheSameRealRepository(){
        val app=RuntimeEnvironment.getApplication();val workspace=Workspace(app,false)
        val source=File(workspace.root,"main.py").apply{writeText("print(1)\n")}
        GitWorkbench(app,GitClient(HostGitBackend.transport())).use{backend->
            val empty=run(backend,workspace,"status");assertFalse(empty.state!!.repository)
            val initialized=run(backend,workspace,"init",expected=empty.state.snapshot)
            assertTrue(File(workspace.root,".git/HEAD").isFile)
            assertEquals(listOf("main.py"),workspace.children(workspace.root).map{it.name})
            val staged=run(backend,workspace,"stage",paths("main.py"),initialized.state!!.snapshot)
            assertEquals(listOf("main.py"),staged.state!!.stagedPaths)
            val committed=run(backend,workspace,"commit",commitFields(),staged.state.snapshot)
            val history=run(backend,workspace,"history");assertEquals(committed.state!!.head,history.commits.single().id)
            source.writeText("print(2)\n");val beforeIndex=File(workspace.root,".git/index").readBytes()
            val difference=run(backend,workspace,"diff",JSONObject().put("path","main.py").put("staged",false))
            assertTrue(difference.text!!.contains("+print(2)"));assertArrayEquals(beforeIndex,File(workspace.root,".git/index").readBytes())
            assertEquals("print(2)\n",source.readText())
        }
        assertTrue(GitJobs.parent(app).listFiles().orEmpty().isEmpty())
    }
    @Test fun actualSafGatewayPublishesBinaryIndexObjectsAndRefsWithoutChangingSourceText(){
        val app=RuntimeEnvironment.getApplication();val (provider,_)=SafGitBinaryIntegrationTest().provider()
        val id=provider.put("main.py","print('SAF')\n".toByteArray());val workspace=Workspace.fromTree(app,provider.tree)
        val modes=mutableListOf<Boolean>()
        GitWorkbench(app,GitClient(HostGitBackend.transport{modes.add(it.getJSONObject("request").getBoolean("modeAvailable"))})).use{backend->
            val state=run(backend,workspace,"status").state!!
            val initialized=run(backend,workspace,"init",expected=state.snapshot)
            val staged=run(backend,workspace,"stage",paths("main.py"),initialized.state!!.snapshot)
            val committed=run(backend,workspace,"commit",commitFields(),staged.state!!.snapshot)
            assertEquals(committed.state!!.head,run(backend,workspace,"history").commits.single().id)
            assertTrue(provider.nodes.values.any{it.name=="index" && it.file.readBytes().take(4).toByteArray().contentEquals("DIRC".toByteArray())})
            assertEquals("print('SAF')\n",provider.nodes.getValue(id).file.readText())
            assertEquals(listOf("main.py"),workspace.children(workspace.root).map{it.name})
        }
        assertTrue(modes.isNotEmpty() && modes.none{it});assertTrue(provider.writeCalls>0)
    }
    @Test fun staleUiSnapshotAndChangedSavedBytesCannotStageOrOverwriteAnotherVersion(){
        val app=RuntimeEnvironment.getApplication();val workspace=Workspace(app,false)
        val source=File(workspace.root,"main.py").apply{writeText("one\n")}
        GitWorkbench(app,GitClient(HostGitBackend.transport())).use{backend->
            val state=run(backend,workspace,"status").state!!;val initial=run(backend,workspace,"init",expected=state.snapshot).state!!
            source.writeText("outside\n")
            val rejected=background{backend.perform(workspace,"stage",paths("main.py"),initial.snapshot)}
            assertEquals("stale",rejected.error);assertFalse(File(workspace.root,".git/index").exists())
            val saved=background{backend.perform(workspace,"stage",paths("main.py"),initial.snapshot,mapOf("main.py" to DocumentTree.digest("one\n")))}
            assertEquals("workspace_changed",saved.error);assertEquals("outside\n",source.readText())
            val accepted=run(backend,workspace,"stage",paths("main.py"),saved=mapOf("main.py" to DocumentTree.digest("outside\n")))
            assertEquals(listOf("main.py"),accepted.state!!.stagedPaths)
        }
    }
    @Test fun cancelledResultNeverPublishesThePrivateServiceMutation(){
        val app=RuntimeEnvironment.getApplication();val workspace=Workspace(app,false);File(workspace.root,"main.py").writeText("keep\n")
        val cancelled=AtomicBoolean();val transport=object:SemanticTransport {
            override fun analyze(payload:String):String {
                val packet=JSONObject(payload);val result=HostGitBackend.run(File(packet.getString("root")),packet.getJSONObject("request").toString())
                if(packet.getJSONObject("request").getString("action")=="init")cancelled.set(true)
                return result
            }
            override fun close(){}
        }
        GitWorkbench(app,GitClient(transport)).use{backend->
            val state=run(backend,workspace,"status").state!!
            try {background{backend.perform(workspace,"init",expected=state.snapshot,cancelled=cancelled::get)};fail("Expected cancellation")}
            catch(error:java.util.concurrent.ExecutionException){assertTrue(error.cause is InterruptedException)}
            assertFalse(File(workspace.root,".git").exists());assertEquals("keep\n",File(workspace.root,"main.py").readText())
        }
    }
    @Test fun protocolRejectsWrongEngineUnsafePathsDuplicateEntriesAndMalformedTypes(){
        val json=JSONObject().put("ok",true).put("engine","dulwich 1.2.20").put("repository",true).put("snapshot","a".repeat(64))
            .put("head",JSONObject.NULL).put("branch","main").put("staged",JSONObject().put("add",JSONArray()).put("modify",JSONArray()).put("delete",JSONArray()))
            .put("unstaged",JSONArray()).put("untracked",JSONArray())
        assertTrue(GitProtocol.decode(json.toString()).ok)
        assertEquals("invalid_response",GitProtocol.decode(JSONObject(json.toString()).put("engine","other").toString()).error)
        assertEquals("invalid_response",GitProtocol.decode(JSONObject(json.toString()).put("untracked",JSONArray().put("../outside")).toString()).error)
        assertEquals("invalid_response",GitProtocol.decode(JSONObject(json.toString()).put("untracked",JSONArray().put("a.py").put("a.py")).toString()).error)
        assertEquals("invalid_response",GitProtocol.decode(JSONObject(json.toString()).put("repository","true").toString()).error)
    }
}
