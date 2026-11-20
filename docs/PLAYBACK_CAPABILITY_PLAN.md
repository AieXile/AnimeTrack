# AnimeTrack 播放能力规划（合并版）

> 合并自两份播放能力规划，只保留经代码核实、可落地、不过度设计的内容，并标注当前进度。
> 核对基线：分支 `main` @ `f088b1c`，数据库版本 **30**，日期 2026-11-18。

---

## 一、结论先行

两份文档分歧最大的三点，已按代码事实定案：

| 争议点 | 结论 |
| --- | --- |
| WebDAV「每次回根目录」的根因 | 浏览页**只读不写** `webdavMediaPath`，且它与备份同步配置无关。已拆成播放器专属的两个 key 修复。 |
| 网页播放能否复用现有 HTTP 链路 | 不能。原逻辑把全部 http(s) 交给 WebDAV 数据源，会把 WebDAV 账号发给任意第三方主机，且 3xx 直接抛错。必须先拆数据源，已完成。 |
| m3u8 是否可播 | 原工程无 `media3-exoplayer-hls`，拿到流也放不了。依赖已加入。 |

数据层不采用「`media_sources` 三合一 + 独立绑定表」的重型模型，而是两张业务表 + 一张完成记录表，用户手动绑定直接落在 `local_media` 上。WebDAV 远程建库、统一播放中心重构、站点适配器均不做。

执行顺序以「最小可用、可回退」为准：
**WebDAV 记忆 → 本地库 → 本地播放联动 → 数据源拆分 + 网页播放 → 详情页联动与健壮性。**

---

## 二、进度总览

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| P0 | 基线确认、迁移策略、解析器/匹配器单测 | ✅ 完成 |
| P1 | WebDAV 目录记忆与配置统一 | ✅ 完成 |
| P2 | 本地媒体库（选文件夹 / 扫描 / 解析 / 匹配 / 修正） | ✅ 完成（后台生命周期与增量见 P6） |
| P3 | 本地播放联动（稳定 ID / 字幕 / 连播 / 进度） | ✅ 完成（详情页入口未接，见 P5） |
| P4 | 数据源拆分 + HLS/DASH + 网页播放 + 规则引擎 | ✅ 完成 |
| P5 | 番剧详情页接入应用内播放 | ✅ 完成（2026-11-18） |
| P6 | 本地库健壮性与性能 | ✅ 后台扫描、重新授权、Room 分页已实现；真机验证见 P6 |
| P7 | WebDAV 收尾（进度稳定键 / 测试连接 / 浏览记忆增强） | ⏳ 未开始 |

---

## 三、已落地内容（按代码核实）

### 3.1 WebDAV 目录记忆 ✅

**根因**：媒体根目录与「上次浏览目录」混用一个 key，且全项目只读不写。

**现状实现**：

- 偏好项拆分：`player_webdav_media_root`（用户设定根，可空）、`player_webdav_last_browse_path`（成功后才写）、`player_webdav_last_selected_file`。
- 进入顺序：上次路径 → 媒体根 → 服务器根。
- 记忆只在与当前服务器同 host/port、且位于媒体根之下时才采用，避免换服务器串目录。
- 只有 PROPFIND 成功后才写 `last_browse_path`；失败不写。
- 上次路径失效时清除浏览记忆并回退到媒体根一次，不会反复撞死路径。
- 浏览页顶部提供：回到媒体根、设为媒体根、清除浏览记忆。
- `setPlayerWebdavCredentials()` 改为一次 DataStore 事务写入 URL / 用户名 / 密码。
- 浏览、本地库、网页播放页均使用 `AppScaffold`，符合外壳规范。
- 旧的 `webdavMediaPath` 已无任何读取方（仅剩声明），属于可清理的死 key。

### 3.2 本地媒体库 ✅

**数据层**（数据库 28 → 29 → 30）：

- `local_source(id, treeUri*, displayName, lastScanAt, videoCount, scanState, scanError, permissionState, createdAt, updatedAt)`
- `local_media(id, sourceId→local_source CASCADE, documentUri, relativePath, fileName, size, lastModified, parsedTitle, seasonNumber, episodeNumber, episodeEndNumber, matchState, matchedAnimeId, confidence, isUserOverride, isMissing, episodeType, parentDocumentId, updatedAt)`，唯一键 `(sourceId, relativePath)`
- `local_episode_progress(id, animeId, episodeNumber*, seasonNumber, completedAt)`，唯一键 `(animeId, episodeNumber)`

完成记录单独成表，比原方案更清楚：**位置表达续播点，完成记录表达这一集是否看完**，跳看和重看不靠删除位置记录推断。

**扫描**：

- `OpenDocumentTree` + `takePersistableUriPermission`，持久化读权限。
- `DocumentsContract.buildChildDocumentsUriUsingTree` 递归遍历，不引入 `DocumentFile`。
- 扫描在 IO 调度器上运行，每 100 条提交新增/变化项，协程取消不覆盖已提交批次。
- 只有完整读完来源，才在 Room 事务内标记未见文件为缺失并更新扫描摘要；不直接删除。
- 同路径且大小、修改时间已知且未变的视频跳过解析；URI / 父目录标识变化仍刷新定位，沿用媒体 ID 与关联。
- 授权失效（换卡 / 重挂载）落为 `REVOKED` 并抛错提示，不静默丢库。
- 视频扩展名统一走 `VideoFormats`，浏览页与本地库复用。

**命名解析**（`EpisodeFileNameParser`，纯文本、可单测）：

- 支持 `S01E03`、`Season 2 Episode 04`、`第03集`、`EP03`、`[字幕组] 番剧名 - 01`、`01-02` 区间、`03v2`。
- 识别字幕组前缀、分辨率、编码、音轨、字幕语言等噪声。
- 识别 `NCOP/NCED/PV/特典/预告` 为非正片，标为 `EXTRA` 不参与选集。
- 输出标题、季数、集数、结束集数、是否正片、置信度。

**自动匹配**：

- 先与本地 Anime 做归一化标题精确匹配（含季数加成），≥ 0.90 自动绑定。
- 未命中时按「清洗后标题」查 Bangumi，每次扫描最多 30 次远程查询，同标题结果缓存复用。
- 自动匹配结果不覆盖 `isUserOverride`；用户确认/解除关联后标记覆盖，重扫不会改回。
- 低置信度进入待确认列表，可在本地库页面手动关联或解除。

### 3.3 本地播放联动 ✅

- 稳定媒体键：`local:<id>`（`LocalPlaybackQueue.mediaKey`）。
- 选集队列按「同一来源 + 同一匹配番剧 + 同一季」构建，S1 与未标季视为同季，S2+ 必须完全一致；同集多版本取用户绑定优先、其次 id 最小。
- 起播时自动查找同目录外挂字幕（srt/ass/ssa/vtt），3 秒超时静默降级，不阻塞起播。
- 播放列表自动连播；切集时用采样值结算上一集，避免 ExoPlayer 不触发 `STATE_ENDED` 导致丢进度。
- 完成判定基于**有效观看时长**：观看 ≥ 90% 时长，或位置 ≥ 95% 且实际观看 ≥ 60%。直接拖到片尾不算看完。
- 完成后才推进追番进度：只处理用户确认关联、集数明确的正片；按「从第 1 集连续完成」推进，跳看第 8 集不会把前 7 集算已看。
- 状态语义复用详情页规则：想看 → 在看；开启自动完结且达标时置为已完结。
- 进度更新后走既有 Bangumi 同步链路（`pushProgressThenStatus`）。

### 3.4 播放数据源拆分 ✅

- `RoutingHttpDataSource` 按 **host + 有效端口** 分流：配置的 WebDAV 服务器走 WebDAV 数据源，其余 HTTP(S) 走网页数据源，不再把凭据发给第三方。
- 网页数据源逐跳重算请求头，跨主机不携带上一站的 Cookie / Referer；重定向由 OkHttp 跟随，并有跳数上限。
- 403 反盗链兜底：去掉 Referer/Origin 并模拟 `<video>` 的 `Range: bytes=0-` 重试一次。
- 本地 `content://` / `file://` 仍由 `DefaultDataSource` 处理。
- 已加入 `media3-exoplayer-hls`、`media3-exoplayer-dash`、`jsoup`，版本统一进 `libs.versions.toml`。

### 3.5 网页播放 ✅

- 网页播放页：地址输入、直接播放、解析页面、最近地址历史（最多 20 条、去重、可删除、可清空）、失败重试。
- WebView 嗅探：隐藏 WebView 通过 `shouldInterceptRequest` 捕获直链与请求头，捕获后直接起播；超时、多候选或用户主动打开时才展开交互面板。
- 稳定媒体键：`web:<seriesUrl>|<episode 身份>`，同一线路多集时按集号归一，避免列表缺集/倒序误播；媒体地址每次重新解析，不复用短期签名链。
- 规则引擎：支持导入 Kazumi 规则（HTML / API 两种模式），提供搜索、选集、规则管理与默认规则。
- 未做任何内置站点专用适配器，未注入脚本绕过站点限制。

### 3.6 详情页应用内播放 ✅

- 详情页播放入口与「外部播放」共用同一块 FAB / 展开卡片，主文案按来源切换（本地/网页续播显示「继续观看 · 第 N 集」，仅外部播放时保持「外部播放」）。
- 开启「视频播放」后，详情页始终显示播放入口：没有本地/网页历史时点击直接进入网页播放页（自动带上番剧名，便于写入绑定）；有多个来源时弹出来源选择。
- 本地来源只取「用户确认关联（`CONFIRMED`）+ 文件未缺失 + 非特典 + 非季数 99」的剧集，自动匹配结果不会出现在详情页。
- 续播目标按规则选择：最近有未完成进度的集 → 第一个未完成集 → 全部看完回到第一集；播放时沿用 `local:<id>` 续播点。
- 网页来源：从详情页进入网页播放后写下的历史会带 `animeId` / `animeTitle`；详情页读取该番最近一条，直接恢复原剧集页面与队列，播放完成后与本地共用同一套完成记录推进追番进度。
- 全局网页入口（设置 → 视频播放）产生的历史保持无番剧绑定，不会被详情页误用。
- 入口受 `playerHubVisible`（视频播放聚合开关）控制，与设置页播放入口保持一致；外部播放继续受既有 `externalPlayerEntryVisible` 与追番状态门控。
- 网页播放完成记录按 `WebPlaybackQueue` 的媒体键定位集数，剧集名无法解析出唯一集号时不推进进度。

### 3.7 工程与测试

- 迁移链 28→29、29→30 完整保留。
- 单测覆盖：`EpisodeFileNameParserTest`、`LocalMediaMatcherTest`、`LocalPlaybackQueueTest`、`WatchProgressRulesTest`、`WebPlaybackSessionTest`、`WebPlaybackHistoryCodecTest`、`WebSourceClassifierTest`、`WebSourceParserTest`、`WebSourceApiParserTest`、`WebSourceRuleCodecTest`、`JsonPathLiteTest`。

---

## 四、仍未完成的部分（按代码核实）

| # | 缺口 | 影响 | 归属 |
| --- | --- | --- | --- |
| 1 | 尚无多机型、SD 卡或慢速 SAF 提供方的数据 | 当前真机结果只覆盖一台 Android 17 / ColorOS 设备 | P6 验证边界 |
| 2 | 真实 SAF 首次扫描不等于合成基准 | 真实目录遍历是重扫主要耗时，设备和提供方差异仍需观察 | P6 验证边界 |
| 3 | 尚无 release 构建的端到端滚动帧指标 | debug 真机已有分页首屏和滚动采样，不作为所有设备的性能保证 | P6 验证边界 |
| 4 | WebDAV 播放进度仍以完整 URL 为键 | 与 `local:<id>` 不一致；URL 末尾斜杠/编码变化会产生新续播点 | P7 |
| 5 | 播放器 WebDAV 配置无「测试连接」 | 配置错误只能进浏览页才发现 | P7 |
| 6 | 密码仍以明文存 DataStore | 本机备份/多用户场景有泄露面 | P7（可后续单独立项） |
| 7 | 浏览页不记忆滚动位置与排序；`last_selected_file` 只写不读 | 大目录每次回到顶部；无「继续上次播放」入口 | P7 |
| 8 | 无 `.nfo` 解析；多集合并文件（`01-02`）不进入选集队列 | 命名解析已识别区间，但队列刻意排除多集文件，只单独播放 | 视需求 |
| 8 | WebDAV 播放完成不推进追番进度 | 本地与网页已接入同一套完成记录，WebDAV 仍只记续播点 | 视需求 |
| 9 | 旧版全局网页历史没有 `animeId` | 需从详情页重新进入一次网页播放，才会生成该番的续播入口 | 说明 |
| 10 | 死代码 `SettingsRepository.webdavMediaPath` | 可清理，避免后人误用 | 顺手 |

---

## 五、剩余阶段计划

### P5：番剧详情页接入应用内播放 ✅

**已实现**：

- 播放入口沿用原 FAB / 展开卡片表面；本地或网页续播可用时主文案为「继续观看」，副文案为「第 N 集」。
- 多来源时点击弹出底部来源选择（本地 / 外部播放器 / 网页播放）；单来源保持一步直达。
- 本地来源只取 `CONFIRMED`、未缺失、非特典、非季数 99 的剧集；未确认媒体不出现。
- 续播目标：最近有未完成进度的集 → 第一个未完成集 → 全部看完回到第一集（`LocalPlaybackResume`）。
- 网页续播：历史条目记录 `animeId` / `animeTitle`，详情页取该番最近一条恢复剧集页面；网页播放完成按队列媒体键定位集数，与本地共用完成记录推进追番进度（`WebPlaybackResume`）。
- 播放跳转复用 `player/{animeId}?localMediaId=...` 与 `player/{animeId}?webPlay=true`。
- 入口受 `playerHubVisible` 控制，与设置页「视频播放」一致；外部播放行为保持不变。

**改动清单**：

- `data/media/LocalMediaDao.kt`、`data/media/LocalMediaRepository.kt`：按番剧观察已确认剧集与完成集数
- `domain/media/LocalPlaybackResume.kt`（新增）：本地续播集选择规则
- `data/player/WebPlaybackResume.kt`（新增）：网页续播请求与集数文案
- `data/player/WebPlaybackHistoryRepository.kt`、`PendingWebPlayback.kt`：历史与请求携带番剧绑定
- `domain/media/WebPlaybackQueue.kt`：集号解析
- `domain/media/LocalWatchProgressUseCase.kt`：按已有进度继续推进
- `ui/detail/AnimeDetailViewModel.kt`：本地与网页续播状态
- `ui/detail/AnimeDetailScreen.kt`：入口可见性、来源分流与弹层
- `ui/detail/DetailPlaySourceSheet.kt`（新增）：来源选择弹层
- `ui/detail/DetailExternalPlayAction.kt`：入口文案参数化
- `ui/player/PlayerViewModel.kt`：网页队列完成 → 追番进度
- `ui/player/WebPlaybackScreen.kt`、`WebPlaybackViewModel.kt`：网页播放页携带番剧上下文
- `ui/navigation/Screen.kt`、`AdaptivePaneNav.kt`、`AnimeTrackApp.kt`：网页播放路由参数
- `res/values*/strings_main.xml`：新增 4 条文案（含 en / zh-rTW）
- 单测：`LocalPlaybackResumeTest`、`WebPlaybackResumeTest`、`WebPlaybackQueueEpisodeNumberTest`、`WatchProgressRulesTest`

**验收**：已确认关联的番剧可从详情页续播对应集；未确认/缺失文件不出现入口；外部播放流程不变；详情页编辑态隐藏播放浮层。

### P6：本地库健壮性与性能

**目标**：大目录、后台切换、失败重试都可控。

**已交付**：

- P6.1：可靠元数据未变时跳过解析；仅在完整读完目录后事务提交缺失标记。失败、取消和进程终止不隐藏未扫到的旧媒体。
- P6.2：`LocalMediaScanScheduler` / `LocalMediaScanWorker` 按来源唯一入队，退出页面后继续。状态由 WorkManager 提供，支持取消、重扫及重新授权；只接受原目录，不重建来源。自动匹配移到 `LocalMediaAutoMatchUseCase`，继续保护手动绑定。
- P6.3：数据库过滤缺失并用稳定键排序，Room + Paging Compose 每页 50 条、首批 100 条，列表不再全量物化；番剧标题只订阅一次。Room v30 未变，未引入前台服务。

**真机实测（PLZ110 / Android 17 / ColorOS，debug）**：合成元数据 + 磁盘 Room，每档预热后跑三轮，以下为中位数（ms）；扫描列不含自动匹配，数据库列表列为全量查询并过滤 → 首次加载一页。

| 视频数 | 首次扫描 | 无变化重扫 | 一增一改一删 | 数据库列表：全量 → 首批 |
| ---: | ---: | ---: | ---: | ---: |
| 1,000 | 387.9 | 32.6 | 40.8 | 15.2 → 3.3 |
| 5,000 | 1367.9 | 85.5 | 88.0 | 66.9 → 7.2 |
| 10,000 | 3330.5 | 151.5 | 177.1 | 105.7 → 32.0 |

- 万条合成样本首次遍历/解析/数据库/匹配中位数分别为 64.3 / 2620.6 / 645.0 / 137.1 ms；数据库首批只物化 100 行（旧查询 12,001 行，含缺失历史）。三档无变化均解析 0、写 0；少量变更均解析 2、写 3。合成取消到协程收尾为 0.7–3.4 ms，不代表所有 SAF 提供方的取消响应。
- 分页后的 Compose 首行可见为 110.0 / 113.8 / 106.3 ms（从测试送入数据流起算，非整 App 冷启动）；用系统手势采样的滚动帧 P95 为 12.9 / 11.7 / 12.4 ms，各档约 140 帧中有 1 帧超过 16.7 ms。手势采样不和旧的测试时钟注入数据直接比较。
- 真正的 Android ExternalStorageProvider 树：100 目录、10,001 个视频条目（一个可播放样片，其余为占位文件）。首扫 6.39 s；无变化约 5.1 s、解析和媒体写入均为 0；一增一改一删约 5.31 s，解析 2、写 3。真实重扫主要耗时为 SAF 遍历，增量解析不会跳过目录访问。
- 真机通过系统文件选择器授权、播放样片、后台往返、运行中取消、进程终止后自动恢复、失去权限后重新选择原目录，以及恢复后沿用 `local:1` 播放。只操作 debug 包和自建目录。
- 191 个 JVM 单测、15 个目标 Android 仪器用例通过，包含 WorkManager 去重/取消、Room 一致性、分页渲染与两类基准。测试依赖 Espresso 3.7.0 修复 Android 17 原输入初始化错误。尚不覆盖 release 帧指标、其他机型、SD 卡或长时间被厂商限制的后台执行。

### P7：WebDAV 收尾

**方案**：

- WebDAV 播放进度键改为 `webdav:<规范化路径>`，去掉协议/端口差异带来的重复。
- 配置页增加「测试连接」，明确区分 401/403/404/超时/证书错误。
- 浏览页可选记忆滚动位置与排序（按路径缓存，容量上限）。
- 读取 `last_selected_file`，在设置页提供「继续上次播放」。
- 清理 `webdavMediaPath`。
- 密码加密（Keystore）单独立项，不与本次混合。

### 明确不做

- 三合一 `media_sources` 表与独立 `media_bindings` 表（现有两表 + 完成记录表已够用）。
- WebDAV 远程扫描建库（浏览已满足需求，建库只会放大失败面）。
- 统一「播放中心」重构（现有设置页 + 三个来源入口已构成中心）。
- 站点专用适配器、多来源收藏、平板大屏专属布局。
- DRM、加密 MediaSource、需要复杂登录或绕过防盗链的网站。

---

## 六、验收清单（合并后保留项）

### WebDAV

- 设置媒体根目录后重启仍能恢复；成功进入过的目录下次默认回到该目录。
- 切换服务器不会串用；网络失败不覆盖上次成功路径。
- 播放器不会给普通网页请求附加 WebDAV 认证。
- Basic / Digest / 自签名证书场景有明确结果。

### 本地媒体库

- 选择文件夹后重启仍可访问；授权失效有提示且库不丢失。
- 扫描不阻塞 UI，可取消；重复扫描不重复插入。
- 常见命名可识别集数；自动匹配错误可手动修正；修正不被重扫覆盖。
- 文件移动/删除标记缺失而非直接删除。

### 播放

- 本地、WebDAV、网页三类来源都能播放；只对 WebDAV 主机携带认证。
- MP4 直链与标准 HLS 可播放；过期流地址会重新解析并提示。
- 本地外挂字幕自动挂载；自动连播可用。
- 播放中途只记位置，完成或点「下一集」才推进追番进度；拖到片尾不算看完。
- 未匹配 / 未确认的媒体不修改任何番剧数据。

### 安全

- Cookie、密码、完整敏感请求头不进入日志与普通历史记录。
- 网页跨主机跳转不继承上一站请求头。
