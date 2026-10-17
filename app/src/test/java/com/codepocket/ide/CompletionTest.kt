package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class CompletionTest {
    private fun request(code: String, caret: Int = code.length, cpp: Boolean = false) =
        CompletionRequest("workspace/file.py", 7, code, caret, caret, cpp)

    @Test fun acceptingInMiddleOfWordReplacesWholeTokenWithUtf16Offsets() {
        val request = request("😀\r\nprint", 7)
        val batch = LexicalCompletion.complete(request)!!
        val index = batch.items.indexOfFirst { it.label == "print" }
        assertTrue(index >= 0)
        assertEquals(TextChange(4, 9, "print", 9), batch.edit(request, index))
        assertTrue(batch.items[index].detail.contains("Python 3.13 内置参考"))
    }

    @Test fun acceptingChosenCandidateDoesNotSilentlyUseFirstItem() {
        val req = request("pri", cpp = true)
        val batch = LexicalCompletion.complete(req)!!
        assertTrue(batch.items.size > 1)
        val second = batch.items[1]
        assertNotEquals(batch.items[0].label, second.label)
        assertEquals(second.label, batch.edit(req, 1)!!.text)
        assertNull(batch.edit(req, batch.items.size))
    }

    @Test fun candidatesAreNeverAppliedToChangedDocumentOrSelection() {
        val request = request("pr")
        val batch = LexicalCompletion.complete(request)!!
        val index = batch.items.indexOfFirst { it.label == "print" }
        assertNull(batch.edit(request.copy(document = "other.py"), index))
        assertNull(batch.edit(request.copy(revision = 8), index))
        assertNull(batch.edit(request.copy(code = "px"), index))
        assertNull(batch.edit(request.copy(caret = 1), index))
        assertNull(batch.edit(request.copy(end = 0), index))
        assertNull(batch.edit(request, -1))
        assertNull(batch.edit(request, index, maxChars = 4))
        assertEquals(TextChange(0, 2, "print", 5), batch.edit(request, index))
    }

    @Test fun lexicalResultsDoNotPretendToResolveMembersOrLiteralContents() {
        for (code in listOf("thing.pr", "thing. pr", "obj->pr", "std::pr")) {
            val req = request(code, cpp = true)
            assertTrue(LexicalCompletion.memberContext(req)); assertNull(LexicalCompletion.complete(req))
        }
        assertNull(LexicalCompletion.complete(request("# pr")))
        assertNull(LexicalCompletion.complete(request("\"pr")))
        assertNull(LexicalCompletion.complete(request("// pr", cpp = true)))
        assertNull(LexicalCompletion.complete(request("12pr")))
        assertNull(LexicalCompletion.complete(request("pr").copy(end = 0)))
        val batch = LexicalCompletion.complete(request("# secret_name\nsecret_value = 3\nsec"))!!
        assertEquals(listOf("secret_value"), batch.items.map { it.label })
        assertEquals(CompletionKind.IDENTIFIER, batch.items.single().kind)
    }

    @Test fun languagesRemainSeparateAndLargeFilesKeepOnlyBuiltinFallback() {
        assertTrue(LexicalCompletion.complete(request("pri"))!!.items.any { it.label == "print" })
        assertFalse(LexicalCompletion.complete(request("pri", cpp = true))!!.items.any { it.label == "print" })
        assertNull(LexicalCompletion.complete(request("")))
        val code = "hidden_name\n" + " ".repeat(45000) + "hid"
        assertNull(LexicalCompletion.complete(request(code)))
        val builtins = LexicalCompletion.complete(request(" ".repeat(45001) + "pri"))!!
        assertTrue(builtins.items.any { it.label == "print" })
    }

    @Test fun keyboardDefaultsKeepNewlineAndAutomaticArrowMovementNative() {
        fun action(key: CompletionKey, settings: AppSettings = AppSettings(), explicit: Boolean = false,
            selected: Boolean = false, composing: Boolean = false, selection: Boolean = false, modified: Boolean = false) =
            CompletionKeys.action(key, settings, true, explicit, selected, composing, selection, modified)
        assertEquals(CompletionAction.ACCEPT, action(CompletionKey.TAB))
        assertEquals(CompletionAction.PASS, action(CompletionKey.ENTER))
        assertEquals(CompletionAction.PASS, action(CompletionKey.DOWN))
        assertEquals(CompletionAction.NEXT, action(CompletionKey.DOWN, explicit = true))
        assertEquals(CompletionAction.PREVIOUS, action(CompletionKey.UP, AppSettings(completionArrows = CompletionArrows.WHEN_VISIBLE)))
        assertEquals(CompletionAction.PASS, action(CompletionKey.UP, AppSettings(completionArrows = CompletionArrows.OFF), explicit = true))
        assertEquals(CompletionAction.PASS, action(CompletionKey.TAB, AppSettings(completionTab = false)))
        assertEquals(CompletionAction.PASS, action(CompletionKey.ENTER, AppSettings(completionEnter = true)))
        assertEquals(CompletionAction.ACCEPT, action(CompletionKey.ENTER, AppSettings(completionEnter = true), selected = true))
        assertEquals(CompletionAction.PASS, action(CompletionKey.TAB, composing = true))
        assertEquals(CompletionAction.PASS, action(CompletionKey.TAB, selection = true))
        assertEquals(CompletionAction.PASS, action(CompletionKey.TAB, modified = true))
        assertEquals(CompletionAction.DISMISS, action(CompletionKey.ESC))
        assertEquals(CompletionAction.PASS, CompletionKeys.action(CompletionKey.ENTER,
            AppSettings(completionEnter = true), true, true, true, false, false, false, softKeyboard = true))
        assertEquals(CompletionAction.PASS, CompletionKeys.action(CompletionKey.TAB, AppSettings(), false, true, true, false, false, false))
    }

    @Test fun placementFitsVisibleWindowAndFlipsAboveKeyboard() {
        val bounds = CompletionBounds(100, 60, 500, 600)
        assertEquals(CompletionPlacement(120, 123, 380, 240), CompletionGeometry.place(bounds, 490, 100, 120, 380, 240, 76, 3))
        val above = CompletionGeometry.place(bounds, 150, 560, 580, 380, 240, 76, 3)!!
        assertEquals(317, above.y); assertTrue(above.y + above.height < 560)
        val narrow = CompletionGeometry.place(CompletionBounds(20, 20, 180, 300), 100, 140, 160, 380, 280, 76, 3)!!
        assertEquals(160, narrow.width); assertEquals(137, narrow.height)
        assertNull(CompletionGeometry.place(CompletionBounds(0, 0, 200, 100), 30, 40, 60, 380, 240, 76, 3))
        assertNull(CompletionGeometry.place(bounds, 90, 100, 120, 380, 240, 76, 3))
        assertNull(CompletionGeometry.place(bounds, 120, 50, 80, 380, 240, 76, 3))
    }

    @Test fun completionPreferencesMigrateAndRoundTripWithoutChangingDefaults() {
        val old = AppSettings.fromJson("""{"version":1,"fontSize":18}""")
        assertTrue(old.completionTab); assertFalse(old.completionEnter); assertEquals(CompletionArrows.EXPLICIT, old.completionArrows)
        val configured = old.copy(completionTab = false, completionEnter = true, completionArrows = CompletionArrows.OFF)
        assertEquals(configured, AppSettings.fromJson(configured.toJson()))
        assertEquals(CompletionArrows.EXPLICIT, AppSettings.fromJson("""{"completionArrows":"future"}""").completionArrows)
    }
}
