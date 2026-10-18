# Pinned Termux component port

Version: v0.118.3, commit 5b657c6adf4304e5198951ce815fe0205dcac29c.
Original source hashes and Git blobs are in UPSTREAM.json. Root LICENSE.md documents GPLv3-only with Apache-2.0 source exceptions; both license texts are retained. Do not relabel the whole source subset as Apache.

Local Gradle Kotlin scripts use CodePocket SDK35/Build Tools35/Java17/min24/arm64 and fixed NDK27.2.12479018. The upstream Gradle scripts remain beside the modules as *-upstream.gradle. Five upstream source/config files have recorded changes: public ParcelFileDescriptor duplication instead of private fd reflection/System.exit; cancelled-before-layout PID0 protection; JNI cwd-release/error/waitpid repairs; 16KB ELF alignment; manifests use namespace from Gradle. Exact patch is in verification/native-terminal-2026-10-18/upstream-port.patch.

All original upstream tests remain. The src/test Android utility bridges implement the specific Log/Base64/Color operations needed on a JVM. They do not mock terminal rendering, View, IME, PTY or Shell, and are not runtime sources. Do not enable unitTests.returnDefaultValues globally.

CodePocket documentation and verification explicitly separate host tests/build checks from Android runtime validation. The terminal uses /system/bin/sh, not a bundled Termux userland or on-device compiler.

## Live terminal integration (2026-10-18)

`codepocket-port.patch` is the tracked cumulative source patch against the archive pinned in UPSTREAM.json. It includes the earlier source port plus CodePocket hooks: invisible OSC 133 prompt boundaries, terminal input/output observation, foreground PTY process-group query, private Python-service process-group helpers, external cloud-result bytes appended to the mature emulator, paused scrolling while browsing history, scroll-offset retention on resize and middle-click paste routed through the session owner. Original UPSTREAM.json and license bytes remain provenance, not claims that patched files are byte-identical to upstream. Separately obtained GNU/Apache license files are retained and not treated as entries in the upstream ZIP.

All 145 original emulator cases remain; ShellIntegrationTest adds fragmented OSC markers and invisible prompt parsing. App tests exercise actual emulator/View host paths without invoking Android PTY, app_process, LocalSocket/SCM_RIGHTS or Chaquopy. Those runtime paths require device acceptance. No full editor migration or extra userland/compiler is included.
