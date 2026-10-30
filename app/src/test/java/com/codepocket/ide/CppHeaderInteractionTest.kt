package com.codepocket.ide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
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
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration
import java.util.concurrent.*

/** Actual Main/Sora, input, save and undo. Android IPC replays recorded real clangd replies. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class CppHeaderInteractionTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private lateinit var transport:RecordedTransport
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(4){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Actual Sora layout unavailable")}
    private fun invoke(method:String){ReflectionHelpers.callInstanceMethod<Unit>(main,method);idle()}
    private fun worker(key:String){field<ExecutorService>(key).submit{}.get(15,TimeUnit.SECONDS);idle()}
    private fun settle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(180));worker("completionExecutor");worker("semanticExecutor");ready();idle()}
    private fun setCase(tag:String){val request=HeaderFixtures.request(tag);editor.setText(request.code);ready();editor.setSelection(request.caret);assertTrue(editor.revealCaret());editor.finishMotion();idle()}
    private fun request(){invoke("requestCompletion");worker("completionExecutor");worker("semanticExecutor");ready();idle()}
    private fun key(code:Int,meta:Int=0){main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0,meta));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,code,0,meta));idle()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun batch():CompletionBatch {assertTrue("Requests ${transport.requests}; text ${editor.text}",field<CompletionPopup?>("completionPopup")?.isShowing==true);return field("completionBatch")}
    private fun capture(tag:String){val v=ReflectionHelpers.getField<PopupWindow>(field<CompletionPopup>("completionPopup"),"popup").contentView
        assertTrue(v.width>0 && v.height>0);val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(DesignTokens.forContext(main).overlay);v.draw(c)
        val folder=File(CppFixtures.root,"verification/cpp-header-completion-2026-10-27/previews").apply{mkdirs()};File(folder,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
        assertTrue(views(v).filterIsInstance<TextView>().any{it.text.toString().startsWith("C++ 语义")})
    }
    private class RecordedTransport:SemanticTransport {
        @Volatile var blocked=false;@Volatile var empty=false
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val requests=java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        override fun analyze(payload:String):String {
            val data=JSONObject(payload);requests.add(data)
            if(blocked){entered.countDown();var done=false;while(!done)try{release.await();done=true}catch(_:InterruptedException){}}
            if(empty)return JSONObject().put("engine",CppSemanticProtocol.ENGINE).put("available",true).put("response",JSONObject().put("result",org.json.JSONArray())).toString()
            val tag=listOf("system","project","nested","middle","closed-system","directory","directory-next","system-nested","unicode-crlf","closed-project")
                .firstOrNull{HeaderFixtures.request(it).let{request->request.code==data.getString("code") && request.caret==data.getInt("caret")}}
                ?:return JSONObject().put("available",false).toString()
            val root=File(data.getString("root"));assertEquals("",File(root,"main.cpp").readText());assertEquals("main.cpp",data.getString("path"))
            assertEquals("struct Model {};\n",File(root,"model.hpp").readText());assertEquals("struct Sub {};\n",File(root,"sub/file.hpp").readText())
            return HeaderFixtures.envelope(tag).toString()
        }
        override fun close(){release.countDown()}
    }
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.cpp")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false,tabWidth=4,useSpaces=true).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val content=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(2){content.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));content.layout(0,0,content.measuredWidth,content.measuredHeight);idle()}
        val ws=field<Workspace>("workspace");ws.write(ws.create(ws.root,"model.hpp",false),"struct Model {};\n")
        val sub=ws.create(ws.root,"sub",true);ws.write(ws.create(sub,"file.hpp",false),"struct Sub {};\n")
        assertTrue(editor.requestFocus());ready();transport=RecordedTransport();field<CppSemanticClient?>("cppSemanticClient")?.close();ReflectionHelpers.setField(main,"cppSemanticClient",CppSemanticClient(transport))
    }
    @After fun close(){if(::transport.isInitialized)transport.release.countDown();if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLightLargeProjectHeaderAcceptsToolbarTabSavesAndUndoes(){
        setCase("project");val buffer=editor.text;request();assertEquals("头文件",batch().items.single().kind.title);capture("phone-light-large-header")
        val toolbar=field<View>("keyboardToolbar");toolbar.visibility=View.VISIBLE;toolbar.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(64,View.MeasureSpec.EXACTLY));toolbar.layout(0,48,360,112)
        val tab=field<View>("completionTabButton");val xy=IntArray(2);tab.getLocationOnScreen(xy);val event=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,xy[0]+tab.width/2f,xy[1]+tab.height/2f,0)
        try{assertTrue(tab.isShown);main.dispatchTouchEvent(event);assertNotNull(batch());tab.performClick()}finally{event.recycle()}
        assertSame(buffer,editor.text);assertEquals("#include \"model.hpp\"",editor.text.toString())
        key(KeyEvent.KEYCODE_S,KeyEvent.META_CTRL_ON);drain();val ws=field<Workspace>("workspace");assertEquals(editor.text.toString(),ws.read(ws.resolve("main.cpp")))
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals(HeaderFixtures.request("project").code,editor.text.toString());assertEquals("#include \"model.hpp\"",ws.read(ws.resolve("main.cpp")))
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletMiddlePathAndClosedSystemHeaderAcceptPhysicalTabWithoutDuplicateDelimiters(){
        setCase("middle");request();assertEquals("头文件",batch().items.single().kind.title);capture("tablet-dark-nested-header")
        key(KeyEvent.KEYCODE_TAB);assertEquals("#include \"sub/file.hpp\"\n",editor.text.toString());key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals(HeaderFixtures.request("middle").code,editor.text.toString())
        setCase("closed-system");request();val found=batch();val index=found.items.indexOfFirst{it.label=="vector"};assertTrue(index>=0);field<CompletionPopup>("completionPopup").move(index)
        key(KeyEvent.KEYCODE_TAB);assertEquals("#include <vector>\n",editor.text.toString());assertEquals(17,editor.selectionStart)
    }
    @Test @Config(qualifiers="w800dp-h1000dp") fun acceptingADirectoryAutomaticallyRequestsItsChildrenAndRetainsSeparateUndoSteps(){
        setCase("directory");request();val found=batch();val index=found.items.indexOfFirst{it.label=="sub/"};assertTrue(index>=0);field<CompletionPopup>("completionPopup").move(index);capture("tablet-dark-directory")
        key(KeyEvent.KEYCODE_TAB);assertEquals("#include \"sub/",editor.text.toString());settle();assertEquals("file.hpp",batch().items.single().label)
        assertTrue(transport.requests.any{it.getString("code")=="#include \"sub/"});key(KeyEvent.KEYCODE_TAB);assertEquals("#include \"sub/file.hpp\"",editor.text.toString())
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals("#include \"sub/",editor.text.toString());key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);ready();assertEquals(HeaderFixtures.request("directory").code,editor.text.toString())
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun delayedHeaderResultCannotEditOrReopenCandidatesAfterSwitchingFiles(){
        val ws=field<Workspace>("workspace");ws.write(ws.create(ws.root,"other.py",false),"answer=42\n")
        transport.blocked=true;setCase("project");invoke("requestCompletion");worker("completionExecutor");assertTrue(transport.entered.await(3,TimeUnit.SECONDS))
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,"other.py"),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));drain();ready()
        val text=editor.text.toString();transport.release.countDown();worker("semanticExecutor");assertEquals("other.py",field<String>("activePath"));assertEquals(text,editor.text.toString());assertFalse(field<CompletionPopup?>("completionPopup")?.isShowing==true)
    }
    @Test @Config(qualifiers="w411dp-h891dp") fun emptyHeaderResultsAndDisabledTabAcceptanceKeepTheOriginalIndentAction(){
        transport.empty=true;setCase("project");request();assertNull(field<CompletionBatch?>("completionBatch"))
        val original=editor.text.toString();key(KeyEvent.KEYCODE_TAB)
        assertEquals(original+"    ",editor.text.toString());assertEquals(original.length+4,editor.selectionStart)
        transport.empty=false;setCase("project");request();assertNotNull(batch());val settings=field<AppSettings>("settings");ReflectionHelpers.setField(main,"settings",settings.copy(completionTab=false))
        invoke("tab");assertFalse(editor.text.toString().contains("model.hpp"));assertEquals(HeaderFixtures.request("project").code+"    ",editor.text.toString())
    }
}
