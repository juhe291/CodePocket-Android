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
}
