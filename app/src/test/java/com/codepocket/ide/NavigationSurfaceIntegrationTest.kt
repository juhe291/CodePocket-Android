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
class NavigationSurfaceIntegrationTest {
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
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
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
        val folder=File(repo,"verification/workbench-navigation-2026-10-24/previews").apply{mkdirs()}
        File(folder,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneFileDrawerKeepsReadableTreeAndFolderAction(){
        val workspace=field<Workspace>("workspace");val src=workspace.create(workspace.root,"src",true)
        workspace.create(src,"solve.py",false);workspace.create(src,"solver.hpp",false)
        field<MutableSet<String>>("expanded").add("src");invoke("showExplorer");idle()
        val dialog=field<android.app.AlertDialog>("explorerDialog");val tree=field<LinearLayout>("overlayExplorerList")
        assertTrue(views(tree).any{it.getTag(com.codepocket.ide.R.id.workbench_key)=="entry:src/solve.py"})
        capture("phone-file-drawer",dialog.window!!.decorView)
        views(dialog.window!!.decorView).single{it.contentDescription=="文件与工作区命令"}.performClick();idle()
        val menu=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow!!
        val create=views(menu.contentView).single{it.contentDescription=="新建文件夹"}
        create.performClick();idle()
        val form=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(form.isShowing);assertEquals(1,views(form.window!!.decorView).filterIsInstance<android.widget.EditText>().size)
        capture("phone-new-folder-form",form.window!!.decorView)
        form.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        menu.dismiss();dialog.dismiss();assertTrue(editor.length()>0)
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun terminalMenuGroupsKeepSessionAndCopyAction(){
        invoke("openTerminal");layout()
        val bar=field<PanelToolbar>("panelToolbar");bar.menuAnchor.performClick();idle()
        fun popup()=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow!!
        var window=popup();val categories=views(window.contentView).filterIsInstance<android.widget.TextView>().map{it.text.toString()}
        assertTrue(categories.containsAll(listOf("终端操作","会话管理","布局与输入","配色与帮助")))
        val selected=field<com.codepocket.ide.terminal.shell.ShellSessionRegistry>("terminalRegistry").catalog.selected()
        capture("tablet-terminal-categories",window.contentView)
        views(window.contentView).single{it.contentDescription=="终端操作"}.performClick();idle();window=popup()
        assertTrue(views(window.contentView).any{it.contentDescription=="复制终端记录"})
        assertSame(selected,field<com.codepocket.ide.terminal.shell.ShellSessionRegistry>("terminalRegistry").catalog.selected());window.dismiss()
        capture("tablet-terminal-toolbar")
    }
}
