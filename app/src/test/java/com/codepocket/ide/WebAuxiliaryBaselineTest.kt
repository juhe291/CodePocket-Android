package com.codepocket.ide

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.ui.IdeCommand
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.TimeUnit

/** Missing-feature baseline exercises actual native entry points, without pretending to run Chromium. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@LooperMode(LooperMode.Mode.PAUSED)
class WebAuxiliaryBaselineTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun all()=views(main.findViewById(android.R.id.content))
    private fun layout(){val root=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(3){root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));root.layout(0,0,root.measuredWidth,root.measuredHeight);root.viewTreeObserver.dispatchOnGlobalLayout();idle()}}
    @Before fun launch(){
        val app=RuntimeEnvironment.getApplication();Workspace(app)
        app.getSharedPreferences("codepocket_v2",0).edit().clear().putBoolean("workspaceOpen",true).putString("activeFile","main.py")
            .putString("settings",AppSettings(animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get()
        repeat(4){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()};controller.windowFocusChanged(true);layout()
    }
    private fun open(){
        val command=IdeCommand.values().singleOrNull{it.name=="WEB"};assertNotNull("Actual WEB command is missing",command)
        ReflectionHelpers.callInstanceMethod<Unit>(main,"dispatchCommand",ClassParameter.from(IdeCommand::class.java,command!!));idle();layout()
    }
    @After fun close(){main.finish();controller.pause().stop().destroy();idle()}
    @Test fun auxiliaryRemainsClosedAtFirstStartAndDoesNotCreateWebViewEarly(){
        assertEquals(View.GONE,field<View>("auxiliarySlot").visibility);assertTrue(all().filterIsInstance<WebView>().isEmpty())
    }
    @Test fun phoneRailHasTheActualWebEntryAndOpeningItKeepsTheRail(){
        val entry=all().singleOrNull{it.contentDescription?.toString()=="网页辅助栏"};assertNotNull("Actual web activity-bar entry is missing",entry)
        assertTrue(entry!!.performClick());idle();layout();assertTrue(field<View>("activityBar").isShown)
        assertTrue(all().filterIsInstance<WebView>().single().isShown)
    }
    @Test @Config(qualifiers="w1100dp-h780dp") fun tabletOpensARealWebViewInTheRightAuxiliarySlot(){
        open();val slot=field<ViewGroup>("auxiliarySlot");val editor=field<View>("editorRegion")
        assertTrue(slot.isShown);assertTrue(slot.width>=240);assertTrue(editor.width>=280)
        assertTrue(views(slot).filterIsInstance<WebView>().single().isShown);assertTrue(slot.left>=editor.right)
    }
    @Test fun addressSearchUsesBingForChineseAndCppKeywords(){
        open();val address=all().single{it.tag=="web-address"} as EditText
        address.setText("C++ vector 中文");address.onEditorAction(EditorInfo.IME_ACTION_GO);idle()
        val url=Shadows.shadowOf(all().filterIsInstance<WebView>().single()).lastLoadedUrl
        val target=android.net.Uri.parse(url);assertEquals("cn.bing.com",target.host);assertEquals("C++ vector 中文",target.getQueryParameter("q"))
    }
    @Test fun phoneOpenAndClosePreservesDirtyDocumentAndSavedBase(){
        val editor=field<WorkbenchEditor>("editor");editor.setText("unsaved browser coexistence\n")
        val base=field<String?>("loadedVersion");assertTrue(field("dirty"));open()
        assertTrue(all().single{it.tag=="web-close"}.performClick());idle();layout()
        assertEquals("unsaved browser coexistence\n",editor.text.toString());assertTrue(field("dirty"));assertEquals(base,field<String?>("loadedVersion"))
        assertTrue(editor.isShown);assertTrue(field<View>("activityBar").isShown)
    }
}
