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
  boolean input;int stopped,closed;
  public boolean control(int code) {if(code==3){stopped++;return true;}return code==4&&!input;}
  public boolean acceptsInput(){return input;}
  public boolean output(int bytes){return true;}
  public String label(){return "Python";}
  public void closed(){closed++;}
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
}
