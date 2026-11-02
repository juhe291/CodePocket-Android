# 已查看的原生表单图

来自真实GitRemoteUiIntegrationTest的Main/Sora与Android原生表单，集中流程再次生成后逐张查看。最终摘要在git-remote-delivery-2026-11-03/results.json。

- phone-dark-clone.png：411×800，手机深色克隆、HTTPS地址/分支、内部/授权空目录选择。
- tablet-light-fetch.png：1100×780，平板浅色获取表单，最大宽520dp，目标分支与仅获取说明。
- compact-large-credentials.png：360×640，1.5倍字体凭据表单，令牌掩码，默认不记住，关闭/清除/使用按钮。

字段和操作未见重叠/裁切，表单内容可滚动。Robolectric原生窗口合成不代表设备WindowManager实际居中、IME遮挡、Sora底层画面或触摸验收。两张首轮失败图另存ui-first-previews，不作为最终图；其中旧令牌销毁问题有测试失败并已修复。
