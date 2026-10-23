package com.codepocket.ide

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Actual main workbench, production settings/environment hooks, Sora input and one terminal registry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class MainCaretMotionIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val editor get()=field<WorkbenchEditor>("editor")
    private val motion get()=ReflectionHelpers.getField<Any>(editor.inputView(),"caretMotion")
    private val movement get()=ReflectionHelpers.getField<ValueAnimator?>(motion,"movement")
    private val blinking get()=ReflectionHelpers.getField<ValueAnimator?>(motion,"blinking")
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private fun invoke(key:String){ReflectionHelpers.callInstanceMethod<Unit>(main,key)}
    private fun idle(ms:Long=0){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))}
    private fun settle(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(100){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Main editor layout did not finish")}
    private fun layout(w:Int=main.resources.displayMetrics.widthPixels,h:Int=main.resources.displayMetrics.heightPixels){
        val root=main.findViewById<View>(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);root.viewTreeObserver.dispatchOnGlobalLayout()}
    private fun frame(){val v=editor.inputView();val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));b.recycle()}
    private fun text(s:String){editor.setText(s);ready();editor.setSelection(0);editor.finishMotion();frame()}
    @Before fun launch(){
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();settle();controller.windowFocusChanged(true);layout();editor.requestFocus();ready();frame()
    }
    @After fun close(){
        Settings.Global.putFloat(main.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        Shadows.shadowOf(main.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(false)
        main.finish();controller.pause().stop().destroy();settle()
    }
    private fun key(code:Int,meta:Int=0){main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0,meta));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,code,0,meta))}
    private fun apply(next:AppSettings){var done=false;ReflectionHelpers.callInstanceMethod<Unit>(main,"applySettings",ClassParameter.from(AppSettings::class.java,next),
        ClassParameter.from(kotlin.jvm.functions.Function1::class.java,{success:Boolean->done=success}));assertTrue(done);ready()}
    private fun capture(tag:String){val root=main.findViewById<View>(android.R.id.content);val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(b))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd
        val folder=File(repo,"verification/main-app-caret-motion-2026-10-23/previews").apply{mkdirs()}
        File(folder,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}

    @Test fun phoneMainImeCommitsImmediatelyAndUsesTheRequestedTypingAndBackspaceDuration(){
        text("abcdef");val content=editor.text;val ic=editor.onCreateInputConnection(EditorInfo());assertTrue(ic.commitText("x",1))
        assertSame(content,editor.text);assertEquals("xabcdef",editor.text.toString());assertEquals(1,editor.selectionStart);assertEquals(60,movement!!.duration)
        movement!!.setCurrentFraction(.5f);assertTrue(ic.deleteSurroundingText(1,0));assertEquals("abcdef",editor.text.toString());assertEquals(0,editor.selectionStart);assertEquals(60,movement!!.duration)
        assertTrue(field("dirty"));assertEquals("main.py",field<String>("loadedPath"))
    }
    @Test fun actualHardwareArrowChangesLogicalSelectionBeforeEightyMillisecondTransition(){
        text("abcdef");val content=editor.text;key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(1,editor.selectionStart);assertSame(content,editor.text);assertEquals(80,movement!!.duration);assertTrue(movement!!.isRunning)
        val anchor=editor.anchor()!!;editor.finishMotion();assertFalse(movement?.isRunning==true);assertEquals(anchor.x,editor.anchor()!!.x)
    }
    @Test fun completionShowAlignsTheCaretEvenBeforeAnExtendedTweenCanFinish(){
        text("pri");editor.setSelection(3);assertTrue(movement!!.isRunning);movement!!.duration=5000
        ReflectionHelpers.callInstanceMethod<Unit>(main,"updateSuggestions",ClassParameter.from(Boolean::class.javaPrimitiveType,true))
        field<ExecutorService>("completionExecutor").submit{}.get(10,TimeUnit.SECONDS);idle()
        assertTrue(field<CompletionPopup?>("completionPopup")?.isShowing==true);assertFalse(movement?.isRunning==true)
        assertEquals(3,editor.selectionStart);assertTrue(editor.hasFocus());key(KeyEvent.KEYCODE_TAB);assertEquals("print",editor.text.toString())
    }
    @Test fun actualSettingsApplySeparatelyControlsMovementAndBlinkWithoutReplacingTheBuffer(){
        text("abcdef");val content=editor.text;val registry=field<ShellSessionRegistry>("terminalRegistry");val entries=registry.catalog.entries().map{it.id}
        apply(field<AppSettings>("settings").copy(cursorMovement=false));editor.setSelection(4);frame();assertFalse(movement?.isRunning==true);idle(650);assertNotNull(blinking)
        apply(field<AppSettings>("settings").copy(cursorMovement=true,cursorBlink=false));editor.setSelection(2);assertEquals(80,movement!!.duration);idle(1200);assertNull(blinking)
        assertSame(content,editor.text);assertEquals(entries,registry.catalog.entries().map{it.id})
    }
    @Test fun systemReducedAnimationSettingCancelsActualMainEditorAnimations(){
        text("abcdef");editor.setSelection(3);assertTrue(movement!!.isRunning)
        Settings.Global.putFloat(main.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f);idle();frame()
        assertFalse(movement?.isRunning==true);idle(700);assertNull(blinking);assertEquals(3,editor.selectionStart)
    }
    @Test fun powerSavingBroadcastStopsAnimationsAndKeepsTheDocument(){
        text("abcdef");val content=editor.text;editor.setSelection(3);assertTrue(movement!!.isRunning)
        Shadows.shadowOf(main.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        main.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));idle();frame()
        assertFalse(movement?.isRunning==true);idle(700);assertNull(blinking);assertSame(content,editor.text)
    }
    @Test fun mainLifecyclePauseReleasesCaretCallbacksAndResumeRetainsSelection(){
        text("abcdef");editor.setSelection(3);val content=editor.text;controller.pause();idle(700)
        assertFalse(movement?.isRunning==true);assertNull(blinking);controller.resume();controller.windowFocusChanged(true);layout();ready();frame();idle(650)
        assertSame(content,editor.text);assertEquals(3,editor.selectionStart);assertNotNull(blinking)
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightWindowResizeKeepsPanelsSessionsAndCancelsOnlyTheCaretTransition(){
        text("value = 42\nprint(value)\n");val content=editor.text;val registry=field<ShellSessionRegistry>("terminalRegistry");val entries=registry.catalog.entries().map{it.id}
        assertTrue(field("panelVisible"));val state=field<PanelState>("panelState");assertEquals(.35f,state.bottomFraction,0f)
        editor.setSelection(4);assertTrue(movement!!.isRunning);layout(880,700);ready();frame();assertFalse(movement?.isRunning==true)
        assertSame(content,editor.text);assertEquals(4,editor.selectionStart);assertEquals(state,field<PanelState>("panelState"));assertEquals(entries,registry.catalog.entries().map{it.id});assertTrue(field("panelVisible"))
        capture("tablet-light-host-workbench")
    }
    @Test fun phoneCompositionAndBackgroundFocusKeepTheCursorAndTerminalOwnershipAccurate(){
        text("print()\n");val registry=field<ShellSessionRegistry>("terminalRegistry");val entries=registry.catalog.entries().map{it.id};val ic=editor.onCreateInputConnection(EditorInfo())
        assertTrue(ic.setComposingText("拼音",1));frame();assertTrue(editor.isComposing);assertFalse(movement?.isRunning==true);assertNull(blinking)
        assertTrue(ic.finishComposingText());frame();assertEquals(2,editor.selectionStart);assertEquals(entries,registry.catalog.entries().map{it.id})
        capture("phone-dark-host-workbench")
    }
}
