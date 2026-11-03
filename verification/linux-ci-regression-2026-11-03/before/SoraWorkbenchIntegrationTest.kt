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
    @get:Rule val testName=org.junit.rules.TestName()
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var main: MainActivity
    private lateinit var editor: WorkbenchEditor
    private val port get() = ReflectionHelpers.getField<SoraEditorPort>(main,"editorPort")
    private val workspace get() = ReflectionHelpers.getField<Workspace>(main,"workspace")
    private fun <T> field(name:String):T = ReflectionHelpers.getField(main,name)
    @Before fun launch() {
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py").putString("settings",AppSettings(theme=if(testName.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
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
    private fun assertThemeTokens(code:String,words:List<String>,expected:List<Int>) {
        waitForTextMate()
        var actual=words.map{colorAt(code.indexOf(it))}
        repeat(200){
            if(actual==expected){assertEquals(code,editor.text.toString());return}
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));Thread.sleep(5)
            actual=words.map{colorAt(code.indexOf(it))}
        }
        assertEquals("Current Sora tokens: "+words.zip(actual.map{Integer.toHexString(it)}),expected,actual)
    }
    private fun captureTheme(name:String) {
        val cwd=java.io.File(checkNotNull(System.getProperty("user.dir")));val root=if(cwd.name=="app")cwd.parentFile else cwd
        val folder=java.io.File(root,"verification/workbench-token-theme-2026-10-24/previews").apply{mkdirs()}
        val view=main.findViewById<View>(android.R.id.content)
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        java.io.File(folder,"$name.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    @Test fun phoneLightPythonTokensUseRestrainedLoadedTheme() {
        val code="def greet():\n    return 'hello' + str(42) # note"
        loadText(code)
        assertThemeTokens(code,listOf("return","hello","42","note"),listOf(0xff7e4e94.toInt(),0xff8e4630.toInt(),0xff4f6f47.toInt(),0xff617667.toInt()))
        captureTheme("phone-light-python")
    }
    @Test fun phoneDarkPythonTokensKeepTheExistingLoadedTheme() {
        val code="def greet():\n    return 'hello' + str(42) # note"
        loadText(code)
        assertThemeTokens(code,listOf("return","hello","42","note"),listOf(0xffc586c0.toInt(),0xffce9178.toInt(),0xffb5cea8.toInt(),0xff6a9955.toInt()))
        captureTheme("phone-dark-python")
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightCppTokensUseRestrainedLoadedTheme() {
        switch("main.cpp")
        val code="#include <string>\nint main() { auto s = \"hello\"; return 42; } // note"
        loadText(code)
        assertThemeTokens(code,listOf("return","int","hello","42","note"),listOf(0xff7e4e94.toInt(),0xff286b7d.toInt(),0xff8e4630.toInt(),0xff4f6f47.toInt(),0xff617667.toInt()))
        captureTheme("tablet-light-cpp")
    }
    @Test fun syntaxThemeRoundTripKeepsBufferUndoAndSelection() {
        val code="def greet():\n    return 'hello' + str(42) # note"
        loadText(code.dropLast(1));editor.setSelection(editor.length())
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText(code.takeLast(1),1)
        editor.setSelection(3)
        val content=editor.text;val revision=field<Long>("editRevision")
        val before=field<AppSettings>("settings")
        for(light in listOf(true,false)){
            ReflectionHelpers.setField(main,"settings",before.copy(syntax=if(light)SyntaxTheme.LIGHT else SyntaxTheme.DARK))
            ReflectionHelpers.callInstanceMethod<Unit>(main,"updateTabStops")
            val expected=if(light)listOf(0xff7e4e94.toInt(),0xff8e4630.toInt()) else listOf(0xffc586c0.toInt(),0xffce9178.toInt())
            assertThemeTokens(code,listOf("return","hello"),expected)
            assertSame(content,editor.text);assertEquals(3,editor.selectionStart);assertEquals(revision,field<Long>("editRevision"))
        }
        assertTrue(editor.history(false));assertEquals(code.dropLast(1),editor.text.toString())
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
        val before=port.snapshot()!!
        assertFalse(editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("x".repeat(200001),1))
        assertEquals(before,port.snapshot());assertEquals(disk,workspace.resolve("main.py").readText())
        val message=field<android.widget.TextView>("status").text.toString()
        assertTrue(message,message.contains("20万"));assertTrue(message,message.contains("本次输入未写入"))
        switch("main.cpp");assertEquals("main.cpp",field<String>("activePath"))
    }


    private fun native(name:String,vararg parameters:Pair<Class<*>,Any?>):Any? =
        Class.forName("io.github.rosemoe.sora.widget.CodeEditor").getMethod(name,*parameters.map{it.first}.toTypedArray())
            .invoke(editor.inputView(),*parameters.map{it.second}.toTypedArray())
    private fun composingLimitContract() {
        loadText("x".repeat(199999));editor.setSelection(editor.length())
        val ic=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        assertTrue(ic.setComposingText("中",1));assertTrue(editor.isComposing);assertEquals(200000,editor.length())
        val source=editor.text.toString();val selection=editor.selectionStart
        assertFalse(ic.setComposingText("中文",1));assertEquals(source,editor.text.toString());assertEquals(selection,editor.selectionStart)
        assertTrue(editor.isComposing);assertTrue(ic.commitText("文",1))
        assertEquals("x".repeat(199999)+"文",editor.text.toString());assertFalse(editor.isComposing)
    }
    @Test fun composingReplacementAtLimitKeepsTheActualPreeditAndThenCommits() = composingLimitContract()
    @Test @Config(sdk=[28]) fun olderAndroidComposingRangeUsesTheSamePublicContract() = composingLimitContract()
    @Test fun exactlyAtLimitRejectsFurtherTypingButAllowsSelectionReplacementAndDeletion() {
        loadText("x".repeat(199999));editor.setSelection(editor.length())
        val ic=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        assertTrue(ic.commitText("y",1));assertEquals(200000,editor.length())
        val frame=port.snapshot()!!;assertFalse(ic.commitText("z",1));assertEquals(frame,port.snapshot())
        assertTrue(ic.setSelection(199998,200000));assertTrue(ic.commitText("文",1));assertEquals(199999,editor.length())
        assertTrue(ic.deleteSurroundingText(1,0));assertEquals(199998,editor.length())
    }
    @Test fun emojiPasteIsRejectedAsAWholeAndNativeClipboardAndAccessibilityAreBounded() {
        loadText("x".repeat(199999));editor.setSelection(editor.length());val before=port.snapshot()!!
        val ic=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo());assertFalse(ic.commitText("😀",1));assertEquals(before,port.snapshot())
        val clipboard=main.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("test","ab"))
        ic.performContextMenuAction(android.R.id.paste);assertEquals(before,port.snapshot());assertEquals("ab",clipboard.primaryClip!!.getItemAt(0).text.toString())
        val args=android.os.Bundle().apply{putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"x".repeat(200001))}
        assertFalse(editor.inputView().performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,args))
        assertEquals(before,port.snapshot())
    }
    @Test fun nativeIndentDuplicateAndInsertionDoNotPartiallyChangeAnOverBudgetOperation() {
        loadText("x".repeat(199998)+"\ny");editor.setSelection(0,editor.length());val before=port.snapshot()!!
        native("indentLines",Boolean::class.javaPrimitiveType!! to false);assertEquals(before,port.snapshot())
        native("duplicateSelection",String::class.java to "",Boolean::class.javaPrimitiveType!! to true);assertEquals(before,port.snapshot())
        native("insertText",String::class.java to "x".repeat(200001),Int::class.javaPrimitiveType!! to 0);assertEquals(before,port.snapshot())
        loadText("\t\t "+"x".repeat(199997));editor.setSelection(0,editor.length());val mixed=port.snapshot()!!
        native("unindentSelection");assertEquals(mixed,port.snapshot())
    }
    @Test fun generationAwareInputConnectionCannotEditTheNextDocumentOrEndItsBatch() {
        loadText("old");val old=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo());old.beginBatchEdit()
        switch("main.cpp");editor.requestFocus();awaitEditor();val current=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        val before=port.snapshot()!!
        assertFalse(old.commitText("late",1));assertFalse(old.setComposingText("旧",1));assertFalse(old.deleteSurroundingText(1,0))
        assertFalse(old.setSelection(0,0));assertFalse(old.endBatchEdit());old.closeConnection();assertEquals(before,port.snapshot())
        assertTrue(current.commitText("a",1))
        val newer=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo());assertFalse(current.commitText("old connection",1));assertTrue(newer.commitText("b",1))
        val text=editor.text.toString();port.close();assertFalse(newer.commitText("destroyed",1));assertEquals(text,editor.text.toString())
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletAutomaticExtrasAtTheBoundaryKeepRawTextAndGlobalSettings() {
        loadText("x".repeat(199999));editor.setSelection(editor.length());awaitEditor()
        val settings=field<AppSettings>("settings")
        native("commitText",CharSequence::class.java to "(",Boolean::class.javaPrimitiveType!! to true,Boolean::class.javaPrimitiveType!! to true)
        assertEquals(200000,editor.length());assertEquals('(',editor.text.last());assertEquals(settings,field<AppSettings>("settings"))
        key(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);assertEquals(199999,editor.length())
        editor.setSelection(0);key(KeyEvent.KEYCODE_A)
        assertEquals(200000,editor.length());assertEquals('a',editor.text.first())
        val full=port.snapshot()!!;key(KeyEvent.KEYCODE_ENTER,KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)
        assertEquals(full,port.snapshot())
        loadText("    "+"x".repeat(199995));editor.setSelection(editor.length())
        native("commitText",CharSequence::class.java to "\n",Boolean::class.javaPrimitiveType!! to true,Boolean::class.javaPrimitiveType!! to true)
        assertEquals(200000,editor.length());assertTrue(editor.text.toString().endsWith("\n"))
        loadText("value = ");editor.setSelection(editor.length());waitForTextMate()
        native("commitText",CharSequence::class.java to "(",Boolean::class.javaPrimitiveType!! to true,Boolean::class.javaPrimitiveType!! to true)
        assertEquals("value = ()",editor.text.toString())
        loadText("    value = 1");editor.setSelection(editor.length());waitForTextMate()
        native("commitText",CharSequence::class.java to "\n",Boolean::class.javaPrimitiveType!! to true,Boolean::class.javaPrimitiveType!! to true)
        assertEquals("    value = 1\n    ",editor.text.toString())
    }
    @Test fun genuineDraftIoFailureStillReportsThatTheNewCheckpointWasNotWritten() {
        loadText("print('safe')");key(KeyEvent.KEYCODE_S,KeyEvent.META_CTRL_ON);settleIo();val disk=workspace.resolve("main.py").readText()
        val blocked=java.io.File(main.cacheDir,"draft-parent-is-file").apply{writeText("occupied")}
        ReflectionHelpers.setField(main,"drafts",DraftStore(blocked){a,b->android.system.Os.rename(a.path,b.path)})
        editor.setSelection(editor.length());editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("# new",1)
        switch("main.cpp");assertEquals("main.py",field<String>("activePath"));assertEquals(disk,workspace.resolve("main.py").readText())
        assertTrue(field<android.widget.TextView>("status").text.toString().contains("本次恢复草稿未写入"))
        val dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);dialog.dismiss()
    }

    @Test @android.annotation.TargetApi(34) fun modernImeOverloadsAndReplaceTextRespectTheSameLimit() {
        loadText("x".repeat(199999));editor.setSelection(editor.length())
        val ic=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo());val attr=android.view.inputmethod.TextAttribute.Builder().build()
        assertTrue(ic.commitText("a",1,attr));val before=port.snapshot()!!
        assertFalse(ic.setComposingText("中",1,attr));assertEquals(before,port.snapshot())
        assertTrue(ic.setComposingRegion(199999,200000,attr));assertTrue(ic.setComposingText("文",1,attr))
        val composing=port.snapshot()!!;assertTrue(composing.composing)
        assertFalse(ic.replaceText(0,1,"ab",1,attr));assertEquals(composing,port.snapshot())
        assertTrue(ic.replaceText(0,2,"国",1,attr));assertEquals(199999,editor.length());assertEquals('国',editor.text.first())
    }
    @Test fun compositionRangeOutsideTheSelectionIsReadFromTheRealConnection() {
        loadText("中"+"x".repeat(199998)+"文");editor.setSelection(200000)
        val ic=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        assertTrue(ic.setComposingRegion(0,1));assertTrue(ic.setSelection(199999,200000));assertTrue(editor.isComposing)
        assertTrue(ic.commitText("abc",1).not());assertEquals(200000,editor.length());assertTrue(editor.isComposing)
        assertTrue(ic.commitText("ab",1));assertEquals(200000,editor.length());assertFalse(editor.isComposing)
    }
    private fun mousePoint(column:Int):Pair<Float,Float> {
        editor.setSelection(column);val anchor=port.anchor()!!;val position=IntArray(2);editor.inputView().getLocationOnScreen(position)
        return (anchor.x-position[0]).toFloat() to ((anchor.top+anchor.bottom)/2-position[1]).toFloat()
    }
    private fun mouseDragCopy(from:Pair<Float,Float>,to:Pair<Float,Float>) {
        // Native double-click detection uses uptime, not MotionEvent's timestamps.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        val view=editor.inputView();val time=android.os.SystemClock.uptimeMillis()
        view.dispatchKeyEvent(KeyEvent(time,time,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_CTRL_LEFT,0,KeyEvent.META_CTRL_ON))
        val meta=native("getKeyMetaStates")!!;assertTrue(meta.javaClass.getMethod("isCtrlPressed").invoke(meta) as Boolean)
        fun send(action:Int,offset:Long,point:Pair<Float,Float>) {
            val properties=android.view.MotionEvent.PointerProperties().apply{id=0;toolType=android.view.MotionEvent.TOOL_TYPE_MOUSE}
            val coords=android.view.MotionEvent.PointerCoords().apply{x=point.first;y=point.second;pressure=1f;size=1f}
            val event=android.view.MotionEvent.obtain(time,time+offset,action,1,arrayOf(properties),arrayOf(coords),KeyEvent.META_CTRL_ON,
                if(action==android.view.MotionEvent.ACTION_UP)0 else android.view.MotionEvent.BUTTON_PRIMARY,1f,1f,0,0,android.view.InputDevice.SOURCE_MOUSE,0)
            try{view.dispatchTouchEvent(event)}finally{event.recycle()}
        }
        send(android.view.MotionEvent.ACTION_DOWN,0,from);send(android.view.MotionEvent.ACTION_MOVE,300,to);send(android.view.MotionEvent.ACTION_UP,600,to)
        view.dispatchKeyEvent(KeyEvent(time,time+601,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_CTRL_LEFT,0,0))
    }
    @Test fun realMouseCtrlDragCannotCopySelectionPastTheLimitButStillCopiesWhenItFits() {
        for(size in listOf(200000,199998)) {
            loadText("x".repeat(size));editor.requestFocus();awaitEditor()
            val from=mousePoint(2);val to=mousePoint(12);editor.setSelection(1,3);val before=port.snapshot()!!
            mouseDragCopy(from,to)
            if(size==200000) assertEquals(before,port.snapshot()) else assertEquals(200000,editor.length())
        }
    }

}
