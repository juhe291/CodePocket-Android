# CodePocket Sora production adapter

Java Android library used by the existing `:app` workbench. It is not an application and owns no project files, grants, settings or execution sessions.

`WorkbenchEditor` exposes Android/Java and `EditorSurface` types. Sora 0.23.7 and TextMate are implementation dependencies, so the main Kotlin 1.9 compiler never reads their Kotlin 2 metadata. Main compile stdlib is 1.9.25; runtime stdlib remains 2.1.21. API24 uses desugar_jdk_libs 2.1.5. Metadata validation is enabled.

The seven adapter classes retain their historical `com.codepocket.editorlab` package for existing same-package tests. The production host facade is in this library; no experimental Activity or file controller is included in the main APK. Assets and licenses moved here without changing upstream grammar/license bytes.

Call `close()` before replacing the host. It closes navigation, releases Sora and then disposes grammar registries. Late loader completion disposes its bundle. Document loading and background selection must not request editor focus. Keep composing transactions enabled; Sora disallowSuggestions commits preedit text and must not be used as an IME hint.

The main owner validates identity/revision/source/selection before explicit edits and retains the existing save/draft/SAF controllers. TextMate provides syntax, the main shared providers provide lexical completion/snippets/built-in signatures; semantic LSP is not implemented. See [I1 report and remaining I2/I3 checks](../docs/MAIN_APP_SORA_INTEGRATION_2026-10-22.md).

2026-10-22：SoraDecorations通过公开Spans/Span扩展叠加主程序词法角色色、括号分层与匹配背景，按Content身份/版本防止旧偏移生效，保持TextMate原Span只读并缓存合并行。主程序与原生内部回归共用EditorPalette；这不是语义分析。新增8项主工作台和6项共享层用例，见[I2首批报告](../docs/MAIN_APP_EDITOR_FEEDBACK_2026-10-22.md)。普通输入硬限制仍待处理，文件与草稿上限保持。
