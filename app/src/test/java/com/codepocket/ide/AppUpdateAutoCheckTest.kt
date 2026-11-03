package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Startup auto-check is opt-in, once per process, main-thread delivered and silent unless newer. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class AppUpdateAutoCheckTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val calls=AtomicInteger()
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(15,TimeUnit.SECONDS);idle()}}
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun result(kind:AppUpdateKind)=when(kind){
        AppUpdateKind.AVAILABLE->AppUpdateMetadata.parse(AppUpdateFixtures.repository,AppUpdateFixtures.current,AppUpdateFixtures.release().toString())
        else->AppUpdateResult(kind)
    }
    private inner class Immediate(private val value:AppUpdateResult):AppUpdateSource {
        override fun check(current:AppUpdateVersion):AppUpdateResult{calls.incrementAndGet();assertNotSame(Looper.getMainLooper(),Looper.myLooper());return value}
        override fun cancel(){}
    }
    private fun launch(autoCheck:Boolean,source:AppUpdateResult){
        calls.set(0);AppUpdateAutoCheck.resetAttemptedForTests()
        AppUpdateAutoCheck.sourceFactoryForTests={ _:AppUpdateRepository -> Immediate(source) }
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=UiTheme.DARK,animations=false,autoSave=false,autoCheckUpdates=autoCheck).toJson())
            .commit()
        Workspace(app)
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get()
        drain();controller.windowFocusChanged(true)
        val deadline=System.nanoTime()+8_000_000_000L
        while(calls.get()==0 && System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));Thread.sleep(5)}
    }
    @After fun close(){if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};AppUpdateAutoCheck.resetAttemptedForTests();AppUpdateAutoCheck.sourceFactoryForTests=null}
    @Test fun disabledSettingNeverStartsANetworkCheck(){
        launch(false,result(AppUpdateKind.AVAILABLE));idle()
        assertEquals(0,calls.get());assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }
    @Test fun availableReleaseShowsTheSharedNativeDialogButCurrentVersionStaysSilent(){
        launch(true,result(AppUpdateKind.AVAILABLE))
        assertEquals(1,calls.get())
        val deadline=System.nanoTime()+8_000_000_000L
        var dialog:AlertDialog?=null
        while(System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));dialog=ShadowAlertDialog.getLatestAlertDialog();if(dialog!=null)break;Thread.sleep(5)}
        assertNotNull("startup auto-check must show a real dialog only when newer",dialog)
        val body=views(dialog!!.window!!.decorView).filterIsInstance<TextView>().single{it.contentDescription=="自动更新检查结果"}
        assertTrue(body.text.toString(),body.text.toString().contains("发现新版本 0.2.2"));assertTrue(body.text.toString().contains("实际更新说明"))
        dialog!!.dismiss()
        main.finish();controller.pause().stop().destroy();drain()
        launch(true,result(AppUpdateKind.CURRENT))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertEquals(1,calls.get());assertFalse("current version must stay quiet",ShadowAlertDialog.getLatestAlertDialog()?.isShowing==true)
    }
    @Test fun oneAttemptPerProcessSurvivesActivityRecreation(){
        launch(true,result(AppUpdateKind.CURRENT))
        assertEquals(1,calls.get())
        val previous=main
        controller.configurationChange(Configuration(main.resources.configuration).apply{screenWidthDp=320;screenHeightDp=800})
        main=controller.get();controller.visible();drain();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertNotSame(previous,main);assertEquals(1,calls.get())
    }
    @Test fun cancelBeforeDeliveryPreventsAnyCallback(){
        AppUpdateAutoCheck.resetAttemptedForTests()
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val gate=CountDownLatch(1);val entered=CountDownLatch(1);val cancelled=AtomicInteger()
        val check=AppUpdateAutoCheck(activity,AppUpdateFixtures.repository,{ _:AppUpdateRepository ->
            object:AppUpdateSource{
                override fun check(current:AppUpdateVersion):AppUpdateResult{entered.countDown();gate.await(5,TimeUnit.SECONDS);return result(AppUpdateKind.AVAILABLE)}
                override fun cancel(){cancelled.incrementAndGet();gate.countDown()}
            }
        })
        var delivered=0
        check.checkOnce{_,_->delivered++}
        assertTrue(entered.await(5,TimeUnit.SECONDS))
        check.close();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        assertEquals(0,delivered);assertEquals(1,cancelled.get())
        activity.finish()
    }
}
