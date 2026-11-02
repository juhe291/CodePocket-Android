package com.codepocket.ide

import android.app.AlertDialog
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.Canvas
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.IdeCommand
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Actual Main/Sora, original save transactions, native windows and pinned host Git backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class GitWorkbenchIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val requests=AtomicInteger()
    private var observer:(org.json.JSONObject)->Unit={}
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val ui get()=field<GitUiController>("gitUi")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(3){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun settle(){repeat(3){drain();ReflectionHelpers.getField<ExecutorService>(ui,"executor").submit{}.get(30,TimeUnit.SECONDS);idle()};assertFalse(ui.busy)}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun dispatch(command:IdeCommand){ReflectionHelpers.callInstanceMethod<Unit>(main,"dispatchCommand",ClassParameter.from(IdeCommand::class.java,command));idle()}
    private fun panel()=field<View>("sidebarGitView")
    private fun click(tag:String){val view=views(panel()).single{it.tag==tag};assertTrue("$tag enabled",view.isEnabled);assertTrue(view.performClick());settle()}
    private fun disk(path:String="main.py")=File(main.filesDir,"workspace/$path")
    private fun create(saved:Bundle?=null){
        controller=Robolectric.buildActivity(MainActivity::class.java);main=controller.get()
        ReflectionHelpers.setField(main,"gitFactory",{context:android.content.Context->GitWorkbench(context,GitClient(HostGitBackend.transport{requests.incrementAndGet();observer(it)}))})
        if(saved==null)controller.setup().visible() else controller.create(saved).start().restoreInstanceState(saved).resume().visible()
        drain();controller.windowFocusChanged(true);measure()
    }
    private fun launch(project:Boolean=true,theme:UiTheme=UiTheme.DARK){
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile",if(project)"main.py" else "")
            .putString("settings",AppSettings(theme=theme,animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        if(project)Workspace(app)
        create()
    }
    private fun measure(){val root=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(3){root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));root.layout(0,0,root.measuredWidth,root.measuredHeight);root.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun capture(name:String,dialog:AlertDialog?=field("sidebarGitDialog")){
        measure();val root=main.findViewById<View>(android.R.id.content);val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);root.draw(canvas)
        dialog?.window?.let{window->val origin=IntArray(2);val where=IntArray(2);root.getLocationOnScreen(origin);window.decorView.getLocationOnScreen(where);canvas.save();canvas.translate((where[0]-origin[0]).toFloat(),(where[1]-origin[1]).toFloat());window.decorView.draw(canvas);canvas.restore()}
        val directory=File(CppFixtures.root,"verification/git-workspace-2026-11-02/previews").apply{mkdirs()}
        File(directory,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun open(){dispatch(IdeCommand.SOURCE_CONTROL);settle()}
    private fun initialize(){open();click("git-init");assertTrue(disk(".git/HEAD").isFile)}
    private fun commitDialog():AlertDialog=ReflectionHelpers.getField(ui,"commitDialog")
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){main.finish();controller.pause().stop().destroy()};drain();RuntimeEnvironment.setFontScale(1f)}
    @Test fun phoneSourceControlUsesTheExistingDrawerAndCommitsOnlySavedStagedContent(){
        launch();val original=disk().readText();initialize()
        val drawer=field<AlertDialog>("sidebarGitDialog");assertTrue(drawer.isShowing);assertTrue(drawer.window!!.attributes.x>=48)
        editor.setText("print('saved from Git')\n");assertTrue(field("dirty"));click("git-stage:main.py")
        assertEquals(editor.text.toString(),disk().readText());assertFalse(field("dirty"));assertNotEquals(original,disk().readText())
        capture("phone-dark-staged");click("git-unstage:main.py");assertNotNull(views(panel()).singleOrNull{it.tag=="git-stage:main.py"})
        click("git-stage:main.py");click("git-commit");val dialog=commitDialog()
        val form=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        form.single{it.tag=="git-author"}.setText("CodePocket 用户");form.single{it.tag=="git-email"}.setText("user@example.invalid")
        form.single{it.tag=="git-message"}.setText("提交中文 😀\n\n完整正文")
        capture("phone-dark-commit",dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
        assertTrue(File(disk(".git"),"objects").walkTopDown().any{it.isFile});assertFalse(views(panel()).any{it.tag=="git-commit"})
        val backend=HostGitBackend.run(disk().parentFile,org.json.JSONObject().put("action","history").toString())
        assertEquals("提交中文 😀\n\n完整正文",GitProtocol.decode(backend).commits.single().message.trimEnd())
        val workspace=field<Workspace>("workspace");assertTrue(workspace.children(workspace.root).none{it.name==".git"});assertEquals("print('saved from Git')\n",editor.text.toString())
        click("git-history");assertTrue(requests.get()>5)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletReusesSidebarAndShowsRealDiffWithoutChangingSoraUndo(){
        launch(theme=UiTheme.LIGHT);initialize();val content=editor.text;val before=disk().readText()
        assertTrue(panel().isShown);assertFalse(field<AlertDialog?>("sidebarGitDialog")?.isShowing==true)
        click("git-change:false:main.py");val reader=field<AlertDialog>("gitReader")
        val text=views(reader.window!!.decorView).filterIsInstance<TextView>().map{it.text.toString()}
        val response=ReflectionHelpers.getField<GitResponse>(ui,"response")
        assertTrue(text.contains(response.text));assertTrue(before.lineSequence().filter{it.isNotEmpty()}.all{line->text.any{it.contains("+$line")}})
        capture("tablet-light-untracked-diff",reader);reader.dismiss();assertSame(content,editor.text);assertEquals(before,disk().readText())
        dispatch(IdeCommand.EXPLORER);assertFalse(field("sidebarGitActive"));dispatch(IdeCommand.SOURCE_CONTROL);settle();assertTrue(panel().isShown)
    }
    @Test fun commitDraftSurvivesRecreationAndCancelWithoutChangingTheStagedIndex(){
        launch();initialize();click("git-stage:main.py");val bytes=disk(".git/index").readBytes();click("git-commit")
        val dialog=commitDialog();views(dialog.window!!.decorView).filterIsInstance<EditText>().single{it.tag=="git-message"}.setText("保留草稿 😀")
        val saved=Bundle();controller.saveInstanceState(saved);controller.pause().stop().destroy();create(saved);settle()
        val restored=commitDialog();assertTrue(restored.isShowing)
        assertEquals("保留草稿 😀",views(restored.window!!.decorView).filterIsInstance<EditText>().single{it.tag=="git-message"}.text.toString())
        restored.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertArrayEquals(bytes,disk(".git/index").readBytes())
        click("git-commit");assertEquals("保留草稿 😀",views(commitDialog().window!!.decorView).filterIsInstance<EditText>().single{it.tag=="git-message"}.text.toString())
    }
    @Test fun lateDiffDoesNotDismissOrModifyAnOpenSettingsDraft(){
        launch();initialize();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        observer={if(it.getJSONObject("request").getString("action")=="diff"){entered.countDown();check(release.await(15,TimeUnit.SECONDS))}}
        try {
            assertTrue(views(panel()).single{it.tag=="git-change:false:main.py"}.performClick());assertTrue(entered.await(5,TimeUnit.SECONDS))
            dispatch(IdeCommand.SETTINGS);val settings=field<AlertDialog>("toolDialog");assertTrue(settings.isShowing)
            release.countDown();settle();assertSame(settings,field("toolDialog"));assertTrue(settings.isShowing);assertNull(field("gitReader"))
        } finally {release.countDown()}
    }
    @Test fun sourceControlWithoutAProjectDoesNotSeedSampleFilesOrInitializeARepository(){
        launch(false);val welcome=editor.text.toString();open();assertEquals(0,requests.get())
        assertTrue(views(panel()).any{it.tag=="git-open"});assertFalse(disk(".git").exists());assertFalse(disk().exists());assertEquals(welcome,editor.text.toString())
        capture("phone-dark-no-project")
    }
    @Test @Config(qualifiers="w360dp-h640dp") fun largeFontCommitFormRemainsScrollableWithReachableActions(){
        RuntimeEnvironment.setFontScale(1.5f);launch(theme=UiTheme.LIGHT);initialize();click("git-stage:main.py");click("git-commit")
        val dialog=commitDialog();val decor=dialog.window!!.decorView
        assertTrue(views(decor).filterIsInstance<android.widget.ScrollView>().any{it.isShown})
        val close=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);val commit=dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertTrue(close.isShown);assertTrue(commit.isShown);assertTrue(dialog.window!!.attributes.width<=main.resources.displayMetrics.widthPixels)
        capture("phone-light-large-commit",dialog)
    }
}
