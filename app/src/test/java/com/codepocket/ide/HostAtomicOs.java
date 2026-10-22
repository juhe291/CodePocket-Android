package com.codepocket.ide;

import android.system.Os;
import android.system.ErrnoException;
import android.system.OsConstants;
import java.io.IOException;
import java.nio.file.*;
import org.robolectric.annotation.*;

/** Host-only real atomic rename. Shadow the static entry, not a Linux instance cached by other test configurations. */
@Implements(Os.class)
public class HostAtomicOs {
    @Implementation protected static void rename(String source, String target) throws ErrnoException {
        try { Files.move(Paths.get(source), Paths.get(target), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException error) { throw new ErrnoException("rename", OsConstants.EIO, error); }
    }
}
