package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import com.codepocket.ide.animation.*
import com.codepocket.ide.ui.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

/** Real Views/overlays/platform transitions. Host state/geometry evidence, not device GPU or PTY evidence. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class FullWorkbenchMotionTest {
    private lateinit var activity:Activity
    private lateinit var root:LinearLayout
    private lateinit var editor:EditText
    private lateinit var pane:LinearLayout
    private var windowWidth=1000
    private var windowHeight=700
    private fun layout(width:Int=windowWidth,height:Int=windowHeight){root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height)}
    private fun frame(){root.viewTreeObserver.dispatchOnPreDraw()}
    private fun idle(){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))}
    @Before fun prepare(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible();activity=controller.get();controller.windowFocusChanged(true)
        root=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL}
        editor=EditText(activity).apply {setText("keep document");setSelection(4)}
        pane=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;isFocusableInTouchMode=true}
        root.addView(editor,LinearLayout.LayoutParams(-1,0,1f));root.addView(pane,LinearLayout.LayoutParams(-1,200))
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        // Use the actual host window after its initial traversal; later traversals must not resize a forced test viewport.
        windowWidth=root.width;windowHeight=root.height
        layout();editor.requestFocus()
    }
    @After fun cleanup(){Animations.cancelTree(root)}
    @Test fun panelCloseUsesANonInteractiveOverlayAndHidesTheRealPaneImmediately(){
        SurfaceMotion.outgoing(root,pane,true,dy=12);pane.visibility=View.GONE;layout();frame()
        assertEquals(View.GONE,pane.visibility);assertEquals(2,root.childCount);assertTrue(editor.hasFocus());assertEquals(4,editor.selectionStart)
        assertNotNull(root.getTag(R.id.workbench_surface_motion));idle();assertNull(root.getTag(R.id.workbench_surface_motion))
    }
    @Test fun reopeningCancelsTheOldClosingSnapshot(){
        SurfaceMotion.outgoing(root,pane,true);pane.visibility=View.GONE;frame()
        SurfaceMotion.finish(root);pane.visibility=View.VISIBLE;Animations.enterPanel(pane,true);idle()
        assertEquals(View.VISIBLE,pane.visibility);assertEquals(1f,pane.alpha,0f);assertEquals(0f,pane.translationY,0f)
        assertNull(root.getTag(R.id.workbench_surface_motion))
    }
    @Test fun themeCrossfadeRetainsFocusSelectionAndHasNoExtraViewOrTouchTarget(){
        val snapshot=SurfaceMotion.capture(root,true)!!
        root.setBackgroundColor(0xffffffff.toInt());SurfaceMotion.play(root,snapshot,matchRootSize=true);frame()
        assertTrue(editor.hasFocus());assertEquals(4,editor.selectionStart);assertEquals(2,root.childCount)
        idle();assertNull(root.getTag(R.id.workbench_surface_motion));assertTrue(snapshot.bitmap.isRecycled)
    }
    @Test fun themeSnapshotIsDiscardedWhenTabletWindowChangesSize(){
        val snapshot=SurfaceMotion.capture(root,true)!!
        SurfaceMotion.play(root,snapshot,Rect(0,0,root.width,root.height),matchRootSize=true)
        layout(600,800);frame();assertNull(root.getTag(R.id.workbench_surface_motion));assertTrue(snapshot.bitmap.isRecycled)
    }
    @Test fun highResolutionTabletSnapshotsHaveABoundedPixelBudget(){
        layout(3840,2160);val snapshot=SurfaceMotion.capture(root,true)!!
        assertTrue(snapshot.bitmap.width.toLong()*snapshot.bitmap.height<=2_000_000)
        assertEquals(3840,snapshot.width);assertEquals(2160,snapshot.height);snapshot.release()
    }
    @Test fun repeatedSnapshotsAndDetachReleaseThePreviousBitmap(){
        val first=SurfaceMotion.capture(root,true)!!;SurfaceMotion.play(root,first)
        val second=SurfaceMotion.capture(root,true)!!;SurfaceMotion.play(root,second)
        assertTrue(first.bitmap.isRecycled)
        activity.setContentView(View(activity));assertTrue(second.bitmap.isRecycled)
        assertNull(root.getTag(R.id.workbench_surface_motion))
    }
    @Test fun panelBoundsTransitionDoesNotRelayoutItsContentOnEveryAnimationFrame(){
        class CountedView:View(activity){var changes=0;override fun onSizeChanged(w:Int,h:Int,ow:Int,oh:Int){super.onSizeChanged(w,h,ow,oh);changes++}}
        val content=CountedView();pane.addView(content,LinearLayout.LayoutParams(-1,-1));layout();content.changes=0
        LayoutMotion.begin(root,true,pane);pane.layoutParams=LinearLayout.LayoutParams(-1,350);layout();frame();idle()
        assertTrue(content.changes<=1);assertEquals(350,content.height);assertEquals(350,pane.height)
        assertTrue(editor.hasFocus());assertEquals(4,editor.selectionStart)
        LayoutMotion.finish(root);assertNull(root.getTag(R.id.workbench_layout_motion))
    }
    @Test fun treeCollapseCompletesAndCancellationClearsPendingPlatformTransitions(){
        val list=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL};pane.addView(list)
        KeyedViews.update(list,listOf("src","src/a.py","main.py"),{View(activity).apply {minimumHeight=48}}){_,_,_->};layout()
        LayoutMotion.begin(list,true)
        KeyedViews.update(list,listOf("src","main.py"),{View(activity)}){_,_,_->};layout();frame()
        Animations.cancelTree(list);assertEquals(2,list.childCount);assertNull(list.getTag(R.id.workbench_layout_motion));idle()
        assertTrue(editor.hasFocus())
    }
    @Test fun tabSelectionIndicatorMovesWithoutMovingTabsOrLosingTheirFocus(){
        val tabs=EditorTabs(activity,DesignTokens.palette(true))
        val one=EditorTab(activity,DesignTokens.palette(true),"a.py",{},{},{})
        val two=EditorTab(activity,DesignTokens.palette(true),"b.cpp",{},{},{})
        tabs.addView(one);tabs.addView(two);pane.addView(tabs);layout()
        tabs.select(one,false);val originalLeft=two.left;tabs.select(two,true)
        val animator=ReflectionHelpers.getField<android.animation.ValueAnimator?>(tabs,"animator");assertNotNull(animator)
        tabs.select(two,true);assertSame(animator,ReflectionHelpers.getField(tabs,"animator"))
        idle();assertEquals(two.left.toFloat(),ReflectionHelpers.getField<Float>(tabs,"leftEdge"),.1f)
        assertEquals(originalLeft,two.left);assertTrue(editor.hasFocus())
        tabs.finishMotion();assertEquals(two.right.toFloat(),ReflectionHelpers.getField<Float>(tabs,"rightEdge"),.1f)
    }
    private fun switches(view:View):List<Switch> = if(view is Switch) listOf(view) else if(view is android.view.ViewGroup) (0 until view.childCount).flatMap {switches(view.getChildAt(it))} else emptyList()
    @Test fun settingsCancelDiscardsCursorChangesAndApplyPersistsThem(){
        var saved:AppSettings?=null
        val original=AppSettings()
        val first=SettingsDialog.show(activity,original){saved=it}
        switches(first.window!!.decorView).filter {it.text.toString().contains("光标")}.forEach {it.isChecked=false}
        first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertNull(saved)
        val second=SettingsDialog.show(activity,original){saved=it}
        switches(second.window!!.decorView).filter {it.text.toString().contains("光标")}.forEach {it.isChecked=false}
        second.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertFalse(saved!!.cursorMovement);assertFalse(saved!!.cursorBlink);assertEquals(saved,AppSettings.fromJson(saved!!.toJson()))
    }
    @Test fun dialogWindowAnimationHonorsAppDisable(){
        val dialog=DialogMotion.show(AlertDialog.Builder(activity).setMessage("test").create(),false)
        assertEquals(0,dialog.window!!.attributes.windowAnimations);dialog.dismiss()
    }
    @Test fun largeTreesSkipNativeReflowInsteadOfSchedulingHundredsOfAnimations() {
        val list=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL}
        repeat(LayoutMotion.MAX_TREE_ITEMS+1){list.addView(View(activity),LinearLayout.LayoutParams(10,10))}
        pane.addView(list);layout();LayoutMotion.begin(list,true)
        assertNull(list.getTag(R.id.workbench_layout_motion));assertTrue(editor.hasFocus())
    }
    @Test fun themeHandoverTransfersOnlyTheBitmapAndDoesNotKeepTheOldActivity() {
        val main=Robolectric.buildActivity(MainActivity::class.java).get()
        val snapshot=SurfaceMotion.capture(root,true)!!
        ReflectionHelpers.setField(main,"themeSnapshot",snapshot)
        assertSame(snapshot,main.onRetainNonConfigurationInstance())
        assertNull(ReflectionHelpers.getField<SurfaceMotion.Snapshot?>(main,"themeSnapshot"))
        snapshot.release()
    }

    @Test fun phoneAndNarrowTabletDrawersUseALeftTransitionAndRespectAppOff() {
        val animated=DialogMotion.show(AlertDialog.Builder(activity).setMessage("files").create(),true,drawer=true)
        assertEquals(R.style.WorkbenchDrawerWindowMotion,animated.window!!.attributes.windowAnimations);animated.dismiss()
        val disabled=DialogMotion.show(AlertDialog.Builder(activity).setMessage("files").create(),false,drawer=true)
        assertEquals(0,disabled.window!!.attributes.windowAnimations);disabled.dismiss()
    }
    @Test fun nativeContextMenuStillUsesTheRealMenuAndItsCommandCallback() {
        var clicked=false
        val menu=DialogMotion.menu(activity,editor,true)
        val item=menu.menu.add(0,42,0,"run")
        menu.setOnMenuItemClickListener {clicked=it.itemId==42;true};menu.show()
        assertTrue(menu.menu.performIdentifierAction(item.itemId,0));assertTrue(clicked);menu.dismiss()
        val plain=DialogMotion.menu(activity,editor,false);plain.menu.add("files");plain.show();plain.dismiss()
    }

    @Test fun followingSystemThemeCapturesABoundedConfigurationTransitionWithoutKeepingAView() {
        val main=Robolectric.buildActivity(MainActivity::class.java).get()
        ReflectionHelpers.setField(main,"root",root);ReflectionHelpers.setField(main,"settings",AppSettings(theme=UiTheme.SYSTEM))
        val snapshot=main.onRetainNonConfigurationInstance() as SurfaceMotion.Snapshot
        assertEquals(root.width,snapshot.width);assertEquals(root.height,snapshot.height)
        assertNull(ReflectionHelpers.getField<SurfaceMotion.Snapshot?>(main,"themeSnapshot"));snapshot.release()
    }

    @Test fun configurationCheckpointKeepsTheExpandedTreeAndItsViewport() {
        val main=Robolectric.buildActivity(MainActivity::class.java).get()
        val expanded=ReflectionHelpers.getField<MutableSet<String>>(main,"expanded")
        expanded.addAll(listOf("src","src/nested"))
        ReflectionHelpers.setField(main,"restoredExplorerScroll",180)
        val state=android.os.Bundle()
        ReflectionHelpers.callInstanceMethod<Any?>(main,"saveExplorerViewport",ReflectionHelpers.ClassParameter.from(android.os.Bundle::class.java,state))
        val recreated=Robolectric.buildActivity(MainActivity::class.java).get()
        ReflectionHelpers.callInstanceMethod<Any?>(recreated,"restoreExplorerViewport",
            ReflectionHelpers.ClassParameter.from(android.os.Bundle::class.java,state))
        assertTrue(ReflectionHelpers.getField<MutableSet<String>>(recreated,"expanded").containsAll(listOf("src","src/nested")))
        assertEquals(180,ReflectionHelpers.getField<Int>(recreated,"restoredExplorerScroll"))
    }
    @Test fun explorerRestoreRejectsNegativeScrollAndBoundsItsConfigurationPayload() {
        val main=Robolectric.buildActivity(MainActivity::class.java).get()
        val state=android.os.Bundle().apply {putInt("explorerScroll",-50);putStringArrayList("expandedDirectories",ArrayList((0..800).map {"folder$it"}))}
        ReflectionHelpers.callInstanceMethod<Any?>(main,"restoreExplorerViewport",ReflectionHelpers.ClassParameter.from(android.os.Bundle::class.java,state))
        assertEquals(0,ReflectionHelpers.getField<Int>(main,"restoredExplorerScroll"))
        assertEquals(513,ReflectionHelpers.getField<MutableSet<String>>(main,"expanded").size)
        val expanded=ReflectionHelpers.getField<MutableSet<String>>(main,"expanded")
        expanded.clear();expanded.addAll((0..800).map {"folder$it/"+"x".repeat(1000)})
        val checkpoint=android.os.Bundle()
        ReflectionHelpers.callInstanceMethod<Any?>(main,"saveExplorerViewport",ReflectionHelpers.ClassParameter.from(android.os.Bundle::class.java,checkpoint))
        val paths=checkpoint.getStringArrayList("expandedDirectories")!!
        assertTrue(paths.size<=512);assertTrue(paths.sumOf {it.length}<=32768)
    }

}
