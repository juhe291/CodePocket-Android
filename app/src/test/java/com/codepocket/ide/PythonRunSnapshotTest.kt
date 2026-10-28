package com.codepocket.ide

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class PythonRunSnapshotTest {
    @get:Rule val temp=TemporaryFolder()
    private fun workspace()=Workspace(temp.newFolder()){a,b->Files.move(a.toPath(),b.toPath(),REPLACE_EXISTING);Unit}
    private fun drafts()=DraftStore(temp.newFolder()){a,b->Files.move(a.toPath(),b.toPath(),REPLACE_EXISTING);Unit}
    private fun add(ws:Workspace,name:String,text:String)=ws.create(ws.root,name,false).also{ws.write(it,text)}
    @Test fun internalDraftManifestKeepsTheRealRootAndCloseNeverDeletesProgramOutputs(){
        val ws=workspace();val store=drafts();val sessions=EditorSessions(ws.identity)
        val helper=add(ws,"helper.py","value=1\n");val draft="value='😀中文'\n"
        store.put(sessions.key("helper.py"),"helper.py",draft)
        val parent=temp.newFolder();val job=PythonRunSnapshot.capture(parent,ws,store,sessions.analysisKeys(),"main.py",ws.read(ws.resolve("main.py")))
        assertEquals(ws.root,job.root);assertEquals(ws.resolve("main.py"),job.entry);assertTrue(job.isCurrent())
        assertEquals(draft,JSONObject(job.manifest.readText()).getJSONObject("files").getString("helper.py"))
        assertEquals("value=1\n",ws.read(helper));assertEquals(draft,store.read(sessions.key("helper.py"))!!.text)
        assertEquals(job.manifest,PythonRunSnapshot.open(parent,job.directory,job.root,job.entry).manifest)
        val output=File(ws.root,"created.txt").apply{writeText("program output")}
        job.close();job.close();assertFalse(job.isCurrent());assertTrue(parent.listFiles().isNullOrEmpty());assertEquals("program output",output.readText())
        assertEquals("value=1\n",ws.read(helper))
    }
    @Test fun changedDiskOrOtherDraftInvalidatesPreparationWithoutChangingTheCapturedManifest(){
        val ws=workspace();val store=drafts();val sessions=EditorSessions(ws.identity);val helper=add(ws,"helper.py","value=1")
        val key=sessions.key("helper.py");store.put(key,"helper.py","value=2")
        val parent=temp.newFolder();val job=PythonRunSnapshot.capture(parent,ws,store,sessions.analysisKeys(),"main.py",ws.read(ws.resolve("main.py")))
        store.put(key,"helper.py","value=3");assertFalse(job.isCurrent())
        assertEquals("value=2",JSONObject(job.manifest.readText()).getJSONObject("files").getString("helper.py"))
        store.put(key,"helper.py","value=2");assertTrue(job.isCurrent());ws.write(helper,"value=4");assertFalse(job.isCurrent());job.close()
    }
    @Test fun deletedModulesAreNotResurrectedAndWrongDraftIdentityCleansOnlyTheNewJob(){
        val ws=workspace();val store=drafts();val sessions=EditorSessions(ws.identity)
        store.put(sessions.key("deleted.py"),"deleted.py","deleted=1")
        val parent=temp.newFolder();val code=ws.read(ws.resolve("main.py"))
        PythonRunSnapshot.capture(parent,ws,store,sessions.analysisKeys(),"main.py",code).use{
            assertFalse(JSONObject(it.manifest.readText()).getJSONObject("files").has("deleted.py"))
        }
        add(ws,"helper.py","value=1");store.put(sessions.key("helper.py"),"wrong.py","value=2")
        val sentinel=File(parent,"keep").apply{writeText("keep")}
        assertThrows(IllegalArgumentException::class.java){PythonRunSnapshot.capture(parent,ws,store,sessions.analysisKeys(),"main.py",code)}
        assertEquals(listOf(sentinel),parent.listFiles()!!.toList());assertEquals("keep",sentinel.readText())
    }
    @Test fun oversizedCombinedDraftsDoNotLeavePartialMetadataOrOverwriteOriginalFiles(){
        val ws=workspace();val store=drafts();val sessions=EditorSessions(ws.identity)
        repeat(6){val name="m$it.py";add(ws,name,"saved=1");store.put(sessions.key(name),name,"#".repeat(180000))}
        val parent=temp.newFolder()
        assertThrows(IllegalArgumentException::class.java){PythonRunSnapshot.capture(parent,ws,store,sessions.analysisKeys(),"main.py",ws.read(ws.resolve("main.py")))}
        assertTrue(parent.listFiles().isNullOrEmpty());assertEquals("saved=1",ws.read(ws.resolve("m0.py")));assertEquals(6,store.list("").size)
    }
    @Test fun privateServiceRejectsForeignJobsTraversalAndAnotherJobsExecutionRoot(){
        val ws=workspace();val store=drafts();val parent=temp.newFolder();val code=ws.read(ws.resolve("main.py"))
        val first=PythonRunSnapshot.capture(parent,ws,store,emptyMap(),"main.py",code)
        val second=PythonRunSnapshot.capture(parent,ws,store,emptyMap(),"main.py",code)
        try{
            assertThrows(IllegalArgumentException::class.java){PythonRunSnapshot.open(parent,File(parent,"not-a-job"),ws.root,ws.resolve("main.py"))}
            val otherRoot=File(second.directory,"workspace").apply{mkdir()};val otherEntry=File(otherRoot,"main.py").apply{writeText(code)}
            assertThrows(IllegalArgumentException::class.java){PythonRunSnapshot.open(parent,first.directory,otherRoot,otherEntry)}
            first.manifest.writeText(JSONObject().put("version",1).put("files",JSONObject().put("../escape.py","bad")).toString())
            assertThrows(IllegalArgumentException::class.java){PythonRunSnapshot.open(parent,first.directory,ws.root,ws.resolve("main.py"))}
            assertEquals(code,ws.read(ws.resolve("main.py")))
        }finally{first.close();second.close()}
    }
    @Test fun staleActiveBytesAndAnExistingParentFileAreNotSilentlyAccepted(){
        val ws=workspace();val parent=temp.newFolder();val store=drafts()
        assertThrows(IllegalArgumentException::class.java){PythonRunSnapshot.capture(parent,ws,store,emptyMap(),"main.py","different")}
        assertTrue(parent.listFiles().isNullOrEmpty());val sentinel=temp.newFile().apply{writeText("keep")}
        assertThrows(IllegalArgumentException::class.java){PythonRunSnapshot.capture(sentinel,ws,store,emptyMap(),"main.py",ws.read(ws.resolve("main.py")))}
        assertEquals("keep",sentinel.readText())
    }
    @Test fun externalJobUsesFreshModulesAndBinaryResourcesAndNeverChangesTheEditorMirror(){
        val files=linkedMapOf("main.py" to "print(1)\n".toByteArray());var writes=0
        val gateway=object:DocumentGateway{
            override val root=DocumentNode("root","project",true)
            override fun children(id:String)=files.keys.map{DocumentNode(it,it,false)}
            override fun read(id:String)=files.getValue(id).toString(Charsets.UTF_8)
            override fun readBytes(id:String)=files.getValue(id)
            override fun write(id:String,text:String){writes++;files[id]=text.toByteArray()}
            override fun create(parent:String,name:String,directory:Boolean):DocumentNode=error("not used")
            override fun rename(id:String,name:String):DocumentNode=error("not used")
            override fun move(id:String,oldParent:String,newParent:String):DocumentNode=error("not used")
            override fun delete(id:String){files.remove(id)}
        }
        val ws=workspace();val tree=DocumentTree(gateway,temp.newFolder());tree.scan(ws.root)
        ReflectionHelpers.setField(ws,"documents",tree)
        files["helper.py"]="answer=42\n".toByteArray();files["data.bin"]=byteArrayOf(0,-1,2)
        val parent=temp.newFolder();val job=PythonRunSnapshot.capture(parent,ws,drafts(),emptyMap(),"main.py","print(1)\n")
        assertEquals(File(job.directory,"workspace"),job.root);assertEquals("answer=42\n",File(job.root,"helper.py").readText())
        assertArrayEquals(byteArrayOf(0,-1,2),File(job.root,"data.bin").readBytes());assertFalse(File(ws.root,"helper.py").exists())
        job.close();assertTrue(parent.listFiles().isNullOrEmpty());assertEquals("print(1)\n",ws.read(ws.resolve("main.py")));assertEquals(0,writes)
    }
}
