package com.codepocket.ide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.DesignTokens
import com.codepocket.ide.ui.IdeControls
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual Main/Sora, measured text, real disk-conflict save and retained recovery draft. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class WorkbenchPathStatusIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private val breadcrumb get()=field<TextView?>("pathBreadcrumb")!!
    private val footer get()=field<LinearLayout>("statusBarComponent")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun layout(){repeat(3){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Actual Sora layout unavailable")}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,animations=false,autoSave=false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()
    }
    @After fun close(){if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    private fun capture(tag:String,v:View){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(b)
        val background=generateSequence(v){it.parent as? View}.mapNotNull{(it.background as? android.graphics.drawable.ColorDrawable)?.color}.firstOrNull{android.graphics.Color.alpha(it)==255}
        canvas.drawColor(background?:DesignTokens.forContext(main).editor);v.draw(canvas)
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd;val dir=File(repo,"verification/workbench-path-status-2026-10-24/previews").apply{mkdirs()}
        File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private fun textHeightFits(v:TextView){assertNotNull(v.layout);assertTrue("${v.text}: text height=${v.layout.height}, available=${v.height-v.compoundPaddingTop-v.compoundPaddingBottom}",v.layout.height<=v.height-v.compoundPaddingTop-v.compoundPaddingBottom)}
    private fun longPath():String {
        val folder=workspace.create(workspace.root,"学习项目",true);val nested=workspace.create(folder,"练习",true)
        val f=workspace.create(nested,"long_solution_name_".repeat(6)+".py",false);workspace.write(f,"print('path')\n")
        val path=workspace.path(f);ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready();layout();return path
    }
    private fun checkPath(tag:String){
        val path=longPath();capture(tag+"-path",breadcrumb);capture(tag+"-footer",footer)
        textHeightFits(breadcrumb);assertTrue(editor.height>0);assertTrue(editor.hasFocus())
        assertTrue(breadcrumb.contentDescription.toString().contains(path));assertEquals(breadcrumb.contentDescription,breadcrumb.tooltipText)
        val state=footer.getChildAt(0) as TextView;val details=footer.getChildAt(1) as TextView;textHeightFits(state);textHeightFits(details)
        assertTrue(footer.contentDescription.toString().contains("Python"));assertTrue(footer.contentDescription.toString().contains("Ln"))
    }
    @Test @Config(qualifiers="w320dp-h891dp") fun phoneLargePathAndFooterDoNotClipText(){checkPath("phone-large")}
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightLargePathAndFooterDoNotClipText(){checkPath("tablet-light-large")}
    @Test @Config(qualifiers="w411dp-h891dp") fun normalPathKeepsCompactHeightAndFullExplanation(){
        checkPath("phone-normal");assertEquals(IdeControls.dp(main,24),breadcrumb.height)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun errorWordInFilenameNeverMakesAnOlderSaveDescribeTheNewBuffer(){
        val f=workspace.create(workspace.root,"错误.py",false);workspace.write(f,"value = 1\n")
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,"错误.py"),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready();layout()
        editor.setText("value = 2\n");ready()
        main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));drain();ready();layout()
        assertFalse(field("dirty"));assertEquals("已保存",(footer.getChildAt(0) as TextView).text.toString())
        editor.setSelection(editor.length());assertTrue(editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("# new edit\n",1));idle();ready();layout()
        assertTrue(field("dirty"));assertEquals("未保存",(footer.getChildAt(0) as TextView).text.toString());assertEquals("value = 2\n",f.readText())
        assertEquals("value = 2\n# new edit\n",editor.text.toString());capture("filename-error-word-new-buffer",footer)
    }
    @Test @Config(qualifiers="w320dp-h891dp") fun phoneLargeRealSaveConflictShowsFailureAndRetainsBufferAndDraft(){
        val original=workspace.resolve("main.py").readText()
        val text="print('keep this buffer')\n";editor.setText(text);ready();layout();val content=editor.text
        val external="# actual external edit\n";workspace.resolve("main.py").writeText(external)
        main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));drain();ready();layout()
        val raw=field<TextView>("status").text.toString();assertTrue(raw.startsWith("保存失败"));assertTrue(field("dirty"));assertFalse(field("saving"))
        assertSame(content,editor.text);assertEquals(text,editor.text.toString());assertEquals(external,workspace.resolve("main.py").readText())
        val key=field<EditorSessions>("sessions").key("main.py");assertEquals(text,field<DraftStore>("drafts").read(key)!!.text)
        val message=footer.getChildAt(0) as TextView;val details=footer.getChildAt(1) as TextView;capture("phone-large-real-save-conflict",footer)
        assertEquals("保存失败",message.text.toString());assertEquals(DesignTokens.palette(true).error,message.currentTextColor)
        assertTrue(message.contentDescription.toString().contains("未保存"));assertTrue(message.contentDescription.toString().contains("保存失败"))
        textHeightFits(message);textHeightFits(details);assertEquals(0,message.layout.getEllipsisCount(0));assertEquals(0,details.layout.getEllipsisCount(0))
        assertTrue(details.text.toString().startsWith("Ln "))
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()?.takeIf{it.isShowing}?.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.performClick()
        workspace.resolve("main.py").writeText(original)
        main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));drain();ready();layout()
        assertFalse(field("dirty"));assertSame(content,editor.text);assertEquals(text,workspace.resolve("main.py").readText())
        assertNull(field<DraftStore>("drafts").read(key));assertEquals("已保存",message.text.toString());assertNotEquals(DesignTokens.palette(true).error,message.currentTextColor)
        capture("phone-large-real-save-recovered",footer)
    }
}
