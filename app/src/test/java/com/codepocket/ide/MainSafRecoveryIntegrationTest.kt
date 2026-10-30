package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.view.View
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

/** Main Activity + real Sora + ContentResolver/PFD backed by real files. Not device/provider certification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainSafRecoveryIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private val sessions get()=field<EditorSessions>("sessions")
    private val drafts get()=field<DraftStore>("drafts")
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    @Before fun launch(){
        ReflectionHelpers.setStaticField(RunSession::class.java,"result",null)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py").putString("settings",AppSettings(autoSave=false,animations=false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get()
        settle();controller.windowFocusChanged(true);layout();ready()
    }
    private fun settle(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle()}}
    private fun ready(){repeat(100){Shadows.shadowOf(Looper.getMainLooper()).idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Sora layout not ready")}
    private fun layout(){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight)}
    private fun edit(text:String){editor.setText(text);ready();editor.setSelection(text.length)}
    private fun command(name:String){ReflectionHelpers.callInstanceMethod<Unit>(main,name)}
    private fun save(){ReflectionHelpers.callInstanceMethod<Boolean>(main,"saveActive",ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));settle()}
    private fun switch(path:String){ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));settle();ready()}
    private fun tree(uri:Uri?){ReflectionHelpers.callInstanceMethod<Unit>(main,"requestWorkspace",ClassParameter.from(String::class.java,uri?.toString()));settle();layout();ready()}
    private fun picker(data:Intent){ReflectionHelpers.callInstanceMethod<Unit>(main,"onActivityResult",ClassParameter.from(Int::class.javaPrimitiveType,203),ClassParameter.from(Int::class.javaPrimitiveType,Activity.RESULT_OK),ClassParameter.from(Intent::class.java,data))}
    private fun latest()=ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing)}
    private fun provider():Provider {val folder=File(main.filesDir,"main-test-provider").apply{mkdirs()}
        val p=Provider(File(folder,"source.py").apply{writeText("print('provider original')\n")},File(folder,"helper.py").apply{writeText("answer = 42\n")})
        p.attachInfo(main,ProviderInfo().apply{authority=Provider.AUTHORITY});ShadowContentResolver.registerProviderInternal(Provider.AUTHORITY,p);return p}
    private fun originalBackup()=File(main.filesDir,"provider-recovery/${workspace.identity.substring(4)}/${DocumentTree.digest("opaque:file:1")}.before.txt")
    @After fun close(){ReflectionHelpers.setStaticField(RunSession::class.java,"result",null);if(::main.isInitialized){main.finish();controller.pause().stop().destroy();settle()}}

    @Test fun pickerRejectsReadOnlyGrantBeforeReplacingCurrentWorkspace(){
        val p=provider();val content=editor.text;val id=sessions.file("main.py").id
        picker(Intent().setData(p.tree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));settle()
        assertEquals("internal",workspace.identity);assertSame(content,editor.text);assertEquals(id,sessions.file("main.py").id)
        assertTrue(latest().findViewById<TextView>(android.R.id.message).text.contains("可读写目录"))
    }
    @Test fun actualPickerGrantLoadsOpaqueUrisAndSaveVerifiesRealProviderBytes(){
        val p=provider();picker(Intent().setData(p.tree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION));settle();layout();ready()
        assertTrue(workspace.external);assertEquals("external.py",field<String>("activePath"));assertEquals(p.source.readText(),editor.text.toString())
        edit("print('SAF 中文 😀')\n");save()
        assertEquals(editor.text.toString(),p.source.readText());assertFalse(field("dirty"));assertNull(drafts.read(sessions.key("external.py")))
        assertEquals("print('provider original')\n",originalBackup().readText())
    }
    @Test fun revokedPermissionPreservesContentCheckpointAndBlocksWorkspaceSwitchUntilRetry(){
        val p=provider();tree(p.tree);edit("print('revoked draft')\n");val content=editor.text;val key=sessions.key("external.py");p.revoked=true
        tree(null);latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle()
        assertTrue(workspace.external);assertSame(content,editor.text);assertTrue(field("dirty"));assertEquals(editor.text.toString(),drafts.read(key)!!.text)
        assertEquals("print('provider original')\n",p.source.readText());assertFalse(p.written)
        p.revoked=false;save();assertEquals(editor.text.toString(),p.source.readText());assertFalse(field("dirty"));assertNull(drafts.read(key))
    }
    @Test fun providerWriteDeniedDoesNotTruncateOriginalOrClearDraft(){
        val p=provider();tree(p.tree);edit("my readonly draft");p.writable=false;save()
        assertTrue(field("dirty"));assertFalse(p.written);assertEquals("print('provider original')\n",p.source.readText())
        assertEquals("my readonly draft",drafts.read(sessions.key("external.py"))!!.text)
        p.writable=true;save();assertEquals("my readonly draft",p.source.readText());assertFalse(field("dirty"))
    }
    @Test fun readbackMismatchBlocksNavigationRetainsActualOriginalAndRecoveryDraft(){
        val p=provider();tree(p.tree);edit("print('new draft')\n");p.mismatch=true;val content=editor.text
        switch("helper.py")
        assertEquals("external.py",field<String>("activePath"));assertSame(content,editor.text);assertTrue(field("dirty"))
        assertEquals("print('provider original')\n",originalBackup().readText());assertEquals(editor.text.toString(),drafts.read(sessions.key("external.py"))!!.text)
        assertTrue(field<TextView>("status").text.contains("校验失败"));assertFalse(field<List<String>>("opened").contains("helper.py"))
    }
    @Test fun failedReadOfAnotherProviderFileDoesNotReplaceActiveTabOrHistory(){
        val p=provider();tree(p.tree);editor.setSelection(2);val content=editor.text;val id=sessions.file("external.py").id;p.unreadable="opaque:file:2"
        switch("helper.py")
        assertEquals("external.py",field<String>("loadedPath"));assertEquals("external.py",field<String>("activePath"));assertSame(content,editor.text);assertEquals(2,editor.selectionStart)
        assertEquals(id,sessions.file("external.py").id);assertFalse(field<List<String>>("opened").contains("helper.py"));assertFalse(field("workspaceBusy"))
    }
    @Test fun invalidUtf8WorkspaceCannotReplaceInternalEditor(){
        val p=provider();p.source.writeBytes(byteArrayOf(0xc3.toByte(),0x28));val content=editor.text;val root=workspace.root
        tree(p.tree)
        assertFalse(workspace.external);assertEquals(root,workspace.root);assertSame(content,editor.text);assertFalse(field("workspaceBusy"));assertEquals("main.py",field<String>("loadedPath"))
    }
    @Test fun revokedWorkspaceCanSwitchWithDurableDraftAndRestoreAfterReauthorization(){
        val p=provider();tree(p.tree);edit("print('offline retained')\n");val key=sessions.key("external.py");val id=sessions.file("external.py").id;p.revoked=true
        tree(null);latest().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();settle();layout();ready()
        assertEquals("internal",workspace.identity);assertEquals("print('offline retained')\n",drafts.read(key)!!.text)
        p.revoked=false;tree(p.tree)
        assertEquals("external.py",field<String>("loadedPath"));assertEquals(id,sessions.file("external.py").id);assertTrue(field("dirty"));assertEquals("print('offline retained')\n",editor.text.toString())
        save();assertEquals(editor.text.toString(),p.source.readText());assertNull(drafts.read(key))
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun recreationRestoresDirtyProviderDraftAndRejectsNewExternalVersionOnSave(){
        val p=provider();tree(p.tree);edit("print('my surviving draft')\n");editor.setSelection(3);val key=sessions.key("external.py");val id=sessions.file("external.py").id
        p.revoked=true;controller.recreate();main=controller.get();settle()
        // Provider denied at startup: recover through the failure dialog without discarding its durable draft.
        assertEquals("print('my surviving draft')\n",drafts.read(key)!!.text)
        p.revoked=false;p.source.writeText("print('external changed')\n")
        latest().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();settle();layout();ready();tree(p.tree)
        assertEquals(id,sessions.file("external.py").id);assertEquals("print('my surviving draft')\n",editor.text.toString());assertTrue(field("dirty"))
        save();assertEquals("print('external changed')\n",p.source.readText());assertTrue(field("dirty"));assertEquals(editor.text.toString(),drafts.read(key)!!.text)
    }
    @Test fun matchingCheckpointIsCleanAndDoesNotKeepStaleConflictHash(){
        val text=editor.text.toString();val key=sessions.key("main.py");drafts.put(key,"main.py",text,"old-invalid-base")
        switch("main.cpp");switch("main.py");assertFalse(field("dirty"));assertEquals(DocumentTree.digest(text),field<String>("loadedVersion"))
        edit("print('save after matching checkpoint')\n");save();assertFalse(field("dirty"));assertEquals(editor.text.toString(),workspace.resolve("main.py").readText())
    }

    // Diagnostic metadata fixtures exercise production navigation only, not simulated program execution.
    private fun diagnostic(path:String="main.py",line:Int=2,identity:String=workspace.identity,hash:String=DocumentTree.digest(editor.text.toString()),file:String=path){
        val result=RunResult("","traceback","1",1,errorLine=line,errorFile=File(workspace.root,file).path,sourcePath=File(workspace.root,path).path,projectRoot=workspace.root.path,
            workspaceId=identity,sourceKey=sessions.key(path),sourceHash=hash)
        ReflectionHelpers.setStaticField(RunSession::class.java,"result",result);ReflectionHelpers.setField(main,"result",result)
    }
    @Test fun mainRuntimeDiagnosticLocatesMatchingSourceByUtf16(){
        edit("emoji = '😀'\nprint(unknown)\n");save();diagnostic();editor.setSelection(0);command("gotoError");settle();ready()
        assertEquals(editor.text.toString().indexOf("print"),editor.selectionStart);assertEquals(main.getString(R.string.editor_diagnostic_verified),field<TextView>("status").text.toString())
    }
    @Test fun changedRuntimeSourceDoesNotMoveCaretOrClaimVerifiedLocation(){
        edit("a = 1\nprint(a)\n");save();diagnostic(hash=DocumentTree.digest("old program"));editor.setSelection(1);command("gotoError");settle()
        assertEquals(1,editor.selectionStart);assertEquals(main.getString(R.string.editor_diagnostic_changed),field<TextView>("status").text.toString())
    }
    @Test fun runtimeLocationRejectsForeignWorkspaceAndEscapingFile(){
        edit("a = 1\nprint(a)\n");save();editor.setSelection(1);val content=editor.text
        diagnostic(identity="saf:another");command("gotoError");assertEquals(1,editor.selectionStart);assertSame(content,editor.text)
        diagnostic(file="../outside.py");command("gotoError");settle();assertEquals(1,editor.selectionStart);assertSame(content,editor.text);assertEquals("main.py",field<String>("activePath"))
    }
    @Test fun runtimeDiagnosticInAnotherFileUsesExistingSwitchAndSaveProtection(){
        val helper=workspace.create(workspace.root,"helper.py",false);workspace.write(helper,"x = 1\nprint(unknown)\n")
        edit("print('active source')\n");diagnostic(path="helper.py",hash=DocumentTree.digest(helper.readText()));command("gotoError");settle();ready()
        assertEquals("helper.py",field<String>("activePath"));assertEquals(helper.readText().indexOf("print"),editor.selectionStart)
        assertEquals("print('active source')\n",workspace.resolve("main.py").readText());assertEquals(main.getString(R.string.editor_diagnostic_verified),field<TextView>("status").text.toString())
    }

    @Test fun openingAnEmptyProviderDirectoryShowsWelcomeWithoutCreatingOrWritingFiles(){
        val p=provider();p.empty=true
        ReflectionHelpers.callInstanceMethod<Unit>(main,"requestWorkspace",ClassParameter.from(String::class.java,p.tree.toString()));settle();layout()
        assertTrue(workspace.external);assertTrue(field("workspaceOpen"));assertEquals("",field<String>("activePath"));assertNull(field<String?>("loadedPath"))
        assertEquals("",editor.text.toString());assertTrue(workspace.children(workspace.root).isEmpty());assertFalse(p.written)
        val welcome=main.findViewById<View>(android.R.id.content).findViewWithTag<View>("workbench-welcome")
        assertNotNull(welcome);assertTrue(welcome.isShown)
        val identity=workspace.identity;controller.recreate();main=controller.get();settle();layout()
        assertEquals(identity,workspace.identity);assertTrue(workspace.external);assertTrue(field("workspaceOpen"));assertEquals("",editor.text.toString());assertTrue(workspace.children(workspace.root).isEmpty());assertFalse(p.written)
        assertEquals("print('provider original')\n",p.source.readText());assertEquals("answer = 42\n",p.helper.readText())
    }

    class Provider(val source:File,val helper:File):ContentProvider(){
        companion object {const val AUTHORITY="com.codepocket.ide.test.documents"}
        val tree:Uri get()=DocumentsContract.buildTreeDocumentUri(AUTHORITY,"root")
        var writable=true;var revoked=false;var mismatch=false;var written=false;var unreadable:String?=null;var empty=false
        private fun permission(){if(revoked)throw SecurityException("Provider permission revoked")}
        override fun onCreate()=true
        override fun getType(uri:Uri)="text/plain"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,order:String?):android.database.Cursor {
            permission();val columns=projection!!;val cursor=MatrixCursor(columns)
            val children=uri.lastPathSegment=="children";val id=if(children)"root" else DocumentsContract.getDocumentId(uri)
            for(doc in if(children){if(empty)emptyList()else listOf("opaque:file:1","opaque:file:2")} else listOf(id))cursor.addRow(columns.map{column->when(column){
                Document.COLUMN_DOCUMENT_ID->doc
                Document.COLUMN_DISPLAY_NAME->when(doc){"root"->"授权测试项目";"opaque:file:1"->"external.py";else->"helper.py"}
                Document.COLUMN_MIME_TYPE->if(doc=="root")Document.MIME_TYPE_DIR else "text/x-python"
                Document.COLUMN_FLAGS->if(writable)Document.FLAG_SUPPORTS_WRITE else 0
                else->null
            }}.toTypedArray());return cursor
        }
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            permission();val id=DocumentsContract.getDocumentId(uri);val target=if(id=="opaque:file:1")source else helper
            if(id==unreadable)throw FileNotFoundException("Provider read failed")
            if(mode.contains('w')){if(!writable)throw SecurityException("Read-only provider");written=true
                return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)}
            val read=if(mismatch&&written&&target==source)File(source.parentFile,"mismatch.txt").apply{writeText("truncated provider readback")}else target
            return ParcelFileDescriptor.open(read,ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri:Uri,values:ContentValues?):Uri=throw UnsupportedOperationException()
        override fun delete(uri:Uri,where:String?,args:Array<out String>?)=throw UnsupportedOperationException()
        override fun update(uri:Uri,values:ContentValues?,where:String?,args:Array<out String>?)=throw UnsupportedOperationException()
    }
}
