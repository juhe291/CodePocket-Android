package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.ide.ui.*
import com.codepocket.ide.terminal.shell.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit

/** Measures and draws the production native controls; screenshots use an explicitly marked host fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TerminalChromeDesignTest {
    private fun measure(view:View,width:Int,height:Int?=null) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height?:1000,if(height==null)View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY))
        view.layout(0,0,width,view.measuredHeight)
    }
    private fun descendants(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{descendants(view.getChildAt(it))}else emptyList()
    private fun header(activity:Activity,dark:Boolean=true):PanelToolbar {
        val p=DesignTokens.palette(dark)
        val toolbar=PanelToolbar(activity,p,{},{},{},{},{})
        val tabs=LinearLayout(activity)
        tabs.addView(TerminalChrome.channel(activity,p,"终端",IdeIcon.TERMINAL,true){})
        tabs.addView(TerminalChrome.channel(activity,p,"问题",IdeIcon.INFO,false){})
        val sessions=LinearLayout(activity)
        sessions.addView(TerminalChrome.session(activity,p,"Shell 1 · 很长的项目名称","选择终端会话"){},LinearLayout.LayoutParams(0,48,1f))
        sessions.addView(TerminalChrome.action(activity,p,IdeIcon.ADD,"新建终端",Runnable{}),LinearLayout.LayoutParams(48,48))
        sessions.addView(TerminalChrome.action(activity,p,IdeIcon.DELETE,"关闭当前终端会话",Runnable{}),LinearLayout.LayoutParams(48,48))
        toolbar.bindNavigation(HorizontalScrollView(activity).apply{addView(tabs)},sessions)
        toolbar.setInputAvailable(false)
        toolbar.setCloseSessionAction {}
        return toolbar
    }
    @Test fun phoneKeepsSessionAndCloseActionInASecondWellSizedRow() {
        val a=Robolectric.buildActivity(Activity::class.java).setup().get()
        val h=header(a);measure(h,320)
        assertEquals(96,h.measuredHeight)
        val close=descendants(h).single{it.contentDescription=="关闭当前终端会话"&&it.visibility==View.VISIBLE}
        assertEquals(48,close.measuredWidth);assertEquals(48,close.measuredHeight)
        assertTrue(close.bottom<=close.rootView.height)
    }
    @Test fun tabletCombinesNavigationAndSessionIntoOneRowAndRestoresOnResize() {
        val a=Robolectric.buildActivity(Activity::class.java).setup().get()
        val h=header(a);measure(h,840);assertEquals(48,h.measuredHeight)
        measure(h,411);assertEquals(96,h.measuredHeight)
        measure(h,840);assertEquals(48,h.measuredHeight)
    }
    @Test fun compactModePreservesTopLevelNavigationAndOverflowWithoutSessionRow() {
        val a=Robolectric.buildActivity(Activity::class.java).setup().get()
        val h=header(a);h.setCompact(true);measure(h,320)
        assertEquals(48,h.measuredHeight)
        assertTrue(descendants(h).filterIsInstance<TextView>().any{it.text=="终端"&&it.visibility==View.VISIBLE})
        assertEquals(View.VISIBLE,h.menuAnchor.visibility)
        assertTrue(descendants(h).any{it.contentDescription=="关闭当前终端会话"&&it.visibility==View.VISIBLE&&it.measuredWidth==48})
    }
    @Test fun largeFontUsesTwoRowsEvenOnWideWindow() {
        val a=Robolectric.buildActivity(Activity::class.java).setup().get()
        a.resources.configuration.fontScale=1.6f
        val h=header(a);measure(h,840);assertEquals(96,h.measuredHeight)
    }
    @Test fun closeButtonCancelsSafelyAndThenClosesOnlyTheCurrentSession() {
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("settings",AppSettings(animations=false,autoSave=false).toJson()).commit()
        ShellSessionRegistry.finishAll()
        val c=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val main=c.get()
        try {
            WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()
            val registry=ReflectionHelpers.getField<ShellSessionRegistry>(main,"terminalRegistry")
            val first=registry.catalog.selected()!!;val second=registry.create(null)
            ReflectionHelpers.callInstanceMethod<Unit>(main,"showOutput",ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!,3))
            val strip=ReflectionHelpers.getField<LinearLayout>(main,"terminalSessionStrip")
            val close=descendants(strip).single{it.contentDescription=="关闭当前终端会话"}
            close.performClick();assertNotNull(registry.catalog.find(second.id))
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(registry.catalog.find(second.id));assertNotNull(registry.catalog.find(first.id))
            close.performClick();ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
            assertNull(registry.catalog.find(second.id));assertNotNull(registry.catalog.find(first.id))
            assertEquals(first.id,registry.catalog.selectedId())
        }finally {main.finish();c.pause().stop().destroy();ShellSessionRegistry.finishAll()}
    }

    private fun capture(width:Int,dark:Boolean,name:String) {
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit()
            .putString("settings",AppSettings(theme=if(dark)UiTheme.DARK else UiTheme.LIGHT,animations=false).toJson()).commit()
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val a=c.get()
        val runtime=ShellRuntime.create(a)
        val plan=ShellLaunchPlan.prepare(a.filesDir,a.cacheDir,null)
        val session=com.termux.terminal.TerminalSession(ShellLaunchPlan.EXECUTABLE,plan.directory.path,plan.arguments,plan.environment,2000,runtime)
        val emulator=com.termux.terminal.TerminalEmulator(session,if(width<600)46 else 112,12,10,18,2000,runtime)
        ReflectionHelpers.setField(session,"mEmulator",emulator);ReflectionHelpers.setField(session,"mSessionClosed",true)
        ReflectionHelpers.setField(runtime,"session",session);ReflectionHelpers.setField(runtime,"plan",plan);ReflectionHelpers.setField(runtime,"state",ShellRuntime.State.OPEN)
        val pane=ShellTerminalPane(a,null,12,false,{},runtime)
        pane.setEmbedded(true);pane.setAppearance("FOLLOW_UI",dark)
        val fixture="\u001b[34mCodePocket\u001b[36m ~/workspace \u001b[32m$ \u001b[0m# IDE: main.py (Chaquopy)\r\nHello, CodePocket!\r\n\u001b[36mEnter a number: \u001b[0m42\r\nResult: \u001b[32m1764\u001b[0m\r\n\u001b[34mCodePocket\u001b[36m ~/workspace \u001b[32m$ \u001b[0m"
        val bytes=fixture.toByteArray(Charsets.UTF_8);emulator.append(bytes,bytes.size)
        val root=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(DesignTokens.palette(dark).panel)}
        root.addView(TextView(a).apply{text="原生界面主机预览 · 示例终端内容";textSize=11f;setTextColor(DesignTokens.palette(dark).muted);setPadding(12,4,12,4)},LinearLayout.LayoutParams(-1,28))
        root.addView(header(a,dark),LinearLayout.LayoutParams(-1,-2));root.addView(pane,LinearLayout.LayoutParams(-1,0,1f))
        a.setContentView(root);measure(root,width,430);pane.changed()
        val bitmap=Bitmap.createBitmap(width,430,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
        val out=File("../verification/terminal-polish-2026-10-20/$name.png");out.parentFile!!.mkdirs()
        out.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        pane.dispose();runtime.shutdown();c.pause().stop().destroy()
        assertTrue(out.isFile&&out.length()>1000)
    }
    @Test fun nativePhoneDarkPreview() {capture(411,true,"phone-dark")}
    @Test fun nativeTabletDarkPreview() {capture(1000,true,"tablet-dark")}
    @Test fun nativePhoneLightPreview() {capture(411,false,"phone-light")}
    @Test fun nativeTabletLightPreview() {capture(1000,false,"tablet-light")}
}
