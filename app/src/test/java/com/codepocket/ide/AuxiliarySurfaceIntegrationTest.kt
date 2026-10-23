package com.codepocket.ide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Native Main/Sora view hierarchy and drawings. No Android Python/PTY execution is claimed. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class AuxiliarySurfaceIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun invoke(method:String)=ReflectionHelpers.callInstanceMethod<Unit>(main,method)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun layout(){repeat(3){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun point(v:View)=IntArray(2).also{v.getLocationOnScreen(it)}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))1.7f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.cpp").putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,
                animations=false,autoSave=false,panelPreferSide=true).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus()
        repeat(200){idle();if(editor.isLayoutReady){awaitSyntax();return};Thread.sleep(5)};fail("Main Sora layout unavailable")
    }
    private fun awaitSyntax(){
        ReflectionHelpers.getField<java.util.concurrent.ExecutorService>(editor,"syntaxLoader").submit{}.get(20,TimeUnit.SECONDS)
        repeat(200){
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            if(editor.syntaxState().contains("TextMate"))return
            Thread.sleep(5)
        }
        fail("Actual TextMate theme did not become ready: ${editor.syntaxState()}")
    }
    @After fun close(){main.finish();controller.pause().stop().destroy();drain();RuntimeEnvironment.setFontScale(1f)}
    private fun assertHierarchy(sidebar:Boolean){
        val rail=field<View>("activityBar");val top=field<ViewGroup>("topWorkspace");val split=field<LinearLayout>("splitHost")
        val panel=field<View>("outputRegion");val footer=field<View>("statusBarComponent");val region=field<View>("editorRegion")
        assertEquals(View.VISIBLE,rail.visibility);assertSame(top,region.parent);assertSame(split,top.parent);assertSame(split,panel.parent)
        assertEquals(LinearLayout.VERTICAL,split.orientation);assertEquals(PanelDock.BOTTOM,field<PanelLayout>("panelLayout").dock)
        assertEquals(point(rail)[0]+rail.width,point(split)[0]);assertEquals(point(rail)[1],point(split)[1])
        assertEquals(point(rail)[1]+rail.height,point(footer)[1]+footer.height)
        assertEquals(point(split)[0],point(footer)[0]);assertTrue(editor.width>=IdeControls.dp(main,280))
        if(panel.visibility==View.VISIBLE){assertEquals(top.width,panel.width);assertEquals(point(top)[0],point(panel)[0]);assertTrue(point(top)[1]+top.height<=point(panel)[1])}
        val pane=field<View?>("explorerPane")
        assertEquals(sidebar,pane?.visibility==View.VISIBLE)
        if(sidebar){assertSame(top,pane!!.parent);assertEquals(top.height,pane.height);if(panel.visibility==View.VISIBLE)assertTrue(point(pane)[1]+pane.height<=point(panel)[1])}
        assertEquals(View.GONE,field<View>("auxiliarySlot").visibility)
    }
    private fun capture(tag:String,target:View?=null){
        layout();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));val v=target?:main.findViewById<View>(android.R.id.content)
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd
        val folder=File(repo,"verification/workbench-heading-polish-2026-10-24/previews").apply{mkdirs()}
        File(folder,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    @Test @Config(qualifiers="w840dp-h411dp") fun phoneLandscapeLightWorkbench(){
        assertHierarchy(false)
        val native=editor.inputView();val scheme=native.javaClass.getMethod("getColorScheme").invoke(native)
        val base=Class.forName("io.github.rosemoe.sora.widget.schemes.EditorColorScheme")
        assertEquals(0xff7e4e94.toInt(),base.getMethod("getColor",Int::class.javaPrimitiveType).invoke(scheme,base.getField("KEYWORD").getInt(null)))
        assertEquals(0xff536277.toInt(),base.getMethod("getColor",Int::class.javaPrimitiveType).invoke(scheme,base.getField("OPERATOR").getInt(null)))
        capture("phone-landscape-light-workbench")
    }
    @Test @Config(qualifiers="w800dp-h1200dp") fun tabletPortraitDarkWorkbench(){
        assertHierarchy(true);capture("tablet-portrait-dark-workbench")
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneLargeWorkbenchKeepsEditorSpace(){
        assertHierarchy(false);capture("phone-large-workbench")
    }
    private fun latest()=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletSearchRoutesRemainDistinctWithNoRepeatedTopEntry(){
        val content=editor.text
        val top=views(field<View>("mainToolbar"))
        assertFalse(top.any{it.contentDescription?.toString()?.contains("项目搜索")==true})
        assertEquals(3,top.filterIsInstance<android.widget.ImageView>().size)
        views(field<View>("activityBar")).single{it.contentDescription=="项目搜索"}.performClick();drain()
        val project=field<android.app.AlertDialog>("toolDialog")
        assertTrue(views(project.window!!.decorView).filterIsInstance<android.widget.TextView>().any{it.text.toString()=="项目搜索"})
        assertEquals(1,views(project.window!!.decorView).filterIsInstance<android.widget.EditText>().size)
        capture("tablet-project-search-from-rail",project.window!!.decorView);project.dismiss();idle();editor.requestFocus()
        views(field<View>("editorToolbar")).single{it.contentDescription==IdeCommand.FIND.title}.performClick();idle()
        val current=field<android.app.AlertDialog>("toolDialog")
        assertTrue(views(current.window!!.decorView).any{it.contentDescription=="查找文本"})
        assertTrue(views(current.window!!.decorView).filterIsInstance<android.widget.TextView>().any{it.text.toString()==main.getString(R.string.search_current_file,"main.cpp")})
        assertSame(content,editor.text);capture("tablet-current-file-search-from-editor",current.window!!.decorView)
        current.dismiss()
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightWorkspaceKeepsRealChoicesAndPermissionCancel(){
        val content=editor.text;invoke("workspaceMenu");val picker=latest()
        assertTrue(views(picker.window!!.decorView).filterIsInstance<android.widget.TextView>().any{it.text.toString()=="工作区"})
        assertEquals(3,picker.listView.adapter.count);capture("tablet-light-workspace",picker.window!!.decorView)
        picker.listView.performItemClick(picker.listView.adapter.getView(0,null,picker.listView),0,0);idle()
        val permission=latest();assertTrue(permission.isShowing);permission.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        assertSame(content,editor.text)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneDraftPickerExportsExactSnapshotWithoutReplacingEditor(){
        val content=editor.text;val value="real draft snapshot\n";field<DraftStore>("drafts").put("visual-draft","src/solution.py",value)
        invoke("recoverDrafts");drain();val d=latest();assertEquals(1,d.listView.adapter.count)
        capture("phone-draft-picker",d.window!!.decorView)
        d.listView.performItemClick(d.listView.adapter.getView(0,null,d.listView),0,0);drain()
        assertEquals(value,java.io.File(main.filesDir,"pending-export.txt").readText())
        assertEquals(value,field<String>("exportContent"));assertSame(content,editor.text)
        val launched=Shadows.shadowOf(main).nextStartedActivityForResult;assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT,launched.intent.action)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneFontPreviewUsesActualSizeAndKeepsUndoBuffer(){
        val content=editor.text;val before=field<AppSettings>("settings").fontSize
        invoke("showCodeFontSize");val d=latest();views(d.window!!.decorView).single{it.contentDescription==main.getString(com.codepocket.ide.R.string.editor_font_larger)}.performClick();idle()
        assertEquals(before+1,field<AppSettings>("settings").fontSize);assertSame(content,editor.text)
        val preview=views(d.window!!.decorView).filterIsInstance<android.widget.TextView>().single{it.contentDescription=="代码字号预览"}
        assertEquals((before+1)*main.resources.displayMetrics.scaledDensity,preview.textSize,.01f)
        capture("phone-font-preview",d.window!!.decorView);d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
    }
    @Test @Config(qualifiers="w800dp-h1200dp") fun tabletLightLicenseDetailsRemainCompleteSelectableAndScrollable(){
        val license=main.assets.open("SORA-NOTICE.txt").bufferedReader().use{it.readText()}
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showReadOnlyDetails",ClassParameter.from(String::class.java,"Sora 编辑器与语法许可"),ClassParameter.from(String::class.java,license));val d=latest()
        val text=views(d.window!!.decorView).filterIsInstance<android.widget.TextView>().single{it.text.toString()==license}
        assertTrue(text.isTextSelectable);assertTrue(text.parent is android.widget.ScrollView)
        capture("tablet-light-license-details",d.window!!.decorView);d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();idle();assertFalse(d.isShowing)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun explicitDiagnosticDetailsKeepCopyAndFullMessage(){
        val message="界面测试说明，未执行程序。\n".repeat(30)
        val issue=com.codepocket.ide.editor.InlineIssue(0,1,message,"UI fixture")
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showDiagnosticDetails",ClassParameter.from(com.codepocket.ide.editor.InlineIssue::class.java,issue));val d=latest()
        assertTrue(views(d.window!!.decorView).filterIsInstance<android.widget.TextView>().any{it.text.contains(message)})
        capture("phone-explicit-diagnostic-details",d.window!!.decorView)
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();idle()
        assertEquals(message,main.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
    }
}
