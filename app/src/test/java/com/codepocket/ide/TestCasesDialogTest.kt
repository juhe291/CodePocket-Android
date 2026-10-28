package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Actual Views and DOWN/UP dispatch. These fixtures do not claim Android program execution. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class TestCasesDialogTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var activity: Activity
    private lateinit var storage: CaseStore
    private var rejectWrite = false
    private val dialogs = mutableListOf<AlertDialog>()
    private val key = "project:stable-file-id"
    private val launches = mutableListOf<List<ProgramCase>>()
    private fun idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220)) }
    private fun drain() { repeat(4) { WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS); idle() } }
    private fun views(view:View): List<View> = listOf(view) + if(view is ViewGroup)(0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    @Before fun launch() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        storage = CaseStore(temp.newFolder().canonicalFile) { a,b -> if(rejectWrite) throw IOException("disk full") else { Files.move(a.toPath(),b.toPath(),REPLACE_EXISTING); Unit } }
    }
    @After fun close() { dialogs.asReversed().filter { it.isShowing }.forEach { it.dismiss() }; activity.finish(); drain(); RuntimeEnvironment.setFontScale(1f) }
    private fun show(restored:String?=null): TestCasesDialog = TestCasesDialog(activity,key,"src/示例.py",storage,false,restored,
        { launches.add(it) }, {}).show().also { dialogs.add(it.dialog); drain(); layout(it.dialog) }
    private fun latest(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog().also { if(it !in dialogs)dialogs.add(it); layout(it) }
    private fun layout(dialog:AlertDialog) {
        idle(); val decor=dialog.window!!.decorView;val dm=decor.resources.displayMetrics
        val width=minOf(dialog.window!!.attributes.width.takeIf { it>0 } ?: dm.widthPixels,dm.widthPixels)
        val full=dialog.window!!.attributes.height==ViewGroup.LayoutParams.MATCH_PARENT
        repeat(2) { decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,if(full)View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);decor.viewTreeObserver.dispatchOnGlobalLayout();idle() }
    }
    private fun tap(dialog:AlertDialog,target:View) {
        layout(dialog);val rect=Rect();assertTrue("Visible touch target ${target.contentDescription}",target.getGlobalVisibleRect(rect));assertTrue(rect.width()>0&&rect.height()>0)
        val origin=IntArray(2);dialog.window!!.decorView.getLocationOnScreen(origin);val t=SystemClock.uptimeMillis()
        for((action,delay)in listOf(MotionEvent.ACTION_DOWN to 0L,MotionEvent.ACTION_UP to 30L)) {
            val event=MotionEvent.obtain(t,t+delay,action,(rect.centerX()-origin[0]).toFloat(),(rect.centerY()-origin[1]).toFloat(),0).apply{source=InputDevice.SOURCE_TOUCHSCREEN}
            try { dialog.dispatchTouchEvent(event) } finally { event.recycle() }
        };idle()
    }
    private fun row(dialog:AlertDialog,title:String)=views(dialog.window!!.decorView).first { it.getTag(R.id.workbench_key)=="choice:$title" }
    private fun field(dialog:AlertDialog,label:String)=views(dialog.window!!.decorView).filterIsInstance<EditText>().single { it.contentDescription.toString().startsWith(label) }
    private fun option(dialog:AlertDialog,index:Int):View { dialog.listView.setSelection(index);layout(dialog);return dialog.listView.getChildAt(index-dialog.listView.firstVisiblePosition) }
    private fun fill(dialog:AlertDialog) {
        field(dialog,"用例名称").setText("中文用例 😀")
        field(dialog,"标准输入").setText("6 7\n尾部空格 \t")
        field(dialog,"预期输出").setText("42\n")
    }
    private fun capture(name:String,dialog:AlertDialog) {
        layout(dialog);val decor=dialog.window!!.decorView;val bitmap=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888);decor.draw(Canvas(bitmap))
        val directory=File(CppFixtures.root,"verification/test-case-batch-2026-10-28/previews").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
    }
    @Test fun phoneTouchCreatesAnExactCaseAndSecondaryMenuRunsTheSelectedImmutableSnapshot() {
        val ui=show();val d=ui.dialog;tap(d,row(d,"新建用例"));fill(d)
        capture("phone-editor",d);tap(d,d.getButton(AlertDialog.BUTTON_POSITIVE));drain();layout(d)
        val saved=storage.load(key).cases.single();assertEquals("6 7\n尾部空格 \t",saved.input);assertEquals("42\n",saved.expected);assertEquals(OutputComparison.EXACT,saved.comparison)
        capture("phone-list",d);tap(d,row(d,saved.name));val choices=latest();assertNull(option(choices,0).tooltipText)
        tap(choices,option(choices,0));assertEquals(listOf(listOf(saved)),launches);assertFalse(choices.isShowing)
    }
    @Test fun editorCheckpointRestoresWhitespaceUnicodeRuleAndValidationDoesNotDismissIt() {
        val ui=show();tap(ui.dialog,row(ui.dialog,"新建用例"));fill(ui.dialog)
        val rule=views(ui.dialog.window!!.decorView).filterIsInstance<CheckBox>().single();rule.isChecked=true
        val checkpoint=ui.checkpoint()!!;ui.dialog.dismiss();val restored=show(checkpoint);val d=restored.dialog
        assertEquals("6 7\n尾部空格 \t",field(d,"标准输入").text.toString());assertTrue(views(d.window!!.decorView).filterIsInstance<CheckBox>().single().isChecked)
        field(d,"用例名称").setText(" ");tap(d,d.getButton(AlertDialog.BUTTON_POSITIVE));assertTrue(d.isShowing);assertTrue(storage.load(key).cases.isEmpty())
        field(d,"用例名称").setText("恢复后保存");tap(d,d.getButton(AlertDialog.BUTTON_POSITIVE));drain()
        assertEquals(OutputComparison.IGNORE_FINAL_NEWLINES,storage.load(key).cases.single().comparison)
    }
    @Test fun failedAtomicSaveKeepsTheInputsAndCanBeRetriedWithoutDuplicatingTheCase() {
        val ui=show();val d=ui.dialog;tap(d,row(d,"新建用例"));fill(d);rejectWrite=true
        tap(d,d.getButton(AlertDialog.BUTTON_POSITIVE));drain();assertTrue(d.isShowing);assertEquals("42\n",field(d,"预期输出").text.toString())
        assertTrue(storage.load(key).cases.isEmpty());assertTrue(views(d.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("disk full") })
        val checkpoint=ui.checkpoint()!!;d.dismiss();rejectWrite=false;val retry=show(checkpoint)
        tap(retry.dialog,retry.dialog.getButton(AlertDialog.BUTTON_POSITIVE));drain();assertEquals(1,storage.load(key).cases.size)
    }
    @Test fun historyKeepsDeletedCaseSnapshotsAndReaderCopiesExactActualTextWithTruncationNotice() {
        val case=ProgramCase(name="已删除的用例",input="",expected="")
        val record=CaseRecord.create(case,DocumentTree.digest("source"),RunResult("a".repeat(50001),"trace","1",9,kind=RunKind.RUNTIME),1234)
        assertTrue(record.outputTruncated);storage.record(key,record)
        val ui=show();tap(ui.dialog,row(ui.dialog,"运行记录"));tap(ui.dialog,row(ui.dialog,case.name));var d=latest();tap(d,option(d,0));d=latest()
        assertTrue(views(d.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("输出记录已截断") })
        val selectable=views(d.window!!.decorView).filterIsInstance<TextView>().filter { it.isTextSelectable };assertTrue(selectable.isNotEmpty())
        tap(d,d.getButton(AlertDialog.BUTTON_POSITIVE));val clipboard=activity.getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("a".repeat(50000),clipboard.primaryClip!!.getItemAt(0).text.toString());assertTrue(ui.dialog.isShowing)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletUsesBoundedWidthAndDeletingACasePreservesItsHistory() {
        val case=ProgramCase(name="相加",input="1 2",expected="3\n");storage.saveCases(key,listOf(case))
        storage.record(key,CaseRecord.create(case,DocumentTree.digest("source"),RunResult("3\n","","0",4)))
        val ui=show();val d=ui.dialog;assertTrue(d.window!!.decorView.width<=720*activity.resources.displayMetrics.density+1)
        capture("tablet-list",d);tap(d,row(d,case.name));val choices=latest();tap(choices,option(choices,2));val confirm=latest();tap(confirm,confirm.getButton(AlertDialog.BUTTON_POSITIVE));drain()
        assertTrue(storage.load(key).cases.isEmpty());assertEquals(1,storage.load(key).history.size)
    }
    @Test @Config(qualifiers="w600dp-h320dp") fun compactWindowKeepsButtonsAccessibleAndScrollsTheEditor() {
        val ui=show();val d=ui.dialog;tap(d,row(d,"新建用例"));fill(d);layout(d)
        val scroll=generateSequence(field(d,"标准输入").parent){it.parent}.filterIsInstance<ScrollView>().first();assertTrue(scroll.height>0)
        val rect=Rect();assertTrue(d.getButton(AlertDialog.BUTTON_POSITIVE).getGlobalVisibleRect(rect));assertTrue(rect.height()>0)
        assertTrue(scroll.getChildAt(0).height>scroll.height);capture("compact-editor",d)
    }
    @Test fun foreignFileCheckpointCannotAttachItsFormToTheCurrentFile() {
        val state=JSONObject().put("key","other:file").put("mode","edit").put("name","wrong file").toString()
        val ui=show(state);assertTrue(views(ui.dialog.window!!.decorView).filterIsInstance<EditText>().isEmpty())
        assertTrue(storage.load(key).cases.isEmpty())
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun lightTabletEditorUsesTheCurrentPaletteAndKeepsItsFormOnRefresh() {
        activity.getSharedPreferences("codepocket_v2",0).edit().putString("settings",AppSettings(theme=UiTheme.LIGHT,animations=false).toJson()).commit()
        activity.setTheme(R.style.Theme_CodePocket_Light)
        val ui=show();tap(ui.dialog,row(ui.dialog,"新建用例"));fill(ui.dialog);ui.refresh()
        assertEquals("6 7\n尾部空格 \t",field(ui.dialog,"标准输入").text.toString())
        assertEquals(com.codepocket.ide.ui.DesignTokens.palette(false).foreground,field(ui.dialog,"标准输入").currentTextColor)
        capture("tablet-light-editor",ui.dialog)
    }
}
