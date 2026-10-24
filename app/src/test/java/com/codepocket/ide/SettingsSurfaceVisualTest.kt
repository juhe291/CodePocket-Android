package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

/** Real SettingsDialog drawings and draft behavior; no simulated program execution. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class SettingsSurfaceVisualTest {
    private lateinit var activity:Activity
    private lateinit var dialog:AlertDialog
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))}
    private fun show(light:Boolean=false,scale:Float=1f){
        RuntimeEnvironment.setFontScale(scale)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().putString("settings",AppSettings(theme=if(light)UiTheme.LIGHT else UiTheme.DARK,animations=false).toJson()).commit()
        activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        dialog=SettingsDialog.show(activity,AppSettings(theme=if(light)UiTheme.LIGHT else UiTheme.DARK,animations=false)){fail("Visual navigation must not apply settings")};idle()
    }
    @After fun close(){if(::dialog.isInitialized)dialog.dismiss();if(::activity.isInitialized)activity.finish();RuntimeEnvironment.setFontScale(1f)}
    private fun screenshot(name:String){
        idle();val v=dialog.window!!.decorView
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd
        val out=File(repo,"verification/workbench-settings-consistency-2026-10-24/previews").apply{mkdirs()}
        File(out,"$name.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
        val button=dialog.getButton(AlertDialog.BUTTON_POSITIVE);assertTrue(button.height>=48)
        val position=IntArray(2);button.getLocationOnScreen(position);val top=IntArray(2);v.getLocationOnScreen(top)
        assertTrue(position[1]+button.height>=top[1]+v.height-32)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneCategoryAndEditorPage(){
        show();screenshot("phone-settings-categories")
        tree(dialog.window!!.decorView).single{it.contentDescription=="编辑器"}.performClick();idle()
        assertTrue(tree(dialog.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()=="代码字号"})
        screenshot("phone-settings-editor")
        val before=SettingsDialog.checkpoint(dialog)!!;assertTrue(before.contains("category"))
        tree(dialog.window!!.decorView).single{it.contentDescription=="返回设置分类"}.performClick();idle()
        assertTrue(tree(dialog.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()=="工作台偏好"})
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightHasNavigationAndContentLayers(){
        show(light=true);assertTrue(tree(dialog.window!!.decorView).any{it.contentDescription=="外观，已选择"});screenshot("tablet-light-settings")
        tree(dialog.window!!.decorView).single{it.contentDescription=="终端与运行"}.performClick();idle()
        screenshot("tablet-light-terminal-settings")
    }
    @Test @Config(qualifiers="w320dp-h800dp") fun narrowLargeRunDetailsWrapAndRemainReachable(){
        show(scale=1.7f)
        tree(dialog.window!!.decorView).single{it.contentDescription=="终端与运行"}.performClick();idle()
        val disclosure=tree(dialog.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()==activity.getString(R.string.settings_cpp_mode_help)}
        assertTrue(disclosure.text.contains("代码和输入会发送至第三方 Wandbox"))
        val textLayout=disclosure.layout;assertNotNull(textLayout);assertTrue(textLayout.lineCount>1)
        for(line in 0 until textLayout.lineCount){
            assertEquals(0,textLayout.getEllipsisCount(line))
            assertTrue(textLayout.getLineWidth(line)<=disclosure.width-disclosure.compoundPaddingLeft-disclosure.compoundPaddingRight+1)
        }
        assertTrue(textLayout.height<=disclosure.height-disclosure.compoundPaddingTop-disclosure.compoundPaddingBottom)
        val scroller=generateSequence(disclosure.parent){it.parent}.filterIsInstance<ScrollView>().first()
        // fullScroll animates by default; use the immediate native scroll for deterministic host geometry.
        scroller.isSmoothScrollingEnabled=false;scroller.fullScroll(View.FOCUS_DOWN);idle()
        screenshot("phone-large-terminal-settings")
        val visible=android.graphics.Rect();assertTrue("disclosure visible after scroll=${scroller.scrollY}, viewport=${scroller.height}, content=${scroller.getChildAt(0).height}",disclosure.getGlobalVisibleRect(visible))
        assertEquals(disclosure.height,visible.height())
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneLargeSearchKeepsEveryControlReadable(){
        show(scale=1.7f)
        tree(dialog.window!!.decorView).filterIsInstance<EditText>().single().setText("光标");idle()
        val switches=tree(dialog.window!!.decorView).filterIsInstance<Switch>();assertTrue(switches.size>=2)
        assertTrue(switches.all{it.height>=48});screenshot("phone-large-settings-search")
    }
}
