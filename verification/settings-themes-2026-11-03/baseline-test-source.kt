package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Duration

/** Real settings controls and serialized preferences; missing-capability baseline stays intact. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class SettingsSearchAndThemeBaselineTest {
    private lateinit var activity:Activity
    private lateinit var dialog:AlertDialog
    private val initial=AppSettings(animations=false,autoSave=false)
    private var saved:AppSettings?=null
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220))}
    @Before fun start(){activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get();show(null)}
    private fun show(checkpoint:String?){dialog=SettingsDialog.show(activity,initial,{saved=it},checkpoint,null);idle()}
    @After fun close(){if(::dialog.isInitialized)dialog.dismiss();activity.finish();idle()}
    private fun query(text:String){views(dialog.window!!.decorView).filterIsInstance<EditText>().single{it.contentDescription=="搜索设置"}.setText(text);idle()}
    private fun rows():List<LinearLayout> = views(dialog.window!!.decorView).filterIsInstance<LinearLayout>().filter {
        it.childCount==2 && it.getChildAt(0) is LinearLayout && (it.getChildAt(1) is Switch || it.getChildAt(1) is ImageView) &&
            (it.getChildAt(0) as LinearLayout).getChildAt(0) is TextView
    }
    private fun titles()=rows().map{((it.getChildAt(0) as LinearLayout).getChildAt(0) as TextView).text.toString()}
    @Test fun spacedChineseWordsMatchEveryTermAndKeepOriginalStableOrder(){
        query("自动 保存")
        assertEquals(listOf("自动保存","自动保存延迟"),titles());assertNull(saved)
    }
    @Test fun englishAliasesAndFullWidthWhitespaceFindTheSameRealSetting(){
        for(text in listOf("terminal font","ＴＥＲＭＩＮＡＬ　ＦＯＮＴ","terminalFontSize")){
            query(text);assertEquals(text,listOf("终端字号"),titles())
        }
        assertNull(saved)
    }
    @Test fun cppAndLocalTermsFindBothRealBackendAndScopeWithoutDuplicateRows(){
        query("C++ 本地")
        assertEquals(listOf("C++ 运行方式","本地 C++ 构建范围"),titles());assertEquals(titles().size,titles().toSet().size)
    }
    @Test fun searchingOperatingAndRestoringKeepTheSameUnappliedDraft(){
        query("word wrap");assertEquals(listOf("自动换行"),titles())
        rows().single().getChildAt(1).performClick();idle()
        query("terminal theme");assertEquals(listOf("终端配色"),titles())
        rows().single().performClick();idle();val picker=ShadowAlertDialog.getLatestAlertDialog()
        picker.listView.performItemClick(null,2,2);idle()
        val checkpoint=SettingsDialog.checkpoint(dialog)!!;dialog.dismiss();show(checkpoint)
        val state=JSONObject(SettingsDialog.checkpoint(dialog)!!)
        val draft=AppSettings.fromJson(state.getString("draft"))
        assertTrue(draft.wordWrap);assertEquals(TerminalTheme.NORD,draft.terminalTheme)
        assertEquals("terminal theme",state.getString("query"));assertNull(saved)
        assertFalse(initial.wordWrap);assertEquals(TerminalTheme.FOLLOW_UI,initial.terminalTheme)
    }
    @Test fun aValidSavedCustomSyntaxThemeSurvivesTheExistingSettingsRoundTrip(){
        val theme="""{"name":"Quiet custom","type":"dark","colors":{"editor.background":"#20242A","editor.foreground":"#D0D4DA"},"tokenColors":[{"scope":["keyword"],"settings":{"foreground":"#B4A0C0"}}]}"""
        val input=JSONObject(initial.toJson()).put("syntax","CUSTOM").put("customSyntaxTheme",theme)
        val restored=AppSettings.fromJson(input.toString())
        assertEquals("CUSTOM",restored.syntax.name)
        val roundTrip=JSONObject(AppSettings.fromJson(restored.toJson()).toJson())
        val payload=JSONObject(roundTrip.getString("customSyntaxTheme"))
        assertEquals("Quiet custom",payload.getString("name"))
        assertEquals("#20242A",payload.getJSONObject("colors").getString("editor.background"))
        assertEquals(initial.wordWrap,restored.wordWrap);assertEquals(initial.cppBackend,restored.cppBackend)
    }
    @Test fun customSyntaxIsAppendedWithoutChangingTheThreeExistingEnumIdentities(){
        assertEquals(listOf("FOLLOW_UI","DARK","LIGHT","CUSTOM"),SyntaxTheme.values().map{it.name})
    }
}
