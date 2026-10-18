package com.codepocket.ide.terminal.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** One bounded private diagnostic. Never records terminal content or sends data off-device. */
final class ShellFailureLog {
    static final int MAX_BYTES = 65536;
    static final String NAME = "terminal-last-error.txt";
    static void write(File files, String platform, Throwable failure) throws IOException {
        StringWriter stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        String report = "CodePocket Shell startup/render failure\n" + platform + "\n" + new java.util.Date() + "\n" + stack;
        // <=16K UTF-16 code units always fit within 64K UTF-8 bytes, including replacement encoding.
        if (report.length() > MAX_BYTES / 4) report = report.substring(0, MAX_BYTES / 4 - 20) + "\n[report truncated]";
        if (!(files.isDirectory() || files.mkdirs())) throw new IOException("Cannot create private diagnostic directory");
        try (FileOutputStream out = new FileOutputStream(new File(files, NAME))) {
            out.write(report.getBytes(StandardCharsets.UTF_8));
        }
    }
    static String read(File files) throws IOException {
        File report = new File(files, NAME);
        if (!report.exists()) return "尚无本机终端错误记录。记录仅在当前修复版本捕获到错误后生成，不包含旧版本的崩溃日志。";
        byte[] bytes = new byte[MAX_BYTES];
        int used = 0;
        try (FileInputStream in = new FileInputStream(report)) {
            while (used < bytes.length) {
                int count = in.read(bytes, used, bytes.length - used);
                if (count < 0) break;
                used += count;
            }
        }
        return new String(bytes, 0, used, StandardCharsets.UTF_8);
    }
}
