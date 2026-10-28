package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.codepocket.ide.animation.DialogMotion
import com.codepocket.ide.ui.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.time.Duration

/** DOWN/UP through actual dialog/list dispatch, never performClick or performItemClick. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class SettingsTouchIntegrationTest {
    private lateinit var activity:Activity
    private val dialogs=mutableListOf<AlertDialog>()
    private val original=AppSettings(animations=true,autoSave=false)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220))}
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
    @Before fun launch(){
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("settings",original.toJson()).commit()
        activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
    }
    @After fun close(){dialogs.asReversed().filter{it.isShowing}.forEach{it.dismiss()};if(::activity.isInitialized)activity.finish();idle();RuntimeEnvironment.setFontScale(1f)}
    private fun track(dialog:AlertDialog):AlertDialog {if(dialog !in dialogs)dialogs.add(dialog);layout(dialog);return dialog}
    private fun layout(dialog:AlertDialog){
        idle();val decor=dialog.window!!.decorView;val dm=decor.resources.displayMetrics
        val width=minOf(dialog.window!!.attributes.width.takeIf{it>0}?:dm.widthPixels,dm.widthPixels)
        val full=dialog.window!!.attributes.height==ViewGroup.LayoutParams.MATCH_PARENT
        repeat(2){decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,if(full)View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);decor.viewTreeObserver.dispatchOnGlobalLayout();idle()}
    }
    private fun tap(dialog:AlertDialog,target:View){
        layout(dialog);val rect=Rect();assertTrue("Touch target must be visible: ${target.contentDescription}",target.getGlobalVisibleRect(rect));assertTrue(rect.width()>0 && rect.height()>0)
        val origin=IntArray(2);dialog.window!!.decorView.getLocationOnScreen(origin)
        val x=rect.centerX()-origin[0];val y=rect.centerY()-origin[1];val time=SystemClock.uptimeMillis()
        for((action,delay) in listOf(MotionEvent.ACTION_DOWN to 0L,MotionEvent.ACTION_UP to 30L)){
            val event=MotionEvent.obtain(time,time+delay,action,x.toFloat(),y.toFloat(),0).apply{source=InputDevice.SOURCE_TOUCHSCREEN}
            try{dialog.dispatchTouchEvent(event)}finally{event.recycle()}
        };idle()
    }
    private fun setting(dialog:AlertDialog,title:String)=views(dialog.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()==title}.parent.parent as View
    private fun option(dialog:AlertDialog,index:Int):View {
        val list=dialog.listView;list.setSelection(index);layout(dialog)
        return list.getChildAt(index-list.firstVisiblePosition).also{assertNotNull("Option $index must be laid out",it)}
    }
    private fun capture(name:String,dialog:AlertDialog){
        layout(dialog);val decor=dialog.window!!.decorView;val image=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888);decor.draw(Canvas(image))
        val directory=File(CppFixtures.root,"verification/settings-touch-fix-2026-10-28/previews").apply{mkdirs()}
        File(directory,"$name.png").outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }
    @Test fun phoneSettingsThemeCanBeChosenByTouchAndRemainsDraftUntilApply(){
        var saved:AppSettings?=null;val settings=track(SettingsDialog.show(activity,original){saved=it})
        tap(settings,views(settings.window!!.decorView).single{it.contentDescription=="外观"})
        tap(settings,setting(settings,"界面主题"));val picker=track(ShadowAlertDialog.getLatestAlertDialog())
        assertNotSame(settings,picker);capture("phone-theme-before-touch",picker)
        tap(picker,option(picker,1));assertFalse("A physical tap must choose and close the theme picker",picker.isShowing)
        assertNull(saved);assertEquals(UiTheme.LIGHT,AppSettings.fromJson(JSONObject(SettingsDialog.checkpoint(settings)!!).getString("draft")).theme)
        tap(settings,settings.getButton(AlertDialog.BUTTON_POSITIVE));assertEquals(UiTheme.LIGHT,saved!!.theme)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletChoiceRowsKeepClickAndCancelDraftBehaviorWithAnimationsEnabled(){
        var saved:AppSettings?=null
        val settings=track(SettingsDialog.show(activity,original,{saved=it},SettingsDialog.Draft(original,category=1).checkpoint(),null))
        tap(settings,setting(settings,"代码字号"));val picker=track(ShadowAlertDialog.getLatestAlertDialog())
        tap(picker,option(picker,8));assertFalse("Font size option must respond to real touch",picker.isShowing)
        assertEquals(18,AppSettings.fromJson(JSONObject(SettingsDialog.checkpoint(settings)!!).getString("draft")).fontSize);assertNull(saved)
        tap(settings,settings.getButton(AlertDialog.BUTTON_NEGATIVE));val discard=track(ShadowAlertDialog.getLatestAlertDialog())
        tap(discard,discard.getButton(AlertDialog.BUTTON_POSITIVE));assertFalse(settings.isShowing);assertNull(saved)
    }
    @Test fun sharedSecondaryMenuDispatchesTheSelectedSnapshotIndexOnTouch(){
        var selected=-1;val choices=listOf(WorkbenchChoice("first","第一项","",IdeIcon.SETTINGS),WorkbenchChoice("second","第二项","完整说明",IdeIcon.CODE))
        val dialog=track(DialogMotion.show(AlertDialog.Builder(activity).setAdapter(WorkbenchChoiceAdapter(activity,choices)){_,which->selected=which}.create(),true))
        val row=option(dialog,1);assertTrue(row.contentDescription.toString().contains("完整说明"))
        tap(dialog,row);assertEquals("AdapterView must own row selection",1,selected)
    }
    @Test fun completionCandidateRowsAlsoLeaveTouchSelectionToTheOwningList(){
        var selected=-1
        val adapter=object:BaseAdapter(){
            override fun getCount()=2
            override fun getItem(position:Int)=if(position==0)"print" else "property"
            override fun getItemId(position:Int)=position.toLong()
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View = CompletionCandidateRow(activity).apply{bind(CompletionItem(getItem(position),CompletionKind.FUNCTION,"函数说明"),false)}
        }
        // This test owns just the candidate adapter/list, with the same native dispatch as CompletionPopup.
        val dialog=AlertDialog.Builder(activity).setAdapter(adapter){_,which->selected=which}.create();dialog.show();track(dialog)
        val row=option(dialog,1);assertTrue(row.contentDescription.toString().contains("函数/方法"))
        tap(dialog,row);assertEquals("Candidate selection must receive the physical tap",1,selected)
    }
    @Test fun plainNativeListControlProvesThePhysicalTouchFixtureCanSelectARow(){
        var selected=-1;val dialog=AlertDialog.Builder(activity).setItems(arrayOf("第一项","第二项")){_,which->selected=which}.create();dialog.show();track(dialog)
        tap(dialog,option(dialog,1));assertEquals(1,selected)
    }
}
