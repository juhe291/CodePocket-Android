package com.codepocket.editorlab;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class SoraGutterTest {
    private org.robolectric.android.controller.ActivityController<Activity> controller;
    private WorkbenchEditor host;private SoraInputEditor editor;
    @Before public void setup() throws Exception {
        controller=Robolectric.buildActivity(Activity.class).setup().visible();host=new WorkbenchEditor(controller.get());editor=(SoraInputEditor)host.inputView();
        controller.get().setContentView(host);controller.windowFocusChanged(true);host.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));host.layout(0,0,360,400);host.requestFocus();
        host.replaceDocument("gutter","abc\nsecond row");ready();host.setSelection(0);host.finishMotion();
    }
    private void ready() throws Exception {for(int i=0;i<200;i++){Shadows.shadowOf(Looper.getMainLooper()).idle();if(host.isLayoutReady())return;Thread.sleep(5);}fail("Layout not ready");}
    @After public void close(){host.close();controller.pause().stop().destroy();}
    @Test public void nativeGutterHasRoomWithoutChangingLogicalSelection(){
        float dp=editor.getResources().getDisplayMetrics().density;assertEquals(10*dp,editor.getLineNumberMarginLeft(),.1f);assertEquals(8*dp,editor.getDividerMarginLeft(),.1f);assertEquals(12*dp,editor.getDividerMarginRight(),.1f);
        assertTrue(editor.measureTextRegionOffset()>=31*dp);assertEquals(0,host.getSelectionStart());assertEquals("abc\nsecond row",host.getText().toString());
    }
    @Test public void publicLayoutPopupAnchorAndHitTestingAgreeWithNewInset(){
        var anchor=host.anchor();assertNotNull(anchor);int[] xy=new int[2];editor.getLocationOnScreen(xy);
        assertEquals(xy[0]+Math.round(editor.measureTextRegionOffset()),anchor.x,1);
        long hit=editor.getPointPositionOnScreen(editor.measureTextRegionOffset()+1,editor.getRowHeight()/2f);
        assertEquals(0,io.github.rosemoe.sora.util.IntPair.getFirst(hit));assertEquals(0,io.github.rosemoe.sora.util.IntPair.getSecond(hit));
    }
    @Test public void hiddenLineNumbersKeepTextInsetAndNativeWrapping() throws Exception {
        host.setLineNumbers(false);host.setHorizontallyScrolling(false);ready();host.setText("value = "+"x".repeat(100));ready();host.setSelection(0);host.finishMotion();
        assertEquals(12*editor.getResources().getDisplayMetrics().density,editor.measureTextRegionOffset(),.1f);
        assertNotNull(host.anchor());assertEquals(0,host.getSelectionStart());assertTrue(editor.getLayout().getRowCountForLine(0)>1);
    }
}
