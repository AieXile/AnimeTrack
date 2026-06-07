# 主界面添加本地搜索按钮功能

## Summary
在主界面右上角添加搜索按钮，点击后顶部栏替换为搜索输入框，可按标题搜索已添加的番剧。按钮仅在用户已有番剧且当前过滤结果非空时显示，并提供设置开关控制是否出现。

## Current State Analysis
- **顶部栏布局**（HomeScreen.kt L233-255）：Row 布局，左侧 TypingGreeting，右侧当 `fabLocation == TOP_BAR` 时显示添加按钮（IconButton + Add icon）
- **过滤逻辑**（HomeScreen.kt L180-182）：`filteredAnimeList` 由 `animeList` + `selectedFilter` 计算得出；`animeList.isEmpty()` 判断无番剧（L307）；`filteredAnimeList.isEmpty()` 但 `animeList` 非空表示过滤无结果（L1203）
- **现有过滤方法**（HomeViewModel.kt L449-480）：`getFilteredAnimeList` 按 AnimeFilter 枚举过滤，基于内存列表 filter
- **设置模式**：SettingsRepository (booleanPreferencesKey) → ThemeViewModel (StateFlow + setter) → Screen (collectAsState)

## Proposed Changes

### 1. SettingsRepository.kt — 添加搜索按钮开关
- 新增 `SHOW_SEARCH_BUTTON_KEY = booleanPreferencesKey("show_search_button")`
- 新增 `showSearchButton: Flow<Boolean>`（默认 true）
- 新增 `setShowSearchButton(show: Boolean)` setter

### 2. ThemeViewModel.kt — 添加搜索按钮 StateFlow
- 新增 `showSearchButton: StateFlow<Boolean>`（默认 true）
- 新增 `setShowSearchButton(show: Boolean)` setter

### 3. FeaturesScreen.kt — 添加开关 UI
- 新增独立 SettingsGroup（title: "搜索"），包含 SwitchItem：
  - title: "搜索按钮"
  - description: "在主界面右上角显示搜索按钮"
  - 绑定 `showSearchButton` / `setShowSearchButton`

### 4. HomeViewModel.kt — 添加本地搜索状态和逻辑
- 在 HomeUiState 中新增 `localSearchQuery: String = ""`
- 新增 `updateLocalSearchQuery(query: String)` 更新搜索词
- 修改 `getFilteredAnimeList`：新增 `searchQuery` 参数（默认空字符串），在现有 AnimeFilter 过滤后，若 searchQuery 非空则进一步按标题模糊匹配过滤（忽略大小写）
- 新增 `clearLocalSearch()` 清空搜索词

### 5. HomeScreen.kt — 添加搜索按钮和搜索栏 UI
- 收集 `showSearchButton` 状态：`val showSearchButton by (themeViewModel?.showSearchButton?.collectAsState() ?: mutableStateOf(true))`
- 新增 `isSearchMode` 状态控制顶部栏模式切换
- 搜索按钮显示条件：`showSearchButton && animeList.isNotEmpty() && filteredAnimeList.isNotEmpty() && !isSearchMode`
- 在顶部栏 Row 中，TypingGreeting 右侧添加搜索按钮：
  - 当 `fabLocation == TOP_BAR` 时：搜索按钮在添加按钮**左边**
  - 当 `fabLocation != TOP_BAR` 时：搜索按钮单独显示在右侧
- 按钮样式：IconButton + `Icons.Default.Search`，tint 为 primary，size 26.dp
- 点击搜索按钮后：`isSearchMode = true`，顶部栏替换为搜索输入框
- 搜索栏 UI：Row 布局，左侧 `Icons.Default.Search` 图标 + TextField（placeholder: "搜索番剧"），右侧清除按钮（有内容时显示）+ 关闭按钮
- 关闭搜索栏时：清空搜索词，`isSearchMode = false`，恢复原始顶部栏

### 6. HomeScreen.kt — filteredAnimeList 逻辑调整
- 当前 `filteredAnimeList` 在 L180 计算：`viewModel.getFilteredAnimeList(animeList, uiState.selectedFilter)`
- 改为：`viewModel.getFilteredAnimeList(animeList, uiState.selectedFilter, uiState.localSearchQuery)`
- 将 `localSearchQuery` 传入 `getFilteredAnimeList`，在 AnimeFilter 过滤后再按标题模糊匹配

## UI 交互流程
1. 用户点击搜索按钮 → 顶部栏替换为搜索输入框，自动聚焦弹出键盘
2. 用户输入文字 → 实时过滤当前列表（在 AnimeFilter 基础上叠加标题搜索）
3. 用户点击关闭按钮 → 清空搜索词，恢复原始顶部栏

## Assumptions & Decisions
- 搜索为纯客户端内存过滤，不涉及数据库查询
- 搜索匹配规则：标题包含搜索词（忽略大小写）
- 开关放在 FeaturesScreen 的独立"搜索"SettingsGroup 中
- 默认开启搜索按钮
- 搜索栏替换顶部栏（类似微信/Telegram 交互），关闭后恢复欢迎语

## Verification
- 构建成功
- 无番剧时搜索按钮不显示
- 过滤到空结果时搜索按钮不显示
- 开关关闭时搜索按钮不显示
- 添加按钮在右上角时，搜索按钮在其左边
- 添加按钮在右下角时，搜索按钮在右上角单独显示
- 搜索栏输入文字后实时过滤列表
- 关闭搜索栏后恢复原始列表
