package com.codepocket.ide

import android.app.AlertDialog
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import com.codepocket.editorlab.WorkbenchEditor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual Main/Sora/file saves and RunSession entry. Host lacks app_process: no Python execution is claimed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class MainRunIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(8){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real main Sora layout unavailable")}
    private fun edit(value:String){editor.setText(value);ready();editor.setSelection(value.length)}
    private fun key(code:Int,meta:Int=0){main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0,meta));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,code,0,meta))}
    private fun switch(path:String){ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready()}
    @Before fun launch(){
        assertFalse("Host must expose the real missing Android bridge",File("/system/bin/app_process").canExecute())
        RunSession.stop();ReflectionHelpers.setStaticField(RunSession::class.java,"result",null)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(autoSave=false,animations=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val view=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        view.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));view.layout(0,0,view.measuredWidth,view.measuredHeight);view.viewTreeObserver.dispatchOnGlobalLayout()
        editor.requestFocus();ready()
    }
    @After fun close(){RunSession.stop();if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};ReflectionHelpers.setStaticField(RunSession::class.java,"result",null)}
    private fun bridgeFailureFor(code:String):RunResult {
        val response=RunSession.result!!
        assertEquals(RunKind.SERVICE,response.kind);assertTrue(response.stderr.contains("app_process"));assertEquals("",response.stdout)
        assertEquals(DocumentTree.digest(code),response.sourceHash);assertEquals(workspace.resolve("main.py").absolutePath,response.sourcePath)
        assertEquals(workspace.identity,response.workspaceId);assertEquals(field<EditorSessions>("sessions").key("main.py"),response.sourceKey)
        assertFalse(RunSession.running);assertFalse(field<Boolean>("dirty"));assertEquals(code,workspace.resolve("main.py").readText());return response
    }
    @Test fun f5SavesActualSoraBytesAndReportsMissingHostBridgeTruthfully(){
        val code="print('当前主缓冲')\n";edit(code);val content=editor.text;key(KeyEvent.KEYCODE_F5);drain();bridgeFailureFor(code)
        assertSame(content,editor.text);assertEquals(code.length,editor.selectionStart);assertEquals(code,editor.text.toString())
    }
    @Test fun editDuringPendingSaveRunsOnlyTheNewestSavedRevision(){
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        WorkspaceIo.executor.execute{entered.countDown();check(release.await(10,TimeUnit.SECONDS))};assertTrue(entered.await(10,TimeUnit.SECONDS))
        val newer="print('新输入必须进入运行快照')\n"
        try{edit("print('older')\n");key(KeyEvent.KEYCODE_F5);assertTrue(field("saving"));edit(newer);assertNull(RunSession.result)}finally{release.countDown()}
        drain();bridgeFailureFor(newer);assertEquals(newer,editor.text.toString())
    }
    @Test fun saveConflictBlocksF5WithoutStartingOrDiscardingRecoverableCode(){
        val unsaved="print('my local draft')\n";edit(unsaved);val original="print('external writer')\n";workspace.resolve("main.py").writeText(original)
        key(KeyEvent.KEYCODE_F5);drain();assertNull(RunSession.result);assertFalse(RunSession.running);assertEquals(original,workspace.resolve("main.py").readText())
        assertTrue(field("dirty"));assertEquals(unsaved,editor.text.toString());assertEquals(unsaved,field<DraftStore>("drafts").read(field<EditorSessions>("sessions").key("main.py"))!!.text)
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle()
    }
    @Test fun cppCtrlEnterRequiresThirdPartyConsentAndCancelNeverStartsTheNetwork(){
        switch("main.cpp");val code="#include <iostream>\nint main(){std::cout << 42;}\n";edit(code)
        key(KeyEvent.KEYCODE_ENTER,KeyEvent.META_CTRL_ON);drain();val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing)
        assertTrue(dialog.findViewById<android.widget.TextView>(android.R.id.message).text.contains("Wandbox"));assertNull(RunSession.result);assertFalse(RunSession.running)
        assertEquals(code,workspace.resolve("main.cpp").readText());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle()
        assertFalse(main.getSharedPreferences("codepocket_v2",0).getBoolean("wandboxConsent",false));assertNull(RunSession.result);assertEquals(code,editor.text.toString())
    }
    @Test fun emptyCodeIsRejectedBeforeAnyRunSessionIsStarted(){
        edit(" \n");key(KeyEvent.KEYCODE_F5);drain();assertNull(RunSession.result);assertFalse(RunSession.running)
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing);assertTrue(dialog.findViewById<android.widget.TextView>(android.R.id.message).text.contains("输入代码"))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle()
    }
}
