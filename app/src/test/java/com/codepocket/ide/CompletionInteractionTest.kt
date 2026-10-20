package com.codepocket.ide

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Full editor, real completion executor, native popup and input connection; no mocked provider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CompletionInteractionTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var main: MainActivity
    private lateinit var editor: CodeEditor
    @Before fun openPythonEditor() {
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py")
            .putString("settings",AppSettings(autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        main=controller.get()
        WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        controller.windowFocusChanged(true)
        editor=ReflectionHelpers.getField(main,"editor")
        layout(main.resources.displayMetrics.widthPixels,main.resources.displayMetrics.heightPixels)
        assertTrue(editor.requestFocus())
    }
    private fun layout(width:Int,height:Int) {
        val root=main.findViewById<View>(android.R.id.content)
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        root.layout(0,0,width,height)
        root.viewTreeObserver.dispatchOnGlobalLayout()
    }
    private fun typePrefix() { editor.setText("pri");editor.setSelection(3);settle() }
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        ReflectionHelpers.getField<ExecutorService>(main,"completionExecutor").submit{}.get(10,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun diagnostics():String {
        val port=ReflectionHelpers.getField<com.codepocket.ide.editor.DocumentEditorPort?>(main,"editorPort")
        val batch=ReflectionHelpers.getField<CompletionBatch?>(main,"completionBatch")
        val anchor=port?.anchor()
        val request=ReflectionHelpers.callInstanceMethod<CompletionRequest>(main,"completionRequest")
        return "suggested=${LexicalCompletion.complete(request)?.items?.size} task=${ReflectionHelpers.getField<java.util.concurrent.Future<*>?>(main,"completionTask")?.isDone} suppressed=${ReflectionHelpers.getField<CompletionRequest?>(main,"completionSuppressed")} anchorRect=${anchor?.visible?.let {"${it.left},${it.top},${it.right},${it.bottom}"}} caret=${anchor?.top}/${anchor?.bottom} windowFocus=${main.hasWindowFocus()} focus=${editor.hasFocus()} shown=${editor.isShown} size=${editor.width}x${editor.height} selection=${editor.selectionStart}/${editor.selectionEnd} composing=${BaseInputConnection.getComposingSpanStart(editor.text)} active=${ReflectionHelpers.getField<String>(main,"activePath")} loaded=${ReflectionHelpers.getField<String?>(main,"loadedPath")} anchor=${port?.anchor()} batch=$batch status=${ReflectionHelpers.getField<android.widget.TextView>(main,"status").text}"
    }
    private fun visible():Boolean = ReflectionHelpers.getField<CompletionPopup?>(main,"completionPopup")?.isShowing==true
    @After fun close() {
        main.finish();controller.pause().stop().destroy()
        WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS)
    }
    @Test fun committedEnglishTypingAutomaticallyShowsRealCandidates() {
        typePrefix();assertTrue("Typing pri should show print: ${diagnostics()}",visible())
    }
    @Test fun physicalTabAcceptsTheVisibleCandidate() {
        typePrefix();assertTrue(diagnostics(),visible())
        main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB))
        main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB))
        assertEquals("print",editor.text.toString())
    }
    @Test @Config(qualifiers="w411dp-h290dp") fun compactEditorStillShowsAtLeastOneUsableCandidate() {
        typePrefix();assertTrue("Short keyboard-constrained editor must retain one candidate: ${diagnostics()}",visible())
    }
    @Test fun toolbarTabRetainsCandidatesUntilItsClickIsAccepted() {
        typePrefix();assertTrue(diagnostics(),visible())
        // No real IME exists on the host. Lay out only the production symbol bar so the
        // Activity's next global inset pass cannot hide this explicitly supplied touch fixture.
        val toolbar=ReflectionHelpers.getField<View>(main,"keyboardToolbar")
        toolbar.visibility=View.VISIBLE
        toolbar.measure(View.MeasureSpec.makeMeasureSpec(411,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY))
        toolbar.layout(0,48,411,96)
        val popup=ReflectionHelpers.getField<CompletionPopup>(main,"completionPopup")
        assertFalse(ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup").isOutsideTouchable)
        val tab=ReflectionHelpers.getField<View>(main,"completionTabButton")
        val location=IntArray(2);tab.getLocationOnScreen(location)
        val event=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,location[0]+tab.width/2f,location[1]+tab.height/2f,0)
        try {assertTrue("Touch fixture must expose the real Tab control",tab.isShown);main.dispatchTouchEvent(event);assertTrue(diagnostics(),visible());tab.performClick()}finally{event.recycle()}
        assertEquals("print",editor.text.toString())
    }
    @Test fun touchingOutsideTheCandidateAndTabStillDismissesIt() {
        typePrefix();assertTrue(visible())
        val event=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,1f,1f,0)
        try {main.dispatchTouchEvent(event)}finally{event.recycle()}
        assertFalse(visible())
    }
    @Test fun cppKeywordsAlsoAppearAndAcceptTab() {
        val method=main.javaClass.declaredMethods.single{it.name=="switchFile"}
        method.isAccessible=true;method.invoke(main,"main.cpp",null)
        WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()
        editor=ReflectionHelpers.getField(main,"editor");editor.requestFocus()
        editor.setText("ret");editor.setSelection(3);settle();assertTrue(diagnostics(),visible())
        main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB))
        main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB))
        assertEquals("return",editor.text.toString())
    }
    @Test fun toolbarTabDoesNotModifyChineseComposition() {
        editor.setText("")
        val connection=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        connection.setComposingText("拼",1);settle();assertFalse(visible())
        ReflectionHelpers.callInstanceMethod<Unit>(main,"tab")
        assertEquals("拼",editor.text.toString());assertTrue(BaseInputConnection.getComposingSpanStart(editor.text)>=0)
    }
    @Test fun unfinishedCompositionDoesNotRewriteOrOfferAStaleCandidate() {
        editor.setText("")
        val info=android.view.inputmethod.EditorInfo()
        val connection=editor.onCreateInputConnection(info)
        connection.setComposingText("pri",1);settle()
        assertFalse(visible());assertEquals("pri",editor.text.toString())
        connection.finishComposingText();settle()
        assertTrue("Committed composition must resume completion: ${diagnostics()}",visible())
        assertEquals(-1,BaseInputConnection.getComposingSpanStart(editor.text))
    }
}
