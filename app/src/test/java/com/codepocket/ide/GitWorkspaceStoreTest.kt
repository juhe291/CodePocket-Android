package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class GitWorkspaceStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private class Provider:DocumentGateway {
        override val root=DocumentNode("provider-root","project",true)
        val nodes=linkedMapOf(root.id to root);val parents=mutableMapOf<String,String>();val bytes=mutableMapOf<String,ByteArray>()
        var fail:String?=null;var textWrites=0;var binaryWrites=0
        var beforeBinaryWrite:((String,ByteArray)->Unit)?=null
        val binaryTrace=mutableListOf<String>()
        override fun children(id:String)=nodes.values.filter{parents[it.id]==id}
        override fun read(id:String)=readBytes(id).toString(Charsets.UTF_8)
        override fun readBytes(id:String)=readBinary(id,1_000_000)
        override fun readBinary(id:String,limit:Int):ByteArray {
            if(id==fail)throw IOException("provider disconnected")
            return bytes.getValue(id).copyOf().also{require(it.size<=limit)}
        }
        override fun write(id:String,text:String){textWrites++;bytes[id]=text.toByteArray()}
        override fun writeBytes(id:String,bytes:ByteArray){binaryWrites++;binaryTrace.add(nodes.getValue(id).name);beforeBinaryWrite?.invoke(id,bytes);this.bytes[id]=bytes.copyOf()}
        override fun create(parent:String,name:String,directory:Boolean):DocumentNode=DocumentNode("opaque-${nodes.size}",name,directory).also{
            nodes[it.id]=it;parents[it.id]=parent;if(!directory)bytes[it.id]=byteArrayOf()
        }
        override fun rename(id:String,name:String):DocumentNode=error("not used")
        override fun move(id:String,oldParent:String,newParent:String):DocumentNode=error("not used")
        override fun delete(id:String){nodes.remove(id);parents.remove(id);bytes.remove(id)}
        fun put(path:String,value:ByteArray):DocumentNode {
            var parent=root
            val parts=path.split('/')
            parts.dropLast(1).forEach{name->parent=children(parent.id).singleOrNull{it.name==name}?:create(parent.id,name,true)}
            val file=children(parent.id).singleOrNull{it.name==parts.last()}?:create(parent.id,parts.last(),false)
            bytes[file.id]=value.copyOf();return file
        }
    }
    private fun job(name:String="job")=File(temp.root,name)
    private fun replace(from:File,to:File){from.copyTo(to,true);check(from.delete())}
    private fun transactional(p:Provider)=GitWorkspaceStore(p,false,::replace)
    @Test fun freshBinaryProviderSnapshotNeverUsesTheOldEditorMirrorOrTextWriter(){
        val p=Provider();p.put("main.py","old=1\n".toByteArray());val mirror=temp.newFolder("mirror")
        val tree=DocumentTree(p,temp.newFolder("recovery"));tree.scan(mirror)
        p.put("main.py","fresh=2\n".toByteArray());val binary=ByteArray(1_200_000){(it%256).toByte()};p.put(".git/objects/aa/object",binary)
        val store=GitWorkspaceStore(p);val snapshot=store.capture(job())
        assertEquals("fresh=2\n",File(snapshot.root,"main.py").readText());assertEquals("old=1\n",File(mirror,"main.py").readText())
        assertArrayEquals(binary,File(snapshot.root,".git/objects/aa/object").readBytes())
        assertArrayEquals(binary,File(snapshot.beforeRoot,".git/objects/aa/object").readBytes())
        assertEquals(0,p.textWrites);assertEquals(0,p.binaryWrites);store.assertFresh(snapshot)
    }
    @Test fun staleProviderContentOrMetadataFailsBeforePublishingAnything(){
        val p=Provider();p.put("main.py","one\n".toByteArray());p.put(".git/index",byteArrayOf(0,1,2))
        val store=GitWorkspaceStore(p);val snapshot=store.capture(job())
        p.put("main.py","external update\n".toByteArray())
        assertThrows(IllegalArgumentException::class.java){store.assertFresh(snapshot)}
        assertEquals("one\n",File(snapshot.root,"main.py").readText());assertEquals(0,p.binaryWrites)
        p.put("main.py","one\n".toByteArray());p.put(".git/index",byteArrayOf(9,8,7))
        assertThrows(IllegalArgumentException::class.java){store.assertFresh(snapshot)}
    }
    @Test fun changedMetadataIsExactAndWorkingFileChangesAreRejected(){
        val p=Provider();p.put("main.py","one\n".toByteArray());p.put(".git/index",byteArrayOf(0,1))
        val store=GitWorkspaceStore(p);val snapshot=store.capture(job())
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(2,3,-1))
        val changes=store.metadataChanges(snapshot)
        assertEquals(listOf(".git/index"),changes.map{it.path});assertEquals(GitWorkspaceStore.digest(byteArrayOf(0,1)),changes.single().before!!.sha256)
        assertEquals(GitWorkspaceStore.digest(byteArrayOf(2,3,-1)),changes.single().after!!.sha256)
        File(snapshot.root,"main.py").writeText("must not publish")
        assertThrows(IllegalArgumentException::class.java){store.metadataChanges(snapshot)};assertEquals(0,p.binaryWrites)
    }
    @Test fun failedCaptureDeletesOnlyItsOwnedJobAndKeepsOtherFiles(){
        val p=Provider();p.put("a.py","keep\n".toByteArray());p.fail=p.put("z.bin",byteArrayOf(1)).id
        val sentinel=temp.newFile("unrelated.txt").apply{writeText("keep")};val store=GitWorkspaceStore(p)
        assertThrows(IOException::class.java){store.capture(job())}
        assertFalse(job().exists());assertEquals("keep",sentinel.readText());assertEquals(0,p.binaryWrites)
        val existing=temp.newFolder("existing");File(existing,"keep.txt").writeText("keep")
        assertThrows(IllegalArgumentException::class.java){store.capture(existing)};assertEquals("keep",File(existing,"keep.txt").readText())
    }
    @Test fun cyclesAndDuplicateIdsAreRejectedWithoutMutatingProvider(){
        val p=Provider();p.parents[p.root.id]=p.root.id
        assertThrows(IllegalArgumentException::class.java){GitWorkspaceStore(p).capture(job())}
        assertFalse(job().exists());assertEquals(0,p.binaryWrites);assertEquals(0,p.textWrites)
    }
    @Test fun gitMetadataIsNotLoadedAsEditorTextOrCopiedIntoAnExternalRun(){
        val p=Provider();p.put("main.py","print(1)\n".toByteArray())
        repeat(1005){p.put(".git/objects/aa/blob-$it",byteArrayOf(0,-1))}
        val mirror=temp.newFolder("mirror");val tree=DocumentTree(p,temp.newFolder("recovery"));tree.scan(mirror)
        assertEquals(listOf("main.py"),tree.children(""));assertFalse(File(mirror,".git").exists())
        val run=File(temp.root,"run");tree.copyForRun(run)
        assertEquals("print(1)\n",File(run,"main.py").readText());assertFalse(File(run,".git").exists())
        val capture=GitWorkspaceStore(p).capture(job());assertTrue(capture.before.files.size>1000)
    }
    @Test fun privateFilesystemAdapterWritesExactBinaryBytesAndDoesNotLeaveTemporaryFiles(){
        val root=temp.newFolder("workspace");val target=File(root,"index").apply{writeBytes(byteArrayOf(1))}
        val gateway=FileGitGateway(root){from,to->from.copyTo(to,true);check(from.delete())}
        gateway.writeBytes("index",byteArrayOf(0,-1,2));assertArrayEquals(byteArrayOf(0,-1,2),target.readBytes())
        assertArrayEquals(byteArrayOf(0,-1,2),gateway.readBinary("index",10))
        assertTrue(temp.root.listFiles()!!.none{it.name.startsWith(".codepocket-git-")})
        assertThrows(IllegalArgumentException::class.java){gateway.readBinary("../outside",10)}
        assertThrows(IllegalArgumentException::class.java){gateway.delete("")}
    }
    @Test fun publishesOnlyExactMetadataAndKeepsRefsAfterObjectsAndIndex(){
        val p=Provider();p.put("main.py","source\n".toByteArray());p.put(".git/index",byteArrayOf(1))
        p.put(".git/refs/heads/main","oldref".toByteArray());p.put(".git/HEAD","oldhead".toByteArray())
        val store=transactional(p);val snapshot=store.capture(job());val journal=temp.newFolder("journal")
        val objectName="b".repeat(38);val objectPath=".git/objects/aa/$objectName"
        File(snapshot.root,".git/objects/aa").mkdirs();File(snapshot.root,objectPath).writeBytes(byteArrayOf(0,-1,2))
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(3,4));File(snapshot.root,".git/refs/heads/main").writeText("newref")
        File(snapshot.root,".git/HEAD").writeText("newhead");store.publish(snapshot,journal)
        assertEquals(listOf(objectName,"index","main","HEAD"),p.binaryTrace)
        assertArrayEquals(byteArrayOf(0,-1,2),p.bytes.getValue(p.nodes.values.single{it.name==objectName}.id))
        assertEquals("source\n",p.read(p.nodes.values.single{it.name=="main.py"}.id));assertEquals(0,p.textWrites)
        assertTrue(File(journal,"pending.json").exists());val count=p.binaryWrites;store.recover(journal)
        assertFalse(File(journal,"pending.json").exists());assertEquals(count,p.binaryWrites)
        assertArrayEquals(byteArrayOf(3,4),p.bytes.getValue(p.nodes.values.single{it.name=="index"}.id))
    }
    @Test fun staleWorkspaceRejectsPublicationBeforeAnyProviderWrites(){
        val p=Provider();val file=p.put("main.py","old\n".toByteArray());p.put(".git/index",byteArrayOf(1))
        val store=transactional(p);val snapshot=store.capture(job());File(snapshot.root,".git/index").writeBytes(byteArrayOf(2))
        p.bytes[file.id]="external\n".toByteArray();val journal=temp.newFolder("journal")
        assertThrows(IllegalArgumentException::class.java){store.publish(snapshot,journal)}
        assertEquals(0,p.binaryWrites);assertFalse(File(journal,"pending.json").exists());assertEquals("external\n",p.read(file.id))
    }
    @Test fun laterWriteFailureRollsBackKnownOwnIndexBytesAndLeavesSourceUntouched(){
        val p=Provider();val index=p.put(".git/index",byteArrayOf(1,2));val head=p.put(".git/HEAD","old".toByteArray())
        val source=p.put("main.py","source\n".toByteArray());val store=transactional(p);val snapshot=store.capture(job())
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(3,4));File(snapshot.root,".git/HEAD").writeText("new")
        p.beforeBinaryWrite={id,_->if(id==head.id)throw IOException("provider write denied")}
        val journal=temp.newFolder("journal");assertThrows(IOException::class.java){store.publish(snapshot,journal)}
        assertArrayEquals(byteArrayOf(1,2),p.bytes.getValue(index.id));assertEquals("old",p.read(head.id));assertEquals("source\n",p.read(source.id))
        assertFalse(File(journal,"pending.json").exists())
    }
    @Test fun aNewStoreCanRecoverKnownOwnWritesAfterAnInterruptedRollback(){
        val p=Provider();val index=p.put(".git/index",byteArrayOf(1));val head=p.put(".git/HEAD","old".toByteArray())
        val store=transactional(p);val snapshot=store.capture(job());File(snapshot.root,".git/index").writeBytes(byteArrayOf(2))
        File(snapshot.root,".git/HEAD").writeText("new")
        p.beforeBinaryWrite={id,data->if(id==head.id || id==index.id && data.contentEquals(byteArrayOf(1)))throw IOException("provider offline")}
        val journal=temp.newFolder("journal");assertThrows(GitRecoveryRequired::class.java){store.publish(snapshot,journal)}
        assertTrue(File(journal,"pending.json").exists());assertArrayEquals(byteArrayOf(2),p.bytes.getValue(index.id))
        p.beforeBinaryWrite=null;transactional(p).recover(journal)
        assertArrayEquals(byteArrayOf(1),p.bytes.getValue(index.id));assertEquals("old",p.read(head.id));assertFalse(File(journal,"pending.json").exists())
    }
    @Test fun unknownPartialOrConcurrentMetadataIsNeverOverwrittenDuringRecovery(){
        val p=Provider();val index=p.put(".git/index",byteArrayOf(1,2));val source=p.put("main.py","source\n".toByteArray())
        val store=transactional(p);val snapshot=store.capture(job());File(snapshot.root,".git/index").writeBytes(byteArrayOf(3,4))
        p.beforeBinaryWrite={id,_->if(id==index.id){p.bytes[id]=byteArrayOf(99);throw IOException("partial write")}}
        val journal=temp.newFolder("journal");assertThrows(GitRecoveryRequired::class.java){store.publish(snapshot,journal)}
        val folder=journal.listFiles()!!.single{it.isDirectory}
        assertArrayEquals(byteArrayOf(1,2),File(folder,"before/.git/index").readBytes());assertArrayEquals(byteArrayOf(99),p.bytes.getValue(index.id))
        p.beforeBinaryWrite=null;assertThrows(GitRecoveryRequired::class.java){transactional(p).recover(journal)}
        assertArrayEquals(byteArrayOf(99),p.bytes.getValue(index.id));assertEquals("source\n",p.read(source.id));assertTrue(File(journal,"pending.json").exists())
        val output=java.io.ByteArrayOutputStream();store.exportRecovery(journal,output)
        val zip=java.util.zip.ZipInputStream(output.toByteArray().inputStream());val exported=linkedMapOf<String,ByteArray>()
        zip.use{while(true){val entry=it.nextEntry?:break;exported[entry.name]=it.readBytes()}}
        assertEquals(setOf("pending.json","before/.git/index","after/.git/index","current/.git/index"),exported.keys)
        assertArrayEquals(byteArrayOf(1,2),exported["before/.git/index"]);assertArrayEquals(byteArrayOf(3,4),exported["after/.git/index"])
        assertArrayEquals(byteArrayOf(99),exported["current/.git/index"]);assertTrue(store.recoveryDescription(journal).contains(".git/index"))
        assertArrayEquals(byteArrayOf(99),p.bytes.getValue(index.id));assertEquals("source\n",p.read(source.id))
    }
    @Test fun changedImmutableObjectsOrConfigurationAreRejectedBeforeWriting(){
        val p=Provider();val objectPath=".git/objects/aa/"+"b".repeat(38);p.put(objectPath,byteArrayOf(1));p.put(".git/config","original".toByteArray())
        val store=transactional(p);val snapshot=store.capture(job());File(snapshot.root,objectPath).writeBytes(byteArrayOf(2))
        val journal=temp.newFolder("journal");assertThrows(IllegalArgumentException::class.java){store.publish(snapshot,journal)}
        File(snapshot.root,objectPath).writeBytes(byteArrayOf(1));File(snapshot.root,".git/config").writeText("not allowed")
        assertThrows(IllegalArgumentException::class.java){store.publish(snapshot,journal)};assertEquals(0,p.binaryWrites)
    }
    @Test fun committedCleanupCanResumeWhenItsPrivateArtifactFolderIsAlreadyGone(){
        val p=Provider();val index=p.put(".git/index",byteArrayOf(1));val store=transactional(p);val snapshot=store.capture(job())
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(2));val journal=temp.newFolder("journal");store.publish(snapshot,journal)
        val folder=journal.listFiles()!!.single{it.isDirectory}
        require(folder.canonicalPath.startsWith(journal.canonicalPath+File.separator))
        folder.walkBottomUp().forEach{check(it.delete())}
        transactional(p).recover(journal)
        assertFalse(File(journal,"pending.json").exists());assertArrayEquals(byteArrayOf(2),p.bytes.getValue(index.id))
    }
    @Test fun anotherStoreCannotRecoverAnActivePublicationOnTheSameJournal(){
        val p=Provider();val index=p.put(".git/index",byteArrayOf(1));val store=transactional(p);val snapshot=store.capture(job())
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(2));val journal=temp.newFolder("journal")
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val recoveryStarted=CountDownLatch(1)
        p.beforeBinaryWrite={id,_->if(id==index.id){entered.countDown();check(release.await(10,TimeUnit.SECONDS))}}
        val workers=Executors.newFixedThreadPool(2)
        try {
            val publish=workers.submit{store.publish(snapshot,journal)};assertTrue(entered.await(5,TimeUnit.SECONDS))
            val recover=workers.submit{recoveryStarted.countDown();transactional(p).recover(journal)}
            assertTrue(recoveryStarted.await(5,TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java){recover.get(100,TimeUnit.MILLISECONDS)}
            release.countDown();publish.get(10,TimeUnit.SECONDS);recover.get(10,TimeUnit.SECONDS)
            assertArrayEquals(byteArrayOf(2),p.bytes.getValue(index.id));assertFalse(File(journal,"pending.json").exists())
        } finally {release.countDown();workers.shutdownNow()}
    }
    @Test fun privatePublicationHoldsStandardGitLocksUntilAllMetadataIsVerified(){
        val root=temp.newFolder("real");File(root,".git/refs/heads").mkdirs();File(root,".git/index").writeBytes(byteArrayOf(1))
        File(root,".git/HEAD").writeText("ref: refs/heads/main\n");File(root,".git/refs/heads/main").writeText("old")
        val source=File(root,"main.py").apply{writeText("source\n")};var checks=0
        val gateway=FileGitGateway(root){from,to->
            for(path in listOf("index","HEAD","config","refs/heads/main"))assertTrue(File(root,".git/$path.lock").isFile)
            assertFalse("Another standard Git writer must not acquire the index",File(root,".git/index.lock").createNewFile())
            checks++;replace(from,to)
        }
        val store=GitWorkspaceStore(gateway,false,::replace);val snapshot=store.capture(job())
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(2));File(snapshot.root,".git/refs/heads/main").writeText("new")
        store.publish(snapshot,temp.newFolder("journal"));assertEquals(2,checks)
        assertTrue(File(root,".git").walkTopDown().none{it.name.endsWith(".lock")});assertEquals("source\n",source.readText())
    }
    @Test fun foreignStandardGitLocksArePreservedAndNoMetadataIsPublished(){
        val root=temp.newFolder("real");File(root,".git").mkdir();val index=File(root,".git/index").apply{writeBytes(byteArrayOf(1))}
        val store=GitWorkspaceStore(FileGitGateway(root,::replace),false,::replace);val snapshot=store.capture(job())
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(2));val lock=File(root,".git/index.lock").apply{writeText("foreign")}
        assertThrows(IllegalArgumentException::class.java){store.publish(snapshot,temp.newFolder("journal"))}
        assertArrayEquals(byteArrayOf(1),index.readBytes());assertEquals("foreign",lock.readText())
    }
    @Test fun restartReleasesOnlyJournalProvenLocksAndRetainsTheCompletedCommit(){
        val root=temp.newFolder("real");File(root,".git").mkdir();val index=File(root,".git/index").apply{writeBytes(byteArrayOf(1))}
        val store=GitWorkspaceStore(FileGitGateway(root,::replace),false,::replace);val snapshot=store.capture(job())
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(2));val journal=temp.newFolder("journal");store.publish(snapshot,journal)
        val record=org.json.JSONObject(File(journal,"pending.json").readText());val locks=record.getJSONObject("metadata_locks")
        val paths=locks.getJSONArray("paths");for(i in 0 until paths.length())File(root,paths.getString(i)).writeText(locks.getString("token"))
        GitWorkspaceStore(FileGitGateway(root,::replace),false,::replace).recover(journal)
        assertArrayEquals(byteArrayOf(2),index.readBytes());assertFalse(File(journal,"pending.json").exists())
        assertTrue(File(root,".git").walkTopDown().none{it.name.endsWith(".lock")})
    }
    @Test fun restartNeverDeletesAForeignReplacementOfARecordedLock(){
        val root=temp.newFolder("real");File(root,".git").mkdir();val index=File(root,".git/index").apply{writeBytes(byteArrayOf(1))}
        val store=GitWorkspaceStore(FileGitGateway(root,::replace),false,::replace);val snapshot=store.capture(job())
        File(snapshot.root,".git/index").writeBytes(byteArrayOf(2));val journal=temp.newFolder("journal");store.publish(snapshot,journal)
        val lock=File(root,".git/index.lock").apply{writeText("foreign")}
        assertThrows(GitRecoveryRequired::class.java){GitWorkspaceStore(FileGitGateway(root,::replace),false,::replace).recover(journal)}
        assertEquals("foreign",lock.readText());assertArrayEquals(byteArrayOf(2),index.readBytes());assertTrue(File(journal,"pending.json").exists())
    }
}
