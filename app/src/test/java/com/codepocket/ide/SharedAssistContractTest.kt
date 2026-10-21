package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Test

class SharedAssistContractTest {
    @Test fun appOptionsAndStandaloneOptionsProduceTheSameCandidatesAndEdits() {
        for (cpp in listOf(false, true)) {
            val code = if (cpp) "fo" else "pr"
            val request = CompletionRequest("same", 4, code, code.length, code.length, cpp)
            val batch = LexicalCompletion.complete(request)!!
            for (i in batch.items.indices) assertEquals(batch.insertion(request, i, AppSettings()), batch.insertion(request, i, AssistOptions()))
        }
    }
    @Test fun actualTemplatesKeepCustomIndentationCrlfAndUnicodeAcrossTheSharedBoundary() {
        for (cpp in listOf(false, true)) for (width in listOf(-1, 2, 4, 8, 99)) for (spaces in listOf(false, true)) {
            val request = CompletionRequest("文档😀", 3, "# 😀\r\n  fo", 11, 11, cpp).let { it.copy(caret = it.code.length, end = it.code.length) }
            for (template in CodeSnippets.all(cpp)) assertEquals(
                CodeSnippets.expand(template.id, request, AppSettings(tabWidth = width, useSpaces = spaces)),
                CodeSnippets.expand(template.id, request, AssistOptions(tabWidth = width, useSpaces = spaces)))
        }
    }
    @Test fun defaultStandalonePolicyStillNavigatesVisibleCandidatesAndPreservesEnter() {
        fun action(key: CompletionKey, options: AssistOptionsContract) = CompletionKeys.action(key, options, true, false, false, false, false, false)
        for (key in CompletionKey.values()) assertEquals(action(key, AppSettings()), action(key, AssistOptions()))
        assertEquals(CompletionAction.NEXT, action(CompletionKey.DOWN, AssistOptions()))
        assertEquals(CompletionAction.PASS, action(CompletionKey.ENTER, AssistOptions()))
    }
    @Test fun appPersistenceRemainsVersionNineAndKeepsNonEditorPreferences() {
        val settings = AppSettings(terminalTheme = TerminalTheme.NORD, theme = UiTheme.LIGHT, autoSave = false, tabWidth = 8)
        assertEquals(9, org.json.JSONObject(settings.toJson()).getInt("version"))
        assertEquals(settings, AppSettings.fromJson(settings.toJson()))
    }
}
