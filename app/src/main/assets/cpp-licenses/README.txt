CodePocket C++ 静态语义分析：clangd 21.1.8

本组件仅在设备本地分析，不执行项目代码，不上传代码。
分析与原有 C++（Wandbox）运行、Python 运行及 PTY 分开。
默认分析参数为 C++17、aarch64-linux-android24 和 Android NDK 头文件；
第三方库、项目自定义编译参数及其他目标平台暂不保证正确。

来源及固定版本：
https://packages.termux.dev/apt/termux-main/
https://github.com/termux/termux-packages
clang / libllvm 21.1.8-3（LLVM Apache-2.0 WITH LLVM-exception 等，保留完整通知）
libc++ 30（Android NDK r30 来源，保留 LLVM 及 NDK 通知）
libffi 3.8（MIT）、libxml2 2.15.4-1（MIT）、zlib 1.3.2（zlib）
zstd 1.5.7-1（采用库的 BSD 选项；未分发 GPL 命令行工具）
libiconv 1.19（库 LGPL 2.1 或更新版本；未分发 iconv 命令行程序）
ndk-sysroot 30（头文件的各自许可见四份 Android NDK r30 通知）
这些依赖的许可分别保留，不以项目自身许可替代。

完整对应源码随同 APK 提供：
APK 是 ZIP 格式；提取 assets/cpp-source/libiconv-1.19-source.tgz，
其中包含完整未修改的 libiconv 1.19 源码、COPYING.LIB、配置和构建材料。
原始归档 SHA256：88dd96a8c0464eca144fc791ae60cd31cd8ee78321e67397e25fc095c4a19aa6
https://mirrors.kernel.org/gnu/libiconv/libiconv-1.19.tar.gz
相应 Termux 构建脚本也位于 assets/cpp-source/；原始包无额外 libiconv 补丁。

与原包相比，CodePocket 只修改 ELF 动态字符串：私有共享库名、DT_NEEDED、
DT_SONAME 和 $ORIGIN RUNPATH；不修改机器代码，不静态链接 LGPL 库。
原始/修改后摘要、映射和完整依赖版本在 assets/cpp-source/cpp-runtime-lock.json；
APK 实际库摘要在 assets/cpp-runtime/manifest.json。

修改与重新链接：
你可以修改、替换 LGPL 库，并为调试这些修改进行逆向工程。
使用 Android NDK 为 arm64-v8a/API24 构建共享 libiconv.so（16KiB 段对齐），
SONAME 为 libiconv.so，系统依赖限 libc/libm/libdl；完整对应修改源码保存为 tar 归档。
在 CodePocket 源码仓库中运行：
python scripts/prepare_cpp_runtime.py --iconv-library <libiconv.so> --iconv-source <完整源码.tar.gz>
然后按仓库 README 的构建方法构建 App。脚本会改写私有库名和实际清单，并将你
提供的完整修改源码加入 APK。保留原有签名密钥即可正常覆盖你自己的旧安装。
不要为了替换库清除用户项目或恢复草稿。

也可直接重新打包 APK：使用所附 cpp_runtime_elf.py 的 patch_dynamic 和锁文件
aliases 映射修补你自己的 libiconv.so，替换 lib/arm64-v8a/ 下对应私有库；更新
assets/cpp-runtime/manifest.json 中该文件的 bytes/sha256，替换对应源码资产及其
清单项，重新 zipalign 和签名。签名后再次核对 ZIP 完整性、ABI 和 16KiB 对齐。
Android 要求覆盖安装使用同一签名；使用不同密钥时请自行保留项目和草稿。

许可证全文保留在同目录单独条目。软件及依赖按各自许可证无担保提供。
