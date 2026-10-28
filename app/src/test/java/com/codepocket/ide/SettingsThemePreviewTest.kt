package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.time.Duration

/** Actual settings draft/preview, using the shipped Sora TextMate theme assets as authority. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class SettingsThemePreviewTest {
    private lateinit var activity:Activity
    private lateinit var dialog:AlertDialog
    private var applied:AppSettings?=null
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220))}
    @Before fun launch(){activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()}
    @After fun close(){if(::dialog.isInitialized)dialog.dismiss();activity.finish();idle()}
    private fun show(settings:AppSettings){dialog=SettingsDialog.show(activity,settings,{applied=it},SettingsDialog.Draft(settings,category=0).checkpoint(),null);idle()}
    private fun preview()=views(dialog.window!!.decorView).single{it.contentDescription=="主题预览示意，不是编辑文件"}
    private fun token(text:String):Int {
        val view=views(preview()).filterIsInstance<TextView>().first{it.text.contains(text)}
        val start=view.text.indexOf(text);val styled=view.text as? Spanned
        return styled?.getSpans(start,start+1,ForegroundColorSpan::class.java)?.lastOrNull()?.foregroundColor ?: view.currentTextColor
    }
    private fun expected(dark:Boolean,scope:String):Int {
        val data=JSONObject(activity.assets.open("textmate/${if(dark)"dark" else "light"}.json").bufferedReader().use{it.readText()})
        val tokens=data.getJSONArray("tokenColors")
        return (0 until tokens.length()).map{tokens.getJSONObject(it)}.single{row->val scopes=row.getJSONArray("scope");(0 until scopes.length()).any{scopes.getString(it)==scope}}
            .getJSONObject("settings").getString("foreground").let(Color::parseColor)
    }
    private fun capture(name:String){val v=preview();v.measure(View.MeasureSpec.makeMeasureSpec(340,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));v.layout(0,0,v.measuredWidth,v.measuredHeight)
        val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val dir=File(CppFixtures.root,"verification/test-case-batch-2026-10-28/previews").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
    @Test fun darkPreviewUsesDistinctProductionTokenColorsInsteadOfOneWorkbenchColorPerLine(){
        show(AppSettings(theme=UiTheme.DARK,animations=false));assertEquals(expected(true,"storage.type"),token("int main"));assertEquals(expected(true,"entity.name.function"),token("main()"));assertEquals(expected(true,"string"),token("Hello"));capture("theme-dark-preview")
        assertNull(applied)
    }
    @Test fun choosingLightUiRefreshesTheFollowingCodeThemeWithoutApplyingTheSettingsDraft(){
        val initial=AppSettings(theme=UiTheme.DARK,animations=false);show(initial)
        val title=views(dialog.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()=="界面主题"};(title.parent.parent as View).performClick();idle()
        val picker=ShadowAlertDialog.getLatestAlertDialog();picker.listView.performItemClick(null,1,1);idle()
        assertEquals(expected(false,"storage.type"),token("int main"));assertEquals(expected(false,"string"),token("Hello"));assertNull(applied)
        assertEquals(UiTheme.LIGHT,AppSettings.fromJson(JSONObject(SettingsDialog.checkpoint(dialog)!!).getString("draft")).theme)
        capture("theme-light-preview")
    }
    @Test fun explicitDarkSyntaxRemainsDarkInsideALightUiPreview(){
        show(AppSettings(theme=UiTheme.LIGHT,syntax=SyntaxTheme.DARK,animations=false));assertEquals(expected(true,"storage.type"),token("int main"));assertEquals(expected(true,"string"),token("Hello"));assertNull(applied);capture("theme-light-ui-dark-code")
    }
}
