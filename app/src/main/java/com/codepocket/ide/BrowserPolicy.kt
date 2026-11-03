package com.codepocket.ide

import java.net.IDN
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

internal enum class WebSearchEngine(val title:String,val endpoint:String,val query:String) {
    BING("必应","https://cn.bing.com/search","q"),
    BAIDU("百度","https://www.baidu.com/s","wd");
    fun search(value:String)=endpoint+"?"+query+"="+URLEncoder.encode(value,"UTF-8").replace("+","%20")
    companion object {fun restored(value:String?)=values().firstOrNull{it.name==value}?:BING}
}

/** Address-bar search and page navigation differ: page links never turn into implicit searches. */
internal object BrowserAddress {
    private val blocked=Regex("(?i)^(?:javascript|data|file|content|intent|about|mailto|tel|sms|ftp):")
    private val hostPort=Regex("(?i)^(?:localhost|[a-z0-9.-]+|\\[[0-9a-f:]+]):[0-9]+(?:[/#?].*)?$")
    fun resolve(input:String,engine:WebSearchEngine=WebSearchEngine.BING):String {
        val value=checked(input)
        require(!blocked.containsMatchIn(value) && !Regex("^[a-zA-Z]:[\\\\/]").containsMatchIn(value)) { "仅支持网页地址和搜索关键词" }
        if(value.startsWith("https://",true) || value.startsWith("http://",true))return navigation(value)
        val domain=value.substringBefore('/').substringBefore('?').substringBefore('#')
        val looksLikeHost=value.none{it.isWhitespace()} && (domain.contains('.') || domain=="localhost" || hostPort.matches(value) || domain.startsWith('['))
        return if(looksLikeHost)navigation("https://"+value) else engine.search(value)
    }
    fun navigation(input:String):String {
        val value=checked(input);val uri=try{URI(value)}catch(_:java.net.URISyntaxException){throw IllegalArgumentException("网页地址无效")}
        val scheme=uri.scheme?.lowercase(Locale.ROOT);require(scheme in setOf("http","https") && !uri.isOpaque && uri.rawUserInfo==null)
        val authority=uri.rawAuthority?:throw IllegalArgumentException("缺少网页主机")
        require('@' !in authority && '\\' !in value)
        val host:String;val port:String
        if(authority.startsWith('[')) {
            val end=authority.indexOf(']');require(end>1);host=authority.substring(0,end+1).lowercase(Locale.ROOT)
            require(URI("$scheme://$host").host!=null);port=authority.substring(end+1)
        } else {
            require(authority.count{it==':'}<=1)
            host=IDN.toASCII(authority.substringBefore(':'),IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            require(host.isNotEmpty());port=if(':' in authority)":"+authority.substringAfter(':') else ""
        }
        if(port.isNotEmpty()){val number=port.substring(1).toIntOrNull();require(port.startsWith(':') && port.substring(1).all{it.isDigit()} && number!=null && number in 1..65535)}
        val suffix=uri.rawPath.orEmpty().ifEmpty{"/"}+uri.rawQuery?.let{"?$it"}.orEmpty()+uri.rawFragment?.let{"#$it"}.orEmpty()
        return URI("$scheme://$host$port$suffix").toASCIIString()
    }
    private fun checked(value:String)=value.trim().also{
        require(it.isNotEmpty() && it.length<=8192 && it.none{char->char.code<32 || char.code==127}) { "网址或关键词无效" }
    }
}

internal enum class AuxiliarySide { LEFT, RIGHT }
internal enum class AuxiliaryMode { HIDDEN, SIDEBAR, FOCUS }
internal data class AuxiliaryLayout(val mode:AuxiliaryMode,val width:Int,val maximumWidth:Int) {
    companion object {
        const val DEFAULT=320
        const val MINIMUM=240
        const val EDITOR_MINIMUM=280
        const val HANDLE=12
        /** Width is the measured upper workspace, already excluding the fixed activity rail. */
        fun resolve(width:Int,height:Int,shown:Boolean,side:AuxiliarySide,userWidth:Int=DEFAULT,primaryWidth:Int=0,focus:Boolean=false):AuxiliaryLayout {
            if(!shown || width<=0 || height<=0)return AuxiliaryLayout(AuxiliaryMode.HIDDEN,0,0)
            if(focus)return AuxiliaryLayout(AuxiliaryMode.FOCUS,width,width)
            val primary=if(side==AuxiliarySide.RIGHT && primaryWidth>0)primaryWidth+HANDLE else 0
            val maximum=minOf(560,width-primary-EDITOR_MINIMUM-HANDLE)
            if(height<240 || maximum<MINIMUM)return AuxiliaryLayout(AuxiliaryMode.HIDDEN,0,maximum.coerceAtLeast(0))
            return AuxiliaryLayout(AuxiliaryMode.SIDEBAR,userWidth.coerceIn(MINIMUM,maximum),maximum)
        }
    }
}
