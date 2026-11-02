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
    private lateinit var editor: com.codepocket.editorlab.WorkbenchEditor
    @Before fun openPythonEditor() {
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py")
            // Seed an actual v8 install, whose always-serialized EXPLICIT was the old default.
            .putString("settings",org.json.JSONObject(AppSettings(autoSave=false,animations=false).toJson())
                .put("version",8).put("completionArrows","EXPLICIT").toString()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        main=controller.get()
        WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        controller.windowFocusChanged(true)
        editor=ReflectionHelpers.getField(main,"editor")
        layout(main.resources.displayMetrics.widthPixels,main.resources.displayMetrics.heightPixels)
        assertTrue(editor.requestFocus());awaitEditor()
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
    private fun layout(width:Int,height:Int) {
        val root=main.findViewById<View>(android.R.id.content)
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        root.layout(0,0,width,height)
        root.viewTreeObserver.dispatchOnGlobalLayout()
    }
    private fun typePrefix() { loadText("pri");editor.setSelection(3);settle() }
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        ReflectionHelpers.getField<ExecutorService>(main,"completionExecutor").submit{}.get(10,TimeUnit.SECONDS)
        ReflectionHelpers.getField<ExecutorService>(main,"signatureExecutor").submit{}.get(10,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun diagnostics():String {
        val port=ReflectionHelpers.getField<com.codepocket.ide.editor.DocumentEditorPort?>(main,"editorPort")
        val batch=ReflectionHelpers.getField<CompletionBatch?>(main,"completionBatch")
        val anchor=port?.anchor()
        val request=ReflectionHelpers.callInstanceMethod<CompletionRequest>(main,"completionRequest")
        return "suggested=${LexicalCompletion.complete(request)?.items?.size} task=${ReflectionHelpers.getField<java.util.concurrent.Future<*>?>(main,"completionTask")?.isDone} suppressed=${ReflectionHelpers.getField<CompletionRequest?>(main,"completionSuppressed")} anchorRect=${anchor?.visible?.let {"${it.left},${it.top},${it.right},${it.bottom}"}} caret=${anchor?.top}/${anchor?.bottom} windowFocus=${main.hasWindowFocus()} focus=${editor.hasFocus()} shown=${editor.isShown} size=${editor.width}x${editor.height} selection=${editor.selectionStart}/${editor.selectionEnd} composing=${editor.isComposing} active=${ReflectionHelpers.getField<String>(main,"activePath")} loaded=${ReflectionHelpers.getField<String?>(main,"loadedPath")} anchor=${port?.anchor()} batch=$batch status=${ReflectionHelpers.getField<android.widget.TextView>(main,"status").text}"
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
        loadText("ret");editor.setSelection(3);settle();assertTrue(diagnostics(),visible())
        main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB))
        main.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB))
        assertEquals("return",editor.text.toString())
    }
    @Test fun toolbarTabDoesNotModifyChineseComposition() {
        loadText("")
        val connection=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        connection.setComposingText("拼",1);settle();assertFalse(visible())
        ReflectionHelpers.callInstanceMethod<Unit>(main,"tab")
        assertEquals("拼",editor.text.toString());assertTrue(editor.isComposing)
    }
    @Test fun unfinishedCompositionDoesNotRewriteOrOfferAStaleCandidate() {
        loadText("")
        val info=android.view.inputmethod.EditorInfo()
        val connection=editor.onCreateInputConnection(info)
        connection.setComposingText("pri",1);settle()
        assertFalse(visible());assertEquals("pri",editor.text.toString())
        connection.finishComposingText();settle()
        assertTrue("Committed composition must resume completion: ${diagnostics()}",visible())
        assertFalse(editor.isComposing)
    }
    private fun popup() = ReflectionHelpers.getField<CompletionPopup>(main,"completionPopup")
    private fun key(code:Int,meta:Int=0,repeat:Int=0) {
        val time=android.os.SystemClock.uptimeMillis()
        main.dispatchKeyEvent(KeyEvent(time,time,KeyEvent.ACTION_DOWN,code,repeat,meta,7,0,0,android.view.InputDevice.SOURCE_KEYBOARD))
        main.dispatchKeyEvent(KeyEvent(time,time,KeyEvent.ACTION_UP,code,0,0,7,0,0,android.view.InputDevice.SOURCE_KEYBOARD))
    }
    private fun multiplePythonCandidates() {
        loadText("p");editor.setSelection(1);settle();assertTrue(diagnostics(),visible())
        assertEquals(listOf("pass","print"),ReflectionHelpers.getField<CompletionBatch>(main,"completionBatch").items.take(2).map{it.label})
    }
    @Test fun automaticCandidatesAcceptPhysicalArrowsAndTabWithoutMovingCaret() {
        multiplePythonCandidates()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("Automatic candidate list should retain arrows: ${diagnostics()}",visible())
        assertEquals(1,popup().selectedIndex);assertEquals(1,editor.selectionStart);assertEquals("p",editor.text.toString())
        key(KeyEvent.KEYCODE_DPAD_UP);assertEquals(0,popup().selectedIndex)
        key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_TAB)
        assertEquals("print",editor.text.toString());assertFalse(visible())
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletAutomaticCandidatesAlsoRetainPhysicalArrows() {
        multiplePythonCandidates();key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(diagnostics(),visible());assertEquals(1,popup().selectedIndex);assertEquals(1,editor.selectionStart)
        key(KeyEvent.KEYCODE_TAB);assertEquals("print",editor.text.toString())
    }
    @Test fun holdingArrowClampsAtListEdgesAndEscReturnsToCodeNavigation() {
        loadText("p\nxyz");editor.setSelection(1);settle();assertTrue(visible())
        val size=ReflectionHelpers.getField<CompletionBatch>(main,"completionBatch").items.size
        repeat(20){key(KeyEvent.KEYCODE_DPAD_DOWN,repeat=it)}
        assertTrue(visible());assertEquals(size-1,popup().selectedIndex);assertEquals(1,editor.selectionStart)
        repeat(20){key(KeyEvent.KEYCODE_DPAD_UP,repeat=it)};assertEquals(0,popup().selectedIndex)
        key(KeyEvent.KEYCODE_ESCAPE);assertFalse(visible())
        key(KeyEvent.KEYCODE_DPAD_DOWN);assertTrue("After Esc, arrows should navigate code",editor.selectionStart>1)
        assertEquals("p\nxyz",editor.text.toString())
    }
    @Test fun enterStillCreatesNewlineByDefaultEvenAfterCandidateNavigation() {
        multiplePythonCandidates();key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_ENTER);assertEquals("p\n",editor.text.toString())
    }
    @Test fun configuredEnterAcceptsTheArrowSelectedCandidate() {
        ReflectionHelpers.setField(main,"settings",AppSettings(autoSave=false,animations=false,completionEnter=true))
        multiplePythonCandidates();key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_ENTER)
        assertEquals("print",editor.text.toString())
    }
    @Test fun explicitAndOffModesRemainAvailableAfterTheDefaultChanges() {
        ReflectionHelpers.setField(main,"settings",AppSettings(autoSave=false,animations=false,completionArrows=CompletionArrows.EXPLICIT))
        multiplePythonCandidates()
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle();assertTrue(visible())
        key(KeyEvent.KEYCODE_DPAD_DOWN);assertTrue(visible());assertEquals(1,popup().selectedIndex)
        key(KeyEvent.KEYCODE_ESCAPE)
        ReflectionHelpers.setField(main,"settings",AppSettings(autoSave=false,animations=false,completionArrows=CompletionArrows.OFF))
        loadText("p\nxyz");editor.setSelection(1);settle();assertTrue(visible())
        key(KeyEvent.KEYCODE_DPAD_DOWN);assertTrue(editor.selectionStart>1)
    }
    @Test fun ctrlEqualsAndMinusResizeOnlyCodeAndRememberTheSetting() {
        loadText("abc\ndef");editor.setSelection(1,3)
        val original=ReflectionHelpers.getField<AppSettings>(main,"settings")
        val port=ReflectionHelpers.getField<com.codepocket.ide.editor.DocumentEditorPort>(main,"editorPort")
        val snapshot=port.snapshot()!!.buffer
        val registry=ReflectionHelpers.getField<com.codepocket.ide.terminal.shell.ShellSessionRegistry>(main,"terminalRegistry")
        val sessions=registry.catalog.entries().map{it.id to it.value.runtime}
        val panel=ReflectionHelpers.getField<PanelState>(main,"panelState")
        key(KeyEvent.KEYCODE_EQUALS,KeyEvent.META_CTRL_ON)
        assertEquals(original.fontSize+1,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
        assertEquals(snapshot,port.snapshot()!!.buffer);assertSame(editor,ReflectionHelpers.getField<com.codepocket.editorlab.WorkbenchEditor>(main,"editor"))
        assertEquals(panel,ReflectionHelpers.getField<PanelState>(main,"panelState"))
        assertEquals(sessions,registry.catalog.entries().map{it.id to it.value.runtime})
        val saved=AppSettings.fromJson(main.getSharedPreferences("codepocket_v2",0).getString("settings",null))
        assertEquals(original.fontSize+1,saved.fontSize);assertEquals(original.terminalFontSize,saved.terminalFontSize)
        assertEquals((original.fontSize+1)*editor.resources.displayMetrics.scaledDensity,editor.textSize,0.01f)
        val numbers=ReflectionHelpers.getField<android.widget.TextView>(main,"numbers")
        assertEquals(editor.textSize,numbers.textSize,0.01f)
        key(KeyEvent.KEYCODE_MINUS,KeyEvent.META_CTRL_ON);assertEquals(original.fontSize,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
        assertEquals(snapshot,port.snapshot()!!.buffer)
    }
    @Test fun shiftedPlusNumpadAndRepeatedZoomRespectFontLimits() {
        val ctrl=KeyEvent.META_CTRL_ON
        val original=ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize
        key(KeyEvent.KEYCODE_EQUALS,ctrl or KeyEvent.META_SHIFT_ON)
        key(KeyEvent.KEYCODE_NUMPAD_ADD,ctrl)
        assertEquals(original+2,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
        repeat(25){key(KeyEvent.KEYCODE_PLUS,ctrl,it)}
        assertEquals(28,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
        repeat(30){key(KeyEvent.KEYCODE_NUMPAD_SUBTRACT,ctrl,it)}
        assertEquals(10,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
    }
    @Test fun fontZoomDoesNotDestroyTheNativeUndoHistory() {
        loadText("abc");editor.setSelection(3)
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("d",1)
        assertEquals("abcd",editor.text.toString())
        key(KeyEvent.KEYCODE_PLUS,KeyEvent.META_CTRL_ON)
        assertTrue(editor.onTextContextMenuItem(android.R.id.undo));assertEquals("abc",editor.text.toString())
    }
    @Test fun fontZoomWaitsForCompositionAndIgnoresNonEditorFocus() {
        val original=ReflectionHelpers.getField<AppSettings>(main,"settings")
        awaitEditor()
        val connection=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        connection.setComposingText("拼",1)
        val composing=editor.text.toString();val start=editor.isComposing
        key(KeyEvent.KEYCODE_PLUS,KeyEvent.META_CTRL_ON)
        assertEquals(original.fontSize,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
        assertEquals(composing,editor.text.toString());assertEquals(start,editor.isComposing)
        connection.finishComposingText()
        val input=ReflectionHelpers.getField<android.widget.EditText>(main,"input")
        input.visibility=View.VISIBLE;input.requestFocus()
        key(KeyEvent.KEYCODE_PLUS,KeyEvent.META_CTRL_ON)
        assertEquals(original.fontSize,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
    }

    @Test fun cppAutomaticArrowsChoosePrivateInsteadOfTheFirstPrintfItem() {
        val method=main.javaClass.declaredMethods.single{it.name=="switchFile"}
        method.isAccessible=true;method.invoke(main,"main.cpp",null)
        WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()
        editor=ReflectionHelpers.getField(main,"editor");editor.requestFocus()
        loadText("pri");editor.setSelection(3);settle();assertTrue(diagnostics(),visible())
        assertEquals(listOf("printf","private"),ReflectionHelpers.getField<CompletionBatch>(main,"completionBatch").items.take(2).map{it.label})
        key(KeyEvent.KEYCODE_DPAD_DOWN);assertTrue(visible());assertEquals(1,popup().selectedIndex)
        key(KeyEvent.KEYCODE_TAB);assertEquals("private",editor.text.toString())
    }

    private fun viewTree(view:View):List<View> = listOf(view)+if(view is android.view.ViewGroup)
        (0 until view.childCount).flatMap{viewTree(view.getChildAt(it))} else emptyList()
    private fun openTouchZoomMenu():Pair<android.widget.PopupMenu,android.widget.PopupWindow> {
        viewTree(main.findViewById(android.R.id.content)).single{it.contentDescription=="工作台菜单"}.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val categories=ReflectionHelpers.getField<android.widget.PopupMenu>(main,"commandMenu")
        val categoryWindow=ReflectionHelpers.getField<android.widget.PopupWindow>(categories,"window")
        viewTree(categoryWindow.contentView).single{it.contentDescription=="编辑"}.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val menu=ReflectionHelpers.getField<android.widget.PopupMenu>(main,"commandMenu")
        return menu to ReflectionHelpers.getField(menu,"window")
    }
    private fun touchZoom(label:String) {
        val (menu,window)=openTouchZoomMenu()
        val entry=viewTree(window.contentView).singleOrNull{it.contentDescription?.toString()?.startsWith("代码字号")==true}
        assertNotNull("One hidden font-size entry should exist in the actual editor overflow",entry)
        assertTrue(entry!!.isEnabled);entry.performClick();shadowOf(Looper.getMainLooper()).idle();menu.dismiss()
        val dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        val decor=dialog.window!!.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.widthPixels,View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.heightPixels,View.MeasureSpec.AT_MOST))
        decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
        val button=viewTree(decor).single{it.contentDescription==label}
        assertTrue("Font button should be enabled away from its boundary",button.isEnabled)
        assertTrue("Actual native touch target must be at least 48dp",button.height>=com.codepocket.ide.ui.IdeControls.dp(main,48))
        button.performClick();shadowOf(Looper.getMainLooper()).idle()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun phoneHiddenMenuZoomsCodeAndPreservesNativeHistoryAndSelection() {
        loadText("abc");editor.setSelection(3)
        editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()).commitText("d",1)
        val original=ReflectionHelpers.getField<AppSettings>(main,"settings")
        val port=ReflectionHelpers.getField<com.codepocket.ide.editor.DocumentEditorPort>(main,"editorPort")
        val source=port.snapshot()!!.buffer
        val registry=ReflectionHelpers.getField<com.codepocket.ide.terminal.shell.ShellSessionRegistry>(main,"terminalRegistry")
        val sessions=registry.catalog.entries().map{it.id to it.value.runtime}
        // No extra always-visible zoom buttons: the entry is behind the real editor overflow.
        assertFalse(viewTree(main.findViewById(android.R.id.content)).any{it.contentDescription?.toString()?.startsWith("代码字号")==true})
        touchZoom("放大代码")
        assertEquals(original.fontSize+1,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
        assertEquals(source,port.snapshot()!!.buffer);assertSame(editor,ReflectionHelpers.getField<com.codepocket.editorlab.WorkbenchEditor>(main,"editor"))
        assertEquals(sessions,registry.catalog.entries().map{it.id to it.value.runtime})
        val saved=AppSettings.fromJson(main.getSharedPreferences("codepocket_v2",0).getString("settings",null))
        assertEquals(original.fontSize+1,saved.fontSize)
        touchZoom("缩小代码");assertEquals(original.fontSize,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
        assertTrue(editor.onTextContextMenuItem(android.R.id.undo));assertEquals("abc",editor.text.toString())
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletHiddenZoomRetainsInputFocusAndRespectsComposition() {
        val method=main.javaClass.declaredMethods.single{it.name=="switchFile"}
        method.isAccessible=true;method.invoke(main,"main.cpp",null)
        WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()
        editor=ReflectionHelpers.getField(main,"editor")
        ReflectionHelpers.setField(main,"presetInputExpanded",true)
        ReflectionHelpers.setField(main,"panelVisible",true)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"applyPanelLayout")
        val original=ReflectionHelpers.getField<AppSettings>(main,"settings")
        val text=editor.text.toString()
        val input=ReflectionHelpers.getField<android.widget.EditText>(main,"input")
        input.visibility=View.VISIBLE;assertTrue(input.requestFocus())
        touchZoom("放大代码")
        assertEquals(original.fontSize+1,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
        assertTrue(input.hasFocus());assertEquals(text,editor.text.toString())
        assertEquals(original.terminalFontSize,ReflectionHelpers.getField<AppSettings>(main,"settings").terminalFontSize)
        val connection=input.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        connection.setComposingText("拼",1)
        val inputText=input.text.toString();val start=BaseInputConnection.getComposingSpanStart(input.text)
        val (menu,window)=openTouchZoomMenu()
        try {
            val row=viewTree(window.contentView).single{it.contentDescription?.toString()?.startsWith("代码字号")==true}
            assertFalse(row.isEnabled)
            row.performClick();shadowOf(Looper.getMainLooper()).idle()
            assertEquals(original.fontSize+1,ReflectionHelpers.getField<AppSettings>(main,"settings").fontSize)
            assertEquals(inputText,input.text.toString());assertEquals(start,BaseInputConnection.getComposingSpanStart(input.text))
        }finally{menu.dismiss();connection.finishComposingText()}
    }

    // Drive the real widget's public layout-busy state; providers and popup geometry stay real.
    private fun busyLayout(value:Boolean) {
        ReflectionHelpers.callInstanceMethod<Unit>(editor.inputView(),"setLayoutBusy",
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType,value))
        assertEquals(!value,editor.isLayoutReady)
    }
    private fun manualAssistanceOnly(text:String) {
        val settings=ReflectionHelpers.getField<AppSettings>(main,"settings")
        ReflectionHelpers.setField(main,"settings",settings.copy(autoCompletion=false,signatureHelp=false))
        loadText(text);editor.setSelection(text.length);settle()
        assertFalse(visible())
    }
    private fun signatureVisible() = ReflectionHelpers.getField<SignaturePopup?>(main,"signaturePopup")?.isShowing==true
    @Test fun explicitCompletionWaitsForLayoutWhenAutomaticCompletionIsOff() {
        manualAssistanceOnly("pri");busyLayout(true)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle()
        assertFalse(visible());assertEquals("pri",editor.text.toString())
        busyLayout(false);settle()
        assertTrue("Ctrl+Space must survive layout without another keystroke: ${diagnostics()}",visible())
        key(KeyEvent.KEYCODE_TAB);assertEquals("print",editor.text.toString())
    }
    @Test fun explicitSignatureWaitsForLayoutWhenAutomaticHelpIsOff() {
        manualAssistanceOnly("range(1, ");busyLayout(true)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON);settle()
        assertFalse(signatureVisible())
        busyLayout(false);settle()
        assertTrue("Ctrl+Shift+Space must retain its reference request across layout: ${diagnostics()}",signatureVisible())
        assertEquals("range(1, ",editor.text.toString());assertTrue(editor.hasFocus())
    }

    @Test fun automaticCompletionAndSignatureResumeAfterLayout() {
        loadText("pri");editor.setSelection(3);busyLayout(true);settle();assertFalse(visible())
        busyLayout(false);settle();assertTrue(diagnostics(),visible())
        loadText("range(1, ");editor.setSelection(editor.length());busyLayout(true);settle()
        assertFalse(signatureVisible());busyLayout(false);settle()
        assertTrue("Automatic parameter help must not be lost behind the completion readiness check",signatureVisible())
    }
    @Test fun layoutStartingAfterCandidateCalculationAlsoKeepsTheExplicitRequest() {
        manualAssistanceOnly("pri")
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON)
        ReflectionHelpers.getField<ExecutorService>(main,"completionExecutor").submit{}.get(10,TimeUnit.SECONDS)
        // The real provider has finished, but its main-thread callback has not run yet.
        busyLayout(true);settle();assertFalse(visible())
        busyLayout(false);settle();assertTrue(diagnostics(),visible())
    }
    @Test fun layoutStartingAfterSignatureCalculationAlsoKeepsTheExplicitRequest() {
        manualAssistanceOnly("range(1, ")
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        ReflectionHelpers.getField<ExecutorService>(main,"signatureExecutor").submit{}.get(10,TimeUnit.SECONDS)
        busyLayout(true);settle();assertFalse(signatureVisible())
        busyLayout(false);settle();assertTrue(diagnostics(),signatureVisible())
    }
    @Test fun escapeCancelsTheWaitingRequestWithoutEditingOrReopening() {
        manualAssistanceOnly("pri");busyLayout(true)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle()
        key(KeyEvent.KEYCODE_ESCAPE);busyLayout(false);settle()
        assertFalse(visible());assertFalse(signatureVisible());assertEquals("pri",editor.text.toString())
        assertNull(ReflectionHelpers.getField<Runnable?>(main,"assistLayoutTask"))
    }
    @Test fun waitingRequestCannotStealInputFocusOrReopenAfterWindowFocusLoss() {
        val method=main.javaClass.declaredMethods.single{it.name=="switchFile"}
        method.isAccessible=true;method.invoke(main,"main.cpp",null)
        WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()
        editor=ReflectionHelpers.getField(main,"editor");editor.requestFocus();awaitEditor()
        ReflectionHelpers.setField(main,"presetInputExpanded",true)
        ReflectionHelpers.setField(main,"panelVisible",true)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"applyPanelLayout")
        layout(main.resources.displayMetrics.widthPixels,main.resources.displayMetrics.heightPixels)
        manualAssistanceOnly("pri");busyLayout(true)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle()
        val input=ReflectionHelpers.getField<android.widget.EditText>(main,"input")
        input.visibility=View.VISIBLE;input.setText("terminal input");assertTrue(input.requestFocus())
        busyLayout(false);settle();assertFalse(visible());assertTrue(input.hasFocus());assertEquals("terminal input",input.text.toString())
        editor.requestFocus();busyLayout(true);key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle()
        controller.windowFocusChanged(false);busyLayout(false);settle();assertFalse(visible())
        controller.windowFocusChanged(true);settle();assertFalse(visible())
    }
    @Test fun replacingTheBufferOrChangingSelectionInvalidatesTheWaitingRequest() {
        manualAssistanceOnly("pri");busyLayout(true);key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle()
        loadText("xyz");editor.setSelection(3);settle();assertFalse(visible());assertEquals("xyz",editor.text.toString())
        manualAssistanceOnly("pri");busyLayout(true);key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle()
        editor.setSelection(0,2);busyLayout(false);settle()
        assertFalse(visible());assertEquals(0,editor.selectionStart);assertEquals(2,editor.selectionEnd)
    }
    @Test fun automaticRefreshCannotReplaceTheWaitingExplicitModeAndLatestShortcutWins() {
        manualAssistanceOnly("range(1, pri");busyLayout(true)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        ReflectionHelpers.getField<Runnable>(main,"refreshEditor").run();settle()
        busyLayout(false);settle();assertTrue(diagnostics(),signatureVisible());assertFalse(visible())
        busyLayout(true);key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle();busyLayout(false);settle()
        assertTrue(diagnostics(),visible());assertFalse(signatureVisible())
        key(KeyEvent.KEYCODE_TAB);assertEquals("range(1, print",editor.text.toString())
    }
    @Test fun permanentlyBusyLayoutHasABoundedWaitAndCanBeRequestedAgain() {
        manualAssistanceOnly("pri");busyLayout(true)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2100))
        assertNull(ReflectionHelpers.getField<Runnable?>(main,"assistLayoutTask"));assertFalse(visible())
        assertEquals(main.getString(R.string.assist_layout_pending),ReflectionHelpers.getField<android.widget.TextView>(main,"status").text.toString())
        busyLayout(false);settle();assertFalse(visible())
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON);settle();assertTrue(diagnostics(),visible())
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletRealWordwrapReflowRetainsPhysicalCompletionAndSnippetFields() {
        manualAssistanceOnly("fo")
        editor.setHorizontallyScrolling(false)
        // setText creates an actual asynchronous WordwrapLayout; do not await its main-thread publish.
        editor.setText("fo");editor.setSelection(2)
        assertFalse("The fixture must enter real Sora asynchronous layout",editor.isLayoutReady)
        key(KeyEvent.KEYCODE_SPACE,KeyEvent.META_CTRL_ON)
        awaitEditor();settle();assertTrue(diagnostics(),visible())
        val batch=ReflectionHelpers.getField<CompletionBatch>(main,"completionBatch")
        val snippet=batch.items.indexOfFirst{it.snippetId!=null}
        assertTrue("Real shared snippet must be offered",snippet>=0)
        repeat(snippet){key(KeyEvent.KEYCODE_DPAD_DOWN)};key(KeyEvent.KEYCODE_TAB)
        assertTrue(editor.text.toString().contains("for item in"))
        awaitEditor()
        val input=editor.onCreateInputConnection(android.view.inputmethod.EditorInfo())
        assertTrue(input.commitText("索引",1));key(KeyEvent.KEYCODE_TAB)
        assertEquals("range(10)",editor.text.subSequence(editor.selectionStart,editor.selectionEnd).toString())
        assertTrue(editor.hasFocus())
    }

}
