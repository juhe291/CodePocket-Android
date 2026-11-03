package com.codepocket.ide

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.codepocket.editorlab.WorkbenchEditor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

/** Real MainActivity gutter: tap toggles a persisted 1-based breakpoint and never edits the document. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class BreakpointIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun layout(){repeat(3){val v=main.findViewById<View>(android.R.id.content);val m=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.EXACTLY))
        v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun gutter():View=views(editor).single{it.contentDescription=="断点栏"}
    private fun tap(lineY:Float){
        val now=SystemClock.uptimeMillis()
        gutter().dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,1f,lineY,0))
        gutter().dispatchTouchEvent(MotionEvent.obtain(now,now+8,MotionEvent.ACTION_UP,1f,lineY,0));idle()
    }
    @Before fun launch(){
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py").putString("settings",AppSettings(theme=UiTheme.DARK,animations=false,autoSave=false).toJson()).commit()
        Workspace(app)
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain()
        controller.windowFocusChanged(true);layout();awaitHostEditor(main);layout()
    }
    @After fun close(){main.finish();controller.pause().stop().destroy();drain()}
    @Test fun gutterTapTogglesPersistedBreakpointWithoutDirtyingTheDocument(){
        val sessions=field<EditorSessions>("sessions")
        assertFalse(field<Boolean>("dirty"))
        assertEquals(emptyList<Int>(),sessions.file("main.py").breakpoints)
        tap(5f)
        assertEquals(listOf(1),sessions.file("main.py").breakpoints)
        assertArrayEquals(intArrayOf(1),editor.getBreakpoints())
        assertEquals(listOf(1),field<EditorSessions>("sessions").file("main.py").breakpoints)
        assertFalse("Breakpoints are not document edits",field<Boolean>("dirty"))
        assertFalse(editor.history(false))
        tap(5f)
        assertEquals(emptyList<Int>(),sessions.file("main.py").breakpoints)
        assertEquals(0,editor.getBreakpoints().size)
    }
    @Test fun breakpointsAreRestoredPerFileAcrossTabSwitches(){
        ReflectionHelpers.callInstanceMethod<Unit>(main,"toggleBreakpoint",ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,3))
        idle()
        assertEquals(listOf(3),field<EditorSessions>("sessions").file("main.py").breakpoints)
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",
            ReflectionHelpers.ClassParameter.from(String::class.java,"main.cpp"),
            ReflectionHelpers.ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null))
        drain();layout();awaitHostEditor(main)
        assertEquals("main.cpp",field<String>("activePath"))
        assertEquals(0,editor.getBreakpoints().size)
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",
            ReflectionHelpers.ClassParameter.from(String::class.java,"main.py"),
            ReflectionHelpers.ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null))
        drain();layout();awaitHostEditor(main)
        assertEquals("main.py",field<String>("activePath"))
        assertArrayEquals(intArrayOf(3),editor.getBreakpoints())
        assertEquals(listOf(3),field<EditorSessions>("sessions").file("main.py").breakpoints)
    }
}
