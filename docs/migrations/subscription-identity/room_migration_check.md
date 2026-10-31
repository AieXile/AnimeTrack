# Room 27 → 28 迁移检查（订阅身份重构）

> 目的：在发版前用真实设备/模拟器数据库验证 `MIGRATION_27_28` 与 `serverSubscriptionId` 列，
> 确保升级后旧待删除墓碑既不丢失、也不会被补偿重试误删其他番剧。
> 相关代码：`app/src/main/java/com/aiexile/animetrack/data/AnimeDatabase.kt`（`MIGRATION_23_24`、`MIGRATION_26_27`、`MIGRATION_27_28`）。

## 1. 预期结构

### 1.1 `anime` 表

```sql
-- 服务端权威订阅 ID（26→27 新增，可空）
PRAGMA table_info(anime);        -- 应存在 serverSubscriptionId，类型 TEXT，notnull=0
PRAGMA index_list(anime);        -- 原有索引不变
```

### 1.2 `pending_deletions`（27→28 重建）

```sql
PRAGMA table_info(pending_deletions);
```

预期列：

| cid | name | type | notnull | dflt_value | pk |
|---|---|---|---|---|---|
| 0 | id | INTEGER | 1 | NULL | 1 |
| 1 | animeId | TEXT | 1 | NULL | 0 |
| 2 | keyType | TEXT | 1 | NULL | 0 |
| 3 | groupKey | TEXT | 1 | NULL | 0 |
| 4 | ownerKey | TEXT | 1 | NULL | 0 |
| 5 | createdAt | INTEGER | 1 | NULL | 0 |

预期索引：

```sql
PRAGMA index_list(pending_deletions);
-- index_pending_deletions_ownerKey_animeId   (UNIQUE)
-- index_pending_deletions_ownerKey_groupKey  (非唯一)
```

## 2. 迁移数据规则（必须逐条核对）

`MIGRATION_27_28` 的判定原则是「宁可残留不可误删」：

| 旧 `animeId` 形态 | 新 `keyType` | 补偿重试行为 |
|---|---|---|
| 纯数字（如 `123`、`0`） | `LEGACY_LOCAL` | 跳过，不会被当成 Bangumi ID 误删其他番 |
| UUID（8-4-4-4-12，GLOB 匹配） | `LEGACY_UUID` | 依次按 `subscriptionId` / `remoteSyncId` 精确尝试 |
| 其他字符串 | `LEGACY_LOCAL` | 跳过，由 TTL 清理 |

`groupKey` 全部为 `'migrated:' || id`，保证旧数据每行独立成组，不会跨番剧整组清理。

迁移后校验：

```sql
SELECT keyType, COUNT(*) FROM pending_deletions GROUP BY keyType;
SELECT COUNT(*) FROM pending_deletions WHERE groupKey NOT LIKE 'migrated:%';  -- 期望 0
SELECT COUNT(*) FROM pending_deletions WHERE typeof(createdAt) <> 'integer';  -- 期望 0
```

## 3. 验证步骤

1. 用 **27 版本 App**（上一发布版本）登录并制造一批旧墓碑：
   - 断网后删除 1 部有 `bangumiId` 的番剧、1 部只有 `remoteSyncId` 的番剧；
   - 重启一次，确认删除未成功、墓碑仍在。
2. 覆盖安装本分支 APK（Room 28），启动后立即确认：
   - `PRAGMA user_version;` 返回 `28`；
   - `pending_deletions` 行数与升级前一致（id 保留）；
   - `keyType` 分布符合 §2；
   - `index_pending_deletions_ownerKey_groupKey` 存在。
3. 恢复网络并触发同步（下拉刷新 / 冷启动），确认：
   - 旧墓碑按组补偿成功后被清理，日志出现「补偿处理服务端订阅 N 组」；
   - 其他番剧没有被误删（重点检查 `bangumiId` 与旧本地 id 同值的番剧）。
4. 打开网页端核对：上述两部番剧在服务端已删除，App 列表无残留卡片；
5. 在网页重新添加其中一部，App 同步后卡片恢复且进度保留。

## 4. 取数据库方式

```powershell
# 模拟器（Google APIs 镜像）或已 root 的测试机
adb shell "run-as com.aiexile.animetrack cat databases/animetrack_db > /sdcard/animetrack_db"
adb pull /sdcard/animetrack_db .\animetrack_db
sqlite3 .\animetrack_db "PRAGMA user_version; PRAGMA table_info(pending_deletions);"
```

若设备不可 `run-as`，可用 Android Studio App Inspection → Databases 直接导出。

## 5. 失败处理

- 若 `user_version` 未变成 28：检查 `RoomDatabase.Builder` 是否注册了全部 `MIGRATIONS`（见 `AnimeDatabase.kt` 的 `MIGRATIONS` 数组）；
- 若 `pending_deletions` 缺列/缺索引：不要手动改表，回到迁移实现修正后重新验证；
- 若出现非预期的 `LEGACY_UUID`（例如把标题误判为 UUID）：检查 GLOB 模式是否被改动；
- 任何数据异常先保留设备数据库副本，再回滚到 27 版本验证可恢复性。
