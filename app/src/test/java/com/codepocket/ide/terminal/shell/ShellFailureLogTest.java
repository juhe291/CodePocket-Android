package com.codepocket.ide.terminal.shell;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ShellFailureLogTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    @Test public void savesActualCauseAndOverwritesOlderFailure() throws Exception {
        File files = temporary.newFolder();
        ShellFailureLog.write(files, "test platform", new IllegalStateException("first failure"));
        ShellFailureLog.write(files, "test platform", new NullPointerException("renderer"));
        String report = ShellFailureLog.read(files);
        assertTrue(report.contains("NullPointerException: renderer"));
        assertTrue(report.contains("ShellFailureLogTest.savesActualCause"));
        assertFalse(report.contains("first failure"));
        assertEquals(1, files.list().length);
    }
    @Test public void oversizedUnicodeFailureRemainsBounded() throws Exception {
        File files = temporary.newFolder();
        ShellFailureLog.write(files, "test", new IllegalStateException("中文故障".repeat(50000)));
        assertTrue(new File(files, ShellFailureLog.NAME).length() <= ShellFailureLog.MAX_BYTES);
        assertTrue(ShellFailureLog.read(files).endsWith("[report truncated]"));
    }
    @Test public void readDoesNotLoadAnOversizedFileEntirely() throws Exception {
        File files = temporary.newFolder();
        try (FileOutputStream out = new FileOutputStream(new File(files, ShellFailureLog.NAME))) {
            out.write("x".repeat(ShellFailureLog.MAX_BYTES * 2).getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(ShellFailureLog.MAX_BYTES, ShellFailureLog.read(files).length());
    }
    @Test public void missingRecordIsExplicitRatherThanInventingOldCrashEvidence() throws Exception {
        assertTrue(ShellFailureLog.read(temporary.newFolder()).contains("不包含旧版本"));
    }
}
