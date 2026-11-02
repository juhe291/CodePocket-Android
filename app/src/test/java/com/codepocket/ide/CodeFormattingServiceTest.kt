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
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class CodeFormattingServiceTest {
    private class HangingFormatter : AnalysisProcess {
        private val closed=AtomicBoolean();private val released=CountDownLatch(1);val closes=AtomicInteger()
        private val body=JSONObject().put("jsonrpc","2.0").put("id",1).put("result",JSONObject()
            .put("serverInfo",JSONObject().put("version","clangd version 21.1.8 deadline fixture"))
            .put("capabilities",JSONObject().put("documentFormattingProvider",true))).toString().toByteArray()
        override val input=SequenceInputStream(ByteArrayInputStream("Content-Length: ${body.size}\r\n\r\n".toByteArray()+body),
            object:InputStream(){override fun read():Int{released.await();return -1}})
        override val output=ByteArrayOutputStream();override val errors=ByteArrayInputStream(ByteArray(0))
        override fun close(){if(closed.compareAndSet(false,true)){closes.incrementAndGet();released.countDown()}}
    }
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun waitUntil(check:()->Boolean){repeat(600){idle();if(check())return;Thread.sleep(5)};fail("Formatter service did not settle")}
    private fun payload(code:String="int main(){return 0;}\n")=CodeFormatting.payload(EditorSnapshot("k",1,code,0,0),false,FormatOptions())
    private fun send(remote:Messenger,reply:Messenger,id:Long,payload:String){remote.send(Message.obtain(null,PythonAnalysisService.ANALYZE).apply{
        replyTo=reply;data=Bundle().apply{putLong("id",id);putString("payload",payload)}
    });idle()}
    @Test fun privateServiceUsesRealFormatterAndIgnoresAnySuppliedProjectPath(){
        val controller=Robolectric.buildService(CodeFormattingService::class.java).create();val service=controller.get()
        val info=service.packageManager.getServiceInfo(ComponentName(service,CodeFormattingService::class.java),0)
        assertFalse(info.exported);assertTrue(info.processName.endsWith(":formatting"))
        val factory:(File,()->Boolean,(AnalysisProcess)->Unit)->CppLspEngine={root,_,own->HostFormatting.create(root,own)}
        ReflectionHelpers.setField(service,"engineFactory",factory)
        val replies=CopyOnWriteArrayList<JSONObject>();val reply=Messenger(Handler(Looper.getMainLooper()){replies.add(JSONObject(it.data.getString("result")!!));true})
        try {
            send(Messenger(service.onBind(Intent())),reply,1,JSONObject(payload()).put("root","C:/untrusted-project").put("path","../../bad.cpp").toString())
            waitUntil{replies.size==1};assertTrue(replies.single().getBoolean("available"));assertEquals(CodeFormatting.CPP,replies.single().getString("engine"))
            assertEquals("int main() { return 0; }\n",replies.single().getString("text"))
            assertEquals("",File(service.cacheDir,"code-formatting/cpp-4-true/document.cpp").readText())
            waitUntil{ReflectionHelpers.getField<Any?>(service,"active")==null}
        } finally { controller.destroy() }
    }
    @Test fun queueKeepsOnlyTheNewestRequestAndCancellationCannotPublishOldSuccess(){
        val controller=Robolectric.buildService(CodeFormattingService::class.java).create();val service=controller.get()
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val factory:(File,()->Boolean,(AnalysisProcess)->Unit)->CppLspEngine={root,cancelled,own->
            entered.countDown();check(release.await(10,TimeUnit.SECONDS));if(cancelled())throw CancellationException();HostFormatting.create(root,own)}
        ReflectionHelpers.setField(service,"engineFactory",factory)
        val replies=ConcurrentHashMap<Long,JSONObject>();val reply=Messenger(Handler(Looper.getMainLooper()){
            replies[it.data.getLong("id")]=JSONObject(it.data.getString("result")!!);true})
        val remote=Messenger(service.onBind(Intent()))
        try {
            send(remote,reply,1,payload());assertTrue(entered.await(5,TimeUnit.SECONDS))
            send(remote,reply,2,payload("int second=2;\n"));send(remote,reply,3,payload("int third=3;\n"));release.countDown()
            waitUntil{replies.size==3}
            assertEquals("superseded",replies[2]!!.getString("error"));assertEquals("cancelled",replies[1]!!.getString("error"))
            assertTrue(replies[3]!!.getBoolean("available"));assertEquals("int third = 3;\n",replies[3]!!.getString("text"))
            waitUntil{ReflectionHelpers.getField<Any?>(service,"active")==null}
            assertNull(ReflectionHelpers.getField<Any?>(service,"queued"))
        } finally { release.countDown();controller.destroy() }
    }
    @Test fun deadlineRepliesOnceAndClosesOnlyTheOwnedFormatterChild(){
        val controller=Robolectric.buildService(CodeFormattingService::class.java).create();val service=controller.get()
        val process=HangingFormatter();val entered=CountDownLatch(1)
        val factory:(File,()->Boolean,(AnalysisProcess)->Unit)->CppLspEngine={root,_,own->own(process);entered.countDown();CppLspEngine(process,root,emptyList(),true)}
        ReflectionHelpers.setField(service,"engineFactory",factory)
        val replies=CopyOnWriteArrayList<JSONObject>();val reply=Messenger(Handler(Looper.getMainLooper()){replies.add(JSONObject(it.data.getString("result")!!));true})
        try {
            send(Messenger(service.onBind(Intent())),reply,1,payload());assertTrue(entered.await(5,TimeUnit.SECONDS))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
            waitUntil{ReflectionHelpers.getField<Any?>(service,"active")==null}
            assertEquals(1,replies.size);assertEquals("timeout",replies.single().getString("error"));assertEquals(1,process.closes.get())
        } finally {controller.destroy();process.close()}
        assertEquals(1,process.closes.get())
    }
}
