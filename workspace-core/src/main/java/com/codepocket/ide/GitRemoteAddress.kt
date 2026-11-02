package com.codepocket.ide

import java.net.IDN
import java.net.URI
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Mirrors the pinned Python transport's HTTPS repository identity, without DNS or I/O. */
object GitRemoteAddress {
    private fun utf8(value:String)=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(value)).let{buffer->ByteArray(buffer.remaining()).also{buffer.get(it)}}
    private fun segment(raw:String):String {
        val bytes=java.io.ByteArrayOutputStream();var i=0
        while(i<raw.length) {
            if(raw[i]=='%') {require(i+2<raw.length);bytes.write(raw.substring(i+1,i+3).toInt(16));i+=3}
            else {val end=raw.indexOf('%',i).let{if(it<0)raw.length else it};bytes.write(utf8(raw.substring(i,end)));i=end}
        }
        val decoded=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        require(decoded.isNotEmpty() && decoded !in setOf(".","..") && decoded.none{it.code<=32 || it.code==127 || it in "/\\"})
        return utf8(decoded).joinToString(""){byte->val b=byte.toInt() and 255
            if(b in 65..90 || b in 97..122 || b in 48..57 || b.toChar() in "-._~")b.toChar().toString() else "%%%02X".format(b)}
    }
    private fun ipv6(raw:String):String {
        var text=raw.lowercase();require('%' !in text)
        if('.' in text) {
            val tail=text.substringAfterLast(':');val parts=tail.split('.')
            require(parts.size==4 && parts.all{it.isNotEmpty() && it.all(Char::isDigit) && (it=="0" || !it.startsWith('0')) && it.toInt() in 0..255})
            val numbers=parts.map(String::toInt)
            text=text.substringBeforeLast(':')+":"+((numbers[0] shl 8)+numbers[1]).toString(16)+":"+((numbers[2] shl 8)+numbers[3]).toString(16)
        }
        require(text.all{it in "0123456789abcdef:"} && text.split("::").size<=2)
        fun words(value:String)=if(value.isEmpty())emptyList() else value.split(':').map {
            require(it.length in 1..4);it.toInt(16)
        }
        val sides=text.split("::");val left=words(sides[0]);val right=if(sides.size==2)words(sides[1]) else emptyList()
        val all=if(sides.size==2){require(left.size+right.size<8);left+List(8-left.size-right.size){0}+right}
            else left.also{require(it.size==8)}
        var start=-1;var length=0;var i=0
        while(i<8){if(all[i]!=0){i++;continue};val from=i;while(i<8 && all[i]==0)i++;if(i-from>length){start=from;length=i-from}}
        if(length<2)return all.joinToString(":"){it.toString(16)}
        return all.take(start).joinToString(":"){it.toString(16)}+"::"+all.drop(start+length).joinToString(":"){it.toString(16)}
    }
    fun canonical(value:String):String=try{parse(value)}catch(_:Exception){throw IllegalArgumentException("仅支持无凭据的有效HTTPS仓库地址")}
    private fun parse(value:String):String {
        require(value.length in 1..2048 && value.none{it.code<=32 || it.code==127 || it=='\\'} && '?' !in value && '#' !in value)
        val uri=URI(value);require(uri.scheme.equals("https",true) && uri.rawUserInfo==null)
        val authority=uri.rawAuthority?:error("HTTPS地址缺少主机");require('@' !in authority)
        val host:String;val portText:String
        if(authority.startsWith('[')) {
            val end=authority.indexOf(']');require(end>1)
            host="["+ipv6(authority.substring(1,end))+"]"
            val rest=authority.substring(end+1);require(rest.isEmpty() || rest.startsWith(':'));portText=rest.removePrefix(":")
            require(rest.isEmpty() || portText.isNotEmpty())
        } else {
            require(authority.count{it==':'}<=1)
            val rawHost=authority.substringBefore(':');require(rawHost.isNotEmpty())
            host=IDN.toASCII(rawHost).lowercase().trimEnd('.');require(host.isNotEmpty() && Regex("[a-z0-9.-]+").matches(host))
            portText=if(':' in authority)authority.substringAfter(':').also{require(it.isNotEmpty())} else ""
        }
        val port=if(portText.isEmpty())null else portText.also{require(it.all{c->c in '0'..'9'})}.toInt().also{require(it in 1..65535)}
        val path=uri.rawPath.orEmpty().trimEnd('/');require(path.isEmpty() || path.startsWith('/'))
        val encoded=if(path.isEmpty())"" else path.substring(1).split('/').joinToString("/"){segment(it)}
        return "https://"+host+(if(port==null || port==443)"" else ":$port")+"/"+encoded
    }
}
