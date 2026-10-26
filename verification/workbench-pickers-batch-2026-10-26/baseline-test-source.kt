package com.codepocket.ide

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual Main/Sora pickers and filesystem snapshots, with Android provider UI deliberately deferred. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class WorkspacePickerIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Actual Sora layout unavailable")}
    private fun layout(){repeat(2){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun invoke(method:String){ReflectionHelpers.callInstanceMethod<Unit>(main,method);idle()}
    private fun latest()=ShadowAlertDialog.getLatestAlertDialog()
    private fun move(file:File):AlertDialog {ReflectionHelpers.callInstanceMethod<Unit>(main,"askMove",ClassParameter.from(File::class.java,file));idle();return latest()}
    private fun row(list:ListView,index:Int)=list.adapter.getView(index,null,list)
    private fun texts(v:View)=views(v).filterIsInstance<TextView>().map{it.text.toString()}
    private fun picker(d:AlertDialog){assertEquals("Use explicit two-level choice metadata", "WorkbenchChoiceAdapter",d.listView.adapter.javaClass.simpleName)}
    private fun capture(tag:String,v:View){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(DesignTokens.forContext(main).overlay);v.draw(c)
        val cwd=File(System.getProperty("user.dir")!!);val root=if(cwd.name=="app")cwd.parentFile else cwd;val dir=File(root,"verification/workbench-pickers-batch-2026-10-26/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private fun edit(text:String){editor.setSelection(editor.length());assertTrue(editor.onCreateInputConnection(EditorInfo()).commitText(text,1));idle();ready();layout()}
    private fun enter(d:AlertDialog,name:String){val label=views(d.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()=="$name/"};assertTrue((label.parent as View).performClick());idle()}
    @Before fun launch(){RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.cpp")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()}
    @After fun close(){if(::main.isInitialized){latest()?.takeIf{it.isShowing}?.dismiss();main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}

    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletWorkspaceShowsCurrentStorageAndCancelKeepsTheEditor(){
        val buffer=editor.text;val origin=workspace;invoke("workspaceMenu");val d=latest();picker(d);assertEquals(3,d.listView.adapter.count)
        val selected=row(d.listView,1);assertTrue(selected.isSelected);assertTrue(texts(selected).contains("内部项目"));assertTrue(texts(selected).any{it.contains("卸载会清除")})
        assertFalse(row(d.listView,0).isSelected);capture("tablet-workspace-current",d.window!!.decorView)
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertFalse(d.isShowing);assertSame(origin,workspace);assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w320dp-h800dp") fun phoneLightLargeRecentChoicesKeepNamesSeparateAndPermissionCancelIsSafe(){
        val recent=JSONArray().put(JSONObject().put("name","算法练习_"+"长目录".repeat(6)).put("uri","content://test.documents/tree/one"))
            .put(JSONObject().put("name","Python课程").put("uri","content://test.documents/tree/two"))
        main.getSharedPreferences("codepocket_v2",0).edit().putString("recentTrees",recent.toString()).commit()
        val buffer=editor.text;val origin=workspace;invoke("workspaceMenu");val d=latest();picker(d);assertEquals(5,d.listView.adapter.count)
        val r=row(d.listView,3);assertTrue(texts(r).contains(recent.getJSONObject(0).getString("name")));assertTrue(texts(r).any{it.contains("最近")})
        assertTrue(r.contentDescription.toString().contains(recent.getJSONObject(0).getString("name")))
        capture("phone-light-large-workspaces",d.window!!.decorView)
        d.listView.performItemClick(row(d.listView,0),0,0);idle();val permission=latest();assertTrue(permission.isShowing)
        permission.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertSame(origin,workspace);assertSame(buffer,editor.text);assertNull(field<String?>("selectedTree"))
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightDraftsDistinguishSameNamesAndExportTheChosenSnapshot(){
        val drafts=field<DraftStore>("drafts");drafts.put("chapter-one","chapter1/solution.py","print('first')\n");drafts.put("chapter-two","chapter2/solution.py","print('second')\n")
        val buffer=editor.text;invoke("recoverDrafts");drain();val d=latest();picker(d);assertEquals(2,d.listView.adapter.count)
        val index=(0 until d.listView.adapter.count).single{texts(row(d.listView,it)).contains("chapter2")}
        assertTrue(texts(row(d.listView,index)).contains("solution.py"));capture("tablet-light-drafts",d.window!!.decorView)
        d.listView.performItemClick(row(d.listView,index),index,index.toLong());drain()
        assertEquals("print('second')\n",File(main.filesDir,"pending-export.txt").readText());assertEquals("chapter2/solution.py",field<String>("exportPath"));assertSame(buffer,editor.text)
        val intent=Shadows.shadowOf(main).nextStartedActivityForResult.intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.action);assertEquals("solution.py",intent.getStringExtra(Intent.EXTRA_TITLE));assertNotNull(drafts.read("chapter-two"))
    }
    @Test @Config(qualifiers="w320dp-h640dp") fun phoneLightLargeDraftChoicesKeepFullPathsAndCancelPreservesRecovery(){
        val drafts=field<DraftStore>("drafts");repeat(10){drafts.put("choice-$it","课程目录_".repeat(3)+"/章节$it/solution.cpp","// snapshot $it")}
        val buffer=editor.text;invoke("recoverDrafts");drain();val d=latest();picker(d);assertEquals(10,d.listView.adapter.count)
        val first=row(d.listView,0);assertTrue(first.contentDescription.toString().contains("/章节"));assertTrue(texts(first).contains("solution.cpp"))
        d.listView.setSelection(7);idle();capture("phone-light-large-drafts",d.window!!.decorView)
        assertTrue(d.getButton(AlertDialog.BUTTON_NEGATIVE).height>=48);d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertSame(buffer,editor.text);assertEquals(10,drafts.list("").size)
    }
    @Test @Config(qualifiers="w360dp-h640dp") fun phoneLightLargeMoveShowsTheFullTargetAndPreservesTheSavedBuffer(){
        val parentName="目标目录_".repeat(4);val childName="算法练习_".repeat(4);val parent=workspace.create(workspace.root,parentName,true);val dest=workspace.create(parent,childName,true)
        edit("\n// preserve dirty code\n");val buffer=editor.text;val text=buffer.toString();val id=field<EditorSessions>("sessions").file("main.cpp").id
        val d=move(workspace.resolve("main.cpp"));enter(d,parentName);enter(d,childName)
        val target=views(d.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString().startsWith("目标：")}
        assertEquals(main.getString(R.string.move_target_path,workspace.path(dest)),target.text.toString())
        assertTrue("Target path must grow with its wrapped text",target.height>=target.lineCount*target.lineHeight+target.compoundPaddingTop+target.compoundPaddingBottom)
        assertTrue("Whole move body must remain scrollable",views(d.window!!.decorView).filterIsInstance<ScrollView>().size>=2)
        capture("phone-light-large-move-deep-target",d.window!!.decorView);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();ready();layout()
        val path=workspace.path(dest)+"/main.cpp";assertEquals(path,field<String>("activePath"));assertSame(buffer,editor.text);assertEquals(id,field<EditorSessions>("sessions").file(path).id);assertEquals(text,workspace.read(workspace.resolve(path)));assertFalse(workspace.resolve("main.cpp").exists());assertFalse(field("dirty"))
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletMoveEmptyTargetsDoNotReserveABlankPanelOrAllowTheSource(){
        val source=workspace.create(workspace.root,"source",true);val child=workspace.create(source,"nested",true);val buffer=editor.text
        val d=move(source);val all=views(d.window!!.decorView);assertFalse(all.filterIsInstance<TextView>().any{it.text.toString()=="source/"})
        val empty=all.filterIsInstance<TextView>().single{it.text.toString()=="此目录没有可选子文件夹"};val viewport=(empty.parent as View).parent as ScrollView
        assertTrue("An empty target directory should not reserve 240dp",viewport.height<96)
        capture("tablet-move-empty",d.window!!.decorView);d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertTrue(source.isDirectory);assertTrue(child.isDirectory);assertSame(buffer,editor.text)
    }
}
