package com.codepocket.ide

import android.app.Application
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.codepocket.ide.ui.IdeCommand
import com.codepocket.ide.ui.MainToolbar
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Portable real path alias: same canonical mismatch as Android /data/data vs /data/user/0.
 * No host symlink privilege or device Keystore is required; production child guards stay active. */
class GitAliasedApplication:Application() {
    private fun alias(root:File):File {check(File(root,"trusted-alias").isDirectory || File(root,"trusted-alias").mkdirs());return File(root,"trusted-alias/..")}
    override fun getFilesDir()=alias(super.getFilesDir())
    override fun getCacheDir()=alias(super.getCacheDir())
    override fun getNoBackupFilesDir()=alias(super.getNoBackupFilesDir())
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=GitAliasedApplication::class,qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@LooperMode(LooperMode.Mode.PAUSED)
class GitPrivatePathRegressionTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(15,TimeUnit.SECONDS);idle()}}
    private fun <T> background(work:()->T):T {
        val executor=Executors.newSingleThreadExecutor();val task=executor.submit<T>{work()}
        return try {
            val deadline=System.nanoTime()+45_000_000_000L
            // The production Messenger response is delivered on Main; a PAUSED host looper must run it.
            while(!task.isDone && System.nanoTime()<deadline){idle();Thread.sleep(5)}
            task.get(1,TimeUnit.SECONDS)
        }finally{task.cancel(true);executor.shutdownNow()}
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun dispatch(command:IdeCommand){ReflectionHelpers.callInstanceMethod<Unit>(main,"dispatchCommand",ClassParameter.from(IdeCommand::class.java,command));idle()}
    private fun launch(){
        Workspace(app);app.getSharedPreferences("codepocket_v2",0).edit().clear().putBoolean("workspaceOpen",true).putString("activeFile","main.py")
            .putString("settings",AppSettings(animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java);main=controller.get()
        ReflectionHelpers.setField(main,"gitFactory",{context:android.content.Context->GitWorkbench(context,GitClient(HostGitBackend.transport{}))})
        controller.setup().visible();drain();controller.windowFocusChanged(true)
        val root=main.findViewById<View>(android.R.id.content);repeat(3){root.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,411,800);root.viewTreeObserver.dispatchOnGlobalLayout();idle()}
    }
    @Before fun aliasIsGenuine(){for(root in listOf(app.filesDir,app.cacheDir,app.noBackupFilesDir)){assertTrue(root.isDirectory);assertNotEquals(root.absoluteFile,root.canonicalFile)}}
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){main.finish();controller.pause().stop().destroy()};drain()}
    @Test fun temporaryCredentialsUseTheTrustedNoBackupAliasWithoutCreatingAKey(){
        val vault=GitCredentialVault(app);val url="https://github.com/fixture/project.git"
        vault.save(url,GitCredentials("fixture","not-a-real-secret"));assertEquals("not-a-real-secret",vault.load(url)!!.token)
        assertTrue(File(app.noBackupFilesDir.canonicalFile,"git-credentials").listFiles().orEmpty().isEmpty());vault.forgetSession();assertNull(vault.load(url))
    }
    @Test fun pushReceiptLedgerUsesTheSameCanonicalPrivateBoundary(){
        val ledger=GitPushLedger(app);assertTrue(ledger.forWorkspace("internal").isEmpty());ledger.clearConfirmed()
        assertTrue(File(app.noBackupFilesDir.canonicalFile,"git-push-receipts/ledger.lock").isFile)
    }
    @Test fun projectPickerCanListCreateAndReopenAManagedCloneUnderTheTrustedAlias(){
        val targets=GitCloneTargets(app);assertTrue(targets.list().isEmpty());val target=targets.createPrivate("保留克隆")
        targets.completed(target);assertEquals(target.identity,targets.open(target.selector).identity)
        assertEquals(target.selector,targets.list().single().first);assertTrue(targets.open(target.selector).root.canonicalFile.isDirectory)
    }
    @Test fun localJobsRoundTripTheirStrictRootGuardAndCleanupThroughTheAlias(){
        val workspace=Workspace(app,false);val store=workspace.gitStore();val snapshot=store.capture(GitJobs.next(app))
        assertEquals(snapshot.root,GitJobs.root(app,snapshot.root.path));GitJobs.discard(app,snapshot.root)
        assertTrue(GitJobs.parent(app).listFiles().orEmpty().isEmpty())
    }
    @Test fun remoteJobsRoundTripTheirStrictRootGuardAndCleanupThroughTheAlias(){
        val workspace=Workspace(app,false);val store=workspace.gitStore();val snapshot=store.capture(GitRemoteJobs.next(app))
        assertEquals(snapshot.root,GitRemoteJobs.root(app,snapshot.root.path));GitRemoteJobs.discard(app,snapshot.root)
        assertTrue(GitRemoteJobs.parent(app).listFiles().orEmpty().isEmpty())
    }
    @Test fun actualLocalGitStatusAndInitializationKeepTheOriginalSource(){
        val workspace=Workspace(app,false);File(workspace.root,"keep.py").writeText("print('keep')\n")
        GitWorkbench(app,GitClient(HostGitBackend.transport{})).use{backend->
            val status=background{backend.perform(workspace,"status")};assertTrue(status.toString(),status.ok)
            val init=background{backend.perform(workspace,"init",expected=status.state!!.snapshot)};assertTrue(init.toString(),init.ok)
            assertEquals("print('keep')\n",File(workspace.root,"keep.py").readText());assertTrue(File(workspace.root,".git/HEAD").isFile)
        }
    }
    @Test fun realSourceControlMoreButtonOpensWithoutTheDeviceVaultConstructorCrash(){
        launch();dispatch(IdeCommand.SOURCE_CONTROL)
        val surface=field<View>("sidebarGitView");assertTrue(views(surface).single{it.tag=="git-remote-menu"}.performClick());idle()
        assertNotNull(field<GitRemoteUiController?>("remoteUi"));assertFalse(main.isFinishing)
    }
    @Test fun realInternalProjectHeadingOpensTheWorkspacePickerWithoutCrashing(){
        launch();val bar=field<MainToolbar>("mainToolbar");val heading=views(bar).filterIsInstance<TextView>().single{it.contentDescription?.toString()?.contains("内部项目")==true}
        assertTrue(heading.performClick());idle();assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing);assertFalse(main.isFinishing)
    }
    @Test fun actualRemoteSnapshotAlsoUsesTheCanonicalRecoveryAndJobRoots(){
        val workspace=Workspace(app,false);File(workspace.root,"keep.py").writeText("kept remote snapshot\n")
        GitRemoteWorkbench(app,GitRemoteClient(HostRemoteGit.Channel()),GitPushLedger(app)).use{backend->
            val result=background{backend.perform(workspace,"snapshot")};assertTrue(result.toString(),result.ok)
            assertEquals("kept remote snapshot\n",File(workspace.root,"keep.py").readText())
        }
        assertTrue(GitRemoteJobs.parent(app).listFiles().orEmpty().isEmpty())
    }
    @Test fun unavailableCredentialStorageShowsAnErrorAndKeepsTheDirtyEditor(){
        launch();val editor=field<com.codepocket.editorlab.WorkbenchEditor>("editor");editor.setText("print('retained')\n")
        val sentinel=File(app.noBackupFilesDir.canonicalFile,"git-credentials");sentinel.writeText("keep existing file")
        dispatch(IdeCommand.SOURCE_CONTROL);val surface=field<View>("sidebarGitView")
        assertTrue(views(surface).single{it.tag=="git-remote-menu"}.performClick());idle()
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing);assertNull(field<GitRemoteUiController?>("remoteUi"))
        assertTrue(views(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView).filterIsInstance<TextView>().any{it.text.contains("Git 凭据存储暂不可用")})
        assertEquals("keep existing file",sentinel.readText());assertEquals("print('retained')\n",editor.text.toString());assertTrue(field("dirty"));assertFalse(main.isFinishing)
    }
    @Test fun unavailableCloneListStillAllowsTheRealWorkspacePickerAndKeepsItsSentinel(){
        launch();val sentinel=File(app.filesDir.canonicalFile,"git-projects");sentinel.writeText("keep projects sentinel")
        val bar=field<MainToolbar>("mainToolbar");val heading=views(bar).filterIsInstance<TextView>().single{it.contentDescription?.toString()?.contains("内部项目")==true}
        assertTrue(heading.performClick());idle();val picker=ShadowAlertDialog.getLatestAlertDialog();assertTrue(picker.isShowing)
        assertTrue(picker.listView.adapter.count>=3);assertEquals("keep projects sentinel",sentinel.readText());assertFalse(main.isFinishing)
    }
}
