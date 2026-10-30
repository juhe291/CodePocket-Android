package com.codepocket.ide

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import android.widget.EditText
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import com.codepocket.editorlab.WorkbenchEditor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

/** Existing workbench host, real drawer geometry and actual window animation resources. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class WorkbenchSidebarBehaviorTest {
    @get:Rule val name=org.junit.rules.TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun resize(width:Int,height:Int){
        val density=main.resources.displayMetrics.density
        RuntimeEnvironment.setQualifiers("w${(width/density).toInt()}dp-h${(height/density).toInt()}dp")
        org.robolectric.shadows.ShadowDisplayManager.changeDisplay(android.view.Display.DEFAULT_DISPLAY,
            "w${(width/density).toInt()}dp-h${(height/density).toInt()}dp")
        main.window.setLayout(width,height)
        val v=main.findViewById<View>(android.R.id.content)
        repeat(3){v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}
        assertEquals("The window manager must keep the requested viewport",(width/density).toInt(),field<com.codepocket.ide.ui.WorkspaceGeometry>("currentGeometry").contentWidth)
    }
    private fun search(){ReflectionHelpers.callInstanceMethod<Any?>(main,"showProjectSearch");idle()}
    @Before fun launch(){
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("tablet"))UiTheme.LIGHT else UiTheme.DARK,animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val v=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(2){v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}
    }
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){main.finish();controller.pause().stop().destroy()};drain()}
    private fun capture(tag:String,dialog:AlertDialog?=null){
        val root=main.findViewById<View>(android.R.id.content);val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);root.draw(canvas)
        // Compose only the real measured native windows at their actual window-manager offsets.
        dialog?.window?.let{window->val decor=window.decorView;val where=IntArray(2);decor.getLocationOnScreen(where);val origin=IntArray(2);root.getLocationOnScreen(origin);canvas.save();canvas.translate((where[0]-origin[0]).toFloat(),(where[1]-origin[1]).toFloat());decor.draw(canvas);canvas.restore()}
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd;val folder=File(repo,"verification/workbench-behavior-2026-10-28/previews").apply{mkdirs()}
        File(folder,"$tag.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    @Test fun phoneSearchOpensInTheSameDrawerGeometryAsExplorer(){
        val before=field<WorkbenchEditor>("editor").text.toString();ReflectionHelpers.callInstanceMethod<Any?>(main,"showExplorer");idle()
        val explorer=field<AlertDialog>("explorerDialog");assertTrue(explorer.isShowing);val expected=android.view.WindowManager.LayoutParams().apply{copyFrom(explorer.window!!.attributes)};capture("phone-explorer",explorer);explorer.dismiss()
        ReflectionHelpers.callInstanceMethod<Any?>(main,"showProjectSearch");idle();val search=field<AlertDialog>("toolDialog");assertTrue(search.isShowing)
        val actual=search.window!!.attributes;assertEquals(expected.x,actual.x);assertEquals(expected.y,actual.y);assertEquals(expected.gravity,actual.gravity);assertEquals(expected.width,actual.width);assertEquals(expected.height,actual.height)
        assertTrue(actual.x>=48);assertTrue(actual.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL!=0);assertEquals(0,actual.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);capture("phone-search",search)
        assertEquals(before,field<WorkbenchEditor>("editor").text.toString())
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletSearchLivesInTheSidebarAndLeavesTheEditorBufferUntouched(){
        val before=field<WorkbenchEditor>("editor").text.toString();ReflectionHelpers.callInstanceMethod<Any?>(main,"showProjectSearch");idle()
        val query=views(main.findViewById(android.R.id.content)).filterIsInstance<EditText>().firstOrNull{it.contentDescription=="项目查找文本"&&it.isShown}
        assertNotNull("Tablet search must use the same real sidebar host as Explorer",query)
        assertEquals(before,field<WorkbenchEditor>("editor").text.toString());assertTrue(field<AlertDialog?>("toolDialog")?.isShowing!=true)
    }
    @Test fun drawerWindowAnimationNeverSlidesItsWholeSurfaceOverTheFixedActivityBar(){
        val attributes=main.obtainStyledAttributes(R.style.WorkbenchDrawerWindowMotion,intArrayOf(android.R.attr.windowEnterAnimation,android.R.attr.windowExitAnimation))
        try { for(i in 0..1){val resource=attributes.getResourceId(i,0);assertTrue(resource!=0);val animation=AnimationUtils.loadAnimation(main,resource)
            animation.initialize(300,600,300,600);animation.startTime=0
            for(fraction in listOf(0.25,0.5,0.75)){val transform=Transformation();animation.getTransformation((animation.duration*fraction).toLong(),transform);val values=FloatArray(9);transform.matrix.getValues(values)
                assertTrue("Move content inside a clipped host, not the whole window into the activity bar: ${values[2]}",values[2]>=-.01f)
            }
        }}finally{attributes.recycle()}
    }
    @Test fun queryAndEditorIdentitySurviveRehostingBetweenPhoneDrawerAndTabletSidebar(){
        val editor=field<WorkbenchEditor>("editor");val text=editor.text;search()
        val pane=field<View>("sidebarSearchView");val query=views(pane).filterIsInstance<EditText>().single();query.setText("草稿 😀 token")
        resize(1280,800);assertSame(pane,field("sidebarSearchView"));assertTrue("Rehosted pane should be shown: ${field<Any?>("currentGeometry")} / ${pane.parent}",pane.isShown);assertTrue("The old phone dialog must close: ${field<Any?>("currentGeometry")}",field<AlertDialog?>("toolDialog")?.isShowing!=true)
        assertEquals("草稿 😀 token",query.text.toString());resize(411,800)
        val dialog=field<AlertDialog>("toolDialog");assertTrue(dialog.isShowing);assertSame(pane,field("sidebarSearchView"));assertEquals("草稿 😀 token",query.text.toString());assertSame(text,editor.text)
    }
    @Test fun recreatedSearchDrawerRestoresItsQueryWithAPositiveWindowHeight(){
        search();views(field<View>("sidebarSearchView")).filterIsInstance<EditText>().single().setText("restore token")
        controller.recreate();main=controller.get();drain();resize(411,800)
        val dialog=field<AlertDialog>("toolDialog");assertTrue(dialog.isShowing);assertTrue("A restored drawer cannot remain height zero",dialog.window!!.attributes.height>0)
        assertEquals("restore token",views(field<View>("sidebarSearchView")).filterIsInstance<EditText>().single().text.toString())
    }
    @Test fun closingPhoneSearchDoesNotReopenASidebarOnWindowExpansion(){
        search();views(field<View>("sidebarSearchView")).single{it.contentDescription=="关闭项目搜索"}.performClick();idle();resize(1280,800)
        assertEquals(View.GONE,field<View>("explorerPane").visibility);assertFalse(field("sidebarSearchActive"))
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun backClosesTheSearchSidebarWithoutOpeningExplorerOrReplacingTheEditor(){
        val editor=field<WorkbenchEditor>("editor");val buffer=editor.text;search();assertTrue(field<Boolean>("sidebarSearchActive"))
        main.onBackPressed();idle();assertFalse(main.isFinishing);assertFalse(field("sidebarSearchActive"));assertFalse(field("explorerVisible"))
        assertEquals(View.GONE,field<View>("explorerPane").visibility);assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun outgoingTabletExplorerSnapshotStaysToTheRightOfTheActivityRail(){
        assertTrue(field<Boolean>("explorerVisible"));val editor=field<WorkbenchEditor>("editor");val buffer=editor.text
        ReflectionHelpers.setField(main,"settings",field<AppSettings>("settings").copy(animations=true))
        assertTrue(com.codepocket.ide.animation.MotionEnvironment.enabled(main,true))
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showExplorer")
        val root=field<ViewGroup>("contentHost");val flight=root.getTag(R.id.workbench_surface_motion)
        assertNotNull("Inspect the real outgoing native snapshot",flight)
        val bounds=ReflectionHelpers.getField<android.graphics.Rect>(flight,"bounds");val dx=ReflectionHelpers.getField<Int>(flight,"dx")
        val rail=field<View>("activityBar");val right=rail.left+rail.width
        for(fraction in listOf(.25f,.5f,.75f,1f))assertTrue("Outgoing snapshot crosses the fixed rail at $fraction: left=${bounds.left}, dx=$dx, rail=$right",bounds.left+dx*fraction>=right)
        assertSame(buffer,editor.text);assertEquals(View.GONE,field<View>("explorerPane").visibility)
        com.codepocket.ide.animation.SurfaceMotion.finish(root)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletSidebarUsesTheRealUnsavedWorkspaceSearchWorker(){
        val editor=field<WorkbenchEditor>("editor");editor.setText("sidebar_real_unsaved_token\n");idle();val text=editor.text;search()
        val pane=field<View>("sidebarSearchView");views(pane).filterIsInstance<EditText>().single().setText("sidebar_real_unsaved_token")
        views(pane).filterIsInstance<android.widget.Button>().single{it.text.toString()=="搜索"}.performClick();drain()
        val list=views(pane).filterIsInstance<android.widget.ListView>().single();assertEquals(1,list.adapter.count)
        val row=list.adapter.getView(0,null,list);val labels=views(row).filterIsInstance<android.widget.TextView>().map{it.text.toString()}
        assertTrue(labels.contains("main.py:1"));assertTrue(labels.contains("sidebar_real_unsaved_token"));assertSame(text,editor.text);assertTrue(field("dirty"))
        capture("tablet-light-search-results")
    }
}
