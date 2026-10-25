package com.codepocket.ide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.IdeControls
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit

/** Host constraints + dispatched API34 Insets on actual Main/Sora; no real IME/device claim. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class,CompactCaptionWindowRoot::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class CompactCaptionsIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val caption get()=field<TextView?>("pathBreadcrumb")!!
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Actual Sora layout unavailable")}
    private fun evidenceRoot():File {val cwd=File(System.getProperty("user.dir")!!);return File(if(cwd.name=="app")cwd.parentFile else cwd,"verification/workbench-compact-captions-2026-10-26").apply{mkdirs()}}
    private fun viewport(height:Int,visible:Boolean=false,bottom:Int=0){
        val content=main.findViewById<View>(android.R.id.content);val root=field<View>("root")
        val insets=WindowInsets.Builder().setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,IdeControls.dp(main,bottom))).setVisible(WindowInsets.Type.ime(),visible).build()
        // Constrain the real window before draining host traversals; measuring only its child
        // is overwritten by Robolectric's next ViewRoot traversal. No installed IME is claimed.
        val width=main.resources.displayMetrics.widthPixels;val pixels=IdeControls.dp(main,height)
        CompactCaptionWindowRoot.supplied=insets
        main.window.setLayout(width,pixels)
        repeat(3){
            idle();content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY))
            content.layout(0,0,width,pixels)
        };ready()
        // Supply Insets after the host's unoccluded traversal, then measure the actual Views.
        root.dispatchApplyWindowInsets(insets)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"updateResponsiveLayout")
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY))
        content.layout(0,0,width,pixels)
        assertEquals(height,(root.height/main.resources.displayMetrics.density).toInt())
        assertEquals(visible&&bottom>80,field<Boolean>("imeVisible"))
    }
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        viewport(main.resources.configuration.screenHeightDp);editor.requestFocus();ready()
    }
    @After fun close(){if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    private fun record(tag:String){
        val row=ReflectionHelpers.callInstanceMethod<Int>(editor.inputView(),"getRowHeight")
        File(evidenceRoot(),"metrics").apply{mkdirs()}.resolve("$tag.json").writeText(org.json.JSONObject().put("heightDp",(field<View>("root").height/main.resources.displayMetrics.density).toInt())
            .put("editorHeightPx",editor.height).put("rowHeightPx",row).put("captionVisibility",caption.visibility).put("captionHeightPx",caption.height)
            .put("imeOccludes",field<Boolean>("imeVisible")).put("panelSelected",field<Boolean>("panelVisible"))
            .put("editorFocused",editor.hasFocus()).put("focusView",main.currentFocus?.javaClass?.simpleName?:"none").toString())
        val v=main.findViewById<View>(android.R.id.content);val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        File(evidenceRoot(),"previews").apply{mkdirs()}.resolve("$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    private fun compact(){
        assertEquals(View.GONE,caption.visibility);assertEquals(View.GONE,field<View>("statusBarComponent").visibility)
        val row=ReflectionHelpers.callInstanceMethod<Int>(editor.inputView(),"getRowHeight");assertTrue(row>0)
        assertTrue("two actual Sora rows must fit: editor=${editor.height}, row=$row",editor.height>=2*row)
    }
    @Test @Config(qualifiers="w840dp-h240dp") fun phoneLandscapeLargeReclaimsCaptionSpaceForInput(){
        viewport(240,true,300);record("phone-large-short-input");compact()
        val buffer=editor.text;val original=buffer.toString();editor.setSelection(original.length)
        assertTrue(editor.onCreateInputConnection(EditorInfo()).commitText("# compact input\n",1));idle();ready();viewport(240,true,300)
        assertSame(buffer,editor.text);assertEquals(original+"# compact input\n",editor.text.toString());assertTrue(editor.hasFocus())
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightLargeRestoresTheSameCaptionBufferAndPanelChoice(){
        val view=caption;val content=editor.text;editor.setSelection(editor.length());val selected=field<ShellSessionRegistry>("terminalRegistry").catalog.selected();assertNotNull(selected)
        val id=field<EditorSessions>("sessions").file("main.py").id;val panel=field<Boolean>("panelVisible");assertTrue(panel)
        val original=content.toString();viewport(240,true,300);record("tablet-light-large-short-input");compact();editor.requestFocus();assertTrue(editor.hasFocus())
        assertTrue(editor.onCreateInputConnection(EditorInfo()).commitText("# keep selection\n",1));idle();ready()
        main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_S,0,KeyEvent.META_CTRL_ON));drain();ready()
        viewport(800,false);record("tablet-light-large-restored")
        assertSame(view,caption);assertEquals(View.VISIBLE,caption.visibility);assertSame(content,editor.text);assertEquals(original+"# keep selection\n",editor.text.toString())
        assertEquals(editor.text.toString(),field<Workspace>("workspace").resolve("main.py").readText());assertFalse(field("dirty"));assertTrue("Explicitly selected editor keeps focus after input, save and restoration",editor.hasFocus())
        // The separate passive-transition observation is retained in known-issues.json.
        assertEquals(id,field<EditorSessions>("sessions").file("main.py").id);assertEquals(panel,field<Boolean>("panelVisible"));assertSame(selected,field<ShellSessionRegistry>("terminalRegistry").catalog.selected())
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightLargeFloatingInsetsKeepTheCaptionAndStartupPanel(){
        val before=caption.height;val content=editor.text;val selected=field<ShellSessionRegistry>("terminalRegistry").catalog.selected();assertNotNull(selected)
        viewport(800,true,0);record("tablet-light-large-floating-insets")
        assertEquals(View.VISIBLE,caption.visibility);assertEquals(before,caption.height);assertSame(content,editor.text);assertTrue(editor.hasFocus())
        assertTrue(field("panelVisible"));assertEquals(.35f,field<PanelState>("panelState").bottomFraction,0f);assertSame(selected,field<ShellSessionRegistry>("terminalRegistry").catalog.selected())
    }
    @Test @Config(qualifiers="w600dp-h600dp") fun netHeightBoundaryRestoresContextWithoutReplacingTheDocument(){
        val view=caption;val content=editor.text;ReflectionHelpers.callInstanceMethod<Unit>(main,"togglePanel");viewport(359);record("medium-height-359");compact()
        viewport(360);record("medium-height-360");assertEquals(View.VISIBLE,caption.visibility);assertEquals(View.VISIBLE,field<View>("statusBarComponent").visibility)
        assertSame(view,caption);assertSame(content,editor.text);assertTrue(editor.hasFocus())
    }
}

/** Keep the supplied API34 Insets consistent across host ViewRoot traversals, without an IME app. */
@Implements(className="android.view.ViewRootImpl",isInAndroidSdk=false)
class CompactCaptionWindowRoot:org.robolectric.shadows.ShadowViewRootImpl(){
    companion object {
        var supplied:WindowInsets?=null
        @JvmStatic @Resetter fun clearSupplied(){supplied=null}
    }
    @Implementation public override fun getWindowInsets(forceConstruct:Boolean):WindowInsets = supplied?:super.getWindowInsets(forceConstruct)
}
