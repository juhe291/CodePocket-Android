首次汇总在检查历史片段复用依据时，错误地读取了不存在的旧目录app/src/main/java/com/codepocket/ide/Snippets.kt，脚本以FileNotFoundError结束，尚未复制候选APK。实际源文件位于editor-assist/src/main/kotlin/com/codepocket/ide/CodeSnippets.kt。

已按实际路径检查CodeSnippets、SnippetSession和SnippetTest与旧有效源摘要相等，并把本轮JUnit真实生成的14份片段逐项按id/variant/code摘要与旧执行报告比较。保留全部APK、主回归、源和片段检查，没有因此重新构建APK或声称重跑旧Shell/片段主机程序。
