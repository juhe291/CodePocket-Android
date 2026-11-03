package com.codepocket.ide

import android.app.AlertDialog
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
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@LooperMode(LooperMode.Mode.PAUSED)
class AppUpdateEntryBaselineTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private lateinit var settings:AlertDialog
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(4){WorkspaceIo.executor.submit{}.get(15,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    @Before fun launch(){
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("settings",AppSettings(animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showSettings");idle();settings=ReflectionHelpers.getField(main,"toolDialog")
        views(settings.window!!.decorView).single{it.contentDescription=="帮助与许可"}.performClick();idle()
    }
    @After fun close(){if(::main.isInitialized){settings.dismiss();main.finish();controller.pause().stop().destroy();drain()}}
    @Test fun helpProvidesExactlyOneEnabledCheckUpdateAction(){
        val rows=views(settings.window!!.decorView).filterIsInstance<TextView>().filter{it.text.toString()=="检查更新"}
        assertEquals("The actual settings help page needs a single manual update entry",1,rows.size)
        assertTrue(rows.single().isEnabled)
    }
    @Test fun helpDisplaysTheActualInstalledVersion(){
        @Suppress("DEPRECATION") val info=main.packageManager.getPackageInfo(main.packageName,0)
        @Suppress("DEPRECATION") val expected="当前版本 ${info.versionName?:"未知"} (${info.versionCode})"
        assertTrue("Installed package metadata must be visible",views(settings.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()==expected})
    }
}
