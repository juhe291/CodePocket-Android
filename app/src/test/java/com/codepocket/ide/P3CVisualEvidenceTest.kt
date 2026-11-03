package com.codepocket.ide

import android.app.AlertDialog
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.codepocket.editorlab.SyntaxThemeDefinition
import com.codepocket.editorlab.SyntaxThemePreview
import com.codepocket.editorlab.WorkbenchEditor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Shared custom theme must reach the real Sora scheme/sample and native settings chrome, not only storage. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h891dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class P3CVisualEvidenceTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private lateinit var dialog:AlertDialog
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(15,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun layout(activity:Activity=main,width:Int=activity.resources.displayMetrics.widthPixels){
        repeat(3){val v=activity.findViewById<View>(android.R.id.content)
            v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(activity.resources.displayMetrics.heightPixels,View.MeasureSpec.EXACTLY))
            v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun capture(name:String,view:View){
        val b=Bitmap.createBitmap(view.width.coerceAtLeast(1),view.height.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        view.draw(Canvas(b))
        val dir=File(CppFixtures.root,"verification/p3-settings-themes-2026-11-03/previews").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    private fun launch(settings:AppSettings,qualifiers:String?=null){
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("codepocket_v2",0).edit().clear()
            .putString("activeFile","main.py").putString("settings",settings.toJson()).commit()
        Workspace(app)
        val builder=Robolectric.buildActivity(MainActivity::class.java)
        controller=builder.setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout()
        awaitHostEditor(main);layout()
    }
    @After fun close(){
        if(::dialog.isInitialized)dialog.dismiss()
        if(::main.isInitialized){main.finish();controller.pause().stop().destroy();drain()}
        ReflectionHelpers.setStaticField(RunSession::class.java,"result",null)
    }
    private fun waitForTextMate(){
        repeat(200){Shadows.shadowOf(Looper.getMainLooper()).idle()
            val editor=field<WorkbenchEditor>("editor")
            val styles=editor.inputView().javaClass.getMethod("getStyles").invoke(editor.inputView())
            if(editor.syntaxState().contains("自定义TextMate") && styles!=null)return
            Thread.sleep(5)}
        fail("Custom TextMate theme did not reach the real editor: ${field<WorkbenchEditor>("editor").syntaxState()}")
    }
    private fun colorAt(offset:Int):Int {
        val editor=field<WorkbenchEditor>("editor");val view=editor.inputView()
        val styles=view.javaClass.getMethod("getStyles").invoke(view)
        val spans=styles.javaClass.getField("spans").get(styles)
        val reader=Class.forName("io.github.rosemoe.sora.lang.styling.Spans").getMethod("read").invoke(spans)
        val code=editor.text.toString();val line=code.take(offset).count{it=='\n'};val column=offset-(code.lastIndexOf('\n',offset-1)+1)
        @Suppress("UNCHECKED_CAST") val list=Class.forName("io.github.rosemoe.sora.lang.styling.Spans\$Reader").getMethod("getSpansOnLine",Int::class.javaPrimitiveType).invoke(reader,line) as List<*>
        val spanType=Class.forName("io.github.rosemoe.sora.lang.styling.Span")
        val span=list.last{(spanType.getMethod("getColumn").invoke(it) as Int)<=column}!!
        val scheme=view.javaClass.getMethod("getColorScheme").invoke(view)
        val schemeType=Class.forName("io.github.rosemoe.sora.widget.schemes.EditorColorScheme")
        val resolver=spanType.getMethod("getSpanExt",Int::class.javaPrimitiveType).invoke(span,0)
        if(resolver!=null){
            val color=Class.forName("io.github.rosemoe.sora.lang.styling.span.SpanColorResolver").getMethod("getForegroundColor",spanType).invoke(resolver,span)
            if(color!=null)return Class.forName("io.github.rosemoe.sora.lang.styling.color.ResolvableColor").getMethod("resolve",schemeType).invoke(color,scheme) as Int
        }
        val style=spanType.getMethod("getStyle").invoke(span) as Long;val id=(style and 0x7ffff).toInt()
        return schemeType.getMethod("getColor",Int::class.javaPrimitiveType).invoke(scheme,id) as Int
    }
    private fun customJson()= """{"name":"Quiet custom","type":"dark","colors":{"editor.background":"#20242A","editor.foreground":"#D0D4DA","editorCursor.foreground":"#B4A0C0","editor.selectionBackground":"#334155","editor.lineHighlightBackground":"#262B33","editorLineNumber.foreground":"#7C8794"},"tokenColors":[{"scope":["keyword"],"settings":{"foreground":"#B4A0C0"}},{"scope":["string"],"settings":{"foreground":"#A3BE8C"}},{"scope":["storage.type"],"settings":{"foreground":"#88C0D0"}},{"scope":["entity.name.function"],"settings":{"foreground":"#EBCB8B"}},{"scope":["constant.numeric"],"settings":{"foreground":"#B48EAD"}},{"scope":["comment"],"settings":{"foreground":"#6A7A8A"}}]}"""
    @Test fun customThemeReachesTheRealSoraSchemeAndTheSharedPreview(){
        val settings=AppSettings(theme=UiTheme.DARK,syntax=SyntaxTheme.CUSTOM,customSyntaxTheme=customJson(),animations=false,autoSave=false)
        launch(settings)
        val editor=field<WorkbenchEditor>("editor")
        editor.setText("def greet():\n    return 'hello' + str(42) # note");idle();waitForTextMate()
        val scheme=editor.inputView().javaClass.getMethod("getColorScheme").invoke(editor.inputView())
        val schemeType=Class.forName("io.github.rosemoe.sora.widget.schemes.EditorColorScheme")
        fun schemeColor(id:String)=schemeType.getMethod("getColor",Int::class.javaPrimitiveType)
            .invoke(scheme,schemeType.getField(id).getInt(null)) as Int
        assertEquals(Color.parseColor("#20242A"),schemeColor("WHOLE_BACKGROUND"))
        assertEquals(Color.parseColor("#D0D4DA"),schemeColor("TEXT_NORMAL"))
        // The pinned Python grammar maps "def" to storage.type; both must come from the custom JSON.
        assertEquals("real def token uses the custom storage.type color",Color.parseColor("#88C0D0"),colorAt(0))
        val keyword=editor.text.toString().indexOf("return")
        assertEquals("real keyword token uses the custom color",Color.parseColor("#B4A0C0"),colorAt(keyword))
        val preview=SyntaxThemePreview.create(main,true,14,SyntaxThemeDefinition.parse(customJson()))
        assertEquals(Color.parseColor("#20242A"),(preview.background as android.graphics.drawable.ColorDrawable).color)
        val text=preview.text as Spanned;val start=preview.text.indexOf("#include")
        val span=text.getSpans(start,start+1,ForegroundColorSpan::class.java).last()
        assertEquals(Color.parseColor("#B4A0C0"),span.foregroundColor)
        layout();capture("phone-dark-custom-sora",editor)
    }
    private fun captureSettings(name:String){
        val decor=dialog.window!!.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.widthPixels,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(main.resources.displayMetrics.heightPixels,View.MeasureSpec.EXACTLY))
        decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);idle()
        // The custom theme editor is below the preview; scroll the real settings body, never fabricate it.
        views(decor).filterIsInstance<android.widget.ScrollView>().firstOrNull()?.scrollTo(0,10000);idle()
        capture(name,decor)
    }
    @Test fun phoneSettingsShowEditableCustomThemeAndPreview(){
        val settings=AppSettings(theme=UiTheme.DARK,syntax=SyntaxTheme.CUSTOM,customSyntaxTheme=customJson(),animations=false,autoSave=false)
        launch(settings);layout()
        dialog=SettingsDialog.show(main,settings,{},SettingsDialog.Draft(settings,category=0).checkpoint(),null);idle()
        val decor=dialog.window!!.decorView
        val input=views(decor).filterIsInstance<android.widget.EditText>().single{it.contentDescription=="自定义语法主题JSON"}
        assertTrue(input.text.toString().contains("Quiet custom"))
        assertTrue(views(decor).filterIsInstance<TextView>().any{it.text.toString().contains("主题预览 · 自定义「Quiet custom」")})
        captureSettings("phone-dark-custom-theme-settings")
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightSettingsShowTheSameCustomThemeSource(){
        val settings=AppSettings(theme=UiTheme.LIGHT,syntax=SyntaxTheme.CUSTOM,customSyntaxTheme=customJson(),animations=false,autoSave=false)
        launch(settings);layout()
        dialog=SettingsDialog.show(main,settings,{},SettingsDialog.Draft(settings,category=0).checkpoint(),null);idle()
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()=="自定义语法主题"})
        captureSettings("tablet-light-custom-theme-settings")
    }
    @Test fun breakpointGutterDrawsRealMarkers(){
        launch(AppSettings(theme=UiTheme.DARK,animations=false,autoSave=false))
        val editor=field<WorkbenchEditor>("editor")
        editor.setText("a = 1\nb = 2\nc = 3\nd = 4");idle()
        for(line in listOf(1,3))ReflectionHelpers.callInstanceMethod<Unit>(main,"toggleBreakpoint",ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,line))
        idle();layout()
        assertArrayEquals(intArrayOf(1,3),editor.getBreakpoints())
        capture("phone-dark-breakpoints",editor)
    }
}
