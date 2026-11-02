package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import com.codepocket.ide.ui.DesignTokens
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

internal object FormattingPreview {
    fun capture(name:String,view:View){
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        assertTrue(view.width>0 && view.height>0)
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val output=File(CppFixtures.root,"verification/formatting-2026-10-30/previews").apply{mkdirs()}
        File(output,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
}

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class FormattingSurfaceTest {
    private fun tree(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{tree(v.getChildAt(it))}else emptyList()
    private fun layout(dialog:AlertDialog){
        val decor=dialog.window!!.decorView;val dm=decor.resources.displayMetrics
        repeat(2){decor.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);decor.viewTreeObserver.dispatchOnGlobalLayout();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))}
    }
    private fun tap(dialog:AlertDialog,view:View){
        layout(dialog);val rect=Rect();assertTrue(view.getGlobalVisibleRect(rect));assertTrue(rect.width()>0 && rect.height()>0)
        val origin=IntArray(2);dialog.window!!.decorView.getLocationOnScreen(origin);val time=SystemClock.uptimeMillis()
        for((action,delay) in listOf(MotionEvent.ACTION_DOWN to 0L,MotionEvent.ACTION_UP to 30L)){
            val event=MotionEvent.obtain(time,time+delay,action,(rect.centerX()-origin[0]).toFloat(),(rect.centerY()-origin[1]).toFloat(),0).apply{source=InputDevice.SOURCE_TOUCHSCREEN}
            try{dialog.dispatchTouchEvent(event)}finally{event.recycle()}
        }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
    }
    private fun verify(light:Boolean,scale:Float,name:String){
        RuntimeEnvironment.setFontScale(scale)
        val original=AppSettings(theme=if(light)UiTheme.LIGHT else UiTheme.DARK,animations=false)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("settings",original.toJson()).commit()
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible();val activity=controller.get()
        assertEquals("The preview must use the requested real palette",light,Color.luminance(DesignTokens.forContext(activity).surface)>0.5f)
        var saved:AppSettings?=null;val dialog=SettingsDialog.show(activity,original){saved=it}
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
            tree(dialog.window!!.decorView).filterIsInstance<EditText>().single().setText("保存时格式化")
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
            layout(dialog)
            val toggle=tree(dialog.window!!.decorView).filterIsInstance<Switch>().single()
            assertFalse(toggle.isChecked);assertTrue("Native switch must keep its 48dp target",toggle.height>=48);tap(dialog,toggle);assertTrue(toggle.isChecked)
            val draft=SettingsDialog.Draft.restore(original,SettingsDialog.checkpoint(dialog));assertTrue(draft.value.formatOnSave);assertNull(saved)
            FormattingPreview.capture(name,dialog.window!!.decorView)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(saved!!.formatOnSave)
        } finally {dialog.dismiss();controller.pause().stop().destroy();RuntimeEnvironment.setFontScale(1f)}
    }
    @Test @Config(qualifiers="w320dp-h640dp") fun phoneLargeTextUsesTheExistingSettingsDraftAndNativeToggle(){verify(false,1.5f,"phone-large-format-setting")}
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightUsesTheSameSettingAndApplyFlow(){verify(true,1f,"tablet-light-format-setting")}
}
