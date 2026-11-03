package com.codepocket.editorlab;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Small illustrative sample; colors come from the same shipped/custom TextMate themes as production Sora. */
public final class SyntaxThemePreview {
    private SyntaxThemePreview() {}
    public static TextView create(Context context, boolean dark, int fontSize) {
        return create(context, dark, fontSize, null);
    }
    /** Custom and built-in themes both render from one validated SyntaxThemeDefinition; no second palette. */
    public static TextView create(Context context, boolean dark, int fontSize, SyntaxThemeDefinition custom) {
        if (custom != null) return custom(context, custom, fontSize);
        TextView view = new TextView(context);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(fontSize);
        view.setContentDescription("C++ 代码配色预览");
        try (InputStream input = context.getAssets().open("textmate/" + (dark ? "dark" : "light") + ".json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int size;
            while ((size = input.read(buffer)) != -1) bytes.write(buffer, 0, size);
            JSONObject theme = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            JSONObject colors = theme.getJSONObject("colors");
            int foreground = Color.parseColor(colors.getString("editor.foreground"));
            view.setTextColor(foreground);
            view.setBackgroundColor(Color.parseColor(colors.getString("editor.background")));
            SpannableStringBuilder sample = new SpannableStringBuilder();
            append(sample, "#include", color(theme, "keyword", foreground));
            append(sample, " <iostream>\n", color(theme, "string", foreground));
            append(sample, "int", color(theme, "storage.type", foreground));
            append(sample, " ", foreground);
            append(sample, "main", color(theme, "entity.name.function", foreground));
            append(sample, "() {\n    std::cout << ", foreground);
            append(sample, "\"Hello\"", color(theme, "string", foreground));
            append(sample, ";\n    ", foreground);
            append(sample, "return", color(theme, "keyword", foreground));
            append(sample, " ", foreground);
            append(sample, "0", color(theme, "constant.numeric", foreground));
            append(sample, ";\n}", foreground);
            view.setText(sample);
        } catch (Exception failure) {
            // Do not silently replace missing syntax colors with workbench accent colors.
            view.setTextColor(dark ? 0xffd4d4d4 : 0xff263445);
            view.setText("代码配色预览暂不可用");
            android.util.Log.w("CodePocketEditor", "Cannot load syntax preview theme", failure);
        }
        return view;
    }
    private static TextView custom(Context context, SyntaxThemeDefinition theme, int fontSize) {
        TextView view = new TextView(context);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(fontSize);
        view.setContentDescription("C++ 代码配色预览（自定义主题）");
        int foreground = theme.color("editor.foreground", 0xffd4d4d4);
        view.setTextColor(foreground);
        view.setBackgroundColor(theme.color("editor.background", 0xff1e1e1e));
        SpannableStringBuilder sample = new SpannableStringBuilder();
        append(sample, "#include", theme.tokenColor("keyword", foreground));
        append(sample, " <iostream>\n", theme.tokenColor("string", foreground));
        append(sample, "int", theme.tokenColor("storage.type", foreground));
        append(sample, " ", foreground);
        append(sample, "main", theme.tokenColor("entity.name.function", foreground));
        append(sample, "() {\n    std::cout << ", foreground);
        append(sample, "\"Hello\"", theme.tokenColor("string", foreground));
        append(sample, ";\n    ", foreground);
        append(sample, "return", theme.tokenColor("keyword", foreground));
        append(sample, " ", foreground);
        append(sample, "0", theme.tokenColor("constant.numeric", foreground));
        append(sample, ";\n}", foreground);
        view.setText(sample);
        return view;
    }
    private static int color(JSONObject theme, String scope, int fallback) throws Exception {
        JSONArray entries = theme.getJSONArray("tokenColors");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            JSONArray scopes = entry.getJSONArray("scope");
            for (int j = 0; j < scopes.length(); j++) if (scope.equals(scopes.getString(j)))
                return Color.parseColor(entry.getJSONObject("settings").getString("foreground"));
        }
        return fallback;
    }
    private static void append(SpannableStringBuilder text, String value, int color) {
        int begin = text.length(); text.append(value);
        text.setSpan(new ForegroundColorSpan(color), begin, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
