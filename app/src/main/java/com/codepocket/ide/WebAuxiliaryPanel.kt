package com.codepocket.ide

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.codepocket.ide.animation.DialogMotion
import com.codepocket.ide.ui.*
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.Executors

internal data class AuxiliaryDocument(val title:String,val scope:String,val body:String,val markdown:Boolean=false,val sourceUrl:String?=null)
internal interface WebAuxiliaryHost {
    val motion:Boolean
    fun layoutChanged()
    fun closed()
    fun previewMarkdown()
    fun status(message:String)
    fun navigated()
}

/** Owns a single system WebView across rehosting, not editor, workspace, Git or terminal state. */
internal class WebAuxiliaryPanel(private val activity:Activity,private val host:WebAuxiliaryHost,saved:Bundle?=null):Closeable {
    private val prefs=activity.getSharedPreferences("codepocket_v2",Context.MODE_PRIVATE)
    private val handler=Handler(Looper.getMainLooper())
    private val renderer=Executors.newSingleThreadExecutor()
    @Volatile private var renderGeneration=0L
    @Volatile private var closed=false
    private var root:LinearLayout?=null
    private var webContainer:FrameLayout?=null
    private var web:WebView?=null
    private var address:EditText?=null
    private var title:TextView?=null
    private var message:TextView?=null
    private var progress:ProgressBar?=null
    private var navigationRow:LinearLayout?=null
    private var addressRow:LinearLayout?=null
    private var documentScroll:ScrollView?=null
    private var documentText:TextView?=null
    private var documentScope:TextView?=null
    private var back:View?=null;private var forward:View?=null;private var reload:ImageView?=null
    private var palette=DesignTokens.forContext(activity)
    private var menu:PopupMenu?=null
    private var currentUrl=""
    private var addressDraft=""
    private var addressEdited=false
    private var settingAddress=false
    private var pageTitle=""
    private var loading=false
    private var failure:String?=null
    private var browserMode=true
    private var document:AuxiliaryDocument?=null
    private var documentUnavailable=false
    private var restoreDocumentY=saved?.getInt("documentY",0)?:0
    private var restoreHistory:Bundle?=saved?.getBundle("history")
    private var restoreScrollX=saved?.getInt("scrollX",0)?:0
    private var restoreScrollY=saved?.getInt("scrollY",0)?:0
    private var pendingScroll=false
    private var resumed=false
    var shown=false
        private set
    var side=AuxiliarySide.RIGHT
        private set
    var width=AuxiliaryLayout.DEFAULT
        private set
    var focus=false
        private set
    var presented=AuxiliaryMode.HIDDEN
        private set
    var searchEngine=WebSearchEngine.BING
        private set
    init {
        runCatching{JSONObject(saved?.getString("state")?:prefs.getString("webAuxiliaryState","{}")?:"{}").also {value->
            shown=value.optBoolean("shown");side=AuxiliarySide.values().firstOrNull{it.name==value.optString("side")}?:AuxiliarySide.RIGHT
            width=value.optInt("width",AuxiliaryLayout.DEFAULT).coerceIn(240,560);focus=value.optBoolean("focus")
            searchEngine=WebSearchEngine.restored(value.optString("search"))
            currentUrl=value.optString("url").takeIf{it.isNotEmpty()}?.let{BrowserAddress.navigation(it)}.orEmpty()
            addressDraft=saved?.getString("address")?:currentUrl
            addressEdited=addressDraft!=currentUrl
            browserMode=value.optBoolean("browser",true)
            value.optJSONObject("document")?.let{doc->
                document=AuxiliaryDocument(doc.optString("title"),doc.optString("scope"),doc.optString("body"),doc.optBoolean("markdown"),
                    doc.optString("source").takeIf{it.isNotEmpty()}?.let{runCatching{BrowserAddress.navigation(it)}.getOrNull()})
                documentUnavailable=!doc.has("body")
            }
        }}
        saved?.getBundle("document")?.let {value->
            val body=value.getString("body")
            if(body==null || body.length<=STATE_DOCUMENT_LIMIT){document=AuxiliaryDocument(value.getString("title").orEmpty(),value.getString("scope").orEmpty(),body.orEmpty(),value.getBoolean("markdown"),
                value.getString("source")?.let{runCatching{BrowserAddress.navigation(it)}.getOrNull()});documentUnavailable=body==null}
        }
        if(!browserMode && document==null){document=AuxiliaryDocument("文档","上次辅助视图","");documentUnavailable=true}
    }
    private fun dp(value:Int)=IdeControls.dp(activity,value)
    private fun state()=JSONObject().put("shown",shown).put("side",side.name).put("width",width).put("focus",focus)
        .put("search",searchEngine.name).put("url",currentUrl).put("browser",browserMode)
        .also{json->document?.let{doc->json.put("document",JSONObject().put("title",doc.title).put("scope",doc.scope).put("markdown",doc.markdown).put("source",doc.sourceUrl?:JSONObject.NULL)
            .also{if(!documentUnavailable && doc.body.length<=STATE_DOCUMENT_LIMIT)it.put("body",doc.body)})}}.toString()
    private fun persist(){prefs.edit().putString("webAuxiliaryState",state()).apply()}
    fun save(output:Bundle) {
        output.putString("state",state());output.putString("address",address?.text?.toString()?:addressDraft)
        output.putInt("documentY",documentScroll?.scrollY?:restoreDocumentY)
        web?.let {view->
            output.putInt("scrollX",view.scrollX);output.putInt("scrollY",view.scrollY)
            val history=Bundle();runCatching{view.saveState(history)}.getOrNull()?.let {
                val parcel=Parcel.obtain()
                try{parcel.writeBundle(history);if(parcel.dataSize()<=HISTORY_LIMIT)output.putBundle("history",history)}finally{parcel.recycle()}
            }
        }
        document?.let {doc->output.putBundle("document",Bundle().apply {
            putString("title",doc.title);putString("scope",doc.scope);putBoolean("markdown",doc.markdown)
            putString("source",doc.sourceUrl)
            if(!documentUnavailable && doc.body.length<=STATE_DOCUMENT_LIMIT)putString("body",doc.body)
        })}
        persist()
    }
    fun open(){shown=true;focus=false;persist();host.layoutChanged()}
    fun openFocus(){shown=true;focus=true;persist();host.layoutChanged()}
    fun hide(){shown=false;focus=false;persist();setPresented(AuxiliaryMode.HIDDEN);host.closed()}
    fun move(next:AuxiliarySide){side=next;persist();host.layoutChanged()}
    fun resized(next:Int){width=next.coerceIn(240,560);persist();host.layoutChanged()}
    fun resetWidth(){resized(AuxiliaryLayout.DEFAULT)}
    fun setPresented(mode:AuxiliaryMode){presented=mode;syncLifecycle()}
    fun hasInputFocus()=address?.hasFocus()==true || web?.hasFocus()==true
    fun activityResumed(value:Boolean){resumed=value;syncLifecycle()}
    private fun syncLifecycle(){web?.let{if(resumed && presented!=AuxiliaryMode.HIDDEN && browserMode)it.onResume() else it.onPause()}}
    fun detach(){(root?.parent as? ViewGroup)?.removeView(root)}
    fun view(nextPalette:IdePalette):View {
        val changed=palette!=nextPalette
        palette=nextPalette
        root?.let{existing->
            existing.setBackgroundColor(palette.surface)
            listOfNotNull(address,message,title,documentText,documentScope).forEach{WorkbenchStyle.content(it,palette)}
            message?.setTextColor(palette.muted);documentScope?.setTextColor(palette.muted)
            mapOf("web-menu" to IdeIcon.MORE,"web-close" to IdeIcon.CLOSE,"web-go" to IdeIcon.NEXT,"web-back" to IdeIcon.BACK,"web-forward" to IdeIcon.NEXT)
                .forEach{(key,icon)->existing.findViewWithTag<ImageView>(key)?.setImageDrawable(IdeIconDrawable(activity,icon,palette.foreground,dp(20)))}
            web?.setBackgroundColor(palette.editor);if(browserMode)ensureWeb();renderChrome();if(changed&&!browserMode)document?.let(::renderDocument);return existing
        }
        val container=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;tag="web-panel";setBackgroundColor(palette.surface)};root=container
        val heading=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,0,0)}
        title=TextView(activity).apply{gravity=Gravity.CENTER_VERTICAL;textSize=12f;setTextColor(palette.foreground);setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END}
        heading.addView(title,LinearLayout.LayoutParams(0,dp(48),1f))
        heading.addView(IdeControls.icon(activity,palette,IdeIcon.MORE,"网页与文档选项",{}).apply{tag="web-menu";setOnClickListener{showMenu(it)}},LinearLayout.LayoutParams(dp(48),dp(48)))
        heading.addView(IdeControls.icon(activity,palette,IdeIcon.CLOSE,"关闭辅助栏",::hide).apply{tag="web-close"},LinearLayout.LayoutParams(dp(48),dp(48)))
        container.addView(heading)
        documentScope=TextView(activity).apply{textSize=11f;setTextColor(palette.muted);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;setPadding(dp(12),0,dp(12),dp(8));visibility=View.GONE}
        container.addView(documentScope,LinearLayout.LayoutParams(-1,-2))
        addressRow=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(4),dp(4))}
        address=EditText(activity).apply {
            tag="web-address";contentDescription="网页地址或搜索关键词";setSingleLine();inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions=EditorInfo.IME_ACTION_GO;minHeight=dp(48);textSize=13f;setText(addressDraft)
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){addressDraft=s.toString();if(!settingAddress)addressEdited=true}
                override fun afterTextChanged(s:Editable?){}
            })
            setOnEditorActionListener{_,action,event->if(action==EditorInfo.IME_ACTION_GO || event?.keyCode==android.view.KeyEvent.KEYCODE_ENTER){
                if(event==null || event.action==android.view.KeyEvent.ACTION_DOWN)submit();true
            }else false}
        }
        addressRow!!.addView(address,LinearLayout.LayoutParams(0,-2,1f))
        addressRow!!.addView(IdeControls.icon(activity,palette,IdeIcon.NEXT,"打开网址或搜索",::submit).apply{tag="web-go"},LinearLayout.LayoutParams(dp(48),dp(48)))
        container.addView(addressRow)
        navigationRow=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
        back=IdeControls.icon(activity,palette,IdeIcon.BACK,"网页后退",{addressEdited=false;web?.takeIf{it.canGoBack()}?.goBack()}).apply{tag="web-back"}
        forward=IdeControls.icon(activity,palette,IdeIcon.NEXT,"网页前进",{addressEdited=false;web?.takeIf{it.canGoForward()}?.goForward()}).apply{tag="web-forward"}
        reload=IdeControls.icon(activity,palette,IdeIcon.REFRESH,"刷新网页",::reloadOrStop).apply{tag="web-reload"}
        listOf(back,forward,reload).forEach{navigationRow!!.addView(it,LinearLayout.LayoutParams(dp(48),dp(48)))}
        message=TextView(activity).apply{textSize=11f;setTextColor(palette.muted);maxLines=2;setPadding(dp(6),0,dp(8),0)}
        navigationRow!!.addView(message,LinearLayout.LayoutParams(0,-2,1f));container.addView(navigationRow)
        progress=ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=View.GONE;progressTintList=android.content.res.ColorStateList.valueOf(palette.accentText)}
        container.addView(progress,LinearLayout.LayoutParams(-1,dp(2)))
        webContainer=FrameLayout(activity).apply{tag="web-content"};container.addView(webContainer,LinearLayout.LayoutParams(-1,0,1f))
        documentText=TextView(activity).apply{tag="web-document-body";textSize=14f;setTextColor(palette.foreground);setPadding(dp(16),dp(12),dp(16),dp(20));setTextIsSelectable(true)}
        documentScroll=ScrollView(activity).apply{tag="web-document";addView(documentText);visibility=View.GONE};container.addView(documentScroll,LinearLayout.LayoutParams(-1,0,1f))
        WorkbenchStyle.content(container,palette);if(browserMode)ensureWeb();renderChrome();document?.let(::renderDocument)
        return container
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWeb(loadInitial:Boolean=true):WebView? {
        web?.let{return it};val parent=webContainer?:return null
        return try {
            WebView(activity).also {view->web=view;view.tag="web-view";view.setBackgroundColor(palette.editor)
                view.settings.apply {
                    javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false
                    @Suppress("DEPRECATION") allowFileAccessFromFileURLs=false
                    @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs=false
                    mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;javaScriptCanOpenWindowsAutomatically=false
                    setSupportMultipleWindows(false);setGeolocationEnabled(false);builtInZoomControls=true;displayZoomControls=false
                    useWideViewPort=true;loadWithOverviewMode=true
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(view,false)
                runCatching{if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings,palette.editor!=android.graphics.Color.WHITE)}
                view.webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(v:WebView,request:WebResourceRequest)=!allowed(request.url.toString())
                    @Deprecated("Required for older WebView providers") override fun shouldOverrideUrlLoading(v:WebView,url:String)=!allowed(url)
                    override fun onPageStarted(v:WebView,url:String?,favicon:Bitmap?){if(web!==v)return;loading=true;failure=null
                        runCatching{currentUrl=BrowserAddress.navigation(url.orEmpty())};renderChrome()}
                    override fun onPageFinished(v:WebView,url:String?){if(web!==v || (v.url!=null && v.url!=url))return
                        loading=false;renderChrome();persist();if(pendingScroll){pendingScroll=false;v.post{if(web===v)v.scrollTo(restoreScrollX,restoreScrollY)}}}
                    override fun doUpdateVisitedHistory(v:WebView,url:String?,isReload:Boolean){if(web===v){runCatching{currentUrl=BrowserAddress.navigation(url.orEmpty())};renderChrome();persist()}}
                    override fun onReceivedError(v:WebView,request:WebResourceRequest,error:WebResourceError){if(web===v&&request.isForMainFrame){loading=false;failure="网页加载失败，可刷新或在外部打开";renderChrome()}}
                    override fun onReceivedHttpError(v:WebView,request:WebResourceRequest,response:WebResourceResponse){if(web===v&&request.isForMainFrame){loading=false;failure="HTTP ${response.statusCode}";renderChrome()}}
                    override fun onReceivedSslError(v:WebView,handler:SslErrorHandler,error:SslError){handler.cancel();if(web===v){loading=false;failure="网页证书校验失败";renderChrome()}}
                    @android.annotation.TargetApi(26) override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean {
                        if(web===v){parent.removeView(v);web=null;v.destroy();loading=false;failure="网页进程已结束，可刷新重开";renderChrome()};return true
                    }
                }
                view.webChromeClient=object:WebChromeClient() {
                    override fun onProgressChanged(v:WebView,value:Int){if(web===v){progress?.progress=value;renderChrome()}}
                    override fun onReceivedTitle(v:WebView,value:String?){if(web===v){pageTitle=value.orEmpty();renderChrome()}}
                    override fun onPermissionRequest(request:PermissionRequest){request.deny()}
                }
                view.setDownloadListener{_,_,_,_,_->host.status("下载请使用辅助栏菜单的“在外部浏览器打开”")}
                parent.addView(view,FrameLayout.LayoutParams(-1,-1))
                runCatching{WebViewCompat.getCurrentWebViewPackage(activity)?.versionName}.getOrNull()?.let{parent.contentDescription="网页内容 · 系统 WebView $it"}
                val history=restoreHistory;restoreHistory=null
                val restored=history?.let{runCatching{view.restoreState(it)}.getOrNull()};pendingScroll=restoreScrollX!=0||restoreScrollY!=0
                if(restored==null && loadInitial && currentUrl.isNotEmpty())view.loadUrl(currentUrl)
                syncLifecycle()
            }
        }catch(_:Exception){discardFailedWeb();failure="系统网页组件不可用，可在外部浏览器打开";loading=false;renderChrome();null}
        catch(_:LinkageError){discardFailedWeb();failure="系统网页组件不可用";loading=false;renderChrome();null}
    }
    private fun discardFailedWeb(){web?.let{runCatching{(it.parent as? ViewGroup)?.removeView(it);it.destroy()}};web=null}
    private fun allowed(url:String):Boolean=try{BrowserAddress.navigation(url);true}catch(_:Exception){host.status("辅助栏仅打开 HTTP 或 HTTPS 网页");false}
    fun navigate(url:String) {
        val target=try{BrowserAddress.navigation(url)}catch(_:Exception){host.status("网页地址无效");return}
        browserMode=true;shown=true;currentUrl=target;addressDraft=target;addressEdited=false;setAddress(target);address?.clearFocus()
        failure=null;loading=true;ensureWeb(false)?.let{it.loadUrl(target);it.requestFocus()};renderChrome();persist();host.layoutChanged();host.navigated()
    }
    private fun submit(){val value=address?.text?.toString()?:addressDraft
        val target=try{BrowserAddress.resolve(value,searchEngine)}catch(_:Exception){address?.error="请输入有效网址或搜索关键词";return};navigate(target)}
    private fun reloadOrStop(){
        if(!browserMode){if(document?.markdown==true)host.previewMarkdown();return}
        if(loading){web?.stopLoading();loading=false;renderChrome();return}
        val existing=web;val view=ensureWeb(false)?:return
        if(existing==null && currentUrl.isNotEmpty())view.loadUrl(currentUrl) else if(currentUrl.isNotEmpty())view.reload()
    }
    fun backPressed():Boolean {if(!shown||presented==AuxiliaryMode.HIDDEN)return false
        if(browserMode&&web?.canGoBack()==true)web?.goBack() else hide();return true}
    private fun renderChrome() {
        title?.text=if(browserMode)pageTitle.takeIf{it.isNotBlank()}?:"网页" else document?.title?:"文档"
        documentScope?.text=document?.scope;documentScope?.visibility=if(browserMode)View.GONE else View.VISIBLE
        address?.hint="输入网址或用${searchEngine.title}搜索"
        if(address?.hasFocus()!=true && !addressEdited && currentUrl.isNotEmpty() && browserMode)setAddress(currentUrl)
        addressRow?.visibility=if(browserMode)View.VISIBLE else View.GONE
        navigationRow?.visibility=if(browserMode)View.VISIBLE else View.GONE
        webContainer?.visibility=if(browserMode)View.VISIBLE else View.GONE;documentScroll?.visibility=if(browserMode)View.GONE else View.VISIBLE
        back?.isEnabled=web?.canGoBack()==true;forward?.isEnabled=web?.canGoForward()==true
        reload?.setImageDrawable(IdeIconDrawable(activity,if(loading)IdeIcon.STOP else IdeIcon.REFRESH,palette.foreground,dp(20)))
        reload?.contentDescription=if(loading)"停止加载" else "刷新网页"
        message?.text=failure?:if(loading)"正在加载…" else if(currentUrl.isEmpty())"${searchEngine.title}搜索" else Uri.parse(currentUrl).host.orEmpty()
        progress?.visibility=if(browserMode&&loading)View.VISIBLE else View.GONE
        syncLifecycle()
    }
    private fun setAddress(value:String){if(address?.text?.toString()==value)return;settingAddress=true
        try{address?.setText(value)}finally{settingAddress=false}}
    fun showDocument(value:AuxiliaryDocument){document=value;documentUnavailable=false;restoreDocumentY=0;documentScroll?.scrollTo(0,0)
        browserMode=false;shown=true;failure=null;persist();renderChrome();renderDocument(value);host.layoutChanged()}
    private fun renderDocument(value:AuxiliaryDocument) {
        val target=documentText?:return;val token=++renderGeneration;target.text=if(value.markdown)"正在生成预览…" else value.body
        if(documentUnavailable){target.text="上次文档较长，未写入窗口状态。请在原文件或提示入口重新打开；原文件和编辑草稿仍保留。";return}
        fun restoreScroll(){val y=restoreDocumentY;restoreDocumentY=0;if(y>0)documentScroll?.post{documentScroll?.scrollTo(0,y)}}
        if(!value.markdown){restoreScroll();return}
        if(value.body.length>RENDER_LIMIT){documentScope?.text=value.scope+" · 长文档以纯文本显示";target.text=value.body;restoreScroll();return}
        val colors=palette
        renderer.execute {
            if(closed || token!=renderGeneration || Thread.currentThread().isInterrupted)return@execute
            val result=runCatching{Markwon.builder(activity).usePlugin(object:AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder:MarkwonConfiguration.Builder){builder.linkResolver{_,link->handler.post{if(!closed)navigate(link)}}}
                override fun configureTheme(builder:MarkwonTheme.Builder){builder.linkColor(colors.accentText).codeTextColor(colors.foreground).codeBackgroundColor(colors.panel)}
            }).build().toMarkdown(value.body)}
            handler.post {if(!closed && token==renderGeneration && document===value){
                result.onSuccess{target.text=it;target.setTextIsSelectable(true);target.movementMethod=LinkMovementMethod.getInstance();restoreScroll()}
                    .onFailure{documentScope?.text=value.scope+" · 预览失败，显示原文";target.text=value.body}
            }}
        }
    }
    private fun showMenu(anchor:View) {
        menu?.dismiss();val popup=DialogMotion.menu(activity,anchor,host.motion);menu=popup
        (popup as? WorkbenchMenu)?.setHeading("网页与文档")
        val labels=listOf("网页浏览","Python 标准库文档","C++ 标准库参考","预览当前 Markdown","移到左侧","移到右侧",if(focus)"退出网页专注视图" else "网页专注视图","恢复默认宽度","在外部浏览器打开",if(browserMode)"复制网址" else "复制全文","搜索引擎：必应","搜索引擎：百度","打开文档来源")
        labels.forEachIndexed{index,label->popup.menu.add(if(index>=10)3 else if(index>=4)2 else 1,index,index,label).apply{
            isEnabled=when(index){4->side!=AuxiliarySide.LEFT;5->side!=AuxiliarySide.RIGHT;8->browserMode&&currentUrl.isNotEmpty();9->if(browserMode)currentUrl.isNotEmpty() else document!=null&&!documentUnavailable;12->!browserMode&&document?.sourceUrl!=null;else->true}
            isCheckable=index in 10..11;isChecked=if(index==10)searchEngine==WebSearchEngine.BING else if(index==11)searchEngine==WebSearchEngine.BAIDU else false
        };(popup as? WorkbenchMenu)?.setItemAppearance(index,when(index){4->IdeIcon.BACK;5->IdeIcon.NEXT;6->IdeIcon.MAXIMIZE;9->IdeIcon.COPY;else->IdeIcon.HELP})}
        popup.setOnMenuItemClickListener{item->when(item.itemId) {
            0->{browserMode=true;ensureWeb();renderChrome();persist()}
            1->navigate("https://docs.python.org/3.13/library/")
            2->navigate("https://en.cppreference.com/w/cpp/header")
            3->host.previewMarkdown()
            4->move(AuxiliarySide.LEFT)
            5->move(AuxiliarySide.RIGHT)
            6->{focus=!focus;persist();host.layoutChanged()}
            7->resetWidth()
            8->runCatching{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(BrowserAddress.navigation(currentUrl))).addCategory(Intent.CATEGORY_BROWSABLE))}.onFailure{host.status("没有可用的外部浏览器")}
            9->{val value=if(browserMode)currentUrl else document?.body.orEmpty();runCatching{(activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(if(browserMode)"网页地址" else document?.title,value))}.onSuccess{host.status(if(browserMode)"已复制网址" else "已复制全文")}.onFailure{host.status("复制失败，可选择部分文本复制")}}
            10,11->{searchEngine=if(item.itemId==10)WebSearchEngine.BING else WebSearchEngine.BAIDU;persist();renderChrome()}
            12->document?.sourceUrl?.let(::navigate)
        };true};popup.show()
    }
    override fun close(){closed=true;renderGeneration++;renderer.shutdownNow();menu?.dismiss();handler.removeCallbacksAndMessages(null)
        detach();web?.let{(it.parent as? ViewGroup)?.removeView(it);it.stopLoading();it.destroy()};web=null;root=null}
    companion object {private const val HISTORY_LIMIT=128*1024;private const val STATE_DOCUMENT_LIMIT=32768;private const val RENDER_LIMIT=128000}
}
