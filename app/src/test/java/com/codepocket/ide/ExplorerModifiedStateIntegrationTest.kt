package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.ExplorerRow
import com.codepocket.ide.ui.IdeControls
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
import java.util.concurrent.TimeUnit

/** Real Main/Sora input and disk conflict/retry; tree decorations must preserve rows and hit targets. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class ExplorerModifiedStateIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Actual Sora layout unavailable")}
    private fun layout(){repeat(2){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun row(list:LinearLayout,path:String):ExplorerRow=(0 until list.childCount).map{list.getChildAt(it)}.single{it.getTag(R.id.workbench_key)==if(path.isEmpty())"root" else "entry:$path"} as ExplorerRow
    private fun marked(view:ExplorerRow,expected:Boolean){assertEquals("Tree must describe the current unsaved state",expected,view.contentDescription.toString().contains("未保存"))}
    private fun geometry(v:LinearLayout)=(0 until v.childCount).map{val c=v.getChildAt(it);listOf(c.left,c.top,c.width,c.height,c.visibility)}
    private fun bitmap(v:View)=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888).also{v.draw(Canvas(it))}
    private fun evidence():File{val cwd=File(System.getProperty("user.dir")!!);return File(if(cwd.name=="app")cwd.parentFile else cwd,"verification/workbench-explorer-state-2026-10-26").apply{mkdirs()}}
    private fun capture(tag:String,v:View){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(b);val backing=generateSequence(v){it.parent as? View}.mapNotNull{it.background as? android.graphics.drawable.ColorDrawable}.firstOrNull();assertNotNull("Capture must use its actual inherited surface",backing);canvas.drawColor(backing!!.color);v.draw(canvas);File(evidence(),"previews").apply{mkdirs()}.resolve("$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private fun input(value:String){editor.setSelection(editor.length());assertTrue(editor.onCreateInputConnection(EditorInfo()).commitText(value,1));idle();ready();layout()}
    private fun save(){main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));drain();ready();layout()}
    private fun open(path:String){ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready();layout()}
    private fun drawer():LinearLayout{ReflectionHelpers.callInstanceMethod<Unit>(main,"showExplorer");idle();return field<LinearLayout?>("overlayExplorerList")!!}
    private fun closeDrawer(){field<AlertDialog?>("explorerDialog")?.dismiss();idle();editor.requestFocus();layout()}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()
    }
    @After fun close(){if(::main.isInitialized){field<AlertDialog?>("explorerDialog")?.dismiss();main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletTypingAndSavingKeepTreeGeometryAndOnlyDrawOneSmallMarker(){
        repeat(24){workspace.create(workspace.root,"z_fixture_$it.txt",false)}
        ReflectionHelpers.callInstanceMethod<Unit>(main,"renderExplorer",ClassParameter.from(Boolean::class.javaPrimitiveType!!,false));idle();layout()
        val list=field<LinearLayout?>("sidebarExplorerList")!!;val scroll=list.parent as android.widget.ScrollView
        scroll.scrollTo(0,IdeControls.dp(main,24));idle();val scrollBefore=scroll.scrollY;assertTrue(scrollBefore>0)
        val selected=row(list,"main.py");val before=geometry(selected)
        val nodes=(0 until list.childCount).map{list.getChildAt(it)};val buffer=editor.text;val original=buffer.toString()
        marked(selected,false);val clean=bitmap(selected);capture("tablet-clean-tree",list.parent as View)
        input("# explorer edit\n");marked(selected,true);capture("tablet-dirty-tree",list.parent as View)
        assertSame(selected,row(list,"main.py"));assertEquals(before,geometry(selected));assertEquals(scrollBefore,scroll.scrollY);nodes.forEachIndexed{i,v->assertSame(v,list.getChildAt(i))}
        assertSame(buffer,editor.text);assertTrue(editor.hasFocus());assertEquals("main.py",(selected.getChildAt(1) as TextView).text.toString())
        marked(row(list,""),false);marked(row(list,"main.cpp"),false)
        val dirty=bitmap(selected);val changes=mutableListOf<Pair<Int,Int>>()
        for(y in 0 until clean.height)for(x in 0 until clean.width)if(clean.getPixel(x,y)!=dirty.getPixel(x,y))changes.add(x to y)
        assertTrue("Dirty state must draw a visible marker",changes.isNotEmpty())
        val max=IdeControls.dp(main,8);assertTrue(changes.maxOf{it.first}-changes.minOf{it.first}<max);assertTrue(changes.maxOf{it.second}-changes.minOf{it.second}<max)
        File(evidence(),"marker-measurement.json").writeText(org.json.JSONObject().put("changedPixels",changes.size).put("widthPx",changes.maxOf{it.first}-changes.minOf{it.first}+1).put("heightPx",changes.maxOf{it.second}-changes.minOf{it.second}+1).put("rowHeightPx",selected.height).put("scrollBefore",scrollBefore).put("scrollAfter",scroll.scrollY).toString())
        clean.recycle();dirty.recycle();save();marked(selected,false);capture("tablet-saved-tree",list.parent as View)
        assertSame(selected,row(list,"main.py"));assertEquals(before,geometry(selected));assertEquals(scrollBefore,scroll.scrollY);assertSame(buffer,editor.text);assertFalse(field("dirty"));assertTrue(editor.hasFocus())
        assertEquals(original+"# explorer edit\n",workspace.read(workspace.resolve("main.py")))
        assertEquals("main.py",selected.contentDescription.toString());assertTrue(selected.getChildAt(2).width>=48);assertTrue(selected.getChildAt(2).height>=48)
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLightLargeReopenedAndRecollapsedTreeKeepsLongFileState(){
        val src=workspace.create(workspace.root,"src",true);val deep=workspace.create(src,"deep",true)
        val file=workspace.create(deep,"学习笔记_".repeat(5)+".py",false);workspace.write(file,"value = 1\n");val path=workspace.path(file)
        field<MutableSet<String>>("expanded").addAll(listOf("src","src/deep"));open(path);val buffer=editor.text
        input("# preserve tree state\n");var list=drawer();var current=row(list,path);marked(current,true);capture("phone-light-large-dirty-tree",list.parent as View)
        assertTrue(current.contentDescription.toString().contains(path));assertTrue(current.height>=IdeControls.dp(main,48))
        row(list,"").performClick();idle();assertEquals(1,list.childCount);assertSame(buffer,editor.text);assertEquals(path,field<String>("activePath"));assertTrue(field("dirty"))
        row(list,"").performClick();idle();marked(row(list,path),true)
        row(list,"src/deep").performClick();idle();assertFalse((0 until list.childCount).any{list.getChildAt(it).getTag(R.id.workbench_key)=="entry:$path"})
        row(list,"src/deep").performClick();idle();current=row(list,path);marked(current,true);marked(row(list,"src/deep"),false)
        current.performClick();idle();assertSame(buffer,editor.text);assertTrue(field("dirty"));list=drawer();current=row(list,path)
        marked(current,true);assertTrue(current.getChildAt(2).width>=IdeControls.dp(main,48));assertTrue(current.getChildAt(2).height>=IdeControls.dp(main,48));capture("phone-light-large-restored-tree",list.parent as View)
        closeDrawer();save();list=drawer();marked(row(list,path),false);capture("phone-light-large-saved-tree",list.parent as View)
        assertSame(buffer,editor.text);assertEquals(path,field<String>("activePath"));assertEquals(editor.text.toString(),workspace.read(file));assertFalse(field("dirty"))
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightSaveConflictKeepsMarkerAndRealRetryClearsIt(){
        val list=field<LinearLayout?>("sidebarExplorerList")!!;val current=row(list,"main.py");val buffer=editor.text;val original=buffer.toString()
        input("# conflict retained\n");marked(current,true);val text=editor.text.toString();workspace.resolve("main.py").writeText("# external writer\n");save()
        assertTrue(field("dirty"));marked(current,true);assertSame(current,row(list,"main.py"));assertSame(buffer,editor.text);assertEquals(text,buffer.toString())
        assertEquals("# external writer\n",workspace.read(workspace.resolve("main.py")))
        assertEquals(text,field<DraftStore>("drafts").read(field<EditorSessions>("sessions").key("main.py"))!!.text);capture("tablet-light-conflict-tree",list.parent as View)
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()?.takeIf{it.isShowing}?.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick();idle();editor.requestFocus()
        workspace.resolve("main.py").writeText(original);save();marked(current,false);assertFalse(field("dirty"));assertEquals(text,workspace.read(workspace.resolve("main.py")))
        assertSame(buffer,editor.text);assertTrue(editor.hasFocus());capture("tablet-light-recovered-tree",list.parent as View)
    }
}
