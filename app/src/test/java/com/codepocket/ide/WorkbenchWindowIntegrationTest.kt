package com.codepocket.ide

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.codepocket.editorlab.WorkbenchEditor
import com.codepocket.ide.terminal.shell.ShellSessionRegistry
import com.codepocket.ide.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual normal main entry and measured Android window, not direct calls to a responsive policy. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],shadows=[HostAtomicOs::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class WorkbenchWindowIntegrationTest {
    @get:Rule val name=TestName()
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var main:MainActivity
    private val editor get()=field<WorkbenchEditor>("editor")
    private val workspace get()=field<Workspace>("workspace")
    private fun <T> field(key:String):T=ReflectionHelpers.getField(main,key)
    private fun invoke(key:String){ReflectionHelpers.callInstanceMethod<Unit>(main,key)}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun settle(){repeat(5){WorkspaceIo.executor.submit{}.get(10,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle()}}
    private fun ready(){repeat(200){Shadows.shadowOf(Looper.getMainLooper()).idle();if(editor.isLayoutReady)return;Thread.sleep(5)};fail("Main Sora layout unavailable")}
    private fun layout(){val v=main.findViewById<View>(android.R.id.content);val d=main.resources.displayMetrics
        v.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(d.heightPixels,View.MeasureSpec.EXACTLY));v.layout(0,0,v.measuredWidth,v.measuredHeight);v.viewTreeObserver.dispatchOnGlobalLayout()}
    @Before fun launch(){
        RuntimeEnvironment.setFontScale(if(name.methodName.contains("Large"))1.6f else 1f)
        RuntimeEnvironment.getApplication().getSharedPreferences("codepocket_v2",0).edit().clear().putString("activeFile","main.py")
            .putString("settings",AppSettings(theme=if(name.methodName.contains("Light"))UiTheme.LIGHT else UiTheme.DARK,animations=false,autoSave=false).toJson()).commit()
        controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();main=controller.get();settle();controller.windowFocusChanged(true);layout();editor.requestFocus();ready()
    }
    @After fun close(){main.finish();controller.pause().stop().destroy();settle();RuntimeEnvironment.setFontScale(1f)}
    private fun check(panel:Boolean,sidebar:Boolean){
        val root=field<View>("root");val density=main.resources.displayMetrics.density
        assertEquals(main.resources.configuration.screenWidthDp,(root.width/density).toInt())
        val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd;val folder=File(repo,"verification/main-app-window-ui-2026-10-23/window-metrics").apply{mkdirs()}
        File(folder,name.methodName+".json").writeText(org.json.JSONObject().put("windowWidthDp",(root.width/density).toDouble()).put("windowHeightDp",(root.height/density).toDouble())
            .put("workAreaWidthDp",field<WorkspaceGeometry>("currentGeometry").contentWidth).put("workAreaHeightDp",field<WorkspaceGeometry>("currentGeometry").contentHeight)
            .put("editorWidthPx",editor.width).put("editorHeightPx",editor.height).put("panelVisible",field<Boolean>("panelVisible")).put("panelDock",field<PanelLayout>("panelLayout").dock.name).toString())
        assertEquals(panel,field<Boolean>("panelVisible"));assertEquals(sidebar,field<WorkspaceGeometry>("currentGeometry").showExplorerSidebar)
        assertEquals(sidebar,field<View?>("activityBar")?.visibility==View.VISIBLE);assertFalse(field<Boolean>("startupPanelPending"))
        assertTrue(editor.width>0);assertTrue(editor.height>0);if(panel)assertEquals(.35f,field<PanelState>("panelState").bottomFraction,0f)
    }
    @Test @Config(qualifiers="w599dp-h800dp") fun width599LaunchesThePhoneWorkbench(){check(false,false)}
    @Test @Config(qualifiers="w600dp-h480dp") fun net600By480StartsThePanelWithoutFixedColumns(){check(true,false)}
    @Test @Config(qualifiers="w600dp-h479dp") fun height479DoesNotTriggerTabletStartup(){check(false,false)}
    @Test @Config(qualifiers="w839dp-h800dp") fun width839UsesTheDrawerAndStartupPanel(){check(true,false)}
    @Test @Config(qualifiers="w840dp-h800dp") fun width840UsesFixedColumnsAndStartupPanel(){check(true,true)}
    @Test @Config(qualifiers="w840dp-h480dp") fun shortWideWindowKeepsUsableEditorAndTerminal(){check(true,true)}
    private fun capture(tag:String,v:View=main.findViewById(android.R.id.content)){
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val cwd=File(System.getProperty("user.dir")!!);val repo=if(cwd.name=="app")cwd.parentFile else cwd
        val folder=File(repo,"verification/main-app-window-ui-2026-10-23/previews").apply{mkdirs()};File(folder,"$tag.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    @Test @Config(qualifiers="w360dp-h800dp") fun phoneLargeFontLongFilenameAndLastFolderStillCollapseAndReopen(){
        val folder=workspace.create(workspace.root,"学习项目",true);val file=workspace.create(folder,"long_name_".repeat(8)+".py",false);workspace.write(file,"print('real file')\n")
        ReflectionHelpers.callInstanceMethod<Boolean>(main,"switchFile",ClassParameter.from(String::class.java,workspace.path(file)),ClassParameter.from(kotlin.jvm.functions.Function0::class.java,null));settle();ready();val path=workspace.path(file)
        field<MutableSet<String>>("expanded").addAll(listOf("",workspace.path(folder)));invoke("showExplorer");Shadows.shadowOf(Looper.getMainLooper()).idle();val dialog=field<AlertDialog>("explorerDialog")
        fun rows()=field<LinearLayout>("overlayExplorerList")
        fun row(key:String)=views(rows()).firstOrNull{it.getTag(R.id.workbench_key)==key}
        val fileRow=row("entry:$path")!!;assertEquals(path,fileRow.contentDescription.toString());assertTrue(fileRow.height>=48)
        row("entry:${workspace.path(folder)}")!!.performClick();assertNull(row("entry:$path"));assertEquals(path,field<String>("activePath"))
        row("entry:${workspace.path(folder)}")!!.performClick();assertNotNull(row("entry:$path"));row("root")!!.performClick();assertNull(row("entry:$path"));row("root")!!.performClick();assertNotNull(row("entry:$path"))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80));capture("phone-large-font-file-drawer",dialog.window!!.decorView);dialog.dismiss();capture("phone-large-font-workbench")
        assertNotNull(views(main.findViewById(android.R.id.content)).firstOrNull{it.contentDescription=="打开终端"})
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun tabletLightLargeFontSettingsAndTerminalStatusAreReadable(){
        check(true,true);assertTrue(main.resources.configuration.fontScale>=1.5f)
        val pane=field<View>("shellPane");val runtime=field<ShellSessionRegistry>("terminalRegistry").catalog.selected()!!.value.runtime
        runtime.fail(java.io.IOException("host presentation check"));assertFalse(views(pane).filterIsInstance<TextView>().any{it.text.contains("host presentation check")})
        capture("tablet-light-large-font-status");invoke("showSettings");val dialog=field<AlertDialog>("toolDialog")
        val search=views(dialog.window!!.decorView).filterIsInstance<EditText>().single{it.contentDescription=="搜索设置"};search.setText("主题")
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80));assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).height>=48);assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).height>=48)
        capture("tablet-light-large-font-settings",dialog.window!!.decorView)
        val option=views(dialog.window!!.decorView).filterIsInstance<TextView>().single{it.text.toString()=="界面主题"}
        assertTrue("Settings search result must actually have layout height",option.height>0)
        val decor=dialog.window!!.decorView;val position=IntArray(2);dialog.getButton(AlertDialog.BUTTON_POSITIVE).getLocationOnScreen(position);val origin=IntArray(2);decor.getLocationOnScreen(origin)
        assertTrue("Settings footer must remain at the window bottom",position[1]+dialog.getButton(AlertDialog.BUTTON_POSITIVE).height>=origin[1]+decor.height-32)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertFalse(dialog.isShowing);assertEquals("main.py",field<String>("loadedPath"))
    }
    @Test @Config(sdk=[28],qualifiers="w411dp-h800dp") fun phoneApi28LargeSettingsRetainsBodyAndAnchoredButtons(){
        invoke("showSettings");val dialog=field<AlertDialog>("toolDialog");val decor=dialog.window!!.decorView
        val search=views(decor).filterIsInstance<EditText>().single{it.contentDescription=="搜索设置"};search.setText("光标")
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        val rows=views(decor).filterIsInstance<Switch>();assertTrue(rows.size>=2);assertTrue(rows.all{it.height>=48});assertNull(search.backgroundTintList)
        val point=IntArray(2);dialog.getButton(AlertDialog.BUTTON_POSITIVE).getLocationOnScreen(point);val top=IntArray(2);decor.getLocationOnScreen(top)
        assertTrue(point[1]+dialog.getButton(AlertDialog.BUTTON_POSITIVE).height>=top[1]+decor.height-32)
        capture("phone-api28-large-settings",decor);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(dialog.isShowing)
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun configurationRecreationKeepsTheChosenPanelFileAndCaret(){
        editor.setText("value = 1\nprint(value)\n");ready();editor.setSelection(3);invoke("togglePanel");assertFalse(field("panelVisible"))
        val key=field<EditorSessions>("sessions").file("main.py").id;val ids=field<ShellSessionRegistry>("terminalRegistry").catalog.entries().map{it.id}
        val changed=Configuration(main.resources.configuration).apply{screenWidthDp=700;screenHeightDp=1000;smallestScreenWidthDp=700;orientation=Configuration.ORIENTATION_PORTRAIT}
        controller.configurationChange(changed);main=controller.get();controller.visible();settle();controller.windowFocusChanged(true);layout();ready()
        assertEquals("value = 1\nprint(value)\n",editor.text.toString());assertEquals(3,editor.selectionStart);assertEquals(key,field<EditorSessions>("sessions").file("main.py").id)
        assertFalse(field("panelVisible"));assertFalse(field("startupPanelPending"));assertEquals(ids,field<ShellSessionRegistry>("terminalRegistry").catalog.entries().map{it.id})
    }
    @Test @Config(qualifiers="w840dp-h800dp") fun terminalShortcutOpensAndFocusesWithoutTogglingClosingOrRequestingIme(){
        invoke("togglePanel");assertFalse(field("panelVisible"));val registry=field<ShellSessionRegistry>("terminalRegistry");val ids=registry.catalog.entries().map{it.id}
        val ime=main.getSystemService(InputMethodManager::class.java);val before=Shadows.shadowOf(ime).isSoftInputVisible
        val button=views(main.findViewById(android.R.id.content)).single{it.contentDescription=="打开终端"};button.performClick();assertTrue(field("panelVisible"));button.performClick();assertTrue(field("panelVisible"))
        assertEquals(ids,registry.catalog.entries().map{it.id});assertEquals(before,Shadows.shadowOf(ime).isSoftInputVisible)
    }
}
