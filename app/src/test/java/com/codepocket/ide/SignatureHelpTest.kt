package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class SignatureHelpTest {
    private fun request(marked: String, cpp: Boolean = false): CompletionRequest {
        val caret = marked.indexOf('¦')
        require(caret >= 0)
        return CompletionRequest("project/file", 12, marked.replace("¦", ""), caret, caret, cpp)
    }
    private fun match(marked: String, cpp: Boolean = false) = SignatureHelp.find(request(marked, cpp))!!

    @Test fun referenceKeepsLanguagesSourcesAndSimplifiedFormsExplicit() {
        assertEquals(7, BuiltinSignatures.all.count { !it.cpp })
        assertEquals(5, BuiltinSignatures.all.count { it.cpp })
        assertNull(BuiltinSignatures.find("print", true)); assertNull(BuiltinSignatures.find("sort", true))
        assertTrue(BuiltinSignatures.all.all { it.source.startsWith("https://") && it.forms.isNotEmpty() })
        assertTrue(BuiltinSignatures.find("std::sort", true)!!.source.contains("n4659"))
        assertEquals(2, BuiltinSignatures.find("std::getline", true)!!.forms.size)
    }

    @Test fun positionalAndVariadicParametersAreDifferent() {
        val a = match("len(¦)")
        assertEquals("len", a.reference.name); assertEquals(0, a.argument); assertEquals(0, a.active(0))
        val b = match("print(1, 2, ¦)")
        assertEquals(2, b.argument); assertEquals(0, b.active(0))
        assertNull(match("len(1, ¦)").active(0))
    }

    @Test fun keywordsAndMarkersNeverBecomeFakePositionalParameters() {
        assertEquals(1, match("print(1, sep=¦)").active(0))
        assertEquals(4, match("print(1, flush=¦)").active(0))
        assertEquals(3, match("sorted(xs, key=¦)").active(0))
        assertEquals(4, match("sorted(xs, reverse=¦)").active(0))
        assertNull(match("sorted(xs, ¦)").active(0))
        assertNull(match("len(object=¦)").active(0))
        assertNull(match("print(unknown=¦)").active(0))
        assertEquals(2, match("sum(xs, start=¦)").active(0))
    }

    @Test fun referenceFormsAreNotResolvedAsTypesOrOverloads() {
        val value = match("range(1, ¦)")
        assertNull(value.active(0)); assertEquals(1, value.active(1))
        val cpp = match("std::sort(begin, end, ¦)", true)
        assertNull(cpp.active(0)); assertEquals(2, cpp.active(1))
        assertEquals("std::sort(first, last, comp)", cpp.reference.forms[1].label(cpp.reference.name))
        assertNull(cpp.active(99))
    }

    @Test fun innerKnownCallWinsAndClosingItRestoresOuterCall() {
        assertEquals("len", match("print(len(¦), 2)").reference.name)
        val after = match("print(len(xs), ¦)")
        assertEquals("print", after.reference.name); assertEquals(1, after.argument)
        assertNull(SignatureHelp.find(request("print(custom(¦))")))
        assertNull(SignatureHelp.find(request("print(factory()(¦))")))
        assertEquals("print", match("print(custom(1, 2), ¦)").reference.name)
    }

    @Test fun bracketsStringsAndCommentsDoNotAddOuterArguments() {
        assertEquals(1, match("print([1, 2], ¦)").argument)
        assertEquals(1, match("print({'a': (1, 2)}, ¦)").argument)
        assertEquals(1, match("print('a,b()', ¦)").argument)
        assertEquals(1, match("print(\"a\\\",b\", ¦)").argument)
        assertEquals(1, match("print(1, # ignored , (\nsep=¦)").active(0))
        assertEquals(1, match("std::sort(a /* , ) */, ¦)", true).argument)
        assertEquals(1, match("std::sort({1, 2}, ¦)", true).argument)
    }

    @Test fun caretInsideLiteralOrCommentDeclinesIncludingUnclosedEnds() {
        for (source in listOf("print('abc¦')", "print('abc¦", "print(1, # abc¦", "print('''x\n¦''')"))
            assertNull(source, SignatureHelp.find(request(source)))
        assertNull(SignatureHelp.find(request("std::sort(a, /* abc¦ */ b)", true)))
        assertNull(SignatureHelp.find(request("#define X std::sort(¦)", true)))
        assertEquals("print", match("print('x'¦)").reference.name)
    }

    @Test fun memberCallsAndDeclarationsDoNotClaimBuiltinSemantics() {
        for (source in listOf("obj.print(¦)", "obj . print(¦)", "def print(¦):", "class print(¦):", "blueprint(¦)"))
            assertNull(source, SignatureHelp.find(request(source)))
        assertNull(SignatureHelp.find(request("obj->std::sort(¦)", true)))
        assertNull(SignatureHelp.find(request("sort(¦)", true)))
        assertNull(SignatureHelp.find(request("print(¦)", true)))
        assertNull(SignatureHelp.find(request("std::sort(¦)")))
    }

    @Test fun unsupportedGrammarDeclinesInsteadOfCountingTheWrongComma() {
        for (source in listOf("sorted(xs, key=lambda a,b: ¦)", "sum(x for x,y in ¦)", "print(*xs, ¦)", "print(f'{x}', ¦)"))
            assertNull(source, SignatureHelp.find(request(source)))
        assertNull(SignatureHelp.find(request("std::sort(a, less<int, int>(), ¦)", true)))
        assertNull(SignatureHelp.find(request("std::sort(R\"tag(a,b)tag\", ¦)", true)))
    }

    @Test fun incompleteCallsWorkButSelectionsLimitsAndMismatchesDecline() {
        assertEquals("input", match("input(¦").reference.name)
        assertNull(SignatureHelp.find(request("print(1)¦")))
        assertNull(SignatureHelp.find(request("print([1), ¦")))
        val a = request("len(¦)")
        assertNull(SignatureHelp.find(a.copy(end = 0)))
        assertNull(SignatureHelp.find(a.copy(caret = -1, end = -1)))
        assertNull(SignatureHelp.find(a.copy(caret = 99, end = 99)))
        assertNull(SignatureHelp.find(request(" ".repeat(45000) + "print(¦)")))
        assertNull(SignatureHelp.find(request("print(" + "(".repeat(130) + "¦")))
    }

    @Test fun utf16CrLfAndMultipleLinesKeepExactOpenOffset() {
        val source = "# 猫🐱\r\nprint(1,\r\nend=¦)"
        val actual = match(source)
        assertEquals(source.indexOf('('), actual.openOffset)
        assertEquals(2, actual.active(0)); assertEquals("end", actual.keyword)
        assertEquals(1, actual.argument)
    }

    @Test fun resultsAreBoundToTheExactBufferRevisionAndSelection() {
        val value = match("len(¦)")
        val a = value.request
        assertTrue(value.matches(a))
        for (changed in listOf(a.copy(document = "other"), a.copy(revision = 13), a.copy(code = "len(x)"),
            a.copy(caret = 0), a.copy(end = 0), a.copy(cpp = true))) assertFalse(value.matches(changed))
    }

    @Test fun activeRangesCoverTheDisplayedParameterOnly() {
        for (reference in BuiltinSignatures.all) for (form in reference.forms) {
            val label = form.label(reference.name)
            assertNull(form.range(reference.name, null)); assertNull(form.range(reference.name, -1))
            for (i in form.parts.indices) {
                val range = form.range(reference.name, i)!!
                assertEquals(form.parts[i], label.substring(range.first, range.second))
            }
        }
    }

    @Test fun signatureSettingUpgradesDefaultsAndRoundTripsIndependently() {
        assertTrue(AppSettings.fromJson("""{"version":2,"autoCompletion":false}""").signatureHelp)
        val disabled = AppSettings(signatureHelp = false)
        assertEquals(disabled, AppSettings.fromJson(disabled.toJson()))
        assertTrue(disabled.autoCompletion)
    }

    @Test fun cancelledRequestsStopAndManyCompletedCallsDoNotLeakOldContext() {
        val code = "len(1)\n".repeat(5000) + "print(1, end=¦)"
        assertEquals(2, match(code).active(0))
        Thread.currentThread().interrupt()
        try { assertNull(SignatureHelp.find(request("len(¦)"))) }
        finally { Thread.interrupted() }
    }

    @Test fun escapeDoesNotStealSoftKeyboardOrCompositionOrModifiedEvents() {
        assertTrue(SignatureKeys.dismiss(true, true, false, false, false))
        assertFalse(SignatureKeys.dismiss(true, true, true, false, false))
        assertFalse(SignatureKeys.dismiss(true, true, false, true, false))
        assertFalse(SignatureKeys.dismiss(true, true, false, false, true))
        assertFalse(SignatureKeys.dismiss(false, true, false, false, false))
        assertFalse(SignatureKeys.dismiss(true, false, false, false, false))
    }
}
