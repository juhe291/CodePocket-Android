package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.DesignTokens
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual MainActivity startup/failed ContentResolver transitions, with the real IO worker held briefly. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class WorkspaceStartupIntegrationTest {
    private var controller:ActivityController<MainActivity>?=null
    private lateinit var main:MainActivity
    private var release:CountDownLatch?=null
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun root()=main.findViewById<View>(android.R.id.content)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun launch(light:Boolean=false,scale:Float=1f,unavailable:Boolean=false) {
        RuntimeEnvironment.setFontScale(scale)
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("settings",AppSettings(theme=if(light)UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson())
            .putString("activeFile","main.py")
        if(unavailable)prefs.putString("selectedTree","content://com.codepocket.unavailable.documents/tree/root")
        prefs.commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        this.controller=controller;main=controller.get()
    }
    private fun holdIo() {
        val entered=CountDownLatch(1);val gate=CountDownLatch(1);release=gate
        WorkspaceIo.executor.execute{entered.countDown();check(gate.await(30,TimeUnit.SECONDS))}
        assertTrue(entered.await(5,TimeUnit.SECONDS))
    }
    private fun capture(name:String) {
        val v=root();val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY))
        v.layout(0,0,v.measuredWidth,v.measuredHeight)
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd
        val folder=File(repo,"verification/workbench-startup-ui-2026-10-24/previews").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    private fun loading(light:Boolean,name:String) {
        holdIo();launch(light)
        assertTrue(field("workspaceBusy"));assertFalse(views(root()).any{it is WorkbenchEditor})
        assertTrue(views(root()).filterIsInstance<TextView>().any{it.text.toString()==main.getString(R.string.workspace_loading)})
        val palette=DesignTokens.palette(!light)
        assertEquals(palette.surface,main.window.statusBarColor);assertEquals(palette.panel,main.window.navigationBarColor)
        capture(name)
        release!!.countDown();drain()
        val editor=field<WorkbenchEditor>("editor");val workspace=field<Workspace>("workspace")
        assertFalse(field("workspaceBusy"));assertEquals(workspace.read(workspace.resolve("main.py")),editor.text.toString())
        assertFalse(views(root()).filterIsInstance<TextView>().any{it.text.toString()==main.getString(R.string.workspace_loading)})
        assertEquals(palette.surface,main.window.statusBarColor);assertEquals(palette.panel,main.window.navigationBarColor)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneDarkLoadingBecomesTheRealWorkspace(){loading(false,"phone-dark-loading")}
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightLoadingBecomesTheRealWorkspace(){loading(true,"tablet-light-loading")}
    @Test @Config(sdk=[28],qualifiers="w360dp-h640dp") fun phoneLargeFailurePreservesDraftAndOpensActualInternalProject() {
        val stored=DraftStore(File(RuntimeEnvironment.getApplication().filesDir,"drafts")){a,b->android.system.Os.rename(a.path,b.path)}
        stored.put("retained-startup","lost.py","print('keep draft')\n")
        launch(scale=1.8f,unavailable=true);drain()
        val drafts=field<DraftStore>("drafts")
        assertEquals("print('keep draft')\n",drafts.read("retained-startup")!!.text)
        assertFalse(field("workspaceBusy"));assertFalse(views(root()).any{it is WorkbenchEditor})
        assertTrue(views(root()).filterIsInstance<TextView>().any{it.text.toString()==main.getString(R.string.workspace_unavailable)})
        capture("phone-large-failed-workspace")
        val recovery=ShadowAlertDialog.getLatestAlertDialog();assertTrue(recovery.isShowing)
        assertEquals("选择目录",recovery.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        recovery.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();drain()
        assertEquals("internal",field<Workspace>("workspace").identity)
        assertEquals("print('keep draft')\n",drafts.read("retained-startup")!!.text)
        assertTrue(views(root()).any{it is WorkbenchEditor});assertFalse(recovery.isShowing)
    }
    @After fun close() {
        release?.countDown()
        if(::main.isInitialized){main.finish();controller?.pause()?.stop()?.destroy();drain()}
        RuntimeEnvironment.setFontScale(1f)
    }
}
