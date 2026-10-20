package com.codepocket.ide

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.ide.editor.*
import com.codepocket.ide.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class EditorFeedbackUiTest {
    private fun descendants(v: View): List<View> = listOf(v) + if(v is ViewGroup)(0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun settle(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        do { shadowOf(Looper.getMainLooper()).idleFor(180,TimeUnit.MILLISECONDS); if(condition())return; Thread.sleep(10) } while(System.nanoTime()<deadline)
        fail("worker did not finish")
    }
    private fun measure(v:View,w:Int,h:Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)
    }
    @Test fun findAsYouTypeShowsCountAndReplaceNeedsOnlyOneClick() {
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val a=c.get()
        var state=EditorSnapshot("workspace:main.py",1,"cat cat dog",0,0)
        var changes=0
        val dialog=SearchDialog.show(a,true,{state},{source,start,end->
            if(!source.sameBuffer(state))false else {state=state.copy(start=start,end=end);true}
        },{source,change,_->
            if(!source.sameBuffer(state))false else {
                state=state.copy(revision=state.revision+1,text=state.text.replaceRange(change.start,change.end,change.text),start=change.selectionStart,end=change.selectionEnd)
                changes++;true
            }
        },false)
        try {
            val all=descendants(dialog.window!!.decorView)
            val query=all.single{it.contentDescription=="查找文本"} as EditText
            val replacement=all.single{it.contentDescription=="替换文本"} as EditText
            val feedback=all.single{it.contentDescription=="匹配数量"} as TextView
            query.setText("cat")
            settle{feedback.text=="1 / 2"}
            assertEquals(0,state.start);assertEquals(3,state.end)
            // No prerequisite search: collapse the selection manually and replace in one click.
            state=state.copy(start=0,end=0);replacement.setText("fox")
            all.single{it.contentDescription=="替换当前或下一个匹配"}.performClick()
            settle{changes==1};assertEquals("fox cat dog",state.text)
            settle{feedback.text=="1 / 1"}
            all.single{it.contentDescription=="替换全部匹配，可撤销"}.performClick()
            settle{changes==2};assertEquals("fox fox dog",state.text)
            assertTrue(feedback.text.contains("可撤销"))
        }finally{dialog.dismiss();c.pause().stop().destroy()}
    }
    @Test fun queryTypingOptionsAndClosingNeverModifySource() {
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val a=c.get()
        val source=EditorSnapshot("workspace:x.cpp",2,"Cat cat catalog",0,0)
        var state=source;var changes=0
        val d=SearchDialog.show(a,false,{state},{_,s,e->state=state.copy(start=s,end=e);true},{_,_,_->changes++;true},false)
        try {
            val all=descendants(d.window!!.decorView)
            val query=all.single{it.contentDescription=="查找文本"} as EditText
            val feedback=all.single{it.contentDescription=="匹配数量"} as TextView
            query.setText("cat");settle{feedback.text=="1 / 3"}
            all.single{it.contentDescription=="区分大小写"}.performClick();settle{feedback.text=="1 / 2"}
            all.single{it.contentDescription=="全字匹配"}.performClick();settle{feedback.text=="1 / 1"}
            query.setText("[.*");settle{feedback.text=="无匹配结果"}
            all.single{it.contentDescription=="关闭查找"}.performClick()
            assertFalse(d.isShowing);assertEquals(0,changes);assertEquals(source.text,state.text)
        }finally{d.dismiss();c.pause().stop().destroy()}
    }
    @Test fun queuedSearchResultsCannotSelectOrEditAnotherDocument() {
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val a=c.get()
        var state=EditorSnapshot("workspace:one",1,"match",0,0);var selected=0;var changed=0
        val d=SearchDialog.show(a,true,{state},{_,_,_->selected++;true},{_,_,_->changed++;true},false)
        try {
            val all=descendants(d.window!!.decorView)
            (all.single{it.contentDescription=="查找文本"} as EditText).setText("match")
            state=state.copy(key="workspace:two",text="unrelated")
            settle{(all.single{it.contentDescription=="匹配数量"} as TextView).text=="无匹配结果"}
            assertEquals(0,selected);assertEquals(0,changed)
        }finally{d.dismiss();c.pause().stop().destroy()}
    }
    @Test fun diagnosticPopupKeepsEditorFocusAndCopiesTheActualMessage() {
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val a=c.get()
        val e=CodeEditor(a).apply{gravity=Gravity.TOP;setText("int main() {\n    return 0;\n}");setSelection(16);configureMotion(false,false,false,0)}
        a.setContentView(e);measure(e,411,700);e.requestFocus()
        val p=NativeEditorPort(e,{"workspace:main.cpp" to 2L},{true},{true},{true},{})
        val popup=DiagnosticPopup(a,e,p)
        try {
            val source=p.snapshot()!!
            val issue=InlineIssue(15,21,"compiler detail with source proof","C++ 实际编译")
            assertTrue(popup.show(issue));assertTrue(popup.isShowing)
            val window=org.robolectric.util.ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup")
            descendants(window.contentView).single{it.contentDescription=="复制错误信息"}.performClick()
            val clipboard=a.getSystemService(Activity.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertEquals(issue.message,clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertTrue(e.hasFocus());assertEquals(source.buffer,p.snapshot()!!.buffer)
            descendants(window.contentView).single{it.contentDescription=="关闭错误提示"}.performClick();assertFalse(popup.isShowing)
        }finally{popup.dismiss();p.close();c.pause().stop().destroy()}
    }
    private fun fixture(width:Int,height:Int,dark:Boolean,name:String?) {
        val c=Robolectric.buildActivity(Activity::class.java).setup().visible();val a=c.get()
        val palette=DesignTokens.palette(dark)
        a.getSharedPreferences("codepocket_v2",0).edit().putString("settings",AppSettings(theme=if(dark) UiTheme.DARK else UiTheme.LIGHT).toJson()).commit()
        val root=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(palette.editor)}
        val label=TextView(a).apply{text="原生主机预览 · 编辑反馈示例";textSize=12f;setTextColor(palette.muted);gravity=Gravity.CENTER_VERTICAL;setPadding(12,0,0,0)}
        root.addView(label,LinearLayout.LayoutParams(-1,48))
        val e=CodeEditor(a).apply {
            setTextColor(palette.foreground);setBackgroundColor(palette.editor);typeface=Typeface.MONOSPACE;textSize=15f
            gravity=Gravity.TOP;setPadding(16,12,16,12);setHorizontallyScrolling(true);configureMotion(false,false,false,palette.accent)
        }
        root.addView(e,LinearLayout.LayoutParams(-1,0,1f));a.setContentView(root)
        val code="#include <vector>\nint main() {\n    std::vector<std::pair<int,int>> data;\n    int count = 2;\n    if (count > 1] {\n        data.push_back({1, (count + 2)});\n    }\n}"
        e.setText(code);e.setSelection(code.indexOf("]"))
        val p=NativeEditorPort(e,{"workspace:main.cpp" to 1L},{true},{dark},{true},{})
        try {
            val before=e.text.toString();val caret=e.selectionStart
            assertTrue(p.applyDecorations(EditorAnalyzer().analyze(p.snapshot()!!)))
            assertEquals(before,e.text.toString());assertEquals(caret,e.selectionStart)
            assertTrue(e.diagnosticReport.issues.isNotEmpty())
            assertTrue(e.text.getSpans(0,e.length(),ForegroundColorSpan::class.java).map{it.foregroundColor}.toSet().size>=8)
            measure(root,width,height)
            val plain=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val decorated=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val report=e.diagnosticReport
            e.setDiagnostics(SyntaxReport(),dark);root.draw(Canvas(plain))
            e.setDiagnostics(report,dark);root.draw(Canvas(decorated))
            var changedPixels=0
            for(y in 0 until height)for(x in 0 until width)if(plain.getPixel(x,y)!=decorated.getPixel(x,y))changedPixels++
            assertTrue("actual wave pixels",changedPixels>30)
            if(name!=null){
                val folder=File("../verification/editor-feedback-2026-10-20").apply{mkdirs()}
                File(folder,"$name.png").outputStream().use{decorated.compress(Bitmap.CompressFormat.PNG,100,it)}
                val search=SearchDialog.show(a,true,{p.snapshot()!!.buffer},{_,_,_->true},{_,_,_->false},false)
                try{
                    val content=search.window!!.decorView;measure(content,minOf(width-16,600),228)
                    val image=Bitmap.createBitmap(content.width,content.height,Bitmap.Config.ARGB_8888)
                    content.draw(Canvas(image));File(folder,"$name-find.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
                }finally{search.dismiss()}
            }
            // Source changes clear old red marks synchronously, before another worker result.
            e.text.insert(0,"// edited\n");assertTrue(e.diagnosticReport.issues.isEmpty())
            val current=p.snapshot()!!;val analysis=EditorAnalyzer().analyze(current)
            BaseInputConnection.setComposingSpans(e.text)
            assertFalse(p.applyDecorations(analysis));assertEquals(before.prependIndent(""),e.text.toString().removePrefix("// edited\n"))
        }finally{p.close();c.pause().stop().destroy()}
    }
    @Test fun phoneDarkDecorationsAndSearchPreview(){fixture(411,700,true,"phone-dark")}
    @Test fun phoneLightDecorationsAndSearchPreview(){fixture(411,700,false,"phone-light")}
    @Test @Config(qualifiers="w1000dp-h800dp-mdpi") fun tabletDarkDecorationsAndSearchPreview(){fixture(1000,700,true,"tablet-dark")}
    @Test @Config(qualifiers="w1000dp-h800dp-mdpi") fun tabletLightDecorationsAndSearchPreview(){fixture(1000,700,false,"tablet-light")}
}
