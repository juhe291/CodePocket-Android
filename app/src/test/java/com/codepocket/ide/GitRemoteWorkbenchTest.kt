package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import android.os.Looper
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class]) @LooperMode(LooperMode.Mode.PAUSED)
class GitRemoteWorkbenchTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun <T> background(work:()->T):T {
        val executor=Executors.newSingleThreadExecutor();val task=executor.submit<T>{work()}
        try {
            val deadline=System.nanoTime()+90_000_000_000L
            while(!task.isDone && System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5)}
            return task.get(1,TimeUnit.SECONDS)
        }finally{task.cancel(true);executor.shutdownNow()}
    }
    private fun backend(channel:HostRemoteGit.Channel)=GitRemoteWorkbench(app,GitRemoteClient(channel),GitPushLedger(app))
    private fun files(root:File)=root.walkTopDown().filter{it.isFile}.associate{it.relativeTo(root).invariantSeparatorsPath to it.readBytes().toList()}
    private fun fields(server:HostRemoteGit.Server)=JSONObject().put("url",server.url).put("branch","main")
    private fun clone(server:HostRemoteGit.Server,backend:GitRemoteWorkbench):Pair<Workspace,GitRemoteRun> {
        val targets=GitCloneTargets(app);val target=targets.createPrivate("测试克隆")
        val result=background{backend.perform(target.store,target.identity,"clone",fields(server),credentials=server.credentials)}
        assertTrue(result.toString(),result.ok);assertTrue(result.published);targets.completed(target)
        return targets.open(target.selector) to result
    }
    private fun snapshot(backend:GitRemoteWorkbench,workspace:Workspace)=background{backend.perform(workspace,"snapshot")}.also{assertTrue(it.toString(),it.ok)}
    private fun fetched(server:HostRemoteGit.Server,backend:GitRemoteWorkbench,workspace:Workspace)=background{
        backend.perform(workspace,"fetch",fields(server),credentials=server.credentials)
    }.also{assertTrue(it.toString(),it.ok)}
    private fun guard()=GitRemoteWriteGuard({true},{emptyList()})
    private fun fastForward(server:HostRemoteGit.Server,backend:GitRemoteWorkbench,workspace:Workspace,fetch:GitRemoteRun,guard:GitRemoteWriteGuard=guard()):GitRemoteRun=background {
        backend.perform(workspace,"fast_forward",fields(server).put("expected_head",fetch.reply.state!!.head).put("expected_remote",fetch.reply.remote!!.head),
            fetch.reply.state.snapshot,guard=guard)
    }
    @Test fun actualTlsClonePublishesItsOwnInternalProjectAndStandardGitReadsIt() {
        val original=Workspace(app,false);File(original.root,"keep.py").writeText("default kept")
        HostRemoteGit.Server().use{server->HostRemoteGit.Channel().use{channel->backend(channel).use{backend->
            val (workspace,result)=clone(server,backend)
            assertNotEquals(original.identity,workspace.identity);assertNotEquals(original.root,workspace.root)
            assertEquals(server.firstHead,result.reply.state!!.head);assertEquals("default kept",File(original.root,"keep.py").readText())
            assertFalse(File(workspace.root,"main.cpp").exists());assertEquals(setOf("main.py","helper.py","remove.bin"),workspace.children(workspace.root).map{it.name}.toSet())
            // Git for Windows rejects Robolectric's >260-character dataDir before opening the repo.
            // Compare every actual published byte, then inspect that exact replica at a short owned path.
            val probe=File(HostRemoteGit.repository,".tools/git-remote-probe/fsck-${UUID.randomUUID()}")
            assertTrue(workspace.root.copyRecursively(probe));assertEquals(files(workspace.root),files(probe))
            val fsck=ProcessBuilder("git","-C",probe.path,"fsck","--full").redirectErrorStream(true).start()
            val text=fsck.inputStream.bufferedReader().readText();assertTrue(fsck.waitFor(10,TimeUnit.SECONDS));assertEquals(text,0,fsck.exitValue())
            val reopened=GitCloneTargets(app).open(workspace.identity);assertEquals(workspace.root,reopened.root);assertEquals(workspace.identity,reopened.identity)
        }}}
    }
    @Test fun realFetchOnlyPublishesObjectsAndTrackingRefThenFastForwardChangesBodies() {
        HostRemoteGit.Server().use{server->HostRemoteGit.Channel().use{channel->backend(channel).use{backend->
            val (workspace,_)=clone(server,backend);val source=File(workspace.root,"main.py").readBytes();val index=File(workspace.root,".git/index").readBytes()
            val oldHead=File(workspace.root,".git/refs/heads/main").readBytes()
            val head=server.advance(mapOf("main.py" to "print(2)\n".toByteArray(),"remove.bin" to null,"新增/data.bin" to byteArrayOf(0,-1,4)))
            val fetch=fetched(server,backend,workspace);assertEquals(head,fetch.reply.remote!!.head)
            assertArrayEquals(source,File(workspace.root,"main.py").readBytes());assertArrayEquals(index,File(workspace.root,".git/index").readBytes())
            assertArrayEquals(oldHead,File(workspace.root,".git/refs/heads/main").readBytes());assertTrue(File(workspace.root,"remove.bin").exists())
            val update=fastForward(server,backend,workspace,fetch);assertTrue(update.toString(),update.ok);assertTrue(update.published)
            assertEquals("print(2)\n",File(workspace.root,"main.py").readText());assertFalse(File(workspace.root,"remove.bin").exists())
            assertArrayEquals(byteArrayOf(0,-1,4),File(workspace.root,"新增/data.bin").readBytes())
        }}}
    }
    @Test fun anActiveRecoveryDraftBlocksBodyPublicationAndOtherWorkspaceDraftsRemainIndependent() {
        HostRemoteGit.Server().use{server->HostRemoteGit.Channel().use{channel->backend(channel).use{backend->
            val (workspace,_)=clone(server,backend);server.advance(mapOf("main.py" to "print(2)\n".toByteArray()));val fetch=fetched(server,backend,workspace)
            val sessions=EditorSessions(workspace.identity);val key=sessions.key("helper.py")
            val drafts=DraftStore(File(app.filesDir,"drafts-test-${UUID.randomUUID()}")){a,b->android.system.Os.rename(a.path,b.path)}
            drafts.put(key,"helper.py","unsaved recovery");drafts.put("another-workspace:key","other.py","elsewhere")
            val before=files(workspace.root);val rejected=fastForward(server,backend,workspace,fetch,GitRemoteWriteGuard.drafts(sessions.analysisKeys(),drafts){true})
            assertEquals("drafts_pending",rejected.error);assertEquals(listOf("helper.py"),rejected.draftPaths);assertEquals(before,files(workspace.root))
            assertEquals("unsaved recovery",drafts.read(key)!!.text)
            drafts.removeIfMatches(key,"unsaved recovery")
            val accepted=fastForward(server,backend,workspace,fetch,GitRemoteWriteGuard.drafts(sessions.analysisKeys(),drafts){true})
            assertTrue(accepted.toString(),accepted.ok);assertEquals("elsewhere",drafts.read("another-workspace:key")!!.text)
        }}}
    }
    @Test fun aLateEditorChangeAfterEngineCheckoutPreventsAnyOriginalBodyUpdate() {
        HostRemoteGit.Server().use{server->HostRemoteGit.Channel().use{channel->backend(channel).use{backend->
            val (workspace,_)=clone(server,backend);server.advance(mapOf("main.py" to "print(2)\n".toByteArray()));val fetch=fetched(server,backend,workspace)
            val valid=AtomicBoolean(true);channel.after={request,_->if(request.optString("action")=="fast_forward")valid.set(false)}
            val before=files(workspace.root);val result=fastForward(server,backend,workspace,fetch,GitRemoteWriteGuard({valid.get()},{emptyList()}))
            assertEquals("editor_changed",result.error);assertEquals(before,files(workspace.root))
        }}}
    }
    @Test fun staleConfirmedSnapshotRejectsBeforeConnectingAndKeepsTheExternalChange() {
        HostRemoteGit.Server().use{server->HostRemoteGit.Channel().use{channel->backend(channel).use{backend->
            val (workspace,_)=clone(server,backend);val confirmed=snapshot(backend,workspace).reply.state!!.snapshot
            File(workspace.root,"main.py").writeText("external update");val before=files(workspace.root);val count=server.requests()
            val result=background{backend.perform(workspace,"fetch",fields(server),confirmed,server.credentials)}
            assertEquals("stale",result.error);assertEquals(before,files(workspace.root));assertEquals(count,server.requests())
        }}}
    }
    @Test fun actualPushPersistsItsReceiptEvenWhenTheUiGenerationBecomesStale() {
        HostRemoteGit.Server().use{server->HostRemoteGit.Channel().use{channel->backend(channel).use{backend->
            val (workspace,_)=clone(server,backend);File(workspace.root,"main.py").writeText("print(3)\n")
            var state=JSONObject(HostGitBackend.run(workspace.root,JSONObject().put("action","status").toString()))
            state=JSONObject(HostGitBackend.run(workspace.root,JSONObject().put("action","stage").put("paths",JSONArray().put("main.py")).put("expected",state.getString("snapshot")).toString()))
            state=JSONObject(HostGitBackend.run(workspace.root,JSONObject().put("action","commit").put("expected",state.getString("snapshot"))
                .put("name","Native test").put("email","native@example.invalid").put("message","Real native push").toString()))
            val valid=AtomicBoolean(true);channel.after={request,_->if(request.optString("action")=="push")valid.set(false)}
            val before=files(workspace.root)
            val result=background{backend.perform(workspace,"push",fields(server).put("expected_head",state.getString("head")).put("expected_remote",server.firstHead),
                state.getString("snapshot"),server.credentials,cancelled={!valid.get()})}
            assertTrue(result.toString(),result.ok);assertEquals("pushed",result.receipt!!.state);assertEquals(state.getString("head"),server.head())
            assertEquals(before,files(workspace.root));assertEquals("pushed",background{backend.receipts(workspace.identity)}.first().state)
        }}}
    }
    @Test fun remoteBranchQueryNeedsNoOpenProjectAndNeverChangesTheDefaultProject() {
        val original=Workspace(app,false);File(original.root,"keep.txt").writeText("keep");val before=files(original.root)
        HostRemoteGit.Server().use{server->HostRemoteGit.Channel().use{channel->backend(channel).use{backend->
            val result=background{backend.branches(server.url,server.credentials)}
            assertTrue(result.toString(),result.ok);assertEquals(server.firstHead,result.reply.remote!!.branches["main"]);assertEquals(before,files(original.root))
        }}}
    }
    @Test fun realSafCloneTargetsOnlyTheChosenEmptyProviderAndCanBeOpenedAfterPublication() {
        val (provider,_)=SafGitBinaryIntegrationTest().provider();val targets=GitCloneTargets(app);val target=targets.fromTree(provider.tree)
        HostRemoteGit.Server().use{server->HostRemoteGit.Channel().use{channel->backend(channel).use{backend->
            val result=background{backend.perform(target.store,target.identity,"clone",fields(server),credentials=server.credentials)}
            assertTrue(result.toString(),result.ok);assertTrue(provider.writeCalls>0)
            val workspace=Workspace.fromTree(app,provider.tree);assertEquals(target.identity,workspace.identity)
            assertEquals(setOf("main.py","helper.py","remove.bin"),workspace.children(workspace.root).map{it.name}.toSet())
            assertArrayEquals(byteArrayOf(0,-1,2),File(provider.disk,"remove.bin").readBytes())
        }}}
    }
    @Test fun nonemptyCloneTargetAndUnsafeManagedSelectorNeverTouchAnExistingProject() {
        val (provider,_)=SafGitBinaryIntegrationTest().provider();val key=provider.put("keep.txt","kept".toByteArray());val targets=GitCloneTargets(app)
        assertThrows(IllegalArgumentException::class.java){targets.fromTree(provider.tree)};assertEquals("kept",provider.nodes.getValue(key).file.readText())
        for(selector in listOf("internal-git:../workspace","internal-git:/absolute","internal-git:"+"a".repeat(36)))
            assertThrows(IllegalArgumentException::class.java){targets.open(selector)}
    }
}
