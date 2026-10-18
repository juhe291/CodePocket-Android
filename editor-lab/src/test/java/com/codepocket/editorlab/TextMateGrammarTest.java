package com.codepocket.editorlab;

import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.grammar.IToken;
import org.eclipse.tm4e.core.grammar.ITokenizeLineResult;
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.EncodedTokenAttributes;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

/** Actual Sora 0.23.7 TM4E/Joni engine and exactly the APK assets. No View or grammar mocks. */
public class TextMateGrammarTest {
    private static TextMateBundle bundle;
    private static InputStream asset(String name) throws IOException {
        InputStream input=TextMateGrammarTest.class.getClassLoader().getResourceAsStream(name);
        if(input==null) throw new IOException("Missing asset: "+name);
        return input;
    }
    @BeforeClass public static void load() throws Exception { bundle=TextMateBundle.load(TextMateGrammarTest::asset); }
    @AfterClass public static void dispose() { if(bundle!=null) bundle.close(); }
    private List<String> scopes(ITokenizeLineResult<IToken[]> result,int at) {
        for(IToken token:result.getTokens()) if(at>=token.getStartIndex() && at<token.getEndIndex()) return token.getScopes();
        fail("No token at UTF-16 "+at);return List.of();
    }
    private void scope(ITokenizeLineResult<IToken[]> result,int at,String prefix) {
        List<String> found=scopes(result,at);
        assertTrue(prefix+" at "+at+": "+found,found.stream().anyMatch(s->s.startsWith(prefix)));
    }
    private ITokenizeLineResult<IToken[]> line(SyntaxLanguage language,String text) {
        return bundle.grammar(language).tokenizeLine(text,null,Duration.ofSeconds(3));
    }
    @Test public void bothSelectedScopesAndCppMacroDependencyAreActuallyLoaded() {
        assertEquals("source.python",bundle.grammar(SyntaxLanguage.PYTHON).getScopeName());
        assertEquals("source.cpp",bundle.grammar(SyntaxLanguage.CPP).getScopeName());
        assertNotNull(bundle.grammars.findGrammar("source.cpp.embedded.macro"));
        assertNotNull(bundle.grammars.findGrammar("source.c.platform"));
    }
    @Test public void pythonFunctionsNumbersStringsAndCommentsHaveDifferentScopes() {
        String text="def greet(name): return 'hello' + str(42) # 中文😀";
        var r=line(SyntaxLanguage.PYTHON,text);
        scope(r,0,"storage.type");scope(r,text.indexOf("greet"),"entity.name.function");
        scope(r,text.indexOf("return"),"keyword");scope(r,text.indexOf("hello"),"string");
        scope(r,text.indexOf("42"),"constant.numeric");scope(r,text.indexOf("中文"),"comment");
    }
    @Test public void pythonClassesAndDecoratorsAreGrammarTokensRatherThanSemanticClaims() {
        var a=line(SyntaxLanguage.PYTHON,"class Student(object):"); scope(a,6,"entity.name.type.class");
        var b=line(SyntaxLanguage.PYTHON,"@staticmethod");scope(b,1,"meta.function.decorator");scope(b,1,"support.type");
    }
    @Test public void cppTypesFunctionsNumbersStringsAndCommentsHaveDifferentScopes() {
        String text="int solve(int value) { return 42; } // 注释😀";var r=line(SyntaxLanguage.CPP,text);
        scope(r,0,"storage.type");scope(r,text.indexOf("solve"),"entity.name.function");
        scope(r,text.indexOf("return"),"keyword");scope(r,text.indexOf("42"),"constant.numeric");
        scope(r,text.indexOf("注释"),"comment");scope(line(SyntaxLanguage.CPP,"auto x = \"hello\";"),12,"string");
    }
    @Test public void cppPreprocessorIncludeAndMacroUseTheBundledGrammar() {
        String include="#include <vector>";var r=line(SyntaxLanguage.CPP,include);scope(r,1,"keyword.control.directive");
        String macro="#define ANSWER(x) ((x) + 42)";var m=line(SyntaxLanguage.CPP,macro);scope(m,1,"keyword.control.directive");
        assertFalse(m.isStoppedEarly());
    }
    @Test public void cppMultilineCommentStateResumesAndEnds() {
        IGrammar g=bundle.grammar(SyntaxLanguage.CPP);var a=g.tokenizeLine("/* start");
        var b=g.tokenizeLine("return 42",a.getRuleStack(),Duration.ofSeconds(3));scope(b,0,"comment.block");
        var c=g.tokenizeLine("*/ int n = 7;",b.getRuleStack(),Duration.ofSeconds(3));scope(c,3,"storage.type");scope(c,11,"constant.numeric");
    }
    @Test public void pythonTripleQuotedStringRetainsCrossLineState() {
        IGrammar g=bundle.grammar(SyntaxLanguage.PYTHON);var a=g.tokenizeLine("value = \"\"\"first");
        var b=g.tokenizeLine("return 42 # still string",a.getRuleStack(),Duration.ofSeconds(3));scope(b,0,"string");
        var c=g.tokenizeLine("last\"\"\"; n = 7",b.getRuleStack(),Duration.ofSeconds(3));scope(c,13,"constant.numeric");
    }
    @Test public void supplementaryUnicodeDoesNotShiftNumericOrCommentTokenOffsets() {
        String py="name = '😀中文'; count = 42 # end";var a=line(SyntaxLanguage.PYTHON,py);
        scope(a,py.indexOf("42"),"constant.numeric");scope(a,py.indexOf("end"),"comment");
        String cpp="auto s = u8\"😀中文\"; int n = 42; // end";var b=line(SyntaxLanguage.CPP,cpp);
        scope(b,cpp.indexOf("42"),"constant.numeric");scope(b,cpp.indexOf("end"),"comment");
    }
    @Test public void freshTokenizationDoesNotInheritAnotherDocumentsOpenString() {
        IGrammar g=bundle.grammar(SyntaxLanguage.PYTHON);g.tokenizeLine("\"\"\"open string");
        scope(g.tokenizeLine("return 42",null,Duration.ofSeconds(3)),0,"keyword");
    }
    @Test public void actualLanguageConfigurationsContainBracketsAndIndentation() {
        var py=bundle.grammars.findLanguageConfiguration("source.python");assertNotNull(py);
        assertNotNull(py.getBrackets());assertFalse(py.getBrackets().isEmpty());assertFalse(py.getOnEnterRules().isEmpty());
        var cpp=bundle.grammars.findLanguageConfiguration("source.cpp");assertNotNull(cpp);
        assertFalse(cpp.getBrackets().isEmpty());assertNotNull(cpp.getAutoClosingPairs());
    }
    private String color(SyntaxLanguage language,String text,int at) {
        int[] tokens=bundle.grammar(language).tokenizeLine2(text).getTokens();int metadata=0;
        for(int i=0;i<tokens.length;i+=2) { if(tokens[i]>at)break;metadata=tokens[i+1]; }
        return bundle.themes.getCurrentThemeModel().getTheme().getColorMap().get(EncodedTokenAttributes.getForeground(metadata));
    }
    @Test public void bothThemesActuallyMapBinaryTokensToDistinctNonTransparentColors() throws Exception {
        for(boolean dark:new boolean[]{true,false}) {
            bundle.theme(dark);String text="return 'hello' + 42 # note";Set<String> colors=new HashSet<>();
            for(String word:new String[]{"return","hello","42","note"}) {
                String color=color(SyntaxLanguage.PYTHON,text,text.indexOf(word));assertTrue(color,color.matches("#[0-9A-Fa-f]{6}"));colors.add(color);
            }
            assertEquals(4,colors.size()); assertEquals(dark,bundle.themes.getCurrentThemeModel().isDark());
        }
        bundle.theme(true);
    }
    @Test public void emptyAndMalformedIncompleteLinesStillReturnCoveringTokens() {
        for(SyntaxLanguage language:new SyntaxLanguage[]{SyntaxLanguage.PYTHON,SyntaxLanguage.CPP})
            for(String text:new String[]{"","\"unfinished","a😀 = (", "\\", "#", "/*", "\ud800"}) {
                var r=line(language,text);assertFalse(r.isStoppedEarly());assertTrue(r.getTokens().length>0);
            }
    }
    @Test public void denseInputUsesTheRealGrammarWithoutLosingEndTokens() {
        String text="value = ["+"42, ".repeat(1000)+"7] # final";var r=line(SyntaxLanguage.PYTHON,text);
        assertFalse(r.isStoppedEarly());scope(r,text.indexOf("final"),"comment");
    }
    @Test public void languageBudgetDowngradesWithoutTouchingTheDocument() {
        assertEquals(SyntaxLanguage.CPP,SyntaxLanguage.CPP.effective(45000));
        assertEquals(SyntaxLanguage.PLAIN,SyntaxLanguage.CPP.effective(45001));
        assertEquals(SyntaxLanguage.PLAIN,SyntaxLanguage.PLAIN.effective(0));
        assertEquals(SyntaxLanguage.PYTHON,SyntaxLanguage.PYTHON.effective(0));
    }
    @Test public void loaderClosesEveryStreamAndDetachesItsTemporaryResolver() throws Exception {
        List<boolean[]> streams=new ArrayList<>();
        try(TextMateBundle extra=TextMateBundle.load(path->{
            boolean[] closed={false};streams.add(closed);
            return new FilterInputStream(asset(path)) { @Override public void close() throws IOException {closed[0]=true;super.close();} };
        })) { assertNotNull(extra.grammar(SyntaxLanguage.CPP)); }
        assertTrue(streams.size()>=8);for(boolean[] stream:streams)assertTrue(stream[0]);
        assertNull(io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry.getInstance().tryGetInputStream("textmate/python-configuration.json"));
    }
    @Test public void missingAssetFailureDoesNotPoisonAFollowingLoad() throws Exception {
        try { TextMateBundle.load(path->{throw new IOException("fixture missing");}); fail("must fail"); }
        catch(IOException expected) { assertTrue(expected.getMessage().contains("fixture")); }
        try(TextMateBundle extra=TextMateBundle.load(TextMateGrammarTest::asset)) { assertNotNull(extra.grammar(SyntaxLanguage.PYTHON)); }
    }

    @Test public void actualOnEnterRulesIndentPythonAndCppBraces() {
        var py=bundle.grammars.findLanguageConfiguration("source.python");
        var support=new org.eclipse.tm4e.languageconfiguration.internal.supports.OnEnterSupport(py.getBrackets(),py.getOnEnterRules());
        var action=support.onEnter("", "def solve(value):", ""); assertNotNull(action);
        assertEquals(org.eclipse.tm4e.languageconfiguration.internal.model.EnterAction.IndentAction.Indent,action.indentAction);
        var cpp=bundle.grammars.findLanguageConfiguration("source.cpp");
        support=new org.eclipse.tm4e.languageconfiguration.internal.supports.OnEnterSupport(cpp.getBrackets(),cpp.getOnEnterRules());
        action=support.onEnter("", "int main() {", "}"); assertNotNull(action);
        assertEquals(org.eclipse.tm4e.languageconfiguration.internal.model.EnterAction.IndentAction.IndentOutdent,action.indentAction);
    }
    @Test public void ordinaryCppRawStringsRetainStringScope() {
        String text="auto s = R\"tag(hello \"world\")tag\";";
        scope(line(SyntaxLanguage.CPP,text),text.indexOf("hello"),"string");
    }
    @Test public void coveredFixturesDoNotSilentlyFallBackAfterRegexCompilationErrors() {
        assertTrue(android.util.Log.entries.toString(),android.util.Log.entries.stream().noneMatch(line->line.startsWith("E/")));
    }
}
