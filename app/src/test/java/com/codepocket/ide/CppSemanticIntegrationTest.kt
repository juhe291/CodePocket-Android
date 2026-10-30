package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.util.concurrent.*

/** Actual Main/Sora and snapshot path. Android IPC is replaced by recorded REAL clangd responses. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class CppSemanticIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private lateinit var transport:RecordedTransport
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(4){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real Sora layout unavailable")}
    private fun setCase(tag:String){val f=CppFixtures.case(tag);editor.setText(f.getString("code"));ready();editor.setSelection(f.getInt("caret"));assertTrue(editor.revealCaret());editor.finishMotion();idle()}
    private fun invoke(method:String){ReflectionHelpers.callInstanceMethod<Unit>(main,method);idle()}
    private fun waitFor(worker:String){field<ExecutorService>(worker).submit{}.get(15,TimeUnit.SECONDS);idle()}
    private fun request(signature:Boolean=false,waitSemantic:Boolean=true){invoke(if(signature)"requestSignatureHelp" else "requestCompletion");waitFor(if(signature)"signatureExecutor" else "completionExecutor");if(waitSemantic)waitFor("semanticExecutor");ready();idle()}
    private fun key(code:Int,meta:Int=0){main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0,meta));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,code,0,meta));idle()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun capture(tag:String,v:View){idle();assertTrue(v.width>0 && v.height>0);val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(DesignTokens.forContext(main).overlay);v.draw(c)
        val dir=File(CppFixtures.root,"verification/cpp-semantic-integration-2026-10-27/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private class RecordedTransport:SemanticTransport {
        @Volatile var blocked=false; @Volatile var fail=false
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val requests=java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        override fun analyze(payload:String):String {
            val request=JSONObject(payload);requests.add(request)
            if(blocked){entered.countDown();var done=false;while(!done)try{release.await();done=true}catch(_:InterruptedException){}}
            if(fail)return JSONObject().put("available",false).put("engine",CppSemanticProtocol.ENGINE).put("error","host failure fixture").toString()
            val tags=listOf("dot","arrow","namespace","vector","unicode_crlf","middle","signature","overload","overload_arity","unsaved_header","updated_header","closed_header","scope","unknown","prefix")
            val tag=tags.firstOrNull{CppFixtures.case(it).getString("code")==request.getString("code") && CppFixtures.case(it).getString("mode")==request.getString("mode")}
                ?:return JSONObject().put("available",false).toString()
            val root=File(request.getString("root"));assertNotNull(root.parentFile)
            assertEquals("main.cpp",request.getString("path"));assertEquals("",File(root,"main.cpp").readText())
            val fixture=CppFixtures.case(tag)
            if(tag=="unsaved_header")assertEquals(fixture.getString("unsaved_header"),File(root,"model.hpp").readText())
            return CppFixtures.envelope(tag).toString()
        }
        override fun close(){release.countDown()}
    }
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.cpp")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val content=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(2){content.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));content.layout(0,0,content.measuredWidth,content.measuredHeight);idle()}
        assertTrue(editor.requestFocus());ready();transport=RecordedTransport();field<CppSemanticClient?>("cppSemanticClient")?.close()
        ReflectionHelpers.setField(main,"cppSemanticClient",CppSemanticClient(transport))
        assertEquals("main.cpp",field<String>("activePath"))
    }
    @After fun close(){if(::transport.isInitialized)transport.release.countDown();if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletRealMemberInsertionConsumesSuffixAndSavesThenUndoes(){
        setCase("middle");val buffer=editor.text;request();val batch=field<CompletionBatch>("completionBatch")
        assertEquals(CppSemanticProtocol.ENGINE,batch.source);assertEquals("run()",batch.items.single().semanticInsertion!!.change.text)
        val popup=field<CompletionPopup>("completionPopup");val view=ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup").contentView
        assertTrue(views(view).filterIsInstance<TextView>().any{it.text.toString()=="C++ 语义 · clangd 21.1.8"});capture("tablet-real-member",view)
        key(KeyEvent.KEYCODE_TAB);assertTrue(editor.text.toString().endsWith("item.run();}"));assertSame(buffer,editor.text)
        key(KeyEvent.KEYCODE_S,KeyEvent.META_CTRL_ON);drain();val workspace=field<Workspace>("workspace");val saved=workspace.read(workspace.resolve("main.cpp"));assertEquals(editor.text.toString(),saved)
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals(CppFixtures.request("middle").code,editor.text.toString());assertEquals(saved,workspace.read(workspace.resolve("main.cpp")));assertTrue(field("dirty"))
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLightLargeActualOverloadsKeepSelectionAndRealSecondParameter(){
        setCase("overload");val buffer=editor.text;val caret=editor.selectionStart;request(true)
        val popup=field<SignaturePopup>("signaturePopup");val result=ReflectionHelpers.getField<SignatureResult?>(popup,"result")
        assertTrue("Signature $result; actual requests ${transport.requests}; editor ${editor.width}x${editor.height}",popup.isShowing);assertNotNull(result)
        requireNotNull(result)
        assertTrue(result.reference.semantic && result.reference.cpp);assertEquals(2,result.reference.forms.size);assertEquals(1,result.active(0));assertEquals(1,result.active(1))
        val view=ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup").contentView
        assertTrue(views(view).filterIsInstance<TextView>().any{it.text.toString().startsWith("C++ 参数")});capture("phone-light-large-signature",view)
        assertSame(buffer,editor.text);assertEquals(caret,editor.selectionStart);key(KeyEvent.KEYCODE_ESCAPE);assertFalse(popup.isShowing)
    }
    @Test @Config(qualifiers="w800dp-h1200dp") fun tabletLightAndroidVectorKeepsUnicodeAndPlacesCaretInsideArguments(){
        setCase("unicode_crlf");val original=editor.text.toString();val buffer=editor.text;request()
        val batch=field<CompletionBatch?>("completionBatch");assertNotNull("Actual requests ${transport.requests}; editor text=${editor.text}",batch);requireNotNull(batch);assertEquals(2,batch.items.size)
        capture("tablet-light-vector",ReflectionHelpers.getField<android.widget.PopupWindow>(field<CompletionPopup>("completionPopup"),"popup").contentView)
        key(KeyEvent.KEYCODE_TAB);val text=editor.text.toString();assertTrue(text.contains("😀中文"));assertTrue(text.endsWith("items.push_back()"));assertEquals(text.length-1,editor.selectionStart)
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals(original,editor.text.toString());assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun unsavedHeaderReachesTheActualSnapshotAndDoesNotSaveOriginals(){
        val workspace=field<Workspace>("workspace");val header=workspace.create(workspace.root,"model.hpp",false);workspace.write(header,"struct Token { void old_disk(); };\n")
        val sessions=field<EditorSessions>("sessions");field<DraftStore>("drafts").put(sessions.key("model.hpp"),"model.hpp",CppFixtures.case("unsaved_header").getString("unsaved_header"),DocumentTree.digest(header.readText()))
        val original=workspace.read(workspace.resolve("main.cpp"));setCase("unsaved_header");request()
        val batch=field<CompletionBatch>("completionBatch");assertTrue(batch.items.any{it.label=="run()"});assertFalse(batch.items.any{it.label.contains("old_disk")})
        assertEquals("struct Token { void old_disk(); };\n",header.readText());assertEquals(original,workspace.read(workspace.resolve("main.cpp")))
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun lateCppResponseCannotChangeTheNewPythonFileOrReopenOldCandidates(){
        val workspace=field<Workspace>("workspace");val other=workspace.create(workspace.root,"other.py",false);workspace.write(other,"answer=42\n")
        transport.blocked=true;setCase("dot");request(waitSemantic=false);assertTrue(transport.entered.await(3,TimeUnit.SECONDS))
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,"other.py"),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null))
        drain();ready();assertEquals("other.py",field<String>("activePath"));val buffer=editor.text;val text=editor.text.toString()
        transport.release.countDown();waitFor("semanticExecutor");assertSame(buffer,editor.text);assertEquals(text,editor.text.toString());assertFalse(field<CompletionPopup?>("completionPopup")?.isShowing==true)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun aKeyboardChoiceSurvivesALateCppUpgradeAndFailuresKeepLexicalFallback(){
        transport.blocked=true;setCase("prefix");request(waitSemantic=false);assertTrue(transport.entered.await(3,TimeUnit.SECONDS))
        val batch=field<CompletionBatch>("completionBatch");assertNull(batch.source);val popup=field<CompletionPopup>("completionPopup");popup.move(1);val index=popup.selectedIndex
        transport.release.countDown();waitFor("semanticExecutor");assertSame(batch,field<CompletionBatch>("completionBatch"));assertEquals(index,popup.selectedIndex)
        transport.blocked=false;transport.fail=true;setCase("prefix");request();assertNull(field<CompletionBatch>("completionBatch").source)
        transport.fail=false;setCase("unknown");request();assertNull(field<CompletionBatch?>("completionBatch"));assertEquals(CppFixtures.request("unknown").code,editor.text.toString())
    }
    @Test @Config(qualifiers="w800dp-h1200dp") fun tabletLightCppLicenseReaderPreservesSettingsDraftAndFullSourceInstructions(){
        invoke("showSettings");val settings=field<AlertDialog>("toolDialog");views(settings.window!!.decorView).single{it.contentDescription=="帮助与许可"}.performClick();idle()
        val checkpoint=SettingsDialog.checkpoint(settings);views(settings.window!!.decorView).single{it.contentDescription=="C++ 语义分析许可与源码"}.performClick();idle()
        val picker=ShadowAlertDialog.getLatestAlertDialog();picker.listView.performItemClick(null,0,0);idle()
        val reader=ShadowAlertDialog.getLatestAlertDialog();val full=main.assets.open("cpp-licenses/README.txt").bufferedReader().use{it.readText()}
        val body=views(reader.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()==full};assertTrue(body.isTextSelectable);assertTrue(full.contains("assets/cpp-source/libiconv-1.19-source.tgz"))
        capture("tablet-light-cpp-license",reader.window!!.decorView);reader.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertTrue(picker.isShowing);picker.dismiss();assertTrue(settings.isShowing);assertEquals(checkpoint,SettingsDialog.checkpoint(settings))
    }
}
