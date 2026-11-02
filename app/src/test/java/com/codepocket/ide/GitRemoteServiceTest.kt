package com.codepocket.ide

import android.content.ComponentName
import android.content.Intent
import android.os.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class]) @LooperMode(LooperMode.Mode.PAUSED)
class GitRemoteServiceTest {
    private val url="https://github.com/fixture/project.git"
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun waitFor(check:()->Boolean){repeat(2000){idle();if(check())return;Thread.sleep(5)};fail("Remote service did not settle")}
    private fun snapshot(remote:Boolean=true):GitWorkspaceSnapshot {
        val app=RuntimeEnvironment.getApplication();val original=File(app.filesDir,"remote-origin-${UUID.randomUUID()}").apply{check(mkdir())}
        File(original,"main.py").writeText("print(42)\n")
        return GitWorkspaceStore(FileGitGateway(original)).capture(if(remote)GitRemoteJobs.next(app) else GitJobs.next(app),true)
    }
    private fun replies(target:MutableList<Pair<Long,JSONObject>>)=Messenger(Handler(Looper.getMainLooper()){
        target.add(it.data.getLong("id") to JSONObject(it.data.getString("result")!!));true
    })
    private fun send(remote:Messenger,reply:Messenger,id:Long,root:File,body:JSONObject=JSONObject().put("action","remote_info"),intent:String?=null) {
        remote.send(Message.obtain(null,PythonAnalysisService.ANALYZE).apply{replyTo=reply
            data=Bundle().apply{putLong("id",id);putString("payload",GitRemoteProtocol.packet(root,body,intent))}});idle()
    }
    private fun cancel(remote:Messenger,id:Long){remote.send(Message.obtain(null,PythonAnalysisService.CANCEL).apply{data=Bundle().apply{putLong("id",id)}});idle()}
    private fun info(root:File)=JSONObject(HostGitBackend.run(root,JSONObject().put("action","status").toString()))
        .put("remote",JSONObject().put("url",JSONObject.NULL).put("configured",false)).toString()
    private fun pushed():JSONObject {
        val file=File(CppFixtures.root,"verification/git-remote-workspace-2026-11-02/native-engine-fixtures.json")
        return JSONObject(file.readText()).getJSONArray("cases").getJSONObject(0).getJSONObject("response")
            .apply{remove("plan");put("remote",JSONObject().put("url",url).put("branch","main").put("head",getString("head")))
                put("push_head",getString("head"));put("network_outcome","pushed")}
    }
    private fun pushRequest()=JSONObject().put("action","push").put("expected","b".repeat(64)).put("url",url).put("branch","main")
        .put("expected_head",pushed().getString("head")).put("expected_remote",JSONObject.NULL)
    private fun hostRemote(root:File,body:String,cancelled:AtomicBoolean,plan:File?):String {
        val repo=CppFixtures.root;val windows=System.getProperty("os.name").orEmpty().startsWith("Windows",true)
        val python=System.getProperty("codepocket.hostPython")?:if(windows)File(repo,".tools/python313-full/tools/python.exe").path else "python3"
        val output=File(root.parentFile,"remote-host-${UUID.randomUUID()}.json");val errors=File(root.parentFile,"remote-host-${UUID.randomUUID()}.log")
        val script="import sys,git_remote\nclass Flag:\n def get(self): return sys.argv[3]=='true'\nprint(git_remote.run_android(sys.argv[1],sys.stdin.read(),Flag(),sys.argv[2] or None))"
        val child=ProcessBuilder(python,"-B","-c",script,root.path,plan?.path?:"",cancelled.get().toString())
            .apply{environment()["PYTHONUTF8"]="1";environment()["PYTHONPATH"]=listOf(File(repo,".tools/git-probe/site"),File(repo,"app/src/main/python")).joinToString(File.pathSeparator){it.path}}
            .redirectOutput(output).redirectError(errors).start()
        try {
            child.outputStream.bufferedWriter(Charsets.UTF_8).use{it.write(body)}
            check(child.waitFor(25,TimeUnit.SECONDS));check(child.exitValue()==0){errors.readText()};return output.readText().trim()
        }finally{if(child.isAlive){child.destroyForcibly();child.waitFor(1,TimeUnit.SECONDS)}}
    }
    @Test fun privateServiceRunsRealPinnedConfigurationAndRejectsLocalGitOrOriginalWorkspaceRoots() {
        val controller=Robolectric.buildService(GitRemoteService::class.java).create();val service=controller.get()
        val factory:(File,String,AtomicBoolean,File?)->String=::hostRemote;ReflectionHelpers.setField(service,"backend",factory)
        val declaration=service.packageManager.getServiceInfo(ComponentName(service,GitRemoteService::class.java),0)
        assertFalse(declaration.exported);assertTrue(declaration.processName.endsWith(":git_remote"))
        val copy=snapshot();val local=snapshot(false);val results=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(results)
        try {
            val state=JSONObject(HostGitBackend.run(copy.root,JSONObject().put("action","status").toString()))
            val initialized=JSONObject(HostGitBackend.run(copy.root,JSONObject().put("action","init").put("expected",state.getString("snapshot")).toString()))
            val body=JSONObject().put("action","configure").put("url",url).put("branch","main").put("expected",initialized.getString("snapshot"))
                .put("username","fixture").put("token","fixture-only-never-a-real-token")
            val remote=Messenger(service.onBind(Intent()));send(remote,reply,1,copy.root,body);waitFor{results.size==1}
            assertTrue(results.single().second.toString(),results.single().second.getBoolean("ok"));assertTrue(File(copy.root.parentFile,"result.plan").isFile)
            assertFalse(File(copy.root,".git/config").readText().contains("fixture-only-never-a-real-token"))
            send(remote,reply,2,local.root);waitFor{results.size==2};assertEquals("snapshot_path",results.last().second.getString("error"))
            val original=File(service.filesDir,"outside-${UUID.randomUUID()}").apply{mkdir()};File(original,"keep.txt").writeText("keep")
            send(remote,reply,3,original);waitFor{results.size==3};assertEquals("snapshot_path",results.last().second.getString("error"))
            assertEquals("keep",File(original,"keep.txt").readText());assertFalse(GitRemoteJobs.lease(copy.root).exists())
        }finally{controller.destroy();GitRemoteJobs.discard(service,copy.root);GitJobs.discard(service,local.root)}
    }
    @Test fun busyRequestDoesNotCancelOrSupersedeAnActiveOperation() {
        val controller=Robolectric.buildService(GitRemoteService::class.java).create();val service=controller.get()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val calls=AtomicInteger();var flag:AtomicBoolean?=null
        val factory:(File,String,AtomicBoolean,File?)->String={root,_,cancelled,_->calls.incrementAndGet();flag=cancelled;entered.countDown();check(release.await(10,TimeUnit.SECONDS));info(root)}
        ReflectionHelpers.setField(service,"backend",factory);val jobs=List(2){snapshot()};val results=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(results)
        try {
            val remote=Messenger(service.onBind(Intent()));send(remote,reply,1,jobs[0].root);assertTrue(entered.await(5,TimeUnit.SECONDS))
            send(remote,reply,2,jobs[1].root);waitFor{results.size==1};assertEquals("busy",results.single().second.getString("error"));assertFalse(flag!!.get())
            release.countDown();waitFor{results.size==2};assertTrue(results.single{it.first==1L}.second.getBoolean("ok"));assertEquals(1,calls.get())
        }finally{release.countDown();controller.destroy();jobs.forEach{GitRemoteJobs.discard(service,it.root)}}
    }
    @Test fun cancellationReachesTheBackendAndItsLeaseProtectsTheSnapshotUntilReturn() {
        val controller=Robolectric.buildService(GitRemoteService::class.java).create();val service=controller.get()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);var flag:AtomicBoolean?=null
        val factory:(File,String,AtomicBoolean,File?)->String={_,_,cancelled,_->flag=cancelled;entered.countDown();check(release.await(10,TimeUnit.SECONDS));GitRemoteProtocol.failure("cancelled")}
        ReflectionHelpers.setField(service,"backend",factory);val copy=snapshot();val results=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(results)
        try {
            val remote=Messenger(service.onBind(Intent()));send(remote,reply,1,copy.root);assertTrue(entered.await(5,TimeUnit.SECONDS));cancel(remote,1)
            assertTrue(flag!!.get());GitRemoteJobs.discard(service,copy.root);assertTrue(copy.root.exists())
            release.countDown();waitFor{results.size==1};assertEquals("cancelled",results.single().second.getString("error"));assertFalse(copy.root.parentFile!!.exists())
        }finally{release.countDown();controller.destroy();GitRemoteJobs.discard(service,copy.root)}
    }
    @Test fun completedPushReportIsPersistedAndReturnedEvenWhenCancelArrivesBeforeTheReply() {
        val controller=Robolectric.buildService(GitRemoteService::class.java).create();val service=controller.get();val copy=snapshot()
        val ledger=GitPushLedger(service);val body=pushRequest();val intent=ledger.begin("workspace",copy.root,body)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val factory:(File,String,AtomicBoolean,File?)->String={_,_,_,_->entered.countDown();check(release.await(10,TimeUnit.SECONDS));pushed().toString()}
        ReflectionHelpers.setField(service,"backend",factory);val results=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(results)
        try {
            val remote=Messenger(service.onBind(Intent()));send(remote,reply,1,copy.root,body,intent.id);assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals("started",ledger.get(intent.id).state)
            cancel(remote,1);release.countDown();waitFor{results.size==1}
            assertEquals("pushed",results.single().second.getString("network_outcome"));assertEquals("pushed",ledger.get(intent.id).state)
            assertFalse(copy.root.parentFile!!.exists())
        }finally{release.countDown();controller.destroy();GitRemoteJobs.discard(service,copy.root)}
    }
    @Test fun networkDeadlineIs120SecondsAndAnExpiredPushRemainsUncertainUntilARealReport() {
        val controller=Robolectric.buildService(GitRemoteService::class.java).create();val service=controller.get();val copy=snapshot()
        val ledger=GitPushLedger(service);val body=pushRequest();val intent=ledger.begin("workspace",copy.root,body)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val factory:(File,String,AtomicBoolean,File?)->String={_,_,_,_->entered.countDown();check(release.await(10,TimeUnit.SECONDS));pushed().toString()}
        ReflectionHelpers.setField(service,"backend",factory);val results=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(results)
        try {
            send(Messenger(service.onBind(Intent())),reply,1,copy.root,body,intent.id);assertTrue(entered.await(5,TimeUnit.SECONDS))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31));assertTrue(results.isEmpty())
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(90));assertEquals("push_unknown",results.single().second.getString("error"))
            assertFalse(ledger.get(intent.id).confirmed);assertTrue(GitRemoteJobs.lease(copy.root).exists())
            release.countDown();waitFor{ledger.get(intent.id).state=="pushed" && !copy.root.parentFile!!.exists()};assertEquals(1,results.size)
        }finally{release.countDown();controller.destroy();GitRemoteJobs.discard(service,copy.root)}
    }
    @Test fun interruptionOrUnexpectedBackendErrorAfterPushEntryNeverClaimsNotSentOrLeaksItsMessage() {
        for(mode in listOf("exception","interrupted","invalid_report")) {
            val controller=Robolectric.buildService(GitRemoteService::class.java).create();val service=controller.get();val copy=snapshot()
            val ledger=GitPushLedger(service);val body=pushRequest();val intent=ledger.begin("workspace",copy.root,body)
            val factory:(File,String,AtomicBoolean,File?)->String={_,_,_,_->when(mode){
                "interrupted"->throw InterruptedException()
                "invalid_report"->GitRemoteProtocol.failure("backend")
                else->throw java.io.IOException("secret-diagnostic")}}
            ReflectionHelpers.setField(service,"backend",factory);val results=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(results)
            try {
                send(Messenger(service.onBind(Intent())),reply,1,copy.root,body,intent.id);waitFor{results.size==1}
                assertEquals("push_unknown",results.single().second.getString("error"));assertEquals("unknown",ledger.get(intent.id).state)
                assertFalse(results.single().second.toString().contains("secret-diagnostic"))
            }finally{controller.destroy();GitRemoteJobs.discard(service,copy.root)}
        }
    }
    @Test fun pushWithoutMatchingPersistedIntentNeverCallsBackend() {
        val controller=Robolectric.buildService(GitRemoteService::class.java).create();val service=controller.get();val copy=snapshot();val calls=AtomicInteger()
        val factory:(File,String,AtomicBoolean,File?)->String={_,_,_,_->calls.incrementAndGet();pushed().toString()}
        ReflectionHelpers.setField(service,"backend",factory);val results=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(results)
        try {
            send(Messenger(service.onBind(Intent())),reply,1,copy.root,pushRequest());waitFor{results.size==1}
            assertEquals("push_intent",results.single().second.getString("error"));assertEquals(0,calls.get());assertFalse(GitRemoteJobs.lease(copy.root).exists())
        }finally{controller.destroy();GitRemoteJobs.discard(service,copy.root)}
    }
    @Test fun remoteStartupCleansOnlyItsDeadJobsAndLeavesLocalGitLeasesAlone() {
        val app=RuntimeEnvironment.getApplication();val local=snapshot(false);val stale=snapshot()
        check(GitJobs.lease(local.root).createNewFile());check(GitRemoteJobs.lease(stale.root).createNewFile())
        val controller=Robolectric.buildService(GitRemoteService::class.java).create();val service=controller.get()
        try {waitFor{!stale.root.parentFile!!.exists()};assertTrue(local.root.exists());assertTrue(GitJobs.lease(local.root).exists())}
        finally{controller.destroy();GitJobs.lease(local.root).delete();GitJobs.discard(app,local.root);GitRemoteJobs.discard(service,stale.root)}
    }
}
