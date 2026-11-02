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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class GitOperationServiceTest {
    @Test fun serviceAndUiMayCreateTheirSnapshotParentConcurrently(){
        val app=RuntimeEnvironment.getApplication();val cache=File(app.cacheDir,"concurrent-${UUID.randomUUID()}").apply{check(mkdir())}
        val context=object:android.content.ContextWrapper(app){override fun getCacheDir()=cache}
        val pool=java.util.concurrent.Executors.newFixedThreadPool(8);val start=CountDownLatch(1)
        try {
            val tasks=List(32){pool.submit<File>{check(start.await(5,TimeUnit.SECONDS));GitJobs.parent(context)}}
            start.countDown();val roots=tasks.map{it.get(10,TimeUnit.SECONDS)};assertEquals(1,roots.toSet().size);assertTrue(roots.all{it.isDirectory})
        }finally{start.countDown();pool.shutdownNow()}
    }
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun waitFor(check:()->Boolean){repeat(1500){idle();if(check())return;Thread.sleep(5)};fail("Git service did not settle")}
    private fun snapshot():GitWorkspaceSnapshot {
        val app=RuntimeEnvironment.getApplication();val original=File(app.filesDir,"git-origin-${UUID.randomUUID()}").apply{check(mkdirs())}
        File(original,"main.py").writeText("print(42)\n")
        return GitWorkspaceStore(FileGitGateway(original)).capture(GitJobs.next(app))
    }
    private fun send(remote:Messenger,reply:Messenger,id:Long,root:File,action:JSONObject=JSONObject().put("action","status")) {
        remote.send(Message.obtain(null,PythonAnalysisService.ANALYZE).apply{replyTo=reply
            data=Bundle().apply{putLong("id",id);putString("payload",GitProtocol.packet(root,action))}});idle()
    }
    private fun replies(target:MutableList<Pair<Long,JSONObject>>)=Messenger(Handler(Looper.getMainLooper()){
        target.add(it.data.getLong("id") to JSONObject(it.data.getString("result")!!));true
    })
    @Test fun realPrivateServiceOperatesOnItsOwnSnapshotAndRejectsAWorkspaceRoot(){
        val controller=Robolectric.buildService(GitOperationService::class.java).create();val service=controller.get()
        val factory:(File,String)->String=HostGitBackend::run;ReflectionHelpers.setField(service,"backend",factory)
        val info=service.packageManager.getServiceInfo(ComponentName(service,GitOperationService::class.java),0)
        assertFalse(info.exported);assertTrue(info.processName.endsWith(":git"))
        val snapshot=snapshot();val result=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(result)
        try {
            val remote=Messenger(service.onBind(Intent()));send(remote,reply,1,snapshot.root);waitFor{result.size==1}
            assertTrue(result.single().second.getBoolean("ok"));assertFalse(result.single().second.getBoolean("repository"))
            val token=result.single().second.getString("snapshot")
            send(remote,reply,2,snapshot.root,JSONObject().put("action","init").put("expected",token));waitFor{result.size==2}
            assertTrue(result.last().second.getBoolean("ok"));assertTrue(File(snapshot.root,".git/HEAD").isFile)
            val original=File(service.filesDir,"outside-workspace").apply{mkdirs()};val sentinel=File(original,"keep.py").apply{writeText("keep")}
            send(remote,reply,3,original);waitFor{result.size==3};assertEquals("snapshot_path",result.last().second.getString("error"))
            assertEquals("keep",sentinel.readText());assertFalse(File(original,".git").exists());assertFalse(GitJobs.lease(snapshot.root).exists())
        } finally {controller.destroy();GitJobs.discard(service,snapshot.root)}
    }
    @Test fun activeRequestIsCancelledAndOnlyTheLatestQueuedSnapshotRuns(){
        val controller=Robolectric.buildService(GitOperationService::class.java).create();val service=controller.get()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val calls=AtomicInteger()
        val factory:(File,String)->String={root,request->if(calls.incrementAndGet()==1){entered.countDown();check(release.await(10,TimeUnit.SECONDS))};HostGitBackend.run(root,request)}
        ReflectionHelpers.setField(service,"backend",factory)
        val result=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(result);val jobs=List(3){snapshot()}
        try {
            val remote=Messenger(service.onBind(Intent()));send(remote,reply,1,jobs[0].root);assertTrue(entered.await(5,TimeUnit.SECONDS))
            send(remote,reply,2,jobs[1].root);send(remote,reply,3,jobs[2].root);release.countDown();waitFor{result.size==3}
            assertEquals("cancelled",result.single{it.first==1L}.second.getString("error"))
            assertEquals("superseded",result.single{it.first==2L}.second.getString("error"))
            assertTrue(result.single{it.first==3L}.second.getBoolean("ok"));assertEquals(2,calls.get())
            assertFalse(jobs[0].root.parentFile!!.exists());assertTrue(jobs[2].root.isDirectory)
        } finally {release.countDown();controller.destroy();jobs.forEach{GitJobs.discard(service,it.root)}}
    }
    @Test fun deadlineRepliesOnceAndKeepsTheLeaseUntilTheWorkerFinishes(){
        val controller=Robolectric.buildService(GitOperationService::class.java).create();val service=controller.get()
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val factory:(File,String)->String={_,_->entered.countDown();check(release.await(10,TimeUnit.SECONDS));GitProtocol.failure("finished")}
        ReflectionHelpers.setField(service,"backend",factory)
        val result=CopyOnWriteArrayList<Pair<Long,JSONObject>>();val reply=replies(result);val snapshot=snapshot()
        try {
            send(Messenger(service.onBind(Intent())),reply,1,snapshot.root);assertTrue(entered.await(5,TimeUnit.SECONDS))
            assertTrue(GitJobs.lease(snapshot.root).exists());Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
            assertEquals(1,result.size);assertEquals("timeout",result.single().second.getString("error"))
            GitJobs.discard(service,snapshot.root);assertTrue(snapshot.root.isDirectory)
            release.countDown();waitFor{!snapshot.root.parentFile!!.exists()};idle();assertEquals(1,result.size)
        } finally {release.countDown();controller.destroy();GitJobs.discard(service,snapshot.root)}
    }
}
