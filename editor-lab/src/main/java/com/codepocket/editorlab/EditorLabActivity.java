package com.codepocket.editorlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import com.codepocket.editor.surface.TextEdits;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opt-in, separately installed comparison. Does not accept files, URI grants or project text. */
public final class EditorLabActivity extends Activity {
    private static final String SAMPLE = "# 临时测试草稿：中文 / 😀\nprint('hello')\n";
    private SoraInputEditor sora;
    private SurfaceProbe surfaceProbe;
    private SoraEditorAdapter soraAdapter;
    private SoraSyntaxController syntax;
    private SyntaxLanguage syntaxLanguage = SyntaxLanguage.PYTHON;
    private final ExecutorService syntaxLoader = Executors.newSingleThreadExecutor();
    private int documentGeneration;
    private EditText nativeEditor;
    private TextView status;
    private TextView title;
    private LinearLayout root;
    private boolean useSora = true, dark = true, animate = true, motionAllowed;

    private static final class Scratch {
        final String text; final int start, end; final boolean sora, dark, animate, wrap; final SyntaxLanguage language;
        Scratch(String text, int start, int end, boolean sora, boolean dark, boolean animate, SyntaxLanguage language, boolean wrap) {
            this.text = text; this.start = start; this.end = end;
            this.sora = sora; this.dark = dark; this.animate = animate; this.language = language; this.wrap = wrap;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(this);
        title.setText("编辑器实验 · 临时草稿\n不读写项目；进程结束后草稿清空");
        title.setPadding(dp(12), dp(8), dp(12), dp(8)); root.addView(title);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout tools = new LinearLayout(this); scroll.addView(tools);
        action(tools, "原生 / Sora", this::switchEditor);
        action(tools, "插入 ()", () -> insert("()"));
        action(tools, "缩进", () -> insert("    "));
        action(tools, "撤销", () -> history(false)); action(tools, "重做", () -> history(true));
        action(tools, "跳转行", this::jumpToLine);
        action(tools, "自动换行", () -> {
            if (!useSora || composing()) { message("请在Sora中完成组合输入后操作"); return; }
            SoraBuffer.Viewport saved = soraAdapter.viewport();
            sora.setWordwrap(!sora.isWordwrap()); soraAdapter.restoreViewport(saved);
        });
        action(tools, "语法", this::chooseLanguage);
        action(tools, "深浅色", () -> { dark = !dark; appearance(); });
        action(tools, "光标动画", () -> { animate = !animate; appearance(); });
        action(tools, "重置", () -> new AlertDialog.Builder(this).setMessage("丢弃当前临时草稿？")
            .setNegativeButton("取消", null).setPositiveButton("重置", (d, w) -> load(new Scratch(SAMPLE, 0, 0, useSora, dark, animate, syntaxLanguage, false))).show());
        action(tools, "光标定位", () -> {
            if (!useSora) { message(getString(R.string.surface_probe_sora)); return; }
            sora.requestFocus();
            if (!surfaceProbe.show(dark)) message(getString(R.string.surface_probe_unavailable));
        });
        action(tools, "许可 / 说明", this::notices);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(52)));
        FrameLayout body = new FrameLayout(this);
        nativeEditor = new EditText(this) {
            @Override protected void onSelectionChanged(int start, int end) {
                super.onSelectionChanged(start, end); updateStatus();
            }
        };
        nativeEditor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        nativeEditor.setGravity(Gravity.TOP | Gravity.START); nativeEditor.setTypeface(Typeface.MONOSPACE);
        nativeEditor.setTextSize(15); nativeEditor.setHorizontallyScrolling(true); nativeEditor.setSaveEnabled(false);
        sora = new SoraInputEditor(this); sora.setTypefaceText(Typeface.MONOSPACE); sora.setTextSize(15);
        sora.setSaveEnabled(false); sora.setLineNumberEnabled(true);
        soraAdapter = new SoraEditorAdapter(sora);
        syntax = new SoraSyntaxController(sora, ignored -> updateStatus());
        surfaceProbe = new SurfaceProbe(this, sora, soraAdapter);
        sora.setOnFocusChangeListener((view, focused) -> { if (!focused) surfaceProbe.dismiss(); });
        body.addView(nativeEditor, new FrameLayout.LayoutParams(-1, -1));
        body.addView(sora, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this); status.setPadding(dp(12), dp(8), dp(12), dp(8)); root.addView(status);
        nativeEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) { updateStatus(); }
        });
        sora.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> { surfaceProbe.dismiss(); syntax.request(); updateStatus(); });
        sora.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> { surfaceProbe.dismiss(); updateStatus(); });
        sora.subscribeEvent(ScrollEvent.class, (event, unsubscribe) -> surfaceProbe.reposition());
        sora.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> surfaceProbe.reposition());
        setContentView(root);
        Scratch retained = (Scratch) getLastNonConfigurationInstance();
        load(retained == null ? new Scratch(SAMPLE, 0, 0, true, true, true, SyntaxLanguage.PYTHON, false) : retained);
        var assets = getApplicationContext().getAssets();
        syntaxLoader.execute(() -> {
            try {
                TextMateBundle bundle = TextMateBundle.load(assets::open);
                runOnUiThread(() -> syntax.ready(bundle));
            } catch (Exception | LinkageError error) {
                android.util.Log.e("CodePocketEditorLab", "Offline grammar load failed", error);
                runOnUiThread(() -> syntax.failed());
            }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void action(LinearLayout tools, String name, Runnable work) {
        Button button = new Button(this); button.setText(name); button.setMinHeight(dp(48));
        button.setOnClickListener(v -> work.run()); tools.addView(button, new ViewGroup.LayoutParams(-2, dp(52)));
    }
    private boolean composing() {
        return useSora ? sora.hasComposingText() : BaseInputConnection.getComposingSpanStart(nativeEditor.getText()) >= 0;
    }
    private Scratch capture() {
        return new Scratch(useSora ? sora.getText().toString() : nativeEditor.getText().toString(),
            useSora ? sora.getCursor().getLeft() : nativeEditor.getSelectionStart(),
            useSora ? sora.getCursor().getRight() : nativeEditor.getSelectionEnd(), useSora, dark, animate, syntaxLanguage, sora.isWordwrap());
    }
    private boolean load(Scratch value) {
        if (composing()) { message("请先完成输入法组合输入"); return false; }
        surfaceProbe.dismiss();
        if (value.sora) {
            if (!soraAdapter.replaceDocument("scratch:" + (documentGeneration + 1), value.text, value.start, value.end)) {
                message("草稿更换被拒绝，当前内容已保留"); return false;
            }
        } else {
            int[] selection = TextEdits.restoreSelection(value.text, value.start, value.end);
            nativeEditor.setText(value.text); nativeEditor.setSelection(selection[0], selection[1]);
        }
        sora.setWordwrap(value.wrap);
        documentGeneration++;
        useSora = value.sora; dark = value.dark; animate = value.animate; syntaxLanguage = value.language;
        sora.setVisibility(useSora ? View.VISIBLE : View.GONE);
        nativeEditor.setVisibility(useSora ? View.GONE : View.VISIBLE);
        (useSora ? sora : nativeEditor).requestFocus(); appearance(); updateStatus();
        return true;
    }
    private void switchEditor() {
        if (composing()) { message("请先完成输入法组合输入"); return; }
        Scratch old = capture();
        if (load(new Scratch(old.text, Math.min(old.start, old.end), Math.max(old.start, old.end), !useSora, dark, animate, syntaxLanguage, old.wrap)))
            message("已切换；文本与选区保留，撤销历史按组件重新开始");
    }
    private void insert(String text) {
        if (composing()) { message("请先完成输入法组合输入"); return; }
        Scratch old = capture(); int start = Math.min(old.start, old.end), end = Math.max(old.start, old.end);
        if (!ScratchEdits.valid(old.text, start, end, text)) { message("选区边界无效或超出实验命令的20万字符上限"); return; }
        if (useSora) {
            SoraBuffer.Snapshot source = soraAdapter.snapshot();
            if (!soraAdapter.apply(source, start, end, text, start + text.length(), start + text.length()).accepted())
                message("编辑请求已过期或被拒绝，当前内容已保留");
        } else {
            nativeEditor.beginBatchEdit();
            try { nativeEditor.getText().replace(start, end, text); nativeEditor.setSelection(start + text.length()); }
            finally { nativeEditor.endBatchEdit(); }
        }
        updateStatus();
    }
    private void history(boolean redo) {
        if (composing()) { message("请先完成输入法组合输入"); return; }
        if (useSora) { if (!soraAdapter.history(soraAdapter.snapshot(), redo)) message("当前没有可执行的历史操作"); }
        else nativeEditor.onTextContextMenuItem(redo ? android.R.id.redo : android.R.id.undo);
        updateStatus();
    }
    private void jumpToLine() {
        if (!useSora || composing()) { message("请在Sora中完成组合输入后操作"); return; }
        SoraBuffer.Snapshot source = soraAdapter.snapshot();
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true); input.setHint("从1开始的真实行号");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("跳转行 · Sora导航适配")
            .setView(input).setNegativeButton("取消", null).setPositiveButton("跳转", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int line;
            try { line = Integer.parseInt(input.getText().toString().trim()); }
            catch (NumberFormatException error) { input.setError("请输入有效行号"); return; }
            if (!soraAdapter.jumpToLine(source, line)) { input.setError("行号不存在、草稿已变化或正在组合输入"); return; }
            dialog.dismiss();
        }));
        dialog.show();
    }
    private void chooseLanguage() {
        if (composing()) { message("请先完成输入法组合输入"); return; }
        SyntaxLanguage[] modes = SyntaxLanguage.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = modes[i].label;
        new AlertDialog.Builder(this).setTitle("Sora语法高亮 · 保留草稿与历史")
            .setSingleChoiceItems(labels, syntaxLanguage.ordinal(), (dialog, which) -> {
                if (composing()) { message("请先完成输入法组合输入"); return; }
                syntaxLanguage = modes[which]; surfaceProbe.dismiss(); syntax.configure(syntaxLanguage, dark);
                dialog.dismiss(); updateStatus();
            }).setNegativeButton("取消", null).show();
    }
    private boolean motion() {
        boolean system = Build.VERSION.SDK_INT >= 26 ? ValueAnimator.areAnimatorsEnabled() :
            Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return animate && system && (power == null || !power.isPowerSaveMode());
    }
    private void appearance() {
        surfaceProbe.dismiss();
        root.setBackgroundColor(dark ? 0xff1e1e1e : 0xffffffff);
        title.setTextColor(dark ? 0xffcccccc : 0xff242424);
        nativeEditor.setTextColor(dark ? 0xffd4d4d4 : 0xff242424);
        nativeEditor.setBackgroundColor(dark ? 0xff1e1e1e : 0xffffffff);
        syntax.configure(syntaxLanguage, dark);
        status.setTextColor(dark ? 0xffcccccc : 0xff242424);
        updateMotion();
    }
    private void updateStatus() {
        if (status == null || sora == null || nativeEditor == null) return;
        // No whole-document copies or Settings/PowerManager calls in typing/caret callbacks.
        int start = useSora ? sora.getCursor().getLeft() : nativeEditor.getSelectionStart();
        int end = useSora ? sora.getCursor().getRight() : nativeEditor.getSelectionEnd();
        int length = useSora ? sora.getText().length() : nativeEditor.length();
        status.setText((useSora ? "Sora 0.23.7" : "原生 EditText") + " · UTF-16 " + start + ":" + end +
            " · " + length + " 字符 · 光标动画" + (motionAllowed && useSora ? "开" : "关") +
            "\n" + (useSora ? syntax.state() : "原生对照 · 纯文本"));
    }
    private void updateMotion() {
        motionAllowed = motion(); sora.setCursorAnimationEnabled(motionAllowed); updateStatus();
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private void notices() {
        try (InputStream input = getAssets().open("SORA-NOTICE.txt"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int read;
            while ((read = input.read(buffer)) >= 0) bytes.write(buffer, 0, read);
            new AlertDialog.Builder(this).setTitle("编辑器实验与第三方许可").setMessage(bytes.toString(StandardCharsets.UTF_8.name()))
                .setNegativeButton("关闭", null).setNeutralButton("语法许可", (d, w) -> syntaxLicenses())
                .setPositiveButton("LGPL 2.1", (d, w) -> license()).show();
        } catch (Exception error) { message("说明读取失败：" + error.getMessage()); }
    }
    private void syntaxLicenses() {
        String[] labels = {"VS Code MIT", "VS Code 第三方通知", "EPL 2.0", "Joni MIT", "JCodings MIT", "Apache 2.0", "OpenJDK GPL2 + Classpath Exception", "OpenJDK Assembly Exception"};
        String[] paths = {"VSCODE-LICENSE.txt", "VSCODE-ThirdPartyNotices.txt", "licenses/EPL-2.0.txt", "licenses/JONI-MIT.txt", "licenses/JCODINGS-MIT.txt", "licenses/APACHE-2.0.txt", "licenses/DESUGAR-GPL2.txt", "licenses/DESUGAR-ASSEMBLY-EXCEPTION.txt"};
        new AlertDialog.Builder(this).setTitle("语法实验许可")
            .setItems(labels, (dialog, which) -> {
                try (InputStream input = getAssets().open("textmate/" + paths[which]); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int read;
                    while ((read = input.read(buffer)) >= 0) bytes.write(buffer, 0, read);
                    new AlertDialog.Builder(this).setTitle(labels[which]).setMessage(bytes.toString(StandardCharsets.UTF_8.name())).setPositiveButton("关闭", null).show();
                } catch (Exception error) { message("许可读取失败：" + error.getMessage()); }
            }).setNegativeButton("关闭", null).show();
    }
    private void license() {
        try (InputStream input = getAssets().open("LGPL-2.1.txt"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int read;
            while ((read = input.read(buffer)) >= 0) bytes.write(buffer, 0, read);
            new AlertDialog.Builder(this).setTitle("GNU LGPL 2.1").setMessage(bytes.toString(StandardCharsets.UTF_8.name())).setPositiveButton("关闭", null).show();
        } catch (Exception error) { message("许可读取失败：" + error.getMessage()); }
    }
    @Override public Object onRetainNonConfigurationInstance() { return capture(); }
    @Override protected void onResume() { super.onResume(); if (sora != null) updateMotion(); if (syntax != null) syntax.resume(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (focused && sora != null) updateMotion();
        if (!focused && surfaceProbe != null) surfaceProbe.dismiss();
        if (!focused && soraAdapter != null) soraAdapter.interactionChanged();
    }
    @Override protected void onPause() { if (soraAdapter != null) soraAdapter.interactionChanged(); if (syntax != null) syntax.pause(); if (surfaceProbe != null) surfaceProbe.dismiss(); motionAllowed = false; if (sora != null) sora.setCursorAnimationEnabled(false); super.onPause(); }
    @Override protected void onDestroy() {
        syntaxLoader.shutdownNow();
        if (surfaceProbe != null) surfaceProbe.dismiss();
        if (syntax != null) syntax.pause();
        if (soraAdapter != null) soraAdapter.close();
        if (sora != null) sora.release();
        if (syntax != null) syntax.close();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (!capture().text.equals(SAMPLE)) new AlertDialog.Builder(this).setMessage("离开实验？临时草稿未保存，可先长按复制内容。")
            .setNegativeButton("留在此页", null).setPositiveButton("离开", (d, w) -> finish()).show();
        else super.onBackPressed();
    }
}
