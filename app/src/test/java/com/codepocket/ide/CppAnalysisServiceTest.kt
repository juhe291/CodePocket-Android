package com.codepocket.ide

import android.content.ComponentName
import android.content.Intent
import android.os.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.io.*
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Failure/lifecycle contract only. The hanging child is a fixture, never evidence of Android execution. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class CppAnalysisServiceTest {
    private class HangingProcess:AnalysisProcess {
        private val closed=AtomicBoolean();val closes=AtomicInteger();private val monitor=Object()
        private val initial=JSONObject().put("jsonrpc","2.0").put("id",1).put("result",JSONObject()
            .put("serverInfo",JSONObject().put("version","clangd version 21.1.8 test failure channel"))
            .put("capabilities",JSONObject())).toString().toByteArray()
        private val bytes="Content-Length: ${initial.size}\r\n\r\n".toByteArray()+initial
        override val input=object:InputStream(){var at=0
            override fun read():Int=synchronized(monitor){if(at<bytes.size)bytes[at++].toInt() and 255 else{while(!closed.get())monitor.wait();-1}}
            override fun read(out:ByteArray,offset:Int,length:Int):Int {
                if(length==0)return 0
                return synchronized(monitor){if(at<bytes.size){val n=minOf(length,bytes.size-at);bytes.copyInto(out,offset,at,at+n);at+=n;n}else{while(!closed.get())monitor.wait();-1}}
            }
        }
        override val output=ByteArrayOutputStream()
        override val errors=ByteArrayInputStream(ByteArray(0))
        override fun close(){if(closed.compareAndSet(false,true)){closes.incrementAndGet();synchronized(monitor){monitor.notifyAll()}}}
    }
    private fun payload(service:CppAnalysisService):String {
        val parent=File(service.cacheDir,"cpp-project-context").apply{mkdirs()};val root=File(parent,"a".repeat(64)).apply{mkdirs()};File(root,"main.cpp").writeText("")
        return JSONObject().put("root",root.path).put("path","main.cpp").put("code","void f(){unknown.").put("caret",17).put("mode","complete").toString()
    }
    private fun waitUntil(check:()->Boolean){repeat(300){Shadows.shadowOf(Looper.getMainLooper()).idle();if(check())return;Thread.sleep(5)};fail("Service worker did not settle")}
    @Test fun privateServiceHasItsOwnProcessAndTimeoutClosesOnlyItsOwnedChild(){
        val controller=Robolectric.buildService(CppAnalysisService::class.java).create();val service=controller.get()
        val info=service.packageManager.getServiceInfo(ComponentName(service,CppAnalysisService::class.java),0)
        assertFalse(info.exported);assertTrue(info.processName.endsWith(":cpp_analysis"))
        val process=HangingProcess();val entered=CountDownLatch(1)
        val factory:(File,()->Boolean,(AnalysisProcess)->Unit)->CppLspEngine={root,_,own->own(process);entered.countDown();CppLspEngine(process,root,emptyList())}
        ReflectionHelpers.setField(service,"engineFactory",factory)
        val replies=CopyOnWriteArrayList<JSONObject>();val reply=Messenger(Handler(Looper.getMainLooper()){replies.add(JSONObject(it.data.getString("result")!!));true})
        try {
            Messenger(service.onBind(Intent())).send(Message.obtain(null,PythonAnalysisService.ANALYZE).apply{replyTo=reply;data=Bundle().apply{putLong("id",7);putString("payload",payload(service))}})
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(entered.await(5,TimeUnit.SECONDS))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
            waitUntil{replies.isNotEmpty()};assertEquals("timeout",replies.single().getString("error"));assertFalse(replies.single().getBoolean("available"));assertEquals(1,process.closes.get())
            waitUntil{ReflectionHelpers.getField<Any?>(service,"active")==null};assertEquals(1,replies.size)
        } finally { controller.destroy();process.close() }
        assertEquals(1,process.closes.get())
    }
    @Test fun destroyClosesAHangingOwnedChildAndReleasesItsWorker(){
        val controller=Robolectric.buildService(CppAnalysisService::class.java).create();val service=controller.get();val process=HangingProcess();val entered=CountDownLatch(1)
        val factory:(File,()->Boolean,(AnalysisProcess)->Unit)->CppLspEngine={root,_,own->own(process);entered.countDown();CppLspEngine(process,root,emptyList())}
        ReflectionHelpers.setField(service,"engineFactory",factory)
        Messenger(service.onBind(Intent())).send(Message.obtain(null,PythonAnalysisService.ANALYZE).apply{replyTo=Messenger(Handler(Looper.getMainLooper()){true});data=Bundle().apply{putLong("id",1);putString("payload",payload(service))}})
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(entered.await(5,TimeUnit.SECONDS));controller.destroy()
        assertEquals(1,process.closes.get());assertTrue(ReflectionHelpers.getField<ExecutorService>(service,"worker").awaitTermination(5,TimeUnit.SECONDS))
    }
}
