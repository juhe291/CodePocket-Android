package com.codepocket.ide.terminal.shell;

import java.io.File;
import java.io.IOException;

/** Launches a real Android shell; no user source is interpolated into a command. */
public final class ShellLaunchPlan {
    public static final String EXECUTABLE = "/system/bin/sh";
    public final File home;
    public final File temporary;
    public final File directory;
    public final String[] arguments;
    public final String[] environment;

    private ShellLaunchPlan(File home, File temporary, File directory) {
        this.home = home;
        this.temporary = temporary;
        this.directory = directory;
        arguments = new String[]{EXECUTABLE, "-i"};
        environment = new String[]{
            "HOME=" + home.getPath(), "TMPDIR=" + temporary.getPath(),
            "PATH=/system/bin:/system/xbin", "TERM=xterm-256color", "COLORTERM=truecolor",
            "LANG=C.UTF-8", "SHELL=" + EXECUTABLE, "PS1=CodePocket $ ",
            "ANDROID_ROOT=/system", "ANDROID_DATA=/data"
        };
    }

    public static ShellLaunchPlan prepare(File files, File cache, File requestedDirectory) throws IOException {
        File root = files.getCanonicalFile();
        File cacheRoot = cache.getCanonicalFile();
        File home = new File(root, "terminal-home").getCanonicalFile();
        File temporary = new File(cacheRoot, "terminal-tmp").getCanonicalFile();
        if (!within(home, root) || !within(temporary, cacheRoot)) throw new IOException("终端目录超出应用目录");
        File cwd = requestedDirectory == null ? home : requestedDirectory.getCanonicalFile();
        if (!within(cwd, root)) throw new IOException("初始目录必须位于应用私有文件目录");
        if (!(home.isDirectory() || home.mkdirs()) || !(temporary.isDirectory() || temporary.mkdirs()))
            throw new IOException("无法创建终端目录");
        if (!cwd.isDirectory()) throw new IOException("终端初始目录不存在");
        return new ShellLaunchPlan(home, temporary, cwd);
    }

    private static boolean within(File file, File root) {
        return file.equals(root) || file.getPath().startsWith(root.getPath() + File.separator);
    }
}
