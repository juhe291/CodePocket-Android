package com.codepocket.ide

import android.content.ComponentName
import android.content.Intent
import android.os.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** IPC/lifecycle tests use an injected backend, not Android native execution. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class CppExecutionServiceTest {
    private class Child:ExecutionProcess {
        val closed=AtomicBoolean();val closes=AtomicInteger()
        override val stdout=ByteArrayInputStream(ByteArray(0));override val stderr=ByteArrayInputStream(ByteArray(0))
        override fun poll():ExecutionExit?=null
        override fun close(){if(closed.compareAndSet(false,true))closes.incrementAndGet()}
    }
    private fun waitUntil(check:()->Boolean) {
        repeat(400){Shadows.shadowOf(Looper.getMainLooper()).idle();if(check())return;Thread.sleep(5)}
        fail("C++ execution worker did not settle")
    }
    @Test fun startupFailurePreservesTheLastStageAndConcreteCauseAfterOwnedCleanup(){
        val controller=Robolectric.buildService(CppExecutionService::class.java).create();val service=controller.get()
        try {
            val child=Child();val results=CopyOnWriteArrayList<Bundle>()
            val backend:(Bundle,()->Boolean,(ExecutionProcess?)->Unit,(String)->Unit)->RunResult={_,_,own,stage->
                own(child);stage("准备本地工具链");throw IllegalArgumentException("test constraint")
            }
            ReflectionHelpers.setField(service,"executeRun",backend)
            val reply=Messenger(Handler(Looper.getMainLooper()){m->if(m.what==CppExecutionService.RESULT){assertTrue(child.closed.get());results.add(m.data)};true})
            Messenger(service.onBind(Intent())).send(Message.obtain(null,CppExecutionService.RUN).apply{replyTo=reply;data=Bundle().apply{putLong("id",61)}})
            waitUntil{results.size==1};val result=runResultFromBundle(results.single())
            assertEquals(RunKind.SERVICE,result.kind);assertTrue(result.stderr.contains("准备本地工具链"))
            assertTrue(result.stderr.contains("IllegalArgumentException"));assertTrue(result.stderr.contains("test constraint"));assertEquals(1,child.closes.get())
        }finally{controller.destroy()}
    }
    @Test fun privateServiceSeparatesExecutionAndCancellationWaitsForOwnedCleanup(){
        val controller=Robolectric.buildService(CppExecutionService::class.java).create();val service=controller.get()
        try {
            val info=service.packageManager.getServiceInfo(ComponentName(service,CppExecutionService::class.java),0)
            assertFalse(info.exported);assertEquals("com.codepocket.ide:cpp_run",info.processName)
            val entered=CountDownLatch(1);val child=Child();val cleanup=AtomicBoolean();val results=CopyOnWriteArrayList<Bundle>()
            val backend:(Bundle,()->Boolean,(ExecutionProcess?)->Unit,(String)->Unit)->RunResult={_,cancelled,own,stage->
                own(child);stage("编译 main.cpp");entered.countDown()
                while(!cancelled())Thread.sleep(2)
                assertTrue(child.closed.get());own(null);cleanup.set(true)
                RunResult("late success","","0",1,engine=CppExecutionService.ENGINE)
            }
            ReflectionHelpers.setField(service,"executeRun",backend)
            val reply=Messenger(Handler(Looper.getMainLooper()){m->if(m.what==CppExecutionService.RESULT){assertTrue(cleanup.get());results.add(m.data)};true})
            val remote=Messenger(service.onBind(Intent()))
            remote.send(Message.obtain(null,CppExecutionService.RUN).apply{replyTo=reply;data=Bundle().apply{putLong("id",1)}})
            waitUntil{entered.count==0L}
            remote.send(Message.obtain(null,CppExecutionService.CANCEL).apply{data=Bundle().apply{putLong("id",999)}})
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,child.closes.get())
            remote.send(Message.obtain(null,CppExecutionService.CANCEL).apply{data=Bundle().apply{putLong("id",1)}})
            waitUntil{results.size==1}
            assertEquals(RunKind.CANCELLED.name,results[0].getString("kind"));assertEquals("130",results[0].getString("exit"))
            assertEquals(1,child.closes.get())
        } finally {controller.destroy()}
    }
    @Test fun replayBusyRequestAndLateOwnershipCallbackCannotFinishOrCloseANewerRun(){
        val controller=Robolectric.buildService(CppExecutionService::class.java).create();val service=controller.get()
        val releaseFirst=AtomicBoolean();val releaseSecond=AtomicBoolean()
        try {
            val first=CountDownLatch(1);val second=CountDownLatch(1)
            val results=CopyOnWriteArrayList<Bundle>();val child=Child()
            var oldOwn:((ExecutionProcess?)->Unit)?=null
            val backend:(Bundle,()->Boolean,(ExecutionProcess?)->Unit,(String)->Unit)->RunResult={args,_,own,_->
                if(args.getLong("id")==1L){oldOwn=own;first.countDown();while(!releaseFirst.get())Thread.sleep(2)}
                else{own(child);second.countDown();while(!releaseSecond.get())Thread.sleep(2);child.close();own(null)}
                RunResult("ok","","0",1,engine=CppExecutionService.ENGINE)
            }
            ReflectionHelpers.setField(service,"executeRun",backend)
            val reply=Messenger(Handler(Looper.getMainLooper()){m->if(m.what==CppExecutionService.RESULT)results.add(m.data);true})
            val remote=Messenger(service.onBind(Intent()))
            fun start(id:Long){remote.send(Message.obtain(null,CppExecutionService.RUN).apply{replyTo=reply;data=Bundle().apply{putLong("id",id)}});Shadows.shadowOf(Looper.getMainLooper()).idle()}
            start(1);waitUntil{first.count==0L};start(1);start(99)
            waitUntil{results.size==1};assertEquals(99L,results[0].getLong("id"));assertEquals(RunKind.SERVICE.name,results[0].getString("kind"))
            releaseFirst.set(true);waitUntil{results.any{it.getLong("id")==1L}}
            start(2);waitUntil{second.count==0L}
            val stale=Child();oldOwn!!(stale);assertTrue(stale.closed.get());oldOwn!!(null);assertFalse(child.closed.get())
            remote.send(Message.obtain(null,CppExecutionService.CANCEL).apply{data=Bundle().apply{putLong("id",1)}})
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(child.closed.get())
            releaseSecond.set(true);waitUntil{results.any{it.getLong("id")==2L}}
            assertEquals(1,results.count{it.getLong("id")==1L});assertEquals(1,child.closes.get())
        } finally {releaseFirst.set(true);releaseSecond.set(true);controller.destroy()}
    }
}
