package com.codepocket.ide

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import com.codepocket.ide.animation.EditorCaretMotion
import com.codepocket.ide.editor.NativeEditorSurface
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/** Actual platform text layout/input connection; asserts logical caret independently of its visual tween. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class EditorCaretMotionTest {
    private lateinit var editor: CodeEditor
    private lateinit var motion: EditorCaretMotion
    private lateinit var activity: Activity
    private lateinit var controller: org.robolectric.android.controller.ActivityController<Activity>
    private val bitmap=Bitmap.createBitmap(600,300,Bitmap.Config.ARGB_8888)
    private fun frame(){editor.draw(Canvas(bitmap))}
    private fun idle(ms:Long){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))}
    @Before fun prepare(){
        controller=Robolectric.buildActivity(Activity::class.java).setup().visible()
        activity=controller.get();editor=CodeEditor(activity).apply {
            gravity=Gravity.TOP or Gravity.START;setText("first line\n你好😀 second line");setSelection(1)
            setPadding(10,12,8,9);textSize=16f
        }
        activity.setContentView(editor);controller.windowFocusChanged(true)
        editor.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY))
        editor.layout(0,0,600,300);editor.requestFocus()
        motion=ReflectionHelpers.getField(editor,"caretMotion")
        editor.configureMotion(true,true,true,0xff4488ff.toInt());frame()
    }
    @After fun release(){motion.stop();bitmap.recycle()}
    @Test fun nativeCursorAndInputConnectionStayEnabled(){
        assertTrue(editor.isCursorVisible)
        assertNotNull(editor.onCreateInputConnection(EditorInfo()))
        assertNotNull(editor.textCursorDrawable)
    }
    @Test fun logicalCaretAndPopupAnchorUseTheSameActualLayoutCoordinates(){
        val caret=motion.logicalBounds()!!
        val anchor=NativeEditorSurface.capture(editor,"file",1)!!
        val origin=IntArray(2);editor.getLocationOnScreen(origin)
        assertEquals(anchor.x.toFloat(),origin[0]+caret.left-editor.scrollX,.6f)
        assertEquals(anchor.top.toFloat(),origin[1]+caret.top-editor.scrollY,.6f)
        assertEquals(anchor.bottom.toFloat(),origin[1]+caret.bottom-editor.scrollY,.6f)
    }
    @Test fun navigationChangesRealSelectionBeforeTheVisualMotionFinishes(){
        val start=motion.logicalBounds()!!;editor.setSelection(12);frame()
        assertEquals(12,editor.selectionStart);assertTrue(editor.text.toString().startsWith("first"))
        val visual=ReflectionHelpers.getField<RectF>(motion,"current")
        assertEquals(start.top,visual.top,.1f)
        assertNotNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"movement"))
        ReflectionHelpers.getField<android.animation.ValueAnimator>(motion,"movement").setCurrentFraction(1f)
        assertEquals(motion.logicalBounds()!!.top,ReflectionHelpers.getField<RectF>(motion,"current").top,.1f)
    }
    @Test fun typingAnimatesOnlyTheVisibleCaretAndDoesNotDelayNativeText(){
        editor.setSelection(3);frame()
        editor.text.insert(3,"x");editor.setSelection(4);frame()
        assertEquals("firxst line\n你好😀 second line",editor.text.toString())
        assertEquals(4,editor.selectionStart)
        val tween=ReflectionHelpers.getField<android.animation.ValueAnimator>(motion,"movement")
        assertEquals(60L,tween.duration);tween.setCurrentFraction(1f)
        assertEquals(motion.logicalBounds(),ReflectionHelpers.getField<RectF>(motion,"current"))
    }
    @Test fun composingInputIsNativeAndNeverInterpolatesTheInsertionPoint(){
        val connection=editor.onCreateInputConnection(EditorInfo())!!
        assertTrue(connection.setComposingText("拼音",1));frame()
        assertTrue(BaseInputConnection.getComposingSpanStart(editor.text)>=0)
        assertNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"movement"))
        connection.finishComposingText();assertTrue(connection.commitText("好",1));frame()
        assertTrue(editor.text.toString().contains("拼音好"));assertTrue(editor.isCursorVisible)
    }
    @Test fun selectedTextHidesTheCustomCaretWithoutChangingSelection(){
        editor.setSelection(1,6);frame();assertNull(motion.logicalBounds())
        assertEquals(1,editor.selectionStart);assertEquals(6,editor.selectionEnd)
    }
    @Test fun disablingBlinkLeavesAnOpaqueCaretAndNoRepeatingAnimator(){
        idle(900);assertNotNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"blinking"))
        editor.configureMotion(true,true,false,0xff4488ff.toInt());frame();idle(1200)
        assertEquals(1f,ReflectionHelpers.getField<Float>(motion,"opacity"),0f)
        assertNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"blinking"))
    }
    @Test fun smoothBlinkHasIntermediateOpacityAndNeverChangesTextOrFocus(){
        val text=editor.text.toString();idle(850)
        // Host frame timing is not a device clock; drive the actual platform animator deterministically.
        ReflectionHelpers.getField<android.animation.ValueAnimator>(motion,"blinking").setCurrentFraction(.25f)
        val alpha=ReflectionHelpers.getField<Float>(motion,"opacity")
        assertTrue(alpha in .15f..1f);assertTrue(alpha<.99f)
        assertEquals(text,editor.text.toString());assertEquals(1,editor.selectionStart);assertTrue(editor.hasFocus())
    }
    @Test fun disablingAllMotionSnapsNavigationAndKeepsTheCaretVisible(){
        editor.configureMotion(false,true,true,0xff4488ff.toInt());editor.setSelection(5);frame();idle(1000)
        assertEquals(motion.logicalBounds(),ReflectionHelpers.getField<RectF>(motion,"current"))
        assertEquals(1f,ReflectionHelpers.getField<Float>(motion,"opacity"),0f)
        assertNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"blinking"))
    }
    @Test fun losingFocusOrDetachingStopsAllAnimationCallbacks(){
        // A real window-focus callback exercises the CodeEditor hook without touching its document.
        controller.windowFocusChanged(false);frame();idle(1000)
        assertNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"blinking"))
        assertNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"movement"))
        assertEquals(1,editor.selectionStart)
    }
    @Test fun multilineEmojiAndScrolledCoordinatesStayAtNativeUtf16Offsets(){
        editor.setSelection(16);frame()
        val layout=editor.layout;val line=layout.getLineForOffset(16);val caret=motion.logicalBounds()!!
        assertEquals(editor.compoundPaddingLeft+layout.getPrimaryHorizontal(16),caret.left,.1f)
        assertEquals((editor.totalPaddingTop+layout.getLineTop(line)).toFloat(),caret.top,.1f)
    }
    @Test fun animatedDamageIncludesThePreviouslyDrawnCaretPixels() {
        editor.setSelection(12);frame()
        val animator=ReflectionHelpers.getField<android.animation.ValueAnimator>(motion,"movement")
        animator.setCurrentFraction(.25f);frame()
        val old=RectF(ReflectionHelpers.getField<RectF>(motion,"lastDrawn"))
        animator.setCurrentFraction(.75f)
        val dirty=ReflectionHelpers.callInstanceMethod<RectF>(motion,"damageBounds")
        assertTrue(dirty.contains(old));assertTrue(dirty.contains(motion.logicalBounds()!!))
        assertEquals(12,editor.selectionStart)
    }
    @Test fun movementCanBeDisabledIndependentlyOfSoftBlink() {
        editor.configureMotion(true,false,true,0xff4488ff.toInt());editor.setSelection(12);frame();idle(900)
        assertNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"movement"))
        assertNotNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"blinking"))
    }
    @Test fun detachingTheActualEditorStopsItsBlinkWithoutCallingTheControllerManually() {
        idle(900);activity.setContentView(View(activity));idle(1000)
        assertNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"blinking"))
        assertNull(ReflectionHelpers.getField<android.animation.ValueAnimator?>(motion,"movement"))
    }

}
