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
        val dir=File(root,"verification/workbench-command-ui-2026-10-24/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
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
    @Test fun localizedCommandTitlesKeepStopAndNavigationIconsAndDangerState(){
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showCommandMenu",ClassParameter.from(View::class.java,anchor()))
        idle(80);capture("phone-main-categories",popup().contentView);popup().dismiss()
        val context=CommandContext(false,true,true,false,false,true,false)
        var selected:IdeCommand?=null
        val menu=IdeCommandMenu.show(anchor(),context,listOf(IdeCommand.RUN_STOP,IdeCommand.GOTO_LINE,IdeCommand.DELETE),{selected=it},false)
        menu.menu.findItem(IdeCommand.RUN_STOP.ordinal+1).title="Interrupt execution"
        menu.menu.findItem(IdeCommand.GOTO_LINE.ordinal+1).title="Navigate to source"
        menu.menu.findItem(IdeCommand.DELETE.ordinal+1).title="Remove source"
        menu.show();idle(80);val window=popup()
        fun glyph(title:String)=ReflectionHelpers.getField<IdeIcon>(views(menuRow(window,title)).filterIsInstance<ImageView>().single().drawable,"icon")
        assertEquals(IdeIcon.STOP,glyph("Interrupt execution"));assertEquals(IdeIcon.LOCATE,glyph("Navigate to source"))
        assertEquals(IdeIcon.DELETE,glyph("Remove source"))
        val removed=menuRow(window,"Remove source");assertFalse(removed.isEnabled);assertFalse(removed.isFocusable)
        assertEquals(DesignTokens.forContext(main).error,views(removed).filterIsInstance<TextView>().single().currentTextColor)
        capture("phone-command-identities",window.contentView)
        val content=editor.text;menuRow(window,"Navigate to source").performClick();idle()
        assertEquals(IdeCommand.GOTO_LINE,selected);assertSame(content,editor.text)
    }
    @Test @Config(qualifiers="w280dp-h900dp") fun LargeNarrowEditMenuKeepsFullShortcutsAndRealNavigation(){
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showWorkbenchMenu",ClassParameter.from(View::class.java,anchor()),ClassParameter.from(WorkbenchMenuGroup::class.java,WorkbenchMenuGroup.EDIT))
        idle(100);val window=popup();val scroll=views(window.contentView).filterIsInstance<ScrollView>().single()
        for(label in views(window.contentView).filterIsInstance<TextView>()){
            textFits(label)
            val layout=label.layout!!;val last=minOf(layout.lineCount,label.maxLines)-1
            assertEquals("Menu text must remain complete: ${label.text}",label.text.length,layout.getLineEnd(last))
        }
        val redo=menuRow(window,IdeCommand.REDO.title)
        val shortcut=views(redo).filterIsInstance<TextView>().single{it.text.toString()=="Ctrl+Y / Ctrl+Shift+Z"}
        val title=views(redo).filterIsInstance<TextView>().single{it.text.toString()=="重做"}
        val a=IntArray(2);val b=IntArray(2);shortcut.getLocationOnScreen(a);title.getLocationOnScreen(b)
        assertTrue("Shortcut must not squeeze the title",a[1]>=b[1]+title.height)
        capture("phone-large-narrow-edit-menu",window.contentView)
        val target=menuRow(window,IdeCommand.GOTO_LINE.title);scroll.scrollTo(0,target.top)
        val content=editor.text;target.performClick();idle()
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing)
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()=="跳转行"})
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertSame(content,editor.text)
    }
    @Test @Config(qualifiers="w411dp-h1000dp") fun LargeSignatureKeepsReadableNoticeReferenceAndControls(){
        text("print(");assistance("updateSignatureHelp","signatureExecutor");val hint=field<SignaturePopup>("signaturePopup");assertTrue(hint.isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(hint,"popup");val notice=ReflectionHelpers.getField<TextView>(hint,"disclaimer");val reference=ReflectionHelpers.getField<HorizontalScrollView>(hint,"scroll")
        textFits(notice);assertTrue("Scrollable signature is a touch target",reference.height>=48)
        val content=editor.text;val controls=views(window.contentView).filter{it.contentDescription in listOf(main.getString(R.string.signature_previous),main.getString(R.string.signature_next),main.getString(R.string.signature_close))}
        assertEquals(1,controls.count{it.isShown});assertTrue(controls.single{it.isShown}.contentDescription==main.getString(R.string.signature_close))
        controls.filter{it.isShown}.forEach{assertTrue(it.width>=48);assertTrue(it.height>=48)}
        capture("phone-large-signature",window.contentView);assertTrue(editor.hasFocus());assertSame(content,editor.text)
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLargeLightCompletionKeepsDetailsAndKeyboardAcceptance(){
        text("pri");assistance("updateSuggestions","completionExecutor");val help=field<CompletionPopup>("completionPopup");assertTrue(help.isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(help,"popup");val details=ReflectionHelpers.getField<TextView>(help,"details");if(details.visibility==View.VISIBLE)textFits(details)
        capture("tablet-large-light-completion",window.contentView);main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB));main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB));assertEquals("print",editor.text.toString())
    }
    private fun diagnostic(message:String){text("abc");editor.setSelection(1);idle(150)
        field<android.os.Handler>("handler").removeCallbacks(field<Runnable>("refreshEditor"))
        field<EditorAnalysisController>("editorAnalysis").cancel()
        val frame=port.snapshot()!!
        assertTrue(port.applyDecorations(EditorAnalysis(frame,emptyList(),null,syntax=SyntaxReport(listOf(InlineIssue(0,3,message,"诊断布局验收"))))))
        assertNotNull("Injected diagnostic must reach actual caret",port.diagnosticAtCaret());editor.inputView().performClick();idle(80)
    }
    @Test @Config(qualifiers="w360dp-h200dp") fun tinyLargeDiagnosticOpensFullDetailsInsteadOfSilentlyDisappearing(){
        val message="布局验收消息\n".repeat(30);diagnostic(message);val d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull("No hover room must still offer details",d);assertTrue(d.isShowing)
        assertTrue(views(d.window!!.decorView).filterIsInstance<TextView>().any{it.text.contains(message)});assertEquals(1,editor.selectionStart)
        capture("phone-tiny-large-diagnostic-details",d.window!!.decorView)
        val body=views(d.window!!.decorView).filterIsInstance<TextView>().single{it.text.contains(message)}
        val scroll=body.parent as ScrollView
        assertTrue("At least one complete error line must fit",scroll.height>=body.lineHeight)
        val last=body.layout.getLineForOffset(body.text.lastIndexOf("布局验收消息"))
        scroll.scrollTo(0,body.top+body.compoundPaddingTop+body.layout.getLineTop(last));idle(16)
        val top=body.top+body.compoundPaddingTop+body.layout.getLineTop(last)-scroll.scrollY
        val height=body.layout.getLineBottom(last)-body.layout.getLineTop(last)
        assertTrue("The complete last error line must be reachable by scrolling",scroll.scrollY>0&&top>=0&&top+height<=scroll.height)
        capture("phone-tiny-large-diagnostic-scrolled",d.window!!.decorView)
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle()
    }
    @Test @Config(qualifiers="w360dp-h220dp") fun compactLargeDiagnosticUsesReclaimedPathSpace(){
        diagnostic("紧凑窗口路径留白验收\n".repeat(30))
        assertEquals(View.GONE,field<TextView>("pathBreadcrumb").visibility)
        assertTrue("Reclaimed path space now fits a real diagnostic hover at 220dp",field<DiagnosticPopup>("diagnosticPopup").isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(field<DiagnosticPopup>("diagnosticPopup"),"popup")
        capture("compact-220-large-diagnostic-hover",window.contentView)
    }
    @Test fun diagnosticHoverScrollsCompleteMessageAndCopiesWithoutEditing(){
        val message="诊断布局验收：这是一段供界面回归的长说明。\n".repeat(25);diagnostic(message);val help=field<DiagnosticPopup>("diagnosticPopup");assertTrue(help.isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(help,"popup");val body=views(window.contentView).filterIsInstance<TextView>().single{it.text.toString()==message}
        assertTrue("Full diagnostics should scroll",body.parent is ScrollView);assertTrue((body.parent as ScrollView).canScrollVertically(1));val content=editor.text
        views(window.contentView).single{it.contentDescription=="复制错误信息"}.performClick();assertEquals(message,main.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString());assertSame(content,editor.text);assertTrue(editor.hasFocus())
    }
    @Test @Config(qualifiers="w360dp-h290dp") fun compactLargeDiagnosticKeepsHoverWhenSpaceFits(){
        diagnostic("紧凑窗口诊断显示验收\n".repeat(30))
        assertTrue("The redesigned 290dp window still has hover room",field<DiagnosticPopup>("diagnosticPopup").isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(field<DiagnosticPopup>("diagnosticPopup"),"popup")
        capture("compact-large-diagnostic-hover",window.contentView)
    }
    @Test fun unchangedGlobalLayoutKeepsHoverButCaretChangeDismisses(){
        diagnostic("固定光标的错误说明")
        val help=field<DiagnosticPopup>("diagnosticPopup");assertTrue(help.isShowing);val content=editor.text
        repeat(3){editor.viewTreeObserver.dispatchOnGlobalLayout();idle(16);assertTrue(help.isShowing)}
        editor.setSelection(2);idle();assertFalse(help.isShowing);assertSame(content,editor.text)
    }
    @Test fun fontGeometryChangeDismissesHoverWithoutEditing(){
        diagnostic("字号变化应取消原位置的诊断浮层")
        val help=field<DiagnosticPopup>("diagnosticPopup");assertTrue(help.isShowing);val content=editor.text
        editor.textSize=22f;idle(80);editor.viewTreeObserver.dispatchOnGlobalLayout()
        assertFalse(help.isShowing);assertSame(content,editor.text)
    }
    @Test fun identicalDiagnosticRefreshKeepsHoverButNewMessageCloses(){
        diagnostic("原错误说明")
        val help=field<DiagnosticPopup>("diagnosticPopup");val content=editor.text
        fun apply(message:String):Boolean {
            val frame=port.snapshot()!!
            return port.applyDecorations(EditorAnalysis(frame,emptyList(),null,syntax=SyntaxReport(listOf(InlineIssue(0,3,message,"诊断布局验收")))))
        }
        assertTrue(apply("原错误说明"));idle(16);assertTrue(help.isShowing)
        assertTrue(apply("新错误说明"));assertFalse(help.isShowing);assertSame(content,editor.text)
    }
    @Test fun clearingDiagnosticsDismissesHoverWithoutEditing(){
        diagnostic("待清除错误说明")
        val help=field<DiagnosticPopup>("diagnosticPopup");assertTrue(help.isShowing);val content=editor.text
        port.clearDiagnostics()
        assertTrue(port.report.issues.isEmpty());assertFalse(help.isShowing);assertSame(content,editor.text);assertEquals(1,editor.selectionStart)
    }
    @Test fun actualBracketDiagnosticOpensReadOnlyHover(){
        text("print([)");idle(150)
        val analysis=field<EditorAnalysisController>("editorAnalysis");analysis.request()
        ReflectionHelpers.getField<ExecutorService>(analysis,"worker").submit{}.get(10,TimeUnit.SECONDS);idle(80)
        val issue=checkNotNull(port.report.issues.firstOrNull()){ "Existing structural analyzer must report the mismatched bracket" }
        editor.setSelection(issue.start);editor.inputView().performClick();idle(80)
        val help=field<DiagnosticPopup>("diagnosticPopup");assertTrue(help.isShowing)
        val window=ReflectionHelpers.getField<PopupWindow>(help,"popup")
        assertTrue(views(window.contentView).filterIsInstance<TextView>().any{it.text.toString()==issue.message})
        assertEquals("print([)",editor.text.toString());assertTrue(editor.hasFocus())
        capture("phone-actual-structural-diagnostic",window.contentView)
        capture("phone-actual-structural-code",main.findViewById(android.R.id.content))
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
