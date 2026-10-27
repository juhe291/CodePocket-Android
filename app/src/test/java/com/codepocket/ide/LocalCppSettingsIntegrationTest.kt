package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.codepocket.ide.ui.DesignTokens
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

/** Draw the actual settings component; this is host rendering, not a device result. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class LocalCppSettingsIntegrationTest {
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun layout(dialog:AlertDialog) {
        idle();val view=dialog.window!!.decorView;val metrics=view.resources.displayMetrics
        val width=minOf(view.layoutParams.width.takeIf{it>0}?:metrics.widthPixels,metrics.widthPixels)
        val height=minOf(view.layoutParams.height.takeIf{it>0}?:metrics.heightPixels,metrics.heightPixels)
        repeat(2){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.AT_MOST));view.layout(0,0,view.measuredWidth,view.measuredHeight);view.viewTreeObserver.dispatchOnGlobalLayout();idle()}
    }
    private fun capture(name:String,dialog:AlertDialog) {
        layout(dialog);val view=dialog.window!!.decorView
        assertTrue(view.width>0&&view.height>0)
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(DesignTokens.forContext(view.context).overlay);view.draw(canvas)
        val directory=File(CppFixtures.root,"verification/cpp-local-integration-2026-10-27/previews").apply{mkdirs()}
        File(directory,"$name.png").outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }
    private fun choose(dialog:AlertDialog,title:String):AlertDialog {
        val label=views(dialog.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()==title}
        (label.parent.parent as View).performClick();idle()
        return ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing);layout(it)}
    }
    private fun select(picker:AlertDialog,index:Int) {
        picker.listView.performItemClick(picker.listView.adapter.getView(index,null,picker.listView),index,index.toLong());idle()
    }
    private fun check(name:String,light:Boolean,large:Boolean) {
        RuntimeEnvironment.setFontScale(if(large)2f else 1f)
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible();val activity=controller.get()
        val original=AppSettings(theme=if(light)UiTheme.LIGHT else UiTheme.DARK,animations=false)
        activity.getSharedPreferences("codepocket_v2",0).edit().putString("settings",original.toJson()).commit()
        var saved:AppSettings?=null
        val draft=SettingsDialog.Draft(original,category=4)
        val dialog=SettingsDialog.show(activity,original,{saved=it},draft.checkpoint(),null)
        try {
            layout(dialog)
            // Search uses production rows and avoids scrolling past unrelated terminal preferences.
            views(dialog.window!!.decorView).filterIsInstance<EditText>().single{it.contentDescription=="搜索设置"}.setText("C++")
            layout(dialog);capture("$name-settings",dialog)
            var picker=choose(dialog,"C++ 运行方式")
            assertEquals(2,picker.listView.adapter.count);assertEquals(0,picker.listView.checkedItemPosition)
            capture("$name-backend",picker);select(picker,1)
            assertNull(saved)
            picker=choose(dialog,"本地 C++ 构建范围");assertEquals(2,picker.listView.adapter.count)
            capture("$name-scope",picker);select(picker,1)
            val checkpoint=SettingsDialog.checkpoint(dialog)!!
            val value=AppSettings.fromJson(JSONObject(checkpoint).getString("draft"))
            assertEquals(CppBackend.LOCAL,value.cppBackend);assertEquals(CppBuildScope.PROJECT,value.cppBuildScope);assertNull(saved)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle()
            assertEquals(value,saved);assertFalse(dialog.isShowing)
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf{it.isShowing}?.dismiss();dialog.dismiss()
            controller.pause().stop().destroy();RuntimeEnvironment.setFontScale(1f)
        }
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLightLargeKeepsBackendScopeAndDraftUntilApply(){check("phone-light-large",true,true)}
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletDarkKeepsBackendScopeAndDraftUntilApply(){check("tablet-dark",false,false)}
}
