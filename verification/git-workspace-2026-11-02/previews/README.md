# 本地 Git 原生界面检查

来自 GitWorkbenchIntegrationTest 的实际 MainActivity、正式 Sora、SourceControlPanel 和原生 AlertDialog 绘制，使用固定的真实主机 Git 后端。不是网页样稿，也不是 Android 实机截图。Robolectric 的窗口定位和软键盘不能代替设备验收。

- phone-dark-staged.png：411dp 手机，活动栏固定、Git 与树/搜索共用抽屉，已暂存和未跟踪分组。
- phone-dark-commit.png：同一手机，提交姓名、邮箱和完整中文/emoji消息；保存版本先经原保存保护。
- tablet-light-untracked-diff.png：1280dp 平板浅色，真实未跟踪文件差异，640dp 阅读上限、全文选择/复制与关闭。
- phone-dark-no-project.png：无项目时给打开入口，不生成示例正文、不初始化 Git。
- phone-light-large-commit.png：360dp×640dp、1.5倍字体，提交表单滚动和底部操作可达。

五张图已逐张打开检查。集成断言核对真实暂存/取消暂存/提交、草稿重建和取消、迟到差异不关闭设置窗口；实际 SAF 二进制事务、独立服务与锁恢复另有定向用例。实机 IME、系统返回、Android 服务执行和性能仍后置。
