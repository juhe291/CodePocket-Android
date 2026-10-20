package com.codepocket.ide.ui

import org.json.JSONArray
import org.json.JSONObject

/** Bounded, workspace-scoped presentation state; never grants filesystem authority. */
data class ExplorerUiState(val expanded:Set<String> = setOf(""), val selected:String? = null, val scroll:Int = 0) {
    fun normalized():ExplorerUiState {
        fun valid(path:String)=path.isEmpty()||(!path.startsWith('/')&&!path.contains('\\')&&path.split('/').none{it==".."||it=="."||it.isEmpty()}&&!path.any{it.isISOControl()})
        var length=0
        val paths=expanded.asSequence().filter(::valid).take(512).filter {length+=it.length;length<=32768}.toSet()
        return copy(expanded=paths,selected=selected?.takeIf(::valid),scroll=scroll.coerceAtLeast(0))
    }
    fun toJson():String=normalized().let{s->JSONObject().put("expanded",JSONArray(s.expanded.toList())).put("selected",s.selected).put("scroll",s.scroll).toString()}
    companion object {
        fun fromJson(value:String?):ExplorerUiState=runCatching {
            if(value==null)return@runCatching ExplorerUiState()
            val j=JSONObject(value);val a=j.optJSONArray("expanded")?:JSONArray().put("")
            ExplorerUiState((0 until minOf(a.length(),512)).map{a.optString(it)}.toSet(),if(j.has("selected")&&!j.isNull("selected"))j.optString("selected") else null,j.optInt("scroll")).normalized()
        }.getOrDefault(ExplorerUiState())
    }
}
