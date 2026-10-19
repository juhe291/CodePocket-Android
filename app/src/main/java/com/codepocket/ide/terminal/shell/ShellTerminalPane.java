package com.codepocket.ide.terminal.shell;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import java.io.File;

/** Native Termux view attached to a real PTY. Never fabricates program output. */
// Programmatic View: construction requires the session configuration and focus callback.
@android.annotation.SuppressLint("ViewConstructor")
public final class ShellTerminalPane extends LinearLayout implements TerminalViewClient, ShellRuntime.Observer {
    private final ShellRuntime runtime;
    private final File initialDirectory;
    private final Runnable focusChanged;
    private final TextView caption;
    private final TextView runCaption;
    private final LinearLayout heading;
    private final FrameLayout viewport;
    private final TextView placeholder;
    private final Button start;
    private TerminalView terminal;
    private final TerminalKeyboard keyboardRequest=new TerminalKeyboard();
    private boolean compact;
    public void setCompact(boolean value) { if(compact!=value){compact=value;changed();} }
    public int minimumHeightDp(boolean small) {
        android.graphics.Paint paint=new android.graphics.Paint();paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(fontSp*getResources().getDisplayMetrics().scaledDensity);
        int rows=(int)Math.ceil((4*paint.getFontSpacing()+dp(8))/getResources().getDisplayMetrics().density);
        return 48+(small?0:48)+rows;
    }
    private int fontSp;
    private boolean animations;
    private boolean activeHost = true;
    private boolean reportedLayoutFailure;
    private TerminalSession reportedSession;

    public ShellTerminalPane(Context context, File directory, int fontSp, boolean animations, Runnable focusChanged) {
        this(context, directory, fontSp, animations, focusChanged, ShellRuntime.get(context));
    }
    public ShellTerminalPane(Context context, File directory, int fontSp, boolean animations, Runnable focusChanged, ShellRuntime runtime) {
        super(context);
        this.runtime = runtime;
        this.initialDirectory = directory;
        this.fontSp = fontSp; this.animations = animations; this.focusChanged = focusChanged;
        setOrientation(VERTICAL); setBackgroundColor(0xff10141a);
        heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        caption = new TextView(context);
        caption.setTextColor(0xffaab7c9); caption.setTextSize(10); caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setPadding(dp(10), 0, 0, 0);
        heading.addView(caption, new LayoutParams(0, dp(40), 1));
        start = action("启动", this::ensureSession);
        heading.addView(start, new LayoutParams(dp(56), dp(48)));
        Button menu = action("⋯", () -> showMenu(heading));
        menu.setContentDescription("Shell 会话操作");
        heading.addView(menu, new LayoutParams(dp(48), dp(48)));
        addView(heading);
        runCaption = new TextView(context);
        runCaption.setTextColor(0xffaab7c9);runCaption.setTextSize(11);
        runCaption.setMinHeight(dp(48));runCaption.setGravity(Gravity.CENTER_VERTICAL);runCaption.setMaxLines(2);
        runCaption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        runCaption.setPadding(dp(10),dp(5),dp(10),dp(5));
        runCaption.setOnClickListener(v -> showKeyboard());
        addView(runCaption,new LayoutParams(-1,-2));
        viewport = new FrameLayout(context) {
            @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
                TerminalSession expected = terminal == null ? null : terminal.getCurrentSession();
                try { super.onLayout(changed, l, t, r, b); }
                catch (RuntimeException | LinkageError failure) {
                    reportFailure(expected, failure);
                }
            }
        };
        placeholder = new TextView(context);
        placeholder.setTextColor(0xffaab7c9); placeholder.setTextSize(12); placeholder.setPadding(dp(14), dp(8), dp(14), dp(8));
        viewport.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        addView(viewport, new LayoutParams(-1, 0, 1));
        LinearLayout keys = new LinearLayout(context);
        Button imeButton=action("键盘",this::showKeyboard);imeButton.setContentDescription("显示终端虚拟键盘");
        keys.addView(imeButton,new LayoutParams(dp(48),dp(48)));
        String[][] shortcuts = {{"Ctrl+C","\u0003"},{"Ctrl+D","\u0004"},{"Tab","\t"},{"Esc","\u001b"},{"↑","\u001b[A"},{"↓","\u001b[B"},{"←","\u001b[D"},{"→","\u001b[C"}};
        for (String[] shortcut : shortcuts) {
            String label = shortcut[0], text = shortcut[1];
            Button key = action(label, () -> { sendKey(text); if (terminal != null) terminal.requestFocus(); });
            key.setContentDescription("发送 " + label);
            keys.addView(key, new LayoutParams(dp(label.length() > 3 ? 70 : 48), dp(48)));
        }
        keys.addView(action("到底部", () -> {
            if(terminal!=null) { terminal.stopTextSelectionMode();terminal.setTopRow(0);terminal.invalidate(); }
        }), new LayoutParams(dp(70),dp(48)));
        HorizontalScrollView keyboard = new HorizontalScrollView(context);
        keyboard.setHorizontalScrollBarEnabled(false); keyboard.addView(keys);
        addView(keyboard, new LayoutParams(-1, dp(48)));
        changed();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private Button action(String title, Runnable callback) {
        Button button = new Button(getContext()); button.setText(title); button.setTextSize(10); button.setAllCaps(false);
        button.setTextColor(0xffb8c9df); button.setBackgroundColor(0xff161c25); button.setPadding(dp(6), 0, dp(6), 0);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(dp(48));
        button.setOnClickListener(v -> callback.run()); return button;
    }
    public void setEmbedded(boolean embedded) { heading.setVisibility(embedded ? GONE : VISIBLE); }
    public void ensureSession() { runtime.start(initialDirectory); }
    public boolean hasTerminalFocus() { return terminal != null && terminal.hasFocus(); }
    /** Restore an existing terminal's focus only; never queue a late focus steal or request the IME. */
    public boolean restoreTerminalFocus() { return activeHost && terminal != null && terminal.requestFocus(); }
    private void sendKey(String text) {
        if(text.equals("\u0003") && terminal!=null && terminal.isSelectingText()) {
            runtime.copyText(terminal.getSelectedText());terminal.stopTextSelectionMode();return;
        }
        runtime.send(text);
    }
    public void clearScreen() { runtime.clearScreen(); }
    public void setPreferences(int fontSp, boolean animations) {
        this.fontSp = fontSp; this.animations = animations;
        if (terminal != null) terminal.setTextSize(Math.round(fontSp * getResources().getDisplayMetrics().scaledDensity));
        updateBlink();
    }
    public void hostActive(boolean active) { activeHost = active;if(!active)keyboardRequest.cancel();updateBlink(); }
    public void dispose() { keyboardRequest.cancel();runtime.detach(this); if (terminal != null) { runtime.saveHistoryTopRow(terminal.getTopRow());terminal.setTerminalCursorBlinkerState(false, false); } }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); runtime.attach(this); changed(); }
    @Override protected void onDetachedFromWindow() { dispose(); super.onDetachedFromWindow(); }
    private void updateBlink() {
        if (terminal == null) return;
        float scale = Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        boolean save = ((PowerManager) getContext().getSystemService(Context.POWER_SERVICE)).isPowerSaveMode();
        terminal.setTerminalCursorBlinkerRate(500);
        terminal.setTerminalCursorBlinkerState(activeHost && isShown() && animations && Float.isFinite(scale) && scale > 0 && !save, true);
    }
    @Override public void changed() {
        TerminalSession expected = runtime.session();
        try { renderSession(); }
        catch (RuntimeException | LinkageError failure) { reportFailure(expected, failure); }
    }
    private void reportFailure(TerminalSession expected, Throwable failure) {
        if (expected == null) { Log.e("CodePocket-Shell", "Error presenting terminal state", failure); return; }
        if (reportedLayoutFailure && reportedSession == expected) return;
        reportedLayoutFailure = true; reportedSession = expected;
        post(() -> runtime.failIfCurrent(expected, failure));
    }
    private void renderSession() {
        runCaption.setText(runtime.foregroundLabel());
        runCaption.setVisibility(runtime.foregroundLabel().isEmpty() || compact ? GONE : VISIBLE);
        TerminalSession session = runtime.session();
        if (session == null) {
            if (terminal != null) { terminal.setTerminalCursorBlinkerState(false, false); viewport.removeView(terminal); terminal = null; }
            placeholder.setVisibility(VISIBLE);
            placeholder.setText(runtime.state() == ShellRuntime.State.FAILED ? "真实 Shell 启动失败：\n" + runtime.error() :
                runtime.state() == ShellRuntime.State.PREPARING ? "正在准备真实终端…" : "Android Shell · /system/bin/sh\n点击启动后连接真实进程。Python 运行会接入这个终端；C++ 显示真实云端返回结果。");
            caption.setText(runtime.state() == ShellRuntime.State.PREPARING ? "正在准备 /system/bin/sh" : "/system/bin/sh · 本地终端");
            start.setEnabled(runtime.state() != ShellRuntime.State.PREPARING); start.setVisibility(VISIBLE); return;
        }
        placeholder.setVisibility(GONE);
        if (terminal == null) {
            TerminalView ready = new TerminalView(getContext(), null);
            ready.setOnKeyListener((v,code,event) -> {
                // View.OnKeyListener runs before Termux clears the selected text in onKeyDown.
                if(event.getAction()==KeyEvent.ACTION_DOWN && event.isCtrlPressed() && code==KeyEvent.KEYCODE_C && ready.isSelectingText()) {
                    runtime.copyText(ready.getSelectedText());ready.stopTextSelectionMode();return true;
                }
                return false;
            });
            ready.setTerminalViewClient(this);
            // Termux setTypeface() reads mRenderer.mTextSize; setTextSize() must create the renderer first.
            ready.setTextSize(Math.round(fontSp * getResources().getDisplayMetrics().scaledDensity));
            ready.setTypeface(Typeface.MONOSPACE);
            terminal = ready;
            terminal.setPadding(dp(10), dp(4), dp(10), dp(4));
            terminal.setFocusableInTouchMode(true);
            terminal.setOnFocusChangeListener((v, focused) -> { if (focused) focusChanged.run(); else keyboardRequest.cancel(); updateBlink(); });
            viewport.addView(terminal, new FrameLayout.LayoutParams(-1, -1));
        }
        if(terminal.attachSession(session))terminal.setTopRow(runtime.historyTopRow());
        terminal.onScreenUpdated();
        String state = runtime.state() == ShellRuntime.State.EXITED ? "已退出 · " + session.getExitStatus() : session.getPid() > 0 ? "PID " + session.getPid() : "正在启动";
        caption.setText(getContext().getString(com.codepocket.ide.R.string.shell_session_caption, state, runtime.plan().directory.getPath()));
        start.setVisibility(runtime.state() == ShellRuntime.State.EXITED ? VISIBLE : GONE);
        updateBlink();
    }
    private void showMenu(android.view.View anchor) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        String[] labels = {"显示虚拟键盘", "复制终端记录", "粘贴", "清空终端显示", "结束当前 Shell", "终端说明与许可证", "查看上次终端错误"};
        for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, labels[i]);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: showKeyboard(); break;
                case 1: runtime.copyTranscript(); break;
                case 2: runtime.paste(); break;
                case 3: clearScreen(); break;
                case 4: new AlertDialog.Builder(getContext()).setTitle("结束当前 Shell？")
                    .setMessage("将关闭当前终端及其正在运行的程序，编辑器文件保留。")
                    .setNegativeButton("取消", null).setPositiveButton("结束", (d, w) -> runtime.close()).show(); break;
                case 5: showAbout(); break;
                case 6: new AlertDialog.Builder(getContext()).setTitle("上次终端错误（本机记录）")
                    .setMessage(runtime.failureDetails())
                    .setNegativeButton("关闭", null)
                    .setPositiveButton("复制", (d, w) -> runtime.copyFailureDetails()).show(); break;
            }
            return true;
        }); menu.show();
    }
    public void showAbout() {
        new AlertDialog.Builder(getContext()).setTitle("真实 Android Shell")
                    .setMessage("使用本地 /system/bin/sh 和 Termux 终端组件，无需另装 Termux。\n\n命令以应用 UID 执行，可修改本应用有权访问的文件；终端只读取磁盘上已保存的内容。系统目录权限仍由 Android 控制，不是 root。\n\nSAF URI 不能直接作为 Shell 目录；外部项目仍由文件管理器授权和写回。Python 运行按钮把 Chaquopy 独立进程接入当前 PTY，支持真实输入输出并返回 Shell。未安装 python、pip 或 clang/g++ 命令。C++ 通过 Wandbox 预填输入运行，完成后返回。\n\nTermux v0.118.3 / 5b657c6，保留 GPLv3 与 Apache 2.0 来源及通知；完整许可见 App 资产 terminal-licenses 和仓库 third-party/termux。")
                    .setPositiveButton("知道了", null).show();
    }
    public void showKeyboard() {
        TerminalView target=terminal;
        if (target == null) return;
        // Keyboard/focus is UI state, independent of the startup guard on bytes sent to the PTY.
        keyboardRequest.show(target,()->activeHost&&terminal==target&&runtime.session()==target.getCurrentSession());
    }
    @Override public float onScale(float scale) { setPreferences(Math.max(10, Math.min(24, Math.round(fontSp * scale))), animations); return 1f; }
    @Override public void onSingleTapUp(MotionEvent e) { showKeyboard(); }
    @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
    @Override public boolean shouldEnforceCharBasedInput() { return false; }
    @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
    @Override public boolean isTerminalViewSelected() { return isShown() && hasTerminalFocus(); }
    @Override public void copyModeChanged(boolean copyMode) { }
    @Override public boolean onKeyDown(int code, KeyEvent e, TerminalSession s) {
        if(e.isCtrlPressed() && code==KeyEvent.KEYCODE_C)return runtime.filterInput("\u0003");
        if(e.isCtrlPressed() && code==KeyEvent.KEYCODE_D)return runtime.filterInput("\u0004");
        return !runtime.acceptsInput() && !e.isSystem();
    }
    @Override public boolean onKeyUp(int code, KeyEvent e) { return false; }
    @Override public boolean onLongPress(MotionEvent e) { return false; }
    @Override public boolean readControlKey() { return false; }
    @Override public boolean readAltKey() { return false; }
    @Override public boolean readShiftKey() { return false; }
    @Override public boolean readFnKey() { return false; }
    @Override public boolean onCodePoint(int code, boolean ctrl, TerminalSession s) {
        if(ctrl && (code=='c'||code=='C'||code==3))return runtime.filterInput("\u0003");
        if(ctrl && (code=='d'||code=='D'||code==4))return runtime.filterInput("\u0004");
        return !runtime.acceptsInput();
    }
    @Override public void onEmulatorSet() { updateBlink(); }
    @Override public void logError(String tag, String message) { Log.e(tag, message); }
    @Override public void logWarn(String tag, String message) { Log.w(tag, message); }
    @Override public void logInfo(String tag, String message) { Log.i(tag, message); }
    @Override public void logDebug(String tag, String message) { Log.d(tag, message); }
    @Override public void logVerbose(String tag, String message) { Log.v(tag, message); }
    @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { Log.e(tag, message, e); }
    @Override public void logStackTrace(String tag, Exception e) { Log.e(tag, "Terminal error", e); }
}
