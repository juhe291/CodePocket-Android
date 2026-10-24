package com.codepocket.ide

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.Display
import android.graphics.Rect
import android.widget.HorizontalScrollView
import android.view.ViewGroup
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual Main/Sora/terminal chrome; host Shell unavailability is not program output or device validation. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class TerminalDockIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun layout(width:Int=main.resources.displayMetrics.widthPixels){repeat(3){val v=main.findViewById<View>(android.R.id.content)
        v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.heightPixels,View.MeasureSpec.EXACTLY))
        v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py").putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,animations=false,autoSave=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout()
        ReflectionHelpers.callInstanceMethod<Unit>(main,"openTerminal");layout()
    }
    @After fun close(){main.finish();controller.pause().stop().destroy();drain();RuntimeEnvironment.setFontScale(1f)}
    private fun action(text:String)=views(field<View>("shellPane")).single{it.contentDescription==text}
    private fun point(v:View)=IntArray(2).also{v.getLocationOnScreen(it)}
    private fun assertActionsFit(){
        val keyboard=action("显示终端虚拟键盘");val dock=keyboard.parent as View;val edge=point(dock)
        val unit=main.resources.displayMetrics.density
        for(title in listOf("显示终端虚拟键盘","展开或收起终端控制键","复制终端记录","滚动到终端底部")){
            val button=action(title);val p=point(button)
            assertTrue("$title touch width",button.width>=48*unit);assertTrue("$title touch height",button.height>=48*unit)
            assertTrue("$title left",p[0]>=edge[0]);assertTrue("$title right outside dock ${dock.width}: ${p[0]-edge[0]} + ${button.width}",p[0]+button.width<=edge[0]+dock.width)
            if(button is TextView&&button.text.isNotEmpty()){
                val l=button.layout!!;assertEquals(button.text.length,l.getLineEnd(l.lineCount-1))
                assertTrue("$title text height",l.getLineBottom(l.lineCount-1)+button.compoundPaddingTop+button.compoundPaddingBottom<=button.height)
            }
        }
    }
    private fun capture(tag:String){
        val row=action("显示终端虚拟键盘").parent as View
        val v=if(row.parent is HorizontalScrollView)row.parent as View else row
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd
        val folder=File(repo,"verification/workbench-terminal-dock-2026-10-24/previews").apply{mkdirs()}
        File(folder,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    @Test @Config(qualifiers="w280dp-h900dp") fun narrowPhoneKeepsEveryTerminalActionInsidePanel(){capture("phone-narrow-terminal-actions");assertActionsFit()}
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLargeLightKeepsEveryTerminalActionReadable(){capture("tablet-large-light-terminal-actions");assertActionsFit()}
    @Test @Config(qualifiers="w411dp-h900dp") fun resizingAndControlToggleKeepSessionBufferAndPreference(){
        val registry=field<ShellSessionRegistry>("terminalRegistry");val selected=registry.catalog.selected();val editor=field<WorkbenchEditor>("editor")
        editor.setText("# keep window state\nprint(7)");idle()
        val content=editor.text.toString();val id=field<EditorSessions>("sessions").file("main.py").id
        action("展开或收起终端控制键").performClick();idle();layout()
        assertTrue(main.getSharedPreferences("codepocket_v2",0).getBoolean("terminalControlsExpanded",false))
        capture("phone-normal-terminal-actions")
        org.robolectric.shadows.ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY,"w280dp-h900dp")
        val changed=Configuration(main.resources.configuration).apply{screenWidthDp=280;screenHeightDp=900;smallestScreenWidthDp=280}
        controller.configurationChange(changed);main=controller.get();controller.visible();drain();controller.windowFocusChanged(true);layout()
        assertEquals(280,(main.findViewById<View>(android.R.id.content).width/main.resources.displayMetrics.density).toInt())
        capture("phone-resized-terminal-actions");assertActionsFit()
        assertSame(selected,field<ShellSessionRegistry>("terminalRegistry").catalog.selected());assertEquals(content,field<WorkbenchEditor>("editor").text.toString())
        assertEquals(id,field<EditorSessions>("sessions").file("main.py").id)
        val toggle=action("展开或收起终端控制键");assertTrue(toggle.isSelected)
        toggle.performClick();idle();layout();assertFalse(main.getSharedPreferences("codepocket_v2",0).getBoolean("terminalControlsExpanded",true))

    }    @Test @Config(qualifiers="w220dp-h900dp") fun veryNarrowDockScrollsToExistingActionsWithoutReplacingSession(){
        val registry=field<ShellSessionRegistry>("terminalRegistry");val selected=registry.catalog.selected()
        val keyboard=action("显示终端虚拟键盘");val dock=keyboard.parent.parent as HorizontalScrollView
        assertTrue("Four touch targets must remain reachable by scrolling",dock.canScrollHorizontally(1))
        dock.scrollTo(dock.getChildAt(0).width,0);idle()
        val bottom=action("滚动到终端底部");val visible=Rect();assertTrue(bottom.getGlobalVisibleRect(visible))
        assertEquals(bottom.width,visible.width());assertEquals(bottom.height,visible.height())
        assertSame(selected,registry.catalog.selected());capture("phone-very-narrow-terminal-actions")
    }

}
