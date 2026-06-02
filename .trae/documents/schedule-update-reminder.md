# 新番更新提醒功能实现计划

## 概述
在看板页面实现两个独立的更新提醒功能，并在功能设置页面添加控制开关。

---

## 步骤一：SettingsRepository 新增两个设置项

**文件**: `app/src/main/java/com/aiexile/animetrack/data/SettingsRepository.kt`

新增两个 DataStore 键和对应的 Flow/setter：

- `SHOW_UPDATE_BANNER_KEY` (`booleanPreferencesKey("show_update_banner")`) — 控制功能一（顶部 Banner 提醒）是否显示，默认 `true`
- `SHOW_CALENDAR_BUTTON_KEY` (`booleanPreferencesKey("show_calendar_button")`) — 控制功能二（右上角日历按钮）是否显示，默认 `true`

对应新增：
```kotlin
val showUpdateBanner: Flow<Boolean>
suspend fun setShowUpdateBanner(show: Boolean)

val showCalendarButton: Flow<Boolean>
suspend fun setShowCalendarButton(show: Boolean)
```

---

## 步骤二：ThemeViewModel 新增两个 StateFlow + setter

**文件**: `app/src/main/java/com/aiexile/animetrack/ui/theme/ThemeViewModel.kt`

新增两个 `stateIn` StateFlow 及对应 setter，与现有模式一致：

```kotlin
val showUpdateBanner: StateFlow<Boolean> = settingsRepository.showUpdateBanner
    .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = true)
fun setShowUpdateBanner(show: Boolean) { viewModelScope.launch { settingsRepository.setShowUpdateBanner(show) } }

val showCalendarButton: StateFlow<Boolean> = settingsRepository.showCalendarButton
    .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = true)
fun setShowCalendarButton(show: Boolean) { viewModelScope.launch { settingsRepository.setShowCalendarButton(show) } }
```

---

## 步骤三：FeaturesScreen 新增"看板"分组

**文件**: `app/src/main/java/com/aiexile/animetrack/ui/settings/FeaturesScreen.kt`

在现有"观看"和"界面"分组之间，新增一个 **"看板"** 分组，包含两个 SwitchItem：

| 标题 | 描述 | 绑定 |
|------|------|------|
| 今日更新提醒 | 在看板顶部显示今日番剧更新提醒卡片 | `showUpdateBanner` / `setShowUpdateBanner` |
| 日程预览按钮 | 在看板右上角显示日历按钮，查看今明日更新 | `showCalendarButton` / `setShowCalendarButton` |

需要从 ScheduleScreen 传入 `themeViewModel`（或通过已有的方式获取），读取这两个 StateFlow。

---

## 步骤四：ScheduleViewModel 新增今日/明日番剧数据

**文件**: `app/src/main/java/com/aiexile/animetrack/ui/schedule/ScheduleViewModel.kt`

新增以下状态：

1. `todayWeekday` 已存在（Calendar 计算的当前星期几 1-7）
2. 新增 `tomorrowWeekday`：`(todayWeekday % 7) + 1`
3. 新增 `todayUpdateCount: StateFlow<Int>` — 从 `groupedAnimes` 中取 `todayWeekday` 对应列表的 size
4. 新增 `todayAnimes: StateFlow<List<Anime>>` — 今日更新的番剧列表（按 airDate 排序）
5. 新增 `tomorrowAnimes: StateFlow<List<Anime>>` — 明日更新的番剧列表（按 airDate 排序）

实现方式：将 `groupedAnimes` 改为 `MutableStateFlow`，在数据更新时同时派生 `todayUpdateCount`、`todayAnimes`、`tomorrowAnimes`。或用 `map` 组合 Flow 派生。

---

## 步骤五：ScheduleScreen 实现功能一 — 顶部 Banner 提醒

**文件**: `app/src/main/java/com/aiexile/animetrack/ui/schedule/ScheduleScreen.kt`

### 5.1 接收 themeViewModel

`ScheduleScreen` 签名新增 `themeViewModel: ThemeViewModel? = null` 参数（与 HomeScreen 一致的模式）。

### 5.2 Banner 状态管理

- 读取 `themeViewModel.showUpdateBanner` 判断功能是否启用
- 读取 `viewModel.todayUpdateCount` 判断今日是否有更新
- 本地 `remember { mutableStateOf(true) }` 记录用户本次是否手动关闭

### 5.3 Banner UI 组件

`AnimatedVisibility` 包裹的 Banner 卡片：

```
AnimatedVisibility(visible = showUpdateBanner && todayUpdateCount > 0 && !bannerDismissed)
```

卡片样式：
- `Surface(color = primaryContainer, shape = RoundedCornerShape(12.dp))`
- 内部 `Row`：左侧文本 `"✨ 今日有 X 部番剧更新"`，右侧 `IconButton(Icons.Default.Close)` 关闭
- 水平 padding 16.dp，垂直 padding 12.dp
- `modifier = Modifier.fillMaxWidth()`

### 5.4 Banner 插入位置 — 放入 HorizontalPager 页面内部

**关键设计决策**：Banner 不放在 WeekdaySelector 和 HorizontalPager 之间（那样关闭时会导致整个 Pager 布局跳动），而是放入 HorizontalPager 对应"今天"页面的 `AnimeCoverGrid` 内部作为第一个 item。

具体实现：
- 修改 `AnimeCoverGrid`，新增参数 `showBanner: Boolean`、`bannerDismissed: Boolean`、`onDismissBanner: () -> Unit`
- 在 `LazyVerticalGrid` 中，当 `showBanner && !bannerDismissed` 时，第一个 item 为 Banner 卡片（`span = maxLineSpan` 独占整行）
- 后续 items 为正常的番剧封面卡片
- 这样关闭 Banner 时只会影响当前页面列表的滚动位置，不会造成整体布局硬跳动

---

## 步骤六：ScheduleScreen 实现功能二 — 日历按钮 & 今明日程抽屉

**文件**: `app/src/main/java/com/aiexile/animetrack/ui/schedule/ScheduleScreen.kt`

### 6.1 TopAppBar 改造

将现有的自定义 `Column` topBar 改为使用 M3 `TopAppBar`，在 `actions` 区域添加日历图标按钮：

- `Icons.Default.CalendarMonth`
- 仅当 `showCalendarButton == true` 时显示
- 点击触发 `showScheduleSheet = true`

同时保留原有的标题和副标题布局（在 `title` slot 中用 `Column` 包裹）。

### 6.2 ModalBottomSheet 状态

```kotlin
val scheduleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
var showScheduleSheet by remember { mutableStateOf(false) }
```

### 6.3 抽屉内容

使用 `ModalBottomSheet`，内部为 `LazyColumn`（可滚动），包含：

1. **今天更新** `SettingsGroup`：
   - 标题："今天更新"
   - 副标题："周{weekdayLabel}"
   - 内容：遍历 `viewModel.todayAnimes`，每项用简洁 Row 显示番剧名和 airDate
   - 如果列表为空，居中显示灰色提示文字 "暂无更新番剧"

2. **明天更新** `SettingsGroup`：
   - 标题："明天更新"
   - 副标题："周{weekdayLabel}"
   - 内容：遍历 `viewModel.tomorrowAnimes`，每项用简洁 Row 显示番剧名和 airDate
   - 如果列表为空，居中显示灰色提示文字 "暂无更新番剧"

3. 底部留 16.dp 间距

### 6.4 抽屉关闭逻辑

- 点击遮罩层关闭
- `onDismissRequest = { showScheduleSheet = false }`
- 使用 `rememberCoroutineScope` + `sheetState.hide()` 动画关闭后设 `showScheduleSheet = false`

---

## 步骤七：MainActivity 传入 themeViewModel

**文件**: `app/src/main/java/com/aiexile/animetrack/MainActivity.kt` (L382-384)

当前调用：
```kotlin
"schedule" -> ScheduleScreen(onAnimeClick = { animeId ->
    onNavigateToDetail(animeId, null)
})
```

改为：
```kotlin
"schedule" -> ScheduleScreen(
    onAnimeClick = { animeId -> onNavigateToDetail(animeId, null) },
    themeViewModel = themeViewModel
)
```

`MainPagerContent` 函数签名中已有 `themeViewModel` 参数，无需额外修改。

---

## 涉及文件清单

| 文件 | 改动类型 |
|------|----------|
| `SettingsRepository.kt` | 新增 2 个设置项 |
| `ThemeViewModel.kt` | 新增 2 个 StateFlow + setter |
| `FeaturesScreen.kt` | 新增"看板"分组 + 2 个 SwitchItem |
| `ScheduleViewModel.kt` | 新增 todayUpdateCount/todayAnimes/tomorrowAnimes |
| `ScheduleScreen.kt` | Banner 卡片 + 日历按钮 + ModalBottomSheet 抽屉 |
| `MainActivity.kt` | ScheduleScreen 调用处传入 themeViewModel |
