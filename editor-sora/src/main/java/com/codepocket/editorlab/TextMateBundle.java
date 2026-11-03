package com.codepocket.editorlab;

import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Loads the actual pinned Sora/TM4E engine and offline assets. Also used by JVM grammar tests. */
final class TextMateBundle implements AutoCloseable {
    @FunctionalInterface interface Assets { InputStream open(String path) throws IOException; }
    final GrammarRegistry grammars = new GrammarRegistry(null);
    final ThemeRegistry themes = new ThemeRegistry();
    private ThemeModel dark, light;
    private boolean closed;
    static TextMateBundle load(Assets assets) throws Exception {
        TextMateBundle result = new TextMateBundle();
        try {
            // The pinned registry resolves language configuration through this global provider.
            // Install only during loading, remove in finally, retain no Activity/AssetManager globally.
            List<InputStream> opened = new ArrayList<>();
            FileResolver resolver = path -> {
                if (!path.equals("textmate/python-configuration.json") && !path.equals("textmate/cpp-configuration.json")) return null;
                try { InputStream input = assets.open(path); opened.add(input); return input; }
                catch (IOException error) { throw new IllegalStateException(error); }
            };
            synchronized (FileProviderRegistry.class) {
                FileProviderRegistry files = FileProviderRegistry.getInstance(); files.addFileProvider(resolver);
                try {
                    result.loadGrammar(assets, "python", "source.python", "python-configuration.json");
                    result.loadGrammar(assets, "cpp-macro", "source.cpp.embedded.macro", null);
                    result.loadGrammar(assets, "platform", "source.c.platform", null);
                    result.loadGrammar(assets, "cpp", "source.cpp", "cpp-configuration.json");
                } finally {
                    files.removeFileProvider(resolver);
                    for (InputStream input : opened) input.close();
                }
            }
            result.dark = result.loadTheme(assets, "dark", true);
            result.light = result.loadTheme(assets, "light", false);
            result.theme(true);
            // Lazy regex compilation is primed on the loader thread, never on an input callback.
            result.grammar(SyntaxLanguage.PYTHON).tokenizeLine("def sample(value): return value + 42 # 注释");
            result.grammar(SyntaxLanguage.CPP).tokenizeLine("int sample(int value) { return value + 42; } // 注释");
            return result;
        } catch (Exception | LinkageError error) { result.close(); throw error; }
    }
    private void loadGrammar(Assets assets, String name, String scope, String configuration) throws IOException {
        String path = "textmate/" + name + ".tmLanguage.json";
        try (InputStream input = assets.open(path)) {
            IGrammarSource source = IGrammarSource.fromInputStream(input, path, null);
            grammars.loadGrammar(configuration == null ? DefaultGrammarDefinition.withGrammarSource(source, name, scope) :
                DefaultGrammarDefinition.withLanguageConfiguration(source, "textmate/" + configuration, name, scope));
        }
    }
    private ThemeModel loadTheme(Assets assets, String name, boolean isDark) throws Exception {
        String path = "textmate/" + name + ".json";
        try (InputStream input = assets.open(path)) {
            ThemeModel model = new ThemeModel(IThemeSource.fromInputStream(input, path, null), "codepocket-" + name);
            model.setDark(isDark); themes.loadTheme(model, false); return model;
        }
    }
    IGrammar grammar(SyntaxLanguage language) {
        if (closed || language.scope == null) throw new IllegalStateException("No active grammar");
        IGrammar grammar = grammars.findGrammar(language.scope);
        if (grammar == null) throw new IllegalStateException("Missing grammar: " + language.scope);
        return grammar;
    }
    ThemeModel theme(boolean isDark) throws Exception {
        if (closed) throw new IllegalStateException("Closed grammar bundle");
        ThemeModel model = isDark ? dark : light;
        // Update grammar metadata first, then notify the analyzer and scheme together.
        grammars.setTheme(model); themes.setTheme(model); return model;
    }
    /** Load the same bounded custom subset into the real engine; no registry lifetime ownership is kept. */
    ThemeModel customTheme(SyntaxThemeDefinition definition) throws Exception {
        if (closed) throw new IllegalStateException("Closed grammar bundle");
        ThemeModel model = definition.model();
        grammars.setTheme(model); themes.setTheme(model); return model;
    }
    @Override public void close() {
        if (closed) return;
        closed = true; grammars.dispose(); themes.dispose();
    }
}
