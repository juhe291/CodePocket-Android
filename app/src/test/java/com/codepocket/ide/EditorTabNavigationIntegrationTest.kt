package com.codepocket.ide

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.EditorTab
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

/** Real Main/Sora/files and native tab viewport. No fabricated runs or replacement navigation. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class EditorTabNavigationIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private val tabs get()=field<LinearLayout>("tabsRow")
    private val scroll get()=tabs.parent as HorizontalScrollView
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun layout(){repeat(3){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real Sora layout did not finish")}
    private fun open(path:String){ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready();layout()}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))1.7f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,animations=false,autoSave=false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()
    }
    @After fun close(){if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    private fun active()=(0 until tabs.childCount).map{tabs.getChildAt(it)}.filterIsInstance<EditorTab>().single{it.path==field<String>("activePath")}
    private fun assertVisible(v:View){val r=Rect();assertTrue("${v.contentDescription} must be visible, tab scroll=${scroll.scrollX}, width=${scroll.width}",v.getGlobalVisibleRect(r));assertEquals("complete width",v.width,r.width());assertEquals("complete height",v.height,r.height())}
    private fun files(longLast:Boolean=false):List<String> {
        val paths=(1..8).map{if(longLast&&it==8)"很长的练习文件名_".repeat(6)+".py" else "task_$it.py"}
        for((index,path) in paths.withIndex()){val f=workspace.create(workspace.root,path,false);workspace.write(f,"value = ${index+1}\n");open(path)}
        return paths
    }
    private fun capture(tag:String){val v=field<View>("editorRegion");val b=Bitmap.createBitmap(v.width,IdeControls.dp(main,72).coerceAtMost(v.height),Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd;val dir=File(repo,"verification/workbench-tab-navigation-2026-10-24/previews").apply{mkdirs()}
        File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    @Test @Config(qualifiers="w320dp-h891dp") fun openingAndClosingLastTabKeepsCurrentFileVisible(){
        val paths=files();capture("phone-last-opened");assertVisible(active());assertVisible(active().getChildAt(1));assertTrue(scroll.scrollX>0)
        active().getChildAt(1).performClick();drain();ready();layout();assertEquals(paths[6],field<String>("activePath"));assertVisible(active());capture("phone-after-close")
        open(paths.first());assertVisible(active());assertEquals("value = 1\n",editor.text.toString());assertTrue(editor.hasFocus());capture("phone-back-to-first")
    }
    @Test @Config(qualifiers="w320dp-h891dp") fun oversizedFilenameKeepsItsCloseTargetReachable(){
        val paths=files(true);val selected=active();capture("phone-long-current")
        assertTrue("long tab must fit its viewport",selected.width<=scroll.width);assertVisible(selected);assertVisible(selected.getChildAt(1));assertTrue(selected.getChildAt(0).contentDescription.toString().contains(paths.last()))
        assertEquals("value = 8\n",editor.text.toString());assertTrue(editor.hasFocus())
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightLargeResizeRetainsCurrentDocumentAndRevealsClose(){
        val path=files(true).last();assertVisible(active());capture("tablet-light-large-current")
        val id=field<EditorSessions>("sessions").file(path).id;val text=editor.text.toString()
        org.robolectric.shadows.ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY,"w320dp-h891dp")
        val changed=Configuration(main.resources.configuration).apply{screenWidthDp=320;screenHeightDp=891;smallestScreenWidthDp=320}
        controller.configurationChange(changed);main=controller.get();controller.visible();drain();controller.windowFocusChanged(true);layout();ready()
        assertEquals(320,(main.findViewById<View>(android.R.id.content).width/main.resources.displayMetrics.density).toInt())
        assertEquals(path,field<String>("activePath"));assertEquals(text,editor.text.toString());assertEquals(id,field<EditorSessions>("sessions").file(path).id)
        assertVisible(active());assertVisible(active().getChildAt(1));capture("resized-light-large-current")
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun resizingExplorerRevealsCurrentTabWithoutRecreatingEditor(){
        files();assertVisible(active());val current=active();val content=editor.text;val oldWidth=scroll.width;capture("tablet-before-sidebar-resize")
        val handle=field<View?>("explorerResizeHandle")!!
        repeat(5){assertTrue(handle.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));idle();layout()}
        assertTrue("tab viewport must actually shrink",scroll.width<oldWidth);assertSame(current,active());assertSame(content,editor.text)
        assertVisible(active());assertTrue(editor.hasFocus());capture("tablet-after-sidebar-resize")
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun typingAndSavingDoNotStealManualTabScrollButExplicitOpenReveals(){
        val path=files().last();assertVisible(active());val tab=active();val buffer=editor.text;val id=field<EditorSessions>("sessions").file(path).id
        scroll.scrollTo(0,0);idle();val manual=scroll.scrollX;assertEquals(0,manual)
        editor.setSelection(editor.length());assertTrue(editor.onCreateInputConnection(EditorInfo()).commitText("# keep focus\n",1));idle();ready();layout()
        assertEquals(manual,scroll.scrollX);assertSame(tab,active());assertSame(buffer,editor.text);assertTrue(editor.hasFocus())
        main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));drain();ready();layout()
        assertEquals(manual,scroll.scrollX);assertFalse(field("dirty"));assertEquals(editor.text.toString(),workspace.read(workspace.resolve(path)));capture("phone-manual-scroll-after-save")
        open(path);assertVisible(active());assertSame(tab,active());assertSame(buffer,editor.text);assertEquals(id,field<EditorSessions>("sessions").file(path).id);assertTrue(editor.hasFocus());capture("phone-explicit-reopen")
    }
}
