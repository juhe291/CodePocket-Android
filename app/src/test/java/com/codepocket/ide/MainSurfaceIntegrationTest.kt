package com.codepocket.ide

import android.app.AlertDialog
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.editor.*
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
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

/** Normal main entry, actual menu/popup clicks and Sora. Diagnostic messages are explicitly UI fixtures. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h900dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class MainSurfaceIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val editor get()=field<WorkbenchEditor>("editor")
    private val port get()=field<SoraEditorPort>("editorPort")
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private fun invoke(key:String){ReflectionHelpers.callInstanceMethod<Unit>(main,key)}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun idle(ms:Long=0){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Real main layout unavailable")}
    private fun text(value:String){editor.setText(value);ready();editor.setSelection(value.length);editor.finishMotion()}
    private fun anchor()=views(main.findViewById(android.R.id.content)).single{it.contentDescription=="打开终端"}
    private fun popup()=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow!!
    private fun menuRow(window:PopupWindow,title:String)=views(window.contentView).single{it.contentDescription==title}
    private fun capture(tag:String,v:View){
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(b);canvas.drawColor(DesignTokens.forContext(v.context).overlay);v.draw(canvas);val cwd=File(System.getProperty("user.dir")!!);val root=if(cwd.name=="app")cwd.parentFile else cwd
        val dir=File(root,"verification/main-app-surfaces-2026-10-23/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false,
                autoCompletion=name.methodName.contains("Completion"),signatureHelp=name.methodName.contains("Signature")).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val root=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));root.layout(0,0,root.measuredWidth,root.measuredHeight);root.viewTreeObserver.dispatchOnGlobalLayout()
        editor.requestFocus();ready()
    }
    @After fun close(){popupOrNull()?.dismiss();main.finish();controller.pause().stop().destroy();drain();RuntimeEnvironment.setFontScale(1f)}
    private fun popupOrNull()=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow
    private fun assistance(method:String,worker:String){ReflectionHelpers.callInstanceMethod<Unit>(main,method,ClassParameter.from(Boolean::class.javaPrimitiveType,true));field<ExecutorService>(worker).submit{}.get(10,TimeUnit.SECONDS);idle(80)}
    private fun textFits(v:TextView){val l=v.layout!!;val lines=minOf(l.lineCount,v.maxLines);assertTrue("Text height ${v.height} clips ${l.getLineBottom(lines-1)}: ${v.text}",v.height>=l.getLineBottom(lines-1)+v.compoundPaddingTop+v.compoundPaddingBottom)}

    @Test fun emptyTerminalCloseMenuClosesPanelInsteadOfCreatingAnotherSession(){
        invoke("openTerminal");val registry=field<ShellSessionRegistry>("terminalRegistry");registry.catalog.entries().map{it.id}.forEach(registry::end)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showOutput",ClassParameter.from(Int::class.javaPrimitiveType,3));assertTrue(registry.catalog.entries().isEmpty());assertTrue(field("panelVisible"))
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showTerminalActions",ClassParameter.from(View::class.java,anchor()));val window=popup();menuRow(window,"收起运行面板").performClick();idle()
        assertTrue("Collapse must not invoke New Terminal",registry.catalog.entries().isEmpty());assertFalse(field("panelVisible"))
    }
    @Test fun dangerActionIsSeparatedOnBothSidesAndKeepsStableCommandIdentity(){
        val menu=WorkbenchMenu(main,anchor(),false);menu.menu.add(0,11,0,"保存");menu.menu.add(0,12,1,"删除");menu.menu.add(0,13,2,"导出");var selected=0
        menu.setOnMenuItemClickListener{selected=it.itemId;true};menu.show();val window=popup();val rows=(window.contentView as ScrollView).getChildAt(0) as LinearLayout
        assertEquals("Danger needs its own group before and after",5,rows.childCount);idle(80);capture("phone-grouped-menu",window.contentView);menuRow(window,"导出").performClick();assertEquals(13,selected)
    }
    @Test fun menuDisabledRowsDoNotDispatchAndEscapeKeepsTheDocument(){
        val menu=WorkbenchMenu(main,anchor(),false);menu.menu.add(0,1,0,"不可用").isEnabled=false;menu.menu.add(0,2,1,"可用");var calls=0
        menu.setOnMenuItemClickListener{calls++;true};val content=editor.text;menu.show();val window=popup();val disabled=menuRow(window,"不可用");assertFalse(disabled.isFocusable);disabled.performClick();assertEquals(0,calls)
        val active=menuRow(window,"可用");active.requestFocus();window.contentView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE));window.contentView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ESCAPE));assertFalse(window.isShowing);assertSame(content,editor.text)
    }
    @Test @Config(qualifiers="w411dp-h1000dp") fun LargeSignatureKeepsReadableNoticeReferenceAndControls(){
        text("print(");assistance("updateSignatureHelp","signatureExecutor");val hint=field<SignaturePopup>("signaturePopup");assertTrue(hint.isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(hint,"popup");val notice=ReflectionHelpers.getField<TextView>(hint,"disclaimer");val reference=ReflectionHelpers.getField<HorizontalScrollView>(hint,"scroll")
        textFits(notice);assertTrue("Scrollable signature is a touch target",reference.height>=48)
        val content=editor.text;views(window.contentView).filter{it.contentDescription in listOf(main.getString(R.string.signature_previous),main.getString(R.string.signature_next),main.getString(R.string.signature_close))}.forEach{assertTrue(it.width>=48);assertTrue(it.height>=48)}
        capture("phone-large-signature",window.contentView);assertTrue(editor.hasFocus());assertSame(content,editor.text)
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLargeLightCompletionKeepsDetailsAndKeyboardAcceptance(){
        text("pri");assistance("updateSuggestions","completionExecutor");val help=field<CompletionPopup>("completionPopup");assertTrue(help.isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(help,"popup");val details=ReflectionHelpers.getField<TextView>(help,"details");if(details.visibility==View.VISIBLE)textFits(details)
        capture("tablet-large-light-completion",window.contentView);main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB));main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB));assertEquals("print",editor.text.toString())
    }
    private fun diagnostic(message:String){text("abc");editor.setSelection(1);val frame=port.snapshot()!!
        assertTrue(port.applyDecorations(EditorAnalysis(frame,emptyList(),null,syntax=SyntaxReport(listOf(InlineIssue(0,3,message,"诊断布局验收"))))))
        editor.inputView().performClick();idle(80)
    }
    @Test @Config(qualifiers="w360dp-h290dp") fun tinyLargeDiagnosticOpensFullDetailsInsteadOfSilentlyDisappearing(){
        val message="布局验收消息\n".repeat(30);diagnostic(message);val d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull("No hover room must still offer details",d);assertTrue(d.isShowing)
        assertTrue(views(d.window!!.decorView).filterIsInstance<TextView>().any{it.text.contains(message)});assertEquals(1,editor.selectionStart)
        capture("phone-tiny-large-diagnostic-details",d.window!!.decorView);d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle()
    }
    @Test fun diagnosticHoverScrollsCompleteMessageAndCopiesWithoutEditing(){
        val message="诊断布局验收：这是一段供界面回归的长说明。\n".repeat(25);diagnostic(message);val help=field<DiagnosticPopup>("diagnosticPopup");assertTrue(help.isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(help,"popup");val body=views(window.contentView).filterIsInstance<TextView>().single{it.text.toString()==message}
        assertTrue("Full diagnostics should scroll",body.parent is ScrollView);val content=editor.text
        views(window.contentView).single{it.contentDescription=="复制错误信息"}.performClick();assertEquals(message,main.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString());assertSame(content,editor.text);assertTrue(editor.hasFocus())
    }
    @Test fun newFileValidationRetainsTheInputAndCurrentDocument(){
        val content=editor.text;ReflectionHelpers.callInstanceMethod<Unit>(main,"askCreate",ClassParameter.from(Boolean::class.javaPrimitiveType,false));val d=ShadowAlertDialog.getLatestAlertDialog()
        val input=views(d.window!!.decorView).filterIsInstance<EditText>().single();input.setText("../escape.py");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(d.isShowing);assertEquals("../escape.py",input.text.toString());assertNotNull(input.error);assertSame(content,editor.text);assertNull(input.backgroundTintList);idle(80)
        capture("phone-new-file-validation",d.window!!.decorView);d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle()
    }
    @Test fun gotoLineKeepsInvalidInputThenMovesToTheActualUtf16Offset(){
        text("first\nsecond");editor.setSelection(0);invoke("showGoToLine");val d=field<AlertDialog>("toolDialog");val input=views(d.window!!.decorView).filterIsInstance<EditText>().single();idle(80)
        input.setText("9999");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(d.isShowing);assertEquals("9999",input.text.toString());assertNotNull(input.error);assertEquals(0,editor.selectionStart)
        input.setText("2");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals("Immediate native selection",6,editor.selectionStart);idle();assertFalse(d.isShowing);assertEquals("Selection after queued callbacks",6,editor.selectionStart)
    }
    @Test fun projectSearchIncludesUnsavedBufferThroughTheRealWorkspaceWorker(){
        text("unsaved_buffer_match\n");invoke("showProjectSearch");val d=field<AlertDialog>("toolDialog");val all=views(d.window!!.decorView)
        all.filterIsInstance<EditText>().single().setText("unsaved_buffer_match");all.filterIsInstance<Button>().single{it.text.toString()=="搜索"}.performClick();drain()
        val list=all.filterIsInstance<ListView>().single();assertEquals(1,list.adapter.count);assertTrue(list.adapter.getItem(0).toString().contains("main.py:1"));assertTrue(field("dirty"));d.dismiss()
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLargeReplaceControlsStayReadableAndAllReplacementIsUndoable(){
        text("needle needle");ReflectionHelpers.callInstanceMethod<Unit>(main,"showSearch",ClassParameter.from(Boolean::class.javaPrimitiveType,true));val d=field<AlertDialog>("toolDialog");idle(80)
        val all=views(d.window!!.decorView);val query=all.single{it.contentDescription=="查找文本"} as EditText;val replace=all.single{it.contentDescription=="替换文本"} as EditText
        query.setText("needle");replace.setText("value");idle(80)
        val action=all.single{it.contentDescription=="替换全部匹配，可撤销"} as TextView;textFits(action);textFits(all.single{it.contentDescription=="匹配数量"} as TextView);assertTrue(all.filterIsInstance<TextView>().any{it.text.toString()==main.getString(R.string.search_current_file,"main.py")});capture("phone-large-search-replace",d.window!!.decorView);action.performClick()
        repeat(200){idle(10);if(editor.text.toString()=="value value")return@repeat;Thread.sleep(5)}
        assertEquals("value value",editor.text.toString());d.dismiss();assertTrue(editor.history(false));assertEquals("needle needle",editor.text.toString())
    }
}
