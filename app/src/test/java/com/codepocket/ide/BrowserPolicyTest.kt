package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class BrowserPolicyTest {
    @Test fun defaultSearchIsBingAndBaiduCanBeRestoredWithoutGoogleFallback() {
        val text="C++ std::vector 中文 😀";val bing=URI(BrowserAddress.resolve(text))
        assertEquals("cn.bing.com",bing.host);assertEquals(text,URLDecoder.decode(bing.rawQuery.substringAfter("q="),"UTF-8"))
        val baidu=URI(BrowserAddress.resolve(text,WebSearchEngine.BAIDU));assertEquals("www.baidu.com",baidu.host)
        assertEquals(text,URLDecoder.decode(baidu.rawQuery.substringAfter("wd="),"UTF-8"))
        assertEquals(WebSearchEngine.BAIDU,WebSearchEngine.restored("BAIDU"));assertEquals(WebSearchEngine.BING,WebSearchEngine.restored("unknown"))
    }
    @Test fun typedHostAndExplicitHttpPreserveUnicodeEscapesAndLocalPorts() {
        assertEquals("https://example.com/docs?q=a%2Bb#part",BrowserAddress.resolve("example.com/docs?q=a%2Bb#part"))
        assertEquals("https://xn--bcher-kva.example/%E6%96%87%E6%A1%A3",BrowserAddress.resolve("bücher.example/文档"))
        assertEquals("https://localhost:8080/",BrowserAddress.resolve("localhost:8080"))
        assertEquals("http://[::1]:8080/page",BrowserAddress.navigation("http://[::1]:8080/page"))
        assertEquals("http://127.0.0.1:8080/",BrowserAddress.resolve("http://127.0.0.1:8080"))
        assertTrue(BrowserAddress.resolve("std::vector").startsWith(WebSearchEngine.BING.endpoint))
    }
    @Test fun webpageLinksCannotTurnFileJavascriptCredentialsOrUnknownSchemesIntoNavigation() {
        for(value in listOf("javascript:alert(1)","file:///data/private","content://private/project","intent://app","data:text/html,private","mailto:x@example.invalid")) {
            assertThrows(IllegalArgumentException::class.java){BrowserAddress.resolve(value)}
            assertThrows(IllegalArgumentException::class.java){BrowserAddress.navigation(value)}
        }
        for(value in listOf("https://secret@example.com/","https://example.com:0/","https://example.com:99999/","https://example.com:\n/private","https://example.com\\private"))
            assertThrows(IllegalArgumentException::class.java){BrowserAddress.navigation(value)}
        assertThrows(IllegalArgumentException::class.java){BrowserAddress.navigation("normal search words")}
        assertThrows(IllegalArgumentException::class.java){BrowserAddress.resolve("C:\\private\\file.cpp")}
    }
    @Test fun bothSidebarsBudgetTheEditorAndMovingLeftReplacesThePrimaryBudget() {
        val both=AuxiliaryLayout.resolve(1052,700,true,AuxiliarySide.RIGHT,primaryWidth=240)
        assertEquals(AuxiliaryMode.SIDEBAR,both.mode);assertEquals(320,both.width)
        assertTrue(1052-240-24-both.width>=AuxiliaryLayout.EDITOR_MINIMUM)
        val squeezed=AuxiliaryLayout.resolve(792,700,true,AuxiliarySide.RIGHT,primaryWidth=240)
        assertEquals(AuxiliaryMode.SIDEBAR,squeezed.mode);assertEquals(248,squeezed.width)
        val left=AuxiliaryLayout.resolve(700,700,true,AuxiliarySide.LEFT,primaryWidth=240)
        assertEquals(AuxiliaryMode.SIDEBAR,left.mode);assertEquals(320,left.width)
    }
    @Test fun passiveShrinkHidesAuxiliaryButExplicitFocusKeepsTheMeasuredWorkspace() {
        assertEquals(AuxiliaryMode.HIDDEN,AuxiliaryLayout.resolve(640,700,true,AuxiliarySide.RIGHT,primaryWidth=240).mode)
        val focus=AuxiliaryLayout.resolve(363,360,true,AuxiliarySide.RIGHT,focus=true)
        assertEquals(AuxiliaryMode.FOCUS,focus.mode);assertEquals(363,focus.width)
        assertEquals(AuxiliaryMode.HIDDEN,AuxiliaryLayout.resolve(1100,700,false,AuxiliarySide.RIGHT,focus=true).mode)
        assertEquals(AuxiliaryMode.HIDDEN,AuxiliaryLayout.resolve(1100,150,true,AuxiliarySide.RIGHT).mode)
    }
    @Test fun resizeValuesAreBoundedWithoutChangingTheSavedPreference() {
        assertEquals(240,AuxiliaryLayout.resolve(1052,700,true,AuxiliarySide.RIGHT,-50,240).width)
        assertEquals(508,AuxiliaryLayout.resolve(1052,700,true,AuxiliarySide.RIGHT,9999,240).width)
        assertEquals(AuxiliaryMode.HIDDEN,AuxiliaryLayout.resolve(0,700,true,AuxiliarySide.RIGHT).mode)
    }
}
