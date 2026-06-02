# WebDAV 双备份策略同步功能实现计划

## 概述
实现支持"易读 JSON"和"离线 ZIP 完整包"双备份策略，以及"覆盖"和"兼容"双恢复模式的 WebDAV 同步功能。将现有的导入/导出 Markdown 功能整合到一个新的"数据管理"页面中。

---

## 步骤一：添加 WebDAV 依赖

**文件**: `gradle/libs.versions.toml` + `app/build.gradle.kts`

使用 `com.github.thegrizzlylabs:sardine-android` 库（轻量级 WebDAV 客户端，基于 OkHttp，与项目现有 OkHttp 生态兼容）。

- `libs.versions.toml` 新增：`sardine = "0.9"` + library 条目
- `build.gradle.kts` 新增：`implementation(libs.sardine)`
- `settings.gradle.kts` 新增 JitPack 仓库（如需要）

---

## 步骤二：新增 DataStore 配置项

**文件**: `SettingsRepository.kt`

新增以下 DataStore 键和 Flow/setter：

| 键名 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `webdav_url` | String | `""` | WebDAV 服务器地址 |
| `webdav_username` | String | `""` | 用户名 |
| `webdav_password` | String | `""` | 密码 |
| `webdav_backup_strategy` | Int | `0` | 0=JSON, 1=ZIP |
| `webdav_restore_mode` | Int | `0` | 0=覆盖, 1=兼容 |
| `webdav_last_sync_time` | Long | `0L` | 上次同步时间戳（System.currentTimeMillis()） |

**关键修正**：`webdav_last_sync_time` 使用 `longPreferencesKey` 存储 `Long` 类型时间戳，而非 String。UI 层渲染时通过 `SimpleDateFormat` 转换为本地化时间字符串，避免跨时区/语言切换时解析崩溃。

对应新增 Flow + suspend setter。

---

## 步骤三：ThemeViewModel 新增 WebDAV 配置 StateFlow

**文件**: `ThemeViewModel.kt`

新增与 SettingsRepository 对应的 StateFlow + setter，供 UI 读取和修改。

---

## 步骤四：创建 BackupManager 核心逻辑

**文件**: 新建 `app/src/main/java/com/aiexile/animetrack/data/backup/BackupManager.kt`

### 4.1 backup 函数

```kotlin
suspend fun backup(context: Context, strategy: Int): File
```

**JSON 策略 (strategy == 0)**：
1. 从 AnimeDao 查出所有番剧 `List<Anime>`
2. 用 `GsonBuilder().setPrettyPrinting().create().toJson(animes)` 序列化
3. 写入临时文件 `AnimeTrack_Backup.json`
4. 返回该 File

**ZIP 策略 (strategy == 1)**：
1. 强制 WAL checkpoint：`appDatabase.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")`
2. 获取数据库文件路径：`context.getDatabasePath("anime_database")`
3. 获取封面目录：`File(context.filesDir, "anime_covers")`
4. 用 `ZipOutputStream` 打包：
   - 添加 `anime_database` 文件
   - 如果 covers 目录存在且非空，遍历添加 `covers/` 下的所有文件
5. 写入临时文件 `AnimeTrack_Backup.zip`
6. 返回该 File

### 4.2 restore 函数

```kotlin
suspend fun restore(context: Context, strategy: Int, mode: Int, file: File): RestoreResult
```

**处理封面（仅 ZIP 策略）**：
- 解压 zip 中的 `covers/` 条目到 `context.filesDir/anime_covers/`

**处理数据**：

JSON 策略：
- 读取文件内容，用 Gson 反序列化为 `List<Anime>`

ZIP 策略（**关键修正**）：
- 解压 zip 中的 `anime_database` 到临时位置
- **不使用 Room 打开临时数据库**，而是使用 Android 原生 `SQLiteDatabase.openDatabase(tempPath, null, SQLiteDatabase.OPEN_READONLY)` 以只读模式打开
- 用原始 `cursor.query("anime", null, null, null, null, null, null)` 查出所有行
- 逐行读取 cursor 列值，手动构建 `Anime` 对象（与 Anime 实体的字段一一对应）
- 关闭 cursor 和 SQLiteDatabase
- 删除临时数据库文件
- 这样既轻量，又绝对不会引发 Room 的连接池冲突或 Migration 不一致崩溃

**覆盖模式 (mode == 0)**：
- 在 Room 事务中：清空本地表 → 全量插入备份列表
- 使用 `insertAnimes(animes)` 批量插入

**兼容模式 (mode == 1)**：
- 在 Room 事务中遍历备份列表
- 对每条番剧，用 `getAnimeByBangumiId(bangumiId)` 查重
- 如果本地已有相同 bangumiId，跳过（保留本地进度）
- 如果 bangumiId 为 null，用 `getAnimeByTitle(title)` 查重
- 只插入本地没有的番剧

### 4.3 RestoreResult

```kotlin
data class RestoreResult(
    val totalCount: Int,
    val insertedCount: Int,
    val skippedCount: Int
)
```

### 4.4 cursor → Anime 转换辅助函数

```kotlin
private fun cursorToAnime(cursor: Cursor): Anime
```

按 Anime 实体字段顺序从 cursor 读取：
- `id` → `cursor.getInt(cursor.getColumnIndexOrThrow("id"))`
- `title` → `cursor.getString(...)`
- `totalEpisodes` → `cursor.getInt(...)`
- `watchedEpisodes` → `cursor.getInt(...)`
- `status` → `AnimeStatus.valueOf(cursor.getString(...))`
- `rating` → `cursor.getFloat(...)` (nullable)
- `notes` → `cursor.getString(...)`
- `startDate` → `cursor.getLong(...)` (nullable)
- `finishDate` → `cursor.getLong(...)` (nullable)
- `coverUrl` → `cursor.getString(...)` (nullable)
- `airDate` → `cursor.getString(...)` (nullable)
- `summary` → `cursor.getString(...)` (nullable)
- `bangumiId` → `cursor.getInt(...)` (nullable，用 `isNull` 判断)
- `airWeekday` → `cursor.getInt(...)` (nullable)
- `isFinished` → `cursor.getInt(...) != 0`
- `currentEpisodes` → `cursor.getInt(...)`
- `hasNewUpdate` → `cursor.getInt(...) != 0`

---

## 步骤五：创建 WebDAVClient 传输层

**文件**: 新建 `app/src/main/java/com/aiexile/animetrack/data/backup/WebDAVClient.kt`

封装 Sardine 库的 WebDAV 操作：

```kotlin
object WebDAVClient {
    private const val REMOTE_DIR = "/AnimeTrack/"

    suspend fun upload(url: String, username: String, password: String, file: File): Result<Unit>
    suspend fun download(url: String, username: String, password: String, destFile: File): Result<Unit>
    suspend fun checkConnection(url: String, username: String, password: String): Result<Boolean>
    suspend fun getLastModified(url: String, username: String, password: String): Result<Long?>
    suspend fun remoteFileExists(url: String, username: String, password: String): Result<Boolean>
}
```

- 所有操作在 `Dispatchers.IO` 中执行
- `upload`：先确保 `/AnimeTrack/` 目录存在（MKCOL），再 PUT 文件
- `download`：GET 文件写入 destFile
- 文件名根据策略：`AnimeTrack_Backup.json` 或 `AnimeTrack_Backup.zip`

---

## 步骤六：创建 DataManageScreen 页面

**文件**: 新建 `app/src/main/java/com/aiexile/animetrack/ui/settings/DataManageScreen.kt`

### 页面布局

使用 `LazyColumn` + `SettingsGroup` 共享组件，分为以下分组：

**1. 导入与导出**（整合现有 Markdown 功能）
- "导入 Markdown" — 点击弹出导入引导 BottomSheet
- "导出番剧数据" — 点击导出 Markdown 文件

**2. WebDAV 同步** SettingsGroup
- URL 输入框（OutlinedTextField）
- 用户名输入框
- 密码输入框（PasswordVisualTransformation）
- "测试连接" 按钮 — 调用 `WebDAVClient.checkConnection()`
- "备份策略" 单选行：JSON / 完整 ZIP（RadioButton）
- "恢复模式" 单选行：覆盖本地 / 兼容合并（RadioButton）
- "立即备份" 按钮 — 调用 BackupManager.backup → WebDAVClient.upload
- "立即恢复" 按钮 — 调用 WebDAVClient.download → BackupManager.restore
- 上次同步时间显示（Long 时间戳 → SimpleDateFormat 本地化渲染）

### Loading 状态

- 备份/恢复/测试连接时显示全屏 Loading 遮罩（CircularProgressIndicator + 提示文字）
- 操作完成后显示 Snackbar 反馈结果

---

## 步骤七：创建 DataManageViewModel

**文件**: 新建 `app/src/main/java/com/aiexile/animetrack/ui/settings/DataManageViewModel.kt`

```kotlin
class DataManageViewModel : ViewModel {
    // WebDAV 凭证本地状态（不直接绑定 DataStore）
    var webdavUrl by mutableStateOf("")
    var webdavUsername by mutableStateOf("")
    var webdavPassword by mutableStateOf("")
    var backupStrategy by mutableStateOf(0)
    var restoreMode by mutableStateOf(0)

    // UI 状态
    var isLoading by mutableStateOf(false)
    var loadingMessage by mutableStateOf("")
    var snackbarMessage by mutableStateOf<String?>(null)

    // 初始化时从 DataStore 读取一次
    fun loadConfig(themeViewModel: ThemeViewModel) { ... }

    // 仅在操作时持久化到 DataStore
    fun saveConfig(themeViewModel: ThemeViewModel) { ... }

    // 操作
    fun testConnection() { saveConfig(); ... }
    fun backupNow() { saveConfig(); ... }
    fun restoreNow() { saveConfig(); ... }

    // Markdown 导入导出逻辑（从 SettingsViewModel 迁移）
}
```

**关键修正**：输入框的 value 绑定在 ViewModel 本地的 `mutableStateOf` 中，不直接写入 DataStore。只有当用户点击"测试连接"、"立即备份"、"立即恢复"或离开页面时，才一次性调用 `saveConfig()` 写入 DataStore。避免键盘输入时频繁磁盘 I/O 导致掉帧。

---

## 步骤八：导航集成

**文件**: `MainActivity.kt` + `SettingsScreen.kt`

1. `Screen` sealed class 新增 `data object DataManage`
2. `settingsSubPages` 集合新增 `Screen.DataManage::class`
3. `SettingsScreen` 中将"导入 Markdown"和"导出番剧数据"两项替换为一条"数据管理"入口
4. `MainPagerContent` 中处理 `Screen.DataManage` 路由，渲染 `DataManageScreen`
5. 传入 `themeViewModel` 参数

---

## 涉及文件清单

| 文件 | 改动类型 |
|------|----------|
| `gradle/libs.versions.toml` | 新增 sardine 依赖 |
| `app/build.gradle.kts` | 新增 sardine implementation |
| `settings.gradle.kts` | 新增 JitPack 仓库（如需要） |
| `SettingsRepository.kt` | 新增 6 个 WebDAV 配置项 |
| `ThemeViewModel.kt` | 新增对应 StateFlow + setter |
| `data/backup/BackupManager.kt` | 新建 — 核心备份/恢复逻辑 |
| `data/backup/WebDAVClient.kt` | 新建 — WebDAV 传输层 |
| `ui/settings/DataManageScreen.kt` | 新建 — 数据管理页面 UI |
| `ui/settings/DataManageViewModel.kt` | 新建 — 页面 ViewModel |
| `SettingsScreen.kt` | 替换导入/导出为"数据管理"入口 |
| `MainActivity.kt` | 新增 Screen.DataManage 路由 |
