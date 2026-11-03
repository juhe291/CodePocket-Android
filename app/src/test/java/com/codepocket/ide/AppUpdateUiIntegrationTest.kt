package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class AppUpdateUiIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private lateinit var settings:AlertDialog
    private val calls=AtomicInteger()
    private var next:()->AppUpdateSource={Immediate(AppUpdateResult(AppUpdateKind.NO_RELEASE))}
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(15,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun await(condition:()->Boolean){val deadline=System.nanoTime()+8_000_000_000L;while(!condition()&&System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));Thread.sleep(5)};assertTrue("Update UI did not settle",condition())}
    private fun category(title:String){
        if(views(settings.window!!.decorView).none{it.contentDescription==title}){views(settings.window!!.decorView).single{it.contentDescription=="返回设置分类"}.performClick();idle()}
        views(settings.window!!.decorView).single{it.contentDescription==title}.performClick();idle()
    }
    private fun openSettings(){ReflectionHelpers.callInstanceMethod<Unit>(main,"showSettings");idle();settings=field("toolDialog")}
    private fun openUpdates():AlertDialog {
        category("帮助与许可");settings.window!!.decorView.findViewWithTag<View>("settings-check-updates").performClick();idle()
        return ReflectionHelpers.getField(field<AppUpdateDialog>("appUpdateDialog"),"dialog")
    }
    private fun body(dialog:AlertDialog)=dialog.window!!.decorView.findViewWithTag<TextView>("app-update-result")
    private fun available()=AppUpdateMetadata.parse(AppUpdateFixtures.repository,AppUpdateFixtures.current,AppUpdateFixtures.release().toString())
    private class Immediate(private val result:AppUpdateResult):AppUpdateSource {
        override fun check(current:AppUpdateVersion):AppUpdateResult{assertNotSame(Looper.getMainLooper(),Looper.myLooper());return result}
        override fun cancel(){}
    }
    private class Pending:AppUpdateSource {
        val entered=AtomicBoolean();val cancelled=AtomicBoolean();private val gate=CountDownLatch(1)
        override fun check(current:AppUpdateVersion):AppUpdateResult{entered.set(true);try{gate.await(7,TimeUnit.SECONDS)}catch(_:InterruptedException){throw CancellationException()};return AppUpdateResult(AppUpdateKind.NO_RELEASE)}
        override fun cancel(){cancelled.set(true);gate.countDown()}
    }
    private fun launch(light:Boolean=false){val app=RuntimeEnvironment.getApplication();Workspace(app)
        app.getSharedPreferences("codepocket_v2",0).edit().clear().putBoolean("workspaceOpen",true).putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(light)UiTheme.LIGHT else UiTheme.DARK,animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val root=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(3){root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));root.layout(0,0,root.measuredWidth,root.measuredHeight);root.viewTreeObserver.dispatchOnGlobalLayout();idle()}
        ReflectionHelpers.setField(main,"appUpdateRepository",AppUpdateFixtures.repository)
        ReflectionHelpers.setField(main,"appUpdateSourceFactory",{_:AppUpdateRepository->calls.incrementAndGet();next()});openSettings()
    }
    private fun capture(dialog:AlertDialog,name:String){idle();val view=dialog.window!!.decorView;assertTrue(view.width>0&&view.height>0)
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val folder=File(CppFixtures.root,"verification/app-updates-2026-11-03/previews").apply{mkdirs()};File(folder,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){settings.dismiss();main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    @Test fun manualCheckKeepsTheRealSettingsDraftEditorAndTerminalOwner(){
        launch();editor.setText("print('update keeps my draft')\n");val native=editor.inputView();val registry=field<Any>("terminalRegistry")
        category("编辑器");val toggle=views(settings.window!!.decorView).filterIsInstance<Switch>().single{it.contentDescription=="自动换行"};toggle.isChecked=!toggle.isChecked
        category("帮助与许可");val checkpoint=SettingsDialog.checkpoint(settings);val revision=field<Long>("editRevision")
        assertEquals(0,calls.get());next={Immediate(available())};val dialog=openUpdates();await{body(dialog).text.contains("发现新版本")}
        assertEquals(1,calls.get());assertEquals(checkpoint,SettingsDialog.checkpoint(settings));assertTrue(settings.isShowing)
        assertSame(native,editor.inputView());assertSame(registry,field("terminalRegistry"));assertEquals(revision,field<Long>("editRevision"));assertTrue(field("dirty"))
        assertEquals("print('update keeps my draft')\n",editor.text.toString());assertTrue(body(dialog).isTextSelectable)
        assertTrue(body(dialog).text.contains("最后一行。"));capture(dialog,"phone-dark-update-available")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle()
        val intent=Shadows.shadowOf(main).nextStartedActivity;assertEquals(AppUpdateFixtures.asset().getString("browser_download_url"),intent.dataString)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightLargeNoReleaseIsNotReportedAsLatest(){
        RuntimeEnvironment.setFontScale(1.5f);launch(true);val dialog=openUpdates();await{body(dialog).text.contains("尚无可访问")}
        assertFalse(body(dialog).text.contains("当前已是最新"));assertEquals("查看发布页",dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        assertTrue(dialog.window!!.attributes.width<=640);capture(dialog,"tablet-light-large-no-release")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertEquals(AppUpdateFixtures.repository.releasesPage,Shadows.shadowOf(main).nextStartedActivity.dataString)
    }
    @Test fun aCurrentOrNewerInstalledVersionDoesNotOfferAnOlderApk(){
        launch();next={Immediate(AppUpdateResult(AppUpdateKind.AHEAD,available().release))};val dialog=openUpdates()
        await{body(dialog).text.contains("比最新正式版本更新")};assertEquals(View.GONE,dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility)
        assertNull(Shadows.shadowOf(main).nextStartedActivity);assertEquals(1,calls.get())
    }
    @Test fun closingAnInFlightCheckCancelsItAndKeepsSettingsOpen(){
        launch();val pending=Pending();next={pending};val checkpoint=SettingsDialog.checkpoint(settings);val dialog=openUpdates();await{pending.entered.get()}
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();await{pending.cancelled.get()}
        assertFalse(dialog.isShowing);assertTrue(settings.isShowing)
        // Category changed only when opening help; all setting values remain untouched.
        assertEquals(org.json.JSONObject(checkpoint!!).getString("draft"),org.json.JSONObject(SettingsDialog.checkpoint(settings)!!).getString("draft"))
        assertEquals(1,calls.get());assertFalse(main.isFinishing)
    }
    @Test fun recreationCancelsTheRequestAndRestoresTheUnappliedSettingsWithoutCheckingAgain(){
        launch();category("编辑器");val wrap=views(settings.window!!.decorView).filterIsInstance<Switch>().single{it.contentDescription=="自动换行"};wrap.isChecked=!wrap.isChecked
        category("帮助与许可");val before=SettingsDialog.checkpoint(settings);val pending=Pending();next={pending};openUpdates();await{pending.entered.get()}
        controller.recreate();main=controller.get();drain();settings=field("toolDialog");assertTrue(pending.cancelled.get());assertTrue(settings.isShowing)
        assertEquals(org.json.JSONObject(before!!).getString("draft"),org.json.JSONObject(SettingsDialog.checkpoint(settings)!!).getString("draft"))
        assertNull(field<AppUpdateDialog?>("appUpdateDialog"));assertEquals(1,calls.get())
    }
    @Test fun retryIsExplicitAndReusesTheSameReadOnlyDialog(){
        launch();var attempt=0;next={Immediate(if(attempt++==0)AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.NETWORK) else available())}
        val dialog=openUpdates();await{body(dialog).text.contains("无法连接更新源")};assertEquals(1,calls.get())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();await{body(dialog).text.contains("发现新版本")}
        assertEquals(2,calls.get());assertSame(dialog,ReflectionHelpers.getField(field<AppUpdateDialog>("appUpdateDialog"),"dialog"));assertTrue(settings.isShowing)
    }
}
