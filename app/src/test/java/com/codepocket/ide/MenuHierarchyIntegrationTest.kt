package com.codepocket.ide

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.*
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual Main menus: contextual headers, return navigation, real dispatch and stale-callback isolation. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class MenuHierarchyIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private val editor get()=field<WorkbenchEditor>("editor")
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun ready(){repeat(200){idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Actual Sora layout unavailable")}
    private fun layout(){repeat(2){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun popup()=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow!!
    private fun row(w:PopupWindow,title:String)=views(w.contentView).single{it.contentDescription==title}
    private fun heading(w:PopupWindow,title:String){assertTrue("Second-level menu needs its category heading",views(w.contentView).filterIsInstance<TextView>().any{it.text.toString()==title})}
    private fun choose(title:String):PopupWindow{row(popup(),title).performClick();idle();return popup()}
    private fun rootMenu(){field<MainToolbar>("mainToolbar").menuAnchor.performClick();idle()}
    private fun capture(tag:String,v:View){
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(b);canvas.drawColor(DesignTokens.forContext(main).overlay);v.draw(canvas)
        val cwd=File(System.getProperty("user.dir")!!);val root=if(cwd.name=="app")cwd.parentFile else cwd
        val dir=File(root,"verification/workbench-menu-context-2026-10-26/previews").apply{mkdirs()};File(dir,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    private fun key(w:PopupWindow,code:Int){assertTrue(w.contentView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code)));assertTrue(w.contentView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code)));idle()}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))2f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,autoSave=false,animations=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()
    }
    @After fun close(){if(::main.isInitialized){Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow?.dismiss();ShadowAlertDialog.getLatestAlertDialog()?.takeIf{it.isShowing}?.dismiss();main.finish();controller.pause().stop().destroy();drain()};RuntimeEnvironment.setFontScale(1f)}
    @Test @Config(qualifiers="w411dp-h891dp") fun phoneReturnsToCategoriesWithoutEditingAndIgnoresAnOldBackButton(){
        editor.setSelection(editor.length());assertTrue(editor.onCreateInputConnection(EditorInfo()).commitText("# menu context\n",1));idle();ready();layout()
        val buffer=editor.text;val text=buffer.toString();val caret=editor.selectionStart;val revision=field<Long>("editRevision")
        rootMenu();val child=choose("编辑");heading(child,"编辑");val back=row(child,"返回工作台菜单")
        assertTrue(back.width>=48&&back.height>=48);capture("phone-edit-context",child.contentView)
        back.performClick();idle();val parent=popup();assertNotSame(child,parent);assertFalse(child.isShowing);assertTrue(parent.isShowing)
        assertNotNull(row(parent,"文件"));assertNotNull(row(parent,"编辑"));capture("phone-category-return",parent.contentView)
        back.performClick();idle();assertSame("Dismissed child callbacks must not reopen another parent",parent,popup())
        assertSame(buffer,editor.text);assertEquals(text,buffer.toString());assertEquals(caret,editor.selectionStart);assertEquals(revision,field<Long>("editRevision"));assertTrue(field("dirty"))
        key(parent,KeyEvent.KEYCODE_ESCAPE);assertFalse(parent.isShowing)
    }
    @Test @Config(qualifiers="w280dp-h640dp") fun phoneLightLargeKeepsTheReturnHeaderVisibleWhileCommandsScroll(){
        val buffer=editor.text;rootMenu();val child=choose("编辑");heading(child,"编辑");val back=row(child,"返回工作台菜单")
        val scroll=views(child.contentView).filterIsInstance<ScrollView>().single();assertTrue(scroll.getChildAt(0).height>scroll.height)
        scroll.scrollTo(0,scroll.getChildAt(0).height);idle();val visible=Rect();assertTrue(back.getGlobalVisibleRect(visible));assertEquals(back.height,visible.height());assertTrue(back.height>=48)
        assertNotNull(row(child,IdeCommand.SNIPPET.title));capture("phone-large-edit-scrolled",child.contentView)
        key(child,KeyEvent.KEYCODE_DPAD_LEFT);val parent=popup();assertNotSame(child,parent);assertTrue(parent.isShowing);assertNotNull(row(parent,"设置"));assertSame(buffer,editor.text)
        capture("phone-large-category-return",parent.contentView);key(parent,KeyEvent.KEYCODE_ESCAPE)
    }
    @Test @Config(qualifiers="w1280dp-h800dp") fun tabletLightDirectMenuHasContextAndKeepsRealCommandDispatch(){
        val buffer=editor.text;val title=field<MainToolbar>("mainToolbar").findViewWithTag<View>(WorkbenchMenuGroup.EDIT);assertNotNull(title);title.performClick();idle()
        val child=popup();heading(child,"编辑");assertFalse(views(child.contentView).any{it.contentDescription=="返回工作台菜单"});capture("tablet-direct-edit",child.contentView)
        row(child,IdeCommand.GOTO_LINE.title).performClick();idle();val form=ShadowAlertDialog.getLatestAlertDialog();assertTrue(form.isShowing)
        assertTrue(views(form.window!!.decorView).filterIsInstance<TextView>().any{it.text.toString()=="跳转行"});form.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertSame(buffer,editor.text)
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightLargeTerminalGroupsHaveDistinctIconsAndReturnToTheSameSession(){
        views(main.findViewById(android.R.id.content)).single{it.contentDescription=="打开终端"}.performClick();idle();layout()
        val registry=field<ShellSessionRegistry>("terminalRegistry");val selected=registry.catalog.selected();assertNotNull(selected);val ids=registry.catalog.entries().map{it.id};val buffer=editor.text
        field<PanelToolbar>("panelToolbar").menuAnchor.performClick();idle();val root=popup()
        val icons=listOf("终端操作" to IdeIcon.TERMINAL,"会话管理" to IdeIcon.EXPLORER,"布局与输入" to IdeIcon.MOVE,"配色与帮助" to IdeIcon.PALETTE)
        for((label,expected) in icons){val glyph=views(row(root,label)).filterIsInstance<ImageView>().single().drawable;assertEquals("Section icons follow stable category identity",expected,ReflectionHelpers.getField<IdeIcon>(glyph,"icon"))}
        capture("tablet-terminal-categories",root.contentView);val child=choose("会话管理");heading(child,"会话管理")
        val end=row(child,"结束会话");assertEquals(DesignTokens.forContext(main).error,views(end).filterIsInstance<TextView>().single().currentTextColor)
        capture("tablet-terminal-session-context",child.contentView);row(child,"返回终端菜单").performClick();idle();val parent=popup()
        assertTrue(parent.isShowing);assertNotNull(row(parent,"终端操作"));assertSame(selected,registry.catalog.selected());assertEquals(ids,registry.catalog.entries().map{it.id});assertSame(buffer,editor.text)
        capture("tablet-terminal-category-return",parent.contentView)
        val panel=field<PanelState>("panelState");choose("布局与输入");val sizes=choose("调整面板尺寸");heading(sizes,"面板尺寸")
        capture("tablet-terminal-size-context",sizes.contentView);row(sizes,"返回布局与输入").performClick();idle();heading(popup(),"布局与输入")
        capture("tablet-terminal-layout-return",popup().contentView);row(popup(),"返回终端菜单").performClick();idle()
        assertEquals(panel,field<PanelState>("panelState"));assertSame(selected,registry.catalog.selected());key(popup(),KeyEvent.KEYCODE_ESCAPE)
    }
}
