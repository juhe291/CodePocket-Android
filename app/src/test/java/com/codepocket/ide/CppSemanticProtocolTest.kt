package com.codepocket.ide

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal object CppFixtures {
    val root: File get() = File(System.getProperty("user.dir")!!).let { if (it.name == "app") it.parentFile!! else it }
    private val data by lazy { JSONObject(File(root, "verification/cpp-semantic-probe-2026-10-27/host-semantic-fixtures.json").readText()).also {
        check(it.getBoolean("real_backend") && !it.getBoolean("android_execution") && it.getBoolean("actual_android_headers"))
        check(it.getString("host_archive_sha256") == "aeb5a68414c4d10b0867a479c9f21593043cc6d122e39de61b3801c89ebd57fc")
    } }
    fun case(name: String) = data.getJSONObject("cases").getJSONObject(name)
    fun request(name: String): CompletionRequest = case(name).let { CompletionRequest("cpp:main", 1, it.getString("code"), it.getInt("caret"), it.getInt("caret"), true) }
    fun envelope(name: String) = JSONObject().put("engine", CppSemanticProtocol.ENGINE).put("available", true).put("response", JSONObject(case(name).getJSONObject("response").toString()))
    fun result(name: String) = CppSemanticProtocol.decode(request(name), envelope(name).toString(), case(name).getString("mode"))
}

class CppSemanticProtocolTest {
    @Test fun actualDotArrowNamespaceAndAndroidStandardLibraryHaveRealKindsAndEdits() {
        for (tag in listOf("dot", "arrow", "namespace", "vector")) {
            val result = CppFixtures.result(tag); assertTrue(tag, result.available)
            val batch = result.completion!!; assertEquals(CppSemanticProtocol.ENGINE, batch.source)
            assertTrue(batch.items.all { it.kind == CompletionKind.FUNCTION && it.semanticInsertion != null })
            assertFalse(batch.items.any { it.label.contains("secret_local") })
        }
    }
    @Test fun middleTokenConsumesItsSuffixAndUsesTheExistingRevisionGate() {
        val request = CppFixtures.request("middle"); val batch = CppFixtures.result("middle").completion!!
        val edit = batch.edit(request, 0)!!
        assertTrue(request.code.replaceRange(edit.start, edit.end, edit.text).endsWith("item.run();}"))
        assertFalse(edit.text.contains('$')); assertEquals("run()", edit.text)
        assertNull(batch.edit(request.copy(revision=2), 0)); assertNull(batch.edit(request.copy(document="other"), 0))
    }
    @Test fun unicodeCrLfOffsetsAndFinalStopKeepEmojiAndPlaceCaretInsideArguments() {
        val request = CppFixtures.request("unicode_crlf"); val batch = CppFixtures.result("unicode_crlf").completion!!
        val edit = batch.edit(request, 0)!!
        assertTrue(request.code.replaceRange(edit.start, edit.end, edit.text).contains("😀中文"))
        assertEquals("push_back()", edit.text); assertEquals(edit.start + edit.text.length - 1, edit.selectionStart)
        assertEquals(request.caret - 2, edit.start)
        assertEquals(request.caret, CppSemanticProtocol.offset(request.code, CppSemanticProtocol.position(request.code, request.caret)))
    }
    @Test fun realSignaturesPreserveOverloadsAndActualSecondParameters() {
        val single = CppFixtures.result("signature").signature!!
        assertTrue(single.reference.cpp && single.reference.semantic); assertEquals("combine", single.reference.name)
        assertEquals(listOf("int left", "const char *right"), single.reference.forms.single().parts)
        assertEquals(1, single.active(0)); assertEquals(single.request.code.lastIndexOf('('), single.openOffset)
        val overload = CppFixtures.result("overload").signature!!
        assertEquals(2, overload.reference.forms.size); assertEquals(1, overload.active(0)); assertEquals(1, overload.active(1))
        assertEquals(1, CppFixtures.result("overload_arity").signature!!.reference.forms.size)
    }
    @Test fun updatedAndClosedHeaderFixturesDoNotResurrectPreviousDefinitions() {
        val updated = CppFixtures.result("updated_header").completion!!
        assertTrue(updated.items.any { it.label.startsWith("save") }); assertFalse(updated.items.any { it.label.startsWith("run") || it.label.startsWith("old_disk") })
        val closed = CppFixtures.result("closed_header").completion!!
        assertTrue(closed.items.any { it.label.startsWith("old_disk") }); assertFalse(closed.items.any { it.label.startsWith("save") })
        assertTrue(CppFixtures.result("unknown").available); assertNull(CppFixtures.result("unknown").completion)
    }
    @Test fun malformedRangesSurrogatesExtraEditsAndWrongEnginesCannotProduceAnInsertion() {
        val request = CppFixtures.request("unicode_crlf")
        val raw = CppFixtures.envelope("unicode_crlf")
        val items = raw.getJSONObject("response").getJSONObject("result").getJSONArray("items")
        for (index in 0 until items.length()) items.getJSONObject(index).getJSONObject("textEdit").getJSONObject("range").getJSONObject("start").put("character", 999999)
        assertFalse(CppSemanticProtocol.decode(request, raw.toString(), "complete").available)
        assertFalse(CppSemanticProtocol.decode(request, CppFixtures.envelope("unicode_crlf").put("engine","other").toString(), "complete").available)
        assertThrows(IllegalArgumentException::class.java) { CppSemanticProtocol.position("😀x", 1) }
        assertThrows(IllegalArgumentException::class.java) { CppSemanticProtocol.offset("😀x", JSONObject().put("line",0).put("character",1)) }
        val extras = CppFixtures.envelope("dot"); extras.getJSONObject("response").getJSONObject("result").getJSONArray("items").getJSONObject(0).put("additionalTextEdits", org.json.JSONArray().put(JSONObject()))
        assertFalse(CppSemanticProtocol.decode(CppFixtures.request("dot"), extras.toString(), "complete").available)
    }
    @Test fun semanticSnippetStopsAreParsedAndUnsupportedGrammarFailsClosed() {
        assertEquals("call()" to 5, CppSemanticProtocol.snippet("call($0)", 2))
        assertEquals("name" to 4, CppSemanticProtocol.snippet("name", 1))
        for (text in listOf("call(\${1:arg})", "call($2)", "$0$0", "call(\${0:bad})"))
            assertThrows(IllegalArgumentException::class.java) { CppSemanticProtocol.snippet(text, 2) }
    }
    @Test fun fuzzyIndexNamesOutsideTheTypedPrefixAreFilteredWithoutInventingAFallback(){
        val request=CppFixtures.request("prefix")
        val raw=CppFixtures.envelope("prefix")
        val items=raw.getJSONObject("response").getJSONObject("result").getJSONArray("items")
        for(i in 0 until items.length())items.getJSONObject(i).put("filterText","unrelated_name_$i")
        val result=CppSemanticProtocol.decode(request,raw.toString(),"complete")
        assertTrue(result.available);assertNull(result.completion)
    }
    @Test fun clientSendsTheUnsavedUtf16SnapshotAndRejectsUnsupportedLanguages(){
        val request=CppFixtures.request("unicode_crlf");var payload:JSONObject?=null;var closed=false
        val client=CppSemanticClient(object:SemanticTransport {
            override fun analyze(value:String):String {payload=JSONObject(value);return CppFixtures.envelope("unicode_crlf").toString()}
            override fun close(){closed=true}
        })
        assertTrue(client.analyze(request,SemanticLocation("/private/context","main.cpp",true),"complete").available)
        assertEquals(request.code,payload!!.getString("code"));assertEquals(request.caret,payload!!.getInt("caret"));assertEquals("main.cpp",payload!!.getString("path"))
        assertFalse(client.analyze(request.copy(cpp=false),SemanticLocation("/private/context","main.cpp",true),"complete").available)
        client.close();assertTrue(closed)
    }
}
