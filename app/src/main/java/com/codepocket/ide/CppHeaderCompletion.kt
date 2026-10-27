package com.codepocket.ide

import org.json.JSONObject

/** Header edits have their own grammar; symbol edits retain the identifier-only contract. */
internal object CppHeaderCompletion {
    data class Context(val segmentStart:Int,val end:Int,val closing:Char)
    private val directive=Regex("^[ \\t]*#[ \\t]*include[ \\t]*([<\"])")
    private fun pathChar(ch:Char)=!ch.isISOControl() && ch !in "<>\"\\"

    fun context(request:CompletionRequest):Context? {
        val code=request.code;val caret=request.caret
        if(!request.cpp || code.length>45000 || caret!=request.end || !CppSemanticProtocol.boundary(code,caret))return null
        val lineStart=code.lastIndexOf('\n',caret-1)+1
        val lineEnd=code.indexOf('\n',lineStart).let{if(it<0)code.length else it}.let{if(it>lineStart && code[it-1]=='\r')it-1 else it}
        val match=directive.find(code.substring(lineStart,lineEnd))?:return null
        val hash=code.indexOf('#',lineStart);if(!outsideLiteral(code,hash))return null
        // A physically new line after a splice is still part of the previous preprocessing line.
        if(lineStart>0 && code.substring(0,lineStart-1).trimEnd('\r').endsWith('\\'))return null
        val start=lineStart+match.value.length;val closing=if(match.groupValues[1]=="<")'>' else '"'
        val closed=code.indexOf(closing,start).takeIf{it in start until lineEnd}
        val pathEnd=closed?:lineEnd
        if(caret !in start..pathEnd || pathEnd-start>2048 || !code.substring(start,pathEnd).all(::pathChar))return null
        val segment=(code.lastIndexOf('/',caret-1)+1).coerceAtLeast(start)
        return Context(segment,if(closed==null)pathEnd else closed+1,closing)
    }

    /** Scan only the bounded prefix. Includes inside raw strings or continued comments are not directives. */
    private fun outsideLiteral(code:String,at:Int):Boolean {
        var i=0
        while(i<at){
            when {
                code.startsWith("//",i)->{
                    do {val end=code.indexOf('\n',i);if(end<0 || end>=at)return false
                        val continued=code.substring(i,end).trimEnd('\r').endsWith('\\');i=end+1
                    }while(continued)
                }
                code.startsWith("/*",i)->{val end=code.indexOf("*/",i+2);if(end<0 || end+2>at)return false;i=end+2}
                code.startsWith("R\"",i)->{
                    val opened=code.indexOf('(',i+2)
                    if(opened in (i+2)..minOf(i+18,at-1) && code.substring(i+2,opened).none{it.isWhitespace() || it in "\\()"}){
                        val end=code.indexOf(")"+code.substring(i+2,opened)+"\"",opened+1)
                        if(end<0 || end>=at)return false;i=end+(opened-i-2)+2
                    }else i++
                }
                code[i].isDigit() && (i==0 || !(code[i-1].isLetterOrDigit() || code[i-1]=='_'))->{
                    // C++ numeric separators are not the start of character literals.
                    i++;while(i<at && (code[i].isLetterOrDigit() || code[i] in "_'."))i++
                }
                code[i]=='"' || code[i]=='\''->{
                    val quote=code[i++];var closed=false
                    while(i<at){val ch=code[i++];if(ch=='\\'){if(i<at)i++}else if(ch==quote){closed=true;break}}
                    if(!closed)return false
                }
                else->i++
            }
        }
        return true
    }

    fun item(context:Context,request:CompletionRequest,item:JSONObject,start:Int,end:Int):CompletionItem? {
        require(item.optInt("kind") in setOf(17,19) && item.optInt("insertTextFormat",1)==1)
        require(start==context.segmentStart && (end==request.caret || end==context.end))
        val insert=item.getJSONObject("textEdit").getString("newText")
        val directory=insert.endsWith('/')
        require(directory || insert.endsWith(context.closing))
        val name=insert.dropLast(1)
        require(name.length in 1..255 && name !in setOf(".","..") && name.all{pathChar(it) && it!='/'})
        val prefix=request.code.substring(start,request.caret)
        val filter=item.optString("filterText",item.getString("label").trim())
        require(filter.length in 1..1024 && filter.none{it.isISOControl()})
        if(!filter.startsWith(prefix) || !insert.startsWith(prefix))return null
        if(request.code.substring(start,context.end)==insert && request.caret==start+insert.length)return null
        return CompletionItem(if(directory)insert else name,if(directory)CompletionKind.DIRECTORY else CompletionKind.HEADER,
            if(directory)"继续选择此目录中的头文件。" else "clangd 返回的头文件路径。",
            semanticInsertion=CompletionEdit(TextChange(start,context.end,insert,start+insert.length)))
    }
}
