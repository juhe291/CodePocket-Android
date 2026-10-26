package com.codepocket.ide

import android.app.AlertDialog
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.editor.InlineIssue
import com.codepocket.ide.terminal.shell.*
import com.codepocket.ide.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Real native readers and their actual settings/copy callbacks; no simulated program results. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class ReadOnlyDetailsIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun words(v:View)=views(v).filterIsInstance<TextView>().map{it.text.toString()}
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real Sora layout unavailable")}
    private fun layout(w:Int=main.resources.displayMetrics.widthPixels,h:Int=main.resources.displayMetrics.heightPixels){val v=main.findViewById<View>(android.R.id.content)
        // Constrain the actual host: a bare manual layout is undone by ViewRoot's next traversal.
        v.layoutParams=v.layoutParams.apply{width=w;height=h}
        repeat(2){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun settings():AlertDialog{ReflectionHelpers.callInstanceMethod<Unit>(main,"showSettings");idle();return field("toolDialog")}
    private fun category(d:AlertDialog,title:String){views(d.window!!.decorView).single{it.contentDescription==title}.performClick();idle()}
    private fun latest()=ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing)}
    private fun reader(title:String,text:String):AlertDialog{ReflectionHelpers.callInstanceMethod<Unit>(main,"showReadOnlyDetails",ClassParameter.from(String::class.java,title),ClassParameter.from(String::class.java,text));idle();return latest()}
    private fun bounded(d:AlertDialog){idle();assertTrue("Reader width ${d.window!!.decorView.width}",d.window!!.decorView.width<=IdeControls.dp(main,640));assertTrue(d.window!!.decorView.width>0)}
    private fun content(d:AlertDialog,text:String)=views(d.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()==text}
    private fun capture(tag:String,v:View){idle();val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(DesignTokens.forContext(main).overlay);v.draw(c)
        val cwd=File(System.getProperty("user.dir")!!);val r=if(cwd.name=="app")cwd.parentFile else cwd;val dir=File(r,"verification/workbench-details-batch-2026-10-26/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    @Before fun launch(){RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();assertTrue(editor.requestFocus());ready()}
    @After fun close(){if(::main.isInitialized){ShadowAlertDialog.getLatestAlertDialog()?.takeIf{it.isShowing}?.dismiss();main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}

    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletSettingsKeyHelpHasAReadingWidthAndClosesToTheOriginalDraft(){
        val buffer=editor.text;val d=settings();category(d,"补全与快捷键")
        val original=field<AppSettings>("settings");val toggle=views(d.window!!.decorView).filterIsInstance<Switch>().first()
        toggle.isChecked=!toggle.isChecked;idle();val checkpoint=SettingsDialog.checkpoint(d)
        views(d.window!!.decorView).single{it.contentDescription=="补全按键说明"}.performClick();val explanation=latest();bounded(explanation)
        val text=content(explanation,main.getString(R.string.completion_key_help));assertTrue(text.isTextSelectable);assertTrue(text.parent is ScrollView)
        assertTrue(words(explanation.window!!.decorView).contains("补全与快捷键"));capture("tablet-key-help-reader",explanation.window!!.decorView)
        explanation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertTrue(d.isShowing);assertEquals(checkpoint,SettingsDialog.checkpoint(d));assertSame(buffer,editor.text)
        views(d.window!!.decorView).single{it.contentDescription=="补全按键说明"}.performClick();val reopened=latest()
        reopened.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK))
        reopened.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK));idle()
        assertFalse(reopened.isShowing);assertTrue(d.isShowing);assertEquals(checkpoint,SettingsDialog.checkpoint(d));assertEquals(original,field<AppSettings>("settings"))
    }
    @Test @Config(qualifiers="w800dp-h1200dp") fun tabletLightLicenseRetainsItsCompleteSelectableTextAndScrollsToTheEnd(){
        val buffer=editor.text;val original=field<AppSettings>("settings");val d=settings();category(d,"帮助与许可")
        views(d.window!!.decorView).single{it.contentDescription=="GNU LGPL 2.1"}.performClick();val explanation=latest();bounded(explanation)
        val license=main.assets.open("LGPL-2.1.txt").bufferedReader().use{it.readText()};val text=content(explanation,license);assertTrue(text.isTextSelectable)
        val scroll=text.parent as ScrollView;scroll.isSmoothScrollingEnabled=false;scroll.fullScroll(View.FOCUS_DOWN);idle();assertTrue(scroll.scrollY>0);capture("tablet-light-license-end",explanation.window!!.decorView)
        explanation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertTrue(d.isShowing);assertEquals(original,field<AppSettings>("settings"));assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w320dp-h800dp") fun phoneLightLargeHelpKeepsTheHeadingBodyAndCloseReachable(){
        val text=main.getString(R.string.shortcuts_help);val buffer=editor.text;val d=reader("快捷键说明",text)
        assertTrue(words(d.window!!.decorView).contains("CodePocket"));val heading=content(d,"快捷键说明");assertTrue(heading.height>=heading.lineHeight);bounded(d)
        val body=content(d,text);assertTrue(body.isTextSelectable);val scroll=body.parent as ScrollView;scroll.isSmoothScrollingEnabled=false;scroll.fullScroll(View.FOCUS_DOWN);idle()
        val close=d.getButton(AlertDialog.BUTTON_POSITIVE);assertTrue(close.width>=48);assertTrue(close.height>=48);val visible=android.graphics.Rect();assertTrue(close.getGlobalVisibleRect(visible));assertEquals(close.height,visible.height())
        capture("phone-light-large-shortcuts",d.window!!.decorView);close.performClick();idle();assertFalse(d.isShowing);assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightDiagnosticCopiesTheFullOriginalMessageAndKeepsCodeSelection(){
        val buffer=editor.text;editor.setSelection(2);val message="完整诊断排版夹具，未运行程序。\n".repeat(60);val issue=InlineIssue(0,1,message,"实际诊断详情入口")
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showDiagnosticDetails",ClassParameter.from(InlineIssue::class.java,issue));val d=latest();bounded(d)
        assertTrue(words(d.window!!.decorView).contains(issue.source));val body=content(d,main.getString(R.string.editor_diagnostic_content,issue.source,message));assertTrue(body.isTextSelectable);assertEquals(Typeface.MONOSPACE,body.typeface)
        capture("tablet-light-diagnostic-reader",d.window!!.decorView);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle()
        assertEquals(message,main.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString());assertSame(buffer,editor.text);assertEquals(2,editor.selectionStart)
    }
    @Test @Config(qualifiers="w840dp-h411dp") fun compactTerminalFailureKeepsFullRecordCopyAndNeverStartsAReplacementSession(){
        val runtime=ShellRuntime.create(main)
        try{runtime.fail(IOException("host failure fixture\n"+"full line\n".repeat(80)));val report=runtime.failureDetails();val d=ShellTerminalPane.showFailureDetails(main,runtime,false);bounded(d)
            val body=views(d.window!!.decorView).filterIsInstance<TextView>().single{it.contentDescription==main.getString(R.string.shell_failure_content)}
            assertEquals(report,body.text.toString());assertTrue(body.isTextSelectable);assertTrue(words(d.window!!.decorView).contains("终端 · 启动记录"));capture("compact-terminal-failure-reader",d.window!!.decorView)
            d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertEquals(report,main.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString());assertEquals(ShellRuntime.State.FAILED,runtime.state());assertNull(runtime.session())
        }finally{runtime.shutdown()}
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun resizedReaderReusesItsTextScrollAndDropsItsLayoutObserverAfterDismiss(){
        val text=main.assets.open("LGPL-2.1.txt").bufferedReader().use{it.readText()};val d=reader("许可排版测试",text);bounded(d);val body=content(d,text);val scroll=body.parent as ScrollView
        val host=main.findViewById<View>(android.R.id.content);val sizes=mutableListOf<String>()
        host.addOnLayoutChangeListener{_,l,t,r,b,_,_,_,_->sizes.add("${r-l}x${b-t}")}
        scroll.isSmoothScrollingEnabled=false;scroll.scrollTo(0,120);idle();assertTrue(scroll.scrollY>0);layout(560,700);idle()
        assertEquals("host=${host.width}x${host.height}, changes=$sizes",528,d.window!!.attributes.width)
        assertSame(body,content(d,text));assertTrue("Resize retains the existing scroll position: ${scroll.scrollY}",scroll.scrollY>0)
        assertEquals(120,scroll.scrollY);capture("resized-existing-reader",d.window!!.decorView)
        assertEquals(560,host.width);assertEquals(700,host.height)
        android.text.Selection.setSelection(body.text as android.text.Spannable,4,10)
        layout(560,320);idle();assertEquals(528,d.window!!.attributes.width);assertSame(body,content(d,text))
        assertEquals(4,body.selectionStart);assertEquals(10,body.selectionEnd);assertTrue(scroll.height>=body.lineHeight)
        assertTrue("Reader follows the constrained host height",d.window!!.decorView.height<=320)
        val button=d.getButton(AlertDialog.BUTTON_POSITIVE);val visible=android.graphics.Rect()
        assertTrue(button.getGlobalVisibleRect(visible));assertEquals(button.height,visible.height());capture("resized-short-reader",d.window!!.decorView)
        val lastWidth=d.window!!.attributes.width;d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertFalse(d.isShowing)
        layout(1000,800);assertEquals(lastWidth,d.window!!.attributes.width)
    }
}
