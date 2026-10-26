package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual Main/Sora and settings draft transactions; host drawings are not device validation. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class SettingsChromeIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun words(v:View)=views(v).filterIsInstance<TextView>().map{it.text.toString()}
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real Sora layout unavailable")}
    private fun layout(){repeat(2){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun settings():AlertDialog{ReflectionHelpers.callInstanceMethod<Unit>(main,"showSettings");idle();return field("toolDialog")}
    private fun search(d:AlertDialog,q:String){views(d.window!!.decorView).filterIsInstance<EditText>().single{it.contentDescription=="搜索设置"}.setText(q);idle()}
    private fun category(d:AlertDialog,title:String){views(d.window!!.decorView).single{it.contentDescription==title}.performClick();idle()}
    private fun toggle(d:AlertDialog,title:String)=views(d.window!!.decorView).filterIsInstance<Switch>().single{it.contentDescription==title}
    private fun choose(d:AlertDialog,title:String):AlertDialog{val label=views(d.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()==title};(label.parent.parent as View).performClick();idle();return ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing)}}
    private fun capture(tag:String,v:View){idle();assertTrue(v.width>0&&v.height>0);val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(DesignTokens.forContext(main).overlay);v.draw(c)
        val cwd=File(System.getProperty("user.dir")!!);val r=if(cwd.name=="app")cwd.parentFile else cwd;val dir=File(r,"verification/workbench-settings-chrome-batch-2026-10-26/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    @Before fun launch(){RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();assertTrue(editor.requestFocus());ready()}
    @After fun close(){if(::main.isInitialized){ShadowAlertDialog.getLatestAlertDialog()?.takeIf{it.isShowing}?.dismiss();main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}

    @Test @Config(qualifiers="w411dp-h891dp") fun phoneProjectHeadingOpensTheRealWorkspacePickerWithoutChangingTheEditor(){
        val workspace=field<Workspace>("workspace");val title=workspace.title.substringBefore("（").substringBefore(" · ")
        val bar=field<MainToolbar>("mainToolbar");val heading=views(bar).filterIsInstance<TextView>().single{it.text.toString()==title}
        assertTrue(heading.width>=96);assertTrue(heading.height>=48);assertNotNull(heading.compoundDrawables[0]);assertNotNull(heading.compoundDrawables[2])
        val content=editor.text;val source=content.toString();val path=field<String>("activePath");heading.performClick();idle();val d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing)
        assertTrue(words(d.window!!.decorView).contains("工作区"));capture("phone-workspace-heading",main.findViewById(android.R.id.content))
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();drain();assertSame(content,editor.text);assertEquals(source,editor.text.toString());assertEquals(path,field<String>("activePath"))
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLightLargeLongProjectHeadingKeepsSingleLineAndFullPath(){
        val bar=field<MainToolbar>("mainToolbar");val full="Projects / 一个很长的练习项目名称 / src / main.py";val title="一个很长的练习项目名称"
        bar.setTitle(title,full);layout();val heading=views(bar).filterIsInstance<TextView>().single{it.contentDescription==full}
        assertEquals(title,heading.text.toString());assertEquals(1,heading.lineCount);assertEquals(full,heading.tooltipText.toString());assertTrue(heading.width>0);assertEquals(android.text.TextUtils.TruncateAt.MIDDLE,heading.ellipsize)
        assertTrue(heading.right<=bar.width);capture("phone-light-large-project",main.findViewById(android.R.id.content))
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletHasOneVisibleRunEntryAndPreservesTheRealRunMenu(){
        val rail=field<View>("activityBar");assertFalse(views(rail).any{it.contentDescription?.toString()?.contains("F5")==true})
        val bar=field<MainToolbar>("mainToolbar");assertEquals(1,views(bar).count{it.isShown&&it.contentDescription=="运行 · F5"})
        val buffer=editor.text;views(bar).single{it.contentDescription=="运行菜单"}.performClick();idle();val popup=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow!!
        assertTrue(words(popup.contentView).any{it.contains("运行")});assertSame(buffer,editor.text);popup.dismiss();capture("tablet-workbench-actions",main.findViewById(android.R.id.content))
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightEditorGroupsAndCancelKeepTheUnappliedDraft(){
        val original=field<AppSettings>("settings");val buffer=editor.text;val panel=field<PanelState>("panelState");val d=settings();category(d,"编辑器")
        val labels=words(d.window!!.decorView);assertTrue(labels.contains("显示与配色"));assertTrue(labels.contains("缩进与输入"));assertTrue(labels.indexOf("自动换行")<labels.indexOf("缩进与输入"))
        capture("tablet-light-editor-groups",d.window!!.decorView);toggle(d,"自动换行").isChecked=!original.wordWrap
        assertEquals(original,field<AppSettings>("settings"));category(d,"补全与快捷键")
        assertTrue(words(d.window!!.decorView).containsAll(listOf("候选与参考","补全按键","触屏输入")))
        val help=main.getString(R.string.completion_key_help);assertFalse(words(d.window!!.decorView).contains(help))
        views(d.window!!.decorView).single{it.contentDescription=="补全按键说明"}.performClick();idle();val explanation=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(words(explanation.window!!.decorView).contains(help));capture("tablet-light-key-help",explanation.window!!.decorView)
        explanation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(d.isShowing)
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain()
        assertEquals(original,field<AppSettings>("settings"));assertSame(buffer,editor.text);assertEquals(panel,field<PanelState>("panelState"))
    }
    @Test @Config(qualifiers="w411dp-h1000dp") fun phoneLightLargeFontChoiceRemainsDraftUntilActualApply(){
        val original=field<AppSettings>("settings");val buffer=editor.text;editor.setSelection(2);val d=settings();category(d,"编辑器");var picker=choose(d,"代码字号")
        assertTrue(words(picker.window!!.decorView).contains("编辑器 · 显示与配色"));assertEquals(original.fontSize-10,picker.listView.checkedItemPosition)
        val selected=picker.listView.adapter.getView(original.fontSize-10,null,picker.listView);assertTrue(selected.contentDescription.toString().contains("已选择"));capture("phone-light-large-font-choice",picker.window!!.decorView)
        picker.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(original.toJson(),org.json.JSONObject(SettingsDialog.checkpoint(d)!!).getString("draft"))
        picker=choose(d,"代码字号");val index=original.fontSize-9;picker.listView.performItemClick(picker.listView.adapter.getView(index,null,picker.listView),index,index.toLong());idle()
        assertEquals(original,field<AppSettings>("settings"));assertEquals(original.fontSize+1,AppSettings.fromJson(org.json.JSONObject(SettingsDialog.checkpoint(d)!!).getString("draft")).fontSize)
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();ready();assertFalse(d.isShowing);assertEquals(original.fontSize+1,field<AppSettings>("settings").fontSize);assertSame(buffer,editor.text);assertEquals(2,editor.selectionStart)
    }
    @Test @Config(qualifiers="w411dp-h1000dp") fun phoneSearchGroupsOnceAndRetainsCursorDraftAcrossMainRecreation(){
        val original=field<AppSettings>("settings");val d=settings();search(d,"光标");assertEquals(1,words(d.window!!.decorView).count{it=="动画 · 代码光标"})
        toggle(d,"平滑移动代码光标").isChecked=!original.cursorMovement;val source=editor.text.toString();controller.recreate();main=controller.get();drain();layout();ready();val restored=field<AlertDialog>("toolDialog")
        assertEquals("光标",views(restored.window!!.decorView).filterIsInstance<EditText>().single().text.toString());assertEquals(!original.cursorMovement,toggle(restored,"平滑移动代码光标").isChecked);assertEquals(original,field<AppSettings>("settings"));assertEquals(source,editor.text.toString())
        capture("phone-cursor-search-groups",restored.window!!.decorView);restored.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();assertEquals(original,field<AppSettings>("settings"))
    }
}
