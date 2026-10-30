package com.codepocket.ide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.editor.SoraEditorPort
import com.codepocket.ide.ui.EditorTab
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
import java.util.concurrent.TimeUnit

/** Actual Main/Sora input and disk save; dirty presentation must not move neighbouring actions. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class EditorTabStabilityIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private val tabs get()=field<LinearLayout>("tabsRow")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun layout(){repeat(2){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real Sora layout did not finish")}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))1.7f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,animations=false,autoSave=false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()
        // A fresh workspace restores only the active tab; open a real neighbour before testing its geometry/close action.
        for(path in listOf("main.cpp","main.py")){
            ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready();layout()
        }
    }
    @After fun close(){if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    private fun tab(path:String)=(0 until tabs.childCount).map{tabs.getChildAt(it)}.filterIsInstance<EditorTab>().single{it.path==path}
    private fun geometry()=(0 until tabs.childCount).map{index->val t=tabs.getChildAt(index) as LinearLayout;listOf(t.left,t.top,t.width,t.height,t.getChildAt(0).width,t.getChildAt(1).left,t.getChildAt(1).width)}
    private fun capture(tag:String){
        val v=field<View>("editorRegion");val b=Bitmap.createBitmap(v.width,com.codepocket.ide.ui.IdeControls.dp(main,72).coerceAtMost(v.height),Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd;val dir=File(repo,"verification/workbench-tab-stability-2026-10-24/previews").apply{mkdirs()}
        File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    private fun checkCycle(longName:Boolean){
        if(longName){
            val folder=workspace.create(workspace.root,"学习目录",true)
            val f=workspace.create(folder,"很长的解题文件名_".repeat(5)+".py",false);workspace.write(f,"value = 1\n")
            ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,workspace.path(f)),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();layout();ready()
        }
        val path=field<String>("activePath");val current=tab(path);val label=current.getChildAt(0) as TextView
        val buffer=editor.text;val before=geometry();val original=buffer.toString();assertFalse(field("dirty"));capture(name.methodName+"-clean")
        editor.setSelection(original.length);val connection=editor.onCreateInputConnection(EditorInfo());assertTrue(connection.commitText("# edit\n",1));idle();ready();layout()
        assertTrue(field("dirty"));capture(name.methodName+"-dirty")
        assertEquals("typing must keep tab names, close targets and neighbours at the same coordinates",before,geometry())
        assertSame(current,tab(path));assertSame(buffer,editor.text);assertTrue(editor.hasFocus())
        assertEquals(path.substringAfterLast('/'),label.text.toString());assertTrue(label.contentDescription.toString().contains(path));assertTrue(label.contentDescription.toString().contains("未保存"))
        assertTrue(current.getChildAt(1).width>=48);assertTrue(current.getChildAt(1).height>=48)
        main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));drain();ready();layout()
        assertFalse(field("dirty"));assertEquals(original+"# edit\n",workspace.read(workspace.resolve(path)))
        assertEquals(before,geometry());assertSame(current,tab(path));assertSame(buffer,editor.text);assertEquals(path,label.contentDescription.toString())
        assertEquals(workspace.read(workspace.resolve(path)),field<SoraEditorPort>("editorPort").snapshot()!!.buffer.text);capture(name.methodName+"-saved")
        val neighbour=(0 until tabs.childCount).map{tabs.getChildAt(it)}.filterIsInstance<EditorTab>().first{it.path!=path}
        neighbour.getChildAt(1).performClick();drain();ready();layout();assertEquals(path,field<String>("activePath"));assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun explicitIndentKeepsTabAndCloseCoordinates(){
        val path=field<String>("activePath");val current=tab(path);val before=geometry();val oldText=editor.text.toString()
        editor.setSelection(editor.length())
        ReflectionHelpers.callInstanceMethod<Unit>(main,"indent",ClassParameter.from(Boolean::class.javaPrimitiveType!!,false));idle();ready();layout()
        assertTrue(field("dirty"));assertNotEquals(oldText,editor.text.toString())
        assertEquals("explicit edit must not change tab widths or close positions",before,geometry());assertSame(current,tab(path))
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneTypingAndSavingKeepTabCoordinates(){checkCycle(false)}
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightLargeLongNameKeepsCloseTargetAndFullPath(){checkCycle(true)}
}
