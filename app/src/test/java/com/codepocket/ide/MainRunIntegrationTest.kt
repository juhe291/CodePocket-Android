package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
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
    @After fun close(){RunSession.stop();if(::main.isInitialized){if(!main.isDestroyed){main.finish();controller.pause().stop().destroy()};drain()};ReflectionHelpers.setStaticField(RunSession::class.java,"result",null)}
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
    @Test fun localCppF5SavesSoraCapturesTheProjectAndNeverRequestsCloudConsentOnHostBridgeFailure(){
        switch("main.cpp")
        val current=field<AppSettings>("settings")
        ReflectionHelpers.setField(main,"settings",current.copy(cppBackend=CppBackend.LOCAL,cppBuildScope=CppBuildScope.PROJECT))
        val header=workspace.create(workspace.root,"local-value.hpp",false);workspace.write(header,"constexpr int value=1;\n")
        val draftKey=field<EditorSessions>("sessions").key("local-value.hpp")
        val drafts=field<DraftStore>("drafts");drafts.put(draftKey,"local-value.hpp","constexpr int value=2;\n")
        val code="#include \"local-value.hpp\"\nint main(){return value;}\n"
        edit(code);val content=editor.text;key(KeyEvent.KEYCODE_F5);drain()
        val response=RunSession.result!!
        assertEquals(RunKind.SERVICE,response.kind);assertEquals(CppExecutionService.ENGINE,response.engine)
        assertTrue(response.stderr.contains("app_process"));assertEquals("",response.stdout)
        assertEquals(DocumentTree.digest(code),response.sourceHash);assertEquals(workspace.resolve("main.cpp").path,response.sourcePath)
        assertEquals(code,workspace.resolve("main.cpp").readText());assertSame(content,editor.text)
        assertFalse(main.getSharedPreferences("codepocket_v2",0).getBoolean("wandboxConsent",false));assertFalse(RunSession.running)
        assertEquals("constexpr int value=1;\n",workspace.read(header));assertEquals("constexpr int value=2;\n",drafts.analysisTexts(mapOf("local-value.hpp" to draftKey))["local-value.hpp"])
        assertTrue(File(main.cacheDir,"cpp-runs").listFiles().isNullOrEmpty())
    }
    @Test fun pythonF5PreservesOtherDraftsAndCleansOnlyItsOwnedJobWhenTheHostBridgeCannotStart(){
        val helper=workspace.create(workspace.root,"helper.py",false);workspace.write(helper,"value=1\n")
        val key=field<EditorSessions>("sessions").key("helper.py");val drafts=field<DraftStore>("drafts")
        drafts.put(key,"helper.py","value=42\n",DocumentTree.digest("value=1\n"))
        val code="from helper import value\nprint(value)\n";edit(code);val content=editor.text
        key(KeyEvent.KEYCODE_F5);drain();bridgeFailureFor(code)
        assertSame(content,editor.text);assertEquals("value=1\n",workspace.read(helper))
        assertEquals("value=42\n",drafts.read(key)!!.text);assertEquals(workspace.root.path,RunSession.result!!.projectRoot)
        assertTrue(File(main.cacheDir.canonicalFile,"python-runs").listFiles().isNullOrEmpty())
    }
    @Test fun destroyedActivityDiscardsItsPreparedPythonJobWithoutStartingAProgram(){
        val code=editor.text.toString();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        WorkspaceIo.executor.execute{entered.countDown();check(release.await(10,TimeUnit.SECONDS))};assertTrue(entered.await(10,TimeUnit.SECONDS))
        try{key(KeyEvent.KEYCODE_F5);assertTrue(field("workspaceBusy"));controller.pause().stop().destroy();assertTrue(main.isDestroyed)}finally{release.countDown()}
        drain();assertFalse(RunSession.running);assertNull(RunSession.result)
        assertTrue("Destroyed activity must release the newly prepared job",File(main.cacheDir.canonicalFile,"python-runs").listFiles().isNullOrEmpty())
        assertEquals(code,workspace.read(workspace.resolve("main.py")))
    }
    @Test @Config(qualifiers="w840dp-h1050dp") fun actualHostPythonDraftErrorPassesThroughTheProductionDecoderAndLocatesTheRealSoraHelper(){
        val saved="def fail():\n    return 1\n";val source="def fail():\n    raise ValueError('草稿')\n"
        val helper=workspace.create(workspace.root,"helper_error.py",false);workspace.write(helper,saved)
        val sessions=field<EditorSessions>("sessions");val drafts=field<DraftStore>("drafts")
        drafts.put(sessions.key("helper_error.py"),"helper_error.py",source,DocumentTree.digest(saved))
        val entry=workspace.resolve("main.py");val code="import helper_error\nhelper_error.fail()\n"
        edit(code);key(KeyEvent.KEYCODE_S,KeyEvent.META_CTRL_ON);drain()
        val raw=PythonRunSnapshot.capture(File(main.cacheDir.canonicalFile,"python-runs"),workspace,drafts,sessions.analysisKeys(),"main.py",code).use{job->
            val root=CppFixtures.root
            val bundled=File(root,".tools/python313-full/tools/python.exe")
            val python=System.getProperty("codepocket.hostPython")?:if(bundled.isFile)bundled.path else "python3"
            val process=ProcessBuilder(python,"-B",File(root,"verification/python-project-run-2026-10-28/main-helper-fixture.py").path,job.entry.path,job.root.path,job.manifest.path)
                .redirectErrorStream(true).apply{environment()["PYTHONIOENCODING"]="utf-8";environment()["TEMP"]=File(root,".tools/semantics-tmp").path;environment()["TMP"]=File(root,".tools/semantics-tmp").path}.start()
            try{assertTrue(process.waitFor(20,TimeUnit.SECONDS));val text=process.inputStream.bufferedReader(Charsets.UTF_8).readText();assertEquals(text,0,process.exitValue());text}
            finally{if(process.isAlive)process.destroyForcibly()}
        }
        val decoded=PythonEngine.decode(org.json.JSONObject(raw),"Python · actual host fixture")
        assertEquals(RunKind.RUNTIME,decoded.kind);assertEquals(helper.path,decoded.errorFile);assertEquals(2,decoded.errorLine)
        assertEquals(DocumentTree.digest(source),decoded.errorSourceHash)
        val response=runResultFromBundle(decoded.toBundle(91)).copy(sourcePath=entry.path,projectRoot=workspace.root.path,
            workspaceId=workspace.identity,sourceKey=sessions.key("main.py"),sourceHash=DocumentTree.digest(code))
        ReflectionHelpers.setField(main,"result",response)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"gotoError");drain();ready()
        assertEquals("helper_error.py",field<String>("activePath"));assertEquals(source,editor.text.toString());assertEquals(source.indexOf("    raise"),editor.selectionStart)
        val view=main.findViewById<View>(android.R.id.content)
        view.measure(View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.heightPixels,View.MeasureSpec.EXACTLY));view.layout(0,0,view.measuredWidth,view.measuredHeight);idle()
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val image=File(CppFixtures.root,"verification/python-project-run-2026-10-28/previews/tablet-actual-draft-diagnostic.png")
        image.parentFile!!.mkdirs();image.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        edit(source.replace("草稿","更新"));editor.setSelection(0)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"gotoError");drain();ready()
        assertEquals(0,editor.selectionStart);assertEquals(main.getString(R.string.editor_diagnostic_changed),field<android.widget.TextView>("status").text.toString())
        assertEquals(saved,workspace.read(helper))
    }
    @Test fun localCppStartupFailureShowsItsExactReasonAndDoesNotKeepStealingTheChosenOutputTab(){
        switch("main.cpp");val settings=field<AppSettings>("settings")
        ReflectionHelpers.setField(main,"settings",settings.copy(cppBackend=CppBackend.LOCAL))
        val code="int main(){return 0;}\n";edit(code);key(KeyEvent.KEYCODE_F5);drain()
        val failure=requireNotNull(RunSession.result);assertEquals(RunKind.SERVICE,failure.kind)
        assertTrue(failure.stderr.contains("app_process"));assertEquals(1,field<Int>("selectedOutput"))
        assertEquals(failure.stderr,field<android.widget.TextView>("consoleText").text.toString())
        val view=main.findViewById<View>(android.R.id.content)
        view.measure(View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.heightPixels,View.MeasureSpec.EXACTLY))
        view.layout(0,0,view.measuredWidth,view.measuredHeight);idle()
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val cwd=File(System.getProperty("user.dir")!!);val root=if(cwd.name=="app")cwd.parentFile else cwd
        val image=File(root,"verification/cpp-startup-investigation-2026-10-28/previews/phone-service-failure.png")
        image.parentFile!!.mkdirs();image.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showOutput",ClassParameter.from(Int::class.javaPrimitiveType,3))
        ReflectionHelpers.callInstanceMethod<Unit>(main,"renderRunState");idle()
        assertEquals(3,field<Int>("selectedOutput"));assertEquals(code,editor.text.toString())
    }
    @Test fun localCppHelperDiagnosticChecksTheExecutedHelperHashBeforeMovingTheRealSoraCaret() {
        val helper=workspace.create(workspace.root,"fault.cpp",false)
        val source="int helper(){\n return 7;\n}\n";workspace.write(helper,source)
        val response=RunResult("","fault.cpp:2: runtime fault","signal 11",4,errorLine=2,
            engine=CppExecutionService.ENGINE,kind=RunKind.RUNTIME,errorFile=helper.path,
            sourcePath=workspace.resolve("main.cpp").path,projectRoot=workspace.root.path,workspaceId=workspace.identity,
            errorSourceHash=DocumentTree.digest(source))
        // IPC carries proof for the helper, rather than reusing main.cpp's hash.
        assertEquals(response.errorSourceHash,runResultFromBundle(response.toBundle(71)).errorSourceHash)
        ReflectionHelpers.setField(main,"result",response)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"gotoError");drain();ready()
        assertEquals("fault.cpp",field<String>("activePath"));assertEquals(source.indexOf(" return"),editor.selectionStart)
        edit(source.replace("7","8"));editor.setSelection(0)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"gotoError");drain();ready()
        assertEquals(0,editor.selectionStart);assertEquals(source.replace("7","8"),editor.text.toString())
        val status=field<android.widget.TextView>("status")
        assertEquals(main.getString(R.string.editor_diagnostic_changed),status.text.toString())
        assertEquals(source,workspace.read(helper))
    }
}
