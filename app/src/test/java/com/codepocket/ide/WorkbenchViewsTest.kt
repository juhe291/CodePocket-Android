package com.codepocket.ide

import android.app.Activity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.codepocket.ide.animation.Animations
import com.codepocket.ide.ui.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File

/** Production reconciliation/components and actual MainActivity rendering methods; no native PTY needed. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class WorkbenchViewsTest {
    private lateinit var activity: Activity
    private lateinit var rows: LinearLayout
    @Before fun prepare() {
        activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        rows=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL}
        activity.setContentView(rows)
    }
    private fun update(keys: List<String>)=KeyedViews.update(rows,keys,{key->TextView(activity).apply {text=key;isFocusableInTouchMode=true}}) {_,_,_->}
    private fun key(parent:LinearLayout,value:String): View = (0 until parent.childCount).map {parent.getChildAt(it)}.single {it.getTag(R.id.workbench_key)==value}

    @Test fun expandingAndCollapsingKeepTheExistingRowAndItsFocus() {
        update(listOf("src","main"));val folder=key(rows,"src");val main=key(rows,"main")
        folder.requestFocus();update(listOf("src","src/a.py","main"))
        assertSame(folder,key(rows,"src"));assertSame(main,key(rows,"main"));assertTrue(folder.hasFocus())
        update(listOf("src","main"));assertSame(folder,key(rows,"src"));assertTrue(folder.hasFocus())
    }
    @Test fun removingAnAnimatedRowCancelsItsCallbacks() {
        update(listOf("a","b"));val removed=key(rows,"a");Animations.fadeIn(removed)
        update(listOf("b"));assertNull(removed.parent);assertNull(removed.getTag(R.id.workbench_motion))
    }
    @Test fun addingRowsDoesNotTakeFocusFromAnEditorOutsideTheList() {
        val host=LinearLayout(activity);val editor=EditText(activity);host.addView(editor)
        (rows.parent as android.view.ViewGroup).removeView(rows);host.addView(rows);activity.setContentView(host)
        editor.requestFocus();update(listOf("a"));update(listOf("a","b"));assertTrue(editor.hasFocus())
    }
    @Test fun duplicateKeysAreRejectedBeforeChangingTheVisibleList() {
        update(listOf("a"));val original=rows.getChildAt(0)
        assertThrows(IllegalArgumentException::class.java) {update(listOf("a","a"))}
        assertEquals(1,rows.childCount);assertSame(original,rows.getChildAt(0))
    }
    @Test fun reorderedSurvivorsRetainIdentityAndKeyboardFocus() {
        update(listOf("a","b","c"));val c=key(rows,"c");c.requestFocus()
        update(listOf("c","a","b"));assertSame(c,rows.getChildAt(0));assertTrue(c.hasFocus())
    }
    @Test fun deepTreeKeepsRoomForLongNamesAndFullAccessiblePath() {
        val row=ExplorerRow(activity,DesignTokens.palette(true))
        val path="src/".repeat(20)+"very-long-file-name.py"
        row.bind(path,20,false,false,true,false)
        row.measure(View.MeasureSpec.makeMeasureSpec(192,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        row.layout(0,0,row.measuredWidth,row.measuredHeight)
        val name=row.getChildAt(1) as TextView
        assertTrue(name.measuredWidth>=80);assertTrue(row.measuredHeight>=48)
        assertEquals(path,row.contentDescription.toString());assertEquals(1,name.maxLines)
    }
    @Test fun largeFontTreeRowsGrowAndKeepATouchTargetInBothThemes() {
        for (dark in listOf(true,false)) {
            val config=android.content.res.Configuration(activity.resources.configuration).apply {fontScale=2f}
            val context=activity.createConfigurationContext(config)
            val row=ExplorerRow(context,DesignTokens.palette(dark))
            row.bind("nested/very-long-file-name.py",20,false,false,false,false)
            val width=IdeControls.dp(context,192)
            row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            assertTrue(row.measuredHeight>=IdeControls.dp(context,48))
            assertTrue(row.getChildAt(1).measuredWidth>=IdeControls.dp(context,80))
        }
    }
    @Test fun chevronCanBeSettledWithoutChangingRowCoordinates() {
        val row=ExplorerRow(activity,DesignTokens.palette(false));rows.addView(row)
        row.bind("src",0,true,false,false,false)
        val arrow=row.getChildAt(0) as ImageView
        row.bind("src",0,true,true,false,true);Animations.cancelTree(row)
        assertEquals(90f,arrow.rotation,0f);assertEquals(0f,row.translationX,0f);assertEquals(0f,row.translationY,0f)
        row.bind("src",0,true,false,false,false);assertEquals(0f,arrow.rotation,0f)
    }
    @Test fun tabHasSeparateOpenAndCloseTouchActionsAndSingleLineName() {
        var opened=0;var closed=0
        val tab=EditorTab(activity,DesignTokens.palette(true),"src/long-file.py",{opened++},{closed++},{})
        tab.bind(true,true);rows.addView(tab)
        val name=tab.getChildAt(0) as TextView
        name.performClick();tab.getChildAt(1).performClick()
        assertEquals(1,opened);assertEquals(1,closed);assertEquals("long-file.py",name.text.toString());assertEquals("src/long-file.py · 未保存",name.contentDescription.toString());assertEquals(1,name.maxLines)
    }
    private fun mainFixture(): Pair<MainActivity,Workspace> {
        val main=Robolectric.buildActivity(MainActivity::class.java).get();main.setTheme(R.style.Theme_CodePocket)
        val directory=File(main.filesDir,"motion-"+java.util.UUID.randomUUID())
        val ws=Workspace(directory) {source,target->java.nio.file.Files.move(source.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);Unit}
        ReflectionHelpers.setField(main,"workspace",ws)
        ReflectionHelpers.setField(main,"workspaceOpen",true)
        ReflectionHelpers.setField(main,"activePath","main.cpp")
        ReflectionHelpers.setField(main,"sessions",EditorSessions(ws.identity,"[]"))
        ReflectionHelpers.setField(main,"settings",AppSettings(animations=false))
        ReflectionHelpers.setField(main,"status",TextView(main))
        return main to ws
    }
    @Test fun actualTabRefreshKeepsFocusedNameAcrossDirtyAndActiveChanges() {
        val (main,_)=mainFixture();val tabs=LinearLayout(main)
        main.setContentView(tabs);ReflectionHelpers.setField(main,"tabsRow",tabs)
        val opened=ReflectionHelpers.getField<MutableList<String>>(main,"opened");opened.addAll(listOf("main.cpp","main.py"))
        ReflectionHelpers.callInstanceMethod<Any?>(main,"refreshTabs")
        val first=tabs.getChildAt(0);val name=(first as LinearLayout).getChildAt(0) as TextView
        name.isFocusableInTouchMode=true;name.requestFocus()
        ReflectionHelpers.setField(main,"dirty",true)
        ReflectionHelpers.callInstanceMethod<Any?>(main,"refreshTabs")
        assertSame(first,tabs.getChildAt(0));assertTrue(name.hasFocus());assertEquals("main.cpp",name.text.toString());assertEquals("main.cpp · 未保存",name.contentDescription.toString())
        ReflectionHelpers.setField(main,"activePath","main.py")
        ReflectionHelpers.callInstanceMethod<Any?>(main,"refreshTabs")
        assertSame(first,tabs.getChildAt(0));assertTrue(name.hasFocus());assertEquals("main.cpp",name.text.toString())
    }
    @Test fun actualTreeToggleKeepsFolderRowAndDoesNotLoseExpandedState() {
        val (main,ws)=mainFixture();val folder=ws.create(ws.root,"src",true);ws.create(folder,"nested.py",false)
        val list=LinearLayout(main).apply {orientation=LinearLayout.VERTICAL}
        main.setContentView(list);ReflectionHelpers.setField(main,"sidebarExplorerList",list)
        ReflectionHelpers.callInstanceMethod<Any?>(main,"renderExplorer", ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!,false))
        val src=key(list,"entry:src");src.isFocusableInTouchMode=true;src.requestFocus()
        src.performClick();assertSame(src,key(list,"entry:src"));assertTrue(src.hasFocus())
        assertNotNull(key(list,"entry:src/nested.py"))
        src.performClick();assertSame(src,key(list,"entry:src"));assertTrue(src.hasFocus())
        assertFalse((0 until list.childCount).map {list.getChildAt(it).getTag(R.id.workbench_key)}.contains("entry:src/nested.py"))
        src.performClick();assertNotNull(key(list,"entry:src/nested.py"))
    }
    @Test fun actualWorkbenchCancellationSettlesNestedRowsAndKeepsEditorFocus() {
        val (main,_)=mainFixture()
        val host=LinearLayout(main);val list=LinearLayout(main);val editor=EditText(main)
        host.addView(editor);host.addView(list);main.setContentView(host);editor.requestFocus()
        ReflectionHelpers.setField(main,"sidebarExplorerList",list)
        val row=ExplorerRow(main,DesignTokens.palette(true));list.addView(row)
        row.bind("src",0,true,false,false,false);row.bind("src",0,true,true,false,true)
        Animations.fadeIn(row)
        ReflectionHelpers.callInstanceMethod<Any?>(main,"cancelWorkbenchMotion")
        assertEquals(1f,row.alpha,0f);assertEquals(90f,row.getChildAt(0).rotation,0f);assertTrue(editor.hasFocus())
    }
    @Test fun listRefreshPreservesTheScrollContainerAndItsPosition() {
        val scroll=ScrollView(activity)
        (rows.parent as android.view.ViewGroup).removeView(rows);scroll.addView(rows);activity.setContentView(scroll)
        update((0..60).map {"file$it"})
        for(i in 0 until rows.childCount) rows.getChildAt(i).minimumHeight=48
        scroll.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY))
        scroll.layout(0,0,240,320);scroll.scrollTo(0,240)
        update((0..61).map {"file$it"})
        assertSame(rows,scroll.getChildAt(0));assertEquals(240,scroll.scrollY)
    }

    @Test fun projectRootReallyCollapsesAndReopensWithoutChangingActiveDocument() {
        val (main,ws)=mainFixture();val folder=ws.create(ws.root,"src",true);ws.create(folder,"a.py",false)
        val list=LinearLayout(main).apply{orientation=LinearLayout.VERTICAL};main.setContentView(list)
        ReflectionHelpers.setField(main,"sidebarExplorerList",list)
        fun render(){ReflectionHelpers.callInstanceMethod<Any?>(main,"renderExplorer",ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!,false))}
        render();val root=key(list,"root");val active=ReflectionHelpers.getField<String>(main,"activePath")
        root.performClick();assertEquals(1,list.childCount);assertEquals(active,ReflectionHelpers.getField<String>(main,"activePath"))
        assertFalse(ReflectionHelpers.getField<MutableSet<String>>(main,"expanded").contains(""))
        root.performClick();assertNotNull(key(list,"entry:src"));assertSame(root,key(list,"root"))
    }
    @Test fun rootCollapseSurvivesCheckpointAndKeepsDescendantExpansion() {
        val (main,ws)=mainFixture();ws.create(ws.root,"src",true)
        ReflectionHelpers.setField(main,"expanded",mutableSetOf("src"))
        val saved=android.os.Bundle()
        ReflectionHelpers.callInstanceMethod<Any?>(main,"saveExplorerViewport",ReflectionHelpers.ClassParameter.from(android.os.Bundle::class.java,saved))
        ReflectionHelpers.getField<MutableSet<String>>(main,"expanded").add("")
        ReflectionHelpers.callInstanceMethod<Any?>(main,"restoreExplorerViewport",ReflectionHelpers.ClassParameter.from(android.os.Bundle::class.java,saved))
        assertEquals(setOf("src"),ReflectionHelpers.getField<MutableSet<String>>(main,"expanded"))
    }
    @Test fun selectingFolderUpdatesCreationTargetAndDoesNotOpenItAsCode() {
        val (main,ws)=mainFixture();ws.create(ws.root,"src",true)
        val list=LinearLayout(main);main.setContentView(list);ReflectionHelpers.setField(main,"sidebarExplorerList",list)
        ReflectionHelpers.callInstanceMethod<Any?>(main,"renderExplorer",ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!,false))
        key(list,"entry:src").performClick()
        assertEquals("src",ReflectionHelpers.getField<String>(main,"currentDirectory"))
        assertEquals("src",ReflectionHelpers.getField<String>(main,"explorerSelection"));assertTrue(key(list,"entry:src").isSelected)
    }

    @Test fun legacyNavigationUsesSelectedFileParentAsCreationTarget() {
        val (main,ws)=mainFixture();val src=ws.create(ws.root,"src",true);ws.create(src,"a.py",false)
        ReflectionHelpers.setField(main,"activePath","src/a.py");ReflectionHelpers.setField(main,"currentDirectory","")
        ReflectionHelpers.callInstanceMethod<Any?>(main,"reconcileExplorerSelection")
        assertEquals("src/a.py",ReflectionHelpers.getField<String>(main,"explorerSelection"))
        assertEquals("src",ReflectionHelpers.getField<String>(main,"currentDirectory"))
    }
    @Test fun revealAndCreationExpandAllNecessaryAncestors() {
        val (main,_)=mainFixture();ReflectionHelpers.setField(main,"expanded",mutableSetOf<String>())
        ReflectionHelpers.callInstanceMethod<Any?>(main,"expandExplorerParents",ReflectionHelpers.ClassParameter.from(String::class.java,"src/deep/include/a.hpp"))
        assertEquals(setOf("","src","src/deep","src/deep/include"),ReflectionHelpers.getField<MutableSet<String>>(main,"expanded"))
    }
}
