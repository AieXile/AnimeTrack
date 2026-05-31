# 关于界面重构设计

## 目标

重构 `AboutScreen`，采用居中图标式布局，展示开发者信息、社交链接和版本信息，遵循 Material Design 3 规范，使用项目已有 M3 色彩体系，确保充足呼吸感。

## 布局结构

```
┌─────────────────────────┐
│      TopAppBar          │
│  ← 关于                 │
├─────────────────────────┤
│                         │
│     (flex weight 上)    │
│                         │
│        [头像 88dp]       │
│         20dp            │
│        AieXile          │
│         6dp             │
│   一款简洁的番剧追踪应用   │
│                         │
│     (flex weight 下)    │
│                         │
│   🐙     💬     ✈️      │
│  GitHub  QQ群  TG群     │
│      20dp padding       │
├ ─ ─ ─ outlineVariant ─ ─┤
│      AnimeTrack         │
│   v0.3.5 · Build 14    │
│      [检查更新]          │
│   32dp bottom           │
└─────────────────────────┘
```

上半区（flex weight 居中）：头像 → 名字 → 介绍，垂直居中。
社交图标：紧跟分割线上方，不参与居中，padding 24dp。
下半区（分割线下方）：应用名 → 版本号 → 检查更新，固定在底部。

## 组件规格

### 头像

- 尺寸：88dp × 88dp
- 形状：CircleShape
- 边框：3dp `colorScheme.primary`
- 光晕：`colorScheme.primary.copy(alpha=0.3f)` shadow（elevation 效果）
- 图片资源：`R.drawable.my_avatar`（已有）
- ContentScale：Crop

### 名字

- 文字：AieXile
- 样式：`headlineMedium` + `FontWeight.Bold`
- 颜色：`colorScheme.onSurface`
- 对齐：居中

### 一句话介绍

- 文字：一款简洁的番剧追踪应用
- 样式：`bodyMedium`
- 颜色：`colorScheme.onSurfaceVariant`
- 对齐：居中

### 社交图标区

三个等距图标按钮，水平居中排列，间距 24dp：

| 项目 | 图标 | 点击行为 | 长按行为 |
|------|------|---------|---------|
| GitHub | Icons.Default.Code（已有 Material Icon） | 跳转浏览器 `https://github.com/AieXile/AnimeTrack` | 复制链接到剪贴板 |
| QQ群 | 自定义 Vector Asset | 跳转浏览器 QQ 群加群链接 | 复制群号 `951059178` 到剪贴板 |
| TG群 | Icons.Default.Send（Material Icon 近似） | 跳转浏览器 `https://t.me/AnimeTrackovo` | 复制链接到剪贴板 |

每个图标按钮规格：
- 容器尺寸：52dp × 52dp
- 容器形状：RoundedCornerShape(16dp)
- 容器背景：`colorScheme.surfaceContainerLow`
- 图标尺寸：24dp
- 图标颜色：`colorScheme.onSurface`
- 标签文字：`labelSmall`
- 标签颜色：`colorScheme.onSurfaceVariant.copy(alpha=0.5f)`

### 版本区

- 分割线：1dp `colorScheme.outlineVariant`
- 内边距：horizontal 24dp, vertical 20dp，底部 32dp
- 应用名：`titleMedium` + `FontWeight.SemiBold` + `colorScheme.onSurface`
- 版本信息：`labelMedium` + `colorScheme.onSurfaceVariant.copy(alpha=0.5f)`
  - 格式：`v{VERSION_NAME} · Build {VERSION_CODE}`
- 检查更新按钮：`FilledTonalButton`
  - 文字：检查更新
  - onClick：`updateViewModel.checkForUpdate(force = true)`

## 交互行为

### 社交链接点击

- **GitHub**：`uriHandler.openUri("https://github.com/AieXile/AnimeTrack")`
- **QQ群**：`uriHandler.openUri("https://qun.qq.com/universal-share/share?ac=1&authKey=...")`，长按复制群号 `951059178`
- **TG群**：`uriHandler.openUri("https://t.me/AnimeTrackovo")`

### 社交链接长按

- 复制对应内容到剪贴板（群号或链接）
- 显示 Snackbar/Toast 提示"已复制"

### 检查更新

- 复用现有 `UpdateViewModel` 和 `UpdateDialog`

## 色彩规范

全部使用 `MaterialTheme.colorScheme` token，不自定义颜色：

| 元素 | Token |
|------|-------|
| 头像边框 | `primary` |
| 头像光晕 | `primary.copy(alpha=0.3f)` |
| 名字 | `onSurface` |
| 介绍 | `onSurfaceVariant` |
| 图标容器背景 | `surfaceContainerLow` |
| 图标颜色 | `onSurface` |
| 图标标签 | `onSurfaceVariant.copy(alpha=0.5f)` |
| 分割线 | `outlineVariant` |
| 应用名 | `onSurface` |
| 版本号 | `onSurfaceVariant.copy(alpha=0.5f)` |
| 检查更新按钮 | `FilledTonalButton`（自动使用 primaryContainer/onPrimaryContainer） |

## 实现范围

- 仅修改 `AboutScreen.kt`
- 保留现有 `UpdateViewModel` 和 `UpdateDialog` 逻辑
- 需要添加 QQ 的 Vector Asset 图标文件（GitHub 用 Icons.Default.Code，TG 用 Icons.Default.Send）
- 需要在 `MainActivity.kt` 中传入 `context`（用于剪贴板和 Intent 跳转）

## 不做的事

- 不新增自定义颜色
- 不修改主题系统
- 不修改 TopAppBar 样式（保持与其他设置子页面一致）
