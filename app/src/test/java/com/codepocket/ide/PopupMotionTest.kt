package com.codepocket.ide

import android.app.Activity
import android.widget.EditText
import android.widget.LinearLayout
import com.codepocket.editor.surface.EditorSurface
import com.codepocket.editor.surface.PopupGeometry
import com.codepocket.ide.ui.DesignTokens
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

/** Actual anchored PopupWindows retain their entrance while successive analysis batches replace the contents. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class PopupMotionTest {
    private lateinit var activity:Activity
    private lateinit var editor:EditText
    private lateinit var request:CompletionRequest
    private lateinit var surface:EditorSurface
    private var completion:CompletionPopup?=null
    private var signature:SignaturePopup?=null
    private val colors=DesignTokens.palette(true)
    @Before fun prepare() {
        activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        editor=EditText(activity);activity.setContentView(editor);editor.requestFocus()
        surface=EditorSurface {PopupGeometry.anchor(request.document,request.revision,request.caret,
            EditorSurface.Bounds(0,0,480,640),100.0,120.0,144.0)}
        select("pri",1)
    }
    private fun select(code:String,revision:Long) {
        editor.setText(code);editor.setSelection(code.length)
        request=CompletionRequest("file:main.py",revision,code,code.length,code.length,false)
    }
    @After fun cleanup() {completion?.dismiss();signature?.dismiss()}
    @Test fun actualCompletionUpdatesDoNotReplayEntranceOrTakeEditorFocus() {
        val popup=CompletionPopup(activity,editor,surface,colors.panel,colors.foreground,colors.muted,colors.accent,{},{}).also {completion=it}
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,true))
        val content=ReflectionHelpers.getField<LinearLayout>(popup,"content")
        val entrance=content.getTag(R.id.workbench_motion);assertNotNull(entrance)
        select("prin",2);assertTrue(popup.show(LexicalCompletion.complete(request)!!,true))
        assertSame(entrance,content.getTag(R.id.workbench_motion));assertTrue(editor.hasFocus());assertEquals(4,editor.selectionStart)
        assertEquals(0f,content.translationX,0f);assertEquals(0f,content.translationY,0f)
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,false))
        assertEquals(1f,content.alpha,0f);assertNull(content.getTag(R.id.workbench_motion))
        popup.dismiss();assertFalse(popup.isShowing)
    }
    @Test fun actualSignatureUpdatesDoNotReplayEntranceAndDismissCancelsIt() {
        select("print(",1)
        val popup=SignaturePopup(activity,editor,surface,colors.panel,colors.foreground,colors.muted,colors.accent,{}).also {signature=it}
        assertTrue(popup.show(SignatureHelp.find(request)!!,true))
        val content=ReflectionHelpers.getField<LinearLayout>(popup,"content")
        val entrance=content.getTag(R.id.workbench_motion);assertNotNull(entrance)
        select("print(1,",2);assertTrue(popup.show(SignatureHelp.find(request)!!,true))
        assertSame(entrance,content.getTag(R.id.workbench_motion));assertTrue(editor.hasFocus());assertEquals(8,editor.selectionStart)
        popup.dismiss();assertFalse(popup.isShowing);assertEquals(1f,content.alpha,0f)
        assertNull(content.getTag(R.id.workbench_motion))
    }
    @Test fun inputReferenceDescribesTheRealInteractiveTerminal() {
        select("inp",1)
        val hint=LexicalCompletion.complete(request)!!.items.single {it.label=="input"}.detail
        val signature=BuiltinSignatures.find("input",false)!!.description
        for(text in listOf(hint,signature)) {assertTrue(text.contains("终端"));assertFalse(text.contains("预填"))}
    }
    @Test fun completionDismissCanReopenImmediatelyWithoutAStaleExitHidingIt() {
        val popup=CompletionPopup(activity,editor,surface,colors.panel,colors.foreground,colors.muted,colors.accent,{},{}).also {completion=it}
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,true))
        popup.dismiss();assertFalse(popup.isShowing)
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,true))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        assertTrue(popup.isShowing);assertTrue(editor.hasFocus())
        popup.dismiss(false);assertFalse(popup.isShowing)
    }
    @Test fun disabledMotionHasNoNativeExitTransition() {
        val popup=CompletionPopup(activity,editor,surface,colors.panel,colors.foreground,colors.muted,colors.accent,{},{}).also {completion=it}
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,false))
        val window=ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup")
        assertNull(window.exitTransition);popup.dismiss(false)
    }

    @Test fun rapidTypingReopenDoesNotFadeTheSameDocumentAgain() {
        val popup=CompletionPopup(activity,editor,surface,colors.panel,colors.foreground,colors.muted,colors.accent,{},{}).also {completion=it}
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,true))
        val content=ReflectionHelpers.getField<LinearLayout>(popup,"content")
        assertNotNull(content.getTag(R.id.workbench_motion))
        popup.dismiss(false);select("prin",2)
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,true))
        assertNull(content.getTag(R.id.workbench_motion));assertEquals(1f,content.alpha,0f);assertTrue(editor.hasFocus())
    }
    @Test fun anotherDocumentStillGetsItsOwnEntranceAfterRapidDismiss() {
        val popup=CompletionPopup(activity,editor,surface,colors.panel,colors.foreground,colors.muted,colors.accent,{},{}).also {completion=it}
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,true));popup.dismiss(false)
        request=request.copy(document="file:another.py")
        assertTrue(popup.show(LexicalCompletion.complete(request)!!,true))
        val content=ReflectionHelpers.getField<LinearLayout>(popup,"content")
        assertNotNull(content.getTag(R.id.workbench_motion))
    }

}
