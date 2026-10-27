package com.codepocket.ide

import android.content.ComponentName
import android.os.*
import com.codepocket.ide.terminal.bridge.TerminalBridgeBroker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Binding protocol only. No native process/TTY execution is simulated as a successful run. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class LocalCppRunClientTest {
    @get:Rule val temp=TemporaryFolder()
    private fun idle()=Shadows.shadowOf(Looper.getMainLooper()).idle()
    private fun snapshot():CppRunSnapshot {
        val workspace=Workspace(temp.newFolder()){a,b->a.renameTo(b);Unit}
        val drafts=DraftStore(temp.newFolder()){a,b->a.renameTo(b);Unit}
        return CppRunSnapshot.capture(temp.newFolder(),workspace,drafts,emptyMap(),"main.cpp","int main(){return 0;}")
    }
    private fun streams():TerminalBridgeBroker.Streams {
        val pair=ParcelFileDescriptor.createPipe()
        return try { ReflectionHelpers.callConstructor(TerminalBridgeBroker.Streams::class.java,
            ClassParameter.from(ParcelFileDescriptor::class.java,ParcelFileDescriptor.dup(pair[0].fileDescriptor)),
            ClassParameter.from(ParcelFileDescriptor::class.java,ParcelFileDescriptor.dup(pair[1].fileDescriptor)),
            ClassParameter.from(ParcelFileDescriptor::class.java,ParcelFileDescriptor.dup(pair[1].fileDescriptor)))
        } finally {pair.forEach{it.close()}}
    }
    @Test fun cancellationWaitsForServiceCleanupAndIgnoresLateStateOrWrongIdentity(){
        val app=RuntimeEnvironment.getApplication();val shadow=Shadows.shadowOf(app);val received=mutableListOf<Message>()
        val endpoint=Messenger(Handler(Looper.getMainLooper()){received.add(Message.obtain(it));true})
        shadow.setComponentNameAndServiceForBindService(ComponentName(app,CppExecutionService::class.java),endpoint.binder)
        val job=snapshot();val stages=mutableListOf<String>();val results=mutableListOf<RunResult>()
        val client=LocalCppRunClient(app,7,job,CppBuildScope.PROJECT,"stdin",temp.root.path,streams(),{true},{stages.add(it)},{results.add(it)})
        try {
            client.start();idle();assertEquals(1,received.size);val run=received.single()
            assertEquals(CppExecutionService.RUN,run.what);assertEquals(job.directory.path,run.data.getString("job"));assertEquals("PROJECT",run.data.getString("scope"))
            client.cancel(RunResult("","user stop","130",0,kind=RunKind.CANCELLED));idle()
            assertEquals(CppExecutionService.CANCEL,received.last().what);assertFalse(client.released);assertTrue(results.isEmpty())
            run.replyTo.send(Message.obtain(null,CppExecutionService.STATE).apply{data=Bundle().apply{putLong("id",7);putString("stage","running")}})
            run.replyTo.send(Message.obtain(null,CppExecutionService.RESULT).apply{data=RunResult("","","0",1).toBundle(8)})
            idle();assertTrue(stages.isEmpty());assertTrue(results.isEmpty())
            run.replyTo.send(Message.obtain(null,CppExecutionService.RESULT).apply{data=RunResult("","cleaned","130",1,kind=RunKind.CANCELLED).toBundle(7)})
            idle();assertTrue(client.released);assertEquals("user stop",results.single().stderr)
            assertTrue(job.directory.isDirectory)
        } finally {client.close();job.close()}
    }
    @Test fun cancellationBeforeConnectionNeverSendsRunOrStartsAChild(){
        val app=RuntimeEnvironment.getApplication();val shadow=Shadows.shadowOf(app);val messages=mutableListOf<Int>()
        val endpoint=Messenger(Handler(Looper.getMainLooper()){messages.add(it.what);true})
        shadow.setComponentNameAndServiceForBindService(ComponentName(app,CppExecutionService::class.java),endpoint.binder)
        val job=snapshot();val results=mutableListOf<RunResult>()
        val client=LocalCppRunClient(app,9,job,CppBuildScope.CURRENT,"",temp.root.path,streams(),{true},{},{results.add(it)})
        try {
            client.start();client.cancel(RunResult("","cancel before connect","130",0,kind=RunKind.CANCELLED));idle()
            assertTrue(client.released);assertTrue(messages.isEmpty());assertEquals(1,results.size)
        } finally {client.close();job.close()}
    }
}
