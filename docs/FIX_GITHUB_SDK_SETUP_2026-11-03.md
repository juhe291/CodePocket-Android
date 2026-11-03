# 现有 GitHub 工作流的 SDK 准备输入

45facf9已正常推送到公开仓库juhe291/CodePocket-Android/master，SHA已精确核对。真实Linux CI41586306486执行159项Python测试全部通过（47.944秒），此前目录变文件的ENOTDIR回归和原真实TLS/Git转换均通过。随后Setup Android SDK失败，尚未进入Kotlin、Lint或Android构建；不能说远端APK通过。

实际日志：android-actions/setup-android@v3调用cmdline-tools/16.0的sdkmanager，报告Failed to find package 'tools'后退出1。官方[action.yml](https://raw.githubusercontent.com/android-actions/setup-android/v3/action.yml)声明packages默认值为tools platform-tools；[官方说明](https://github.com/android-actions/setup-android/tree/v3#additional-packages)允许显式指定附加包。

本次仅给原有Setup Android SDK步骤添加packages: platform-tools，避开当前runner找不到的legacy tools。后续原sdkmanager仍显式安装platforms;android-35、build-tools;35.0.0与ndk;27.2.12479018。原JDK/Python/Gradle、任务、触发、权限、超时、许可处理与产物上传均保持；没有新建工作流、自动任务、手动调度或Release。未触发的editor-lab工作流保持，其相同默认输入问题登记后续处理。

原工作流和完整失败日志（含原始压缩副本）保存在verification/github-sdk-setup-fix-2026-11-03。补丁精确核对仅这两行配置输入；生产和本机APK未变，不重复本机构建。当前本地有效包仍为CodePocket-git-directory-status-fix-2026-11-03-debug.apk，SHA256 36c76a0b0a38909ee4d8c8b06267ab2af229d1a8b6607ec4506f125571d4e8d1。

正常追加推送后核对既有CI，真实状态记录在本地本批push-results.json。不把主机完整验证或Linux159项通过当成远端Android构建/实机验收，C/D原任务继续保留。
