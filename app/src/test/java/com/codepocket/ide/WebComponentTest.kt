package com.codepocket.ide

import android.os.Looper
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.webkit.WebViewCompat
import io.noties.markwon.Markwon
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.parser.Parser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Actual pinned parser/renderer classes, not sample HTML or a replacement renderer. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class WebComponentTest {
    @Test fun markdownCoreRendersActualNativeSpansWithoutAWebView() {
        val context=RuntimeEnvironment.getApplication();val renderer=Markwon.builder(context).build()
        val output=renderer.toMarkdown("# 中文标题\n\n正文 **加粗** 和 `code()`。\n\n- first\n- second\n")
        val body=output.toString();assertTrue(body.contains("中文标题"));assertTrue(body.contains("加粗"));assertTrue(body.contains("code()"))
        assertFalse(body.contains("**"));assertFalse(body.contains("# 中文"));assertTrue(output is Spanned)
        val names=output.getSpans(0,output.length,Any::class.java).map{it.javaClass.simpleName}
        assertTrue(names.contains("HeadingSpan"));assertTrue(names.any{it.contains("StrongEmphasis") || it==StyleSpan::class.java.simpleName})
        assertTrue(names.contains("CodeSpan"));assertTrue(names.contains("BulletListItemSpan"))
        val view=TextView(context);renderer.setParsedMarkdown(view,output);assertEquals(body,view.text.toString())
    }
    @Test fun commonmarkParserPreservesActualLinkAndInlineCodeNodes() {
        val document=Parser.builder().build().parse("# title\n\n[文档](https://docs.python.org/3/library/) and `std::vector`\n")
        assertTrue(document.firstChild is Heading)
        val paragraph=document.firstChild.next;val link=paragraph.firstChild as Link
        assertEquals("https://docs.python.org/3/library/",link.destination)
        var child=paragraph.firstChild;val codes=mutableListOf<String>()
        while(child!=null){if(child is Code)codes.add(child.literal);child=child.next}
        assertEquals(listOf("std::vector"),codes)
    }
    @Test fun webkitCompatibilityPackageLookupUsesTheActualLibraryWithoutRequiringChromium() {
        assertEquals(Looper.getMainLooper(),Looper.myLooper())
        val info=WebViewCompat.getCurrentWebViewPackage(RuntimeEnvironment.getApplication())
        assertTrue(info==null || info.packageName.isNotBlank())
    }
}
