package com.codepocket.editorlab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import com.codepocket.editor.surface.EditorSurface;
import com.codepocket.editor.surface.PopupGeometry;
import com.codepocket.ide.*;
import java.util.function.IntConsumer;

/** Non-focusable native popup; actual shared lexical candidates/references, no fabricated symbols. */
final class SoraAssistPopup {
    private final Activity activity;
    private final View parent;
    private final EditorSurface surface;
    private final IntConsumer accept;
    private final LinearLayout content, header;
    private final TextView heading, details, signature;
    private final Button close, forms;
    private final ListView list;
    private final PopupWindow popup;
    private final BaseAdapter rows;
    private SoraAssistance.Result result;
    private int index, form, foreground, background, selection;
    private boolean explicitSelection;
    SoraAssistPopup(Activity activity, View parent, EditorSurface surface, IntConsumer accept, Runnable dismissed) {
        this.activity = activity; this.parent = parent; this.surface = surface; this.accept = accept;
        content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        heading = text(11); heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        close = new Button(activity); close.setText("×"); close.setContentDescription("关闭代码辅助"); close.setMinHeight(dp(48));
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48))); content.addView(header);
        details = text(12); details.setMaxLines(3);
        signature = text(13); signature.setTypeface(Typeface.MONOSPACE); signature.setMaxLines(5);
        forms = new Button(activity); forms.setText("切换参数形式"); forms.setMinHeight(dp(48));
        forms.setOnClickListener(view -> { if (result != null && result.signature != null) { form = (form + 1) % result.signature.getReference().getForms().size(); describe(); } });
        rows = new BaseAdapter() {
            @Override public int getCount() { return result == null || result.completions == null ? 0 : result.completions.getItems().size(); }
            @Override public Object getItem(int position) { return result.completions.getItems().get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convert, ViewGroup container) {
                TextView row = convert instanceof TextView ? (TextView) convert : text(13);
                row.setTypeface(Typeface.MONOSPACE); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinHeight(dp(48));
                row.setSingleLine(true); row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                CompletionItem item = result.completions.getItems().get(position);
                row.setText(activity.getString(R.string.assist_candidate_label, item.getKind().getIcon(), item.getLabel()));
                row.setContentDescription(activity.getString(R.string.assist_candidate_description, item.getLabel(), item.getKind().getTitle()));
                row.setTextColor(foreground); row.setBackgroundColor(position == index ? selection : background);
                return row;
            }
        };
        list = new ListView(activity); list.setAdapter(rows); list.setDividerHeight(0); list.setFocusable(false);
        list.setOnItemClickListener((view, row, position, id) -> accept.accept(position));
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(signature, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(forms, new LinearLayout.LayoutParams(-1, dp(48))); content.addView(details);
        popup = new PopupWindow(content, 1, 1, false);
        popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setElevation(dp(6)); popup.setAnimationStyle(0);
        if (Build.VERSION.SDK_INT >= 29) popup.setIsLaidOutInScreen(true);
        popup.setOnDismissListener(dismissed::run); close.setOnClickListener(view -> dismiss());
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private TextView text(int size) {
        TextView view = new TextView(activity); view.setTextSize(size); view.setPadding(dp(8), dp(4), dp(8), dp(4)); return view;
    }
    boolean showingCandidates() { return popup.isShowing() && result != null && result.completions != null; }
    boolean showing() { return popup.isShowing(); }
    int index() { return index; }
    boolean explicitSelection() { return explicitSelection; }
    void move(int delta) {
        if (!showingCandidates()) return;
        explicitSelection = true;
        index = Math.max(0, Math.min(index + delta, rows.getCount() - 1)); rows.notifyDataSetChanged(); list.setSelection(index); describe();
    }
    boolean show(SoraAssistance.Result result, boolean dark) {
        this.result = result; index = form = 0; explicitSelection = false;
        foreground = dark ? 0xffd4d4d4 : 0xff242424; background = dark ? 0xff252a32 : 0xfff8fafc;
        selection = dark ? 0xff30475e : 0xffdbeafe;
        for (TextView view : new TextView[] {heading, details, signature}) view.setTextColor(foreground);
        GradientDrawable border = new GradientDrawable(); border.setColor(background); border.setCornerRadius(dp(6));
        border.setStroke(dp(1), dark ? 0xff495361 : 0xffc5cdd6); popup.setBackgroundDrawable(border);
        content.setBackgroundColor(background); rows.notifyDataSetChanged(); describe(); return reposition();
    }
    private void describe() {
        if (result.completions != null) {
            heading.setText("基础词法补全 · 非语义");
            CompletionItem item = result.completions.getItems().get(index); details.setText(activity.getString(R.string.assist_candidate_detail, item.getKind().getTitle(), item.getDetail()));
        } else {
            heading.setText("内置参数参考 · 非语义");
            SignatureResult found = result.signature; SignatureForm choice = found.getReference().getForms().get(form);
            SpannableString label = new SpannableString(choice.label(found.getReference().getName()));
            var range = choice.range(found.getReference().getName(), found.active(form));
            if (range != null) label.setSpan(new StyleSpan(Typeface.BOLD), range.getFirst(), range.getSecond(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            signature.setText(label); details.setText(activity.getString(R.string.assist_reference_detail, found.getReference().getDescription(), found.getReference().getSource()));
        }
    }
    @SuppressLint("RtlHardcoded") // Shared anchors are screen coordinates, independent of UI text direction.
    boolean reposition() {
        if (result == null) return false;
        EditorSurface.Anchor anchor = surface.anchor();
        if (activity.isDestroyed() || activity.isFinishing() || anchor == null || !anchor.matches(result.source.key, result.source.revision, result.source.start)) { dismiss(); return false; }
        boolean candidates = result.completions != null;
        int desired = candidates ? 48 + Math.min(rows.getCount(), 5) * 48 + 72 : 240;
        var placed = PopupGeometry.place(anchor.visible, anchor.x, anchor.top, anchor.bottom, dp(380), dp(desired), dp(candidates ? 48 : 96), dp(3));
        if (placed == null) { dismiss(); return false; }
        header.setVisibility(placed.height >= dp(96) ? View.VISIBLE : View.GONE);
        list.setVisibility(candidates ? View.VISIBLE : View.GONE); signature.setVisibility(candidates ? View.GONE : View.VISIBLE);
        forms.setVisibility(!candidates && placed.height >= dp(192) && result.signature.getReference().getForms().size() > 1 ? View.VISIBLE : View.GONE);
        details.setVisibility(placed.height >= dp(candidates ? 216 : 192) ? View.VISIBLE : View.GONE);
        int x = placed.x, y = placed.y;
        if (Build.VERSION.SDK_INT < 29) {
            int[] screen = new int[2], window = new int[2]; activity.getWindow().getDecorView().getLocationOnScreen(screen); activity.getWindow().getDecorView().getLocationInWindow(window);
            x -= screen[0] - window[0]; y -= screen[1] - window[1];
        }
        if (popup.isShowing()) popup.update(x, y, placed.width, placed.height);
        else {
            popup.setWidth(placed.width); popup.setHeight(placed.height);
            try { popup.showAtLocation(parent, Gravity.TOP | Gravity.LEFT, x, y); }
            catch (WindowManager.BadTokenException error) { dismiss(); return false; }
        }
        return true;
    }
    void dismiss() { result = null; popup.dismiss(); }
}
