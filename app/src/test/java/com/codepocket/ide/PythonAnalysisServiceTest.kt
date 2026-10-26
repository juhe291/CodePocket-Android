package com.codepocket.ide

import android.app.Activity
import android.content.ComponentName
import android.os.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class PythonAnalysisServiceTest {
    private fun pump(condition:()->Boolean){repeat(300){Shadows.shadowOf(Looper.getMainLooper()).idle();if(condition())return;Thread.sleep(3)};fail("Host Messenger callback did not arrive")}
    @Test fun manifestSeparatesPrivateAnalysisFromRealExecution(){
        val app=RuntimeEnvironment.getApplication();val pm=app.packageManager
        val analysis=pm.getServiceInfo(ComponentName(app,PythonAnalysisService::class.java),0)
        val execution=pm.getServiceInfo(ComponentName(app,PythonExecutionService::class.java),0)
        assertFalse(analysis.exported);assertFalse(execution.exported);assertTrue(analysis.processName.endsWith(":analysis"));assertTrue(execution.processName.endsWith(":python"));assertNotEquals(execution.processName,analysis.processName)
    }
    @Test fun cancellationWithoutARequestDoesNotStartPythonOrCreateAQueue(){
        val controller=Robolectric.buildService(PythonAnalysisService::class.java).create();val service=controller.get()
        val endpoint=Messenger(service.onBind(android.content.Intent()))
        endpoint.send(Message.obtain(null,PythonAnalysisService.CANCEL).apply{data=Bundle().apply{putLong("id",123)}})
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(ReflectionHelpers.getField<Any?>(service,"active"));assertNull(ReflectionHelpers.getField<Any?>(service,"queued"));controller.destroy()
    }
    @Test fun closedTransportNeverBindsOrStartsAnAnalysisProcess(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup();val transport=AndroidSemanticTransport(controller.get());transport.close()
        val worker=Executors.newSingleThreadExecutor()
        try{assertNull(worker.submit<String?>{transport.analyze("{}")}.get(2,TimeUnit.SECONDS))}finally{worker.shutdownNow();controller.pause().stop().destroy()}
    }
    @Test fun nullBinderFailsWithoutCrashingOrWaitingForTheAnalysisDeadline(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup();val app=RuntimeEnvironment.getApplication()
        val shadow=Shadows.shadowOf(app);shadow.setComponentNameAndServiceForBindService(ComponentName(app,PythonAnalysisService::class.java),null)
        val transport=AndroidSemanticTransport(controller.get());val worker=Executors.newSingleThreadExecutor()
        try{val pending=worker.submit<String?>{transport.analyze("{}")};pump{pending.isDone};assertNull(pending.get(1,TimeUnit.SECONDS))}
        finally{transport.close();worker.shutdownNow();Shadows.shadowOf(Looper.getMainLooper()).idle();controller.pause().stop().destroy()}
    }
    @Test fun lateDisconnectFromAnOldBindingCannotReleaseANewRequest(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup();val app=RuntimeEnvironment.getApplication();val shadow=Shadows.shadowOf(app)
        val component=ComponentName(app,PythonAnalysisService::class.java);val received=mutableListOf<Message>()
        val endpoint=Messenger(Handler(Looper.getMainLooper()){message->if(message.what==PythonAnalysisService.ANALYZE)received.add(Message.obtain(message));true})
        shadow.setComponentNameAndServiceForBindService(component,endpoint.binder)
        val transport=AndroidSemanticTransport(controller.get());val worker=Executors.newSingleThreadExecutor()
        try{
            val first=worker.submit<String?>{transport.analyze("{}")};pump{received.size==1}
            val old=shadow.boundServiceConnections.single();old.onServiceDisconnected(component);assertNull(first.get(1,TimeUnit.SECONDS))
            assertNull(worker.submit<String?>{transport.analyze("{}")}.get(1,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle()
            val next=worker.submit<String?>{transport.analyze("{}")};pump{received.size==2}
            old.onServiceDisconnected(component)
            try{next.get(60,TimeUnit.MILLISECONDS);fail("An old disconnect released the new connection") }catch(_:TimeoutException){ }
            val reply=received.last();val raw=SemanticFixtures.case("list").getString("response")
            reply.replyTo.send(Message.obtain(null,PythonAnalysisService.RESULT).apply{data=Bundle().apply{putLong("id",reply.data.getLong("id"));putString("result",raw)}})
            pump{next.isDone};assertEquals(raw,next.get(1,TimeUnit.SECONDS))
        }finally{transport.close();worker.shutdownNow();Shadows.shadowOf(Looper.getMainLooper()).idle();controller.pause().stop().destroy()}
    }
    @Test fun closingAnInFlightBindingReleasesItsWaitingWorker(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup();val app=RuntimeEnvironment.getApplication();var received=false
        val endpoint=Messenger(Handler(Looper.getMainLooper()){message->if(message.what==PythonAnalysisService.ANALYZE)received=true;true})
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app,PythonAnalysisService::class.java),endpoint.binder)
        val transport=AndroidSemanticTransport(controller.get());val worker=Executors.newSingleThreadExecutor()
        try{val next=worker.submit<String?>{transport.analyze("{}")};pump{received};transport.close();assertNull(next.get(1,TimeUnit.SECONDS))}
        finally{transport.close();worker.shutdownNow();Shadows.shadowOf(Looper.getMainLooper()).idle();controller.pause().stop().destroy()}
    }
}
