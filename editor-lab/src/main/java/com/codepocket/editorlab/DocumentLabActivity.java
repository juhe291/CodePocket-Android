package com.codepocket.editorlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.codepocket.ide.EditorViewState;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate opt-in Sora file-session experiment; no execution or broad storage permissions. */
public final class DocumentLabActivity extends Activity implements LabDocuments.Host {
    private static final int PICK_TREE = 71;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService grammarWorker = Executors.newSingleThreadExecutor();
    private LabDocuments documents;
    private final Runnable checkpoint = () -> { if (documents != null) documents.checkpoint(); };
    private SoraInputEditor editor;
    private SoraEditorAdapter port;
    private SoraSyntaxController syntax;
    private SoraAssistController assistance;
    private TextView title, status;
    private String documentMessage = "";
    private boolean installing, destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        documents = (LabDocuments) getLastNonConfigurationInstance();
        if (documents == null) documents = new LabDocuments(getApplicationContext());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xff202630);
        title = new TextView(this); title.setTextColor(0xffdde3ec); title.setTextSize(14); title.setPadding(dp(12), dp(10), dp(12), dp(10));
        title.setText(R.string.documents_initial); root.addView(title);
        HorizontalScrollView bar = new HorizontalScrollView(this); LinearLayout tools = new LinearLayout(this); bar.addView(tools);
        action(tools, R.string.documents_files, this::files);
        action(tools, R.string.documents_save, () -> documents.save(null));
        action(tools, R.string.documents_tree, this::pickTree);
        action(tools, R.string.documents_internal, documents::internal);
        action(tools, R.string.documents_undo, () -> port.history(port.snapshot(), false));
        action(tools, R.string.documents_redo, () -> port.history(port.snapshot(), true));
        action(tools, R.string.documents_completion, () -> assistance.request(SoraAssistance.Mode.COMPLETION));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));
        editor = new SoraInputEditor(this); editor.setSaveEnabled(false); editor.setTypefaceText(Typeface.MONOSPACE);
        editor.setTextSize(15); editor.setLineNumberEnabled(true); editor.setWordwrap(true); editor.setEditable(false);
        // Device cursor motion remains part of the separate scratch experiment's existing controls.
        editor.setCursorAnimationEnabled(false);
        port = new SoraEditorAdapter(editor); syntax = new SoraSyntaxController(editor, ignored -> refreshStatus());
        assistance = new SoraAssistController(this, editor, port, ignored -> {});
        editor.setOnFocusChangeListener((view, focused) -> { if (focused) assistance.resume(); else assistance.pause(); });
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this); status.setTextColor(0xffbdc7d5); status.setTextSize(12); status.setTextIsSelectable(true);
        status.setPadding(dp(12), dp(8), dp(12), dp(8)); root.addView(status);
        editor.subscribeEvent(ContentChangeEvent.class, (event, receiver) -> {
            if (installing) return;
            syntax.request();
            if (documents.current() != null) title.setText(getString(R.string.documents_modified, documents.current().path));
            scheduleCheckpoint();
        });
        editor.subscribeEvent(SelectionChangeEvent.class, (event, receiver) -> { if (!installing) scheduleCheckpoint(); });
        setContentView(root); documents.attach(this);
        if (documents.current() == null && !documents.busy()) documents.internal();
        var assets = getApplicationContext().getAssets();
        grammarWorker.execute(() -> {
            try {
                TextMateBundle bundle = TextMateBundle.load(assets::open);
                runOnUiThread(() -> syntax.ready(bundle)); // closed controller disposes late bundles without touching its View
            } catch (Exception | LinkageError error) {
                runOnUiThread(() -> { if (!destroyed) syntax.failed(); });
            }
        });
    }
    private void scheduleCheckpoint() { handler.removeCallbacks(checkpoint); handler.postDelayed(checkpoint, 400); }
    private void action(LinearLayout tools, int label, Runnable work) {
        Button button = new Button(this); button.setText(label); button.setMinHeight(dp(48));
        button.setOnClickListener(view -> work.run()); tools.addView(button, new ViewGroup.LayoutParams(-2, dp(52)));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void files() {
        LabDocuments.Entry entry = documents.current();
        if (entry == null) { status(getString(R.string.documents_loading)); return; }
        String[] names = entry.files.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(getString(R.string.documents_path, entry.workspace.getTitle()))
            .setSingleChoiceItems(names, entry.files.indexOf(entry.path), (dialog, which) -> { documents.open(names[which]); dialog.dismiss(); })
            .setNegativeButton(android.R.string.cancel, null).show();
    }
    private void pickTree() {
        if (documents.busy()) { status(getString(R.string.documents_loading)); return; }
        if (editor.hasComposingText()) { status(getString(R.string.documents_composing)); return; }
        documents.checkpoint();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_TREE); }
        catch (android.content.ActivityNotFoundException error) { status(getString(R.string.documents_picker_unavailable)); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_TREE || result != RESULT_OK || data == null || data.getData() == null) return;
        int grants = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((grants & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) { status(getString(R.string.documents_no_grant)); return; }
        try {
            if ((grants & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)
                getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        catch (SecurityException error) { status(getString(R.string.documents_temporary_grant)); }
        documents.tree(data.getData());
    }
    @Override public LabDocuments.Capture capture() {
        SoraBuffer.Snapshot source = port == null ? null : port.snapshot();
        SoraBuffer.Viewport stamp = port == null ? null : port.viewport();
        if (source == null || stamp == null) return null;
        return new LabDocuments.Capture(stamp, source.text, new EditorViewState(source.start, source.end,
            editor.getOffsetX(), editor.getOffsetY(), "editor", 0, 0));
    }
    @Override public boolean display(LabDocuments.Entry entry) {
        installing = true; assistance.pause();
        try {
            if (!port.replaceDocument(entry.key, entry.text, entry.view.getStart(), entry.view.getEnd())) return false;
            editor.setEditable(true); port.restoreViewport(entry.view.getScrollX(), entry.view.getScrollY());
            String path = entry.path.toLowerCase(Locale.ROOT);
            SyntaxLanguage language = path.endsWith(".py") ? SyntaxLanguage.PYTHON :
                path.endsWith(".cpp") || path.endsWith(".h") || path.endsWith(".hpp") ? SyntaxLanguage.CPP : SyntaxLanguage.PLAIN;
            syntax.configure(language, true); assistance.configure(language, true);
            title.setText(getString(R.string.documents_title, entry.workspace.getTitle(), entry.path));
            editor.requestFocus(); assistance.resume(); return true;
        } finally { installing = false; }
    }
    @Override public void status(String message) {
        documentMessage = message; refreshStatus();
        LabDocuments.Entry entry = documents == null ? null : documents.current();
        if (entry != null && title != null) title.setText(entry.dirty() ? getString(R.string.documents_modified, entry.path) :
            getString(R.string.documents_title, entry.workspace.getTitle(), entry.path));
    }
    private void refreshStatus() {
        if (status != null && syntax != null) status.setText(getString(R.string.documents_status, documentMessage, syntax.state()));
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.isCtrlPressed() && !event.isAltPressed() && !event.isMetaPressed() && event.getKeyCode() == KeyEvent.KEYCODE_S) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) documents.save(null);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public Object onRetainNonConfigurationInstance() { documents.checkpoint(); return documents; }
    @Override protected void onResume() { super.onResume(); if (syntax != null) syntax.resume(); if (assistance != null) assistance.resume(); }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (assistance != null) { if (focus) assistance.resume(); else assistance.pause(); }
        if (!focus && port != null) port.interactionChanged();
    }
    @Override protected void onPause() {
        handler.removeCallbacks(checkpoint); if (documents != null) documents.checkpoint();
        if (assistance != null) assistance.pause(); if (syntax != null) syntax.pause(); super.onPause();
    }
    @Override public void onBackPressed() { if (documents.current() == null) finish(); else documents.save(this::finish); }
    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacks(checkpoint); documents.detach(this);
        if (!isChangingConfigurations()) documents.close();
        grammarWorker.shutdownNow(); assistance.close(); syntax.pause(); port.close(); editor.release(); syntax.close(); super.onDestroy();
    }
}
