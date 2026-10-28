package com.codepocket.ide

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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

/** Real Main/Sora/save/snapshot/session integration. Host bridge failure is recorded as failure. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class MainCaseIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private val cases=List(2){ProgramCase(name="case $it",input="$it",expected="$it\n")}
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(10){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real Sora layout unavailable")}
    private fun layout(){val content=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics;content.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));content.layout(0,0,content.measuredWidth,content.measuredHeight);content.viewTreeObserver.dispatchOnGlobalLayout();ready()}
    private fun run(){ReflectionHelpers.callInstanceMethod<Any?>(main,"runCases",ClassParameter.from(List::class.java,cases),ClassParameter.from(Boolean::class.javaPrimitiveType,false))}
    private fun open(){ReflectionHelpers.callInstanceMethod<Any?>(main,"showTestCases");drain()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun text(d:AlertDialog,label:String)=views(d.window!!.decorView).filterIsInstance<EditText>().single{it.contentDescription.toString().startsWith(label)}
    private fun dialog():AlertDialog=field("toolDialog")
    private fun newCase(){open();views(dialog().window!!.decorView).single{it.getTag(R.id.workbench_key)=="choice:新建用例"}.performClick();idle()}
    @Before fun launch(){
        assertFalse(File("/system/bin/app_process").canExecute());CaseRunSession.stop();RunSession.stop()
        ReflectionHelpers.setStaticField(CaseRunSession::class.java,"batch",null);ReflectionHelpers.setStaticField(RunSession::class.java,"result",null)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(autoSave=false,animations=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout()
    }
    @After fun close(){CaseRunSession.stop();RunSession.stop();if(::main.isInitialized&&!main.isDestroyed){main.finish();controller.pause().stop().destroy()};drain();ReflectionHelpers.setStaticField(RunSession::class.java,"result",null);ReflectionHelpers.setStaticField(CaseRunSession::class.java,"batch",null)}
    @Test fun batchSavesActualSoraCodeAndPersistsTheRealMissingBridgeFailureWithoutRunningTheNextCase(){
        val code="print(input())\n";editor.setText(code);ready();run();drain()
        assertEquals(code,workspace.resolve("main.py").readText());assertFalse(field("dirty"));assertFalse(CaseRunSession.running)
        val batch=CaseRunSession.batch!!;assertEquals(1,batch.records.size);assertEquals(1,batch.queue.completed)
        val record=batch.records.single();assertEquals(CaseVerdict.SERVICE_ERROR,record.verdict);assertTrue(record.result.stderr.contains("app_process"));assertEquals("",record.result.stdout)
        val key=field<EditorSessions>("sessions").key("main.py");assertEquals(record,CaseRunSession.store(main).load(key).history.single())
        assertTrue(File(main.cacheDir.canonicalFile,"python-runs").listFiles().orEmpty().isEmpty());assertEquals(code,editor.text.toString())
    }
    @Test fun sourceSaveConflictPreventsBatchStartAndKeepsTheRecoverableEditorBytes(){
        val code="print('local')\n";editor.setText(code);ready();workspace.resolve("main.py").writeText("print('external')\n")
        run();drain();assertNull(CaseRunSession.batch);assertFalse(RunSession.running);assertEquals(code,editor.text.toString());assertTrue(field("dirty"))
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing)
    }
    @Test fun cloudCasesRequireTheExistingUploadConsentAndCancelDoesNotStartTheQueue(){
        ReflectionHelpers.callInstanceMethod<Any?>(main,"switchFile",ClassParameter.from(String::class.java,"main.cpp"),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready()
        run();drain();val consent=ShadowAlertDialog.getLatestAlertDialog();assertTrue(consent.isShowing)
        assertTrue(consent.findViewById<android.widget.TextView>(android.R.id.message).text.contains("Wandbox"));assertNull(CaseRunSession.batch)
        consent.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertFalse(main.getSharedPreferences("codepocket_v2",0).getBoolean("wandboxConsent",false))
    }
    @Test fun activityRecreationRestoresTheOpenCaseEditorAndExactWhitespace(){
        newCase();text(dialog(),"用例名称").setText("旋转草稿 😀");text(dialog(),"标准输入").setText("1 2\n尾空格 \t");text(dialog(),"预期输出").setText("3\n")
        controller.recreate();main=controller.get();drain();layout()
        val restored=dialog();assertTrue(restored.isShowing);assertEquals("旋转草稿 😀",text(restored,"用例名称").text.toString());assertEquals("1 2\n尾空格 \t",text(restored,"标准输入").text.toString())
        restored.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();val key=field<EditorSessions>("sessions").key("main.py")
        assertEquals("3\n",CaseRunSession.store(main).load(key).cases.single().expected)
    }
    @Test fun rotatingDuringAnAtomicSaveKeepsTheSameCaseIdInsteadOfAddingADuplicate(){
        newCase();text(dialog(),"用例名称").setText("pending save");text(dialog(),"标准输入").setText("4");text(dialog(),"预期输出").setText("4\n")
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        WorkspaceIo.executor.execute{entered.countDown();check(release.await(10,TimeUnit.SECONDS))};assertTrue(entered.await(10,TimeUnit.SECONDS))
        try { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(text(dialog(),"标准输入").isEnabled);controller.recreate();main=controller.get() } finally { release.countDown() }
        drain();layout();val restored=dialog();assertEquals("4",text(restored,"标准输入").text.toString());restored.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain()
        val key=field<EditorSessions>("sessions").key("main.py");assertEquals(1,CaseRunSession.store(main).load(key).cases.size)
    }
    @Test fun finishingDuringPreparationStopsTheBatchAndCleansItsLateSnapshotWithoutAnExecutedRecord(){
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        WorkspaceIo.executor.execute{entered.countDown();check(release.await(10,TimeUnit.SECONDS))};assertTrue(entered.await(10,TimeUnit.SECONDS))
        try { run();assertTrue(CaseRunSession.running);main.finish();controller.pause().stop().destroy() } finally { release.countDown() }
        drain();assertFalse(CaseRunSession.running);assertTrue(CaseRunSession.batch!!.records.isEmpty())
        assertTrue(File(main.cacheDir.canonicalFile,"python-runs").listFiles().orEmpty().isEmpty())
    }
    @Test fun cloudInputMenuCanCollapseItsOwnInputWithoutDeletingTheText(){
        ReflectionHelpers.callInstanceMethod<Any?>(main,"switchFile",ClassParameter.from(String::class.java,"main.cpp"),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready()
        fun toggle(){ReflectionHelpers.callInstanceMethod<Any?>(main,"dispatchCommand",ClassParameter.from(com.codepocket.ide.ui.IdeCommand::class.java,com.codepocket.ide.ui.IdeCommand.PRESET_INPUT));idle()}
        toggle();val input=field<EditText>("input");assertEquals(View.VISIBLE,input.visibility);input.setText("6 7\n");toggle()
        assertEquals(View.GONE,input.visibility);assertEquals("6 7\n",input.text.toString());assertFalse(field("presetInputExpanded"))
    }
    @Test fun localCppNeverDisplaysTheCloudPresetInputEvenWhenItsExpandedStateWasRemembered(){
        ReflectionHelpers.callInstanceMethod<Any?>(main,"switchFile",ClassParameter.from(String::class.java,"main.cpp"),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready()
        ReflectionHelpers.setField(main,"settings",field<AppSettings>("settings").copy(cppBackend=CppBackend.LOCAL));ReflectionHelpers.setField(main,"presetInputExpanded",true)
        ReflectionHelpers.callInstanceMethod<Any?>(main,"showOutput",ClassParameter.from(Int::class.javaPrimitiveType,3));idle()
        assertEquals(View.GONE,field<EditText>("input").visibility);assertEquals(View.GONE,field<android.widget.LinearLayout>("panelInputHeading").visibility)
    }
    @Test @Config(qualifiers="w360dp-h320dp") fun cloudInputCloseButtonRespondsToTouchAndRemainsClosedAfterRecreation(){
        ReflectionHelpers.callInstanceMethod<Any?>(main,"switchFile",ClassParameter.from(String::class.java,"main.cpp"),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready()
        ReflectionHelpers.callInstanceMethod<Any?>(main,"setPresetInputExpanded",ClassParameter.from(Boolean::class.javaPrimitiveType,true));layout();idle()
        val input=field<EditText>("input");input.setText("exact cloud input \n")
        val close=views(main.findViewById(android.R.id.content)).single{it.contentDescription=="收起云端预设输入"}
        val rect=android.graphics.Rect();assertTrue("Close must stay visible in a short window",close.getGlobalVisibleRect(rect));assertTrue(rect.width()>0&&rect.height()>0)
        val origin=IntArray(2);main.window.decorView.getLocationOnScreen(origin);val time=android.os.SystemClock.uptimeMillis()
        for((action,delay)in listOf(android.view.MotionEvent.ACTION_DOWN to 0L,android.view.MotionEvent.ACTION_UP to 30L)){
            val event=android.view.MotionEvent.obtain(time,time+delay,action,(rect.centerX()-origin[0]).toFloat(),(rect.centerY()-origin[1]).toFloat(),0).apply{source=android.view.InputDevice.SOURCE_TOUCHSCREEN}
            try{main.dispatchTouchEvent(event)}finally{event.recycle()}
        };idle()
        assertEquals(View.GONE,input.visibility);assertEquals("exact cloud input \n",input.text.toString());assertFalse(field("presetInputExpanded"))
        controller.recreate();main=controller.get();drain();layout();assertEquals(View.GONE,field<EditText>("input").visibility);assertEquals("exact cloud input \n",field<EditText>("input").text.toString())
    }
}
