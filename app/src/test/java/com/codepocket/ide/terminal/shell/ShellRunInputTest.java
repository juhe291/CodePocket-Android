package com.codepocket.ide.terminal.shell;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class ShellRunInputTest {
 static class Control implements ShellRuntime.RunControl {
  boolean input;int stopped,closed,returned;
  public boolean control(int code) {if(code==3){stopped++;return true;}return code==4&&!input;}
  public boolean acceptsInput(){return input;}
  public boolean output(int bytes){return true;}
  public String label(){return "Python";}
  public void closed(){closed++;}
  public void shellPromptReturned(){returned++;}
 }
 @Test public void startupAndCloudBlockInputButStillAcceptStop() {
  ShellRuntime r=ShellRuntime.create(RuntimeEnvironment.getApplication());Control c=new Control();
  try {assertTrue(r.beginForeground(c));assertTrue(r.filterInput("text"));assertTrue(r.filterInput("\u0004"));assertTrue(r.filterInput("\u0003"));r.send("\u0003");assertEquals(2,c.stopped);}
  finally {r.shutdown();}assertEquals(1,c.closed);
 }
 @Test public void interactiveInputAndEofReachRealTerminal() {
  ShellRuntime r=ShellRuntime.create(RuntimeEnvironment.getApplication());Control c=new Control();c.input=true;
  try {r.beginForeground(c);assertFalse(r.filterInput("hello\n"));assertFalse(r.filterInput("\u0004"));assertEquals("Python",r.foregroundLabel());}
  finally {r.shutdown();}
 }
 @Test public void oldOwnerCannotClearNewRunAndConcurrentLaunchIsRefused() {
  ShellRuntime r=ShellRuntime.create(RuntimeEnvironment.getApplication());Control old=new Control(),next=new Control();
  try {assertTrue(r.beginForeground(old));assertFalse(r.beginForeground(next));r.clearForeground(old);assertTrue(r.beginForeground(next));r.clearForeground(old);assertEquals("Python",r.foregroundLabel());}
  finally {r.shutdown();}assertEquals(0,old.closed);assertEquals(1,next.closed);
 }
 @Test public void returnedPromptNeedsDispatchedCommandAndKernelForegroundAndIsOnceOnly() {
  ShellRuntime r=ShellRuntime.create(RuntimeEnvironment.getApplication());Control c=new Control();
  try {
   assertTrue(r.beginForeground(c));
   org.robolectric.util.ReflectionHelpers.setField(r,"promptReady",true);
   r.checkReturnedPrompt(true);assertEquals(0,c.returned);
   org.robolectric.util.ReflectionHelpers.setField(r,"foregroundCommandDispatched",true);
   r.checkReturnedPrompt(true);assertEquals(0,c.returned); // Editing redraw before the command starts.
   org.robolectric.util.ReflectionHelpers.setField(r,"foregroundCommandStarted",true);
   r.checkReturnedPrompt(false);assertEquals(0,c.returned); // An OSC marker in program output is insufficient.
   r.checkReturnedPrompt(true);r.checkReturnedPrompt(true);assertEquals(1,c.returned);
  } finally {r.shutdown();}
 }
 @Test public void clearedOrReplacedRunCannotReceivePreviousPromptReturn() {
  ShellRuntime r=ShellRuntime.create(RuntimeEnvironment.getApplication());Control a=new Control(),b=new Control();
  try {
   assertTrue(r.beginForeground(a));org.robolectric.util.ReflectionHelpers.setField(r,"foregroundCommandDispatched",true);
   r.clearForeground(a);assertTrue(r.beginForeground(b));org.robolectric.util.ReflectionHelpers.setField(r,"promptReady",true);
   r.checkReturnedPrompt(true);assertEquals(0,a.returned);assertEquals(0,b.returned);
  } finally {r.shutdown();}
 }
 @Test public void actualStartAndPromptMarkersHandleEditingRedrawBeforeBridgeLaunch() throws Exception {
  android.app.Application app=RuntimeEnvironment.getApplication();ShellRuntime r=ShellRuntime.create(app);Control c=new Control();
  ShellLaunchPlan plan=ShellLaunchPlan.prepare(app.getFilesDir(),app.getCacheDir(),null);
  com.termux.terminal.TerminalSession session=new com.termux.terminal.TerminalSession(ShellLaunchPlan.EXECUTABLE,plan.directory.getPath(),plan.arguments,plan.environment,2000,r);
  org.robolectric.util.ReflectionHelpers.setField(r,"session",session);org.robolectric.util.ReflectionHelpers.setField(r,"plan",plan);
  try {
   assertTrue(r.beginForeground(c));org.robolectric.util.ReflectionHelpers.setField(r,"foregroundCommandDispatched",true);
   r.onShellIntegration(session,"B");r.checkReturnedPrompt(true);assertEquals(0,c.returned);
   r.onShellIntegration(session,"C");r.checkReturnedPrompt(true);assertEquals(0,c.returned);
   r.onShellIntegration(session,"A");r.onShellIntegration(session,"B");r.checkReturnedPrompt(false);assertEquals(0,c.returned);
   r.checkReturnedPrompt(true);assertEquals(1,c.returned);
  } finally {r.shutdown();}
 }
}
