package com.codepocket.ide.terminal.bridge;

/** A quoted command to the existing Shell, not a generated replacement program. */
public final class BridgeCommand {
    public static final String MAIN = "com.codepocket.ide.terminal.bridge.PythonTerminalBridge";
    public static String quote(String value) {
        if (value == null || value.indexOf(0) >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid command argument");
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
    public static String build(String apk, String address, String token) {
        if (!address.matches("cp-[0-9a-f]{32}") || !token.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid bridge identity");
        return "CLASSPATH=" + quote(apk) + " /system/bin/app_process /system/bin " + MAIN + " " + quote(address) + " " + quote(token);
    }
}
