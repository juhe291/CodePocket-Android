package com.codepocket.ide

import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
import java.util.concurrent.TimeUnit

/** Actual workbench command and welcome gating; engine/Sora edits are verified separately. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h800dp",shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class FormatEntryIntegrationTest {
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private fun <T> field(name:String):T=ReflectionHelpers.getField(main,name)
    private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun drain(){repeat(6){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);idle()}}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    @Before fun settings(){RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear()
        .putString("settings",AppSettings(animations=false,autoSave=false,autoCompletion=false,signatureHelp=false).toJson()).commit()}
    private fun launch(existing:Boolean){
        if(existing){Workspace(RuntimeEnvironment.getApplication());RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().putString("activeFile","main.cpp").commit()}
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();drain();controller.windowFocusChanged(true)
        val root=main.findViewById<View>(android.R.id.content);val dm=main.resources.displayMetrics
        repeat(2){root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dm.heightPixels,View.MeasureSpec.EXACTLY));root.layout(0,0,root.measuredWidth,root.measuredHeight);root.viewTreeObserver.dispatchOnGlobalLayout();idle()}
    }
    @After fun close(){if(::main.isInitialized&&!main.isDestroyed){main.finish();controller.pause().stop().destroy()};drain()}
    private fun command():IdeCommand {
        val value=IdeCommand.entries.firstOrNull{it.name=="FORMAT_DOCUMENT"}
        assertNotNull("Formatting must be a real workbench command",value);return value!!
    }
    @Test fun loadedDocumentHasFormattingInTheActualEditMenu(){
        launch(true);val format=command();val state=ReflectionHelpers.callInstanceMethod<CommandContext>(main,"commandContext")
        assertTrue(IdeCommands.enabled(format,state));assertTrue(format.title.contains("Shift+Alt+F"))
        val toolbar=field<View>("mainToolbar")
        ReflectionHelpers.callInstanceMethod<Unit>(main,"showWorkbenchMenu",ClassParameter.from(View::class.java,toolbar),ClassParameter.from(WorkbenchMenuGroup::class.java,WorkbenchMenuGroup.EDIT));idle()
        val popup=Shadows.shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow
        assertNotNull(popup);assertTrue(views(popup!!.contentView).any{it.contentDescription==format.title})
        FormattingPreview.capture("phone-format-edit-menu",popup.contentView)
    }
    @Test fun welcomeDisablesFormattingAndKeepsItsSourceAndWorkspaceEmpty(){
        launch(false);val format=command();val state=ReflectionHelpers.callInstanceMethod<CommandContext>(main,"commandContext")
        assertFalse(IdeCommands.enabled(format,state));assertEquals("",field<WorkbenchEditor>("editor").text.toString())
        assertTrue(File(main.filesDir,"workspace").listFiles().orEmpty().isEmpty());assertFalse(field("workspaceOpen"))
    }
}
