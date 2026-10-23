package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.codepocket.ide.terminal.shell.*
import com.codepocket.ide.ui.TerminalStatusCard
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.IOException

/** Actual runtime failure log/state and actual Views; never claims a host PTY is running. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class TerminalStatusPresentationTest {
    private lateinit var activity:Activity
    private lateinit var controller:ActivityController<Activity>
    private lateinit var runtime:ShellRuntime
    private lateinit var pane:ShellTerminalPane
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun label(text:String)=views(pane).filterIsInstance<TextView>().single{it.text.toString()==text}
    private fun measure(v:View,w:Int,h:Int){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)}
    @Before fun launch(){
        controller=Robolectric.buildActivity(Activity::class.java).setup().visible();activity=controller.get()
        runtime=ShellRuntime.create(activity);pane=ShellTerminalPane(activity,null,12,false,{},runtime);pane.setEmbedded(true)
        activity.setContentView(pane);controller.windowFocusChanged(true);Shadows.shadowOf(Looper.getMainLooper()).idle();measure(pane,360,480)
    }
    @After fun close(){pane.dispose();runtime.shutdown();controller.pause().stop().destroy();Shadows.shadowOf(Looper.getMainLooper()).idle()}
    @Test fun embeddedFailureShowsFriendlyActionsAndKeepsTheFullPrivateLog(){
        runtime.fail(IOException("private launch failure\n"+"diagnostic line\n".repeat(100)))
        assertNotNull(label(activity.getString(R.string.shell_status_failed_title)));assertNotNull(label(activity.getString(R.string.shell_status_retry)))
        assertFalse(views(pane).filterIsInstance<TextView>().any{it.text.contains("private launch failure")})
        assertTrue(runtime.failureDetails().contains("private launch failure"));assertEquals(ShellRuntime.State.FAILED,runtime.state());assertNull(runtime.session())
        measure(pane,360,480);for(id in listOf(R.string.shell_status_retry,R.string.shell_status_details)){val b=label(activity.getString(id));assertTrue(b.width>=48);assertTrue(b.height>=48)}
    }
    @Test fun detailsAreScrollableSelectableAndCopyTheCompleteSavedRecord(){
        runtime.fail(IOException("details marker\n"+"line\n".repeat(100)));val report=runtime.failureDetails()
        label(activity.getString(R.string.shell_status_details)).performClick();val d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing)
        val content=views(d.window!!.decorView).filterIsInstance<TextView>().single{it.contentDescription==activity.getString(R.string.shell_failure_content)}
        assertEquals(report,content.text.toString());assertTrue(content.isTextSelectable);assertTrue(content.parent is ScrollView)
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
        val clipboard=activity.getSystemService(ClipboardManager::class.java);assertEquals(report,clipboard.primaryClip!!.getItemAt(0).text.toString());assertNull(runtime.session())
    }
    @Test fun retryUsesOneExistingRuntimeAndPreparingStatePreventsDuplicateLaunch(){
        runtime.fail(IOException("retry marker"));val before=ReflectionHelpers.getField<Long>(runtime,"generation")
        val retry=label(activity.getString(R.string.shell_status_retry));retry.performClick();assertEquals(ShellRuntime.State.PREPARING,runtime.state())
        assertFalse(retry.isEnabled);repeat(5){retry.performClick()};assertEquals(before+1,ReflectionHelpers.getField<Long>(runtime,"generation"))
        assertNotNull(label(activity.getString(R.string.shell_status_preparing)));assertNull(runtime.session())
    }
    @Test fun shortLargeFontCardScrollsRatherThanClippingTheActionTargets(){
        val config=android.content.res.Configuration(activity.resources.configuration).apply{fontScale=2f};val context=activity.createConfigurationContext(config)
        val card=TerminalStatusCard(context,{}, {}, {});card.bind(true,false);measure(card,240,100)
        assertTrue(card.getChildAt(0).height>card.height)
        val buttons=views(card).filterIsInstance<TextView>().filter{it.isClickable};assertEquals(2,buttons.size)
        buttons.forEach{assertTrue(it.width>=48);assertTrue(it.height>=48)};card.fullScroll(View.FOCUS_DOWN)
    }
    @Test fun backgroundFailureDoesNotStealTheCodeInputFocus(){
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL};val code=EditText(activity).apply{setText("keep code");setSelection(4)}
        (pane.parent as ViewGroup).removeView(pane);root.addView(code);root.addView(pane,LinearLayout.LayoutParams(-1,0,1f));activity.setContentView(root);code.requestFocus()
        runtime.fail(IOException("background marker"));assertTrue(code.hasFocus());assertEquals(4,code.selectionStart);assertEquals("keep code",code.text.toString())
    }
}
