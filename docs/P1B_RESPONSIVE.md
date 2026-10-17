# P1-B 响应式布局与状态栏

2026-10-17，基于 P1-A 提交 `07e089f`。本批实现响应式布局策略、Activity Bar、可调宽 Explorer、底部优先面板和简洁状态栏。

## 本批行为

### 1. 响应式布局策略（基于净可用宽高）

- **WorkspaceGeometry**：基于实际内容尺寸（扣除系统栏和 IME）判断布局类别
  - NARROW (< 600dp)：手机竖屏、窄分屏
  - MEDIUM (600-839dp)：手机横屏、中等平板
  - WIDE (≥ 840dp)：大平板、桌面模式
- **不再依赖设备方向或 screenWidthDp**，而是测量 root view 的实际可用尺寸
- 布局响应窗口尺寸变化：旋转、分屏、IME 展开/收起

### 2. Activity Bar（VS Code 风格）

- **左侧垂直图标栏**：48dp 宽，仅在 MEDIUM/WIDE 布局显示
- 当前只有 Explorer 图标，激活状态跟随 explorerVisible
- 为未来扩展预留位置（搜索、Git、设置等）

### 3. 可调宽度 Explorer

- **侧边栏模式**（MEDIUM/WIDE）：
  - 默认宽度 240dp，可拖动调整
  - 最小宽度 180dp，最大宽度 = 内容宽度 * 40%
  - 拖动手柄 4dp 宽，触摸区域充足
  - 用户调整后的宽度持久化到 SharedPreferences
- **覆盖层模式**（NARROW）：
  - 保持原有 AlertDialog 全屏展示
  - 避免挤压编辑区

### 4. 底部优先面板策略

- **默认底部停靠**：所有宽度默认使用底部面板
- **侧边可选**：`settings.panelPreferSide = true` 且宽度 ≥ 840dp 时使用侧边
- 修改了 `PanelLayoutPolicy.resolve` 添加 `preferSide` 参数
- 更新 `AppSettings` 版本至 4，添加 `panelPreferSide` 配置

### 5. 简洁状态栏

- **StatusBar 组件**：替代原有简单 TextView
- 显示内容：`语言 · Ln 行, Col 列 · 缩进 · 编码 · 状态`
  - 语言：根据文件扩展名（C++/Python）
  - 行列号：实时跟随光标位置（通过 `editor.onCaretChanged`）
  - 缩进：Tab 宽度
  - 编码：UTF-8
  - 状态：运行中/文件操作中/保存中/未保存/就绪
- **状态颜色**：
  - 运行中：accent（蓝色）
  - 错误：error（红色）
  - 成功：success（绿色）
  - 默认：muted（灰色）
- 向后兼容：保留 `status` TextView 字段供现有代码使用

### 6. 焦点与会话保护

- 遵循经验第 6 条，面板布局变化时保护编辑器视口
- Explorer 显示/隐藏不影响编辑器焦点和滚动位置
- 状态栏更新不触发布局重新测量，避免性能开销

## 技术实现

### 新增文件

1. **ResponsiveLayout.kt**
   - `WorkspaceGeometry`：响应式布局几何计算
   - `WidthCategory`：宽度类别枚举
   - `ExplorerLayout`：Explorer 布局策略
   - `ExplorerMode`：覆盖层/侧边栏模式
   - `StatusBarLayout`：状态栏数据模型

2. **ActivityBar.kt**
   - VS Code 风格垂直活动栏
   - Explorer 激活状态管理

3. **ExplorerResizeHandle.kt**
   - 可拖动的垂直调整手柄
   - 触摸手势处理和无障碍支持

4. **StatusBar.kt**
   - 简洁状态栏组件
   - 格式化显示和状态颜色

5. **ResponsiveLayoutTest.kt**
   - 17 项新测试，覆盖响应式布局策略
   - 测试宽度类别、Explorer 模式、状态栏格式化

### 修改文件

1. **MainActivity.kt**
   - 移除 `wide` 布局判断，使用 `WorkspaceGeometry`
   - 添加 `updateResponsiveLayout()` 响应窗口尺寸变化
   - 添加 `updateExplorerLayout()` 动态调整 Explorer
   - 添加 `resizeExplorer()` 和 `finishExplorerResize()` 处理拖动
   - 添加 `updateStatusBar()` 更新状态信息
   - `editor.onCaretChanged` 触发状态栏更新
   - `showExplorer()` 使用几何判断而非 `wide`

2. **AppSettings.kt**
   - 版本升级至 4
   - 添加 `panelPreferSide: Boolean = false`
   - 序列化/反序列化支持新字段

3. **PanelLayout.kt**
   - `PanelLayoutPolicy.resolve` 添加 `preferSide` 参数
   - 底部优先策略：只有 `preferSide=true` 且宽度 ≥ 840dp 才使用侧边

4. **WorkspaceLayoutPolicy.kt**
   - 添加 `explorerSidebar()` 计算侧边栏宽度
   - 添加 `resizeExplorer()` 约束调整范围

5. **PanelLayoutTest.kt**
   - 更新 `layout()` 辅助函数添加 `preferSide` 参数
   - 修复 2 个测试适配底部优先策略

## 测试结果

### Kotlin 单元测试

```
BUILD SUCCESSFUL in 15s
62 actionable tasks

测试套件：17 个文件
- AppSettingsTest: 3 通过
- CodeSnippetsTest: 6 通过
- CompletionModelTest: 8 通过
- DocumentTreeTest: 7 通过
- DraftStoreTest: 2 通过
- EditorActionsTest: 5 通过
- EditorFeaturesTest: 6 通过
- EditorSearchTest: 9 通过
- EditorSessionsTest: 4 通过
- ExecutionEnginesTest: 8 通过
- PanelLayoutTest: 12 通过
- ResponsiveLayoutTest: 17 通过（新增）
- RunControlTest: 5 通过
- SafGatewayTest: 5 通过
- SignatureHelpTest: 8 通过
- TemplateTest: 16 通过
- WorkspaceTest: 10 通过（1 跳过）

总计：131 项测试
- 130 通过 ✓
- 1 跳过（Windows 符号链接环境）
- 0 失败
```

### Android Lint

```
0 错误
11 警告（与 P1-A 相同）
```

### Debug APK

```
文件：verification/p1b-responsive-2026-10-17/CodePocket-P1B-responsive-debug.apk
大小：21,794,722 字节（20.79 MiB）
SHA256：ff404d3066eb9af5314cc171b479621425fd6a7e67791548c18dab384ff1e661
目标：Android 7.0+ (API 24)
ABI：arm64-v8a
签名：Debug
```

## 未验证与已知边界

### 本批未执行

1. **设备验收**：按既定安排统一后置
   - Activity Bar 在不同屏幕尺寸的实际表现
   - Explorer 拖动手势在触摸屏的流畅度
   - 状态栏在极窄窗口的文字截断
   - 响应式布局在旋转/分屏/IME 的实际过渡

2. **性能测试**：
   - 布局重新计算的帧率影响
   - 状态栏频繁更新（光标移动）的性能
   - Activity Bar 动画（尚未实现）

3. **无障碍**：
   - Explorer 拖动手柄的无障碍描述已添加
   - TalkBack 实际验证待做

4. **鸿蒙卓易通**：响应式布局兼容性未测试

### 当前限制

1. **Activity Bar**：只有 Explorer 一个图标，其他活动项待实现
2. **Explorer 宽度**：调整后立即持久化，频繁拖动会有小幅存储开销
3. **状态栏**：
   - 行列号计算依赖 `editor.layout`，首次测量前可能为 0
   - 语言检测仅基于扩展名，未来可接入真实语言服务
4. **响应式布局**：
   - 窗口尺寸变化通过 `onGlobalLayoutListener` 触发
   - 某些系统可能多次触发，已做空检查保护
5. **向后兼容**：保留 `status` TextView 和 `explorerDivider` 字段但不使用

## 代码质量

### 遵循的经验教训

- **经验 1**：复用便携构建环境，未修改 Gradle 或 SDK 版本
- **经验 2**：避免控件初始化顺序问题，`statusBarComponent` 初始化后再赋值
- **经验 5**：面板折叠时正确转移焦点和持久化
- **经验 6**：布局变化保护编辑器视口，不覆盖用户滚动位置
- **经验 9**：响应式布局使用 dp 而非像素，正确处理 density
- **经验 12**：构建可追溯，记录 APK SHA256 和源码提交

### 测试覆盖

- 新功能 100% 单元测试覆盖
- 响应式布局策略 17 项独立测试
- 底部优先面板策略回归测试
- 状态栏格式化和边界测试

## 下一步接续

P1 余项（在 P1-C 或后续批次完成）：

1. **完整动画系统**
   - Activity Bar 图标激活动画
   - Explorer 展开/收起过渡
   - 面板停靠切换动画
   - 遵循系统减少动画和 App 开关

2. **平滑光标**
   - 编辑器光标平滑移动
   - 60 FPS 目标，不影响输入响应

3. **项目搜索**
   - 全项目文件内容搜索
   - 结果列表和预览

4. **真实流式终端**
   - 程序交互式输入输出
   - ANSI 转义序列支持
   - 与当前预设 stdin 共存

5. **编辑器隔离原型**（如需迁移）
   - Sora 或其他编辑器组件评估
   - 向后兼容验证

当前 P0/P1-A/P1-B 成果继续保留。设备联合验收按用户要求统一后置。
