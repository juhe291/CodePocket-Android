package com.codepocket.ide

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.codepocket.ide.animation.Animations
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
import java.time.Duration

/** Real attached Android Views/animators on the host, not a device FPS or IME test. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class WorkbenchMotionTest {
    private lateinit var activity: Activity
    private lateinit var parent: LinearLayout
    private lateinit var decoration: View
    private lateinit var editor: EditText
    private fun settle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)) }
    @Before fun prepare() {
        activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        parent=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        decoration=View(activity); editor=EditText(activity).apply { setText("print('hello')");setSelection(5) }
        parent.addView(decoration,LinearLayout.LayoutParams(200,48));parent.addView(editor)
        activity.setContentView(parent);editor.requestFocus()
    }
    @After fun cleanup() { Animations.cancelTree(parent) }

    @Test fun reopenDuringHideCannotBeHiddenByTheOldEndCallback() {
        Animations.slideOut(decoration,-200f)
        Animations.slideIn(decoration,-200f)
        settle()
        assertEquals(View.VISIBLE,decoration.visibility);assertEquals(0f,decoration.translationX,0f)
        assertEquals(1f,decoration.alpha,0f);assertNull(decoration.getTag(R.id.workbench_motion))
    }
    @Test fun cancellingAPendingCloseSettlesItsVisibility() {
        Animations.slideOut(decoration,-200f);Animations.cancel(decoration);settle()
        assertEquals(View.GONE,decoration.visibility);assertEquals(0f,decoration.translationX,0f)
    }
    @Test fun disablingMotionDuringPopupEntranceRestoresOpacityImmediately() {
        Animations.popup(decoration,false,true);assertEquals(.85f,decoration.alpha,0f)
        Animations.popup(decoration,true,false)
        assertEquals(1f,decoration.alpha,0f);assertNull(decoration.getTag(R.id.workbench_motion))
        settle();assertEquals(View.VISIBLE,decoration.visibility)
    }
    @Test fun candidateUpdatesDoNotRestartTheEntrance() {
        Animations.popup(decoration,false,true)
        val motion=decoration.getTag(R.id.workbench_motion)
        assertNotNull(motion)
        Animations.popup(decoration,true,true)
        assertSame(motion,decoration.getTag(R.id.workbench_motion))
        settle();Animations.popup(decoration,true,true)
        assertNull(decoration.getTag(R.id.workbench_motion));assertEquals(1f,decoration.alpha,0f)
    }
    @Test fun leavingTheWindowCancelsAndSettlesThePendingDecoration() {
        Animations.fadeOut(decoration);parent.removeView(decoration)
        assertNull(decoration.getTag(R.id.workbench_motion));assertEquals(View.GONE,decoration.visibility)
        assertEquals(1f,decoration.alpha,0f)
    }
    @Test fun cancellingTheWorkbenchAlsoFinishesNestedChevronRotation() {
        Animations.rotate(decoration,90f,true);Animations.cancelTree(parent)
        assertEquals(90f,decoration.rotation,0f);assertNull(decoration.getTag(R.id.workbench_motion))
        assertTrue(editor.hasFocus());assertEquals(5,editor.selectionStart)
    }
    @Test fun disabledAndNonFiniteMotionNeverLeaveTranslatedOrInvisibleContent() {
        Animations.slideIn(decoration,Float.NaN)
        assertNull(decoration.getTag(R.id.workbench_motion));assertEquals(0f,decoration.translationX,0f)
        Animations.fadeOut(decoration,enabled=false)
        assertEquals(View.GONE,decoration.visibility)
        Animations.fadeIn(decoration,enabled=false)
        assertEquals(View.VISIBLE,decoration.visibility);assertEquals(1f,decoration.alpha,0f)
    }
    @Test fun systemAnimatorDisableSkipsEntranceAndRotation() {
        org.robolectric.util.ReflectionHelpers.callStaticMethod<Any?>(android.animation.ValueAnimator::class.java,"setDurationScale",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType!!,0f))
        try {
            Animations.fadeIn(decoration);Animations.rotate(decoration,90f,true)
            assertEquals(1f,decoration.alpha,0f);assertEquals(90f,decoration.rotation,0f)
            assertNull(decoration.getTag(R.id.workbench_motion))
        } finally {
            org.robolectric.util.ReflectionHelpers.callStaticMethod<Any?>(android.animation.ValueAnimator::class.java,"setDurationScale",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType!!,1f))
        }
    }
    @Test fun transientFadeWithoutHidePreservesVisibilityAndInput() {
        Animations.fadeOut(decoration,hideOnComplete=false);settle()
        assertEquals(View.VISIBLE,decoration.visibility);assertEquals(1f,decoration.alpha,0f)
        assertTrue(editor.hasFocus());assertEquals(5,editor.selectionStart)
    }
    @Test fun rapidRepeatedOpenCloseEndsAtTheLastRequestedState() {
        repeat(20) { Animations.fadeOut(decoration);Animations.fadeIn(decoration) }
        Animations.fadeOut(decoration);settle()
        assertEquals(View.GONE,decoration.visibility);assertEquals(1f,decoration.alpha,0f)
        assertNull(decoration.getTag(R.id.workbench_motion))
    }
}
