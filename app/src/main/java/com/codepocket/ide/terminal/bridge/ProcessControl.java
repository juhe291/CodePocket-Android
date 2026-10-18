package com.codepocket.ide.terminal.bridge;
/** Small public JNI surface; Android does not expose getpgid/setpgid in the SDK. */
public final class ProcessControl {
    static { System.loadLibrary("termux"); }
    private ProcessControl() { }
    public static native int getGroup(int pid);
    public static native int isolate();
}
