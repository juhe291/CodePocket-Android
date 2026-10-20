package com.codepocket.ide.terminal.shell;

import java.io.File;
import java.io.IOException;

/** Launches a real Android shell; no user source is interpolated into a command. */
public final class ShellLaunchPlan {
    public static final String PROMPT = "\u0001\r\u0001\u001b]133;A\u0007\u001b[34m\u0001CodePocket\u0001\u001b[36m\u0001 ${PWD}\u0001\u001b[32m\u0001 $ \u0001\u001b[0m\u001b]133;B\u0007\u0001";
    public static final String EXECUTABLE = "/system/bin/sh";
    public final File home;
    public final File temporary;
    public final File directory;
    public final File startup;
    // mksh uses ENV instead of its default /system/etc/mkshrc. Preserve system setup, then set our markers.
    public static final String STARTUP = "if [ -r /system/etc/mkshrc ]; then . /system/etc/mkshrc; fi\nPS1=$(printf '\\001\\r\\001\\033]133;A\\007\\033[34m\\001CodePocket\\001\\033[36m\\001 ${PWD}\\001\\033[32m\\001 $ \\001\\033[0m\\033]133;B\\007\\001')\nexport PS1\nPS2='> '\n";
    public final String[] arguments;
    public final String[] environment;

    private ShellLaunchPlan(File home, File temporary, File directory, File startup, java.util.Map<String,String> inherited) {
        this.home = home;
        this.temporary = temporary;
        this.directory = directory;this.startup=startup;
        arguments = new String[]{EXECUTABLE, "-i"};
        java.util.Map<String,String> env=new java.util.LinkedHashMap<>();
        env.put("ANDROID_ROOT","/system");env.put("ANDROID_DATA","/data");
        // JNI deliberately calls clearenv(). app_process still needs the device's ART/APEX
        // roots and boot paths; do not hardcode Android-version-specific /apex locations.
        for (String name : new String[]{"ANDROID_ROOT","ANDROID_DATA","ANDROID_ART_ROOT","ANDROID_RUNTIME_ROOT",
                "ANDROID_I18N_ROOT","ANDROID_TZDATA_ROOT","ANDROID_CONSCRYPT_ROOT","ART_APEX_DATA",
                "BOOTCLASSPATH","DEX2OATBOOTCLASSPATH"}) {
            String value=inherited.get(name);
            if(value!=null && !value.isEmpty() && value.indexOf(0)<0)env.put(name,value);
        }
        env.put("HOME",home.getPath());env.put("TMPDIR",temporary.getPath());
        env.put("PATH","/system/bin:/system/xbin");env.put("TERM","xterm-256color");env.put("COLORTERM","truecolor");
        env.put("LANG","C.UTF-8");env.put("SHELL",EXECUTABLE);env.put("PS1",PROMPT);env.put("ENV",startup.getPath());
        environment=env.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toArray(String[]::new);
    }

    public static ShellLaunchPlan prepare(File files, File cache, File requestedDirectory) throws IOException {
        return prepare(files,cache,requestedDirectory,System.getenv());
    }
    static ShellLaunchPlan prepare(File files, File cache, File requestedDirectory, java.util.Map<String,String> inherited) throws IOException {
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
        File startup=File.createTempFile("codepocket-rc-", ".sh",home);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(startup)) {
            out.write(STARTUP.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(IOException failure) { startup.delete();throw failure; }
        return new ShellLaunchPlan(home, temporary, cwd,startup,inherited);
    }

    private static boolean within(File file, File root) {
        return file.equals(root) || file.getPath().startsWith(root.getPath() + File.separator);
    }
}
