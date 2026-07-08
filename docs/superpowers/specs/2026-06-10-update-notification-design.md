# 番剧更新通知功能设计

## 概述

根据番剧的 `airWeekday` 字段，在对应星期几的固定时间推送应用通知，告知用户今天有哪些番剧更新。

## 需求

- 仅通知「正在看」和「想看」状态的连载番剧
- 默认每天早上 9:00 推送
- 通知时间可配置（7:00/8:00/9:00/10:00/11:00/12:00）
- 设置界面添加"更新通知"入口卡片，点击进入二级设置页
- 开发者界面添加控制设置界面是否显示该入口的开关 + 测试通知按钮
- 总开关默认关闭

## 技术方案：WorkManager

选择 WorkManager 而非 AlarmManager：
- 与项目协程架构一致，无需额外权限
- 系统自动管理 Doze 模式、设备重启后重调度
- 通知场景不需要秒级精度，WorkManager 足够

依赖：`androidx.work:work-runtime-ktx`（需确认是否已引入）

## 核心组件

### 1. UpdateNotificationWorker

`CoroutineWorker`，WorkManager 每日触发：

```
doWork():
  1. 读取通知开关状态，未开启则返回 success
  2. 计算今天是周几（Calendar.DAY_OF_WEEK → Bangumi airWeekday 映射）
  3. 查询 AnimeDao：airWeekday == 今天 && status in (WATCHING, WANT_TO_WATCH) && !isFinished
  4. 结果为空则返回 success（不发通知）
  5. 调用 NotificationHelper 发送通知
  6. 调度下一次 Worker（确保每日持续触发）
  7. 返回 success
```

周几映射：Java Calendar.SUNDAY=1 → Bangumi airWeekday=1, MONDAY=2 → 2, ..., SATURDAY=7 → 7（已对齐）

### 2. NotificationHelper

工具 object：

- `createChannel(context)` — 创建 NotificationChannel（id=`anime_update`, name=`番剧更新通知`, importance=DEFAULT）
- `showUpdateNotification(context, animeList)` — 构建并发送通知
  - 标题：`今日番剧更新`
  - 内容：`今天有 N 部番剧更新：番剧A、番剧B、番剧C`（超过 5 部截断为"等 N 部"）
  - 点击 Intent → MainActivity
  - 小图标使用 app icon

### 3. UpdateNotificationManager

管理 Worker 调度的单例 object：

- `scheduleDailyNotification(context, hour)` — 取消旧 Worker，注册新的 PeriodicWorkRequest
  - 使用 `setInitialDelay` 计算到目标时间的延迟
  - 重复间隔 24 小时
- `cancelDailyNotification(context)` — 取消 Worker
- `triggerTestNotification(context)` — 立即执行一次 OneTimeWorkRequest（测试用）

### 4. 设置持久化（SettingsRepository）

新增 DataStore 键：

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `update_notification_enabled` | Boolean | false | 通知总开关 |
| `update_notification_hour` | Int | 9 | 通知时间（小时） |
| `update_notification_visible` | Boolean | false | 设置界面是否显示入口（开发者控制） |

### 5. UI 组件

#### SettingsScreen 修改
- 添加"更新通知"入口卡片（当 `updateNotificationVisible=true` 时显示）
- 点击导航到 `UpdateNotificationScreen`

#### UpdateNotificationScreen（新建）
- 总开关
- 通知时间选择（7:00-12:00，下拉选择）
- 开关状态变化时自动调度/取消 Worker

#### DeveloperScreen 修改
- 添加"更新通知"开关（控制设置界面入口可见性）
- 添加"测试通知"按钮（调用 `triggerTestNotification`）

### 6. 导航

- 新增 `Screen.UpdateNotification` 路由
- MainActivity 添加对应 NavHost composable 和过渡动画

## 数据流

```
用户开启通知开关
  → SettingsRepository 持久化
  → UpdateNotificationManager.scheduleDailyNotification()
  → WorkManager 注册 PeriodicWorkRequest

每日触发时间到达
  → UpdateNotificationWorker.doWork()
  → AnimeDao 查询当天更新番剧
  → NotificationHelper.showUpdateNotification()
  → 系统通知栏显示

用户关闭通知开关
  → UpdateNotificationManager.cancelDailyNotification()
  → WorkManager 取消 Worker
```

## 通知格式

- 1 部：`今天《番剧A》更新`
- 2-5 部：`今天有 N 部番剧更新：番剧A、番剧B、番剧C`
- 6+ 部：`今天有 N 部番剧更新：番剧A、番剧B、番剧C 等 N 部`

## 权限

Android 13+ 需要运行时请求 `POST_NOTIFICATIONS` 权限：
- 首次开启通知开关时检查并请求
- 用户拒绝则开关仍可开启但通知不会显示

## 文件清单

| 文件 | 操作 | 说明 |
|---|---|---|
| `data/notification/UpdateNotificationWorker.kt` | 新建 | WorkManager Worker |
| `data/notification/NotificationHelper.kt` | 新建 | 通知构建工具 |
| `data/notification/UpdateNotificationManager.kt` | 新建 | Worker 调度管理 |
| `data/SettingsRepository.kt` | 修改 | 新增 3 个 DataStore 键 |
| `ui/theme/ThemeViewModel.kt` | 修改 | 新增 StateFlow |
| `ui/settings/UpdateNotificationScreen.kt` | 新建 | 二级设置界面 |
| `ui/settings/SettingsScreen.kt` | 修改 | 添加入口卡片 |
| `ui/settings/DeveloperScreen.kt` | 修改 | 添加开关 + 测试按钮 |
| `MainActivity.kt` | 修改 | 添加导航路由 |
| `AndroidManifest.xml` | 修改 | 添加 POST_NOTIFICATIONS 权限 |
| `data/AnimeDao.kt` | 修改 | 新增查询方法 |
