package com.codepocket.ide.terminal.bridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class BridgeCommandTest {
 @Test public void installedApkPathAndProtocolAreExplicit() {
  String command=BridgeCommand.build("/data/app/test/base.apk","cp-"+"a".repeat(32),"b".repeat(64));
  assertTrue(command.startsWith("CLASSPATH='/data/app/test/base.apk' /system/bin/app_process /system/bin "));
  assertTrue(command.contains(PythonTerminalBridge.class.getName()));
 }
 @Test public void pathsWithQuotesRemainSingleQuotedData() {
  assertEquals("'folder'\"'\"'s file'",BridgeCommand.quote("folder's file"));
  assertEquals("'$(touch /bad); python.py'",BridgeCommand.quote("$(touch /bad); python.py"));
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsNewlineInjection() { BridgeCommand.quote("file\ncommand"); }
 @Test(expected=IllegalArgumentException.class) public void rejectsInvalidAddress() { BridgeCommand.build("/app.apk","../wrong","b".repeat(64)); }
 @Test(expected=IllegalArgumentException.class) public void rejectsInvalidToken() { BridgeCommand.build("/app.apk","cp-"+"a".repeat(32),"b"); }
}
