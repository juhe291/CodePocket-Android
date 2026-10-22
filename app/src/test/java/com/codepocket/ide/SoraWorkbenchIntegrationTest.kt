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
}
