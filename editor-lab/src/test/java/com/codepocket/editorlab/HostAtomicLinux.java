package com.codepocket.editorlab;

import android.system.ErrnoException;
import android.system.OsConstants;
import java.io.IOException;
import java.nio.file.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLinux;

/** Robolectric 4.14 has no Linux.rename implementation. Execute a real host atomic rename
 * instead of allowing its unimplemented native method to silently leave the old file in place.
 * Test-only bridge; no substitute for validating Android POSIX rename on a device. */
@Implements(className="libcore.io.Linux", minSdk=26, isInAndroidSdk=false)
public class HostAtomicLinux extends ShadowLinux {
    @Implementation protected void rename(String source, String target) throws ErrnoException {
        try { Files.move(Paths.get(source), Paths.get(target), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException error) { throw new ErrnoException("rename", OsConstants.EIO, error); }
    }
}
