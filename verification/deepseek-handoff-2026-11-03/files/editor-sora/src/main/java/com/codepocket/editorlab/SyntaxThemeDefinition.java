package com.codepocket.editorlab;

import android.content.Context;
import android.graphics.Color;
import android.util.JsonReader;
import android.util.JsonToken;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import org.eclipse.tm4e.core.registry.IThemeSource;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Bounded offline TextMate theme subset, shared by persistence, production Sora and preview. */
public final class SyntaxThemeDefinition {
    public static final int MAX_BYTES = 16384;
    private static final Set<String> COLORS = Set.of("editor.background", "editor.foreground", "editorCursor.foreground",
            "editor.selectionBackground", "editor.lineHighlightBackground", "editorLineNumber.foreground");
    public final String json, name;
    public final boolean dark;
    private final JSONObject colors;
    private SyntaxThemeDefinition(JSONObject value) throws Exception {
        json = value.toString(); name = value.getString("name"); dark = value.getString("type").equals("dark");
        colors = value.getJSONObject("colors");
    }
    public int color(String key, int fallback) {
        String value = colors.optString(key, "");
        return value.isEmpty() ? fallback : Color.parseColor(value);
    }
    private static void require(boolean condition, String field, String message) {
        if (!condition) throw new IllegalArgumentException(field + "：" + message);
    }
    private static Object read(JsonReader reader, String field, int depth) throws Exception {
        require(depth <= 8, field, "嵌套过深");
        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
            JSONObject object = new JSONObject(); reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName(); require(!object.has(key), field + "." + key, "字段重复");
                object.put(key, read(reader, field + "." + key, depth + 1));
            }
            reader.endObject(); return object;
        }
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            JSONArray array = new JSONArray(); reader.beginArray();
            while (reader.hasNext()) array.put(read(reader, field + "[" + array.length() + "]", depth + 1));
            reader.endArray(); return array;
        }
        require(reader.peek() == JsonToken.STRING, field, "应为文本、对象或数组");
        return reader.nextString();
    }
    private static String string(JSONObject object, String key, String field) {
        require(object.opt(key) instanceof String, field, "应为文本"); return object.optString(key);
    }
    private static void keys(JSONObject object, Set<String> allowed, String field) {
        var keys = object.keys();
        while (keys.hasNext()) { String key = keys.next(); require(allowed.contains(key), field + "." + key, "不支持此字段"); }
    }
    private static void colorValue(String value, String field) {
        require(value.matches("#[0-9a-fA-F]{6}"), field, "请使用 #RRGGBB 颜色");
    }
    public static SyntaxThemeDefinition parse(String text) {
        try {
            require(text != null && text.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES, "主题", "内容不能超过16 KiB");
            Object parsed;
            try (JsonReader reader = new JsonReader(new StringReader(text))) {
                reader.setLenient(false); parsed = read(reader, "主题", 0);
                require(reader.peek() == JsonToken.END_DOCUMENT, "主题", "JSON后有多余内容");
            }
            require(parsed instanceof JSONObject, "主题", "应为JSON对象");
            JSONObject value = (JSONObject) parsed;
            keys(value, Set.of("name", "type", "colors", "tokenColors"), "主题");
            String name = string(value, "name", "name"); require(!name.trim().isEmpty() && name.length() <= 80, "name", "名称应为1–80字");
            String type = string(value, "type", "type"); require(type.equals("dark") || type.equals("light"), "type", "应为dark或light");
            require(value.opt("colors") instanceof JSONObject, "colors", "应为颜色对象");
            JSONObject colors = value.getJSONObject("colors"); keys(colors, COLORS, "colors");
            for (String key : COLORS) if (colors.has(key)) colorValue(string(colors, key, "colors." + key), "colors." + key);
            require(colors.has("editor.background") && colors.has("editor.foreground"), "colors", "需要editor.background和editor.foreground");
            require(value.opt("tokenColors") instanceof JSONArray, "tokenColors", "应为数组");
            JSONArray rules = value.getJSONArray("tokenColors"); require(rules.length() <= 128, "tokenColors", "最多128条规则");
            for (int i = 0; i < rules.length(); i++) {
                String path = "tokenColors[" + i + "]";
                require(rules.opt(i) instanceof JSONObject, path, "应为规则对象");
                JSONObject rule = rules.getJSONObject(i); keys(rule, Set.of("scope", "settings", "name"), path);
                if (rule.has("name")) string(rule, "name", path + ".name");
                Object scope = rule.opt("scope"); JSONArray scopes = scope instanceof String ? new JSONArray().put(scope) : scope instanceof JSONArray ? (JSONArray) scope : null;
                require(scopes != null && scopes.length() > 0 && scopes.length() <= 16, path + ".scope", "需要1–16个范围");
                for (int j = 0; j < scopes.length(); j++) {
                    require(scopes.opt(j) instanceof String, path + ".scope", "应为文本范围");
                    String selector = scopes.getString(j);
                    require(selector.length() <= 192 && selector.matches("[A-Za-z_][A-Za-z0-9_.-]*( [A-Za-z_][A-Za-z0-9_.-]*)*"), path + ".scope", "使用点分范围；父范围可用空格连接");
                }
                require(rule.opt("settings") instanceof JSONObject, path + ".settings", "应为样式对象");
                JSONObject settings = rule.getJSONObject("settings"); keys(settings, Set.of("foreground", "background", "fontStyle"), path + ".settings");
                require(settings.length() > 0, path + ".settings", "样式不能为空");
                for (String key : new String[]{"foreground", "background"}) if (settings.has(key)) colorValue(string(settings, key, path + ".settings." + key), path + ".settings." + key);
                if (settings.has("fontStyle")) {
                    String style = string(settings, "fontStyle", path + ".settings.fontStyle");
                    require(style.matches("(?:(?:italic|bold|underline|strikethrough)(?: (?:italic|bold|underline|strikethrough))*)?"), path + ".settings.fontStyle", "不支持此文字样式");
                }
            }
            return new SyntaxThemeDefinition(value);
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("JSON：" + error.getMessage(), error); }
    }
    public static SyntaxThemeDefinition builtin(Context context, boolean dark) {
        try (InputStream input = context.getAssets().open("textmate/" + (dark ? "dark" : "light") + ".json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) { bytes.write(buffer, 0, count); require(bytes.size() <= MAX_BYTES, "主题", "内容不能超过16 KiB"); }
            JSONObject value = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            value.put("type", dark ? "dark" : "light"); return parse(value.toString());
        } catch (Exception error) { throw new IllegalArgumentException("无法读取内置主题", error); }
    }
    ThemeModel model() throws Exception {
        ThemeModel model = new ThemeModel(IThemeSource.fromString(IThemeSource.ContentType.JSON, json), "codepocket-custom");
        model.setDark(dark); model.load(); return model;
    }
    public void validateEngine() {
        try { model(); } catch (Exception error) { throw new IllegalArgumentException("TextMate：" + error.getMessage(), error); }
    }
}
