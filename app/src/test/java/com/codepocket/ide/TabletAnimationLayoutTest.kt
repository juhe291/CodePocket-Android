package com.codepocket.ide

import com.codepocket.ide.ui.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Window dimensions, not orientation flags, select a fixed tablet workbench. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class TabletAnimationLayoutTest {
    private fun startup(main:MainActivity,w:Int,h:Int)=ReflectionHelpers.callInstanceMethod<Any?>(main,"resolvePanelStartup",ClassParameter.from(Int::class.javaPrimitiveType!!,w),ClassParameter.from(Int::class.javaPrimitiveType!!,h))
    @Test fun launchBoundariesUseNet600By480WindowAndTabletStartsAt35Percent(){
        for((w,h,visible) in listOf(Triple(599,800,false),Triple(600,479,false),Triple(600,480,true),Triple(840,480,true),Triple(1280,800,true))){
            val main=Robolectric.buildActivity(MainActivity::class.java).get();startup(main,w,h)
            assertEquals("$w x $h",visible,ReflectionHelpers.getField<Boolean>(main,"panelVisible"))
            assertEquals(.35f,ReflectionHelpers.getField<PanelState>(main,"panelState").bottomFraction,0f)
        }
    }
    @Test fun zeroSizeAndImeDoNotConsumeTheStartupDecision(){
        val main=Robolectric.buildActivity(MainActivity::class.java).get();startup(main,0,0)
        assertTrue(ReflectionHelpers.getField<Boolean>(main,"startupPanelPending"))
        ReflectionHelpers.setField(main,"imeVisible",true);startup(main,840,480)
        assertTrue(ReflectionHelpers.getField<Boolean>(main,"startupPanelPending"))
        ReflectionHelpers.setField(main,"imeVisible",false);startup(main,840,480)
        assertFalse(ReflectionHelpers.getField<Boolean>(main,"startupPanelPending"))
    }
    @Test fun rotationAndSplitScreenDoNotOverwriteTheCurrentPanelChoiceOrFraction(){
        val main=Robolectric.buildActivity(MainActivity::class.java).get();startup(main,1280,800)
        ReflectionHelpers.setField(main,"panelVisible",false);ReflectionHelpers.setField(main,"panelState",PanelState(.52f,.42f,false))
        startup(main,600,900);startup(main,1400,700)
        assertFalse(ReflectionHelpers.getField<Boolean>(main,"panelVisible"))
        assertEquals(.52f,ReflectionHelpers.getField<PanelState>(main,"panelState").bottomFraction,0f)
    }
    @Test fun fixedColumnsBeginAt840dpAndShortTabletWindowsFallBackToDrawer(){
        for(w in listOf(599,600,650,839,840,1000,1280))for(h in listOf(300,359,360,479,480,800)){
            val geometry=WorkspaceGeometry(w,h,false);val expected=w>=840&&h>=360
            assertEquals("$w x $h",expected,geometry.showExplorerSidebar)
            assertEquals(expected,geometry.showActivityBar)
            assertEquals(if(expected)ExplorerMode.SIDEBAR else ExplorerMode.OVERLAY,ExplorerLayout.resolve(geometry,260).mode)
        }
    }
    @Test fun densityAndImeResizeDoNotDependOnPortraitOrLandscape(){
        val landscape=WorkspaceGeometry.fromPixels(2560,1600,2f,false)
        val portrait=WorkspaceGeometry.fromPixels(1600,2560,2f,false)
        assertTrue(landscape.showExplorerSidebar);assertFalse(portrait.showExplorerSidebar)
        val split=WorkspaceGeometry.fromPixels(1680,720,2f,true)
        assertTrue(split.showExplorerSidebar)
        assertFalse(WorkspaceGeometry.fromPixels(1680,718,2f,true).showExplorerSidebar)
    }
}
