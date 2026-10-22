package com.codepocket.ide

import android.os.Looper
import android.view.View
import android.view.KeyEvent
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.editor.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Actual main Activity + actual Sora widget/content + existing real private workspace and save controller. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SoraWorkbenchIntegrationTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var main: MainActivity
    private lateinit var editor: WorkbenchEditor
    private val port get() = ReflectionHelpers.getField<SoraEditorPort>(main,"editorPort")
    private val workspace get() = ReflectionHelpers.getField<Workspace>(main,"workspace")
    private fun <T> field(name:String):T = ReflectionHelpers.getField(main,name)
    @Before fun launch() {
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py").putString("settings",AppSettings(autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get()
        settleIo(); controller.windowFocusChanged(true);editor=field("editor");layout();editor.requestFocus();awaitEditor()
    }
    private fun awaitEditor() {
        repeat(100) {
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (editor.isLayoutReady) return
            Thread.sleep(5)
        }
        fail("Real Sora layout did not complete")
    }
    private fun loadText(text:String) { editor.setText(text);awaitEditor() }
    private fun layout() {
        val view=main.findViewById<View>(android.R.id.content);val m=main.resources.displayMetrics
        view.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.EXACTLY))
        view.layout(0,0,view.measuredWidth,view.measuredHeight);view.viewTreeObserver.dispatchOnGlobalLayout()
    }
    private fun settleIo() {
        repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle()}
    }
    private fun key(code:Int,meta:Int=0) {
        main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0,meta))
        main.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,code,0,meta))
    }
    private fun switch(path:String) {
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,path),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null))
        settleIo();layout()
    }
    @After fun close() { main.finish();controller.pause().stop().destroy();settleIo() }
    @Test fun normalMainEntryLoadsTheExistingWorkspaceIntoTheMatureEditor() {
        assertEquals("com.codepocket.ide",main.packageName)
        assertEquals("io.github.rosemoe.sora.widget.CodeEditor",editor.inputView().javaClass.superclass.name)
        assertEquals(workspace.read(workspace.resolve("main.py")),port.snapshot()!!.buffer.text)
        assertEquals("internal",workspace.identity)
        assertEquals(1,(editor as android.view.ViewGroup).childCount)
    }
    @Test fun physicalSaveWritesActualProjectBytesAndClearsOnlyMatchingDraft() {
        val text="print('主程序真实保存')\n";loadText(text);editor.setSelection(text.length)
        assertTrue(field("dirty"));key(KeyEvent.KEYCODE_S,KeyEvent.META_CTRL_ON);settleIo()
        assertEquals("status=${field<android.widget.TextView>("status").text} dirty=${field<Boolean>("dirty")} saving=${field<Boolean>("saving")} busy=${field<Boolean>("workspaceBusy")}",text,workspace.resolve("main.py").readText())
        assertFalse(field("dirty"));assertEquals(text,port.snapshot()!!.buffer.text)
        val sessions=field<EditorSessions>("sessions")
        assertNull(field<DraftStore>("drafts").read(sessions.key("main.py")))
    }
    @Test fun switchingTabsSavesOldBufferAndRestoresSelectionWithoutCrossFileUndo() {
        loadText("print('one')\nprint('two')");editor.setSelection(7)
        switch("main.cpp");assertEquals("status=${field<android.widget.TextView>("status").text}","main.cpp",field<String>("activePath"));assertTrue(editor.text.toString().contains("#include"))
        assertFalse(editor.history(false))
        switch("main.py");assertEquals(7,editor.selectionStart);assertEquals("print('one')\nprint('two')",editor.text.toString())
    }
    @Test fun externalConflictKeepsDirtyTextAndPreventsTabSwitch() {
        loadText("print('my unsaved code')")
        workspace.resolve("main.py").writeText("print('external writer')")
        switch("main.cpp")
        assertEquals("main.py",field<String>("activePath"));assertEquals("print('my unsaved code')",editor.text.toString())
        assertTrue(field("dirty"));assertEquals("print('external writer')",workspace.resolve("main.py").readText())
        val draft=field<DraftStore>("drafts").read(field<EditorSessions>("sessions").key("main.py"))
        assertNotNull("status=${field<android.widget.TextView>("status").text}",draft)
        assertEquals(editor.text.toString(),draft!!.text)
    }
    @Test fun staleEditAndChineseCompositionCannotRewriteTheCurrentBuffer() {
        loadText("abc");editor.setSelection(3);val old=port.snapshot()!!.buffer
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("d",1)
        assertEquals(EditResult.STALE,port.applyEdit(EditorTransaction(old,TextChange(0,1,"X",1))))
        assertEquals("abcd",editor.text.toString())
        val connection=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo());connection.setComposingText("拼",1)
        val composing=port.snapshot()!!;assertTrue(composing.composing)
        assertEquals(EditResult.COMPOSING,port.applyEdit(EditorTransaction(composing.buffer,TextChange(0,1,"X",1))))
        connection.finishComposingText();assertFalse(editor.isComposing)
    }
    @Test fun literalReplaceUsesTheSameMainBufferAndOneUndoTransaction() {
        loadText("one one");editor.setSelection(0);val source=port.snapshot()!!.buffer
        val change=EditorSearch.replaceAll(source.text,"one","two")!!.change
        assertTrue(port.applyEdit(EditorTransaction(source,change)).accepted);assertEquals("two two",editor.text.toString())
        assertTrue(editor.history(false));assertEquals("one one",editor.text.toString())
    }
    @Test fun delayedViewportAndBackgroundSelectionNeverStealTerminalOrInputFocus() {
        switch("main.cpp"); loadText("a\nb\nc");editor.setSelection(0)
        ReflectionHelpers.setField(main,"panelVisible",true)
        ReflectionHelpers.setField(main,"presetInputExpanded",true)
        field<View>("outputRegion").visibility=View.VISIBLE
        val input=field<android.widget.EditText>("input");input.visibility=View.VISIBLE
        assertTrue("Real C++ input must accept focus",input.requestFocus())
        val source=port.snapshot()!!.buffer
        assertTrue(port.select(EditorSelectionRequest(source,4,focus=false,reveal=false)))
        assertTrue(input.hasFocus());assertFalse(editor.hasFocus());assertEquals(4,editor.selectionStart)
        val stamp=port.navigationSnapshot()!!;assertTrue(port.restoreViewport(stamp));layout()
        assertTrue(input.hasFocus());assertFalse(editor.hasFocus())
    }
    @Test fun realDiagnosticRegionsAndLineNavigationUseTheCurrentDocumentVersion() {
        loadText("def bad(:\n    pass");editor.setSelection(0)
        val frame=port.snapshot()!!
        val issue=InlineIssue(4,7,"真实诊断适配测试","测试来源")
        assertTrue(port.applyDecorations(EditorAnalysis(frame,emptyList(),null,syntax=SyntaxReport(listOf(issue)))))
        assertEquals(issue,port.report.issues.single())
        assertEquals(DiagnosticStatus.LINE_ONLY,port.locateDiagnostic(EditorDiagnostic(frame.buffer,2)))
        assertEquals(10,editor.selectionStart)
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("#",1)
        assertTrue(port.report.issues.isEmpty())
        assertFalse(port.applyDecorations(EditorAnalysis(frame,emptyList(),null)))
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletUsesTheSameEditorWithStartupTerminalAndExistingSession() {
        assertTrue(field("panelVisible"));assertNotNull(field<Any?>("activityBar"));assertNotNull(field<Any?>("explorerPane"))
        val registry=field<com.codepocket.ide.terminal.shell.ShellSessionRegistry>("terminalRegistry")
        val before=registry.catalog.entries().map{it.id}
        key(KeyEvent.KEYCODE_PLUS,KeyEvent.META_CTRL_ON)
        assertEquals(before,registry.catalog.entries().map{it.id})
        assertSame(editor,field<WorkbenchEditor>("editor"));assertTrue(editor.hasFocus())
    }
    /** Inspect the actual runtime Sora spans through its public API without exposing Kotlin-2 types to app compilation. */
    private fun colorAt(offset:Int, background:Boolean=false):Int {
        val view=editor.inputView()
        val styles=view.javaClass.getMethod("getStyles").invoke(view) ?: return 0
        val spans=styles.javaClass.getField("spans").get(styles)
        val reader=Class.forName("io.github.rosemoe.sora.lang.styling.Spans").getMethod("read").invoke(spans)
        val code=editor.text.toString();val line=code.take(offset).count{it=='\n'}
        val column=offset-(code.lastIndexOf('\n',offset-1)+1)
        val list=Class.forName("io.github.rosemoe.sora.lang.styling.Spans\$Reader").getMethod("getSpansOnLine",Int::class.javaPrimitiveType).invoke(reader,line) as List<*>
        val spanType=Class.forName("io.github.rosemoe.sora.lang.styling.Span")
        val span=list.last{ (spanType.getMethod("getColumn").invoke(it) as Int)<=column }!!
        val scheme=view.javaClass.getMethod("getColorScheme").invoke(view)
        val schemeType=Class.forName("io.github.rosemoe.sora.widget.schemes.EditorColorScheme")
        val resolver=spanType.getMethod("getSpanExt",Int::class.javaPrimitiveType).invoke(span,0)
        if(resolver!=null) {
            val color=Class.forName("io.github.rosemoe.sora.lang.styling.span.SpanColorResolver")
                .getMethod(if(background) "getBackgroundColor" else "getForegroundColor",spanType).invoke(resolver,span)
            if(color!=null) return Class.forName("io.github.rosemoe.sora.lang.styling.color.ResolvableColor")
                .getMethod("resolve",schemeType).invoke(color,scheme) as Int
        }
        val style=spanType.getMethod("getStyle").invoke(span) as Long
        val id=((if(background) style shr 19 else style) and 0x7ffff).toInt()
        return schemeType.getMethod("getColor",Int::class.javaPrimitiveType).invoke(scheme,id) as Int
    }
    private fun decorateCurrent():EditorAnalysis {
        val frame=port.snapshot()!!;val analysis=EditorAnalyzer().analyze(frame)
        assertTrue(port.applyDecorations(analysis));return analysis
    }
    @Test fun mixedBracketsAndLexicalRolesAreDrawnInTheMainEditor() {
        val code="def calc(value):\n    obj.field = value + LIMIT\n    return ([{value: 1}])"
        loadText(code);editor.setSelection(code.lastIndexOf('(')+1)
        waitForTextMate();val analysis=decorateCurrent()
        for(token in analysis.extraTokens) {
            val expected=EditorPalette.color(token.kind,true) ?: continue
            assertEquals("${token.kind} at ${token.start}",expected,colorAt(token.start))
        }
        val pair=analysis.brackets!!
        assertEquals(0xFF3A3D41.toInt(),colorAt(pair.first,true));assertEquals(0xFF3A3D41.toInt(),colorAt(pair.second,true))
        assertNotEquals(colorAt(code.lastIndexOf('(')),colorAt(code.lastIndexOf('[')))
        assertEquals(code,editor.text.toString());captureEditor("phone-dark")
    }
    @Test fun decorationsKeepSelectionDirtyStateRevisionAndActualUndoHistory() {
        loadText("item = (1)");editor.setSelection(4)
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("s",1)
        val source=port.snapshot()!!;val revision=field<Long>("editRevision");val dirty=field<Boolean>("dirty")
        decorateCurrent();decorateCurrent()
        assertEquals(source,port.snapshot());assertEquals(revision,field<Long>("editRevision"));assertEquals(dirty,field<Boolean>("dirty"))
        assertTrue(editor.history(false));assertEquals("item = (1)",editor.text.toString())
    }
    @Test fun obsoleteDecorationsCannotSurviveTypingTabSwitchOrComposition() {
        loadText("old = ([1])");editor.setSelection(0);val old=decorateCurrent()
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("#",1)
        assertFalse(port.applyDecorations(old))
        switch("main.cpp");assertFalse(port.applyDecorations(old))
        loadText("value = 1");editor.setSelection(0);val next=EditorAnalyzer().analyze(port.snapshot()!!)
        val connection=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo());connection.setComposingText("中",1)
        val before=editor.text.toString();assertFalse(port.applyDecorations(next));assertEquals(before,editor.text.toString())
        connection.finishComposingText()
    }
    @Test fun invalidOverlayIsRejectedBeforeChangingDiagnosticsOrBuffer() {
        loadText("value = 1");val frame=port.snapshot()!!
        val issue=InlineIssue(0,5,"known issue","test")
        assertTrue(port.applyDecorations(EditorAnalysis(frame,emptyList(),null,syntax=SyntaxReport(listOf(issue)))))
        assertFalse(port.applyDecorations(EditorAnalysis(frame,emptyList(),null,
            extraTokens=listOf(EditorFeatures.Token(-1,3,"variable")))))
        assertEquals(listOf(issue),port.report.issues);assertEquals("value = 1",editor.text.toString())
    }
    @Test fun oversizedHighlightFallbackClearsRolesButKeepsStatusAndAllowsEditing() {
        loadText("x".repeat(45001));editor.setSelection(45001)
        val result=decorateCurrent();assertEquals(HighlightFallback.TEXT_LIMIT,result.fallback)
        assertTrue(port.report.status.contains("4.5"));assertEquals(45001,editor.length())
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("y",1)
        assertEquals(45002,editor.length())
    }

    private fun captureEditor(name:String) {
        val cwd=java.io.File(checkNotNull(System.getProperty("user.dir")));val root=if(cwd.name=="app") cwd.parentFile else cwd
        val folder=java.io.File(root,"verification/main-app-editor-feedback-2026-10-22/previews");folder.mkdirs()
        val bitmap=android.graphics.Bitmap.createBitmap(editor.width,editor.height,android.graphics.Bitmap.Config.ARGB_8888)
        editor.draw(android.graphics.Canvas(bitmap))
        java.io.File(folder,"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        bitmap.recycle()
    }
    private fun waitForTextMate() {
        repeat(200) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val styles=editor.inputView().javaClass.getMethod("getStyles").invoke(editor.inputView())
            if(editor.syntaxState().contains("TextMate") && styles!=null) return
            Thread.sleep(5)
        }
        fail("Actual TextMate analysis did not become ready: ${editor.syntaxState()}")
    }
    @Test fun textMateReanalysisAndStyleRefreshKeepTheLexicalLayer() {
        val code="def calc(value):\n    return ([value]) # comment"
        loadText(code);editor.setSelection(0);waitForTextMate()
        val keyword=colorAt(0);val comment=colorAt(code.indexOf('#'))
        decorateCurrent()
        assertEquals(keyword,colorAt(0));assertEquals(comment,colorAt(code.indexOf('#')))
        val view=editor.inputView();val styles=view.javaClass.getMethod("getStyles").invoke(view)
        Class.forName("io.github.rosemoe.sora.widget.CodeEditor").getMethod("setStyles",Class.forName("io.github.rosemoe.sora.lang.styling.Styles")).invoke(view,styles)
        assertEquals(EditorPalette.color("parameter",true),colorAt(code.indexOf("value")))
        editor.setSelection(editor.length());editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("\nnext = 2",1)
        decorateCurrent()
        assertEquals(EditorPalette.color("variable",true),colorAt(editor.text.toString().indexOf("next")))
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun syntaxThemeAndFontApplyKeepTheSameTabletBufferAndTerminal() {
        loadText("value = ([1])");editor.setSelection(0);decorateCurrent()
        val before=port.snapshot()!!;val registry=field<com.codepocket.ide.terminal.shell.ShellSessionRegistry>("terminalRegistry")
        val sessions=registry.catalog.entries().map{it.id};val panel=field<Boolean>("panelVisible")
        val old=field<AppSettings>("settings");val next=old.copy(syntax=SyntaxTheme.LIGHT,fontSize=18,wordWrap=true)
        var applied=false
        ReflectionHelpers.callInstanceMethod<Unit>(main,"applySettings",ClassParameter.from(AppSettings::class.java,next),
            ClassParameter.from(kotlin.jvm.functions.Function1::class.java,{ value:Boolean -> applied=value }))
        assertTrue(applied);layout();awaitEditor();val frame=port.snapshot()!!;assertFalse(frame.dark)
        assertTrue(port.applyDecorations(EditorAnalyzer().analyze(frame)))
        assertEquals(EditorPalette.color("variable",false),colorAt(0));assertEquals(EditorPalette.color("bracket0",false),colorAt(8))
        assertEquals(before.buffer,frame.buffer);assertEquals(sessions,registry.catalog.entries().map{it.id})
        assertEquals(panel,field<Boolean>("panelVisible"));assertSame(editor,field<WorkbenchEditor>("editor"))
        captureEditor("tablet-light-syntax")
    }
    @Test fun nativeOversizedPasteDoesNotOverwriteTheFileOrClaimAnUnwrittenRecoveryDraft() {
        loadText("print('safe')");key(KeyEvent.KEYCODE_S,KeyEvent.META_CTRL_ON);settleIo()
        val disk=workspace.resolve("main.py").readText();editor.setSelection(editor.length())
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("x".repeat(200001),1)
        assertTrue(editor.length()>Workspace.MAX_CHARS)
        key(KeyEvent.KEYCODE_S,KeyEvent.META_CTRL_ON);settleIo()
        assertEquals(disk,workspace.resolve("main.py").readText());assertTrue(field("dirty"))
        val message=field<android.widget.TextView>("status").text.toString()
        assertTrue(message,message.contains("本次恢复草稿未写入"));assertTrue(message,message.contains("20万"))
        switch("main.cpp");assertEquals("main.py",field<String>("activePath"))
        val dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val detail=dialog.findViewById<android.widget.TextView>(android.R.id.message).text.toString()
        assertTrue(detail,detail.contains("20万"));assertTrue(detail,detail.contains("已阻止切换"))
        dialog.dismiss();editor.requestFocus()
        assertTrue(editor.history(false));assertEquals(disk,editor.text.toString())
    }

}
