package com.codepocket.ide

import com.codepocket.ide.editor.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EditorAnalysisTest {
    private fun frame(text: String = "print(42)", key: String = "workspace:file", revision: Long = 1,
        start: Int = 6, end: Int = start, cpp: Boolean = false, dark: Boolean = true, composing: Boolean = false) =
        EditorFrame(EditorSnapshot(key, revision, text, start, end), cpp, dark, composing)

    @Test fun aResultMustMatchTheActualDocumentAndRevision() {
        val source = frame()
        val result = EditorAnalyzer().analyze(source)
        assertTrue(result.canApplyTo(source))
        assertFalse(result.canApplyTo(frame(key = "workspace:other-file")))
        assertFalse(result.canApplyTo(frame(key = "other-workspace:file")))
        assertFalse(result.canApplyTo(frame(revision = 2)))
        assertFalse(result.canApplyTo(frame(text = "print(24)")))
    }

    @Test fun caretSelectionLanguageThemeAndCompositionInvalidateDecorations() {
        val result = EditorAnalyzer().analyze(frame())
        assertFalse(result.canApplyTo(frame(start = 7)))
        assertFalse(result.canApplyTo(frame(end = 8)))
        assertFalse(result.canApplyTo(frame(cpp = true)))
        assertFalse(result.canApplyTo(frame(dark = false)))
        assertFalse(result.canApplyTo(frame(composing = true)))
        assertFalse(EditorAnalyzer().analyze(frame(composing = true)).canApplyTo(frame()))
    }

    @Test fun tokensAndBracketPositionsUseUtf16Offsets() {
        val code = "# 😀\nprint(42)"
        val result = EditorAnalyzer().analyze(frame(code, start = code.indexOf('(') + 1))
        val number = result.tokens.single { it.kind == "number" }
        assertEquals("42", code.substring(number.start, number.end))
        assertEquals(code.indexOf('(') to code.indexOf(')'), result.brackets)
    }

    @Test fun bracketsIgnoreStringsAndCommentsAndSelectedText() {
        val code = "(\"ignored)\" # )\n)"
        assertEquals(0 to code.lastIndexOf(')'), EditorAnalyzer().analyze(frame(code, start = 1)).brackets)
        assertNull(EditorAnalyzer().analyze(frame(code, start = 0, end = 1)).brackets)
        assertNull(EditorAnalyzer().analyze(frame("([)]", start = 1)).brackets)
    }

    @Test fun caretAndThemeReuseTokensButTextAndLanguageDoNot() {
        var scans = 0
        val analyzer = EditorAnalyzer { text, cpp -> scans++; EditorFeatures.tokens(text, cpp) }
        val initial = analyzer.analyze(frame())
        val caret = analyzer.analyze(frame(start = 7))
        val theme = analyzer.analyze(frame(start = 7, dark = false))
        assertSame(initial.tokens, caret.tokens); assertSame(caret.tokens, theme.tokens)
        assertEquals(1, scans)
        analyzer.analyze(frame(text = "print(24)"))
        analyzer.analyze(frame(text = "print(24)", cpp = true))
        analyzer.analyze(frame(text = "print(24)", cpp = true, revision = 2))
        assertEquals(4, scans)
    }

    @Test fun switchingToASameContentDocumentStillInvalidatesTheCache() {
        var scans = 0
        val analyzer = EditorAnalyzer { text, cpp -> scans++; EditorFeatures.tokens(text, cpp) }
        analyzer.analyze(frame()); analyzer.analyze(frame(key = "workspace:other"))
        assertEquals(2, scans)
    }

    @Test fun largeDocumentsFallBackWithoutCallingTheLexer() {
        var scans = 0
        val analyzer = EditorAnalyzer { text, cpp -> scans++; EditorFeatures.tokens(text, cpp) }
        val code = "x".repeat(EditorAnalyzer.MAX_HIGHLIGHT_CHARS + 1)
        val result = analyzer.analyze(frame(code))
        assertEquals(HighlightFallback.TEXT_LIMIT, result.fallback)
        assertTrue(result.tokens.isEmpty()); assertNull(result.brackets); assertEquals(0, scans)
        assertEquals(HighlightFallback.NONE, analyzer.analyze(frame()).fallback)
    }

    @Test fun denseShortCodeBoundsUiSpansButStillFindsBrackets() {
        val code = "(" + "0 ".repeat(EditorAnalyzer.MAX_HIGHLIGHT_SPANS + 1) + ")"
        val result = EditorAnalyzer().analyze(frame(code, start = 1))
        assertEquals(HighlightFallback.SPAN_LIMIT, result.fallback)
        assertTrue(result.tokens.isEmpty())
        assertEquals(0 to code.lastIndex, result.brackets)
    }

    @Test fun compositionEndIsDetectedWithoutChangingAnyTextOrCaret() {
        val transition = CompositionTransition()
        assertFalse(transition.observe(false))
        assertFalse(transition.observe(true)); assertFalse(transition.observe(true))
        assertTrue(transition.observe(false)); assertFalse(transition.observe(false))
        assertFalse(transition.observe(true)); assertTrue(transition.observe(false))
    }

    private class ManualWorker : AbstractExecutorService() {
        val jobs = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { check(!stopped); jobs.addLast(command) }
        fun drain() { while (jobs.isNotEmpty()) jobs.removeFirst().run() }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { shutdown(); return jobs.toMutableList().also { jobs.clear() } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && jobs.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }
    private class FakePort(var current: EditorFrame?) : EditorPort {
        val applied = mutableListOf<EditorAnalysis>()
        override fun snapshot() = current
        override fun applyDecorations(analysis: EditorAnalysis): Boolean {
            if (current?.let(analysis::canApplyTo) != true) return false
            applied.add(analysis); return true
        }
    }

    @Test fun lexicalAnalysisActuallyExecutesAwayFromTheRequestThread() {
        val requestingThread = Thread.currentThread()
        val ran = CountDownLatch(1)
        var analysisThread: Thread? = null
        val worker = Executors.newSingleThreadExecutor()
        val callbacks = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()
        val port = FakePort(frame())
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker) {
            analysisThread = Thread.currentThread(); ran.countDown(); EditorAnalyzer().analyze(it)
        }
        try {
            controller.request()
            assertTrue(ran.await(3, TimeUnit.SECONDS))
            assertNotSame(requestingThread, analysisThread)
            assertTrue(port.applied.isEmpty())
            val apply = callbacks.poll(3, TimeUnit.SECONDS)
            assertNotNull(apply); apply!!.invoke()
            assertEquals(1, port.applied.size)
        } finally { controller.close() }
    }

    @Test fun queuedUiResultsCannotApplyAfterAYoungerRequest() {
        val worker = ManualWorker(); val callbacks = ArrayDeque<() -> Unit>(); val port = FakePort(frame())
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker)
        controller.request(); worker.drain() // first result is already in the UI queue
        port.current = frame(text = "print(24)", revision = 2)
        controller.request(); worker.drain()
        callbacks.removeFirst().invoke(); assertTrue(port.applied.isEmpty())
        callbacks.removeFirst().invoke(); assertEquals("print(24)", port.applied.single().source.buffer.text)
        controller.close()
    }

    @Test fun changedDocumentIsRejectedEvenWithoutANewRequest() {
        val worker = ManualWorker(); val callbacks = ArrayDeque<() -> Unit>(); val port = FakePort(frame())
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker)
        controller.request(); worker.drain(); port.current = frame(key = "workspace:other")
        callbacks.removeFirst().invoke(); assertTrue(port.applied.isEmpty()); controller.close()
    }

    @Test fun lateResultDuringCompositionIsRejected() {
        val worker = ManualWorker(); val callbacks = ArrayDeque<() -> Unit>(); val port = FakePort(frame())
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker)
        controller.request(); worker.drain(); port.current = frame(composing = true)
        callbacks.removeFirst().invoke(); assertTrue(port.applied.isEmpty()); controller.close()
    }

    @Test fun pauseCancellationInvalidatesAnAlreadyComputedResult() {
        val worker = ManualWorker(); val callbacks = ArrayDeque<() -> Unit>(); val port = FakePort(frame())
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker)
        controller.request(); worker.drain(); controller.cancel()
        callbacks.removeFirst().invoke(); assertTrue(port.applied.isEmpty())
        controller.request(); worker.drain(); callbacks.removeFirst().invoke()
        assertEquals(1, port.applied.size); controller.close()
    }

    @Test fun closeDropsQueuedCallbacksAndPreventsNewWork() {
        val worker = ManualWorker(); val callbacks = ArrayDeque<() -> Unit>(); val port = FakePort(frame())
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker)
        controller.request(); worker.drain(); controller.close(); callbacks.removeFirst().invoke()
        controller.request()
        assertTrue(worker.isShutdown); assertTrue(worker.jobs.isEmpty()); assertTrue(port.applied.isEmpty())
    }

    @Test fun composingAndMissingDocumentsDoNotStartWorkAndCancelOlderResults() {
        val worker = ManualWorker(); val callbacks = ArrayDeque<() -> Unit>(); val port = FakePort(frame())
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker)
        controller.request(); worker.drain(); port.current = frame(composing = true); controller.request()
        callbacks.removeFirst().invoke(); assertTrue(port.applied.isEmpty()); assertTrue(worker.jobs.isEmpty())
        port.current = null; controller.request(); assertTrue(worker.jobs.isEmpty()); controller.close()
    }

    @Test fun recoverableAnalyzerFailureUsesPlainTextWithoutChangingTheBuffer() {
        val worker = ManualWorker(); val callbacks = ArrayDeque<() -> Unit>(); val port = FakePort(frame())
        val original = port.current
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker) { throw IllegalStateException("lexer failed") }
        controller.request(); worker.drain(); callbacks.removeFirst().invoke()
        assertEquals(HighlightFallback.ANALYSIS_FAILED, port.applied.single().fallback)
        assertTrue(port.applied.single().tokens.isEmpty()); assertEquals(original, port.current); controller.close()
    }

    @Test fun cancelledQueuedRequestsDoNotRunTheLexer() {
        val worker = ManualWorker(); val callbacks = ArrayDeque<() -> Unit>(); val port = FakePort(frame())
        var scans = 0
        val controller = EditorAnalysisController(port, { callbacks.add(it) }, worker) { scans++; EditorAnalyzer().analyze(it) }
        repeat(100) { controller.request() }; worker.drain()
        assertEquals(1, scans); assertEquals(1, callbacks.size)
        callbacks.removeFirst().invoke(); assertEquals(1, port.applied.size); controller.close()
    }

    @Test fun longEscapedAndUnterminatedStringsKeepTheirBoundaries() {
        for (cpp in listOf(false, true)) {
            for (quote in listOf("\"", "'")) {
                val literal = quote + "\\n".repeat(21000)
                val code = literal + "\nprint(7)"
                val tokens = EditorFeatures.tokens(code, cpp)
                assertEquals(EditorFeatures.Token(0, literal.length, "string"), tokens.first())
                assertEquals("7", tokens.single { it.kind == "number" }.let { code.substring(it.start, it.end) })
                val closed = literal + quote + " + 9"
                assertEquals(EditorFeatures.Token(0, literal.length + 1, "string"), EditorFeatures.tokens(closed, cpp).first())
            }
        }
    }

    @Test fun longQuotedLiteralDoesNotOverflowTheLexerStack() {
        for (cpp in listOf(false, true)) {
            val code = "\"" + "a".repeat(44000) + "\""
            val result = EditorAnalyzer().analyze(frame(code, cpp = cpp, start = 0))
            assertEquals(1, result.tokens.size)
            assertEquals(EditorFeatures.Token(0, code.length, "string"), result.tokens.single())
        }
    }
}
