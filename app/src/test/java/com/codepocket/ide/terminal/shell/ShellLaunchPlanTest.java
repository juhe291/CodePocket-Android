package com.codepocket.ide.terminal.shell;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ShellLaunchPlanTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void defaultHomeAndTempArePrivate() throws Exception {
        File files=temp.newFolder("files"),cache=temp.newFolder("cache");
        ShellLaunchPlan p=ShellLaunchPlan.prepare(files,cache,null);
        assertEquals(new File(files,"terminal-home").getCanonicalFile(),p.directory);
        assertTrue(p.home.isDirectory()); assertTrue(p.temporary.isDirectory());
    }
    @Test public void internalWorkspaceIsActualInitialDirectory() throws Exception {
        File files=temp.newFolder("files"),cache=temp.newFolder("cache"),project=new File(files,"workspace");
        assertTrue(project.mkdir());
        assertEquals(project.getCanonicalFile(),ShellLaunchPlan.prepare(files,cache,project).directory);
    }
    @Test public void noSourceOrUserPathIsInterpolatedAsShellCode() throws Exception {
        File files=temp.newFolder("files"),cache=temp.newFolder("cache"),project=new File(files,"a ' quoted folder");
        assertTrue(project.mkdir());
        ShellLaunchPlan p=ShellLaunchPlan.prepare(files,cache,project);
        assertArrayEquals(new String[]{"/system/bin/sh","-i"},p.arguments);
        assertEquals(project.getCanonicalFile(),p.directory);
        assertTrue(Arrays.asList(p.environment).contains("PS1="+ShellLaunchPlan.PROMPT));
        assertFalse(Arrays.toString(p.arguments).contains("quoted"));
    }
    @Test(expected=IOException.class) public void rejectsOutsideInitialDirectory() throws Exception {
        ShellLaunchPlan.prepare(temp.newFolder("files"),temp.newFolder("cache"),temp.newFolder("outside"));
    }
    @Test(expected=IOException.class) public void rejectsSiblingWithSamePrefix() throws Exception {
        File files=temp.newFolder("files"),cache=temp.newFolder("cache"),sibling=temp.newFolder("files-other");
        ShellLaunchPlan.prepare(files,cache,sibling);
    }
    @Test(expected=IOException.class) public void rejectsMissingRequestedDirectory() throws Exception {
        File files=temp.newFolder("files"),cache=temp.newFolder("cache");
        ShellLaunchPlan.prepare(files,cache,new File(files,"missing"));
    }
    @Test(expected=IOException.class) public void rejectsHomePathOccupiedByFile() throws Exception {
        File files=temp.newFolder("files"),cache=temp.newFolder("cache");
        assertTrue(new File(files,"terminal-home").createNewFile());
        ShellLaunchPlan.prepare(files,cache,null);
    }
    @Test public void androidPathDoesNotClaimPythonOrCompilerCommands() throws Exception {
        ShellLaunchPlan p=ShellLaunchPlan.prepare(temp.newFolder("files"),temp.newFolder("cache"),null);
        assertTrue(Arrays.asList(p.environment).contains("PATH=/system/bin:/system/xbin"));
        assertFalse(Arrays.toString(p.environment).contains("termux/files/usr"));
    }
    @Test public void startupFileOverridesSystemPromptAndIsPassedAsEnv() throws Exception {
        ShellLaunchPlan p=ShellLaunchPlan.prepare(temp.newFolder("files"),temp.newFolder("cache"),null);
        assertTrue(p.startup.isFile());
        assertEquals(p.home,p.startup.getCanonicalFile().getParentFile());
        assertTrue(Arrays.asList(p.environment).contains("ENV="+p.startup.getPath()));
        byte[] bytes=java.nio.file.Files.readAllBytes(p.startup.toPath());
        String script=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(script.indexOf(". /system/etc/mkshrc") < script.indexOf("PS1="));
        // Consumed by scripts/verify_shell_startup.py to execute the exact generated startup file.
        System.out.println("CODEPOCKET_SHELL_PROMPT="+java.util.Base64.getEncoder().encodeToString(ShellLaunchPlan.PROMPT.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        System.out.println("CODEPOCKET_SHELL_RC="+java.util.Base64.getEncoder().encodeToString(bytes));
    }
    @Test public void sessionsHaveIndependentStartupFiles() throws Exception {
        File files=temp.newFolder("files"),cache=temp.newFolder("cache");
        ShellLaunchPlan a=ShellLaunchPlan.prepare(files,cache,null),b=ShellLaunchPlan.prepare(files,cache,null);
        assertNotEquals(a.startup,b.startup);
        assertTrue(a.startup.delete());
        assertTrue(b.startup.isFile());
    }
    @Test public void inheritedArtEnvironmentSurvivesNativeClearenv() throws Exception {
        java.util.Map<String,String> supplied=new java.util.HashMap<>();
        supplied.put("ANDROID_ART_ROOT","/apex/com.android.art");supplied.put("ANDROID_I18N_ROOT","/apex/com.android.i18n");
        supplied.put("ANDROID_TZDATA_ROOT","/apex/com.android.tzdata");supplied.put("ANDROID_RUNTIME_ROOT","/apex/com.android.runtime");
        supplied.put("BOOTCLASSPATH","/apex/com.android.art/javalib/core-oj.jar:/system/framework/framework.jar");
        supplied.put("DEX2OATBOOTCLASSPATH","/system/framework/framework.jar");
        ShellLaunchPlan plan=ShellLaunchPlan.prepare(temp.newFolder("files"),temp.newFolder("cache"),null,supplied);
        for(java.util.Map.Entry<String,String> entry:supplied.entrySet())
            assertTrue(Arrays.asList(plan.environment).contains(entry.getKey()+"="+entry.getValue()));
    }
    @Test public void runtimeAllowlistKeepsPrivatePathsAndDoesNotInheritArbitrarySettings() throws Exception {
        java.util.Map<String,String> supplied=new java.util.HashMap<>();
        for(String key:new String[]{"HOME","TMPDIR","PATH","ENV","PS1","CLASSPATH","LD_PRELOAD","SECRET_TOKEN"})supplied.put(key,"untrusted");
        ShellLaunchPlan plan=ShellLaunchPlan.prepare(temp.newFolder("files"),temp.newFolder("cache"),null,supplied);
        assertFalse(Arrays.toString(plan.environment).contains("untrusted"));
        assertTrue(Arrays.asList(plan.environment).contains("HOME="+plan.home));
        assertTrue(Arrays.asList(plan.environment).contains("ENV="+plan.startup));
        assertTrue(Arrays.asList(plan.environment).contains("ANDROID_ROOT=/system"));
        assertTrue(Arrays.asList(plan.environment).contains("ANDROID_DATA=/data"));
        assertFalse(Arrays.toString(plan.environment).contains("/apex/")); // No guessed APEX layout on old systems.
    }
    @Test public void exportActualRunStartedMarkerForHostShellExecution() {
        System.out.println("CODEPOCKET_SHELL_STARTED="+java.util.Base64.getEncoder().encodeToString(
            com.codepocket.ide.terminal.bridge.BridgeCommand.STARTED.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
