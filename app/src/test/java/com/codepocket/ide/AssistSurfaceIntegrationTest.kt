package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.*
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
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Real Main/Sora assistance and editor transactions, not a semantic-server or device test. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class AssistSurfaceIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun idle(ms:Long=0){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real Sora layout unavailable")}
    private fun layout(){repeat(2){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun text(value:String){editor.setText(value);ready();editor.setSelection(value.length);editor.finishMotion()}
    private fun invoke(method:String){ReflectionHelpers.callInstanceMethod<Unit>(main,method);idle()}
    private fun assist(method:String,worker:String){ReflectionHelpers.callInstanceMethod<Unit>(main,if(method=="updateSuggestions")"requestCompletion" else "requestSignatureHelp");field<ExecutorService>(worker).submit{}.get(10,TimeUnit.SECONDS);idle(80)}
    private fun key(code:Int,meta:Int=0){main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0,meta));main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,code,0,meta));idle()}
    private fun popup()=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow!!
    private fun row(list:ListView,index:Int,width:Int):View=list.adapter.getView(index,null,list).apply{measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));layout(0,0,measuredWidth,measuredHeight)}
    private fun words(v:View)=views(v).filterIsInstance<TextView>().filter{it.visibility==View.VISIBLE}.map{it.text.toString()}
    private fun capture(tag:String,v:View){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(DesignTokens.forContext(main).overlay);v.draw(c)
        val cwd=File(System.getProperty("user.dir")!!);val r=if(cwd.name=="app")cwd.parentFile else cwd;val dir=File(r,"verification/workbench-assist-batch-2026-10-26/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()}
    private fun snippets():AlertDialog {invoke("showSnippetMenu");return ShadowAlertDialog.getLatestAlertDialog()}
    @Before fun launch(){RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();assertTrue(editor.requestFocus());ready()}
    @After fun close(){if(::main.isInitialized){Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow?.dismiss();ShadowAlertDialog.getLatestAlertDialog()?.takeIf{it.isShowing}?.dismiss();main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}

    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletCompletionSeparatesRealCandidateKindsAndKeepsArrowTabInsertion(){
        text("project_value=1\np");val buffer=editor.text;assist("updateSuggestions","completionExecutor");val help=field<CompletionPopup>("completionPopup");assertTrue(help.isShowing)
        val batch=field<CompletionBatch>("completionBatch");val list=views(popup().contentView).filterIsInstance<ListView>().single()
        for(kind in listOf(CompletionKind.KEYWORD,CompletionKind.BUILTIN,CompletionKind.IDENTIFIER)){
            val index=batch.items.indexOfFirst{it.kind==kind};assertTrue("Real provider should offer $kind",index>=0)
            assertTrue("Candidate row exposes its actual kind",words(row(list,index,popup().width)).contains(kind.title))
        }
        capture("tablet-completion-kinds",popup().contentView);val index=batch.items.indexOfFirst{it.label=="print"};assertTrue(index>=0)
        repeat(index){key(KeyEvent.KEYCODE_DPAD_DOWN);assertTrue("Navigation keeps the actual popup",help.isShowing)};assertEquals(index,help.selectedIndex);key(KeyEvent.KEYCODE_TAB);assertEquals("project_value=1\nprint",editor.text.toString());assertSame(buffer,editor.text);assertTrue(editor.hasFocus())
    }
    @Test @Config(qualifiers="w411dp-h1000dp") fun phoneLightLargeCompletionKeepsTheNameAndFitsItsKindWhenThereIsRoom(){
        text("p");assist("updateSuggestions","completionExecutor");val help=field<CompletionPopup>("completionPopup");assertTrue(help.isShowing)
        val batch=field<CompletionBatch>("completionBatch");val list=views(popup().contentView).filterIsInstance<ListView>().single();val index=batch.items.indexOfFirst{it.kind==CompletionKind.KEYWORD};assertTrue(index>=0)
        val candidate=row(list,index,popup().width);assertTrue(words(candidate).contains(CompletionKind.KEYWORD.title));assertTrue(words(candidate).contains(batch.items[index].label));assertTrue(candidate.height>=48)
        for(label in views(candidate).filterIsInstance<TextView>().filter{it.visibility==View.VISIBLE})assertTrue(label.height>=label.lineHeight+label.compoundPaddingTop+label.compoundPaddingBottom)
        capture("phone-light-large-completion",popup().contentView);key(KeyEvent.KEYCODE_ESCAPE);assertFalse(help.isShowing);assertEquals("p",editor.text.toString());assertTrue(editor.hasFocus())
    }
    @Test @Config(qualifiers="w411dp-h1000dp") fun phoneLargeSingleSignatureHidesRedundantCycleButtonsAndKeepsTheEditor(){
        text("print(");val buffer=editor.text;assist("updateSignatureHelp","signatureExecutor");val help=field<SignaturePopup>("signaturePopup");assertTrue(help.isShowing)
        val all=views(popup().contentView);for(id in listOf(R.string.signature_previous,R.string.signature_next))assertFalse("A single reference form needs no cycle button",all.single{it.contentDescription==main.getString(id)}.isShown)
        assertTrue(all.filterIsInstance<TextView>().any{it.text.toString()==main.getString(R.string.signature_python_notice)})
        capture("phone-large-single-signature",popup().contentView);all.single{it.contentDescription==main.getString(R.string.signature_close)}.performClick();idle();assertFalse(help.isShowing);assertSame(buffer,editor.text);assertTrue(editor.hasFocus())
    }
    @Test @Config(qualifiers="w360dp-h1000dp") fun phoneLightLargeMultipleReferencesRemainNavigableAndVisiblyScrollable(){
        text("range(1, 10, ");val buffer=editor.text;assist("updateSignatureHelp","signatureExecutor");val help=field<SignaturePopup>("signaturePopup");assertTrue(help.isShowing)
        val all=views(popup().contentView);val next=all.single{it.contentDescription==main.getString(R.string.signature_next)};assertTrue(next.isShown);assertTrue(next.width>=48)
        next.performClick();idle(32);popup().contentView.apply{measure(View.MeasureSpec.makeMeasureSpec(popup().width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(popup().height,View.MeasureSpec.EXACTLY));layout(0,0,measuredWidth,measuredHeight)};idle();val scroll=all.filterIsInstance<HorizontalScrollView>().single()
        assertTrue("Long reference advertises horizontal scrolling",scroll.isHorizontalScrollBarEnabled);assertTrue("Reference width ${scroll.getChildAt(0).width} must exceed ${scroll.width}: ${words(scroll)}",scroll.getChildAt(0).width>scroll.width)
        assertTrue(all.filterIsInstance<TextView>().any{it.text.toString().contains("step")});assertTrue("After real child layout the current parameter is scrolled into view",scroll.scrollX>0);capture("phone-light-large-multiple-reference",popup().contentView);assertEquals("range(1, 10, ",editor.text.toString());assertSame(buffer,editor.text);assertTrue(editor.hasFocus())
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletSnippetTriggerAndExplanationRemainSeparateWithRealInsertionNavigationAndUndo(){
        text("");val buffer=editor.text;val d=snippets();assertTrue(d.isShowing);assertEquals(6,d.listView.adapter.count)
        val row=d.listView.adapter.getView(0,null,d.listView);assertTrue(words(row).contains("for"));assertTrue(words(row).contains("遍历循环"));capture("tablet-snippet-picker",d.window!!.decorView)
        d.listView.performItemClick(row,0,0);idle();ready();assertEquals("for item in range(10):\n    pass",editor.text.toString());assertSame(buffer,editor.text)
        assertEquals("item",editor.text.toString().substring(editor.selectionStart,editor.selectionEnd));key(KeyEvent.KEYCODE_TAB);assertEquals("range(10)",editor.text.toString().substring(editor.selectionStart,editor.selectionEnd))
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);assertEquals("",editor.text.toString());assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLightLargeActiveSnippetShowsItsLanguageAndTouchNavigationDoesNotEdit(){
        text("");var d=snippets();d.listView.performItemClick(d.listView.adapter.getView(0,null,d.listView),0,0);idle();ready();assertTrue(editor.requestFocus())
        val buffer=editor.text;val text=buffer.toString();d=snippets();assertEquals(3,d.listView.adapter.count)
        assertTrue("Snippet scope states the actual language",views(d.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()=="Python · 内置模板"})
        capture("phone-light-large-snippet-navigation",d.window!!.decorView);d.listView.performItemClick(d.listView.adapter.getView(1,null,d.listView),1,1);idle()
        assertEquals("range(10)",buffer.toString().substring(editor.selectionStart,editor.selectionEnd));assertSame(buffer,editor.text);assertEquals(text,buffer.toString())
    }
}
