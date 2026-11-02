package com.codepocket.ide

import android.app.AlertDialog
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.Bitmap
import android.graphics.Canvas
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.IdeCommand
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Main/Sora/native forms -> Messenger -> pinned host TLS engine -> original publication. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class GitRemoteUiIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private var observer:((JSONObject,GitRemoteReply)->Unit)?=null
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val ui get()=field<GitRemoteUiController>("remoteUi")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun settle() {
        val deadline=System.nanoTime()+120_000_000_000L;var stable=0
        while(stable<10 && System.nanoTime()<deadline) {
            idle();Thread.sleep(5)
            val remote=field<GitRemoteUiController?>("remoteUi");val local=field<GitUiController?>("gitUi")
            stable=if(!field<Boolean>("workspaceBusy") && remote?.busy!=true && local?.busy!=true)stable+1 else 0
        }
        assertTrue("Main/remote/local Git did not settle",stable>=10);idle()
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun <T:View> tagged(dialog:AlertDialog,tag:String):T=views(dialog.window!!.decorView).single{it.tag==tag} as T
    private fun form():AlertDialog=ReflectionHelpers.getField(ui,"dialog")
    private fun create(saved:Bundle?=null) {
        controller=Robolectric.buildActivity(MainActivity::class.java);main=controller.get()
        ReflectionHelpers.setField(main,"gitFactory",{context:android.content.Context->GitWorkbench(context,GitClient(HostGitBackend.transport{}))})
        ReflectionHelpers.setField(main,"remoteFactory",{context:android.content.Context->
            val channel=HostRemoteGit.Channel();channel.after={request,reply->observer?.invoke(request,reply)}
            GitRemoteWorkbench(context,GitRemoteClient(channel),GitPushLedger(context))
        })
        if(saved==null)controller.setup().visible() else controller.create(saved).start().restoreInstanceState(saved).resume().visible()
        settle();controller.windowFocusChanged(true)
    }
    private fun launch(theme:UiTheme=UiTheme.DARK) {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("codepocket_v2",0).edit().clear().putBoolean("workspaceOpen",false)
            .putString("activeFile","main.cpp").putString("stdin","legacy default input")
            .putString("settings",AppSettings(theme=theme,animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        File(Workspace(app,false).root,"keep.txt").writeText("default kept")
        create()
    }
    private fun openMenu(index:Int) {
        if(!field<Boolean>("sidebarGitActive")) {
            ReflectionHelpers.callInstanceMethod<Unit>(main,"dispatchCommand",ClassParameter.from(IdeCommand::class.java,IdeCommand.SOURCE_CONTROL));settle()
        }
        val surface=field<View>("sidebarGitView");val button=views(surface).single{it.tag=="git-remote-menu"}
        assertTrue(button.performClick());idle()
        val popup=ReflectionHelpers.getField<PopupMenu>(ui,"menu");assertTrue(popup.menu.performIdentifierAction(index,0));settle()
    }
    private fun fillClone(server:HostRemoteGit.Server) {
        openMenu(0);val dialog=form()
        tagged<EditText>(dialog,"git-remote-url").setText(server.url);tagged<EditText>(dialog,"git-clone-name").setText("远端项目")
        tagged<Button>(dialog,"git-remote-credentials").performClick();idle()
        val secret=ReflectionHelpers.getField<AlertDialog>(ui,"secretDialog")
        tagged<EditText>(secret,"git-credential-user").setText(server.credentials.username)
        tagged<EditText>(secret,"git-credential-token").setText(server.credentials.token)
        assertFalse(tagged<CheckBox>(secret,"git-credential-remember").isChecked)
        secret.getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
        assertTrue(dialog.isShowing);assertFalse(secret.isShowing)
    }
    private fun clone(server:HostRemoteGit.Server):Workspace {
        fillClone(server);form().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
        val workspace=field<Workspace>("workspace");assertTrue(workspace.identity.startsWith("internal-git:"));assertTrue(field("workspaceOpen"))
        return workspace
    }
    private fun fetch() {openMenu(2);assertTrue(form().isShowing);form().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()}
    private fun capture(name:String,dialog:AlertDialog=form()) {
        val root=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(3){root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));root.layout(0,0,root.measuredWidth,root.measuredHeight);idle()}
        val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);root.draw(canvas)
        val origin=IntArray(2);val position=IntArray(2);root.getLocationOnScreen(origin);dialog.window!!.decorView.getLocationOnScreen(position)
        canvas.save();canvas.translate((position[0]-origin[0]).toFloat(),(position[1]-origin[1]).toFloat());dialog.window!!.decorView.draw(canvas);canvas.restore()
        val folder=File(CppFixtures.root,"verification/git-remote-workbench-2026-11-02/previews").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){main.finish();controller.pause().stop().destroy()};idle();RuntimeEnvironment.setFontScale(1f)}
    @Test fun phoneCloneUsesRemoteMenuTemporaryCredentialAndAnIndependentProject() {
        launch();HostRemoteGit.Server().use{server->
            fillClone(server);capture("phone-dark-clone")
            assertEquals(0,server.requests());form().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
            val workspace=field<Workspace>("workspace");assertTrue(workspace.identity.startsWith("internal-git:"))
            assertEquals("default kept",File(main.filesDir,"workspace/keep.txt").readText());assertFalse(File(workspace.root,"main.cpp").exists())
            assertEquals("",field<EditText>("input").text.toString());assertEquals(server.firstHead,File(workspace.root,".git/refs/heads/main").readText().trim())
            assertTrue(File(workspace.root,field<String>("activePath")).isFile)
            assertFalse(File(main.noBackupFilesDir,"git-credentials").listFiles().orEmpty().any{it.extension=="sealed"})
        }
    }
    @Test @Config(qualifiers="w1100dp-h780dp") fun tabletFetchAndExplicitFastForwardReloadTheActualSoraDocument() {
        launch(UiTheme.LIGHT);HostRemoteGit.Server().use{server->
            val workspace=clone(server);val path=field<String>("activePath");val before=editor.text.toString()
            val remote=server.advance(mapOf(path to "updated from remote\n".toByteArray()))
            openMenu(2);capture("tablet-light-fetch");form().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
            assertEquals(before,editor.text.toString());assertEquals(before,File(workspace.root,path).readText())
            openMenu(3);val confirm=form();assertTrue(views(confirm.window!!.decorView).filterIsInstance<TextView>().any{it.text.contains(remote)})
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
            assertEquals("updated from remote\n",editor.text.toString());assertEquals(workspace.identity,field<Workspace>("workspace").identity)
            assertFalse(field("dirty"));assertEquals("default kept",File(main.filesDir,"workspace/keep.txt").readText())
        }
    }
    @Test fun lateNativeEditorInputAfterTlsCheckoutKeepsBothOriginalFileAndRecoveryDraft() {
        launch();HostRemoteGit.Server().use{server->
            val workspace=clone(server);val path=field<String>("activePath");val before=File(workspace.root,path).readBytes()
            server.advance(mapOf(path to "remote body\n".toByteArray()));fetch()
            observer={request,_->if(request.optString("action")=="fast_forward") {
                val latch=CountDownLatch(1);android.os.Handler(Looper.getMainLooper()).post{editor.setText("late unsaved editor content\n");latch.countDown()}
                assertTrue(latch.await(10,TimeUnit.SECONDS))
            }}
            openMenu(3);form().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
            assertEquals("late unsaved editor content\n",editor.text.toString());assertTrue(field("dirty"));assertArrayEquals(before,File(workspace.root,path).readBytes())
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()
            val drafts=field<DraftStore>("drafts");val sessions=field<EditorSessions>("sessions")
            assertEquals("late unsaved editor content\n",drafts.read(sessions.key(path))!!.text)
        }
    }
    @Test fun explicitNativePushConfirmsBothCommitsAndShowsTheDurableReceipt() {
        launch();HostRemoteGit.Server().use{server->
            val workspace=clone(server);fetch()
            File(workspace.root,"helper.py").writeText("value = 3\n")
            var state=JSONObject(HostGitBackend.run(workspace.root,JSONObject().put("action","status").toString()))
            state=JSONObject(HostGitBackend.run(workspace.root,JSONObject().put("action","stage").put("paths",org.json.JSONArray().put("helper.py"))
                .put("expected",state.getString("snapshot")).toString()))
            state=JSONObject(HostGitBackend.run(workspace.root,JSONObject().put("action","commit").put("expected",state.getString("snapshot"))
                .put("name","UI fixture").put("email","ui@example.invalid").put("message","Actual native push").toString()))
            val head=state.getString("head");openMenu(4)
            val confirm=form();val text=views(confirm.window!!.decorView).filterIsInstance<TextView>().joinToString("\n"){it.text}
            assertTrue(text.contains(head));assertTrue(text.contains(server.firstHead));assertTrue(text.contains(server.url))
            assertEquals(server.firstHead,server.head())
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle();assertEquals(head,server.head())
            openMenu(6);val reader=field<AlertDialog>("gitReader")
            val receipt=views(reader.window!!.decorView).filterIsInstance<TextView>().joinToString("\n"){it.text}
            assertTrue(receipt.contains(head));assertTrue(receipt.contains("pushed"));assertFalse(receipt.contains(server.credentials.token))
            reader.dismiss();idle();openMenu(9);form().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
            val records=GitPushLedger(main).forWorkspace(workspace.identity);assertTrue(records.isEmpty());assertEquals(head,server.head())
        }
    }
    @Test @Config(qualifiers="w360dp-h640dp") fun recreationRetainsOnlyNonSecretRemoteFieldsAndDismissesCredentialInput() {
        launch();RuntimeEnvironment.setFontScale(1.5f);openMenu(0)
        val parent=form();tagged<EditText>(parent,"git-remote-url").setText("https://github.com/fixture/project.git")
        tagged<EditText>(parent,"git-remote-branch").setText("feature/native")
        tagged<Button>(parent,"git-remote-credentials").performClick();idle()
        val secret=ReflectionHelpers.getField<AlertDialog>(ui,"secretDialog");val token=tagged<EditText>(secret,"git-credential-token")
        val value="only-a-fixture-token-do-not-serialize";token.setText(value)
        assertFalse(token.isSaveEnabled);assertNotNull(token.transformationMethod);capture("compact-large-credentials",secret)
        val saved=Bundle();controller.saveInstanceState(saved)
        assertFalse(saved.getString("remoteUiState").orEmpty().contains(value));assertFalse(ui.checkpoint().contains(value))
        val parcel=Parcel.obtain();val raw=try{parcel.writeBundle(saved);parcel.marshall().toList()}finally{parcel.recycle()}
        assertFalse(raw.windowed(value.length*2).any{it==value.toByteArray(Charsets.UTF_16LE).toList()})
        controller.pause().stop().destroy();assertEquals("",token.text.toString());create(saved)
        assertEquals("https://github.com/fixture/project.git",tagged<EditText>(form(),"git-remote-url").text.toString())
        assertEquals("feature/native",tagged<EditText>(form(),"git-remote-branch").text.toString())
        assertNull(ReflectionHelpers.getField<AlertDialog?>(ui,"secretDialog"))
    }
}
