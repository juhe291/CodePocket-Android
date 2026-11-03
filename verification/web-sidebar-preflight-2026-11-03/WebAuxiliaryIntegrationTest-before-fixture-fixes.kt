package com.codepocket.ide

import android.app.AlertDialog
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.Canvas
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Actual native Main/Sora/Markwon and WebView API lifecycle; no Chromium or Android PTY claim. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w1100dp-h780dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class WebAuxiliaryIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private var width=1100;private var height=780
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private val editor get()=field<WorkbenchEditor>("editor")
    private val panel get()=field<WebAuxiliaryPanel>("webPanel")
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun all()=views(main.findViewById(android.R.id.content))
    private fun tagged(tag:String)=all().single{it.tag==tag}
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(4){WorkspaceIo.executor.submit{}.get(15,TimeUnit.SECONDS);idle()}}
    private fun layout(w:Int=width,h:Int=height){width=w;height=h;val root=main.findViewById<View>(android.R.id.content)
        repeat(3){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);root.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    private fun create(saved:Bundle?=null){controller=Robolectric.buildActivity(MainActivity::class.java);main=controller.get()
        if(saved==null)controller.setup().visible() else controller.create(saved).start().restoreInstanceState(saved).resume().visible()
        drain();controller.windowFocusChanged(true);layout();awaitReady()}
    private fun launch(path:String="main.py",theme:UiTheme=UiTheme.DARK){val app=RuntimeEnvironment.getApplication();val ws=Workspace(app)
        if(path.endsWith(".md"))File(ws.root,path).writeText("# Saved guide\n\nOriginal body.\n")
        app.getSharedPreferences("codepocket_v2",0).edit().clear().putBoolean("workspaceOpen",true).putString("activeFile",path)
            .putString("settings",AppSettings(theme=theme,animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit();create()}
    private fun awaitReady(){repeat(250){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Sora layout not ready")}
    private fun awaitPopup(ready:()->Boolean){val deadline=System.nanoTime()+10_000_000_000L
        while(!ready()&&System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));Thread.sleep(5)};assertTrue("Native reference popup did not appear",ready())}
    private fun dispatch(command:IdeCommand){ReflectionHelpers.callInstanceMethod<Unit>(main,"dispatchCommand",ClassParameter.from(IdeCommand::class.java,command));idle();layout()}
    private fun menu(index:Int){assertTrue(tagged("web-menu").performClick());idle();val popup=ReflectionHelpers.getField<PopupMenu>(panel,"menu")
        val item=popup.menu.findItem(index);assertTrue(item.title.toString(),item.isEnabled);assertTrue(popup.menu.performIdentifierAction(index,0));idle();layout()}
    private fun navigate(value:String){(tagged("web-address") as EditText).setText(value);(tagged("web-address") as EditText).onEditorAction(EditorInfo.IME_ACTION_GO);idle();layout()}
    private fun web()=all().filterIsInstance<WebView>().single()
    private fun doc():TextView {ReflectionHelpers.getField<ExecutorService>(panel,"renderer").submit{}.get(20,TimeUnit.SECONDS);idle();layout();return tagged("web-document-body") as TextView}
    private fun capture(name:String){layout();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(64));val root=main.findViewById<View>(android.R.id.content)
        val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(image));val folder=File(CppFixtures.root,"verification/web-sidebar-integration-2026-11-03/previews").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()}
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){main.finish();controller.pause().stop().destroy()};drain();RuntimeEnvironment.setFontScale(1f)}
    @Test fun moveRehostsTheSameWebViewHistoryAndUnsubmittedAddressWithoutReload(){
        launch();dispatch(IdeCommand.WEB);navigate("https://example.org/reference")
        val web=web();val shadow=Shadows.shadowOf(web);shadow.pushEntryToHistory("https://example.org/previous");shadow.pushEntryToHistory("https://example.org/reference")
        val history=web.copyBackForwardList();val reloads=shadow.reloadInvocations
        val address=tagged("web-address") as EditText;address.requestFocus();address.setText("尚未提交的 C++ 查询")
        val registry=field<Any>("terminalRegistry");val path=field<String>("activePath");val text=editor.text.toString()
        menu(4);assertEquals(AuxiliarySide.LEFT,panel.side);assertSame(web,web());assertEquals("尚未提交的 C++ 查询",address.text.toString())
        assertEquals(history.size,web.copyBackForwardList().size);assertEquals(history.currentIndex,web.copyBackForwardList().currentIndex);assertEquals(reloads,shadow.reloadInvocations)
        assertFalse(field<View>("explorerPane").isShown);capture("tablet-dark-left-browser")
        menu(5);assertSame(web,web());assertEquals(AuxiliarySide.RIGHT,panel.side);assertEquals(text,editor.text.toString());assertEquals(path,field<String>("activePath"));assertSame(registry,field("terminalRegistry"))
    }
    @Test fun passiveShrinkHidesAuxiliaryFirstAndExpansionUsesTheSamePage(){
        launch();dispatch(IdeCommand.WEB);navigate("https://example.org/reference");val browser=web()
        assertEquals(AuxiliaryMode.SIDEBAR,panel.presented);layout(700,780)
        assertEquals(AuxiliaryMode.HIDDEN,panel.presented);assertTrue(editor.isShown);assertTrue(editor.width>=280);assertTrue(field<View>("activityBar").isShown)
        layout(1100,780);assertEquals(AuxiliaryMode.SIDEBAR,panel.presented);assertSame(browser,web());assertEquals("https://example.org/reference",Shadows.shadowOf(browser).lastLoadedUrl)
    }
    @Test fun leftBrowserSwitchesBackToTheExistingExplorerWithoutDestroyingThePage(){
        launch();dispatch(IdeCommand.WEB);val browser=web();menu(4)
        dispatch(IdeCommand.EXPLORER);assertFalse(panel.shown);assertTrue(field<View>("explorerPane").isShown)
        dispatch(IdeCommand.WEB);assertSame(browser,web());assertEquals(AuxiliarySide.LEFT,panel.side);assertFalse(field<View>("explorerPane").isShown)
    }
    @Test fun baiduChoiceAndUnsubmittedAddressSurviveActualActivityRecreation(){
        launch();dispatch(IdeCommand.WEB);menu(11);assertEquals(WebSearchEngine.BAIDU,panel.searchEngine)
        navigate("中文 vector 文档");val expected=Shadows.shadowOf(web()).lastLoadedUrl;assertTrue(expected.startsWith("https://www.baidu.com/s?wd="))
        (tagged("web-address") as EditText).setText("还没提交的查询")
        val saved=Bundle();controller.saveInstanceState(saved);controller.pause().stop().destroy();drain();create(saved)
        assertEquals(WebSearchEngine.BAIDU,panel.searchEngine);assertEquals("还没提交的查询",(tagged("web-address") as EditText).text.toString())
        assertEquals(expected,Shadows.shadowOf(web()).lastLoadedUrl)
    }
    @Test fun unsavedMarkdownUsesTheActualNativeRendererAndCopiesTheWholeSource(){
        launch("guide.md",UiTheme.LIGHT);val original=File(field<Workspace>("workspace").root,"guide.md").readText()
        val body="# 实际未保存标题\n\n**重要内容** 与 `std::vector` 😀\n\n- first\n- second\n";editor.setText(body);val base=field<String?>("loadedVersion")
        ReflectionHelpers.callInstanceMethod<Unit>(main,"previewMarkdownInAuxiliary");val result=doc()
        assertTrue(result.isShown);assertTrue(result.text.toString().contains("实际未保存标题"));assertFalse(result.text.toString().contains("**"))
        assertTrue(all().filterIsInstance<WebView>().isEmpty());assertEquals(body,editor.text.toString());assertTrue(field("dirty"));assertEquals(base,field<String?>("loadedVersion"))
        assertEquals(original,File(field<Workspace>("workspace").root,"guide.md").readText());capture("tablet-light-native-markdown")
        menu(9);val clipboard=main.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(body,clipboard.primaryClip!!.getItemAt(0).text.toString())
    }
    @Test fun referenceAndMarkdownStateRebuildWithoutRebindingThemToAnotherFile(){
        launch("guide.md");val body="# retained\n\nActual current Markdown body.\n";editor.setText(body)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"previewMarkdownInAuxiliary");assertTrue(doc().text.toString().contains("retained"));menu(4)
        val saved=Bundle();controller.saveInstanceState(saved);controller.pause().stop().destroy();drain();create(saved)
        assertEquals(AuxiliarySide.LEFT,panel.side);assertTrue(doc().text.toString().contains("Actual current Markdown body."))
        assertEquals(body,editor.text.toString());assertTrue(field("dirty"));assertEquals("guide.md",field<String>("activePath"))
    }
    @Test fun theSettingsDraftStaysOpenWhileAnAuxiliaryReferenceArrives(){
        launch();dispatch(IdeCommand.SETTINGS);val settings=field<AlertDialog>("toolDialog");val before=SettingsDialog.checkpoint(settings)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showAuxiliaryDocument",ClassParameter.from(AuxiliaryDocument::class.java,AuxiliaryDocument("函数说明","当前引擎结果","actual reference body")))
        assertTrue(settings.isShowing);assertSame(settings,field("toolDialog"));assertEquals(before,SettingsDialog.checkpoint(settings))
        assertEquals("actual reference body",doc().text.toString())
    }
    @Test @Config(qualifiers="w360dp-h640dp") fun phoneFocusReturnsToTheSameDirtySoraDocument(){
        width=360;height=640;RuntimeEnvironment.setFontScale(1.5f);launch();editor.setText("print('kept body')\n");val native=editor.inputView();val before=field<Long>("editRevision")
        dispatch(IdeCommand.WEB);assertEquals(AuxiliaryMode.FOCUS,panel.presented);navigate("C++ 中文文档");capture("phone-large-bing-focus")
        assertTrue(tagged("web-close").performClick());idle();layout();assertSame(native,editor.inputView());assertEquals(before,field<Long>("editRevision"));assertEquals("print('kept body')\n",editor.text.toString())
        assertTrue(field("dirty"));assertTrue(editor.isShown);assertTrue(field<View>("activityBar").isShown)
    }
    @Test fun actualCompletionPopupOpensItsSelectedMetadataWithoutInsertingIt(){
        launch();editor.requestFocus();editor.setText("pri");editor.setSelection(3);awaitReady();idle()
        ReflectionHelpers.callInstanceMethod<Unit>(main,"updateCompletions",ClassParameter.from(Boolean::class.javaPrimitiveType!!,true))
        awaitPopup{field<CompletionPopup?>("completionPopup")?.isShowing==true}
        val popup=field<CompletionPopup>("completionPopup");assertTrue(popup.isShowing);val native=ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup")
        val selected=field<CompletionBatch>("completionBatch").items[popup.selectedIndex];val revision=field<Long>("editRevision")
        assertTrue(views(native.contentView).single{it.tag=="completion-documentation"}.performClick());idle();layout()
        assertTrue(doc().text.toString().contains(selected.label));assertTrue(doc().text.toString().contains(selected.detail))
        assertEquals("pri",editor.text.toString());assertEquals(revision,field<Long>("editRevision"))
    }
    @Test fun actualParameterPopupPreservesAllFormsAndCanOpenItsRealSource(){
        launch();editor.requestFocus();editor.setText("print(");editor.setSelection(6);awaitReady();idle()
        ReflectionHelpers.callInstanceMethod<Unit>(main,"updateSignatureHelp",ClassParameter.from(Boolean::class.javaPrimitiveType!!,true))
        awaitPopup{field<SignaturePopup?>("signaturePopup")?.isShowing==true}
        val popup=field<SignaturePopup>("signaturePopup");assertTrue(popup.isShowing);val native=ReflectionHelpers.getField<android.widget.PopupWindow>(popup,"popup")
        assertTrue(views(native.contentView).single{it.tag=="signature-documentation"}.performClick());idle();layout()
        val reference=BuiltinSignatures.all.single{it.name=="print"&&!it.cpp};val text=doc().text.toString()
        reference.forms.forEach{assertTrue(text.contains(it.label(reference.name)))};assertTrue(text.contains(reference.description));assertTrue(text.contains(reference.source))
        menu(12);assertEquals(reference.source,Shadows.shadowOf(web()).lastLoadedUrl);assertEquals("print(",editor.text.toString())
    }
}
