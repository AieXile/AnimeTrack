# 订阅身份体系重构规格（Subscription Identity v2）

| 项      | 内容                                                                                                     |
| ------ | ------------------------------------------------------------------------------------------------------ |
| 文档状态   | 待评审（未开始实施）                                                                                             |
| 日期     | 2026-10-29                                                                                             |
| 影响范围   | 服务端 `.trae/server/api/`、网页 `.trae/server/update-site/`、App `app/src/main/java/com/aiexile/animetrack/` |
| 关联现象   | 网页登录后 89 部番剧变成 178 部，多出一份「数据相同但集数为 0」的幽灵卡                                                              |
| 现有临时收敛 | `server.js` 第 1753–2119 行的 P0/P1（已改完、尚未部署）                                                             |
| 实施原则   | 功能与 UI 不变、用户数据零丢失、旧客户端零报错、全程可回滚                                                                        |

***

## 0. 结论摘要

幽灵卡不是网页渲染 bug，也不是某一版 App 的 bug，而是**服务端缺少「订阅」这一实体的权威身份**：

- 唯一身份字段 `user_subscriptions.anime_id` 同时承载了「Room 本地自增 id」「客户端生成的 UUID」「Bangumi 数字 ID」三种语义；

- 数据库唯一约束只有 `(user_id, anime_id)`，只能防同一个键重复，防不了同一部番用不同键并存；

- `remoteSyncId` 是**每台设备各自** **`UUID.randomUUID()`** **生成**的（`AnimeRepository.kt:1224-1230`），跨设备并不唯一；

- App 在展示层用「bangumiId → remoteSyncId → 标题」合并隐藏了重复，网页直接渲染接口数组，于是重复被完整暴露。

**永久解法**：服务端引入 `subscription_id` 作为唯一订阅身份，所有历史键/设备键进 `subscription_aliases`，配合数据库唯一约束、删除墓碑、单一写入入口与 API v2。客户端不再自行判断「哪两张卡是同一部番」，网页也不需要再写合并逻辑。

***

## 1. 现状与证据

### 1.1 现象链路

```
设备 A：番剧「X」无 bangumiId → 生成 UUID-A → 上传 → 服务端行 A
设备 B：同一部「X」无 bangumiId → 生成 UUID-B → 上传 → 服务端行 B
（全站 227 组 UUID+UUID 同标题重复即由此产生）

设备 A：补全 Bangumi → 改用数字 ID N 上传 → 服务端行 N
        旧 UUID-A 行未删 → 数字行 N + UUID 行 A 并存
（UUID 行通常无 totalEpisodes/watchedEpisodes → 0 集数幽灵卡）

App：SubscriptionMerger 按 bangumiId → remoteSyncId → 标题合并，UI 正常
网页：GET /api/subscriptions/list 原样渲染，一张行 = 一张卡 → 幽灵卡暴露
```

### 1.2 数据证据（2026-10 巡检）

| 项                         | 数值                |
| ------------------------- | ----------------- |
| 用户 1 订阅行数 / 去重后标题数        | 149 行 / 85 标题     |
| 按 P0 规则收敛后                | 88 行              |
| 用户 1 数字+数字同标题重复           | 3 组（亚人、链锯人、约会大作战） |
| 全站 UUID+UUID 同标题重复对       | 227 组，且双方都有进度数据   |
| 用户 1 数字行 0 集数但 UUID 旧行有集数 | 0 组（P1 事务测试通过）    |

### 1.3 代码证据

| 位置                                | 事实                                              | 影响                          |
| --------------------------------- | ----------------------------------------------- | --------------------------- |
| `server.js:1874-2002`             | `/subscriptions/add` 以 `anime_id` 为唯一键 upsert   | 不同键 = 不同订阅                  |
| `server.js:1810-1858`             | P0 `dedupeSubscriptionsByTitle` 只折叠「数字行 + 非数字行」 | 数字+数字、UUID+UUID、改名均覆盖不到     |
| `server.js:1860-1871`             | `subscription_key_migrations` 记录 old→new 映射     | 是补丁表，不是身份表                  |
| `AnimeRepository.kt:1224-1230`    | `remoteSyncId = UUID.randomUUID()`              | 每台设备各自生成，跨设备不唯一             |
| `AnimeRepository.kt:1343-1425`    | 全量同步上传只传 `animeId`，不带 `supersededAnimeIds`      | 旧 UUID 行必然残留（P1 之前）         |
| `AnimeRepository.kt:1390`         | 上传用 `buildSubscribeRequest(anime, animeId)`     | 单条编辑与全量同步行为不一致              |
| `SubscriptionMerger.kt:27-31`     | 三级匹配 + 迁移映射 + 进度指纹兜底                            | 重复只在客户端被隐藏                  |
| `SubscriptionMerger.kt:125-137`   | 标题作为最后一级身份                                      | 改名/同名不同季会误判                 |
| `update-site/index.php:2459-2462` | 网页直接拉 `/subscriptions/list`                     | 无任何本地缓存/合并                  |
| `update-site/index.php:2639-2692` | 一行渲染一张卡                                         | 服务端重复 = 网页重复                |
| `update-site/index.php:2734`      | 侧栏计数 = `allAnime.length`                        | 重复会被计入总数                    |
| `Anime.kt:15-25`                  | 本地唯一索引只有 `bangumiId`、`tmdbId`                   | `remoteSyncId` 可重复          |
| `AnimeDatabase.kt:16`             | Room `version = 26`                             | 无 `serverSubscriptionId` 字段 |

***

## 2. 根因分层

| 层级    | 问题                         | 为什么补丁修不完                                    |
| ----- | -------------------------- | ------------------------------------------- |
| 身份模型  | 一个字段承载三种 ID 语义             | 任何按格式猜测都不可靠（数字既可能是 Bangumi ID，也可能是旧本地自增 id） |
| 数据库约束 | 唯一键是 `(user_id, anime_id)` | 同一部番不同键 = 两条合法行                             |
| 身份生成  | 客户端自行生成 `remoteSyncId`     | 两台设备离线添加同一部番必然得到两个 UUID                     |
| 去重位置  | 去重写在客户端展示层                 | 每新增一个客户端（网页/iOS/新 UI）都要重写一遍，漏一处就复发          |

**结论**：只要「同一部番可以合法存在多行」这条成立，幽灵卡就会以不同形态反复出现。必须把这个可能性消灭在数据库层。

***

## 3. 目标、非目标与硬约束

### 3.1 目标

- **G1 服务端权威身份**：每条订阅有稳定的 `subscription_id`，客户端只认它。

- **G2 键归一到订阅**：bangumiId / remoteSyncId / 旧本地 id 全部通过 alias 表解析到唯一订阅。

- **G3 数据库层防重**：同一用户同一 `subscription_id` / `bangumi_id` / alias 值都只能有一行。

- **G4 删除不可复活**：墓碑表拦截旧设备重传。

- **G5 单一写入入口**：订阅的增删改只经过 `subscriptionService`，禁止路由层直接 SQL。

- **G6 客户端减负**：App 与网页不再需要「按标题猜重复」；标题只在迁移期作为候选信号。

- **G7 兼容与数据安全**：旧接口、旧 App、旧网页行为不变；所有合并可审计、可回滚。

### 3.2 非目标

- 不重做 UI、不改卡片样式、不改筛选/排序/统计口径。

- 不改 Bangumi 搜索、封面本地化、WebDAV 备份、Markdown/B 站导入逻辑。

- 不在本次引入跨用户共享番剧元数据表（元数据仍按订阅行冗余存储）。

- 不做实时多人协同编辑（进度冲突仍按「最后有效修改」收敛）。

### 3.3 硬约束

1. **功能不变**：现有所有入口（App 添加/编辑/删除/同步/导入/恢复，网页登录/添加/改状态/改进度/删除）行为保持一致，响应字段只增不减。
2. **数据不丢**：任何合并/删除前先落审计与快照；进度只允许「更完整」的一方胜出，除非用户显式重置。
3. **零报错**：旧版 App 继续可登录、可同步、可增删；网页在 v2 不可用时自动回退 v1。
4. **可回滚**：每个阶段都有独立回滚动作；DDL 与数据脚本分离。

***

## 4. 不变量（Invariants）

以下 9 条是「一劳永逸」的实际含义，每条都必须由代码或数据库强制，而不是靠约定：

| 编号 | 不变量                      | 强制手段                                                   |
| -- | ------------------------ | ------------------------------------------------------ |
| I1 | 一个订阅只有一行                 | `UNIQUE (user_id, subscription_id)`                    |
| I2 | 一个 alias 值在一个用户下只指向一个订阅  | `PRIMARY KEY (user_id, alias_value)`                   |
| I3 | 同一用户同一 bangumiId 只有一行    | `UNIQUE (user_id, bangumi_id)`（NULL 不参与）               |
| I4 | 同一用户同一 remoteSyncId 只有一行 | `UNIQUE (user_id, remote_sync_id)`                     |
| I5 | 所有写入都先解析身份               | 仅 `subscriptionRepository` 持有写 SQL；路由层禁止 INSERT/UPDATE |
| I6 | 删除必须留墓碑                  | `createSubscriptionTombstone()` 与 DELETE 同事务           |
| I7 | 合并必须留痕且不降进度              | `subscription_merge_log` + 合并策略（§8）                    |
| I8 | 标题不参与运行时身份               | 标题只用于候选检测，代码中无 `WHERE anime_title = ?` 作为唯一解析路径        |
| I9 | v1 与 v2 同源               | v1 适配器读同一张 canonical 表，不维护第二套数据                        |

> 代码评审门禁：`user_subscriptions` 的 `INSERT/UPDATE/DELETE` 只允许出现在 `subscriptionRepository.js` 一个文件。

***

## 5. 数据模型

### 5.1 概念图

```
                 ┌─────────────────────────────┐
                 │ user_subscriptions (canonical)│
                 │ subscription_id  ← 权威身份    │
                 │ bangumi_id / remote_sync_id    │
                 │ anime_id         ← 旧接口投影  │
                 └──────────────┬──────────────┘
                                │ 1
                 ┌──────────────┴──────────────┐
                 │ subscription_aliases         │
                 │ (user_id, alias_value) → sub │
                 └─────────────────────────────┘

 删除：subscription_tombstones + subscription_tombstone_aliases
 合并：subscription_merge_log（可回滚审计）
```

### 5.2 DDL

**阶段 1：只加列/表，不改变任何现有行为（全部可空）**

```sql
ALTER TABLE user_subscriptions
  ADD COLUMN subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN bangumi_id INT NULL,
  ADD COLUMN remote_sync_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN client_updated_at DATETIME(3) NULL,
  ADD COLUMN revision INT UNSIGNED NOT NULL DEFAULT 1,
  ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3),
  ADD KEY idx_user_subscription (user_id, subscription_id);

CREATE TABLE IF NOT EXISTS subscription_aliases (
  user_id INT UNSIGNED NOT NULL,
  alias_value VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  alias_type ENUM('bangumi','remote_sync','legacy_local') NOT NULL,
  subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, alias_value),
  KEY idx_subscription (user_id, subscription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅键别名：任意旧键解析到唯一订阅';

CREATE TABLE IF NOT EXISTS subscription_tombstones (
  user_id INT UNSIGNED NOT NULL,
  subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  row_snapshot JSON NULL,
  deleted_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, subscription_id),
  KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅删除墓碑';

CREATE TABLE IF NOT EXISTS subscription_tombstone_aliases (
  user_id INT UNSIGNED NOT NULL,
  alias_value VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (user_id, alias_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='墓碑别名索引：拦截旧键重传';

CREATE TABLE IF NOT EXISTS subscription_merge_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  target_subscription_id CHAR(36) NOT NULL,
  merged_subscription_id CHAR(36) NULL,
  reason VARCHAR(64) NOT NULL,
  source_row JSON NOT NULL,
  target_row JSON NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅合并审计（可回滚）';
```

**阶段 5：存量清干净后才加硬约束**

```sql
ALTER TABLE user_subscriptions
  MODIFY subscription_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  ADD UNIQUE KEY uk_user_subscription (user_id, subscription_id),
  ADD UNIQUE KEY uk_user_bangumi (user_id, bangumi_id),
  ADD UNIQUE KEY uk_user_remote_sync (user_id, remote_sync_id);
```

说明：

- MySQL 的唯一索引允许多个 `NULL`，所以 `bangumi_id` / `remote_sync_id` 为空时不会互相冲突。

- `subscription_aliases` 的主键故意是 `(user_id, alias_value)` 而不是 `(user_id, alias_type, alias_value)`：**一个键值在一个用户下只允许有一种解释**，从根上消灭「同一个数字既是 A 的 bangumiId、又是 B 的旧本地 id」。

- 保留 `UNIQUE (user_id, anime_id)` 与 `anime_id` 列，仅作为 v1 投影；新代码不得把 `anime_id` 当身份使用。

- 不建议在 `user_subscriptions` 上直接放 `deleted_at`：墓碑独立成表，才能同时保留历史快照与别名，且不影响唯一约束。

### 5.3 字段语义

| 字段                  | 语义                  | 写入规则                                                                                               |
| ------------------- | ------------------- | -------------------------------------------------------------------------------------------------- |
| `subscription_id`   | 权威订阅 ID（UUID v4）    | 新建时服务端生成；客户端可回传                                                                                    |
| `bangumi_id`        | 当前正身的 Bangumi 条目 ID | 仅当订阅已绑定 Bangumi 时非空                                                                                |
| `remote_sync_id`    | 当前正身的客户端稳定 UUID     | 仅当订阅未绑定 Bangumi 时非空；绑定后清空并转 alias                                                                  |
| `anime_id`          | v1 投影键              | `bangumi_id` 优先，其次 `remote_sync_id`，最后保留旧值；变更投影前必须先解析目标键所属订阅，若目标键已有其他订阅行，先合并再改，禁止直接 UPDATE 触发唯一键冲突 |
| `client_updated_at` | 客户端最后一次有效修改时间       | v2 必传；v1 缺省时服务端用写入时刻                                                                               |
| `revision`          | 每次成功写入 +1           | 仅用于诊断与并发检测，不作为冲突拒绝依据                                                                               |
| `updated_at`        | 服务端写入时间             | 自动维护                                                                                               |

***

## 6. 身份解析

### 6.1 三类键

| 类型             | 来源                       | 特点                 |
| -------------- | ------------------------ | ------------------ |
| `bangumi`      | Bangumi 条目 ID            | 全局稳定、最强            |
| `remote_sync`  | 客户端 `remoteSyncId`（UUID） | 跨设备可传播，但可能多设备各生成一份 |
| `legacy_local` | 旧客户端上传的 Room 自增 id       | 仅迁移用，绝不作为新写入身份     |

### 6.2 解析顺序（单事务内）

```js
async function resolve(userId, req) {
  // 0) 显式 subscriptionId 最优先
  if (req.subscriptionId) {
    const row = await repo.lockBySubscriptionId(userId, req.subscriptionId);
    if (row) return { row, matchedBy: 'subscription_id' };
  }

  // 1) 收集键：强键优先，去重
  const keys = [
    req.identity.bangumiId > 0 && { type: 'bangumi', value: String(req.identity.bangumiId) },
    req.identity.remoteSyncId && { type: 'remote_sync', value: String(req.identity.remoteSyncId) },
    ...(req.legacyKeys || []).map(v => ({ type: 'legacy_local', value: String(v) })),
  ].filter(Boolean);

  // 2) 逐个查 alias（按 alias_value 排序加锁，避免死锁）
  const hits = new Map(); // subscription_id -> Set<key>
  const conflicts = [];
  for (const key of keys) {
    const alias = await repo.lockAlias(userId, key.value);
    if (!alias) continue;
    if (alias.alias_type !== key.type && !canReclassify(alias, key)) {
      conflicts.push({ key, alias });   // 语义冲突：不静默合并
      continue;
    }
    hits.set(alias.subscription_id, ...);
  }

  // 3) 墓碑检查：命中墓碑的键不参与新建
  const tombstones = await repo.findTombstones(userId, keys.map(k => k.value));

  // 4) 0 个命中 → 新建；1 个 → 更新；>=2 → 合并（§8）
  // 5) 命中墓碑且 intent != 'add' → 视为过期重传（§9）
}
```

### 6.3 数值键歧义（关键安全点）

`animeId = "123"` 既可能是 Bangumi ID，也可能是旧本地自增 id。规则：

1. 若 alias 已存在且 `alias_type = bangumi`，按 Bangumi 处理。
2. 若 alias 已存在且 `alias_type = legacy_local`：

   - 入参声明为 `bangumi` 且**标题一致** → 原地升级 alias 类型为 `bangumi`（同一订阅，安全）。

   - 入参声明为 `bangumi` 且**标题不一致** → 不合并，记 `numeric_ambiguity` 冲突，保留两条订阅（宁可多一张可疑卡，也不把两部番的数据混在一起）。
3. 若 alias 不存在，v2 写入按入参 `identity` 语义建 alias；v1 写入按 `bangumi` 建，并打点 `v1_unverified_numeric`，等待新版客户端用 `legacyKeys` 纠正。
4. v1 适配器同时收到 `animeId` 与 `legacyAnimeId` 时：

   - 两者不同 → `animeId` 为 bangumi/remote\_sync，`legacyAnimeId` 为 `legacy_local`。

   - 两者相同 → 按 `bangumi`（现有客户端在绑定 Bangumi 时必定优先发送 bangumiId）。

### 6.4 标题的边界

- 运行时**禁止**用标题作为身份去命中/合并（I8）。

- 标题只允许出现在两个地方：

  1. 迁移脚本里生成「合并候选」；
  2. 新建订阅时的**候选检测**：同用户、同规范化标题、一方有 bangumi 另一方没有、且进度不冲突 → 可归并并写审计；其余情况只记录候选、不自动合并。

- 同名不同季是合法数据，不能被合并（用户 1 的 3 组数字+数字里可能就有这种情况，必须先人工核对 Bangumi ID 再决定）。

***

## 7. API 契约

### 7.1 v2 端点

| 方法   | 路径                             | 作用                           |
| ---- | ------------------------------ | ---------------------------- |
| GET  | `/api/v2/subscriptions/list`   | canonical 列表 + 别名 + 墓碑       |
| POST | `/api/v2/subscriptions/upsert` | 幂等新增/更新（含身份解析与合并）            |
| POST | `/api/v2/subscriptions/delete` | 按 `subscriptionId` 或身份删除，写墓碑 |

v1 三个端点保留，内部调用同一个 service，仅做字段翻译。

### 7.2 请求 / 响应

**upsert 请求**

```json
{
  "subscriptionId": "0f0b6f0e-...|null",
  "intent": "sync|add",
  "identity": { "bangumiId": 321885, "remoteSyncId": null },
  "legacyKeys": ["12"],
  "baseRevision": 7,
  "clientUpdatedAt": "2026-10-29T12:00:00.000Z",
  "data": {
    "animeTitle": "链锯人",
    "animeImage": "https://...",
    "airDate": "2022-10-12",
    "isAiring": true,
    "weekday": 3,
    "totalEpisodes": 12,
    "watchedEpisodes": 5,
    "currentEpisodes": 12,
    "status": "watching",
    "rating": 8.5,
    "notes": "",
    "startDate": "2022-10-12",
    "finishDate": null
  }
}
```

**upsert 响应**

```json
{
  "success": true,
  "subscriptionId": "0f0b6f0e-...",
  "revision": 8,
  "applied": true,
  "canonical": { "animeId": "321885", "animeTitle": "链锯人", "...": "..." },
  "merged": [
    { "mergedSubscriptionId": "aa...", "aliases": ["12", "uuid-old"] }
  ],
  "conflicts": []
}
```

**list 响应**（在 v1 字段基础上只增不减）

```json
{
  "success": true,
  "apiVersion": 2,
  "subscriptions": [
    {
      "subscriptionId": "0f0b6f0e-...",
      "animeId": "321885",
      "revision": 8,
      "updatedAt": "2026-10-29T12:00:00.000Z",
      "aliases": [{ "type": "legacy_local", "value": "12" }],
      "animeTitle": "链锯人",
      "totalEpisodes": 12,
      "watchedEpisodes": 5,
      "...": "其余字段与 v1 完全一致"
    }
  ],
  "tombstones": [
    { "subscriptionId": "bb...", "aliases": ["9", "uuid-x"], "deletedAt": "2026-10-20T..." }
  ]
}
```

### 7.3 错误码

| HTTP | code                     | 场景                        | 客户端行为                   |
| ---- | ------------------------ | ------------------------- | ----------------------- |
| 400  | `INVALID_IDENTITY`       | 既无 identity 也无 legacyKeys | 提示并上报日志                 |
| 409  | `IDENTITY_CONFLICT`      | 数值键语义冲突（§6.3）             | 保留本地数据，等待下次同步           |
| 409  | `MERGE_REQUIRES_CONFIRM` | 同标题候选但进度冲突                | App/网页弹合并确认             |
| 410  | `SUBSCRIBED_DELETED`     | 命中墓碑且判定为过期重传              | 静默忽略（返回 `success:true`） |
| 500  | `INTERNAL`               | 事务失败                      | 保留本地，下次重试               |

> 为兼容旧客户端，v1 适配器**永远返回 200 + success:true**（除非鉴权失败），把上述情况降级为 `ignored:true` 并打点。

### 7.4 并发与幂等

- 所有 SQL 在同一事务内执行（`getConnection` + `beginTransaction`）。

- 加锁顺序固定：按 alias 值字典序，再按 `subscription_id`；重复调用最多重试 3 次（`ER_LOCK_DEADLOCK`）。

- `upsert` 幂等：同一 `subscriptionId` + 相同字段重复提交，除 `revision` 外无副作用；客户端可用 `clientUpdatedAt` 做去重。

- 并发同 bangumiId 写入由 `uk_user_bangumi` 兜底：后到者捕获唯一键冲突后重新 resolve 一次，转成 update。

### 7.5 v1 适配器逐接口行为

| v1 接口                            | 适配规则                                                                                                           | 保证                         |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------- | -------------------------- |
| `GET /api/subscriptions/list`    | 读 canonical，`animeId` 取 bangumi > remote\_sync > legacy；附加 `subscriptionId`、`supersededAnimeIds` = 该订阅其余 alias | 旧 App 不需要改代码就能不再看到重复；网页也不变 |
| `POST /api/subscriptions/add`    | 按 §6.3 解析；命中多条 → 合并；`legacyAnimeId` 登记为 `legacy_local`；`supersededAnimeIds` 转 alias 迁移                         | 旧 App 的每次全量同步都会自然完成收敛      |
| `POST /api/subscriptions/remove` | 任意 alias 值都能解析到 canonical 并删除，写墓碑                                                                              | 修掉「删数字行后 UUID 行复活」这类问题     |

### 7.6 兼容矩阵

| 客户端 \ 服务端 | 旧服务端                           | 新服务端（v1）                   | 新服务端（v2） |
| --------- | ------------------------------ | -------------------------- | -------- |
| 旧 App     | 现状（有幽灵卡）                       | 收敛，无需发版                    | 同 v1     |
| 新 App     | 走旧合并器（`SubscriptionMerger` 保留） | 走 v2，服务端返回 `apiVersion` 探测 | 正常       |
| 旧网页       | 现状                             | 自动收敛                       | 同 v1     |
| 新网页       | 回退 v1                          | 探测 `apiVersion` 后走 v2      | 正常       |

***

## 8. 合并与冲突

### 8.1 目标行选择

按顺序比较，第一个满足即胜出：

1. 有 `bangumi_id` 的一方（Bangumi 身份最可靠）
2. 进度更完整的一方（`progressScore` 高）
3. `updated_at` 较新的一方（仅作为最终兜底）
4. `subscription_id` 创建更早的一方（保证 ID 稳定，避免地址漂移）

`progressScore = (status != 'planning') + (watchedEpisodes > 0) + (startDate != null) + (finishDate != null) + (notes 非空) + (rating > 0)`。

### 8.2 字段合并规则

| 字段                                     | 规则                                                            |
| -------------------------------------- | ------------------------------------------------------------- |
| `total_episodes`                       | 取非零最大值（旧行常为 0）                                                |
| `watched_episodes`                     | 取 `progressScore` 胜出方的值；仅当另一方为 0 且胜出方也为 0 时保留 0。**迁移阶段禁止降进度** |
| `current_episodes`                     | 取最大值                                                          |
| `status`                               | 跟随进度胜出方                                                       |
| `rating`                               | 取非空/非 0 的一方；都有效取胜出方                                           |
| `notes`                                | 非空优先；都非空取胜出方（不做字符串拼接，避免重复笔记）                                  |
| `start_date` / `finish_date`           | 非空优先，其次胜出方                                                    |
| `anime_image` / `air_date` / `weekday` | 非空优先，其次较新的一方                                                  |
| `subscribed_at`                        | 取较早的时间（保持列表排序稳定）                                              |
| `is_airing`                            | 跟随胜出方；一方为 false 一方为 true 时取胜出方                                |
| `anime_title`                          | 跟随胜出方；差异写入审计，迁移后可人工核对                                         |

**显式重置**：v2 请求带 `progressReset: true` 时允许降低 `watched_episodes`，并写 `revision` 与审计。旧接口永远不允许降低。

### 8.3 合并流程（同事务）

1. 按 §6.2 解析出 ≥2 个 `subscription_id`。
2. `SELECT ... FOR UPDATE` 全部行（按 id 排序）。
3. 选胜出方，按 §8.2 合并字段，`UPDATE` 胜出方。
4. `UPDATE subscription_aliases SET subscription_id = 胜出方`。
5. 删除被合并行。
6. 写 `subscription_merge_log`（两行完整 JSON + reason）。
7. 若被合并行有墓碑，墓碑一并迁移/清理。
8. 返回 `merged` 列表给客户端。

> 回滚：`subscription_merge_log` 保存了双份快照，可按 `id` 反向恢复；同时阶段 0 有全表备份。

***

## 9. 删除与墓碑

### 9.1 删除流程

1. 解析身份（`subscriptionId` 优先，其次任意 alias；v1 的 `animeId` 也走 alias）。
2. 事务内：读整行 → 写 `subscription_tombstones`（含 `row_snapshot` 全字段 JSON）→ 写 `subscription_tombstone_aliases`（该订阅所有 alias + `anime_id`）。
3. 删除 `subscription_aliases` 行与该订阅行。
4. 返回成功（未找到也返回成功，保持老接口幂等）。

### 9.2 重传/复活判定

收到写入且命中墓碑时：

- `intent = 'add'` → 用户显式重新添加：删除墓碑与墓碑别名，按快照恢复同一 `subscription_id`，再应用新字段。

- `intent = 'sync'` 或 v1 写入 → 与 `row_snapshot` 比对：

  - 进度/状态/元数据**完全一致** → 判定为旧设备过期重传，返回 `success:true, ignored:true`，不写库。

  - 有任何字段不同 → 视为用户重新添加或继续观看，清除墓碑并恢复订阅，避免误伤真实操作。

- 墓碑保留期 90 天（后台任务清理），与现有 `subscription_key_migrations` 的 90 天口径一致。

### 9.3 客户端本地墓碑

App 现有 `PendingDeletion` 保留不动，作为断网补偿；服务端墓碑与其互补，不互相替代。

***

## 10. 迁移方案

### 10.1 阶段总览

| 阶段 | 内容                                         | 预估    | 可回滚动作                           |
| -- | ------------------------------------------ | ----- | ------------------------------- |
| 0  | 备份 + 全站审计 + 重复分类报告                         | 0.5 天 | 无需回滚                            |
| 1  | 加列/加表/索引（全部可空）                             | 0.5 天 | DROP 新增列/表                      |
| 2  | 回填 `subscription_id` + alias；自动收敛「数字+UUID」 | 1 天   | 恢复备份表                           |
| 3  | 服务端切 canonical（v1 适配器），移除 P0/P1 标题补丁       | 1–2 天 | 回退旧 `server.js`                 |
| 4  | App / 网页切 v2                               | 1–2 天 | 客户端回退开关                         |
| 5  | 加唯一约束、`subscription_id NOT NULL`、停用补丁表     | 0.5 天 | 删除索引（不自动删数据）                    |
| 6  | P2 存量：3 组数字+数字、227 组 UUID+UUID             | 1–2 天 | 按 `subscription_merge_log` 反向恢复 |

> 阶段 3 上线后网页立即不再出现幽灵卡；阶段 4 完成后客户端不再依赖标题兜底。

### 10.2 阶段 0：审计

输出四类清单（CSV/JSON，含用户、标题、各键、进度、判定）：

- A：数字 + 非数字同标题（可自动收敛）

- B：数字 + 数字同标题（**需人工核对 Bangumi ID**，可能是同名不同季）

- C：UUID + UUID 同标题（进度指纹一致 → 可合并；不一致 → 人工确认）

- D：`anime_id = '0'` 或空标题（直接清理，现有 list 已在做）

同时备份：

```bash
mysqldump --single-transaction animetrack \
  user_subscriptions subscription_key_migrations \
  > backup_20261029.sql
```

### 10.3 阶段 2：回填算法（逐用户事务）

```
for each user:
  rows = SELECT * FROM user_subscriptions WHERE user_id = ? FOR UPDATE
  for row in rows:
     subscription_id = uuid()
     bangumi_id   = numeric(anime_id) ? anime_id : NULL
     remote_sync_id = !numeric(anime_id) ? anime_id : NULL
     INSERT alias(user, anime_id, type=bangumi|remote_sync, subscription_id)
  // A 类：同标题「数字行 + 非数字行」→ 数字行为正身，非数字行并入
  mergeByRule(A, target=数字行, reason='phase2_numeric_over_uuid')
  // B/C 类不自动合并，写入候选报告
  // subscription_key_migrations 逐条转 alias：能解析到 canonical 才写，否则跳过并记录
```

要求：

- 每个用户一个事务；失败即整用户回滚。

- 先跑 `--dry-run`，输出「将删除 N 行 / 将迁移 N 别名」，人工核对后再执行。

- 所有删除前 `INSERT INTO subscription_merge_log`。

- 阶段 2 不处理数字+数字、UUID+UUID。

### 10.4 阶段 2 校验 SQL

```sql
-- 每个订阅必须恰好一行
SELECT subscription_id, COUNT(*) c FROM user_subscriptions
GROUP BY subscription_id HAVING c <> 1;

-- 每个 alias 必须指向存在的订阅
SELECT a.subscription_id FROM subscription_aliases a
LEFT JOIN user_subscriptions s
  ON s.user_id = a.user_id AND s.subscription_id = a.subscription_id
WHERE s.subscription_id IS NULL;

-- 同一用户同一 bangumi 不得多行
SELECT user_id, bangumi_id, COUNT(*) c FROM user_subscriptions
WHERE bangumi_id IS NOT NULL GROUP BY user_id, bangumi_id HAVING c > 1;

-- 每个 canonical 的 anime_id 投影必须正确
SELECT * FROM user_subscriptions
WHERE anime_id <> COALESCE(CAST(bangumi_id AS CHAR), remote_sync_id, anime_id);
```

四条全部返回 0 行才能进阶段 5。

### 10.5 回滚

- 阶段 1：`DROP` 新增表/列（不影响旧逻辑）。

- 阶段 2：从 `backup_YYYYMMDD.sql` 恢复 `user_subscriptions`；新增表可清空。

- 阶段 3：回退 `server.js` 到上一个版本（保留新增列，旧代码不读它们）。

- 阶段 4：客户端开关 `subscription_api = v1|v2`（DataStore 远程配置），网页检测 `apiVersion` 自动回退。

- 阶段 6：按 `subscription_merge_log` 的 `source_row` 逐条 INSERT 回被合并行。

### 10.6 维护窗口是什么、为什么需要

「维护窗口」= 在这 10–20 分钟里**只暂停订阅写入**（新增/编辑/删除/进度同步），不是停服：

- 网页仍可登录、浏览、搜索；
- App 仍可正常本地使用，写请求失败后由下次同步补偿（现有 `syncSubscriptionsFromServer` 本身就是失败静默、下次重试）；
- 登录、公告、反馈、图片等其他接口不受影响。

为什么需要：阶段 2 回填会逐用户重写 `user_subscriptions`（生成 `subscription_id`、迁移 alias、合并旧行）。如果这期间还有客户端写入同一用户的行，可能出现「刚回填完又被写回旧键」的竞态。暂停写入可以把这段时间的请求挡在门外，回填完再放开。

两种做法：

| 方案 | 操作 | 优点 | 代价 |
|---|---|---|---|
| A 停写窗口（推荐） | 阶段 2 期间订阅写接口返回 `503` + `{success:false, message:"维护中，请稍后重试"}` | 简单、可验证、风险最低 | 10–20 分钟内不能改订阅（挑凌晨低峰） |
| B 在线迁移 | 先加列（秒级），再按用户分批回填；回填与写入用用户级锁串行 | 无停写 | 代码复杂、验证周期长、需长期双写兼容 |

注意：App 本地写库不依赖服务端，写接口 503 不会导致本地数据丢失；网页改状态/进度失败需前端针对 503 显示「服务暂时维护中」。

推荐 A：放在凌晨 3:00–3:20，20 分钟足够完成阶段 2；若当时线上活跃用户极少，实际影响接近于零。

***

## 11. 客户端改造

### 11.1 App

**数据库（Room 26 → 27）**

```sql
ALTER TABLE anime ADD COLUMN serverSubscriptionId TEXT;
CREATE INDEX index_anime_serverSubscriptionId ON anime(serverSubscriptionId);
CREATE INDEX index_anime_remoteSyncId ON anime(remoteSyncId);
```

> 不建 `remoteSyncId` 唯一索引：存量可能存在重复值，迁移会失败；改为启动审计 + 应用层保证。

**新增文件**

| 文件                                     | 职责                                                |
| -------------------------------------- | ------------------------------------------------- |
| `data/network/SubscriptionApiV2.kt`    | v2 请求/响应模型 + Retrofit 接口                          |
| `data/sync/SubscriptionIdentity.kt`    | 本地 → 身份的映射（bangumiId / remoteSyncId / legacyKeys） |
| `data/sync/SubscriptionSyncService.kt` | 拉取、上传、合并编排（从 `AnimeRepositoryImpl` 抽离）            |

**修改文件**

- `Anime.kt`：新增 `serverSubscriptionId: String? = null`。

- `AnimeDao.kt`：新增 `getAnimeByServerSubscriptionId`、`updateServerSubscriptionId`。

- `AnimeRepository.kt`：`syncSubscriptionsFromServer` / `pullSubscriptionsFromServer` 委托给 `SubscriptionSyncService`；删除/添加仍走同一入口。

- `SubscriptionMerger.kt`：保留为 **legacy 模式**（服务端无 `apiVersion` 时）；v2 模式匹配顺序改为 `subscriptionId → bangumiId → remoteSyncId`，仅在无强键时允许标题兜底。

- `UserAuthApiService.kt`：保留 v1 接口（兼容旧服务端），新增 v2 接口。

**同步流程（v2）**

1. 拉取 list，拿到 `subscriptionId` 与 `apiVersion`。
2. 本地逐条上传：`subscriptionId`（已知）+ identity + legacyKeys + `clientUpdatedAt`。
3. 上传响应中的 `canonical` 写回本地（含 `serverSubscriptionId`）。
4. 合并远端到本地：优先 `serverSubscriptionId`，其次 bangumiId/remoteSyncId；不再按标题插入。
5. 旧服务端（无 v2）走原逻辑，保证回退可用。

**删除 / 重新添加**

- 删除：优先带 `subscriptionId`，同时带旧键作为兜底。

- 重新添加：`intent = 'add'`，用于清除服务端墓碑。

**进度防回退**

- 上传带 `clientUpdatedAt = max(lastProgressAt, 本地修改时间)`。

- 服务端拒绝比 `client_updated_at` 更旧的写入（§8.2），避免「旧设备把新进度覆盖回去」。这是本次一并修复的数据正确性问题。

### 11.2 网页

| 函数                 | 改造                                                                               |
| ------------------ | -------------------------------------------------------------------------------- |
| `fetchAnimeList()` | 优先 `/v2/subscriptions/list`；无 `apiVersion` 时回退 v1；统一返回数组                         |
| `addAnime(data)`   | 改调 `/v2/subscriptions/upsert`，带 `subscriptionId`（编辑时）、`intent`、`clientUpdatedAt` |
| `removeAnime(id)`  | 改调 `/v2/subscriptions/delete`，优先 `subscriptionId`，回退 `animeId`                   |
| `renderGrid()`     | `data-subscription-id` 作为主键；`data-anime-id` 保留用于旧逻辑                              |
| 状态/进度更新            | 用当前订阅的 `subscriptionId` + `revision`，不再用标题/数组下标                                  |
| 侧栏计数               | 继续用 `allAnime.length`（服务端已 canonical）                                            |

网页不新增任何本地合并逻辑——这是验收点之一。

### 11.3 旧版本兼容

- 服务端 v1 适配器保证兼容 **30 天（1 个月）**，自阶段 3 上线起算。
- 下线门禁：提前 7 天在 App 内提示升级；`v1_write_total` 连续 7 天为 0 才可下线；若仍有写入则自动延长，避免旧版本用户数据异常。
- 旧 App 的 `supersededAnimeIds`、`legacyAnimeId` 字段在支持期内继续被接受，内部转 alias。

- 旧 App 删除时只传一个键：服务端按 alias 解析到 canonical 后整条删除，修复「删不干净」。

***

## 12. 防复发与可观测性

### 12.1 每日巡检（失败即告警）

```sql
-- 1) 同一用户同一 bangumi 多行（必须 0）
SELECT user_id, bangumi_id, COUNT(*) c FROM user_subscriptions
WHERE bangumi_id IS NOT NULL GROUP BY user_id, bangumi_id HAVING c > 1;

-- 2) 同一 alias 指向多个订阅（结构上不可能，出现即代码绕过 service）
SELECT user_id, alias_value, COUNT(DISTINCT subscription_id) c
FROM subscription_aliases GROUP BY user_id, alias_value HAVING c > 1;

-- 3) 订阅缺少身份（必须 0）
SELECT COUNT(*) FROM user_subscriptions
WHERE subscription_id IS NULL
   OR (bangumi_id IS NULL AND remote_sync_id IS NULL);

-- 4) 合并量异常（日环比突增 10 倍）
SELECT DATE(created_at) d, COUNT(*) c FROM subscription_merge_log
GROUP BY DATE(created_at) ORDER BY d DESC LIMIT 14;
```

### 12.2 指标

- `subscription_merge_total`（按 reason 分组）

- `alias_conflict_total` / `numeric_ambiguity_total`

- `tombstone_reject_total`

- `v1_unverified_numeric_total`（用于判断何时可下线 v1）

- `v1_write_total`（旧客户端占比）

### 12.3 代码门禁

- 只允许 `subscriptionRepository.js` 出现 `user_subscriptions` 的写 SQL（CI `grep` 检查）。

- 契约测试必须覆盖：并发同键、旧键重传、改名换绑、删除后重传、旧接口字段兼容。

- 每次改订阅相关代码，必须跑「重复巡检 SQL」作为回归。

***

## 13. 测试计划

### 13.1 服务端

| 用例                                       | 期望                                |
| ---------------------------------------- | --------------------------------- |
| 同 bangumiId 并发 upsert 10 次               | 1 行，revision 递增，无重复               |
| 先 UUID 后 bangumi 上传                      | 1 行，UUID 进 alias，进度不丢             |
| 先 bangumi 后 UUID 上传（旧设备）                 | 不新增行，返回 canonical                 |
| 数字键语义冲突（bangumi vs legacy\_local + 标题不同） | 409 `IDENTITY_CONFLICT`，两条订阅均不丢数据 |
| 删除后旧设备重传同一 payload                       | `ignored:true`，不复活                |
| 删除后用户重新添加（progress 有变化）                  | 恢复成功                              |
| v1 add/remove/list 全流程                   | 与改造前行为逐字段一致（除新增字段）                |
| 迁移脚本 dry-run → 执行 → 校验                   | 4 条校验 SQL 全 0 行                   |
| 合并回滚                                     | 按 merge\_log 恢复后与备份一致             |

### 13.2 App

| 用例                            | 期望                   |
| ----------------------------- | -------------------- |
| Room 26 → 27 升级               | 数据完整，无崩溃             |
| 首次同步写入 `serverSubscriptionId` | 第二次同步不重复上传           |
| 两台设备交替改进度                     | 新进度不被旧设备覆盖           |
| 删除后重新添加                       | 服务端恢复，本地一张卡          |
| 旧服务端（无 v2）                    | 走 legacy 合并器，行为与现在一致 |
| 导入（Markdown / B 站）后同步         | 无重复卡、无丢失             |

### 13.3 网页

| 用例         | 期望                                     |
| ---------- | -------------------------------------- |
| 登录后列表数量    | 等于 canonical 行数（用户 1 为 88，而不是 149/178） |
| 改状态 / 标记进度 | 只更新当前卡，不新增卡                            |
| 删除         | 卡片消失，刷新后不复活                            |
| v2 不可用     | 自动回退 v1，页面不报错                          |

### 13.4 回归清单（必须逐项勾选）

添加番剧、编辑详情、修改进度、状态切换、评分、笔记、删除、搜索、筛选、排序、置顶、收藏、统计栏、系列堆叠、封面补全、WebDAV 备份/恢复、Markdown 导入、B 站同步、自动同步、多设备登录、退出登录。

***

## 14. 风险登记

| 风险                     | 概率 | 影响 | 缓解                                              |
| ---------------------- | -- | -- | ----------------------------------------------- |
| 存量数据合并不当导致进度丢失         | 中  | 高  | 只自动合并「数字+UUID」；其余人工核对；全量备份 + merge\_log         |
| 同名不同季被误合并              | 中  | 高  | 移到阶段 6 人工确认，按 bangumiId 判定                      |
| 旧 App 全量同步产生写放大        | 高  | 低  | alias 解析吸收，无重复；监控 `v1_write_total`              |
| 旧 App 删除后 90 天内重加被墓碑拦截 | 低  | 中  | 仅拦截「字段与快照完全一致」的重传；有差异即恢复                        |
| 迁移期间服务重启/半完成           | 低  | 高  | 逐用户事务 + 幂等脚本 + 断点续跑                             |
| `.trae` 未纳入 Git 导致代码丢失 | 中  | 高  | SQL 迁移放 `docs/migrations/`；服务端源码单独私有备份（见 §16.3） |
| 时钟漂移导致进度 LWW 误判        | 低  | 中  | 服务端钳制未来时间（> now+5min 记为 now）                    |

***

## 15. 验收标准（DoD）

1. 用户 1 登录网页：卡片数 = 85\~88（按真实不同番剧数），无 0 集数幽灵卡。
2. App 与网页同一账号显示数量一致，逐卡标题/进度/状态一致。
3. 以下 SQL 全部返回 0 行：

   - 同用户同 bangumi 多行

   - 同 alias 多订阅

   - 无身份订阅
4. 任意设备删除后，其他设备重传不会复活（观察 24h）。
5. 旧版 App（改造前版本）登录、同步、增删改全部正常，无 4xx/5xx 异常峰值。
6. App 回归清单 100% 通过；网页登录/增删改/刷新无报错。
7. 阶段 0 备份可完整恢复；至少演练一次回滚。

***

## 16. 实施清单

### 16.1 服务端（新增文件，遵循单一职责）

| 文件                                                         | 职责                                |
| ---------------------------------------------------------- | --------------------------------- |
| `.trae/server/api/subscriptions/identity.js`               | 键归一化、类型判定、数值歧义规则                  |
| `.trae/server/api/subscriptions/aliasRepository.js`        | alias / 墓碑表的读写                    |
| `.trae/server/api/subscriptions/subscriptionRepository.js` | canonical 表唯一写入口                  |
| `.trae/server/api/subscriptions/mergeService.js`           | 目标行选择、字段合并、merge\_log             |
| `.trae/server/api/subscriptions/service.js`                | upsert/delete/list 编排 + 事务 + 死锁重试 |
| `.trae/server/api/subscriptions/legacyAdapter.js`          | v1 请求/响应翻译                        |
| `.trae/server/api/subscriptions/routes.js`                 | v1 + v2 Express 路由 + 维护窗口 503     |
| `.trae/server/api/subscriptions/schema.js`                 | 建表/加列/严格约束（幂等）                  |
| `.trae/server/api/subscriptions/backfill.js`               | 阶段 2 单用户回填与校验（dry-run 可回滚）        |

修改：`server.js` 移除原内联订阅实现，改挂 router；账号物理清除与统计改按 canonical 处理。

### 16.2 App

新增：`SubscriptionApiV2.kt`（v2 优先 + v1 回退）、`SubscriptionIdentity.kt`（本地 → 身份映射）、
`SubscriptionTombstoneApplier.kt`（服务端墓碑 → 本地删除，规则可单测）、
`SubscriptionSyncService.kt`（上传/拉取/补偿编排，Repository 只委托）。
修改：`Anime.kt`、`AnimeDao.kt`、`AnimeDatabase.kt`（v28：`serverSubscriptionId` + `pending_deletions` 重建）、`AnimeRepository.kt`、`SubscriptionMerger.kt`、`UserAuthApiService.kt`。

> 与规格初稿的差异：`SubscriptionSyncService.kt` 在收尾阶段才抽出（先保证服务端身份模型稳定，
> 再搬同步编排），抽取时判定顺序、日志、限流/防抖参数逐行等价；`AnimeRepositoryImpl` 仅保留接口委托与本地写库。

### 16.3 数据与文档

| 文件                                                       | 内容                        |
| -------------------------------------------------------- | ------------------------- |
| `docs/migrations/subscription-identity/001_schema.sql`   | 阶段 1 DDL                  |
| `docs/migrations/subscription-identity/002_backfill.js`  | 回填 + 自动合并（支持 `--dry-run`） |
| `docs/migrations/subscription-identity/003_verify.sql`   | §10.4 校验 SQL              |
| `docs/migrations/subscription-identity/004_rollback.sql` | 回滚步骤                      |
| `docs/SUBSCRIPTION_IDENTITY_REFACTOR_SPEC.md`            | 本文档                       |

> 鉴于 `.trae` 被 `.gitignore` 忽略，**SQL 与迁移脚本必须放仓库内** **`docs/migrations/`**；服务端源码与 `.env` 的版本化另行评估（不得把数据库密码提交进 Git）。

### 16.4 实施顺序与状态（2026-10-29）

| 阶段 | 内容 | 代码状态 | 线上状态 |
|---|---|---|---|
| 0 | 全量备份 `mysqldump` + 校验 `gzip -t` / `Dump completed` | 已完成 | 已执行（2026-10-29 17:08，12 MB gzip） |
| 1 | DDL：新增列、索引、alias/墓碑/merge_log 表 | 已完成（`schema.js` / `001_schema.sql`） | 已执行（2026-10-29 17:04，临时表预演通过） |
| 2 | 回填：`subscription_id`、alias、A 类收敛 | 已完成（`backfill.js` / `002_backfill.js`） | 已执行（2026-10-29 17:15，0 错误） |
| 3 | 服务端 v2 + v1 适配器 + 维护窗口 503 | 已完成（`routes.js` / `legacyAdapter.js` / `server.js`） | 已部署（2026-10-29 17:16，PM2 `api` 重启） |
| 4 | App：Room v28、v2 优先、身份映射、墓碑消费与补偿、同步编排抽取（`SubscriptionSyncService`） | 已完成，编译 + 单测通过 | 未发版（旧 App 仍走 v1） |
| 5 | 网页：v2 优先 + v1 回退、完整字段更新、`ignored/applied/merged` 处理、`subscriptionId` 删除链路 | 已完成，内嵌 JS 语法检查通过 | 已部署（2026-10-29 17:16，站点 200） |
| 6 | 严格唯一约束、存量数字+数字/UUID+UUID 特例、监控 | 约束开关已实现（`SUBSCRIPTION_V2_STRICT=1`）；指标端点 `GET /v2/subscriptions/metrics`、结构化日志、6 小时墓碑清理定时任务已上线 | 未开启约束（观察期） |

实际执行记录（生产）：

- 备份：`/www/backup/animetrack/animetrack_20261029_170856.sql.gz`，本地副本在 `build/prod_backups/`；
- 回填：39 用户、扫描 6363 行、补 ID 6363、alias 6372、合并 116 组 / 删除 136 行、迁移 9、0 错误；
- 回填后：6227 行、6227 个唯一 `subscription_id`、verify 全部通过；
- 用户 1：149 行 → v2/v1 列表均返回 88 条，`subscriptionId` 无空值；
- 维护窗口实际执行时间为 **17:15（临时写锁，非凌晨窗口）**；写锁期间 v1/v2 写接口验证返回 503，解除后恢复；
- 代码备份：`/www/backup/animetrack/api_code_20261029/`（`server.js.bak`、`index.php.bak`）。

执行门禁：

1. 阶段 0 备份成功并校验可恢复后，才允许阶段 1/2 写线上库；
2. 阶段 2 先 `--dry-run`，核对输出后再 `--execute`，完成后立即跑 `003_verify.sql`；
3. 阶段 2 处于维护窗口（方案 A：订阅写接口 503）；本次为临时窗口，后续大规模变更仍建议凌晨 3:00–3:20；
4. 阶段 3 部署后先用只读账号跑 `003_verify.sql`，再观察 v1/v2 写入错误率；
5. 阶段 6 的严格约束只在 `003_verify.sql` 全部为 0 行后才开启。

### 16.5 已完成的验证

> 测试运行：`node docs/migrations/subscription-identity/tests/identity.test.js` 与
> `node docs/migrations/subscription-identity/tests/serviceScenarios.test.js`。
> 服务端源码不在默认位置时，用 `SERVER_API_DIR=/www/wwwroot/api` 指定。

- 服务端纯函数测试（`docs/migrations/subscription-identity/tests/identity.test.js`）：键分类、v1/v2 适配、时间解析、墓碑快照匹配全部通过。
- 服务端场景测试（`docs/migrations/subscription-identity/tests/serviceScenarios.test.js`）：14 个场景通过，覆盖
  UUID 幽灵行收敛、alias 幂等、数字歧义拦截、换绑、旧设备旧键重传、墓碑恢复、
  LWW 与 `progressReset`、v1 禁止降进度、UUID+UUID 不自动合并、旧 UUID 身份回退防护、
  `legacy_local` 标题校验与墓碑兜底边界、`mergeForUser` typed aliases。
- 服务端全部订阅 JS 文件 `node --check` 通过。
- 本地 preflight `docs/migrations/subscription-identity/run-checks.ps1` 通过：JS 语法检查、纯函数/场景测试、`user_subscriptions` 写 SQL 门禁（仅 `subscriptionRepository.js`、`schema.js`、一次性 `backfill.js` 允许写主表）。
- 可选 MySQL 集成测试 `docs/migrations/subscription-identity/tests/integration.mysql.test.js`：由 `MYSQL_URL` 门控，覆盖 typed 墓碑、旧设备 `ignored`、`intent=add` 恢复、并发同键最终 1 行、`mergeForUser` typed aliases；未配置数据库时自动 SKIP。
- App `:app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` 通过；新增 `SubscriptionTombstoneRulesTest`（7 个用例：订阅 ID 精确匹配、绑定其他订阅时拒绝、bangumi/remote_sync 匹配、`legacy_local` 标题校验、有时间戳时守卫、非法 `deletedAt` 不守卫）。
- 网页内嵌 JS 提取后 `node --check` 通过。

生产环境验证（2026-10-29）：

- MySQL 5.7 临时表预演 DDL 通过；`002_backfill.js --dry-run` 与 `--execute` 均 0 错误；
- `003_verify.sql` 通过（第 5 条查询返回计数 `0` 属正常，其余查询均为 0 行）；
- 新服务端启动日志无异常，v2 路由挂载成功；写锁期间 v1/v2 写接口返回 503，解除后恢复；
- v2 list：用户 1 返回 88 条，`subscriptionId` 空值 0、alias 150；v1 list 同为 88 条；
- 网页 PHP lint 通过、站点 200、页面已包含 `/v2/subscriptions/list`。

尚未完成：

- App 真机回归与发版（当前线上仍是旧 App，走 v1，功能兼容）；
- Room 27→28 迁移的真机/模拟器验证（步骤见 `docs/migrations/subscription-identity/room_migration_check.md`）；
- MySQL 集成测试实际执行（需设置 `MYSQL_URL`，未设置时 `integration.mysql.test.js` 自动 SKIP）；
- 网页实际登录后的增删改 / 刷新 / 删除后重加的 UI 回归（需要用户账号手动确认）；
- 一次完整的数据库恢复演练；
- 阶段 6 严格约束与 30 天 v1 下线观察。

已知非本次问题：`BangumiImage` 封面缓存偶发 `rename ... ENOENT` 并发日志，
图片会回退网络加载，不影响功能；后续可单独优化临时文件名冲突。

### 16.6 2026-11-01 补充部署与回归

**服务端（`/www/wwwroot/api`，PM2 `api`）**

- 部署 `subscriptions/` 的 typed 墓碑实现（`service.js` / `aliasRepository.js` / `identity.js` / `schema.js` / `routes.js` / `mergeService.js` / `legacyAdapter.js` / 新增 `metrics.js`）与 `server.js` 墓碑定时清理接线；
- `GET /v2/subscriptions/list` 的 `tombstones` 现在下发 `aliases: [{type, value}]` 与 `animeTitle`（线上已验证：`subscriptionId, animeTitle, aliases, deletedAt`，alias 元素为 `type/value`）；
- `subscription_tombstone_aliases` 新增 `alias_type` 列并回填 30 行（bangumi 3 / remote_sync 15 / legacy_local 12）；
- 生产库 `alias_value` 为 `ascii_bin`，回填 SQL 直接比较 `JSON_UNQUOTE(...)`（utf8mb4）会报 `Illegal mix of collations`。已改为 `CONVERT(... USING ascii)`，并把回填失败降级为告警（不阻塞启动）；
- 事故记录：首次重启因上述 collation 报错导致 API 反复重启约数分钟，修正 SQL 后恢复；部署前备份位于 `/www/wwwroot/api/backup/20261101/`。

**App**

- `fix(sync): 兼容服务端墓碑字符串 alias`：新增 `SubscriptionV2AliasAdapter`，旧字符串别名按值形态保守推断（数字→legacy_local、UUID→remote_sync、其余忽略）；
- `fix(sync): 新增番剧上传回写前补上本地 id`：`insertAnime` / `batchInsertAnimes` 同步时传落库 id，修复新增卡片身份键写空、被其他设备删除后无法墓碑清理的问题；
- `feat(sync): 落败方采纳 canonical 与换键元数据`：LWW 落败时用 canonical 回写用户可见字段；合并命中时若服务端已换绑 Bangumi（旧键在 `supersededAnimeIds`）则采纳新键/标题。

**真机回归（两个隔离包 mtestA/mtestB，测试账号）**

- 首次同步、A 新增 → B 拉取、B 删除 → A 墓碑清卡且不复活；
- 断网删除：`pending_deletions` 记录 SERVER + REMOTE_SYNC 同组两键，联网后补偿 1 组、服务端删除、墓碑落地、pending 清零；
- 删除后重新添加同一 Bangumi 番：服务端以同一 `subscriptionId` 恢复，本地身份写回，另一台拉取无重复；
- LWW：A 断网改状态（旧 `clientUpdatedAt`）→ B 在线更新（较新）→ A 全量同步被拒后日志 `LWW 落败，采纳服务端较新数据`，本地状态被纠正；
- 换键：A 匹配 Bangumi 3425 后，B 拉取采纳 `bangumiId` 且卡片数量不变（无重复卡）。


***

## 17. 决策记录与待确认

### 17.1 已确认（2026-10-29）

| # | 决策项 | 结论 |
|---|---|---|
| 1 | 维护窗口 | **方案 A（停写窗口）**：阶段 2 回填期间订阅写接口返回 503；本次已于 2026-10-29 17:15 以临时锁执行（非凌晨窗口），后续大规模变更仍建议凌晨 3:00–3:20；App 本地写库不受影响，失败由下次同步补偿 |
| 2 | 数字+数字 3 组（亚人 / 链锯人 / 约会大作战） | **先按 Bangumi ID 人工核对**，确认是「同名不同季」还是「旧本地 id 污染」，再决定合并或保留 |
| 3 | 227 组 UUID+UUID | 进度指纹一致的自动合并；不一致的**保留两行 + 标记候选**，逐条确认 |
| 4 | 旧客户端支持周期 | **1 个月（30 天）**，自阶段 3 上线起算，按下线门禁执行（见 §11.3） |
| 5 | 墓碑策略 | **接受**「字段与删除快照完全一致才拦截」的折中方案 |
| 6 | 进度被旧设备覆盖 | **本次一并修复**，改为按「最后修改时间」决胜（见 §11.1） |
| 7 | 墓碑别名是否带类型 | **带类型**：`subscription_tombstone_aliases.alias_type`（`bangumi` / `remote_sync` / `legacy_local`），服务端用对象 `{type,value}` 下发；稳定键精确匹配，`legacy_local` 仅标题一致时采信 |
| 8 | 墓碑恢复时的别名处理 | **恢复全部历史别名**；已被其他订阅占用的别名只 `warn` 跳过，不让单条冲突阻断整次恢复 |
| 9 | 快照比较字段范围 | **保持 7 个用户可编辑字段**（标题、图片、播出状态、集数、进度、评分/状态、备注等），理由：元数据刷新不应让已删除订阅“复活”；与「全字段比较」的偏差已登记 |
| 10 | `revision` / `baseRevision` 语义 | **仅诊断**：服务端不因 revision 不一致拒绝写入，LWW 仍由 `clientUpdatedAt` 决定；响应返回 `revisionMatched` 供客户端提示刷新 |
| 11 | 可观测性 | 进程内指标 `GET /v2/subscriptions/metrics`（merge / alias_conflict / numeric_ambiguity / tombstone_reject / tombstone_restore / v1_write）+ 结构化日志；墓碑清理默认 6 小时（`SUBSCRIPTION_TOMBSTONE_CLEANUP_HOURS` 可调） |
| 12 | CI 范围 | GitHub Actions 只跑 Android 编译 + 单测；`.trae` 未纳入 Git，服务端检查走本地 `run-checks.ps1`，MySQL 集成测试由 `MYSQL_URL` 门控 |
| 13 | `SubscriptionSyncService` 抽取 | **最后执行**：同步编排从 `AnimeRepositoryImpl` 迁出，Repository 只保留委托；判定顺序、日志、限流/防抖参数逐行等价 |

### 17.2 待确认

当前无待确认项；维护窗口已选择方案 A（见 17.1 第 1 项）。

> 按 §16.4 顺序实施；所有线上写操作（阶段 1 DDL、阶段 2 回填）仍需先完成阶段 0 备份。
