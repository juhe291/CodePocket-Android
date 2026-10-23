package com.codepocket.ide

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.editor.SoraEditorPort
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.TimeUnit

/** Production dialogs, main Sora buffer, actual workspace files and async IO; no replacement UI/provider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainFileSettingsIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private val sessions get()=field<EditorSessions>("sessions")
    private val port get()=field<SoraEditorPort>("editorPort")
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    @Before fun launch() {
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py").putString("settings",AppSettings(autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get()
        drain();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()
    }
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle()}}
    private fun ready(){repeat(100){Shadows.shadowOf(Looper.getMainLooper()).idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Sora layout did not publish")}
    private fun layout(){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun invokeFile(name:String,file:File){ReflectionHelpers.callInstanceMethod<Unit>(main,name,ClassParameter.from(File::class.java,file))}
    private fun switch(path:String){ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready()}
    private fun create(path:String,text:String):File {var parent=workspace.root;val names=path.split('/');for(n in names.dropLast(1)){val f=File(parent,n);parent=if(f.exists())f else workspace.create(parent,n,true)};return workspace.create(parent,names.last(),false).also{workspace.write(it,text)}}
    private fun edit(text:String){editor.setText(text);ready();editor.setSelection(text.length)}
    private fun latest()=ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing)}
    private fun rename(path:String,name:String):AlertDialog {invokeFile("askRename",workspace.resolve(path));val d=latest();views(d.window!!.decorView).filterIsInstance<EditText>().single().setText(name);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();ready();return d}
    private fun settings():AlertDialog {ReflectionHelpers.callInstanceMethod<Unit>(main,"showSettings");return field("toolDialog")}
    private fun search(d:AlertDialog,q:String){views(d.window!!.decorView).filterIsInstance<EditText>().single{it.contentDescription=="搜索设置"}.setText(q)}
    private fun setting(d:AlertDialog,title:String)=views(d.window!!.decorView).filterIsInstance<Switch>().single{it.contentDescription==title}
    private fun menuText(d:AlertDialog,text:String)=views(d.window!!.decorView).filterIsInstance<TextView>().single{it !is EditText && it.text.toString()==text}
    @After fun close(){if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()}}

    @Test fun dirtyActiveRenameKeepsStableIdentitySelectionUndoAndContinuedSaving(){
        edit("value = 1");val ic=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo());assertTrue(ic.commitText("2",1))
        val content=editor.text;val id=sessions.file("main.py").id;editor.setSelection(1,4)
        val dialog=rename("main.py","solver.py")
        assertFalse(dialog.isShowing);assertFalse(workspace.resolve("main.py").exists());assertEquals("value = 12",workspace.resolve("solver.py").readText())
        assertEquals("solver.py",field<String>("activePath"));assertEquals("solver.py",field<String>("loadedPath"));assertEquals(id,sessions.file("solver.py").id)
        assertSame(content,editor.text);assertEquals(1,editor.selectionStart);assertEquals(4,editor.selectionEnd)
        assertTrue(editor.history(false));assertEquals("value = 1",editor.text.toString())
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"saveActive",ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain()
        assertEquals("value = 1",workspace.resolve("solver.py").readText());assertFalse(field("dirty"))
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun renamedDirectoryRebasesAllTabsInputsSelectionAndExplorerState(){
        create("src/deep/a.py","print('a')");create("src/b.cpp","int main() {}")
        switch("src/b.cpp");field<EditText>("input").setText("cpp input");val bid=sessions.file("src/b.cpp").id
        switch("src/deep/a.py");editor.setSelection(2);val aid=sessions.file("src/deep/a.py").id
        ReflectionHelpers.setField(main,"currentDirectory","src/deep");ReflectionHelpers.setField(main,"explorerSelection","src/deep/a.py")
        field<MutableSet<String>>("expanded").addAll(listOf("","src","src/deep"))
        rename("src","lib")
        assertEquals("lib/deep/a.py",field<String>("activePath"));assertEquals(2,editor.selectionStart)
        assertEquals("lib/deep",field<String>("currentDirectory"));assertEquals("lib/deep/a.py",field<String>("explorerSelection"))
        assertTrue(field<Set<String>>("expanded").containsAll(listOf("","lib","lib/deep")))
        assertFalse(field<List<String>>("opened").any{it.startsWith("src/")})
        assertEquals(aid,sessions.file("lib/deep/a.py").id);assertEquals(bid,sessions.file("lib/b.cpp").id)
        switch("lib/b.cpp");assertEquals("cpp input",field<EditText>("input").text.toString())
        val saved=main.getSharedPreferences("codepocket_v2",0).getString("sessions:${workspace.identity}",null)!!
        assertEquals(bid,EditorSessions(workspace.identity,saved).file("lib/b.cpp").id)
    }
    @Test fun actualMovePickerMovesOpenDirectoryAndKeepsItsBuffer(){
        create("src/task.py","print('move')");workspace.create(workspace.root,"dest",true);switch("src/task.py")
        val content=editor.text;val id=sessions.file("src/task.py").id
        invokeFile("askMove",workspace.resolve("src"));val d=latest()
        // Native touch dispatch belongs to the clickable menu row, not its label child.
        (menuText(d,"dest/").parent as View).performClick()
        assertTrue(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();ready()
        assertFalse(d.isShowing);assertEquals("dest/src/task.py",field<String>("activePath"));assertSame(content,editor.text)
        assertEquals(id,sessions.file("dest/src/task.py").id);assertTrue(workspace.resolve("dest/src/task.py").isFile)
        assertEquals("dest/src",field<String>("explorerSelection"));assertTrue(field<Set<String>>("expanded").contains("dest"))
    }
    @Test fun renameCollisionKeepsDialogContentsAndCanBeRetried(){
        create("occupied.py","do not overwrite");val original=workspace.resolve("main.py").readText()
        val d=rename("main.py","occupied.py");assertTrue(d.isShowing)
        assertEquals("occupied.py",views(d.window!!.decorView).filterIsInstance<EditText>().single().text.toString())
        assertEquals(original,workspace.resolve("main.py").readText());assertEquals("do not overwrite",workspace.resolve("occupied.py").readText())
        latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        views(d.window!!.decorView).filterIsInstance<EditText>().single().setText("retry.py");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain()
        assertEquals("retry.py",field<String>("activePath"));assertFalse(d.isShowing)
    }
    @Test fun externalSaveConflictPreventsRenameAndPreservesRecoverableCode(){
        edit("print('unsaved')");workspace.resolve("main.py").writeText("print('external')")
        val d=rename("main.py","changed.py")
        assertTrue(d.isShowing);assertEquals("main.py",field<String>("activePath"));assertFalse(workspace.resolve("changed.py").exists())
        assertEquals("print('external')",workspace.resolve("main.py").readText());assertEquals("print('unsaved')",editor.text.toString())
        assertEquals("print('unsaved')",field<DraftStore>("drafts").read(sessions.key("main.py"))!!.text)
    }
    @Test fun deleteCancelNeverSavesOrChangesTheDirtyBuffer(){
        val file=workspace.resolve("main.py");val original=file.readText();edit("print('keep dirty')")
        val source=port.snapshot();invokeFile("askDelete",file);latest().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();drain()
        assertEquals(source,port.snapshot());assertEquals(original,file.readText());assertTrue(field("dirty"))
    }
    @Test fun deletingAnotherFilePreservesActiveContentSelectionAndHistory(){
        create("remove.py","print('remove')");switch("remove.py");switch("main.py")
        edit("x");assertTrue(editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("y",1));editor.setSelection(1)
        val content=editor.text;invokeFile("askDelete",workspace.resolve("remove.py"));latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();ready()
        assertEquals("main.py",field<String>("activePath"));assertSame(content,editor.text);assertEquals(1,editor.selectionStart)
        assertFalse(workspace.resolve("remove.py").exists());assertFalse(field<List<String>>("opened").contains("remove.py"))
        assertTrue(editor.history(false));assertEquals("x",editor.text.toString())
    }
    @Test fun deletingTheLastFileCreatesARealFallbackAndInvalidatesTheOldConnection(){
        workspace.delete(workspace.resolve("main.cpp"));val old=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        invokeFile("askDelete",workspace.resolve("main.py"));latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();ready()
        assertEquals("main.cpp",field<String>("activePath"));assertEquals(Workspace.CPP_SAMPLE,workspace.resolve("main.cpp").readText())
        assertEquals(Workspace.CPP_SAMPLE,editor.text.toString());assertFalse(old.commitText("stale",1))
        assertFalse(workspace.resolve("main.py").exists());assertEquals("main.cpp",field<String>("loadedPath"))
    }
    @Test fun cancellingSettingsDiscardsOnlySettingsAndRetainsDirtyEditorAndPanel(){
        edit("print('keep')");editor.setSelection(2);val source=port.snapshot();val original=field<AppSettings>("settings");val panel=field<PanelState>("panelState")
        val d=settings();search(d,"行号");setting(d,"显示行号").isChecked=!original.lineNumbers
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain()
        assertFalse(d.isShowing);assertEquals(original,field<AppSettings>("settings"));assertEquals(source,port.snapshot());assertEquals(panel,field<PanelState>("panelState"))
        assertTrue(field("dirty"));assertNull(SettingsDialog.checkpoint(d))
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun actualSettingsApplyKeepsTabletBufferHistoryAndTerminalSession(){
        edit("x");assertTrue(editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("y",1));editor.setSelection(1)
        val content=editor.text;val source=port.snapshot();val registry=field<com.codepocket.ide.terminal.shell.ShellSessionRegistry>("terminalRegistry")
        val live=registry.catalog.entries().map{it.id to it.value.runtime};val panel=field<PanelState>("panelState")
        val original=field<AppSettings>("settings");val d=settings();search(d,"行号");setting(d,"显示行号").isChecked=!original.lineNumbers
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();ready()
        assertFalse(d.isShowing);assertEquals(!original.lineNumbers,field<AppSettings>("settings").lineNumbers);assertSame(content,editor.text)
        assertEquals(source,port.snapshot());assertEquals(panel,field<PanelState>("panelState"));assertEquals(live,registry.catalog.entries().map{it.id to it.value.runtime})
        assertTrue(editor.history(false));assertEquals("x",editor.text.toString())
    }
    @Test fun rotatingMainActivityRetainsUnappliedSettingsSearchAndDraft(){
        val original=field<AppSettings>("settings");val d=settings();search(d,"行号");setting(d,"显示行号").isChecked=!original.lineNumbers
        controller.recreate();main=controller.get();drain();layout();ready()
        val restored=field<AlertDialog>("toolDialog");assertTrue(restored.isShowing)
        assertEquals("行号",views(restored.window!!.decorView).filterIsInstance<EditText>().single{it.contentDescription=="搜索设置"}.text.toString())
        assertEquals(!original.lineNumbers,setting(restored,"显示行号").isChecked);assertEquals(original,field<AppSettings>("settings"))
        restored.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();assertEquals(!original.lineNumbers,field<AppSettings>("settings").lineNumbers)
    }
    private fun chooseTheme(d:AlertDialog){
        search(d,"界面主题")
        (menuText(d,"界面主题").parent.parent as View).performClick()
        val picker=latest();val index=if(field<Boolean>("dark"))1 else 0
        picker.listView.performItemClick(picker.listView.adapter.getView(index,null,picker.listView),index,index.toLong())
        search(d,"行号")
    }
    @Test fun settingsFormCannotChangeTheSubmittedDraftDuringActualAsyncSave(){
        edit("print('save settings')");val d=settings();chooseTheme(d)
        val toggle=setting(d,"显示行号");val row=toggle.parent as View
        val submitted=org.json.JSONObject(SettingsDialog.checkpoint(d)!!).getString("draft")
        val begun=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        WorkspaceIo.executor.execute{begun.countDown();check(release.await(10,TimeUnit.SECONDS))}
        assertTrue(begun.await(5,TimeUnit.SECONDS))
        try {
            d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue("Actual theme apply must be awaiting its real file save",field("saving"))
            row.performClick()
            assertEquals("Changes made during Apply must not be silently discarded by the pending success callback",
                submitted,org.json.JSONObject(SettingsDialog.checkpoint(d)!!).getString("draft"))
            assertFalse(toggle.isEnabled);assertFalse(row.isEnabled)
            assertFalse(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertFalse(d.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
            assertEquals("正在应用…",d.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            // A resize may rebuild the settings rows while the actual save remains blocked.
            val decor=d.window!!.decorView;val density=main.resources.displayMetrics.density
            decor.measure(View.MeasureSpec.makeMeasureSpec((1000*density).toInt(),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((800*density).toInt(),View.MeasureSpec.EXACTLY))
            decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
            val resized=setting(d,"显示行号");assertNotSame(toggle,resized);assertFalse(resized.isEnabled)
            (resized.parent as View).performClick()
            assertEquals(submitted,org.json.JSONObject(SettingsDialog.checkpoint(d)!!).getString("draft"))
        } finally {release.countDown()}
        drain()
        assertFalse(d.isShowing)
        assertEquals(AppSettings.fromJson(submitted),field<AppSettings>("settings"))
        assertEquals("print('save settings')",workspace.resolve("main.py").readText())
    }
    @Test fun failedThemeSaveReenablesTheFormAndKeepsItsUnappliedDraft(){
        edit("print('unsaved settings')");workspace.resolve("main.py").writeText("print('external')")
        val original=field<AppSettings>("settings");val d=settings();chooseTheme(d)
        val submitted=org.json.JSONObject(SettingsDialog.checkpoint(d)!!).getString("draft")
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain()
        assertTrue(d.isShowing);assertEquals(original,field<AppSettings>("settings"))
        assertEquals(submitted,org.json.JSONObject(SettingsDialog.checkpoint(d)!!).getString("draft"))
        assertTrue(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled);assertTrue(d.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val toggle=setting(d,"显示行号");assertTrue(toggle.isEnabled)
        val before=toggle.isChecked;(toggle.parent as View).performClick();assertEquals(!before,toggle.isChecked)
        assertEquals("print('external')",workspace.resolve("main.py").readText());assertEquals("print('unsaved settings')",editor.text.toString())
    }

}
