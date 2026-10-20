package com.codepocket.ide

import com.codepocket.ide.editor.*
import org.junit.Assert.*
import org.junit.Test

class EditorDecorationTest {
    private fun scan(code: String, cpp: Boolean = false) = EditorDecorations.scan(code, cpp, EditorFeatures.tokens(code, cpp))
    private fun kind(code: String, at: Int, cpp: Boolean = false) = scan(code, cpp).tokens.firstOrNull { it.start == at }?.kind
    private fun frame(text: String = "x = 1") = EditorFrame(EditorSnapshot("workspace:main.py", 1, text, 0, 0), false, true)
    @Test fun mixedBracketsUseOneDepthStackWithMatchingPairColours() {
        val code = "([({x: [1]})])"
        val tokens = scan(code).tokens.filter { it.kind.startsWith("bracket") }
        assertEquals(10, tokens.size)
        for ((a,b) in listOf(0 to 13,1 to 12,2 to 11,3 to 10,7 to 9)) {
            assertEquals("pair $a $b", kind(code,a),kind(code,b))
        }
        assertNotEquals(kind(code,0), kind(code,1)); assertTrue(scan(code).issues.isEmpty())
    }
    @Test fun stringsCommentsAndPythonTripleQuotesAreOpaque() {
        val code = "x = '''([)]''' # }\nprint(\"{]\")"
        val result = scan(code)
        assertTrue(result.issues.isEmpty())
        assertEquals(2,result.tokens.count { it.kind.startsWith("bracket") })
    }
    @Test fun rawCppLiteralsAndContinuedMacrosDoNotInventErrors() {
        val code = "auto s = R\"tag( ([)] )tag\";\n#define X " + "\\" + "\n ([)]\nint main() {}"
        assertTrue(scan(code,true).issues.toString(),scan(code,true).issues.isEmpty())
    }
    @Test fun nestedTemplatesColourAnglesButComparisonsAndShiftsDoNot() {
        val code = "std::vector<std::pair<int, int>> data; if (x < y && z > 1) { x >>= 1; }"
        assertEquals("bracket0",kind(code,code.indexOf('<'),true))
        assertEquals("bracket1",kind(code,code.indexOf('<',code.indexOf('<')+1),true))
        assertNull(kind(code,code.indexOf("< y"),true));assertNull(kind(code,code.indexOf("> 1"),true))
        assertTrue(scan(code,true).issues.isEmpty())
    }
    @Test fun variableParameterMemberConstantAndCustomTypeHintsDiffer() {
        val code = "class Point: pass\ndef area(width, height=1):\n    LIMIT = 10\n    result = width * height + obj.value\n    return result"
        assertEquals("type", kind(code,code.indexOf("Point")))
        assertEquals("parameter",kind(code,code.indexOf("width")))
        assertEquals("parameter",kind(code,code.indexOf("height")))
        assertEquals("variable",kind(code,code.indexOf("result")))
        assertEquals("constant",kind(code,code.indexOf("LIMIT")))
        assertEquals("property",kind(code,code.indexOf("value")))
    }
    @Test fun cppTypedParametersAndOrdinaryVariablesAreDistinct() {
        val code = "int sum(int left, const int right) { int total = left + right; return total; }"
        assertEquals("parameter",kind(code,code.indexOf("left"),true))
        assertEquals("parameter",kind(code,code.indexOf("right"),true))
        assertEquals("variable",kind(code,code.indexOf("total"),true))
    }
    @Test fun malformedBracketsHaveBoundedClearlyLabelledLocalDiagnostics() {
        val result = scan("([)]" + "}".repeat(100),true)
        assertEquals(40,result.issues.size)
        assertTrue(result.issues.all{it.source=="基础括号检查"&&it.start>=0&&it.end>it.start})
    }
    @Test fun emojiBeforeBracketsDoesNotShiftUtf16Ranges() {
        val code = "# 😀\nx = ([1])"
        assertEquals("bracket0",kind(code,code.indexOf('(')))
        assertEquals("bracket1",kind(code,code.indexOf('[')))
    }
    @Test fun syntaxChecksCacheAcrossCaretAndThemeButNeverAcrossBuffersOrLanguages() {
        var count = 0
        val analyzer = EditorAnalyzer(pythonSyntax = { count++; SyntaxReport(listOf(InlineIssue(0,1,"actual parser error","test parser")),"checked") })
        val initial = frame()
        val a = analyzer.analyze(initial)
        val b = analyzer.analyze(initial.copy(buffer=initial.buffer.copy(start=2,end=2),dark=false))
        assertSame(a.syntax,b.syntax);assertSame(a.extraTokens,b.extraTokens);assertEquals(1,count)
        analyzer.analyze(initial.copy(buffer=initial.buffer.copy(revision=2)));assertEquals(2,count)
        val textFile = initial.copy(python=false)
        assertTrue(analyzer.analyze(textFile).syntax.issues.isEmpty());assertEquals(2,count)
        assertFalse(a.canApplyTo(textFile))
    }
    @Test fun spanAndTextBudgetsAlsoBoundNewDecorations() {
        val a = EditorAnalyzer().analyze(frame("x ".repeat(6001)))
        assertEquals(HighlightFallback.SPAN_LIMIT,a.fallback);assertTrue(a.extraTokens.isEmpty())
        var checked = false
        val b = EditorAnalyzer(pythonSyntax={checked=true;SyntaxReport()}).analyze(frame("x".repeat(45001)))
        assertFalse(checked);assertEquals(HighlightFallback.TEXT_LIMIT,b.fallback)
    }
    @Test fun runtimeErrorsRequireExactSourceProofAndActualCodeErrorKind() {
        val source = frame("x = 1\nprint(x / 0)")
        val result = RunResult("","ZeroDivisionError: division by zero","1",1,2,kind=RunKind.RUNTIME,
            sourceKey=source.buffer.key,sourceHash=DocumentTree.digest(source.buffer.text))
        assertEquals("print(x / 0)", InlineRunDiagnostics.resolve(source,result,true).single().let { source.buffer.text.substring(it.start,it.end) })
        for (bad in listOf(result.copy(sourceKey=null),result.copy(sourceHash=null),result.copy(sourceHash="stale"),result.copy(kind=RunKind.NETWORK),result.copy(errorLine=9)))
            assertTrue(InlineRunDiagnostics.resolve(source,bad,true).isEmpty())
        assertTrue(InlineRunDiagnostics.resolve(source,result,false).isEmpty())
        assertTrue(InlineRunDiagnostics.resolve(source.copy(composing=true),result,true).isEmpty())
        assertTrue(InlineRunDiagnostics.resolve(source.copy(buffer=source.buffer.copy(text="x = 2\nprint(x / 0)")),result,true).isEmpty())
    }
}
