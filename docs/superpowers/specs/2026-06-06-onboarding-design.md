# 首次安装引导页设计文档

## 概述

为 AnimeTrack 添加首次安装引导页，4 页左右滑动引导界面，使用 Compose 原生组件搭建微缩高保真功能预览，结合 DataStore 持久化 `isFirstLaunch` 状态控制导航分发。

## 数据层

### SettingsRepository 新增

```kotlin
private val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")

val isFirstLaunch: Flow<Boolean> = context.dataStore.data
    .map { preferences -> preferences[IS_FIRST_LAUNCH_KEY] ?: true }

suspend fun setFirstLaunchCompleted() {
    context.dataStore.edit { preferences ->
        preferences[IS_FIRST_LAUNCH_KEY] = false
    }
}
```

### ThemeViewModel 新增

```kotlin
val isFirstLaunch: StateFlow<Boolean> = settingsRepository.isFirstLaunch
    .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = true)

fun completeFirstLaunch() {
    viewModelScope.launch { settingsRepository.setFirstLaunchCompleted() }
}
```

## 导航层

### Screen sealed class 新增

```kotlin
data object Onboarding : Screen()
```

### AnimeTrackApp 导航逻辑

- 启动时读取 `themeViewModel.isFirstLaunch`，若为 `true` 则 `currentScreen = Screen.Onboarding`，否则 `Screen.Main()`
- `Screen.Onboarding` 不注册 BackHandler（用户不可按返回键退出引导）
- 点击「立即开启」后：调用 `themeViewModel.completeFirstLaunch()` → `currentScreen = Screen.Main()`
- 过渡动画：Onboarding → Main 使用 `fadeIn + fadeOut`（300ms）

## OnboardingScreen 组件结构

```
OnboardingScreen
├── HorizontalPager (4 pages, userScrollEnabled = true)
│   ├── Page 1: 追踪番剧
│   ├── Page 2: 多平台同步
│   ├── Page 3: 数据备份与看板
│   └── Page 4: 个性定制
├── PagerIndicator（长条 + 圆点指示器）
└── BottomBar（跳过 + 下一步/立即开启）
```

### 每页布局

- 上方约 60%：Compose 原生组件搭建的微缩高保真功能预览
- 下方约 40%：大标题（headlineMedium, SemiBold）+ 描述文案（bodyMedium, onSurfaceVariant）

### 底部操作栏

- 左侧：「跳过」文字按钮（TextButton），最后一页隐藏
- 中间：Pager 指示器 — 当前页为长条形（24×8dp, primary），其余为圆点（8×8dp, outline）
- 右侧：「下一步」FilledTonalButton / 「立即开启」FilledButton（最后一页）

### 交互行为

- 左右滑动切换页面
- 点击「下一步」→ `pagerState.animateScrollToPage(nextPage)`
- 点击「跳过」→ `pagerState.animateScrollToPage(lastPage)`
- 点击「立即开启」→ 写入 `isFirstLaunch=false`，`currentScreen = Screen.Main()`
- 指示器可点击跳转到对应页

## 微缩高保真预览实现

每页用 Compose 原生组件搭建简化版界面预览，整体使用 `graphicsLayer { scale = 0.85f; alpha = 0.9f }` 营造微缩预览的极客工业美感。

### Page 1: 追踪番剧 — 微缩 HomeScreen

| 元素 | 组件 |
|------|------|
| 番剧封面占位 | Card + Box(渐变背景) |
| 标题占位 | 灰色圆角矩形 |
| 观看进度 | LinearProgressIndicator |
| 集数标签 | Text |
| 底部导航 | Row + 圆形图标占位 |

### Page 2: 多平台同步 — 微缩登录页

| 元素 | 组件 |
|------|------|
| B站/Bangumi 图标 | 圆形渐变 Box + 文字 |
| 连接状态点 | Box(8dp, 绿色/灰色) |
| 同步进度 | CircularProgressIndicator + 文字 |
| 已同步标签 | Chip / Surface 圆角 |

### Page 3: 数据备份与看板 — 微缩看板页

| 元素 | 组件 |
|------|------|
| 星期选择器 | FilterChip 横向排列 |
| 番剧列表项 | Row + 方形封面占位 + 文字占位 + 状态标签 |
| WebDAV 备份状态 | Surface(绿色背景) + 图标 + 文字 |

### Page 4: 个性定制 — 微缩设置页

| 元素 | 组件 |
|------|------|
| 主题色选择器 | 圆形色块 Row（5-6 色，当前选中带边框） |
| 导航样式对比 | 两个小预览卡片（底部导航 vs 胶囊导航） |
| 问候语预览 | Row + Emoji + 打字效果占位 |

## 文案

| 页面 | 标题 | 描述 |
|------|------|------|
| P1 | 追踪你的番剧 | 记录观看进度，管理追番状态，不再忘记看到哪一集 |
| P2 | 多平台同步 | 一键导入 B站 与 Bangumi 追番列表，自动同步观看进度 |
| P3 | 数据备份与看板 | WebDAV 云端备份守护数据，番剧时间表不再错过更新 |
| P4 | 个性定制 | 主题配色、导航样式、问候语，打造专属追番体验 |

## 文件变更清单

| 操作 | 文件 | 变更内容 |
|------|------|---------|
| 新建 | `ui/onboarding/OnboardingScreen.kt` | 引导页完整 Composable |
| 修改 | `data/SettingsRepository.kt` | +IS_FIRST_LAUNCH_KEY, +isFirstLaunch Flow, +setFirstLaunchCompleted() |
| 修改 | `ui/theme/ThemeViewModel.kt` | +isFirstLaunch StateFlow, +completeFirstLaunch() |
| 修改 | `MainActivity.kt` | +Screen.Onboarding, 启动导航判断, 过渡动画, OnboardingScreen 渲染 |

## 边界情况

- DataStore 首次读取 `isFirstLaunch` 时 Flow 还未发射，`stateIn` 初始值为 `true`，确保引导页优先显示
- 用户在引导页中旋转屏幕：HorizontalPager 自动保持页面状态
- 引导页不可通过返回键退出，必须点击「跳过」或「立即开启」
- 「立即开启」后 `isFirstLaunch` 写入 false，后续启动直接进入 Main
