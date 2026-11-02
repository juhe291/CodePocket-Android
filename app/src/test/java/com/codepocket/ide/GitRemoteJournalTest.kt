package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class GitRemoteJournalTest {
    @get:Rule val temp=TemporaryFolder()
    private val old="a".repeat(40);private val next="b".repeat(40)
    private val beforeToken="1".repeat(64);private val afterToken="2".repeat(64)
    private val url="https://github.com/fixture/project.git"
    private class Provider:DocumentGateway {
        override val root=DocumentNode("opaque-root","project",true)
        val nodes=linkedMapOf(root.id to root);val parents=mutableMapOf<String,String>();val bytes=mutableMapOf<String,ByteArray>()
        private var serial=0;var writes=0;val trace=mutableListOf<String>()
        var beforeWrite:((String,ByteArray)->Unit)?=null;var afterDelete:((String)->Unit)?=null
        var changedCreateName:((String)->String)?=null
        override val identity="test-provider-${UUID.randomUUID()}"
        override fun children(id:String)=nodes.values.filter{parents[it.id]==id}
        override fun read(id:String)=readBinary(id,GitWorkspaceStore.MAX_FILE).toString(Charsets.UTF_8)
        override fun readBinary(id:String,limit:Int)=bytes.getValue(id).copyOf().also{require(it.size<=limit)}
        override fun write(id:String,text:String)=error("Text writes are forbidden")
        override fun writeBytes(id:String,bytes:ByteArray) {
            writes++;val path=path(id);trace.add(path);beforeWrite?.invoke(path,bytes);this.bytes[id]=bytes.copyOf()
        }
        override fun create(parent:String,name:String,directory:Boolean):DocumentNode {
            val actual=changedCreateName?.invoke(name)?:name;check(children(parent).none{it.name==actual})
            return DocumentNode("opaque-${++serial}",actual,directory).also{nodes[it.id]=it;parents[it.id]=parent;if(!directory)bytes[it.id]=byteArrayOf()}
        }
        override fun rename(id:String,name:String):DocumentNode=error("not used")
        override fun move(id:String,oldParent:String,newParent:String):DocumentNode=error("not used")
        override fun delete(id:String) {
            check(children(id).isEmpty());val path=path(id);nodes.remove(id);parents.remove(id);bytes.remove(id);afterDelete?.invoke(path)
        }
        fun path(id:String):String=if(id==root.id)"" else {
            val parent=path(parents.getValue(id));(if(parent.isEmpty())"" else "$parent/")+nodes.getValue(id).name
        }
        fun find(path:String):DocumentNode? {
            var node=root
            for(part in path.split('/'))node=children(node.id).singleOrNull{it.name==part}?:return null
            return node
        }
        fun put(path:String,value:ByteArray) {
            var parent=root;val parts=path.split('/')
            parts.dropLast(1).forEach{name->parent=children(parent.id).singleOrNull{it.name==name}?:create(parent.id,name,true)}
            val node=children(parent.id).singleOrNull{it.name==parts.last()}?:create(parent.id,parts.last(),false)
            bytes[node.id]=value.copyOf()
        }
        fun files()=nodes.values.filter{!it.directory}.associate{path(it.id) to bytes.getValue(it.id).toList()}
        fun dirs()=nodes.values.filter{it.directory}.map{path(it.id)}.toSet()
    }
    private fun replace(from:File,to:File){from.copyTo(to,true);check(from.delete())}
    private fun store(p:Provider)=GitWorkspaceStore(p,false,::replace)
    private fun put(root:File,path:String,value:ByteArray)=File(root,path).apply{parentFile!!.mkdirs();writeBytes(value)}
    private fun put(root:File,path:String,value:String)=put(root,path,value.toByteArray())
    private fun provider(transitions:Boolean=false)=Provider().apply {
        put("main.py","print(1)\n".toByteArray());put("remove.bin",byteArrayOf(0,-1,2));put("keep-local.txt","untracked kept".toByteArray())
        put(".git/config","[core]\n\tbare = false\n".toByteArray());put(".git/HEAD","ref: refs/heads/main\n".toByteArray())
        put(".git/refs/heads/main","$old\n".toByteArray());put(".git/index",byteArrayOf(0,1))
        if(transitions){put("becomes-file/old.txt","old-dir".toByteArray());put("becomes-dir","old-file".toByteArray())}
    }
    private fun snapshot(store:GitWorkspaceStore)=store.capture(File(temp.root,"job-${UUID.randomUUID()}"),true)
    private fun plan(snapshot:GitWorkspaceSnapshot,action:String="fast_forward",beforeHead:String?=old,afterHead:String?=next):GitRemotePlan {
        val files=JSONObject();val dirs=JSONArray().put("")
        snapshot.root.walkTopDown().drop(1).forEach{file->
            val path=file.relativeTo(snapshot.root).invariantSeparatorsPath
            if(file.isDirectory)dirs.put(path) else files.put(path,JSONObject().put("sha256",GitWorkspaceStore.digest(file.readBytes())).put("size",file.length()))
        }
        val json=JSONObject().put("version",1).put("root",snapshot.root.absolutePath).put("action",action).put("before",beforeToken).put("after",afterToken)
            .put("url",url).put("remote_branch","main").put("before_head",beforeHead?:JSONObject.NULL).put("after_head",afterHead?:JSONObject.NULL)
            .put("local_branch","main").put("files",files).put("directories",dirs)
        File(snapshot.root.parentFile,"result.plan").writeText(json.toString())
        return GitRemotePlan.read(snapshot,action,beforeToken,afterToken,url,"main")
    }
    private fun update(snapshot:GitWorkspaceSnapshot,transitions:Boolean=false) {
        put(snapshot.root,"main.py","print(2)\n");File(snapshot.root,"remove.bin").delete();put(snapshot.root,"added/data.bin",byteArrayOf(-1,0,4))
        if(transitions) {
            File(snapshot.root,"becomes-file/old.txt").delete();File(snapshot.root,"becomes-file").delete();put(snapshot.root,"becomes-file","new-file")
            File(snapshot.root,"becomes-dir").delete();put(snapshot.root,"becomes-dir/new.txt","new-dir")
        }
        put(snapshot.root,".git/index",byteArrayOf(2,3));put(snapshot.root,".git/refs/heads/main","$next\n")
    }
    private fun journal()=temp.newFolder("journal-${UUID.randomUUID()}")
    private fun zip(store:GitWorkspaceStore,journal:File):Map<String,ByteArray> {
        val bytes=java.io.ByteArrayOutputStream();store.exportRecovery(journal,bytes);val files=linkedMapOf<String,ByteArray>()
        java.util.zip.ZipInputStream(bytes.toByteArray().inputStream()).use{zip->while(true){val entry=zip.nextEntry?:break;files[entry.name]=zip.readBytes()}}
        return files
    }
    @Test fun safeFastForwardPublishesBinaryAddModifyDeleteAndBothFileDirectoryTransitions() {
        val p=provider(true);val originalKeep=p.find("keep-local.txt")!!.id;val s=store(p);val copy=snapshot(s);update(copy,true)
        val journal=journal();s.publishRemote(copy,plan(copy),journal)
        assertEquals("print(2)\n",p.read(p.find("main.py")!!.id));assertNull(p.find("remove.bin"))
        assertArrayEquals(byteArrayOf(-1,0,4),p.readBinary(p.find("added/data.bin")!!.id,100))
        assertEquals("new-file",p.read(p.find("becomes-file")!!.id));assertEquals("new-dir",p.read(p.find("becomes-dir/new.txt")!!.id))
        assertEquals(originalKeep,p.find("keep-local.txt")!!.id);assertEquals("untracked kept",p.read(originalKeep))
        assertTrue(p.trace.indexOf("main.py")<p.trace.indexOf(".git/index"));assertTrue(p.trace.indexOf(".git/index")<p.trace.indexOf(".git/refs/heads/main"))
        assertEquals("committed",JSONObject(File(journal,"pending.json").readText()).getString("state"))
        val written=p.writes;store(p).recover(journal);assertEquals(written,p.writes);assertFalse(File(journal,"pending.json").exists())
    }
    @Test fun staleOriginalIsRejectedWithoutAnyWriteOrJournal() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy);val approval=plan(copy);p.put("main.py","external".toByteArray())
        val journal=journal();assertThrows(IllegalArgumentException::class.java){s.publishRemote(copy,approval,journal)}
        assertEquals(0,p.writes);assertFalse(File(journal,"pending.json").exists());assertEquals("external",p.read(p.find("main.py")!!.id))
    }
    @Test fun latePrivateSnapshotMutationOrCorruptBackupNeverWritesOriginals() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy);val approval=plan(copy);put(copy.root,"main.py","late")
        assertThrows(IllegalArgumentException::class.java){s.publishRemote(copy,approval,journal())};assertEquals(0,p.writes)
        val second=snapshot(s);update(second);val secondPlan=plan(second);put(second.beforeRoot,"remove.bin",byteArrayOf(9))
        assertThrows(IllegalArgumentException::class.java){s.publishRemote(second,secondPlan,journal())};assertEquals(0,p.writes)
    }
    @Test fun failedFinalRefWriteRestoresKnownOwnBodiesDirectoriesIndexAndDeletedBinary() {
        val p=provider(true);val original=p.files();val dirs=p.dirs();val s=store(p);val copy=snapshot(s);update(copy,true)
        p.beforeWrite={path,_->if(path==".git/refs/heads/main")throw IOException("ref refused")}
        val journal=journal();assertThrows(IOException::class.java){s.publishRemote(copy,plan(copy),journal)}
        assertEquals(original,p.files());assertEquals(dirs,p.dirs());assertFalse(File(journal,"pending.json").exists())
    }
    @Test fun restartedStoreFinishesAnInterruptedKnownRollback() {
        val p=provider(true);val original=p.files();val dirs=p.dirs();val s=store(p);val copy=snapshot(s);update(copy,true)
        p.beforeWrite={path,data->if(path==".git/refs/heads/main" || path=="main.py" && data.contentEquals("print(1)\n".toByteArray()))throw IOException("offline")}
        val journal=journal();assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)}
        assertTrue(File(journal,"pending.json").exists());p.beforeWrite=null;store(p).recover(journal)
        assertEquals(original,p.files());assertEquals(dirs,p.dirs());assertFalse(File(journal,"pending.json").exists())
    }
    @Test fun unknownPartialSourceKeepsBeforeAfterCurrentBytesAndBlocksLegacyPublication() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy)
        p.beforeWrite={path,_->if(path=="main.py"){p.bytes[p.find(path)!!.id]=byteArrayOf(99);throw IOException("partial provider write")}}
        val journal=journal();assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)}
        p.beforeWrite=null;assertThrows(GitRecoveryRequired::class.java){store(p).recover(journal)}
        val exported=zip(s,journal)
        assertArrayEquals("print(1)\n".toByteArray(),exported["before/main.py"]);assertArrayEquals("print(2)\n".toByteArray(),exported["after/main.py"])
        assertArrayEquals(byteArrayOf(99),exported["current/main.py"]);assertTrue(s.recoveryDescription(journal).contains("正文"))
        val local=store(p);val localCopy=snapshot(local);put(localCopy.root,".git/index",byteArrayOf(9))
        assertThrows(GitRecoveryRequired::class.java){local.publish(localCopy,journal)}
        assertArrayEquals(byteArrayOf(99),p.readBinary(p.find("main.py")!!.id,100));assertTrue(File(journal,"pending.json").exists())
    }
    @Test fun sameBytesWithForeignProviderIdentityAreNotOverwritten() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy);val oldId=p.find("main.py")!!.id
        p.beforeWrite={path,data->if(path=="main.py"){p.delete(oldId);p.put(path,data);throw IOException("identity changed")}}
        val journal=journal();assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)}
        p.beforeWrite=null;assertNotEquals(oldId,p.find("main.py")!!.id)
        assertThrows(GitRecoveryRequired::class.java){store(p).recover(journal)};assertEquals("print(2)\n",p.read(p.find("main.py")!!.id))
    }
    @Test fun unknownNewFilePartialIsNotRemovedDuringRollback() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy)
        p.beforeWrite={path,_->if(path=="added/data.bin"){p.bytes[p.find(path)!!.id]=byteArrayOf(88);throw IOException("partial new file")}}
        val journal=journal();assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)}
        assertArrayEquals(byteArrayOf(88),p.readBinary(p.find("added/data.bin")!!.id,100));assertTrue(File(journal,"pending.json").exists())
        assertArrayEquals(byteArrayOf(-1,0,4),zip(s,journal)["after/added/data.bin"])
    }
    @Test fun unacknowledgedDeletionStaysUncertainAndKeepsTheDeletedOriginalBackup() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy)
        p.afterDelete={path->if(path=="remove.bin")throw IOException("delete result lost")}
        val journal=journal();assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)}
        p.afterDelete=null;assertNull(p.find("remove.bin"));assertThrows(GitRecoveryRequired::class.java){store(p).recover(journal)}
        assertArrayEquals(byteArrayOf(0,-1,2),zip(s,journal)["before/remove.bin"]);assertTrue(File(journal,"pending.json").exists())
    }
    @Test fun knownDeletedFileRecreationCanResumeAfterItsRestoreWriteWasInterrupted() {
        val p=provider();val original=p.files();val s=store(p);val copy=snapshot(s);update(copy)
        p.beforeWrite={path,_->if(path==".git/refs/heads/main" || path=="remove.bin")throw IOException("offline during restore")}
        val journal=journal();assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)}
        assertTrue(p.readBinary(p.find("remove.bin")!!.id,100).isEmpty());p.beforeWrite=null;store(p).recover(journal)
        assertEquals(original,p.files());assertFalse(File(journal,"pending.json").exists())
    }
    @Test fun unknownPartialBytesDuringRecreationAreRetainedInsteadOfReplaced() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy)
        p.beforeWrite={path,_->
            if(path==".git/refs/heads/main")throw IOException("ref offline")
            if(path=="remove.bin"){p.bytes[p.find(path)!!.id]=byteArrayOf(98);throw IOException("partial restore")}
        }
        val journal=journal();assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)}
        p.beforeWrite=null;assertThrows(GitRecoveryRequired::class.java){store(p).recover(journal)}
        assertArrayEquals(byteArrayOf(98),p.readBinary(p.find("remove.bin")!!.id,100))
        val exported=zip(s,journal);assertArrayEquals(byteArrayOf(0,-1,2),exported["before/remove.bin"])
        assertArrayEquals(byteArrayOf(98),exported["current/remove.bin"])
    }
    @Test fun providerChangedCreationNameKeepsItsUnknownEntryAndRecoveryRecord() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy);p.changedCreateName={if(it=="added")"unexpected-added" else it}
        val journal=journal();assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)}
        assertNotNull(p.find("unexpected-added"));assertTrue(File(journal,"pending.json").exists())
        assertTrue(s.recoveryDescription(journal).contains("added"))
    }
    @Test fun unrelatedConcurrentFileIsKeptWhileKnownOwnChangesRollBack() {
        val p=provider();val original=p.files();val s=store(p);val copy=snapshot(s);update(copy)
        p.beforeWrite={path,_->if(path==".git/index")p.put("external.bin",byteArrayOf(7,-1))}
        val journal=journal();assertThrows(IllegalArgumentException::class.java){s.publishRemote(copy,plan(copy),journal)}
        assertEquals(original+mapOf("external.bin" to listOf<Byte>(7,-1)),p.files());assertFalse(File(journal,"pending.json").exists())
    }
    @Test fun aSecondStoreCannotRecoverWhileRemotePublicationOwnsTheJournal() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy);val approval=plan(copy);val journal=journal()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val recoveryStarted=CountDownLatch(1);val workers=Executors.newFixedThreadPool(2)
        p.beforeWrite={path,_->if(path=="main.py"){entered.countDown();check(release.await(15,TimeUnit.SECONDS))}}
        try {
            val publish=workers.submit{s.publishRemote(copy,approval,journal)};assertTrue(entered.await(10,TimeUnit.SECONDS))
            val recover=workers.submit{recoveryStarted.countDown();store(p).recover(journal)};assertTrue(recoveryStarted.await(5,TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java){recover.get(150,TimeUnit.MILLISECONDS)}
            release.countDown();publish.get(15,TimeUnit.SECONDS);recover.get(15,TimeUnit.SECONDS)
            assertEquals("print(2)\n",p.read(p.find("main.py")!!.id));assertFalse(File(journal,"pending.json").exists())
        }finally{release.countDown();workers.shutdownNow()}
    }
    private fun fixtureCases():JSONArray {
        val root=generateSequence(File(checkNotNull(System.getProperty("user.dir")))){it.parentFile}
            .first{File(it,"verification/git-remote-workspace-2026-11-02/native-engine-fixtures.json").isFile}
        return JSONObject(File(root,"verification/git-remote-workspace-2026-11-02/native-engine-fixtures.json").readText()).getJSONArray("cases")
    }
    private fun restore(root:File,tree:JSONObject) {
        val dirs=tree.getJSONArray("directories")
        (0 until dirs.length()).map{dirs.getString(it)}.filter{it.isNotEmpty()}.sortedBy{it.count{c->c=='/'}}
            .forEach{File(root,it).let{folder->check(folder.isDirectory || folder.mkdirs())}}
        val files=tree.getJSONObject("files");files.keys().forEach{put(root,it,java.util.Base64.getDecoder().decode(files.getString(it)))}
    }
    private fun engineSnapshot(store:GitWorkspaceStore,case:JSONObject):Pair<GitWorkspaceSnapshot,GitRemotePlan> {
        val copy=snapshot(store);val owned=copy.root.canonicalPath+File.separator
        copy.root.walkBottomUp().filter{it!=copy.root}.forEach{check(it.canonicalPath.startsWith(owned));check(it.delete())}
        restore(copy.root,case.getJSONObject("after"));val json=case.getJSONObject("plan").put("root",copy.root.absolutePath)
        File(copy.root.parentFile,"result.plan").writeText(json.toString())
        val request=case.getJSONObject("request");val response=case.getJSONObject("response")
        return copy to GitRemotePlan.read(copy,request.getString("action"),request.getString("expected"),response.getString("snapshot"),request.getString("url"),request.getString("branch"))
    }
    @Test fun actualEngineCloneFetchFastForwardAndConfigurePublishThroughOpaqueBinaryProvider() {
        val cases=fixtureCases()
        for(i in 0 until cases.length()) {
            val case=cases.getJSONObject(i);val p=Provider();val before=case.getJSONObject("before");val dirs=before.getJSONArray("directories")
            (0 until dirs.length()).map{dirs.getString(it)}.filter{it.isNotEmpty()}.sortedBy{it.count{c->c=='/'}}.forEach{path->
                val parent=path.substringBeforeLast('/',"");p.create(if(parent.isEmpty())p.root.id else p.find(parent)!!.id,path.substringAfterLast('/'),true)
            }
            before.getJSONObject("files").let{files->files.keys().forEach{p.put(it,java.util.Base64.getDecoder().decode(files.getString(it)))}}
            val s=store(p);val (copy,approval)=engineSnapshot(s,case);val journal=journal();s.publishRemote(copy,approval,journal)
            val expected=case.getJSONObject("after");val files=expected.getJSONObject("files")
            assertEquals(case.getString("name"),files.keys().asSequence().associateWith{java.util.Base64.getDecoder().decode(files.getString(it)).toList()},p.files())
            assertEquals((0 until expected.getJSONArray("directories").length()).map{expected.getJSONArray("directories").getString(it)}.toSet(),p.dirs())
            store(p).recover(journal);assertFalse(File(journal,"pending.json").exists())
        }
    }
    @Test fun actualCloneUsesStandardRemoteAndPackedRefsLocksUntilObjectsBodiesAndRefsAreVerified() {
        val case=fixtureCases().getJSONObject(0);assertEquals("clone",case.getString("name"));val original=temp.newFolder("internal")
        val writes=mutableListOf<String>()
        val gateway=FileGitGateway(original){from,to->
            val path=to.relativeTo(original).invariantSeparatorsPath
            assertTrue(File(original,".git/index.lock").isFile);assertTrue(File(original,".git/packed-refs.lock").isFile)
            assertTrue(File(original,".git/refs/remotes/origin/main.lock").isFile)
            assertTrue(File(original,".git/refs/heads/main.lock").isFile)
            writes.add(path);replace(from,to)
        }
        val s=GitWorkspaceStore(gateway,false,::replace);val (copy,approval)=engineSnapshot(s,case);val journal=journal()
        s.publishRemote(copy,approval,journal)
        val firstObject=writes.indexOfFirst{it.startsWith(".git/objects/")}
        assertTrue(firstObject>=0 && firstObject<writes.indexOf("main.py"));assertTrue(writes.indexOf("main.py")<writes.indexOf(".git/index"))
        assertEquals(".git/HEAD",writes.last());assertArrayEquals(java.util.Base64.getDecoder().decode(case.getJSONObject("after").getJSONObject("files").getString("main.py")),File(original,"main.py").readBytes())
        assertTrue(original.walkTopDown().none{it.name.endsWith(".lock")});s.recover(journal)
    }
    @Test fun restartReleasesOnlyItsRecordedRemoteLocksAndKeepsTheCommittedFiles() {
        val case=fixtureCases().getJSONObject(0);val original=temp.newFolder("internal")
        val s=GitWorkspaceStore(FileGitGateway(original,::replace),false,::replace);val (copy,approval)=engineSnapshot(s,case);val journal=journal()
        s.publishRemote(copy,approval,journal);val json=JSONObject(File(journal,"pending.json").readText());val locks=json.getJSONObject("metadata_locks")
        val paths=locks.getJSONArray("paths");for(i in 0 until paths.length())put(original,paths.getString(i),locks.getString("token"))
        GitWorkspaceStore(FileGitGateway(original,::replace),false,::replace).recover(journal)
        assertTrue(original.walkTopDown().none{it.name.endsWith(".lock")});assertArrayEquals(java.util.Base64.getDecoder().decode(case.getJSONObject("after").getJSONObject("files").getString("main.py")),File(original,"main.py").readBytes())
    }
    @Test fun foreignReplacementOfRecordedRemoteLockPreservesItAndTheRecoveryBackup() {
        val case=fixtureCases().getJSONObject(0);val original=temp.newFolder("internal")
        val s=GitWorkspaceStore(FileGitGateway(original,::replace),false,::replace);val (copy,approval)=engineSnapshot(s,case);val journal=journal()
        s.publishRemote(copy,approval,journal);val lock=put(original,".git/refs/remotes/origin/main.lock","foreign-owner")
        assertThrows(GitRecoveryRequired::class.java){GitWorkspaceStore(FileGitGateway(original,::replace),false,::replace).recover(journal)}
        assertEquals("foreign-owner",lock.readText());assertTrue(File(journal,"pending.json").exists())
        assertArrayEquals(java.util.Base64.getDecoder().decode(case.getJSONObject("after").getJSONObject("files").getString("main.py")),File(original,"main.py").readBytes())
    }
    @Test fun recoveryScopeAndUnknownJournalVersionsCannotPublishOrDeleteAnything() {
        val p=provider();val s=store(p);val copy=snapshot(s);update(copy);val journal=journal()
        File(journal,"pending.json").writeText(JSONObject().put("version",9).toString())
        assertThrows(GitRecoveryRequired::class.java){s.publishRemote(copy,plan(copy),journal)};assertEquals(0,p.writes)
        File(journal,"pending.json").writeText(JSONObject().put("version",2).put("kind","remote").put("scope","foreign").toString())
        assertThrows(GitRecoveryRequired::class.java){s.recover(journal)};assertEquals(0,p.writes);assertTrue(File(journal,"pending.json").exists())
    }
}
