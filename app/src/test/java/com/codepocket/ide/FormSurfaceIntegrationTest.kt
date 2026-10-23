package com.codepocket.ide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.*
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
import java.util.concurrent.TimeUnit

/** Native Main/Sora view hierarchy and drawings. No Android Python/PTY execution is claimed. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class FormSurfaceIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun invoke(method:String)=ReflectionHelpers.callInstanceMethod<Unit>(main,method)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun layout(){repeat(3){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun point(v:View)=IntArray(2).also{v.getLocationOnScreen(it)}
    @Before fun launch(){
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.cpp").putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,
                animations=false,autoSave=false,panelPreferSide=true).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus()
        repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Main Sora layout unavailable")
    }
    @After fun close(){main.finish();controller.pause().stop().destroy();drain();RuntimeEnvironment.setFontScale(1f)}
    private fun unusedHierarchy(sidebar:Boolean){
        val rail=field<View>("activityBar");val top=field<ViewGroup>("topWorkspace");val split=field<LinearLayout>("splitHost")
        val panel=field<View>("outputRegion");val footer=field<View>("statusBarComponent");val region=field<View>("editorRegion")
        assertEquals(View.VISIBLE,rail.visibility);assertSame(top,region.parent);assertSame(split,top.parent);assertSame(split,panel.parent)
        assertEquals(LinearLayout.VERTICAL,split.orientation);assertEquals(PanelDock.BOTTOM,field<PanelLayout>("panelLayout").dock)
        assertEquals(point(rail)[0]+rail.width,point(split)[0]);assertEquals(point(rail)[1],point(split)[1])
        assertEquals(point(rail)[1]+rail.height,point(footer)[1]+footer.height)
        assertEquals(point(split)[0],point(footer)[0]);assertTrue(editor.width>=IdeControls.dp(main,280))
        if(panel.visibility==View.VISIBLE){assertEquals(top.width,panel.width);assertEquals(point(top)[0],point(panel)[0]);assertTrue(point(top)[1]+top.height<=point(panel)[1])}
        val pane=field<View?>("explorerPane")
        assertEquals(sidebar,pane?.visibility==View.VISIBLE)
        if(sidebar){assertSame(top,pane!!.parent);assertEquals(top.height,pane.height);if(panel.visibility==View.VISIBLE)assertTrue(point(pane)[1]+pane.height<=point(panel)[1])}
        assertEquals(View.GONE,field<View>("auxiliarySlot").visibility)
    }
    private fun capture(tag:String,target:View?=null){
        layout();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));val v=target?:main.findViewById<View>(android.R.id.content)
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd
        val folder=File(repo,"verification/workbench-forms-2026-10-24/previews").apply{mkdirs()}
        File(folder,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    private fun latest()=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
    private fun fileDialog(method:String,file:java.io.File){ReflectionHelpers.callInstanceMethod<Unit>(main,method,ClassParameter.from(java.io.File::class.java,file));idle()}
    @Test @Config(qualifiers="w411dp-h891dp") fun createAndRenameHaveSeparatePathAndInput(){
        ReflectionHelpers.callInstanceMethod<Unit>(main,"askCreate",ClassParameter.from(Boolean::class.javaPrimitiveType,false));idle();var d=latest()
        assertTrue(views(d.window!!.decorView).any{it.contentDescription=="位置：项目根目录"})
        val input=views(d.window!!.decorView).filterIsInstance<android.widget.EditText>().single();input.setText("../outside.py")
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(d.isShowing);assertNotNull(input.error)
        capture("phone-create-invalid",d.window!!.decorView);d.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        fileDialog("askRename",field<Workspace>("workspace").resolve("main.cpp"));d=latest();capture("phone-rename",d.window!!.decorView)
        assertEquals("main.cpp",views(d.window!!.decorView).filterIsInstance<android.widget.EditText>().single().text.toString());d.dismiss()
    }
    @Test @Config(qualifiers="w840dp-h411dp") fun shortWindowMovePickerRetainsCancelAndDestination(){
        val ws=field<Workspace>("workspace");ws.create(ws.root,"tasks",true)
        fileDialog("askMove",ws.resolve("main.cpp"));val d=latest()
        assertTrue(views(d.window!!.decorView).any{it.contentDescription=="来源：main.cpp"})
        capture("phone-landscape-move",d.window!!.decorView);assertTrue(d.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).height>=48);d.dismiss()
        assertTrue(ws.resolve("main.cpp").exists())
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun deleteShowsRealPathAndCancelDoesNotChangeFile(){
        val ws=field<Workspace>("workspace");val before=ws.resolve("main.cpp").readText()
        fileDialog("askDelete",ws.resolve("main.cpp"));val d=latest()
        assertTrue(views(d.window!!.decorView).any{it.contentDescription=="删除项目：main.cpp"})
        capture("phone-delete",d.window!!.decorView);d.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(before,ws.resolve("main.cpp").readText())
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun projectSearchRendersActualUnsavedResultsInTwoLevels(){
        editor.setText("ui_unique_match\n");idle();invoke("showProjectSearch");val d=field<android.app.AlertDialog>("toolDialog")
        capture("tablet-project-search-idle",d.window!!.decorView)
        views(d.window!!.decorView).filterIsInstance<android.widget.EditText>().single().setText("ui_unique_match")
        views(d.window!!.decorView).filterIsInstance<android.widget.Button>().single{it.text.toString()=="搜索"}.performClick();drain();idle()
        val list=views(d.window!!.decorView).filterIsInstance<android.widget.ListView>().single();assertEquals(1,list.adapter.count)
        val row=list.adapter.getView(0,null,list);val labels=views(row).filterIsInstance<android.widget.TextView>().map{it.text.toString()}
        assertTrue(labels.contains("main.cpp:1"));assertTrue(labels.contains("ui_unique_match"));capture("tablet-project-search",d.window!!.decorView);d.dismiss()
        assertTrue(field<Boolean>("dirty"))
    }
}
