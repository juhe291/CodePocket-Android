package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Recorded real pinned clangd replies; malformed variants below are rejection tests only. */
internal object HeaderFixtures {
    private val data by lazy {
        JSONObject(File(CppFixtures.root,"verification/cpp-header-completion-2026-10-27/host-header-fixtures.json").readText()).also {
            check(it.getBoolean("real_backend") && !it.getBoolean("android_execution"))
        }.getJSONObject("cases")
    }
    fun case(tag:String)=data.getJSONObject(tag)
    fun request(tag:String)=case(tag).let{CompletionRequest("cpp:main",3,it.getString("code"),it.getInt("caret"),it.getInt("caret"),true)}
    fun envelope(tag:String)=JSONObject().put("engine",CppSemanticProtocol.ENGINE).put("available",true)
        .put("response",JSONObject(case(tag).getJSONObject("response").toString()))
    fun result(tag:String)=CppSemanticProtocol.decode(request(tag),envelope(tag).toString(),"complete")
    fun raw(code:String,caret:Int,start:Int,end:Int,text:String,kind:Int=17):String {
        require(caret in start..end)
        val range=JSONObject().put("start",CppSemanticProtocol.position(code,start)).put("end",CppSemanticProtocol.position(code,end))
        val item=JSONObject().put("label",text).put("filterText",text).put("kind",kind).put("insertTextFormat",1)
            .put("textEdit",JSONObject().put("range",range).put("newText",text))
        return JSONObject().put("engine",CppSemanticProtocol.ENGINE).put("available",true)
            .put("response",JSONObject().put("result",JSONArray().put(item))).toString()
    }
}

class CppHeaderCompletionTest {
    @Test fun includeContextSupportsSpacingButExcludesCommentsRawStringsAndSplicedLines(){
        for(code in listOf("#include<vec"," \t# include \"mod","#include \"sub/","constexpr int limit=1'000;\n#include <vec","auto ch=u8'a';\n#include <vec")){
            val request=CompletionRequest("cpp:main",1,code,code.length,code.length,true)
            assertNotNull(code,CppHeaderCompletion.context(request));assertNull(CppHeaderCompletion.context(request.copy(cpp=false)))
        }
        for(code in listOf("/*\n#include <vec","// escaped\\\n#include <vec","#define TEXT \\\n#include <vec","auto s=R\"raw(\n#include <vec","#include <vector> // outside","int value = 2 < vec")){
            val request=CompletionRequest("cpp:main",1,code,code.length,code.length,true)
            assertNull(code,CppHeaderCompletion.context(request))
        }
    }
    @Test fun aCaretOnlyBackendRangeConsumesTheWholeOldPathAndKeepsTrailingComment(){
        val code="#include \"sub/fiOLD.hpp\" // keep\r\nint value;";val caret=code.indexOf("OLD")
        val request=CompletionRequest("cpp:main",1,code,caret,caret,true)
        val batch=requireNotNull(CppSemanticProtocol.decode(request,HeaderFixtures.raw(code,caret,14,caret,"file.hpp\""),"complete").completion)
        val edit=requireNotNull(batch.edit(request,0));assertEquals("#include \"sub/file.hpp\" // keep\r\nint value;",code.replaceRange(edit.start,edit.end,edit.text))
    }
    @Test fun actualSystemAndProjectFilesAreHeaderCandidatesWithOneClosingDelimiter(){
        for(tag in listOf("system","project","nested","system-nested","unicode-crlf")){
            val request=HeaderFixtures.request(tag);val result=HeaderFixtures.result(tag)
            assertTrue(tag,result.available);val batch=requireNotNull(result.completion)
            assertTrue(tag,batch.items.all{it.kind.title=="头文件"})
            val edit=requireNotNull(batch.edit(request,0));val text=request.code.replaceRange(edit.start,edit.end,edit.text)
            val expected=HeaderFixtures.case(tag).getString("expected")
            assertTrue(tag,text.contains(expected));assertEquals(tag,1,text.count{it==if(tag in listOf("system","system-nested"))'>' else '"'}-if(tag in listOf("system","system-nested"))0 else 1)
            assertEquals(CppSemanticProtocol.ENGINE,batch.source)
        }
    }
    @Test fun actualClosedAndMiddlePathsConsumeTheOldSuffixAndDoNotDuplicateQuotes(){
        for(tag in listOf("middle","closed-system","closed-project")){
            val request=HeaderFixtures.request(tag);val result=HeaderFixtures.result(tag)
            assertTrue("$tag: $result",result.available);val batch=requireNotNull(result.completion)
            val expected=HeaderFixtures.case(tag).getString("expected")
            val index=batch.items.indexOfFirst{it.semanticInsertion!!.change.text.startsWith(expected)};assertTrue(index>=0)
            val edit=requireNotNull(batch.edit(request,index));val changed=request.code.replaceRange(edit.start,edit.end,edit.text)
            assertEquals(tag,if(tag=="closed-system")"#include <vector>\n" else if(tag=="middle")"#include \"sub/file.hpp\"\n" else "#include \"model.hpp\"\n",changed)
            assertEquals(edit.start+edit.text.length,edit.selectionStart)
        }
    }
    @Test fun actualDirectoryCandidateRemainsOpenAndTheNextLevelUsesItsOwnEdit(){
        val request=HeaderFixtures.request("directory");val batch=requireNotNull(HeaderFixtures.result("directory").completion)
        val index=batch.items.indexOfFirst{it.semanticInsertion!!.change.text=="sub/"};assertTrue(index>=0)
        assertEquals("目录",batch.items[index].kind.title)
        val edit=requireNotNull(batch.edit(request,index));assertEquals("#include \"sub/",request.code.replaceRange(edit.start,edit.end,edit.text))
        val next=HeaderFixtures.request("directory-next");val nextBatch=requireNotNull(HeaderFixtures.result("directory-next").completion)
        val nextEdit=requireNotNull(nextBatch.edit(next,0));assertEquals("#include \"sub/file.hpp\"",next.code.replaceRange(nextEdit.start,nextEdit.end,nextEdit.text))
    }
    @Test fun headerEditsKeepRevisionDocumentSelectionAndSizeGates(){
        val request=HeaderFixtures.request("project");val batch=requireNotNull(HeaderFixtures.result("project").completion)
        assertNull(batch.edit(request.copy(revision=4),0));assertNull(batch.edit(request.copy(document="other"),0))
        assertNull(batch.edit(request.copy(end=request.end+1),0));assertNull(batch.edit(request,0,request.code.length))
    }
    @Test fun fileKindCannotBypassIncludeContextOrEditTheDirectiveOrFollowingCode(){
        for(code in listOf("int mod", "// #include \"mod", "/*\n#include \"mod", "const char* s=\"mod")){
            val caret=code.length;val start=caret-3
            val request=CompletionRequest("cpp:main",1,code,caret,caret,true)
            assertFalse(code,CppSemanticProtocol.decode(request,HeaderFixtures.raw(code,caret,start,caret,"model.hpp\""),"complete").available)
        }
        val code="#include \"mod\" // keep\nint value;";val caret=13
        val request=CompletionRequest("cpp:main",1,code,caret,caret,true)
        assertFalse(CppSemanticProtocol.decode(request,HeaderFixtures.raw(code,caret,0,code.length,"model.hpp\""),"complete").available)
    }
    @Test fun headerCandidateCannotInsertANewDirectiveEscapeQuotesOrUseSymbolSnippets(){
        val code="#include \"mod";val caret=code.length;val request=CompletionRequest("cpp:main",1,code,caret,caret,true)
        for(text in listOf("model.hpp\"\n#error injected","model\"oops.hpp\"","model.hpp>","model\\escape.hpp\"","model.hpp\u0000\""))
            assertFalse(text,CppSemanticProtocol.decode(request,HeaderFixtures.raw(code,caret,10,caret,text),"complete").available)
        assertFalse(CppSemanticProtocol.decode(request,HeaderFixtures.raw(code,caret,10,caret,"model()",3),"complete").available)
    }
}
