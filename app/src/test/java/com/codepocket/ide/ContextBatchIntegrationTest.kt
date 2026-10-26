package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.*
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
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

/** One UI batch: actual file actions, project scope, terminal selectors and workspace search. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class ContextBatchIntegrationTest {
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
    private fun popup():PopupWindow{val p=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow;assertNotNull("Use the shared anchored context menu",p);assertTrue(p!!.isShowing);return p}
    private fun row(w:PopupWindow,title:String)=views(w.contentView).single{it.contentDescription==title}
    private fun heading(w:PopupWindow,title:String)=assertTrue("Context title must be visible",views(w.contentView).filterIsInstance<TextView>().any{it.text.toString()==title})
    private fun capture(tag:String,v:View){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(DesignTokens.forContext(main).overlay);v.draw(c)
        val cwd=File(System.getProperty("user.dir")!!);val root=if(cwd.name=="app")cwd.parentFile else cwd;val dir=File(root,"verification/workbench-context-batch-2026-10-26/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private fun open(path:String){ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready();layout()}
    private fun input(text:String){editor.setSelection(editor.length());assertTrue(editor.onCreateInputConnection(EditorInfo()).commitText(text,1));idle();ready();layout()}
    private fun tree():LinearLayout {field<LinearLayout?>("sidebarExplorerList")?.let{return it};invoke("showExplorer");return field<LinearLayout?>("overlayExplorerList")!!}
    private fun treeRow(list:LinearLayout,path:String)=(0 until list.childCount).map{list.getChildAt(it)}.single{it.getTag(R.id.workbench_key)=="entry:$path"}
    private fun fileMenu(path:String):PopupWindow {val list=tree();assertTrue(treeRow(list,path).performLongClick());idle();return popup()}
    private fun newFile(name:String):String{val parent=workspace.create(workspace.root,"src",true);val f=workspace.create(parent,name,false);workspace.write(f,"value = 1\n");field<MutableSet<String>>("expanded").add("src");val path=workspace.path(f);open(path);return path}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()
    }
    @After fun close(){if(::main.isInitialized){Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow?.dismiss();ShadowAlertDialog.getLatestAlertDialog()?.takeIf{it.isShowing}?.dismiss();field<AlertDialog?>("explorerDialog")?.dismiss();main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletFileContextKeepsRealSaveProtectionAndRenameIdentity(){
        val path=newFile("rename-source.py");input("# keep real edits\n");val buffer=editor.text;val expected=buffer.toString();val id=field<EditorSessions>("sessions").file(path).id
        val menu=fileMenu(path);heading(menu,path);capture("tablet-file-context",menu.contentView);assertTrue(field("dirty"))
        row(menu,"重命名").performClick();idle();val form=ShadowAlertDialog.getLatestAlertDialog();assertTrue(form.isShowing)
        views(form.window!!.decorView).filterIsInstance<EditText>().single().setText("renamed.py");form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();ready();layout()
        assertEquals("src/renamed.py",field<String>("activePath"));assertEquals(id,field<EditorSessions>("sessions").file("src/renamed.py").id)
        assertEquals(expected,workspace.read(workspace.resolve("src/renamed.py")));assertFalse(workspace.resolve(path).exists());assertSame(buffer,editor.text);assertFalse(field("dirty"))
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLightLargeFileTitleAndDeleteCancelKeepTheFileAndIgnoreOldRows(){
        val path=newFile("很长的学习文件名_".repeat(4)+".py");val buffer=editor.text;val menu=fileMenu(path);heading(menu,path)
        val title=views(menu.contentView).filterIsInstance<TextView>().single{it.text.toString()==path};assertEquals(path,title.contentDescription.toString());assertTrue(title.height>=title.lineHeight)
        val delete=row(menu,"删除");assertEquals(DesignTokens.forContext(main).error,views(delete).filterIsInstance<TextView>().single().currentTextColor)
        capture("phone-light-large-file-context",menu.contentView);delete.performClick();idle();val confirm=ShadowAlertDialog.getLatestAlertDialog();assertTrue(confirm.isShowing)
        confirm.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertTrue(workspace.resolve(path).isFile);assertSame(buffer,editor.text)
        delete.performClick();idle();assertFalse("Retired context rows cannot reopen a destructive confirmation",ShadowAlertDialog.getLatestAlertDialog().isShowing)
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletProjectMenuShowsScopeAndCreatesTheFolderInTheSelectedDirectory(){
        val parent=workspace.create(workspace.root,"selected",true);field<MutableSet<String>>("expanded").add("selected")
        ReflectionHelpers.setField(main,"currentDirectory","selected");val buffer=editor.text
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showExplorerCommands",ClassParameter.from(View::class.java,field<MainToolbar>("mainToolbar").menuAnchor));idle();val menu=popup();heading(menu,"资源管理器");capture("tablet-project-context",menu.contentView)
        row(menu,IdeCommand.NEW_FOLDER.title).performClick();idle();val form=ShadowAlertDialog.getLatestAlertDialog();assertTrue(form.isShowing)
        views(form.window!!.decorView).filterIsInstance<EditText>().single().setText("created-from-menu");form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();layout()
        assertTrue(File(parent,"created-from-menu").isDirectory);assertFalse(workspace.resolve("created-from-menu").exists());assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightLargeSessionAndPaletteSelectorsKeepTheSelectedModel(){
        invoke("openTerminal");invoke("newShellSession");layout();val registry=field<ShellSessionRegistry>("terminalRegistry");val selected=registry.catalog.selected();assertNotNull(selected);val ids=registry.catalog.entries().map{it.id};val buffer=editor.text
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showTerminalChoices",ClassParameter.from(View::class.java,field<PanelToolbar>("panelToolbar").menuAnchor));idle();heading(popup(),"终端会话");capture("tablet-light-large-session-selector",popup().contentView);popup().dismiss()
        field<PanelToolbar>("panelToolbar").menuAnchor.performClick();idle();row(popup(),"配色与帮助").performClick();idle();row(popup(),"终端配色").performClick();idle()
        val colors=popup();heading(colors,"终端配色");assertNotNull(row(colors,"返回配色与帮助"));capture("tablet-light-large-palette-selector",colors.contentView)
        row(colors,"返回配色与帮助").performClick();idle();heading(popup(),"配色与帮助");assertSame(selected,registry.catalog.selected());assertEquals(ids,registry.catalog.entries().map{it.id});assertSame(buffer,editor.text)
        row(popup(),"终端配色").performClick();idle();row(popup(),"Nord 配色").performClick();idle();layout()
        assertEquals(TerminalTheme.NORD,field<AppSettings>("settings").terminalTheme);assertSame(selected,registry.catalog.selected());assertEquals(ids,registry.catalog.entries().map{it.id});assertSame(buffer,editor.text)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showTerminalChoices",ClassParameter.from(View::class.java,field<PanelToolbar>("panelToolbar").menuAnchor));idle()
        row(popup(),registry.catalog.entries().first().name()).performClick();idle();layout()
        assertSame(registry.catalog.entries().first(),registry.catalog.selected());assertEquals(ids,registry.catalog.entries().map{it.id});assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w280dp-h640dp") fun phoneLightLargeProjectSearchUsesTheFullInputWidthAndNavigatesRealUnsavedResults(){
        input("batch_search_token\n");val buffer=editor.text;val text=buffer.toString();invoke("showProjectSearch");val d=field<AlertDialog>("toolDialog");val all=views(d.window!!.decorView);val query=all.filterIsInstance<EditText>().single();val search=all.filterIsInstance<Button>().single{it.text.toString()=="搜索"}
        assertEquals("Narrow large-font controls must stack",LinearLayout.VERTICAL,(query.parent as LinearLayout).orientation)
        assertTrue(query.width>search.width);assertTrue(query.height>=query.lineHeight+query.compoundPaddingTop+query.compoundPaddingBottom)
        query.setText("batch_search_token");search.performClick();drain();val list=all.filterIsInstance<ListView>().single();assertEquals(1,list.adapter.count);capture("phone-light-large-project-search",d.window!!.decorView)
        assertTrue(d.getButton(AlertDialog.BUTTON_NEGATIVE).height>=48);list.performItemClick(list.adapter.getView(0,null,list),0,0);drain();ready();layout()
        assertFalse(d.isShowing);assertSame(buffer,editor.text);assertEquals(text,buffer.toString());assertTrue(field("dirty"));assertEquals("main.py",field<String>("activePath"))
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletProjectSearchEmptyStateDoesNotReserveABlankResultsPanel(){
        val buffer=editor.text;invoke("showProjectSearch");val d=field<AlertDialog>("toolDialog");val all=views(d.window!!.decorView)
        all.filterIsInstance<EditText>().single().setText("no_such_batch_token_927");all.filterIsInstance<Button>().single{it.text.toString()=="搜索"}.performClick();drain()
        val list=all.filterIsInstance<ListView>().single();assertEquals(0,list.adapter.count);assertEquals("Empty results should not occupy a blank fixed-height panel",View.GONE,list.visibility)
        capture("tablet-project-search-empty",d.window!!.decorView);d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertSame(buffer,editor.text)
    }
}
