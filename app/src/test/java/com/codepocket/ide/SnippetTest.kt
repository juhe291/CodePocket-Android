package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SnippetTest {
    private fun request(code: String = "", cpp: Boolean = false, caret: Int = code.length) =
        CompletionRequest("workspace:file", 10, code, caret, caret, cpp)
    private fun apply(code: String, change: TextChange) = code.replaceRange(change.start, change.end, change.text)

    @Test fun catalogueAndCompletionKeepTemplatesSeparateFromLanguageNames() {
        val cpp = CodeSnippets.all(true); val python = CodeSnippets.all(false)
        assertEquals(6, cpp.size); assertEquals(6, python.size)
        assertEquals(12, (cpp + python).map { it.id }.distinct().size)
        val batch = LexicalCompletion.complete(request("pri"))!!
        assertTrue(batch.items.any { it.label == "print" && it.kind == CompletionKind.BUILTIN })
        assertTrue(batch.items.any { it.snippetId == "py.print" && it.kind == CompletionKind.SNIPPET })
        assertTrue(LexicalCompletion.complete(request("for"))!!.items.any { it.snippetId == "py.for" })
        assertFalse(LexicalCompletion.complete(request("for"), includeSnippets = false)?.items?.any { it.snippetId != null } == true)
    }

    @Test fun expansionUsesIndentPreferencesAndPreservesCrLfUtf16Coordinates() {
        val code = "# 😀\r\n\tfo\r\n"
        val req = request(code, caret = code.indexOf("fo") + 2)
        val expanded = CodeSnippets.expand("py.for", req, AppSettings(tabWidth = 8, useSpaces = false))!!
        val result = apply(code, expanded.change)
        assertEquals("# 😀\r\n\tfor item in range(10):\r\n\t\tpass\r\n", result)
        assertEquals("item", result.substring(expanded.stops[0].start, expanded.stops[0].end))
        assertEquals(expanded.stops.first().start, expanded.change.selectionStart)
        assertEquals(expanded.stops.first().end, expanded.change.selectionEnd)
        val spaces = CodeSnippets.expand("py.if", request("  if"), AppSettings(tabWidth = 2))!!
        assertEquals("  if True:\n    pass", apply("  if", spaces.change))
    }

    @Test fun oneRangeReplacementPreservesUnrelatedSourceAndRejectsAmbiguousContexts() {
        val code = "before = 2\n    if   \nafter = 3"
        val req = request(code, caret = code.indexOf("if") + 2)
        val expanded = CodeSnippets.expand("py.if", req, AppSettings())!!
        val result = apply(code, expanded.change)
        assertTrue(result.startsWith("before = 2\n    if True:\n        pass   \nafter = 3"))
        for (text in listOf("value = if", "if(x)", "obj.if", "# if", "\"\"\"\n   ")) {
            assertNull(text, CodeSnippets.expand("py.if", request(text), AppSettings()))
        }
        assertNull(CodeSnippets.expand("cpp.if", request("if"), AppSettings()))
        assertNull(CodeSnippets.expand("unknown", request(), AppSettings()))
        assertNull(CodeSnippets.expand("py.if", request("if").copy(end = 0), AppSettings()))
        assertNull(CodeSnippets.expand("py.if", request(" ".repeat(45001)), AppSettings()))
    }

    @Test fun completionInsertionChecksIdentityRevisionSelectionAndCapacityForRealBody() {
        val req = request("if")
        val batch = LexicalCompletion.complete(req)!!
        val index = batch.items.indexOfFirst { it.snippetId == "py.if" }
        val insertion = batch.insertion(req, index, AppSettings(tabWidth = 2), 200000)!!
        assertEquals("if True:\n  pass", insertion.change.text)
        assertNotNull(insertion.snippet)
        assertNull(batch.insertion(req.copy(document = "other"), index, AppSettings(), 200000))
        assertNull(batch.insertion(req.copy(revision = 11), index, AppSettings(), 200000))
        assertNull(batch.insertion(req.copy(code = "ix"), index, AppSettings(), 200000))
        assertNull(batch.insertion(req.copy(end = 0), index, AppSettings(), 200000))
        assertNull(batch.insertion(req, index, AppSettings(), 5))
        val exact = CodeSnippets.expand("py.if", req, AppSettings())!!
        assertNotNull(CodeSnippets.expand("py.if", req, AppSettings(), exact.change.text.length))
        assertNull(CodeSnippets.expand("py.if", req, AppSettings(), exact.change.text.length - 1))
    }

    @Test fun navigationHandlesEmptyStopsReverseAndFinalCaretWithoutEditing() {
        val expanded = CodeSnippets.expand("cpp.func", request(cpp = true), AppSettings())!!
        var session = expanded.session("workspace:file", 11, expanded.change.text.length)
        assertEquals("函数名", session.active.label)
        assertEquals(0, session.move(true).session!!.index)
        session = session.move(false).session!!
        assertEquals(session.active.start, session.active.end) // Empty parameter field.
        assertEquals(0, session.move(true).session!!.index)
        session = session.move(false).session!!
        assertEquals("代码体", session.active.label)
        val finish = session.move(false)
        assertNull(finish.session); assertEquals(expanded.finalOffset, finish.start); assertEquals(finish.start, finish.end)
    }

    @Test fun fieldReplacementMovesLaterStopsAndFinalOffsetByUtf16Delta() {
        val expansion = CodeSnippets.expand("py.for", request(), AppSettings())!!
        val before = expansion.change.text
        val original = expansion.session("workspace:file", 11, before.length)
        val replacement = "索引😀x"
        val after = original.edited(original.document, 11, original.active.start,
            original.active.end - original.active.start, replacement.length)!!
        val delta = replacement.length - 4
        assertEquals(original.stops[1].start + delta, after.stops[1].start)
        assertEquals(original.finalOffset + delta, after.finalOffset)
        assertEquals(original.length + delta, after.length)
        assertTrue(after.copy(revision = 12).matches(original.document, 12, after.active.end, after.active.start))
    }

    @Test fun editsOutsideActiveStopOrAnotherBufferEndNavigationOnly() {
        val expansion = CodeSnippets.expand("py.for", request(), AppSettings())!!
        val session = expansion.session("workspace:file", 11, expansion.change.text.length)
        assertNull(session.edited("other", 11, session.active.start, 1, 1))
        assertNull(session.edited(session.document, 12, session.active.start, 1, 1))
        assertNull(session.edited(session.document, 11, session.active.start - 1, 0, 1))
        assertNull(session.edited(session.document, 11, session.active.end, 1, 0))
        assertNull(session.edited(session.document, 11, session.active.start, -1, 0))
        assertNull(session.edited(session.document, 11, session.active.start, 0, Int.MAX_VALUE))
        assertFalse(session.matches(session.document, 11, session.active.start - 1, session.active.end))
        assertFalse(session.matches("other", 11, session.active.start, session.active.end))
        assertFalse(session.matches(session.document, 12, session.active.start, session.active.end))
    }

    @Test fun compositionStyleReplacementsCanGrowShrinkAndDeleteAField() {
        val expanded = CodeSnippets.expand("py.print", request(), AppSettings())!!
        var session = expanded.session("workspace:file", 11, expanded.change.text.length)
        val start = session.active.start
        session = session.edited(session.document, 11, start, 1, 3)!!
        session = session.edited(session.document, 11, start, 3, 2)!!
        session = session.edited(session.document, 11, start, 2, 0)!!
        assertEquals(start, session.active.end)
        session = session.edited(session.document, 11, start, 0, 5)!!
        assertEquals(start + 5, session.active.end)
        assertEquals("print()".length + 5, session.length)
    }

    @Test fun automaticPairEditUsesTheSamePlaceholderRangeInsteadOfBreakingIt() {
        val expanded = CodeSnippets.expand("cpp.if", request(cpp = true), AppSettings())!!
        val original = expanded.change.text
        var session = expanded.session("workspace:file", 11, original.length)
        val at = session.active.start
        val removed = session.active.end - at
        val raw = original.replaceRange(at, at + removed, "(")
        session = session.edited(session.document, 11, at, removed, 1)!!
        val pair = EditorActions.typed(original, at, removed, "(", true)!!
        session = session.edited(session.document, 11, pair.start, pair.end - pair.start, pair.text.length)!!
        val result = apply(raw, pair)
        assertEquals("(true)", result.substring(session.active.start, session.active.end))
        assertEquals(result.length, session.length)
        assertTrue(session.matches(session.document, 11, pair.selectionStart, pair.selectionEnd))
    }

    @Test fun ordinaryCompletionInsideAFieldKeepsTheNextTabStopAndClosingText() {
        val expanded = CodeSnippets.expand("py.print", request(), AppSettings())!!
        var session = expanded.session("workspace:file", 11, expanded.change.text.length)
        var code = expanded.change.text.replaceRange(session.active.start, session.active.end, "pri")
        session = session.edited(session.document, 11, session.active.start, 1, 3)!!.copy(revision = 12)
        val req = request(code, caret = session.active.end).copy(revision = 12)
        val batch = LexicalCompletion.complete(req, includeSnippets = false)!!
        val choice = batch.items.indexOfFirst { it.label == "print" }
        val insertion = batch.insertion(req, choice, AppSettings())!!
        assertNull(insertion.snippet)
        val change = insertion.change
        session = session.edited(session.document, 12, change.start, change.end - change.start, change.text.length)!!
        code = apply(code, change)
        assertEquals("print(print)", code)
        assertEquals("print", code.substring(session.active.start, session.active.end))
        assertEquals(code.length, session.length)
        assertNull(session.move(false).session)
        assertEquals(code.length, session.move(false).start)
    }

    @Test fun tabAndEscapeNeverInterceptCompositionSoftKeyboardOrHandledCompletion() {
        fun action(key: CompletionKey, reverse: Boolean = false, handled: Boolean = false,
            composing: Boolean = false, soft: Boolean = false, modified: Boolean = false) =
            SnippetKeys.action(key, reverse, true, handled, composing, soft, modified)
        assertEquals(SnippetKeyAction.NEXT, action(CompletionKey.TAB))
        assertEquals(SnippetKeyAction.PREVIOUS, action(CompletionKey.TAB, reverse = true))
        assertEquals(SnippetKeyAction.END, action(CompletionKey.ESC))
        assertEquals(SnippetKeyAction.PASS, action(CompletionKey.ENTER))
        assertEquals(SnippetKeyAction.PASS, action(CompletionKey.TAB, handled = true))
        assertEquals(SnippetKeyAction.PASS, action(CompletionKey.TAB, composing = true))
        assertEquals(SnippetKeyAction.PASS, action(CompletionKey.TAB, soft = true))
        assertEquals(SnippetKeyAction.PASS, action(CompletionKey.TAB, modified = true))
        assertEquals(SnippetKeyAction.PASS, SnippetKeys.action(CompletionKey.TAB, false, false, false, false, false, false))
    }

    @Test fun generatedDefaultsExposeActualModelOutputForHostCompilerSmoke() {
        val fixtures = JSONArray()
        fun add(id: String, req: CompletionRequest, settings: AppSettings, variant: String) {
            val expanded = CodeSnippets.expand(id, req, settings)!!
            val code = apply(req.code, expanded.change)
            val session = expanded.session(req.document, req.revision + 1, code.length)
            assertTrue(session.stops.zipWithNext().all { (a, b) -> a.end <= b.start })
            assertTrue(session.stops.all { it.start in 0..code.length && it.end in it.start..code.length })
            assertTrue(session.finalOffset <= code.length)
            fixtures.put(JSONObject().put("id", id).put("variant", variant).put("cpp", req.cpp).put("code", code))
        }
        for (cpp in listOf(false, true)) CodeSnippets.all(cpp).forEach { add(it.id, request(cpp = cpp), AppSettings(), "default") }
        val py = "if True:\r\n\tfo\r\n"
        add("py.for", request(py, caret = py.indexOf("fo") + 2), AppSettings(useSpaces = false, tabWidth = 8), "nested-crlf-tab")
        val cpp = "// 😀\r\n  if"
        add("cpp.if", request(cpp, cpp = true), AppSettings(tabWidth = 2), "utf16-indent")
        assertEquals(14, fixtures.length())
        println("CODEPOCKET_SNIPPET_FIXTURES=" + fixtures.toString())
    }
}
