package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.graphics.Bitmap
import android.graphics.Canvas
import com.codepocket.editorlab.WorkbenchEditor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.TimeUnit

/** First launch and existing projects are separate user states. No simulated program execution. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class WorkbenchStartupBehaviorTest {
    @get:Rule val name=org.junit.rules.TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(8){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun code()=runCatching{field<WorkbenchEditor>("editor").text.toString()}.getOrDefault("")
    private fun layout(){val v=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics;repeat(2){v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    @Before fun settings(){val app=RuntimeEnvironment.getApplication();assertTrue(File(app.filesDir,"workspace").listFiles().orEmpty().isEmpty());RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))1.7f else 1f);app.getSharedPreferences("codepocket_v2",0).edit().clear().putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()}
    private fun launch(){controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout()}
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){main.finish();controller.pause().stop().destroy()};drain();RuntimeEnvironment.setFontScale(1f)}
    private fun invoke(method:String){ReflectionHelpers.callInstanceMethod<Any?>(main,method)}
    private fun existing():File {
        val app=RuntimeEnvironment.getApplication();val file=File(app.filesDir,"workspace/kept.py");file.parentFile!!.mkdirs();file.writeText("print('keep 😀')\n")
        app.getSharedPreferences("codepocket_v2",0).edit().putString("activeFile","kept.py").commit();return file
    }
    private fun closeTab(path:String){ReflectionHelpers.callInstanceMethod<Unit>(main,"closeTab",ClassParameter.from(String::class.java,path));drain();layout()}
    private fun open(path:String){ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();layout()}
    private fun capture(tag:String) {
        val v=main.findViewById<View>(android.R.id.content);val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap))
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd
        val dir=File(repo,"verification/workbench-behavior-2026-10-28/previews").apply{mkdirs()}
        File(dir,"$tag.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun welcome(label:String):View {
        val action=views(main.findViewById(android.R.id.content)).filterIsInstance<TextView>().firstOrNull{it.text.toString()==label&&it.isShown}
        assertNotNull("A visible welcome action is required: $label",action)
        return action!!
    }
    private fun assertNoDemo(){assertEquals("First launch must not open bundled demonstration code","",code());assertTrue(File(main.filesDir,"workspace").listFiles().orEmpty().none{it.name in setOf("main.cpp","main.py")})}
    @Test fun firstLaunchOffersNewAndOpenWithNoSeededCodeOrDemoFiles(){launch();assertNoDemo();welcome("新建文件");welcome("打开项目");capture("phone-dark-welcome")}
    @Test fun recreatingTheWelcomeScreenDoesNotTurnItIntoAnImplicitSampleProject(){launch();controller.recreate();main=controller.get();drain();layout();assertNoDemo();welcome("新建文件");welcome("打开项目")}
    @Test fun existingFileAndSavedProjectStillRestoreTheirExactBytes(){
        val app=RuntimeEnvironment.getApplication();val file=File(app.filesDir,"workspace/kept.py");file.parentFile!!.mkdirs();val text="print('已有项目保留 😀')\n";file.writeText(text)
        app.getSharedPreferences("codepocket_v2",0).edit().putString("activeFile","kept.py").commit();launch()
        assertEquals(text,field<WorkbenchEditor>("editor").text.toString());assertEquals(text,file.readText());assertEquals("kept.py",field<String>("activePath"))
    }
    @Test fun cancellingNewFileLeavesTheWelcomeAreaAndDoesNotCreateAnUnrequestedDemo(){
        launch();welcome("新建文件").performClick();idle();val form=ShadowAlertDialog.getLatestAlertDialog();assertTrue(form.isShowing)
        form.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();drain();assertNoDemo();welcome("打开项目")
    }
    @Test fun cancellingTheSystemOpenProjectPickerKeepsTheWelcomeState(){
        launch();welcome("打开项目").performClick();idle();val request=Shadows.shadowOf(main).nextStartedActivityForResult
        assertNotNull("Open project must use the real Android directory picker",request);assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,request.intent.action)
        ReflectionHelpers.callInstanceMethod<Any?>(main,"onActivityResult",ClassParameter.from(Int::class.javaPrimitiveType,request.requestCode),
            ClassParameter.from(Int::class.javaPrimitiveType,Activity.RESULT_CANCELED),ClassParameter.from(Intent::class.java,null))
        drain();assertNoDemo();welcome("新建文件")
    }
    @Test fun newFileFromWelcomeCreatesOnlyTheRequestedEmptyFileAndKeepsSavedEdits(){
        launch();welcome("新建文件").performClick();idle();val form=ShadowAlertDialog.getLatestAlertDialog()
        views(form.window!!.decorView).filterIsInstance<EditText>().single().setText("mine.cpp")
        form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();layout()
        assertEquals(listOf("mine.cpp"),File(main.filesDir,"workspace").listFiles().orEmpty().map{it.name})
        assertEquals("mine.cpp",field<String>("activePath"));assertEquals("",code());assertTrue(field<Boolean>("workspaceOpen"))
        val editor=field<WorkbenchEditor>("editor");editor.setText("int main() { return 0; }\n");ReflectionHelpers.callInstanceMethod<Boolean>(main,"saveActive",ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain()
        val key=field<EditorSessions>("sessions").key("mine.cpp");assertEquals(code(),File(main.filesDir,"workspace/mine.cpp").readText())
        controller.recreate();main=controller.get();drain();layout()
        assertEquals("int main() { return 0; }\n",code());assertEquals(key,field<EditorSessions>("sessions").key("mine.cpp"))
    }
    @Test fun closingTheLastTabSavesItsRealFileAndReopeningKeepsItsStableIdentity(){
        val file=existing();launch();val editor=field<WorkbenchEditor>("editor");editor.setText("print('saved edit')\n");val buffer=editor.text
        val key=field<EditorSessions>("sessions").key("kept.py");closeTab("kept.py")
        assertEquals("print('saved edit')\n",file.readText());assertEquals("",code());assertNull(field<String?>("loadedPath"));assertTrue(field<MutableList<String>>("opened").isEmpty())
        welcome("新建文件");open("kept.py")
        assertSame(editor,field("editor"));assertNotSame(buffer,editor.text);assertEquals("print('saved edit')\n",code());assertEquals(key,field<EditorSessions>("sessions").key("kept.py"));assertEquals(key,field<com.codepocket.ide.editor.SoraEditorPort>("editorPort").snapshot()!!.buffer.key)
        closeTab("kept.py");controller.recreate();main=controller.get();drain();layout();assertEquals("",code());welcome("打开项目");assertEquals("print('saved edit')\n",file.readText())
    }
    @Test fun closingAProjectKeepsItsFilesAndRestoresItsSavedTabsOnlyWhenReopened(){
        val file=existing();launch();val key=field<EditorSessions>("sessions").key("kept.py");invoke("closeWorkspace");drain();layout()
        assertFalse(field("workspaceOpen"));assertEquals("",code());assertEquals("print('keep 😀')\n",file.readText());welcome("打开项目")
        controller.recreate();main=controller.get();drain();layout();assertFalse(field("workspaceOpen"));assertEquals("",code())
        ReflectionHelpers.callInstanceMethod<Unit>(main,"requestWorkspace",ClassParameter.from(String::class.java,null));drain();layout()
        assertTrue(field("workspaceOpen"));assertEquals("kept.py",field<String>("activePath"));assertEquals(file.readText(),code());assertEquals(key,field<EditorSessions>("sessions").key("kept.py"))
    }
    @Test fun conflictingSaveBlocksClosingTheLastTabAndKeepsBothDraftAndProviderBytes(){
        val file=existing();launch();val editor=field<WorkbenchEditor>("editor");editor.setText("print('unsaved draft')\n");val buffer=editor.text
        file.writeText("print('outside change')\n");closeTab("kept.py")
        assertSame(buffer,editor.text);assertEquals("print('unsaved draft')\n",code());assertEquals("kept.py",field<String>("loadedPath"));assertTrue(field("dirty"));assertTrue(field("workspaceOpen"))
        assertEquals("print('outside change')\n",file.readText());val key=field<EditorSessions>("sessions").key("kept.py")
        assertEquals(code(),field<DraftStore>("drafts").read(key)!!.text)
        invoke("closeWorkspace");drain();assertTrue(field("workspaceOpen"));assertEquals("kept.py",field<String>("activePath"));assertSame(buffer,editor.text);assertTrue(field("dirty"));assertEquals("print('outside change')\n",file.readText())
    }
    @Test fun searchWithoutAnOpenProjectUsesTheDrawerAndCannotSearchRetainedClosedFiles(){
        val file=existing();launch();invoke("closeWorkspace");drain();layout();invoke("showProjectSearch");idle()
        val dialog=field<AlertDialog>("toolDialog");assertTrue(dialog.isShowing)
        val pane=field<View>("sidebarSearchView");assertFalse(views(pane).filterIsInstance<EditText>().single().isEnabled)
        assertFalse(views(pane).filterIsInstance<android.widget.Button>().single{it.text.toString()=="搜索"}.isEnabled)
        assertTrue(views(pane).filterIsInstance<android.widget.ListView>().single().adapter==null)
        views(pane).filterIsInstance<TextView>().single{it.text.toString()=="打开项目"}.performClick();idle()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,Shadows.shadowOf(main).nextStartedActivityForResult.intent.action)
        assertEquals("print('keep 😀')\n",file.readText());assertEquals("",code());assertFalse(field("workspaceOpen"))
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightWelcomeHasRealReachableActionsAndNoSamples(){
        launch();assertNoDemo();assertFalse(field("panelVisible"));for(label in listOf("新建文件","打开项目")){val v=welcome(label);assertTrue(v.height>=48);assertTrue(v.getGlobalVisibleRect(android.graphics.Rect()))};capture("tablet-light-welcome")
    }
    @Test @Config(qualifiers="w600dp-h360dp") fun compactLargeWelcomeCanScrollToItsActionsWithoutOpeningADocument(){
        launch();assertNoDemo();val action=welcome("打开项目");action.requestRectangleOnScreen(android.graphics.Rect(0,0,action.width,action.height),true);idle()
        assertTrue(action.getGlobalVisibleRect(android.graphics.Rect()));capture("compact-large-welcome")
    }
}
