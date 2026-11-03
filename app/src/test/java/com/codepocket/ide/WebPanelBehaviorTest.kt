package com.codepocket.ide

import android.app.Activity
import android.os.Looper
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.TextView
import com.codepocket.ide.ui.DesignTokens
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class WebPanelBehaviorTest {
    private val messages=mutableListOf<String>()
    private val controller=Robolectric.buildActivity(Activity::class.java)
    private val activity=controller.setup().get()
    private val panel=WebAuxiliaryPanel(activity,object:WebAuxiliaryHost {
        override val motion=false
        override fun layoutChanged(){}
        override fun closed(){}
        override fun previewMarkdown(){}
        override fun status(message:String){messages.add(message)}
        override fun navigated(){}
    })
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun root()=panel.view(DesignTokens.palette(true))
    private fun web()=views(root()).filterIsInstance<WebView>().single()
    @After fun close(){panel.close();controller.pause().stop().destroy();Shadows.shadowOf(Looper.getMainLooper()).idle()}
    @Test @Config(sdk=[28]) fun olderProviderPathKeepsNativeFileAndContentAccessDisabled() {
        val view=web();assertTrue(view.settings.javaScriptEnabled);assertFalse(view.settings.allowFileAccess);assertFalse(view.settings.allowContentAccess)
        @Suppress("DEPRECATION") assertFalse(view.settings.allowFileAccessFromFileURLs)
        @Suppress("DEPRECATION") assertFalse(view.settings.allowUniversalAccessFromFileURLs)
        assertFalse(view.settings.supportMultipleWindows());assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,view.settings.mixedContentMode)
    }
    private fun request(main:Boolean,url:String="https://example.org/")=object:WebResourceRequest {
        override fun getUrl()=Uri.parse(url)
        override fun isForMainFrame()=main
        override fun isRedirect()=false
        override fun hasGesture()=true
        override fun getMethod()="GET"
        override fun getRequestHeaders()=emptyMap<String,String>()
    }
    @Test fun subresourceHttpErrorsDoNotReplaceTheMainPageStatusAndPrivateSchemesStayBlocked() {
        val view=web();val client=view.webViewClient
        val response=WebResourceResponse("text/plain","UTF-8",404,"Not Found",emptyMap(),ByteArrayInputStream(byteArrayOf()))
        client.onReceivedHttpError(view,request(false),response);assertFalse(views(root()).filterIsInstance<TextView>().any{it.text.contains("HTTP 404")})
        client.onReceivedHttpError(view,request(true),response);assertTrue(views(root()).filterIsInstance<TextView>().any{it.text.contains("HTTP 404")})
        for(url in listOf("file:///data/private","content://private/root","javascript:alert(1)","intent://app"))assertTrue(client.shouldOverrideUrlLoading(view,request(true,url)))
        assertFalse(client.shouldOverrideUrlLoading(view,request(true,"https://docs.python.org/3/library/")));assertTrue(messages.isNotEmpty())
    }
    @Test fun longMarkdownFallsBackToTheEntireOriginalAndHidingDoesNotDestroyTheWebView() {
        val view=web();val body="# title\n"+"body\n".repeat(27000)+"保留最后一行 😀"
        panel.showDocument(AuxiliaryDocument("long.md","current unsaved snapshot",body,true))
        val text=views(root()).single{it.tag=="web-document-body"} as TextView
        assertEquals(body,text.text.toString());assertTrue(text.isTextSelectable)
        panel.hide();panel.open();assertSame(view,ReflectionHelpers.getField<WebView>(panel,"web"));assertEquals(body,text.text.toString())
        assertFalse(Shadows.shadowOf(view).wasDestroyCalled())
    }
}
