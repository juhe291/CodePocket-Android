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
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Opt-in, separately installed comparison. Does not accept files, URI grants or project text. */
public final class EditorLabActivity extends Activity {
    private static final String SAMPLE = "# 临时测试草稿：中文 / 😀\nprint('hello')\n";
    private CodeEditor sora;
    private SurfaceProbe surfaceProbe;
    private long documentRevision;
    private int documentGeneration;
    private String scratchKey = "scratch:0";
    private EditText nativeEditor;
    private TextView status;
    private TextView title;
    private LinearLayout root;
    private boolean useSora = true, dark = true, animate = true, motionAllowed;

    private static final class Scratch {
        final String text; final int start, end; final boolean sora, dark, animate;
        Scratch(String text, int start, int end, boolean sora, boolean dark, boolean animate) {
            this.text = text; this.start = start; this.end = end;
            this.sora = sora; this.dark = dark; this.animate = animate;
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
        action(tools, "深浅色", () -> { dark = !dark; appearance(); });
        action(tools, "光标动画", () -> { animate = !animate; appearance(); });
        action(tools, "重置", () -> new AlertDialog.Builder(this).setMessage("丢弃当前临时草稿？")
            .setNegativeButton("取消", null).setPositiveButton("重置", (d, w) -> load(new Scratch(SAMPLE, 0, 0, useSora, dark, animate))).show());
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
        sora = new CodeEditor(this); sora.setTypefaceText(Typeface.MONOSPACE); sora.setTextSize(15);
        sora.setSaveEnabled(false); sora.setLineNumberEnabled(true);
        surfaceProbe = new SurfaceProbe(this, sora,
            new SoraEditorSurface(sora, () -> scratchKey, () -> documentRevision));
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
        sora.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> { documentRevision++; surfaceProbe.dismiss(); updateStatus(); });
        sora.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> { surfaceProbe.dismiss(); updateStatus(); });
        sora.subscribeEvent(ScrollEvent.class, (event, unsubscribe) -> surfaceProbe.reposition());
        sora.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> surfaceProbe.reposition());
        setContentView(root);
        Scratch retained = (Scratch) getLastNonConfigurationInstance();
        load(retained == null ? new Scratch(SAMPLE, 0, 0, true, true, true) : retained);
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
            useSora ? sora.getCursor().getRight() : nativeEditor.getSelectionEnd(), useSora, dark, animate);
    }
    private void load(Scratch value) {
        surfaceProbe.dismiss(); scratchKey = "scratch:" + (++documentGeneration);
        useSora = value.sora; dark = value.dark; animate = value.animate;
        if (useSora) {
            sora.setText(value.text);
            CharPosition start = sora.getText().getIndexer().getCharPosition(Math.max(0, Math.min(value.start, value.text.length())));
            CharPosition end = sora.getText().getIndexer().getCharPosition(Math.max(0, Math.min(value.end, value.text.length())));
            sora.setSelectionRegion(start.line, start.column, end.line, end.column, false);
        } else {
            nativeEditor.setText(value.text);
            nativeEditor.setSelection(Math.max(0, Math.min(value.start, value.text.length())), Math.max(0, Math.min(value.end, value.text.length())));
        }
        sora.setVisibility(useSora ? View.VISIBLE : View.GONE);
        nativeEditor.setVisibility(useSora ? View.GONE : View.VISIBLE);
        (useSora ? sora : nativeEditor).requestFocus(); appearance(); updateStatus();
    }
    private void switchEditor() {
        if (composing()) { message("请先完成输入法组合输入"); return; }
        Scratch old = capture();
        load(new Scratch(old.text, Math.min(old.start, old.end), Math.max(old.start, old.end), !useSora, dark, animate));
        message("已切换；文本与选区保留，撤销历史按组件重新开始");
    }
    private void insert(String text) {
        if (composing()) { message("请先完成输入法组合输入"); return; }
        Scratch old = capture(); int start = Math.min(old.start, old.end), end = Math.max(old.start, old.end);
        if (!ScratchEdits.valid(old.text, start, end, text)) { message("选区边界无效或超出实验命令的20万字符上限"); return; }
        if (useSora) {
            ScratchEdits.replace(sora.getText(), start, end, text);
            CharPosition point = sora.getText().getIndexer().getCharPosition(start + text.length());
            sora.setSelection(point.line, point.column);
        } else {
            nativeEditor.beginBatchEdit();
            try { nativeEditor.getText().replace(start, end, text); nativeEditor.setSelection(start + text.length()); }
            finally { nativeEditor.endBatchEdit(); }
        }
        updateStatus();
    }
    private void history(boolean redo) {
        if (composing()) { message("请先完成输入法组合输入"); return; }
        if (useSora) { if (redo) sora.redo(); else sora.undo(); }
        else nativeEditor.onTextContextMenuItem(redo ? android.R.id.redo : android.R.id.undo);
        updateStatus();
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
        sora.setColorScheme(dark ? new SchemeVS2019() : new SchemeGitHub());
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
            " · " + length + " 字符 · 光标动画" + (motionAllowed && useSora ? "开" : "关"));
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
                .setNegativeButton("关闭", null).setPositiveButton("LGPL 2.1", (d, w) -> license()).show();
        } catch (Exception error) { message("说明读取失败：" + error.getMessage()); }
    }
    private void license() {
        try (InputStream input = getAssets().open("LGPL-2.1.txt"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int read;
            while ((read = input.read(buffer)) >= 0) bytes.write(buffer, 0, read);
            new AlertDialog.Builder(this).setTitle("GNU LGPL 2.1").setMessage(bytes.toString(StandardCharsets.UTF_8.name())).setPositiveButton("关闭", null).show();
        } catch (Exception error) { message("许可读取失败：" + error.getMessage()); }
    }
    @Override public Object onRetainNonConfigurationInstance() { return capture(); }
    @Override protected void onResume() { super.onResume(); if (sora != null) updateMotion(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (focused && sora != null) updateMotion();
        if (!focused && surfaceProbe != null) surfaceProbe.dismiss();
    }
    @Override protected void onPause() { if (surfaceProbe != null) surfaceProbe.dismiss(); motionAllowed = false; if (sora != null) sora.setCursorAnimationEnabled(false); super.onPause(); }
    @Override protected void onDestroy() { if (surfaceProbe != null) surfaceProbe.dismiss(); if (sora != null) sora.release(); super.onDestroy(); }
    @Override public void onBackPressed() {
        if (!capture().text.equals(SAMPLE)) new AlertDialog.Builder(this).setMessage("离开实验？临时草稿未保存，可先长按复制内容。")
            .setNegativeButton("留在此页", null).setPositiveButton("离开", (d, w) -> finish()).show();
        else super.onBackPressed();
    }
}
