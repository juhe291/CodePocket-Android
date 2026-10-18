package com.termux.terminal;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
public class ShellIntegrationTest extends junit.framework.TestCase {
 public void testFragmentedPromptMarkersAreInvisible() {
  List<String> markers=new ArrayList<>();
  TerminalTestCase.MockTerminalOutput output=new TerminalTestCase.MockTerminalOutput() {
   @Override public void onShellIntegration(String marker){markers.add(marker);}
  };
  TerminalEmulator emulator=new TerminalEmulator(output,80,24,13,15,2000,null);
  for(byte value:"\033]133;A\007CodePocket $ \033]133;B\007".getBytes(StandardCharsets.UTF_8))emulator.append(new byte[]{value},1);
  assertEquals(java.util.Arrays.asList("A","B"),markers);
  assertEquals("CodePocket $",emulator.getScreen().getTranscriptText().trim());
 }
}
