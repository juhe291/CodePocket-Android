package com.codepocket.ide.terminal.shell;

import android.app.Application;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class ShellSessionRegistryTest {
    private Application application;
    @Before public void setUp(){ ShellSessionRegistry.finishAll();application=RuntimeEnvironment.getApplication(); }
    @After public void tearDown(){ ShellSessionRegistry.finishAll(); }
    @Test public void switchingProjectsPreservesOldCwdAndNewShellGetsCurrentProject() {
        File a=new File(application.getFilesDir(),"projectA"),b=new File(application.getFilesDir(),"projectB");
        ShellSessionRegistry r=ShellSessionRegistry.get(application,a);var first=r.catalog.selected();var second=r.create(b);
        assertEquals(a,first.value.directory);assertEquals(b,second.value.directory);
        assertNotSame(first.value.runtime,second.value.runtime);
        r.catalog.select(first.id);assertSame(first,r.catalog.selected());
        assertEquals(ShellRuntime.State.EMPTY,first.value.runtime.state()); // No PTY/JNI is invoked by navigation.
    }
    @Test public void recreatingUiUsesTheSameSessionCatalog() {
        ShellSessionRegistry first=ShellSessionRegistry.get(application,null);var selected=first.create(null);
        assertSame(first,ShellSessionRegistry.get(application,new File("ignored")));
        assertSame(selected,ShellSessionRegistry.get(application,null).catalog.selected());
    }
    @Test public void endingOneSessionLeavesOtherRuntimeAlone() {
        ShellSessionRegistry r=ShellSessionRegistry.get(application,null);var a=r.catalog.selected();var b=r.create(null);
        assertTrue(r.end(a.id));assertFalse(r.end(a.id));assertSame(b,r.catalog.selected());assertEquals(ShellRuntime.State.EMPTY,b.value.runtime.state());
    }
    @Test public void reopenAfterFinishDoesNotReuseShutdownExecutor() {
        ShellSessionRegistry before=ShellSessionRegistry.get(application,null);ShellRuntime runtime=before.catalog.selected().value.runtime;
        ShellSessionRegistry.finishAll();ShellSessionRegistry next=ShellSessionRegistry.get(application,null);
        assertNotSame(before,next);assertNotSame(runtime,next.catalog.selected().value.runtime);
    }
}
