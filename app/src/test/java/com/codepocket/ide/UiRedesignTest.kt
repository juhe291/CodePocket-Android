package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import com.codepocket.ide.ui.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h800dp")
class UiRedesignTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList()
    private fun activity()=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
    private fun search(d:AlertDialog,q:String){views(d.window!!.decorView).filterIsInstance<EditText>().first().setText(q)}
    private fun toggle(d:AlertDialog,title:String)=views(d.window!!.decorView).filterIsInstance<Switch>().first{it.contentDescription==title}
    @Test fun terminalToolbarEntryRemainsAvailableWithoutCreatingSessionOrRequestingIme(){
        val a=activity();var opened=0
        val toolbar=MainToolbar(a,DesignTokens.palette(true),{},{},{},{},{},{opened++})
        toolbar.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY))
        toolbar.layout(0,0,320,48)
        val button=views(toolbar).first{it.contentDescription=="打开终端"}
        assertEquals(View.VISIBLE,button.visibility);assertEquals(48,button.measuredWidth)
        button.performClick();button.performClick();assertEquals(2,opened)
    }
    @Test fun collapsedRootAndSelectedRootRoundTripWithoutForcedExpansion(){
        val state=ExplorerUiState(setOf("src"),"",137)
        assertEquals(state,ExplorerUiState.fromJson(state.toJson()))
        assertFalse(ExplorerUiState.fromJson(state.toJson()).expanded.contains(""))
    }
    @Test fun explorerPresentationStateRejectsTraversalAndBoundsPayload(){
        val state=ExplorerUiState(setOf("../bad","/outside","src","src/../x"),"../bad",-3).normalized()
        assertEquals(setOf("src"),state.expanded);assertNull(state.selected);assertEquals(0,state.scroll)
        assertTrue(ExplorerUiState((0..900).map{"a$it"}.toSet()).normalized().expanded.size<=512)
    }
    @Test fun oldSettingsGetFollowingTerminalThemeAndNewThemePersists(){
        assertEquals(TerminalTheme.FOLLOW_UI,AppSettings.fromJson("{\"version\":7}").terminalTheme)
        val next=AppSettings(terminalTheme=TerminalTheme.NORD)
        assertEquals(next,AppSettings.fromJson(next.toJson()))
        assertEquals(TerminalTheme.FOLLOW_UI,AppSettings.fromJson("{\"terminalTheme\":\"unknown\"}").terminalTheme)
    }
    @Test fun settingsSearchFindsHiddenCategoryAndApplySubmitsDraftOnly(){
        val a=activity();var saved:AppSettings?=null
        val d=SettingsDialog.show(a,AppSettings()){saved=it}
        search(d,"光标");toggle(d,"平滑移动代码光标").isChecked=false
        assertNull(saved)
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertFalse(saved!!.cursorMovement);assertFalse(d.isShowing)
    }
    @Test fun settingsDraftSurvivesSearchAndRecreationWithoutWritingPreferences(){
        val a=activity();var writes=0;val initial=AppSettings()
        val first=SettingsDialog.show(a,initial){writes++}
        search(first,"光标");toggle(first,"柔和闪烁代码光标").isChecked=false
        val checkpoint=SettingsDialog.checkpoint(first);first.dismiss()
        val next=SettingsDialog.show(a,initial,{writes++},checkpoint,null)
        assertFalse(toggle(next,"柔和闪烁代码光标").isChecked);assertEquals(0,writes)
        assertEquals("光标",views(next.window!!.decorView).filterIsInstance<EditText>().first().text.toString());next.dismiss()
    }
    @Test fun failedSettingsApplyKeepsDraftAndAllowsRetry(){
        val a=activity();var attempts=0
        val d=SettingsDialog.show(a,AppSettings(),{},null){_,done->attempts++;done(attempts>1)}
        search(d,"光标");toggle(d,"平滑移动代码光标").isChecked=false
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(d.isShowing);assertFalse(toggle(d,"平滑移动代码光标").isChecked)
        assertTrue(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(d.isShowing);assertEquals(2,attempts)
    }
    @Test fun fileTypesUseDistinctLocalIconsAndRestrainedColors(){
        val p=DesignTokens.palette(true)
        assertEquals(IdeIcon.HEADER,IdeIconDrawable.file("a.hpp"));assertEquals(IdeIcon.CONFIG,IdeIconDrawable.file("a.toml"))
        assertNotEquals(IdeIconDrawable.color(IdeIcon.CPP,p),IdeIconDrawable.color(IdeIcon.PYTHON,p))
        assertNotEquals(IdeIconDrawable.color(IdeIcon.FOLDER,p),p.accent)
    }
    @Test fun settingsDraftCancellationNeverWritesPreferences(){
        val a=activity();val prefs=a.getSharedPreferences("codepocket_v2",0);val before=prefs.getString("settings",null)
        val d=SettingsDialog.show(a,AppSettings()){fail("Cancel must not apply")}
        search(d,"光标");toggle(d,"平滑移动代码光标").isChecked=false
        d.dismiss();assertEquals(before,prefs.getString("settings",null))
    }

    @Test fun floatingImeDoesNotCollapsePanelButDockedImeCan(){
        assertFalse(WorkspaceLayoutPolicy.imeOccludesEditor(true,0));assertFalse(WorkspaceLayoutPolicy.imeOccludesEditor(true,48))
        assertTrue(WorkspaceLayoutPolicy.imeOccludesEditor(true,240));assertFalse(WorkspaceLayoutPolicy.imeOccludesEditor(false,300))
    }
    @Test fun settingsSplitUsesActualWidthAndHeightBoundary(){
        assertFalse(WorkspaceLayoutPolicy.settingsSplit(839,800));assertFalse(WorkspaceLayoutPolicy.settingsSplit(1000,479))
        assertTrue(WorkspaceLayoutPolicy.settingsSplit(840,480));assertTrue(WorkspaceLayoutPolicy.settingsSplit(1000,800))
    }
    @Test fun commandMenuFitsNarrowActualWindowAndRetainsCommandIds(){
        val a=activity();val host=android.widget.LinearLayout(a);val anchor=TextView(a).apply{text="menu"}
        host.addView(anchor);a.setContentView(host)
        host.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));host.layout(0,0,240,600)
        a.window.decorView.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));a.window.decorView.layout(0,0,240,600)
        val menu=com.codepocket.ide.animation.DialogMotion.menu(a,anchor,false);var selected=0
        menu.menu.add(0,42,0,"新建文件");menu.setOnMenuItemClickListener{selected=it.itemId;true};menu.show()
        val popup=org.robolectric.util.ReflectionHelpers.getField<android.widget.PopupWindow>(menu,"window")
        assertTrue(popup.width<=240);menu.menu.performIdentifierAction(42,0);assertEquals(42,selected);menu.dismiss()
    }

    @Test fun dismissedSettingsNeverReopenThroughConfigurationCheckpoint(){
        val d=SettingsDialog.show(activity(),AppSettings()){}
        assertNotNull(SettingsDialog.checkpoint(d));d.dismiss();assertNull(SettingsDialog.checkpoint(d))
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletSettingsKeepsSevenCategoriesAndShowsOnlySelectedPage(){
        val d=SettingsDialog.show(activity(),AppSettings()){}
        val labels=views(d.window!!.decorView).filterIsInstance<TextView>().map{it.text.toString()}
        assertTrue(labels.containsAll(listOf("外观","编辑器","补全与快捷键","文件与保存","终端与运行","动画","帮助与许可")))
        assertTrue(labels.contains("界面主题"));assertFalse(labels.contains("代码字号"));d.dismiss()
    }

    @Test fun terminalPaletteMenuExposesAllFiveThemesWithOneSelected(){
        val main=Robolectric.buildActivity(MainActivity::class.java).get();val anchor=TextView(main).apply{text="palette"};main.setContentView(anchor)
        val menu=org.robolectric.util.ReflectionHelpers.callInstanceMethod<android.widget.PopupMenu>(main,"showTerminalPalette",org.robolectric.util.ReflectionHelpers.ClassParameter.from(View::class.java,anchor))
        assertEquals(5,menu.menu.size());assertEquals(1,(0 until menu.menu.size()).count{menu.menu.getItem(it).isChecked});menu.dismiss()
    }
}
