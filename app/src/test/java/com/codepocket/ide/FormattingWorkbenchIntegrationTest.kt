package com.codepocket.ide

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.IdeCommand
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Actual Main, native Sora Content/undo, WorkspaceIo and real host formatter engines. Android IPC is tested separately. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class FormattingWorkbenchIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private lateinit var backend:HostFormatting
    private val requests=AtomicInteger()
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun settled(){field<ExecutorService>("formattingExecutor").submit{}.get(25,TimeUnit.SECONDS);idle();drain()}
    private fun dispatch(command:IdeCommand){ReflectionHelpers.callInstanceMethod<Unit>(main,"dispatchCommand",ClassParameter.from(IdeCommand::class.java,command));idle()}
    private fun save(){ReflectionHelpers.callInstanceMethod<Boolean>(main,"saveActive",ClassParameter.from(Function0::class.java,null));idle()}
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun disk(path:String="main.py")=File(main.filesDir,"workspace/$path")
    private fun launch(autoFormat:Boolean=false,cpp:Boolean=false){
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("codepocket_v2",0).edit().clear().putString("settings",AppSettings(animations=false,
            autoSave=false,autoCompletion=false,signatureHelp=false,formatOnSave=autoFormat).toJson())
            .putString("activeFile",if(cpp)"main.cpp" else "main.py").commit()
        Workspace(app)
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val root=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(2){root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));root.layout(0,0,root.measuredWidth,root.measuredHeight);root.viewTreeObserver.dispatchOnGlobalLayout();idle()}
        backend=HostFormatting(File(main.cacheDir,"format-test").apply{mkdirs()}.canonicalFile)
        install(object:SemanticTransport{override fun analyze(payload:String):String?{requests.incrementAndGet();return backend.analyze(payload)};override fun close()=backend.close()})
        editor.requestFocus()
    }
    private fun install(transport:SemanticTransport){ReflectionHelpers.setField(main,"formattingClient",CodeFormattingClient(transport))}
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){ReflectionHelpers.setField(main,"settings",field<AppSettings>("settings").copy(formatOnSave=false));main.finish();controller.pause().stop().destroy()};if(::backend.isInitialized)backend.close();drain()}
    @Test fun manualFormattingIsOneNativeUndoAndNeverImplicitlySavesTheFile(){
        launch();val original=disk().readText();val source="value=1+2\nprint(value)\n"
        editor.setText(source);editor.setSelection(source.indexOf("value",1));dispatch(IdeCommand.FORMAT_DOCUMENT);settled()
        val formatted=editor.text.toString();assertEquals("value = 1 + 2\nprint(value)\n",formatted)
        assertEquals(formatted.lastIndexOf("value"),editor.selectionStart);assertEquals(original,disk().readText());assertTrue(field("dirty"))
        assertTrue(editor.onTextContextMenuItem(android.R.id.undo));assertEquals(source,editor.text.toString())
        assertTrue(editor.onTextContextMenuItem(android.R.id.redo));assertEquals(formatted,editor.text.toString())
        assertEquals(1,requests.get())
    }
    @Test fun hardwareShortcutUsesTheSameEngineAndRepeatedKeyDownDoesNotQueueAnotherRequest(){
        launch();editor.setText("a=1+2\n");editor.setSelection(0)
        val meta=KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON
        assertTrue(main.dispatchKeyEvent(KeyEvent(0,1,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_F,0,meta)))
        assertTrue(main.dispatchKeyEvent(KeyEvent(0,2,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_F,1,meta)))
        settled();assertEquals("a = 1 + 2\n",editor.text.toString());assertEquals(1,requests.get())
        assertTrue(main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_F)))
    }
    @Test fun newTypingRejectsALateResultAndKeepsCurrentBufferAndUndo(){
        launch();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        install(object:SemanticTransport{override fun analyze(payload:String):String?{entered.countDown();check(release.await(10,TimeUnit.SECONDS));return backend.analyze(payload)};override fun close(){release.countDown();backend.close()}})
        editor.setText("a=1\n");editor.setSelection(0);dispatch(IdeCommand.FORMAT_DOCUMENT);assertTrue(entered.await(5,TimeUnit.SECONDS))
        editor.edit(0,0,"# new edit\n",11,11);release.countDown();settled()
        assertEquals("# new edit\na=1\n",editor.text.toString());assertTrue(field<TextView>("status").text.contains("已取消"))
        assertTrue(editor.onTextContextMenuItem(android.R.id.undo));assertEquals("a=1\n",editor.text.toString())
    }
    @Test fun caretMovementRejectsALateResultInsteadOfMovingTheSelectionBack(){
        launch();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        install(object:SemanticTransport{override fun analyze(payload:String):String?{entered.countDown();check(release.await(10,TimeUnit.SECONDS));return backend.analyze(payload)};override fun close(){release.countDown();backend.close()}})
        editor.setText("a=1\n");editor.setSelection(0);dispatch(IdeCommand.FORMAT_DOCUMENT);assertTrue(entered.await(5,TimeUnit.SECONDS))
        editor.setSelection(2);release.countDown();settled();assertEquals("a=1\n",editor.text.toString());assertEquals(2,editor.selectionStart)
    }
    @Test fun saveFormatsOnceWritesThroughTheOriginalVersionGuardAndUndoRemainsAvailable(){
        launch(true);editor.setText("a=1+2\n");editor.setSelection(0);save();settled()
        assertEquals("a = 1 + 2\n",editor.text.toString());assertEquals(editor.text.toString(),disk().readText())
        assertFalse(field("saving"));assertFalse(field("dirty"));assertEquals(1,requests.get())
        assertTrue(editor.onTextContextMenuItem(android.R.id.undo));assertEquals("a=1+2\n",editor.text.toString());assertTrue(field("dirty"))
    }
    @Test fun formatterFailureStillSavesTheUnchangedSourceWithoutARepeatLoop(){
        launch(true);install(object:SemanticTransport{override fun analyze(payload:String):String?{requests.incrementAndGet();return CodeFormatting.failure("timeout")};override fun close(){}})
        val source="a=1+2\n";editor.setText(source);save();settled()
        assertEquals(source,editor.text.toString());assertEquals(source,disk().readText());assertEquals(1,requests.get())
        assertFalse(field("saving"));assertFalse(field("dirty"));assertTrue(field<TextView>("status").text.contains("超时"))
    }
    @Test fun formattingCannotBypassAnExternalSaveConflictOrDiscardTheFormattedDraft(){
        launch(true);editor.setText("a=1+2\n");disk().writeText("# external update\n")
        save();settled();assertEquals("# external update\n",disk().readText());assertEquals("a = 1 + 2\n",editor.text.toString())
        assertTrue(field("dirty"));assertFalse(field("saving"));assertTrue(field<TextView>("status").text.contains("保存失败"))
    }
    @Test fun saveFormattingKeepsCloudStdinFocusAndNeverChangesItsText(){
        launch(true,true);editor.setText("int main(){return 0;}\n");editor.setSelection(0)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"setPresetInputExpanded",ClassParameter.from(Boolean::class.javaPrimitiveType!!,true));idle()
        val input=field<EditText>("input");input.setText("42\n");assertTrue(input.requestFocus());assertTrue(input.hasFocus())
        save();settled();assertTrue(input.hasFocus());assertFalse(editor.hasFocus());assertEquals("42\n",input.text.toString())
        assertEquals(editor.text.toString(),disk("main.cpp").readText());assertEquals(1,requests.get())
    }
    @Test fun typingDuringSaveFormattingSavesTheLatestRawBufferOnceWithoutOverwritingIt(){
        launch(true);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        install(object:SemanticTransport{override fun analyze(payload:String):String?{requests.incrementAndGet();entered.countDown();check(release.await(10,TimeUnit.SECONDS));return backend.analyze(payload)};override fun close(){release.countDown();backend.close()}})
        editor.setText("a=1\n");editor.setSelection(0);save();assertTrue(entered.await(5,TimeUnit.SECONDS))
        editor.edit(0,0,"# latest\n",9,9);release.countDown();settled()
        assertEquals("# latest\na=1\n",editor.text.toString());assertEquals(editor.text.toString(),disk().readText())
        assertEquals(1,requests.get());assertFalse(field("saving"));assertFalse(field("dirty"))
    }
    @Test fun fileSwitchRejectsThePreviousDocumentsFormattingResult(){
        launch();val entered=CountDownLatch(1);val release=CountDownLatch(1);val cpp=disk("main.cpp").readText()
        install(object:SemanticTransport{override fun analyze(payload:String):String?{entered.countDown();check(release.await(10,TimeUnit.SECONDS));return backend.analyze(payload)};override fun close(){release.countDown();backend.close()}})
        editor.setText("a=1\n");editor.setSelection(0);dispatch(IdeCommand.FORMAT_DOCUMENT);assertTrue(entered.await(5,TimeUnit.SECONDS))
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,"main.cpp"),ClassParameter.from(Function0::class.java,null));drain()
        release.countDown();settled();assertEquals("main.cpp",field<String>("activePath"));assertEquals(cpp,editor.text.toString());assertEquals("a=1\n",disk().readText())
    }
    @Test fun activeCompositionRejectsTheLateResultAndKeepsTheImeText(){
        launch();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        install(object:SemanticTransport{override fun analyze(payload:String):String?{requests.incrementAndGet();entered.countDown();check(release.await(10,TimeUnit.SECONDS));return backend.analyze(payload)};override fun close(){release.countDown();backend.close()}})
        editor.setText("a=1\n");editor.setSelection(0);dispatch(IdeCommand.FORMAT_DOCUMENT);assertTrue(entered.await(5,TimeUnit.SECONDS))
        val connection=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo());assertTrue(connection.setComposingText("中",1));assertTrue(editor.isComposing)
        dispatch(IdeCommand.FORMAT_DOCUMENT);release.countDown();settled();assertEquals("中a=1\n",editor.text.toString());assertTrue(editor.isComposing);assertEquals(1,requests.get())
        connection.finishComposingText()
    }
    @Test fun normalAutoSaveUsesTheFormatPolicyAndDoesNotScheduleAFormattingLoop(){
        launch(true);ReflectionHelpers.setField(main,"settings",field<AppSettings>("settings").copy(autoSave=true,autoSaveDelayMs=300))
        editor.setText("a=1\n");editor.setSelection(0)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350));settled()
        assertEquals("a = 1\n",disk().readText());assertFalse(field("dirty"));assertEquals(1,requests.get())
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));settled();assertEquals(1,requests.get())
    }
    @Test fun defaultDisabledPolicySavesTheExactTextWithoutStartingAnEngine(){
        launch();val source="a=1+2\n";editor.setText(source);save();drain()
        assertEquals(source,disk().readText());assertEquals(source,editor.text.toString());assertEquals(0,requests.get());assertFalse(field("dirty"))
    }
}
