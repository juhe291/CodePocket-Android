package com.codepocket.ide

import java.util.concurrent.Executors

/** Serializes saving, draft checkpoints, provider mutations and mirror initialization across Activity recreation. */
object WorkspaceIo { val executor = Executors.newSingleThreadExecutor() }
