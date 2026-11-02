package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Real binary capture and independent publication boundaries, without a network or Android service. */
class GitRemotePlanTest {
    @get:Rule val temp=TemporaryFolder()
    private val old="a".repeat(40);private val next="b".repeat(40)
    private val beforeToken="1".repeat(64);private val afterToken="2".repeat(64)
    private val url="https://github.com/fixture/project.git"
    private fun put(root:File,path:String,data:ByteArray):File=File(root,path).apply{parentFile!!.mkdirs();writeBytes(data)}
    private fun put(root:File,path:String,text:String)=put(root,path,text.toByteArray())
    private fun repository(name:String="original"):File=temp.newFolder(name).apply {
        put(this,"main.py","print(1)\n");put(this,".git/config","[core]\n\tbare = false\n")
        put(this,".git/HEAD","ref: refs/heads/main\n");put(this,".git/refs/heads/main","$old\n")
        put(this,".git/index",byteArrayOf(0,1,-1));put(this,".git/objects/aa/"+"c".repeat(38),byteArrayOf(1,2,3))
    }
    private fun capture(root:File,backup:Boolean=true):Pair<GitWorkspaceStore,GitWorkspaceSnapshot> {
        val store=GitWorkspaceStore(FileGitGateway(root),false)
        return store to store.capture(File(temp.root,"job-${temp.root.listFiles()!!.size}"),backup)
    }
    private fun manifest(snapshot:GitWorkspaceSnapshot,action:String,beforeHead:String?=old,afterHead:String?=old,branch:String="main"):JSONObject {
        val files=JSONObject();val dirs=JSONArray().put("")
        snapshot.root.walkTopDown().drop(1).forEach{file->
            val path=file.relativeTo(snapshot.root).invariantSeparatorsPath
            if(file.isDirectory)dirs.put(path) else files.put(path,JSONObject().put("sha256",GitWorkspaceStore.digest(file.readBytes())).put("size",file.length()))
        }
        return JSONObject().put("version",1).put("root",snapshot.root.absolutePath).put("action",action).put("before",beforeToken).put("after",afterToken)
            .put("url",url).put("remote_branch","main").put("before_head",beforeHead?:JSONObject.NULL).put("after_head",afterHead?:JSONObject.NULL)
            .put("local_branch",branch).put("files",files).put("directories",dirs)
    }
    private fun plan(snapshot:GitWorkspaceSnapshot,json:JSONObject):GitRemotePlan {
        File(snapshot.root.parentFile,"result.plan").writeText(json.toString())
        return GitRemotePlan.read(snapshot,json.getString("action"),beforeToken,afterToken,url,"main")
    }
    private fun approve(store:GitWorkspaceStore,snapshot:GitWorkspaceSnapshot,json:JSONObject)=store.remoteChanges(snapshot,plan(snapshot,json))
    private fun rejects(store:GitWorkspaceStore,snapshot:GitWorkspaceSnapshot,json:JSONObject) {
        assertThrows(IllegalArgumentException::class.java){approve(store,snapshot,json)}
    }
    @Test fun completeBackupIncludesBinaryBodiesAndEmptyDirectoriesWhileLocalDefaultKeepsItsScope() {
        val root=repository();val binary=byteArrayOf(0,-1,2,3);put(root,"assets/image.bin",binary);File(root,"empty/nested").mkdirs()
        val (store,snapshot)=capture(root)
        assertTrue(snapshot.worktreeBackedUp);assertArrayEquals(binary,File(snapshot.beforeRoot,"assets/image.bin").readBytes())
        assertTrue(File(snapshot.beforeRoot,"empty/nested").isDirectory);store.assertFresh(snapshot)
        File(snapshot.root,"assets/image.bin").writeBytes(byteArrayOf(9))
        assertArrayEquals(binary,File(snapshot.beforeRoot,"assets/image.bin").readBytes())
        val (_,local)=capture(root,false);assertFalse(local.worktreeBackedUp)
        assertFalse(File(local.beforeRoot,"main.py").exists());assertTrue(File(local.beforeRoot,".git/index").isFile)
        assertArrayEquals(binary,File(root,"assets/image.bin").readBytes())
    }
    @Test fun configureChangesOnlyOriginConfigurationAndRetainsTheOriginalBody() {
        val root=repository();val (store,snapshot)=capture(root,false)
        put(snapshot.root,".git/config","[core]\n\tbare = false\n[remote \"origin\"]\n\turl = $url\n")
        assertEquals(listOf(".git/config"),approve(store,snapshot,manifest(snapshot,"configure")).map{it.path})
        assertEquals("print(1)\n",File(root,"main.py").readText());store.assertFresh(snapshot)
    }
    @Test fun fastForwardApprovesAddedModifiedDeletedBinaryAndFileDirectoryTransitions() {
        val root=repository();put(root,"old/file.txt","old-dir");put(root,"switch","old-file");put(root,"remove.bin",byteArrayOf(0,-1))
        val (store,snapshot)=capture(root)
        File(snapshot.root,"old/file.txt").delete();File(snapshot.root,"old").delete();put(snapshot.root,"old","new-file")
        File(snapshot.root,"switch").delete();put(snapshot.root,"switch/next.bin",byteArrayOf(2,-1));File(snapshot.root,"remove.bin").delete()
        put(snapshot.root,"main.py","print(2)\n");put(snapshot.root,".git/refs/heads/main","$next\n");put(snapshot.root,".git/index",byteArrayOf(3,4))
        val changes=approve(store,snapshot,manifest(snapshot,"fast_forward",afterHead=next))
        assertEquals(setOf("old/file.txt","old","switch","switch/next.bin","remove.bin","main.py",".git/refs/heads/main",".git/index"),changes.map{it.path}.toSet())
        assertNull(changes.single{it.path=="remove.bin"}.after);assertArrayEquals(byteArrayOf(0,-1),File(snapshot.beforeRoot,"remove.bin").readBytes())
        assertTrue(File(root,"old/file.txt").isFile);assertEquals("old-file",File(root,"switch").readText());store.assertFresh(snapshot)
    }
    @Test fun originalMetadataPublisherStillRejectsBodyChangesEvenWithACompleteBackup() {
        val (store,snapshot)=capture(repository());put(snapshot.root,"main.py","changed")
        assertThrows(IllegalArgumentException::class.java){store.metadataChanges(snapshot)}
    }
    @Test fun bodyUpdateWithoutCompleteBackupOrWithCorruptBackupIsRejected() {
        val root=repository();val (store,snapshot)=capture(root,false);put(snapshot.root,"main.py","changed")
        rejects(store,snapshot,manifest(snapshot,"fast_forward"))
        val (full,copy)=capture(root);put(copy.root,"main.py","changed");put(copy.beforeRoot,"main.py","corrupt")
        rejects(full,copy,manifest(copy,"fast_forward"));assertEquals("print(1)\n",File(root,"main.py").readText())
    }
    @Test fun resultMustMatchAllManifestHashesLengthsAndDirectories() {
        val (store,snapshot)=capture(repository());val json=manifest(snapshot,"configure")
        val file=json.getJSONObject("files").getJSONObject("main.py")
        file.put("sha256","f".repeat(64));rejects(store,snapshot,json)
        file.put("sha256",GitWorkspaceStore.digest(File(snapshot.root,"main.py").readBytes())).put("size",999)
        rejects(store,snapshot,json)
        val original=manifest(snapshot,"configure");File(snapshot.root,"unexpected").mkdir();rejects(store,snapshot,original)
    }
    @Test fun snapshotMutationAfterReadingTheManifestCannotBePublished() {
        val (store,snapshot)=capture(repository());val result=plan(snapshot,manifest(snapshot,"configure"))
        put(snapshot.root,"main.py","late engine mutation")
        assertThrows(IllegalArgumentException::class.java){store.remoteChanges(snapshot,result)}
    }
    @Test fun manifestScopeAndRequestResponseTokensAreStrict() {
        val (_,snapshot)=capture(repository());val source=File(snapshot.root.parentFile,"result.plan")
        for(field in listOf("root","action","before","after","url","remote_branch")) {
            val json=manifest(snapshot,"configure").put(field,"wrong");source.writeText(json.toString())
            assertThrows(IllegalArgumentException::class.java){GitRemotePlan.read(snapshot,"configure",beforeToken,afterToken,url,"main")}
        }
        val withSecret=manifest(snapshot,"configure").put("token","must-not-be-here");source.writeText(withSecret.toString())
        assertThrows(IllegalArgumentException::class.java){GitRemotePlan.read(snapshot,"configure",beforeToken,afterToken,url,"main")}
        source.writeText(manifest(snapshot,"configure").toString())
        assertThrows(IllegalArgumentException::class.java){GitRemotePlan.read(snapshot,"push",beforeToken,afterToken,url,"main")}
        assertThrows(IllegalArgumentException::class.java){GitRemotePlan.read(snapshot,"configure",beforeToken,afterToken,"https://secret@github.com/fixture/project.git","main")}
    }
    @Test fun unsafeManifestPathsAndMalformedVersionLengthsAreRejected() {
        val (_,snapshot)=capture(repository())
        for(path in listOf("../outside","/absolute",".git/../config","a/.git/config",".GIT/config","a\\bad","bad:name")) {
            val json=manifest(snapshot,"configure");json.getJSONObject("files").put(path,JSONObject().put("sha256","f".repeat(64)).put("size",1))
            assertThrows(IllegalArgumentException::class.java){plan(snapshot,json)}
        }
        for(value in listOf(-1,16_000_001,true,"10",1.5)) {
            val json=manifest(snapshot,"configure");json.getJSONObject("files").getJSONObject("main.py").put("size",value)
            assertThrows(IllegalArgumentException::class.java){plan(snapshot,json)}
        }
        val duplicate=manifest(snapshot,"configure");duplicate.getJSONArray("directories").put("")
        assertThrows(IllegalArgumentException::class.java){plan(snapshot,duplicate)}
    }
    @Test fun fetchAllowsPairedNewPackAndSelectedRemoteRefWithoutChangingHeadOrIndex() {
        val (store,snapshot)=capture(repository(),false);val pack=".git/objects/pack/pack-"+"d".repeat(40)
        put(snapshot.root,"$pack.pack",byteArrayOf(1,2));put(snapshot.root,"$pack.idx",byteArrayOf(3,4))
        put(snapshot.root,".git/refs/remotes/origin/main","$next\n")
        assertEquals(setOf("$pack.pack","$pack.idx",".git/refs/remotes/origin/main"),approve(store,snapshot,manifest(snapshot,"fetch")).map{it.path}.toSet())
    }
    @Test fun orphanPackAndChangedExistingObjectAreRejected() {
        val (store,snapshot)=capture(repository(),false);val pack=".git/objects/pack/pack-"+"d".repeat(40)+".pack"
        put(snapshot.root,pack,byteArrayOf(1,2));rejects(store,snapshot,manifest(snapshot,"fetch"));File(snapshot.root,pack).delete()
        put(snapshot.root,".git/objects/aa/"+"c".repeat(38),byteArrayOf(9));rejects(store,snapshot,manifest(snapshot,"fetch"))
    }
    @Test fun fetchCannotChangeSourceIndexHeadConfigurationOrUnselectedRemote() {
        for(path in listOf("main.py",".git/index",".git/config",".git/packed-refs",".git/refs/remotes/origin/other")) {
            val (store,snapshot)=capture(repository("original-${temp.root.listFiles()!!.size}"),false)
            put(snapshot.root,path,"unexpected-change");rejects(store,snapshot,manifest(snapshot,"fetch"))
        }
        val (store,snapshot)=capture(repository("moved-head"),false);put(snapshot.root,".git/refs/heads/main","$next\n")
        rejects(store,snapshot,manifest(snapshot,"fetch",afterHead=next))
    }
    @Test fun fastForwardCannotChangeAnotherLocalBranchOrRemoteConfiguration() {
        for(path in listOf(".git/refs/heads/other",".git/config",".git/refs/remotes/origin/main")) {
            val (store,snapshot)=capture(repository("original-${temp.root.listFiles()!!.size}"))
            put(snapshot.root,path,"$next\n");rejects(store,snapshot,manifest(snapshot,"fast_forward"))
        }
    }
    @Test fun cloneRequiresAnOriginallyEmptyTargetAndTheConfirmedBranch() {
        val empty=temp.newFolder("empty");val (store,snapshot)=capture(empty)
        put(snapshot.root,".git/HEAD","ref: refs/heads/main\n");put(snapshot.root,".git/config","[core]\n\tbare = false\n")
        put(snapshot.root,".git/refs/heads/main","$next\n");put(snapshot.root,"hello.cpp","int main() {}\n")
        assertEquals(4,approve(store,snapshot,manifest(snapshot,"clone",beforeHead=null,afterHead=next)).size);assertEquals(emptyList<String>(),empty.list()!!.toList())
        val original=repository();val (other,existing)=capture(original)
        rejects(other,existing,manifest(existing,"clone"))
    }
    @Test fun manifestsCannotSpoofHeadResolutionIncludingPackedRefsOrDetachedState() {
        val root=repository();File(root,".git/refs/heads/main").delete();put(root,".git/packed-refs","# pack-refs with: peeled\n$old refs/heads/main\n")
        val (store,snapshot)=capture(root,false);assertEquals(emptyList<GitMetadataChange>(),approve(store,snapshot,manifest(snapshot,"fetch")))
        rejects(store,snapshot,manifest(snapshot,"fetch",beforeHead=next))
        val detached=repository("detached");put(detached,".git/HEAD","$old\n");val (other,copy)=capture(detached)
        rejects(other,copy,manifest(copy,"fast_forward",branch="HEAD"))
    }
    @Test fun irrelevantMetadataDirectoriesAndRemovedMetadataAreRejected() {
        val (store,snapshot)=capture(repository(),false);File(snapshot.root,".git/unrelated").mkdir();rejects(store,snapshot,manifest(snapshot,"fetch"))
        File(snapshot.root,".git/unrelated").delete();File(snapshot.root,".git/index").delete();rejects(store,snapshot,manifest(snapshot,"configure"))
    }
    @Test fun foreignStoreCannotApproveAnotherWorkspacesSnapshot() {
        val (_,snapshot)=capture(repository());val (other,_)=capture(repository("other"))
        assertThrows(IllegalArgumentException::class.java){other.remoteChanges(snapshot,plan(snapshot,manifest(snapshot,"configure")))}
    }
    @Test fun acceptsActualPinnedEngineTlsResultsForCloneFetchFastForwardAndConfigure() {
        val repo=generateSequence(File(checkNotNull(System.getProperty("user.dir")))){it.parentFile}
            .first{File(it,"verification/git-remote-workspace-2026-11-02/native-engine-fixtures.json").isFile}
        val fixtures=JSONObject(File(repo,"verification/git-remote-workspace-2026-11-02/native-engine-fixtures.json").readText())
        val cases=fixtures.getJSONArray("cases");assertEquals(4,cases.length())
        fun restore(root:File,tree:JSONObject) {
            val dirs=tree.getJSONArray("directories")
            (0 until dirs.length()).map{dirs.getString(it)}.filter{it.isNotEmpty()}.sortedBy{it.count{c->c=='/'}}
                .forEach{File(root,it).let{folder->check(folder.isDirectory || folder.mkdirs())}}
            val files=tree.getJSONObject("files")
            files.keys().forEach{path->put(root,path,java.util.Base64.getDecoder().decode(files.getString(path)))}
        }
        for(i in 0 until cases.length()) {
            val case=cases.getJSONObject(i);val original=temp.newFolder("engine-${case.getString("name")}")
            val before=case.getJSONObject("before");val after=case.getJSONObject("after");restore(original,before)
            val (store,snapshot)=capture(original)
            // Only this test-owned private snapshot is replaced with captured engine bytes.
            val owned=snapshot.root.canonicalPath+File.separator
            snapshot.root.walkBottomUp().filter{it!=snapshot.root}.forEach{file->
                check(file.canonicalPath.startsWith(owned));check(file.delete())
            }
            restore(snapshot.root,after)
            val request=case.getJSONObject("request");val response=case.getJSONObject("response")
            // Fingerprints are path-independent. Relocate only the manifest's owned root.
            val json=case.getJSONObject("plan").put("root",snapshot.root.absolutePath)
            File(snapshot.root.parentFile,"result.plan").writeText(json.toString())
            val approval=GitRemotePlan.read(snapshot,request.getString("action"),request.getString("expected"),response.getString("snapshot"),
                request.getString("url"),request.getString("branch"))
            val changes=store.remoteChanges(snapshot,approval)
            val oldFiles=before.getJSONObject("files");val newFiles=after.getJSONObject("files")
            val names=oldFiles.keys().asSequence().toSet()+newFiles.keys().asSequence().toSet()
            val changed=names.filter{oldFiles.has(it)!=newFiles.has(it) || oldFiles.optString(it)!=newFiles.optString(it)}.toSet()
            assertEquals(case.getString("name"),changed,changes.map{it.path}.toSet())
            store.assertFresh(snapshot)
            for(path in oldFiles.keys())assertArrayEquals(java.util.Base64.getDecoder().decode(oldFiles.getString(path)),File(original,path).readBytes())
            if(case.getString("name")=="fast_forward") {
                assertNull(changes.single{it.path=="remove.bin"}.after)
                assertEquals("file-after",File(snapshot.root,"old").readText())
                assertEquals("directory-after",File(snapshot.root,"switch/new.txt").readText())
            }
        }
    }
}
