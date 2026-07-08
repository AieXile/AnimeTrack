# Bilibili 自动同步功能设计

## 概述

在现有 Bilibili 手动同步基础上，新增「打开 App 自动同步」功能。仅拉取方向（B站→本地），仅同步连载中番剧和已完结还在看的番剧，减少不必要的网络请求和数据库写入。

## 需求

- 打开 App（冷启动）时自动从 B站拉取追番更新
- 仅同步两类番剧：连载中（`isFinish=0`）和已完结在看（`isFinish=1 && followStatus=2`）
- 同步方向：仅拉取（B站→本地），不推送本地修改
- 触发条件：冷启动 + 已登录 + 开关开启 + 距上次同步 > 1 小时
- UI 提示：与现有 Banner 样式一致，3 秒后自动消失（向上滑出动画）
- 设置入口：Bilibili 登录页，已登录状态下显示自动同步开关

## 架构

方案：ViewModel 层驱动。HomeViewModel 在初始化时检查条件并触发同步，通过 StateFlow 暴露同步状态给 HomeScreen。

### 数据流

```
HomeViewModel.init
  → 检查条件（已登录 + 开关开启 + 间隔 > 1h）
  → BilibiliSyncManager.fetchFollowList()（IO 线程）
  → 客户端过滤：isFinish=0 || (isFinish=1 && followStatus=2)
  → BilibiliSyncManager.syncSelectedItems(filteredItems)
  → 更新 autoSyncState StateFlow
  → HomeScreen 读取状态显示 Banner
  → 3 秒后 autoSyncState → Idle（向上滑出动画）
```

### 触发条件（全部满足）

1. Bilibili 已登录（`bilibiliAuthManager.isLoggedIn`）
2. 自动同步开关开启（`bilibiliAutoSync`）
3. 距上次同步 > 1 小时（`lastSyncTime + 3600000 < System.currentTimeMillis()`）
4. 冷启动（ViewModel 使用 `isFirstLoad` 标志位确保只触发一次）

### 过滤逻辑

B站 API `/x/space/bangumi/follow/list` 不支持服务端按 isFinish/followStatus 过滤，因此拉取全量列表后客户端过滤：

- `isFinish == 0` → 连载中，保留
- `isFinish == 1 && followStatus == 2` → 已完结但在看，保留
- 其余跳过（已完结已看完、已弃番、计划观看等）

## UI 设计

### 同步状态

```kotlin
sealed class AutoSyncState {
    object Idle : AutoSyncState()
    object Syncing : AutoSyncState()
    data class Completed(val count: Int) : AutoSyncState()
    data class Failed(val message: String) : AutoSyncState()
}
```

### Banner 展示

复用现有 `AnimeGridHeaderState` 位置，优先级高于"今日更新"Banner：

| 状态 | 显示内容 | 持续时间 |
|------|---------|---------|
| Syncing | "正在同步追番数据..."（带转圈） | 直到完成/失败 |
| Completed(count) | "已同步 N 部番剧更新" | 3 秒后消失 |
| Failed(message) | "同步失败" | 3 秒后消失 |
| Idle | 原有"今日有 X 部番剧更新"Banner | 不变 |

消失动画：向上滑出（与出现时的滑入对应）。

### 设置开关

位置：BilibiliLoginScreen，已登录状态下，同步按钮下方。

- 标题："自动同步"
- 描述："打开 App 时自动同步连载中和在看番剧的更新"
- Switch 绑定 `bilibiliAutoSync`

## 错误处理

- **未登录**：不触发，静默跳过
- **网络错误**：`Failed` 状态，Banner 显示"同步失败"3 秒后消失，不影响 App 正常使用
- **B站 API 异常**：`Failed` 状态 + `Log.e` 日志打印
- **API 限流**：同网络错误处理，不重试
- **同步期间用户操作**：同步在 IO 线程执行，不阻塞 UI；结果写入数据库后 Flow 自动刷新列表
- **与手动同步冲突**：手动同步更新 `lastSyncTime`，自动同步检查间隔时自然避免重复

## 文件变更

| 文件 | 变更 |
|------|------|
| `BilibiliSyncManager.kt` | 新增 `fetchAndSyncFiltered()` 方法 |
| `HomeViewModel.kt` | 新增 `AutoSyncState`、`autoSyncState: StateFlow`、`triggerAutoSync()` |
| `HomeScreen.kt` | Banner 区域增加自动同步状态展示 |
| `AnimeGridHeaderState` | 新增 `autoSyncState` 字段 |
| `AnimeGrid` composable | Banner 渲染逻辑增加自动同步状态分支 |
| `BilibiliAuthManager.kt` | 新增 `bilibiliAutoSync: Flow<Boolean>` 和 `setBilibiliAutoSync()` |
| `BilibiliLoginScreen.kt` | 已登录状态下添加自动同步开关 |

## 存储设计

`bilibiliAutoSync` 存储在 `bilibili_auth` DataStore（与登录状态同存储），退出登录时不清除此开关。

```kotlin
private val BILIBILI_AUTO_SYNC_KEY = booleanPreferencesKey("bilibili_auto_sync")

val bilibiliAutoSync: Flow<Boolean> = context.bilibiliAuthDataStore.data
    .map { preferences -> preferences[BILIBILI_AUTO_SYNC_KEY] ?: false }

suspend fun setBilibiliAutoSync(enabled: Boolean) {
    context.bilibiliAuthDataStore.edit { preferences ->
        preferences[BILIBILI_AUTO_SYNC_KEY] = enabled
    }
}
```
