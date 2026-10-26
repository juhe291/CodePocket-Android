package com.codepocket.ide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.DesignTokens
import org.json.JSONObject
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
import java.util.concurrent.*

/** Actual Main/Sora transactions; only Android IPC is replaced by recorded REAL backend responses. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class PythonSemanticIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private lateinit var transport:RecordedTransport
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(4){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real Sora layout unavailable")}
    private fun setCase(tag:String){val f=SemanticFixtures.case(tag);editor.setText(f.getString("code"));ready();editor.setSelection(f.getInt("caret"));editor.finishMotion();idle()}
    private fun invoke(method:String){ReflectionHelpers.callInstanceMethod<Unit>(main,method);idle()}
    private fun waitFor(worker:String){field<ExecutorService>(worker).submit{}.get(10,TimeUnit.SECONDS);idle()}
    private fun request(signature:Boolean=false,waitSemantic:Boolean=true){invoke(if(signature)"requestSignatureHelp" else "requestCompletion");waitFor(if(signature)"signatureExecutor" else "completionExecutor");if(waitSemantic)waitFor("semanticExecutor");ready();idle()}
    private fun key(code:Int,meta:Int=0){main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0,meta));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,code,0,meta));idle()}
    private fun capture(tag:String,v:View){idle();val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(DesignTokens.forContext(main).overlay);v.draw(c)
        val phase=if(tag in setOf("tablet-unsaved-project","phone-project-signature","tablet-saf-live-module"))"python-project-context-2026-10-26" else "python-semantic-integration-2026-10-26"
        val dir=File(SemanticFixtures.root,"verification/$phase/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private class RecordedTransport:SemanticTransport {
        @Volatile var closed=false
        @Volatile var blocked=false
        @Volatile var fail=false
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val requests=java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        override fun analyze(payload:String):String{
            val request=JSONObject(payload);requests.add(request)
            if(blocked){entered.countDown();var done=false;while(!done)try{release.await();done=true}catch(_:InterruptedException){/* late IPC already in flight */}}
            if(fail)return JSONObject().put("available",false).put("engine",SemanticProtocol.ENGINE).put("error","host transport failure fixture").toString()
            val project=ProjectSemanticFixtures.cases.values.firstOrNull{it.getString("code")==request.getString("code") && it.getString("mode")==request.getString("mode")}
            if(project!=null){
                assertEquals(project.getString("path"),request.getString("path"));assertTrue(request.getBoolean("project"))
                val expected=project.getJSONObject("content").let{obj->obj.keys().asSequence().associateWith{obj.getString(it)}}
                assertEquals("Actual production snapshot must equal the files analyzed by real Jedi",expected,PythonSources.local(File(request.getString("root"))))
                return project.getString("response")
            }
            val tags=listOf("list","json","instance","module","middle","unicode","signature","shadowed","unknown","prefix","snippet")
            val match=tags.map{SemanticFixtures.case(it)}.firstOrNull{it.getString("code")==request.getString("code") && it.getString("mode")==request.getString("mode")}
            return match?.getString("response")?:JSONObject().put("available",false).put("error","unrecorded host request").toString()
        }
        override fun close(){closed=true;release.countDown()}
    }
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val content=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(2){content.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));content.layout(0,0,content.measuredWidth,content.measuredHeight);idle()}
        assertTrue(editor.requestFocus());ready();transport=RecordedTransport();field<PythonSemanticClient?>("semanticClient")?.close()
        ReflectionHelpers.setField(main,"semanticClient",PythonSemanticClient(transport))
    }
    @After fun close(){if(::transport.isInitialized)transport.release.countDown();if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletListMemberAcceptsSavesAndUndoesThroughTheRealEditor(){
        setCase("list");val buffer=editor.text;request();val batch=field<CompletionBatch>("completionBatch")
        assertEquals(SemanticProtocol.ENGINE,batch.source);assertEquals("append",batch.items.single().label);assertEquals(CompletionKind.FUNCTION,batch.items.single().kind)
        val popup=field<CompletionPopup>("completionPopup");capture("tablet-real-list-completion",ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup").contentView)
        key(KeyEvent.KEYCODE_TAB);assertTrue(editor.text.toString().endsWith("items.append"));assertSame(buffer,editor.text)
        key(KeyEvent.KEYCODE_S,KeyEvent.META_CTRL_ON);drain();val workspace=field<Workspace>("workspace");val saved=workspace.read(workspace.resolve("main.py"));assertEquals(editor.text.toString(),saved)
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals(SemanticFixtures.case("list").getString("code"),editor.text.toString());assertEquals(saved,workspace.read(workspace.resolve("main.py")));assertTrue(field("dirty"))
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLightLargeActualFunctionParametersKeepEditorSelection(){
        setCase("signature");val buffer=editor.text;val caret=editor.selectionStart;request(true)
        val popup=field<SignaturePopup>("signaturePopup");assertTrue(popup.isShowing);val result=ReflectionHelpers.getField<SignatureResult>(popup,"result")
        assertTrue(result.reference.semantic);assertEquals("combine",result.reference.name);assertEquals(1,result.active(0));assertTrue(result.reference.forms.single().parts.first().contains("left"))
        capture("phone-light-large-real-signature",ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup").contentView)
        assertSame(buffer,editor.text);assertEquals(caret,editor.selectionStart);key(KeyEvent.KEYCODE_ESCAPE);assertFalse(popup.isShowing)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun customPrintDefinitionReplacesTheCuratedReference(){
        setCase("shadowed");request(true);val popup=field<SignaturePopup>("signaturePopup");assertTrue(popup.isShowing)
        val result=ReflectionHelpers.getField<SignatureResult>(popup,"result");assertTrue(result.reference.semantic)
        assertEquals(listOf("custom: str"),result.reference.forms.single().parts);assertEquals(0,result.active(0))
    }
    @Test @Config(qualifiers="w800dp-h1200dp") fun tabletLightProjectModuleUsesTheActualPrivateWorkspaceContext(){
        val workspace=field<Workspace>("workspace");val sibling=workspace.create(workspace.root,"sibling.py",false);workspace.write(sibling,"class Token:\n    def run(self): pass\n")
        setCase("module");request();val batch=field<CompletionBatch>("completionBatch");assertTrue(batch.items.any{it.label=="run"});assertEquals(SemanticProtocol.ENGINE,batch.source)
        val payload=transport.requests.last{it.optString("mode")=="complete"};assertTrue(payload.getBoolean("project"));assertNotEquals(workspace.root.path,payload.getString("root"));assertEquals("main.py",payload.getString("path"))
        assertEquals(sibling.readText(),File(payload.getString("root"),"sibling.py").readText())
        capture("tablet-light-project-member",ReflectionHelpers.getField<android.widget.PopupWindow>(field<CompletionPopup>("completionPopup"),"popup").contentView)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun lateResponseCannotChangeTheNewFileOrReopenItsOldCandidateList(){
        val workspace=field<Workspace>("workspace");val other=workspace.create(workspace.root,"other.py",false);workspace.write(other,"other_value=7\n")
        transport.blocked=true;setCase("list");request(waitSemantic=false);assertTrue(transport.entered.await(3,TimeUnit.SECONDS))
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,"other.py"),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null))
        drain();ready();assertEquals("other.py",field<String>("activePath"));val buffer=editor.text;val text=editor.text.toString()
        transport.release.countDown();waitFor("semanticExecutor");assertSame(buffer,editor.text);assertEquals(text,editor.text.toString());assertFalse(field<CompletionPopup?>("completionPopup")?.isShowing==true)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun aKeyboardChoiceIsNotReplacedByALateSemanticUpgrade(){
        transport.blocked=true;setCase("prefix");request(waitSemantic=false);assertTrue(transport.entered.await(3,TimeUnit.SECONDS))
        val batch=field<CompletionBatch>("completionBatch");assertNull(batch.source);val popup=field<CompletionPopup>("completionPopup");popup.move(1);val index=popup.selectedIndex
        transport.release.countDown();waitFor("semanticExecutor");assertSame(batch,field<CompletionBatch>("completionBatch"));assertEquals(index,popup.selectedIndex);assertTrue(popup.userInteracted)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun failuresKeepLexicalResultsAndUnknownMembersRemainEmpty(){
        transport.fail=true;setCase("prefix");request();assertNull(field<CompletionBatch>("completionBatch").source);assertTrue(field<CompletionPopup>("completionPopup").isShowing)
        transport.fail=false;setCase("unknown");request();assertNull(field<CompletionBatch?>("completionBatch"));assertFalse(field<CompletionPopup?>("completionPopup")?.isShowing==true)
        assertEquals(SemanticFixtures.case("unknown").getString("code"),editor.text.toString())
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun anExactSnippetTriggerKeepsItsRealInsertionFieldsAndUndo(){
        setCase("snippet");request();val batch=field<CompletionBatch>("completionBatch")
        assertEquals("py.for",batch.items.first().snippetId);val buffer=editor.text
        key(KeyEvent.KEYCODE_TAB);assertEquals("for item in range(10):\n    pass",editor.text.toString())
        assertEquals("item",editor.text.toString().substring(editor.selectionStart,editor.selectionEnd));assertSame(buffer,editor.text)
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals("for",editor.text.toString());assertSame(buffer,editor.text)
    }

    private fun prepareProject(tag:String){
        val workspace=field<Workspace>("workspace");val sessions=field<EditorSessions>("sessions");val drafts=field<DraftStore>("drafts")
        val f=ProjectSemanticFixtures.cases.getValue(tag);val content=f.getJSONObject("content")
        content.keys().asSequence().filter{it!="main.py"}.forEach{path->
            val file=workspace.create(workspace.root,path,false);workspace.write(file,"stale_disk_value=1\n")
            drafts.put(sessions.key(path),path,content.getString(path),DocumentTree.digest(file.readText()))
        }
        editor.setText(f.getString("code"));ready();editor.setSelection(f.getInt("caret"));editor.finishMotion();idle()
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletSeveralUnsavedModulesReachRealMembersWithoutSavingAnyOriginal(){
        val workspace=field<Workspace>("workspace");val original=workspace.read(workspace.resolve("main.py"));prepareProject("unsaved_project")
        val buffer=editor.text;request();val batch=field<CompletionBatch>("completionBatch")
        assertEquals(SemanticProtocol.ENGINE,batch.source);assertFalse(batch.currentFileOnly);assertTrue(batch.items.any{it.label=="upper"});assertFalse(batch.items.any{it.label=="bit_count"})
        assertEquals("stale_disk_value=1\n",workspace.read(workspace.resolve("sibling.py")));assertEquals(original,workspace.read(workspace.resolve("main.py")))
        key(KeyEvent.KEYCODE_TAB);assertEquals("from api import payload\npayload.upper",editor.text.toString());assertSame(buffer,editor.text)
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals(ProjectSemanticFixtures.cases.getValue("unsaved_project").getString("code"),editor.text.toString())
        val popup=ReflectionHelpers.getField<android.widget.PopupWindow>(field<CompletionPopup>("completionPopup"),"popup")
        // Export the actual native candidate view with the real backend's project result.
        request();capture("tablet-unsaved-project",popup.contentView)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneUnsavedCrossFileSignatureUsesTheActualSecondParameter(){
        prepareProject("project_signature");request(signature=true);val result=ReflectionHelpers.getField<SignatureResult>(field<SignaturePopup>("signaturePopup"),"result")
        assertTrue(result.reference.semantic);assertEquals(listOf("left: int","right: str"),result.reference.forms.single().parts);assertEquals(1,result.active(0));assertFalse(result.reference.currentFileOnly)
        capture("phone-project-signature",ReflectionHelpers.getField<android.widget.PopupWindow>(field<SignaturePopup>("signaturePopup"),"popup").contentView)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun changingAnImportedDiskModuleWhileAnalyzingDiscardsTheOldMembers(){
        setCase("module");val workspace=field<Workspace>("workspace");val sibling=workspace.create(workspace.root,"sibling.py",false);workspace.write(sibling,"class Token:\n    def run(self): pass\n")
        transport.blocked=true;request(waitSemantic=false);assertTrue(transport.entered.await(3,TimeUnit.SECONDS));workspace.write(sibling,"class Token:\n    def save(self): pass\n")
        transport.release.countDown();waitFor("semanticExecutor");assertFalse(field<CompletionBatch?>("completionBatch")?.source==SemanticProtocol.ENGINE);assertEquals(SemanticFixtures.case("module").getString("code"),editor.text.toString())
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun changingAnotherUnsavedDraftRejectsItsLateSignature(){
        prepareProject("project_signature");transport.blocked=true;request(signature=true,waitSemantic=false);assertTrue(transport.entered.await(3,TimeUnit.SECONDS))
        val sessions=field<EditorSessions>("sessions");field<DraftStore>("drafts").put(sessions.key("sibling.py"),"sibling.py","def changed(): pass\n")
        transport.release.countDown();waitFor("semanticExecutor");val result=field<SignaturePopup?>("signaturePopup")?.let{ReflectionHelpers.getField<SignatureResult?>(it,"result")};assertFalse(result?.reference?.semantic==true)
    }
    @Test @Config(qualifiers="w800dp-h1200dp") fun tabletSafReadsTheLiveProviderInsteadOfItsOldMirrorAndRevocationFallsBack(){
        val folder=File(main.filesDir,"semantic-provider").apply{mkdirs()}
        val p=MainSafRecoveryIntegrationTest.Provider(File(folder,"source.py").apply{writeText("print('provider original')\n")},File(folder,"helper.py").apply{writeText("payload=1\n")})
        p.attachInfo(main,android.content.pm.ProviderInfo().apply{authority=MainSafRecoveryIntegrationTest.Provider.AUTHORITY})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(MainSafRecoveryIntegrationTest.Provider.AUTHORITY,p)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"requestWorkspace",ClassParameter.from(String::class.java,p.tree.toString()));drain();ready()
        val workspace=field<Workspace>("workspace");assertTrue(workspace.external);assertEquals("payload=1\n",File(workspace.root,"helper.py").readText())
        p.helper.writeText("payload='live provider'\n");val f=ProjectSemanticFixtures.cases.getValue("fresh_provider")
        editor.setText(f.getString("code"));ready();editor.setSelection(f.getInt("caret"));assertTrue(editor.requestFocus());editor.finishMotion();idle();request()
        assertEquals(SemanticProtocol.ENGINE,field<CompletionBatch>("completionBatch").source);assertTrue(field<CompletionBatch>("completionBatch").items.any{it.label=="upper"})
        assertEquals("payload=1\n",File(workspace.root,"helper.py").readText());assertFalse(p.written);assertEquals("print('provider original')\n",p.source.readText())
        capture("tablet-saf-live-module",ReflectionHelpers.getField<android.widget.PopupWindow>(field<CompletionPopup>("completionPopup"),"popup").contentView)
        val count=transport.requests.size;p.revoked=true;request();assertEquals(count,transport.requests.size);assertFalse(field<CompletionBatch?>("completionBatch")?.source==SemanticProtocol.ENGINE);assertFalse(p.written)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun explicitOversizedMemberRequestExplainsItsLimitWithoutStartingTheBackend(){
        val text="#"+"x".repeat(45001)+"\nunknown."
        editor.setText(text);ready();editor.setSelection(text.length);idle();val count=transport.requests.size;request()
        assertEquals(count,transport.requests.size);assertEquals(main.getString(R.string.semantic_document_limit),field<android.widget.TextView>("status").text.toString())
    }
}

internal object ProjectSemanticFixtures {
    val cases:Map<String,JSONObject> by lazy {
        val json=JSONObject(File(SemanticFixtures.root,"verification/python-project-context-2026-10-26/semantic-fixtures.json").readText())
        check(json.getBoolean("real_backend") && !json.getBoolean("android_execution"))
        val bytes=File(SemanticFixtures.root,"app/src/main/python/semantics.py").readText().replace("\r\n","\n").toByteArray()
        check(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}==json.getString("backend_sha256"))
        json.getJSONObject("cases").let{obj->obj.keys().asSequence().associateWith{obj.getJSONObject(it)}}
    }
}
