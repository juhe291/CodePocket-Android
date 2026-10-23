package com.codepocket.editorlab;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Real fixed-version editor/IME/layout/renderer. No alternate cursor positions or input implementation. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class SoraCaretMotionTest {
    private ActivityController<Activity> activity;
    private SoraInputEditor editor;
    private SoraCaretMotion motion;
    private Bitmap bitmap;
    private void idle(long ms) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
    private void frame() { bitmap.eraseColor(0); editor.draw(new Canvas(bitmap)); }
    private ValueAnimator moving() { return ReflectionHelpers.getField(motion,"movement"); }
    private ValueAnimator blinking() { return ReflectionHelpers.getField(motion,"blinking"); }
    private void layout(int width,int height) { editor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));editor.layout(0,0,width,height); }
    private void awaitLayout() throws Exception {
        for(int i=0;i<200;i++) { idle(0); if(editor.isEditable()) return; Thread.sleep(5); }
        fail("Actual asynchronous Sora layout did not publish");
    }
    private float realX() { var c=editor.getCursor();return editor.measureTextRegionOffset()+editor.getLayout().getCharLayoutOffset(c.getLeftLine(),c.getLeftColumn())[1]; }
    @Before public void setup() {
        activity=Robolectric.buildActivity(Activity.class).setup().visible();
        editor=new SoraInputEditor(activity.get());editor.installCaretMotion();editor.setText("abcdef ghijklmnop\nsecond row\nthird row");
        activity.get().setContentView(editor);activity.windowFocusChanged(true);layout(600,400);editor.requestFocus();
        editor.setSelection(0,0);motion=(SoraCaretMotion)editor.getCursorAnimator();editor.configureCaretMotion(true,true,true);
        bitmap=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888);frame();
    }
    @After public void cleanup() { editor.release();bitmap.recycle();activity.pause().stop().destroy(); }
    @Test public void ordinaryImeTypingCommitsBeforeItsSixtyMillisecondTween() {
        float x=realX();assertTrue(editor.onCreateInputConnection(new EditorInfo()).commitText("x",1));
        assertEquals('x',editor.getText().charAt(0));assertEquals(1,editor.getCursor().getLeft());
        assertNotNull(moving());assertEquals(60,moving().getDuration());assertEquals(x,motion.animatedX(),.1f);
        moving().setCurrentFraction(1);assertEquals(realX(),motion.animatedX(),.1f);
    }
    @Test public void backspaceUsesSixtyMillisecondsWithoutDelayingDeletion() {
        editor.setSelection(0,3);motion.align();assertTrue(editor.onCreateInputConnection(new EditorInfo()).deleteSurroundingText(1,0));
        assertEquals("abdef ghijklmnop\nsecond row\nthird row",editor.getText().toString());assertEquals(2,editor.getCursor().getLeft());
        assertNotNull(moving());assertEquals(60,moving().getDuration());
    }
    @Test public void shortNavigationIsEightyMillisecondsAndKeepsTextAndRevision() {
        long revision=editor.getText().getDocumentVersion();float x=realX();editor.setSelection(0,4);
        assertEquals(4,editor.getCursor().getLeft());assertEquals(revision,editor.getText().getDocumentVersion());assertEquals(x,motion.animatedX(),.1f);
        assertNotNull(moving());assertEquals(80,moving().getDuration());moving().setCurrentFraction(1);assertEquals(realX(),motion.animatedX(),.1f);
    }
    @Test public void rapidInputImmediatelyTakesOverTheCurrentVisualPosition() {
        var ic=editor.onCreateInputConnection(new EditorInfo());assertTrue(ic.commitText("x",1));ValueAnimator old=moving();old.setCurrentFraction(.4f);float x=motion.animatedX();
        assertTrue(ic.commitText("y",1));assertFalse(old.isRunning());assertNotSame(old,moving());assertEquals(x,motion.animatedX(),.1f);
        assertEquals("xyabcdef ghijklmnop\nsecond row\nthird row",editor.getText().toString());assertEquals(2,editor.getCursor().getLeft());assertEquals(60,moving().getDuration());
    }
    @Test public void compositionAlwaysUsesRealCaretAndNeverFadesOrTweens() {
        var ic=editor.onCreateInputConnection(new EditorInfo());assertTrue(ic.setComposingText("拼",1));frame();
        assertTrue(editor.hasComposingText());assertFalse(motion.isRunning());assertNull(blinking());assertEquals(1f,motion.frameOpacity(),0f);
        assertTrue(ic.setComposingText("拼音",1));frame();assertEquals(2,editor.getCursor().getLeft());assertFalse(motion.isRunning());
        assertTrue(ic.finishComposingText());frame();assertFalse(editor.hasComposingText());assertEquals("拼音abcdef ghijklmnop\nsecond row\nthird row",editor.getText().toString());
    }
    @Test public void selectionsDragsAndDistantJumpsSnapInsteadOfAnimating() {
        editor.setSelectionRegion(0,1,0,4);frame();assertFalse(motion.isRunning());assertNull(blinking());
        editor.setSelection(0,0);motion.align();motion.dragging(true);editor.setSelection(0,4);frame();assertFalse(motion.isRunning());
        motion.dragging(false);editor.setText("line\n".repeat(100));editor.setSelection(90,0);frame();assertFalse(motion.isRunning());assertEquals(90,editor.getCursor().getLeftLine());
    }
    @Test public void bulkInsertAndUndoAreImmediate() {
        editor.onCreateInputConnection(new EditorInfo()).commitText("large paste",1);frame();assertFalse(motion.isRunning());assertEquals(11,editor.getCursor().getLeft());
        editor.undo();frame();assertFalse(motion.isRunning());assertEquals("abcdef ghijklmnop\nsecond row\nthird row",editor.getText().toString());
    }
    @Test public void movementAndBlinkSwitchesAreIndependent() {
        editor.configureCaretMotion(true,false,true);editor.setSelection(0,3);frame();assertFalse(motion.isRunning());idle(650);assertNotNull(blinking());
        editor.configureCaretMotion(true,true,false);assertNull(blinking());editor.setSelection(0,5);assertTrue(motion.isRunning());
        motion.align();idle(1200);assertNull(blinking());assertEquals(1f,motion.frameOpacity(),0f);
    }
    @Test public void globalDisableCancelsExistingMovementAndIdleBlink() {
        editor.setSelection(0,4);assertTrue(motion.isRunning());editor.configureCaretMotion(false,true,true);assertFalse(motion.isRunning());
        frame();idle(1200);assertNull(blinking());assertEquals(1f,motion.frameOpacity(),0f);editor.setSelection(0,5);assertFalse(motion.isRunning());
    }
    @Test public void softBlinkUsesPaintOnlyAndRestoresTheOwnedPalette() {
        editor.getColorScheme().setColor(EditorColorScheme.SELECTION_INSERT,0xff20a0ef);motion.align();frame();
        int[] before=new int[600*400];bitmap.getPixels(before,0,600,0,0,600,400);long revision=editor.getText().getDocumentVersion();int[] updates={0};
        editor.subscribeEvent(ColorSchemeUpdateEvent.class,(e,r)->updates[0]++);idle(650);assertNotNull(blinking());blinking().setCurrentFraction(.5f);frame();
        assertEquals(.15f,motion.frameOpacity(),.01f);assertEquals(0xff20a0ef,editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT));assertEquals(0,updates[0]);assertEquals(revision,editor.getText().getDocumentVersion());
        int[] after=new int[600*400];bitmap.getPixels(after,0,600,0,0,600,400);int changed=0;for(int i=0;i<before.length;i++)if(before[i]!=after[i])changed++;
        assertTrue("Actual renderer must paint the faded caret",changed>0);assertTrue("No text-area movement or recoloring",changed<editor.getRowHeight()*10);
    }
    @Test public void idleBlinkRestartsAfterInputAndLeavesNoOldAnimator() {
        idle(650);ValueAnimator old=blinking();assertNotNull(old);old.setCurrentFraction(.5f);editor.onCreateInputConnection(new EditorInfo()).commitText("x",1);
        assertFalse(old.isRunning());assertNull(blinking());assertEquals(1f,motion.frameOpacity(),0f);idle(650);assertNotNull(blinking());assertNotSame(old,blinking());
    }
    @Test public void resizeAndExplicitPopupAlignmentCancelTheVisualTween() {
        editor.setSelection(0,4);assertTrue(motion.isRunning());layout(550,390);frame();assertFalse(motion.isRunning());
        editor.setSelection(0,2);assertTrue(motion.isRunning());motion.align();assertFalse(motion.isRunning());assertEquals(realX(),motion.animatedX(),.1f);
    }
    @Test public void fontAndGutterChangesImmediatelyUseTheNewNativeGeometry() throws Exception {
        editor.setSelection(0,4);assertTrue(motion.isRunning());editor.setTextSize(22);frame();assertFalse(motion.isRunning());awaitLayout();frame();
        assertEquals(realX(),motion.animatedX(),.1f);editor.setSelection(0,2);assertTrue(motion.isRunning());editor.setLineNumberEnabled(false);frame();assertFalse(motion.isRunning());awaitLayout();frame();
        assertEquals(realX(),motion.animatedX(),.1f);assertEquals(2,editor.getCursor().getLeft());
    }
    @Test public void focusRoundTripKeepsNativeInsertionCaretVisibleAndStopsHiddenBlink() {
        idle(650);assertNotNull(blinking());editor.clearFocus();frame();assertNull(blinking());assertFalse(motion.isRunning());
        editor.requestFocus();motion.align();frame();idle(650);assertNotNull(blinking());assertTrue(editor.hasFocus());
        int color=editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT);frame();assertEquals(color,editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT));
    }
    @Test public void fallbackChromeUsesTheSameMutedColorsInBothThemes() {
        SoraSyntaxController syntax=new SoraSyntaxController(editor,ignored -> {});editor.setCaretColor(0xffaecdff);
        try {
            syntax.configure(SyntaxLanguage.PYTHON,true);idle(0);
            assertEquals(0xff303846,editor.getColorScheme().getColor(EditorColorScheme.LINE_DIVIDER));
            assertEquals(0xff858585,editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER));
            assertEquals(0xff262626,editor.getColorScheme().getColor(EditorColorScheme.CURRENT_LINE));
            assertEquals(0xffaecdff,editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT));
            syntax.configure(SyntaxLanguage.PYTHON,false);idle(0);
            assertEquals(0xffdbe2eb,editor.getColorScheme().getColor(EditorColorScheme.LINE_DIVIDER));
            assertEquals(0xff707070,editor.getColorScheme().getColor(EditorColorScheme.LINE_NUMBER));
            assertEquals(0xfff3f3f3,editor.getColorScheme().getColor(EditorColorScheme.CURRENT_LINE));
            assertEquals(0xffaecdff,editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT));
        } finally { syntax.close(); }
    }
    @Test public void configuredCaretColorSurvivesGrammarAndFallbackPaletteReplacement() {
        editor.setCaretColor(0xffaecdff);editor.setColorScheme(new io.github.rosemoe.sora.widget.schemes.SchemeGitHub());
        assertEquals(0xffaecdff,editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT));
        editor.setColorScheme(new io.github.rosemoe.sora.widget.schemes.SchemeVS2019());motion.align();frame();idle(650);blinking().setCurrentFraction(.5f);frame();
        assertEquals(0xffaecdff,editor.getColorScheme().getColor(EditorColorScheme.SELECTION_INSERT));
    }
    @Test public void pauseAndDetachReleaseEveryAnimationAndResumeCanBlinkAgain() {
        editor.setSelection(0,4);motion.pause();assertFalse(motion.isRunning());idle(700);assertNull(blinking());motion.resume();frame();idle(650);assertNotNull(blinking());
        activity.get().setContentView(new View(activity.get()));assertNull(blinking());assertFalse(motion.isRunning());
    }
}
