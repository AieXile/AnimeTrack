# AnimeTrack 播放器完善规划

> 核对日期：2026-11-22。AnimeTrack 基线：`feat/player-p7-webdav`，HEAD `719f7ae` **加当前工作区**。Media3 **1.10.1**、ass-media **0.5.1**、Room **32**。P7 的目录队列、字幕预算与缓冲测试等包含未提交实现；「代码已有 / 有用例」不等于已发版或已通过真机回归。
> 本文更新既有路线图，保留 P0–P6 的实现记录和历史实测。新增部分为实施与验收计划，不代表本轮修改了播放器或跑过用例。

## 一、结论与范围

**保留 Media3 / ExoPlayer + MediaSessionService + Compose + ass-media。** 先让 WebDAV 起播、拖动、续播和错轨降级可验证，再补手动外挂、轨道记忆、后台恢复和交互。用 NOVA 对照网络行为、Next Player 对照会话和当前项重载、Just Player 对照外挂发现/编码、mpv-android 检查 ASS 效果；以当前版本的 AndroidX Media3 和 ass-media 源码定实现边界。

**第一批先做 P7.2 的 Range 正确性。** 它直接决定 NAS 拖动和续播时拿到的字节是否正确，不需要改界面、数据库、字幕限额或解码内核。认证与取消边界独立验证，不与 Range 首个提交捆绑；随后再做配置/浏览和字幕体验。

本轮规划覆盖本地单文件、本地媒体库、WebDAV、网页直链/HLS/DASH、详情页和系统媒体控制。现阶段不做 WebDAV 全量扫描建库、三合一来源表、统一播放中心重构、站点专用适配、DRM、全量视频缓存或默认双内核。外部播放器深链仍是独立能力，不拿它替代应用内播放。

**目标数据流：** 来源入口 → 现有协调器解析可播放请求 → `PlaybackService` 装配主媒体和可选字幕 → `MediaController` + Compose 展示；服务按播放事件落断点，再由现有完成规则决定是否更新追番。保持三条边界：**媒体身份不等于当前请求 URL，轨道失败不等于主媒体失败，续播点不等于完成记录。**

## 二、阶段总览

| 阶段 | 范围 | 当前状态 / 优先级 |
| --- | --- | --- |
| P0–P1 | 迁移与解析基线、WebDAV 目录与配置记忆 | 已实现；保留兼容性。 |
| P2–P3 | 本地媒体库、匹配、自动外挂与连播、进度 | 已实现；本地单文件不等同于媒体库。 |
| P4–P5 | 数据源隔离、网页播放及详情页来源入口 | 已实现；回归时保持现有来源和追番语义。 |
| P6 | 扫描、重授权、Room 分页 | 已实现；历史测试边界见 §5。 |
| P7 | WebDAV 稳定性、连接与浏览体验 | 稳定键已实现；目录队列与预算等见工作区。**Range 正确性优先**，再做配置和真实 NAS 回归。 |
| P8 | 字幕、音轨与大字幕策略 | 补手动外挂、选轨恢复、按需加载和分轨降级；以 P7 为前置。 |
| P9 | 会话生命周期、恢复与进度 | 先统一停止/恢复语义和事件归属，再做后台重连、系统控制及 PiP。 |
| P10 | 操作体验、性能与正式版门槛 | 在既有 Compose UI 上迭代；测试和基线采集从 P7 开始，不等到最后。 |
| P11 | 有条件的高级能力 | 不阻塞默认播放器上线；仅在真机数据或用户需求明确后做。 |

---

## 三、已落地内容（按代码核实）

### 3.1 WebDAV 目录记忆 ✅

**根因**：媒体根目录与「上次浏览目录」混用一个 key，且全项目只读不写。

**现状实现**：

- 偏好项拆分：`player_webdav_media_root`（用户设定根，可空）、`player_webdav_last_browse_path`（成功后才写）、`player_webdav_last_selected_file`。
- 进入顺序：上次路径 → 媒体根 → 服务器根。
- 记忆只在与当前服务器同 host/port、且位于媒体根之下时才采用，避免换服务器串目录。
- 只有 PROPFIND 成功后才写 `last_browse_path`；失败不写。
- 恢复上次路径失败时会尝试媒体根一次；回退成功后写入根路径。区分「路径已失效」和「临时断网」是 P7.3 的改进项。
- 浏览页顶部提供：回到媒体根、设为媒体根、清除浏览记忆。
- `setPlayerWebdavCredentials()` 改为一次 DataStore 事务写入 URL / 用户名 / 密码。
- 浏览、本地库、网页播放页均使用 `AppScaffold`，符合外壳规范。
- 旧的 `webdavMediaPath` 已无任何读取方（仅剩声明），属于可清理的死 key。

### 3.2 本地媒体库 ✅

**数据层**（数据库 28 → 32）：

- `local_source(id, treeUri*, displayName, lastScanAt, videoCount, scanState, scanError, permissionState, createdAt, updatedAt)`
- `local_media` 以 `(sourceId, relativePath)` 唯一识别文件；持久化 SAF URI、解析版本、边车证据、隐藏/缺失、归档键和独立的追番绑定。
- `local_media_archive` 以 `(sourceId, key)` 保存已识别标题、海报和平台 ID，不进入追番表。
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

- 复用 Kotlin Anitomy 解析字幕组标签、集数、年份与校验码；仅对中文区间、全括号命名、别名和季目录补充适配。
- `NCOP/NCED/PV/特典/预告` 和小数集不进入整数正片连播。保留完整标题、基础标题和原始集号。
- SAF 同次遍历收集 `tvshow.nfo`、`season.nfo`、同名 NFO 和 `bangumi.ini`，不写回原文件。边车 ID 优先，Ani-RSS 偏移仅在正片表验证后应用。

**自动匹配**：

- Room 31 → 32 新增独立媒体归档，空追番库也能自动归档、选集和续播，不新增收藏、不修改订阅。
- 先使用已知 ID 和已识别结果，再按同证据组查 Bangumi；现有 TMDB 接口补充动画/剧场版或季信息。不按搜索排序自动绑定，季、年份、类型不符即不采用。
- 断网、超时或限流保留等待状态；WorkManager 在联网后补刮，不重复遍历文件。真实歧义可一次匹配整组或单独修正。
- 外层卡片可整组匹配、确认或解除；解除只让文件回到未归类，不删除视频或播放记录，也不会自动绑回这些文件。
- 「重新识别」只重试等待或待确认的文件，保留已归档结果与手动操作；普通重扫仍跳过文件内容与证据未变的项。

### 3.3 本地播放联动 ✅

- 稳定媒体键：`local:<id>`（`LocalPlaybackQueue.mediaKey`）。
- 选集队列按「同一来源 + 同一媒体归档 + 同一季」构建；旧记录回退到追番关联。S1 与未标季视为同季，S2+ 必须完全一致；同集多版本取选中项、用户绑定优先，其次 ID 最小。
- 媒体库起播按目录查找同目录外挂字幕（SRT / ASS / SSA / VTT / TTML），同次准备复用目录结果，目前是**每目录** 3 秒上限，超时跳过该目录；[本地单文件入口](../app/src/main/java/com/aiexile/animetrack/ui/player/coordinator/LocalPlaybackCoordinator.kt#L184)不经过媒体库目录查找。
- 自动连播切集时结算上一集；切换时回调依据与显式 seek 的采样边界仍可收紧（见 P9）。
- 追番完成判定基于**有效观看时长**：观看 ≥ 90%，或位置 ≥ 95% 且有效观看 ≥ 60%。直接拖到片尾不应算看完；播放器仓库对非网页媒体另有 95% 删除续播点规则，这与追番完成不同，P9 对齐这两套持久化语义。
- 完成后才推进追番进度：只处理用户确认关联、集数明确的正片；按「从第 1 集连续完成」推进，跳看第 8 集不会把前 7 集算已看。
- 状态语义复用详情页规则：想看 → 在看；开启自动完结且达标时置为已完结。
- 进度更新后走既有 Bangumi 同步链路（`pushProgressThenStatus`）。

### 3.4 播放数据源拆分 ✅

- `RoutingHttpDataSource` 当前按 **host + 有效端口** 分流；普通网页数据源不附加 WebDAV 凭据。仍需验证普通网页恰好与 NAS 同源时的来源隔离，以及重定向后的认证边界（P7.2）。媒体键等价不代表认证范围等价。
- 网页数据源手动逐跳重定向并限制跳数；每一跳按目标重新组装请求头。不在本轮规划扩展站点行为。
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

- 已保留 28→29、29→30、30→31、31→32 的迁移；P7 不改现有数据库版本。
- 单测覆盖：`EpisodeFileNameParserTest`、`LocalMediaMatcherTest`、`LocalPlaybackQueueTest`、`WatchProgressRulesTest`、`WebPlaybackSessionTest`、`WebPlaybackHistoryCodecTest`、`WebSourceClassifierTest`、`WebSourceParserTest`、`WebSourceApiParserTest`、`WebSourceRuleCodecTest`、`JsonPathLiteTest`。

---

## 四、开源实现怎么用

以下链接固定到核对的提交或版本。只借鉴与项目有直接对应的实现，不把上游当前行为当成 AnimeTrack 已验证能力。

| 参考及源码 | 核实的实现 | 对 AnimeTrack 的取舍 |
| --- | --- | --- |
| NOVA：[`WebdavFileEditor`](https://github.com/nova-video-player/aos-FileCoreLibrary/blob/53ce480bfbfbc879604c6ad59505926e92dc9134/src/com/archos/filecorelibrary/webdav/WebdavFileEditor.java#L65-L130)、[`WebdavSecurityAndRangeTest`](https://github.com/nova-video-player/aos-FileCoreLibrary/blob/53ce480bfbfbc879604c6ad59505926e92dc9134/test/java/com/archos/filecorelibrary/webdav/WebdavSecurityAndRangeTest.java#L122-L195) | 给定字节起点时要求 `206` 和正确的 `Content-Range` 起点，单独处理 `416`；测试覆盖 Range、重定向与认证范围。 | P7.2 采用同样的字节正确性用例，但按 Media3 `DataSpec` 契约实现。无 Range 的服务器明确降级，不把开头字节当作目标偏移。 |
| NOVA：[`WebdavUtils`](https://github.com/nova-video-player/aos-FileCoreLibrary/blob/53ce480bfbfbc879604c6ad59505926e92dc9134/src/com/archos/filecorelibrary/webdav/WebdavUtils.java#L52-L103)、[PROPFIND 基准](https://github.com/nova-video-player/aos-FileCoreLibrary/blob/53ce480bfbfbc879604c6ad59505926e92dc9134/test/java/com/archos/filecorelibrary/webdav/WebdavPropfindBenchmarkTest.java#L103-L174) | 目录先请求属性子集，特定不兼容状态回退 `allprop`；对照响应与时延，不只测速度。 | P7.3 统一只读目录查询；先要求资源名、类型、大小、mtime，若后续要缓存字幕再评估 ETag。保留有条件的一次兼容回退。 |
| Next Player：[`Player.kt`](https://github.com/anilbeesetti/nextplayer/blob/431d34cd33b122f8b5a6e8f49caa65413bbaf88f/feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/extensions/Player.kt#L74-L127)、[`PlayerService.kt`](https://github.com/anilbeesetti/nextplayer/blob/431d34cd33b122f8b5a6e8f49caa65413bbaf88f/feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/service/PlayerService.kt#L272-L399) | 追加字幕时按 ID 去重，快照当前索引/位置，只重载当前项而非整队列；服务用 `onPositionDiscontinuity` 的旧媒体与旧位置记录切换。 | P8 复用「当前项重载」语义，P9 复用「按事件归属结算」语义；不照搬插入后移除或数字轨索引，也不沿用其完播规则。 |
| Next Player：[`SubtitleOptionsState`](https://github.com/anilbeesetti/nextplayer/blob/431d34cd33b122f8b5a6e8f49caa65413bbaf88f/feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/state/SubtitleOptionsState.kt#L53-L89)、[字幕集成测试](https://github.com/anilbeesetti/nextplayer/blob/431d34cd33b122f8b5a6e8f49caa65413bbaf88f/app/src/androidTest/java/dev/anilbeesetti/nextplayer/FfmpegSubtitlePlaybackTest.kt#L44) | 延迟/速度作为渲染器能力，经会话传递；测试通过播放器服务覆盖渲染、seek 和重新选轨。 | P8/P10 用同类验证方式。现用 ass-media 与 NextLib 不共享这些接口，不先承诺全格式可调。 |
| Just Player：[`SubtitleUtils`](https://github.com/moneytoo/Player/blob/6df39ae74464ec8b9cb8bb3992ee3ab508513a11/app/src/main/java/com/brouken/player/SubtitleUtils.java#L144-L183)、[`SubtitleFetcher`](https://github.com/moneytoo/Player/blob/6df39ae74464ec8b9cb8bb3992ee3ab508513a11/app/src/main/java/com/brouken/player/SubtitleFetcher.java#L102-L124)、[编码转换](https://github.com/moneytoo/Player/blob/6df39ae74464ec8b9cb8bb3992ee3ab508513a11/app/src/main/java/com/brouken/player/Utils.java#L657-L690) | 同目录优先匹配；远程自动抓取先检查响应声明的 `2,000,000` 字节，非 UTF-8 走转码路径。声明长度检查不等于真实字节硬上限。 | P8 复用匹配与乱码恢复思路，不照搬单视频配任意字幕、按声明限额或直接用文件名写缓存。 |
| AndroidX Media3 1.10.1：[`OkHttpDataSource`](https://github.com/androidx/media/blob/1.10.1/libraries/datasource_okhttp/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java#L304-L343)、[`SubtitleExtractor`](https://github.com/androidx/media/blob/1.10.1/libraries/extractor/src/main/java/androidx/media3/extractor/text/SubtitleExtractor.java#L160-L175)、[`DefaultMediaSourceFactory`](https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/DefaultMediaSourceFactory.java#L584-L642) | Range 被忽略时，通用源在 `200` 响应读弃到目标位置；外挂子源可合并到主源，提取器对已知长度直接分配数组、对未知长度按 1 KiB 扩容，到完整输入后解析。工厂有 `setLoadOnlySelectedTracks`，并非对所有来源无副作用。 | P7 不在大视频上无界读弃；P8 对照[工厂开关](https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/DefaultMediaSourceFactory.java#L484-L497)，仅在实测确认不影响主源/切轨后试用；先不自造解析器。 |
| AndroidX Media3 1.10.1：[会话 Demo](https://github.com/androidx/media/blob/1.10.1/demos/session_service/src/main/java/androidx/media3/demo/session/DemoPlaybackService.kt#L140-L153)、[通知栏入口示例](https://github.com/androidx/media/blob/1.10.1/demos/session/src/main/java/androidx/media3/demo/session/PlaybackService.kt#L25-L41)、[后台播放指南](https://developer.android.com/media/media3/session/background-playback) | 分离服务和 Activity，释放会话/播放器，通知用 `PendingIntent` 回到正确页面；冷恢复和仍活跃的会话重连是不同路径。 | P9 优先做活跃会话重连和事件归属；不把「点过通知」当成可以无条件恢复旧 URL 的理由。 |
| NextLib：[`FfmpegSubtitleSource` / `AssFileExtractor`](https://github.com/anilbeesetti/nextlib/blob/539411ccb4740251252a2cc8896c6579d0c43c3d/media3ext/src/main/java/io/github/anilbeesetti/nextlib/media3ext/ffdecoder/FfmpegSubtitleSource.kt#L32-L135)、[`FfmpegSubtitleDecoder`](https://github.com/anilbeesetti/nextlib/blob/539411ccb4740251252a2cc8896c6579d0c43c3d/media3ext/src/main/java/io/github/anilbeesetti/nextlib/media3ext/ffdecoder/FfmpegSubtitleDecoder.kt#L15-L54) | ASS 外挂走独立子源，以整份脚本作为样本；声明与实读的单份脚本上限均是 **16 MiB**。原生解码器也处理 Matroska 字体。 | 为 P8 提供 16 MiB 对照档，不证明相同上限适合现用管线；仅参考测试和能力，不引入 NextLib 替换 GL 管线。 |
| ass-media 0.5.1：[渲染模式和预算说明](https://github.com/peerless2012/libass-android/blob/v0.5.1/lib_ass_media/README.md)、[`AssHandler`](https://github.com/peerless2012/libass-android/blob/v0.5.1/lib_ass_media/src/main/java/io/github/peerless2012/ass/media/AssHandler.kt#L102-L143) | Cues 与 GL 的动画及 HDR 路径不同；GL 跟踪当前轨和媒体项，`maxRenderPixels` 是**输出像素预算**，不限制脚本字节数。 | 现有 GL 是默认特效路径；以字体、色彩、Surface 切换和低内存的真机结果决定兼容档，不以「能显示文字」视作 ASS 无回归。 |
| mpv-android：[`BaseMPVView`](https://github.com/mpv-android/mpv-android/blob/ebf1b3f0cbe9d6154a43cf6ec1ad6fc32a1b2d47/app/src/main/java/is/xyz/mpv/BaseMPVView.kt#L83-L106)、[mpv 字幕选项](https://github.com/mpv-player/mpv/blob/7f505de186ddcaeff6dabf48db79c9b8f8adc309/DOCS/man/options.rst#L2893-L2924) | Surface 显式绑定/解绑；外挂匹配提供 exact / fuzzy 等行为，完整 ASS 样式由 libass 路径负责。 | 使用同一批无版权或授权样片作对照，核对定位、字体、动画与卡拉 OK；不引入 JNI 或第二套生命周期。 |

**许可边界：** 项目为 [Apache-2.0](../LICENSE)。NOVA 对照的 FileCoreLibrary 和 AndroidX Media3 也是 Apache-2.0；Just Player 仓库标记 Unlicense，ass-media 与 mpv-android App 包装层仓库标记 MIT。Next Player 与 NextLib 为 GPL-3.0，本方案仅借鉴设计和测试行为，不复制实现、不直接引入。若以后分发 libmpv / FFmpeg 等原生产物，必须按实际构建参数和依赖检查许可、NOTICE、源码义务与 APK 体积，不能只看包装层仓库的许可证。

## 五、历史阶段记录（P5–P6）

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
- `domain/playback/LocalPlaybackResume.kt`、`WebPlaybackResume.kt`：本地/网页续播目标选择
- `data/player/web/WebPlaybackHistoryRepository.kt`、`PendingWebPlayback.kt`：历史与请求携带番剧绑定
- `domain/playback/WebPlaybackQueue.kt`：集号解析
- `domain/playback/LocalWatchProgressUseCase.kt`：按已有进度继续推进
- `ui/detail/AnimeDetailViewModel.kt`：本地与网页续播状态
- `ui/detail/AnimeDetailScreen.kt`：入口可见性、来源分流与弹层
- `ui/detail/DetailPlaySourceSheet.kt`（新增）：来源选择弹层
- `ui/detail/DetailExternalPlayAction.kt`：入口文案参数化
- `ui/player/PlayerViewModel.kt`：网页队列完成 → 追番进度
- `ui/player/web/WebPlaybackScreen.kt`、`WebPlaybackViewModel.kt`：网页播放页携带番剧上下文
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

**历史真机实测（PLZ110 / Android 17 / ColorOS，debug，当时 Room v30）**：合成元数据 + 磁盘 Room，每档预热后跑三轮，以下为中位数（ms）；扫描列不含自动匹配，数据库列表列为全量查询并过滤 → 首次加载一页。

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

## 六、实施路线（P7–P11）

### P7｜WebDAV 收尾：首先保证读到正确字节

**已具备：** [身份归一化](../app/src/main/java/com/aiexile/animetrack/data/player/webdav/WebDavPlaybackIdentity.kt#L19)、[旧 URL 进度按需迁移](../app/src/main/java/com/aiexile/animetrack/data/player/PlayerRepository.kt#L72)、目录记忆。在当前工作区还有同目录队列、可取消的字幕目录请求、字幕失败降级和 WebDAV 样本缓冲预算。保留 `webdav:` 身份、已有 DataStore 记录和媒体根行为，不重做 P7.1。

#### P7.2｜Range、认证边界与响应生命周期（第一批）

- **先补可重复的 Range 测试。** [现有 `open()`](../app/src/main/java/com/aiexile/animetrack/data/player/webdav/WebDAVDataSource.kt#L43) 只在 `position > 0` 时发 Range，收到 `200` 时会减去长度但没有跳过响应体开头，`206` 也未验证起点等于请求位置。这是源码发现，真实 NAS 影响待测。先在本地测试服务器以已知字节序列验证返回内容，不用「没有报错」代替正确性。
- **按 Media3 契约实现。** `position > 0` 或指定有限 `length` 时按 `DataSpec` 生成范围（包括从 0 开始的限长读取）。`206` 校验单位、起止、已知总长和请求起点，限制实际读取上界；`416` 仅在可证明位置正好等于资源长度时按 EOF 处理，其他情况显式失败。`200` 允许从 0 开始并遵守限长；非零位置默认报告不支持拖动/续播，提供「从头播放」，不自动重下整个视频。若确有设备兼容需求，再单独测有字节与时间上限的顺序跳过。
- **认证按来源和目标约束。** 显式区分 WebDAV 业务请求和普通网页请求，再按协议、主机与有效端口校验来源。GET、PROPFIND 与鉴权重试共用同一边界策略，HTTPS 不降级转发凭据，跨源默认不跟随带凭据的请求；连接池可共享，账号、证书豁免不共享。默认严格校验证书，保留经用户明确选择的 NAS 兼容模式；不改变备份 WebDAV 行为。
- **取消与错误走同一套收尾。** 换源、离开、超时要释放/取消当前 `Call` 和响应；旧请求不得更新当前 UI，不在失败后用旧长度重试。沿用[播放器失败分类](../app/src/main/java/com/aiexile/animetrack/ui/player/PlaybackFailure.kt#L18)，补「服务器不支持范围」「异常部分响应」等可操作类别。网页 HLS/DASH 和本地不随这一批重写。

**验收：** 从 0 起播后在片头、中间、片尾拖动，正向、反向及冷启动续播均对应目标媒体；覆盖 `200/206/416`、短响应、错范围、响应关闭后可重开、换源取消和认证隔离。无 Range 不伪造成功；错误后可从头重试，原有效断点不被失败准备时的 `0` 覆盖。

#### P7.3｜连接、目录与续播入口

- **连接测试只读、使用草稿。** 在[播放器 WebDAV 配置](../app/src/main/java/com/aiexile/animetrack/ui/player/settings/PlayerWebDavConfigScreen.kt#L53)增加「测试连接」，对当前草稿 / 媒体根发可取消的只读检测，区分错误地址、401/403/404、DNS、超时、证书与不支持的 WebDAV。先确认媒体根可访问；有明确样片时再单独检测 Range，不从 PROPFIND 成功推断「能拖动」。[备份页的检测](../app/src/main/java/com/aiexile/animetrack/ui/settings/DataManageViewModel.kt#L88)会先保存且配置独立，借鉴交互，不复用写配置流程。将地址、媒体根、凭据和信任模式作为同一次请求的配置快照，避免半途混用新旧设置。
- **浏览和起播复用目录查询。** [浏览](../app/src/main/java/com/aiexile/animetrack/ui/player/webdav/WebDAVBrowseViewModel.kt#L134)与[字幕定位](../app/src/main/java/com/aiexile/animetrack/data/player/webdav/WebDavSubtitleLocator.kt#L130)目前走两套调用。优先沿用可取消的只读实现和 Sardine 解析器；先用 `Depth: 1` 请求必要属性，特定不兼容才回退，给列表和解析设预算。[现有 LRU/TTL](../app/src/main/java/com/aiexile/animetrack/data/player/webdav/WebDavDirectoryCache.kt#L33)仅以目录 URL 作键，改为配置命名空间 + 规范化目录，切账号/根/证书策略时隔离或失效。界面和播放都使用同一套路径边界与目录条目校验。
- **分开浏览锚点和继续上次。** 每目录保存有上限的排序/滚动锚点（条目 ID + 偏移，失效回顶部）；临时断网不抹旧位置，目录确认失效才清理。「最后点过的文件」仍可供高亮；「继续上次播放」只记录成功准备的稳定媒体身份，续播时按当前配置重新验证，不重用历史签名 URI。复用现有 WebDAV 导航和设置/浏览页。
- **目录清单不等于同番剧队列。** [当前目录队列](../app/src/main/java/com/aiexile/animetrack/data/player/webdav/WebDavDirectoryQueue.kt#L21)排序同目录视频，无法解析集号者仍可能入队。复用 `EpisodeFileNameParser`：目录内仍可手工选视频，仅在同组/同季且集号明确时自动连播；混合番剧、特典、小数集、多集合并和同集多版本不猜顺序或番剧绑定。确认兼容迁移完成后再清理不再使用的旧路径键。

**验收：** 真实 NAS + 至少一种独立 WebDAV 实现，覆盖 Basic、Digest、正常 TLS、自签名明确授权、中文/空格路径、断网及千条以上目录。测试不写配置；账号或根切换不串目录与续播；从缓存命中的目录点播不重复 PROPFIND；旧测试/查询回包不覆盖新草稿或当前目录。无真实 NAS 回归不标记 P7 完成。

### P8｜字幕与音轨：补齐自动匹配之外的使用流程

#### P8.1｜手动外挂和可预测的选轨

- **统一入口，不统一权限。** 在详情弹层和全屏抽屉共用的[轨道逻辑](../app/src/main/java/com/aiexile/animetrack/ui/player/components/PlayerTrackChoices.kt#L26)接入「添加外挂字幕 / 移除手动外挂」。本地单文件和媒体库用 SAF `OpenDocument`、按文件名/格式识别；可持久化时才保存该文件读权限，失效时提示重选。WebDAV 优先支持从同目录候选手选，经现有根和来源校验；网页入口先只允许手动选本地文件，不自动跨站拉字幕。
- **更新当前项，不重启整季。** 在[会话启动协调器](../app/src/main/java/com/aiexile/animetrack/ui/player/coordinator/PlaybackSessionCoordinator.kt#L241)与服务之间做一次受控更新：快照当前媒体 ID、位置、是否播放、正常倍速、队列和轨道意图；只重载变化的当前源，不把同 ID 的重载当作切集。用户主动换片、退出、暂停优先，迟到响应作废；失败撤回新增字幕，保留主视频。已有字幕/音轨切换继续用 `TrackSelectionParameters`，不为普通切轨重建源。
- **记偏好，不存数字轨下标。** 当前媒体显式「关 / 自动 / 手动」优先，其次是全局偏好语言与默认/强制标记。跨集只继承语言/角色偏好；媒体级可记稳定媒体键 + 语言、角色、标签或受控外挂 ID。找不到时回到自动，保留明确的「关闭」意图。如果语言未知就显示未知，不强猜；简繁与双语识别沿用 [SubtitleSpec](../app/src/main/java/com/aiexile/animetrack/domain/playback/SubtitleSpec.kt#L43) 的同名边界，补后缀分词测试。

**验收：** 播放、暂停或倍速中添加/移除 SRT 和 ASS，不从 0 重播、不丢队列；重复添加不出重复轨，换集不误选上一集同下标轨，切片后迟到结果不挂到新视频；关闭、自动、手选和没有匹配轨道都可在 UI 中辨认。

#### P8.2｜当前集优先、分轨失败降级、8 MiB 决策

- **发现与下载分开。** [WebDAV 准备](../app/src/main/java/com/aiexile/animetrack/ui/player/coordinator/LocalPlaybackCoordinator.kt#L123)已复用目录结果并设 3 秒查询预算；本地多目录仍可能串行等待多个 3 秒。优先准备当前集，给整次发现设总预算，其他集只保留待查信息，不在首帧前读字节。[已有仪器用例](../app/src/androidTest/java/com/aiexile/animetrack/data/player/service/WebDavSubtitlePlaybackTest.kt#L60)断言超限默认轨只请求一次、未选轨零请求；先复跑该用例，补首次选择另一轨、关闭再开启、切集和 HLS/DASH。只有实测缺口且能限定影响范围时才考虑 Media3 的 `setLoadOnlySelectedTracks`，不直接全局开启。超时后给手动加载入口，不让晚到的字幕打断视频。
- **一条失败只影响一条。** [现有服务](../app/src/main/java/com/aiexile/animetrack/data/player/service/PlaybackService.kt#L152)可提示单轨禁用，致命异常时会清掉当前项全部外挂并恢复视频。让结果关联媒体、请求代次和具体子轨，优先移除坏轨，保留正常轨与内嵌字幕；只有不能定位且确实影响主视频时才回退无外挂，最多一次。区分超体积、超时、权限和解析失败，同一问题不重复弹窗。
- **暂保留 WebDAV 单轨 8 MiB / 最多 3 条候选。** [预算](../app/src/main/java/com/aiexile/animetrack/data/player/webdav/WebDavSubtitleBudget.kt#L8)和[有界数据源](../app/src/main/java/com/aiexile/animetrack/data/player/webdav/BoundedWebDavSubtitleDataSource.kt#L17)同时检查声明和实读，超限最多额外探测 1 字节。先减少同时在途和驻留的字幕，测出当前项累计预算；保留可取消的慢速传输与有限重试。原始文件、解析副本、libass 原生内存和 GL 图层是四笔账，`32/48 MiB` 视频样本缓冲目标也不是进程内存上限。
- **测 8 和 16 MiB，再决定是否扩大 ASS 支持。** 有效普通样本与高复杂度样本分别测接近、等于、超过阈值和慢传输、未知长度、多轨的组合，观察首帧、首条可见字幕、卡顿、Java 堆、Native/总 PSS 和 GC。只有实际存在需求、低内存设备过 §7 门槛时，才评估「用户显式选择的 ASS/SSA 可到 16 MiB」，自动候选维持 8 MiB；不做无限制选项。若试用应用私有临时文件减少下载阶段的堆占用，必须证明整体峰值下降：Media3 解析仍可能整份入内存；同时要定义总容量、清理时点和取消行为。

**验收：** 慢速或坏字幕不把主视频置为失败；主片首帧时延达 §7。超限读取达到预算外第 1 字节即停止，主视频不走字幕限额；失败仅提示/禁用对应轨且保留位置。按需加载时验证未选轨不主动下载，反复切换不导致持续的 Java/Native 内存增长。

#### P8.3｜编码和 ASS 兼容性

- 默认尊重 UTF-8/BOM，提供针对单文件的编码重读（优先 UTF-8、GB18030、Big5；需要自动识别再评估依赖）。仅在需要时做转换，输入/输出受限，写应用私有临时缓存，不修改用户原文件；简繁、混合标点与有无 BOM 都进测试。
- 保留现有 ass-media `EFFECTS_OPEN_GL` 与 1080p 像素预算。用 mpv-android 对照内嵌/外挂 ASS、Matroska 字体附件、描边、定位、动态标签、卡拉 OK、HDR：能显示文字不等于特效正确。初始化失败已可回退为普通播放器；运行中若只发生特效故障，另行验证降级并明确通知，不能无限重建。是否公开兼容档由设备矩阵决定，不一律牺牲 HDR 或样式。

### P9｜会话、切集结算与恢复

- **P9.1 服务拥有会话，页面只管理展示。** 保持 [PlaybackService](../app/src/main/java/com/aiexile/animetrack/data/player/service/PlaybackService.kt#L71) 与 `MediaController` 架构。Home/锁屏保持系统会话；显式返回并关闭播放器保留现有「退出停播」；通知「停止」落盘并释放；通知点击回到活跃媒体，不重建队列。验证任务卡移除及系统回收，保存不依赖 Activity 仍在。本地现有列表由 Media3 管理；[网页/WebDAV](../app/src/main/java/com/aiexile/animetrack/ui/player/coordinator/PlaybackSessionCoordinator.kt#L229)向服务只提交当前项，系统切集还依赖页面接收命令。服务能独立处理队列前，不对脱离页面的控制器承诺「下一集」；若迁移，只迁最小可执行队列，不搬 UI 或存短期 Cookie。
- **P9.2 每次切换只结算正确的一集。** 复用 [PlaybackProgressTracker](../app/src/main/java/com/aiexile/animetrack/ui/player/coordinator/PlaybackProgressTracker.kt#L116) 和[完成规则](../app/src/main/java/com/aiexile/animetrack/domain/playback/LocalWatchProgressUseCase.kt#L11)，拆开 UI 的 500 ms 状态刷新与会话的持久化；切集用 `onPositionDiscontinuity` 中旧媒体 ID/位置与原因结算，暂停、结束、关闭统一做幂等写入。显式 seek 重置采样，短 seek 不被算成连续观看。保留「已确认的绑定或明确的网页关联 + 正片 + 集号 + 用户开关」条件；WebDAV 继续只记位置。收敛 [PlayerRepository 非网页 95% 清点规则](../app/src/main/java/com/aiexile/animetrack/data/player/PlayerRepository.kt#L51)，最终只清已确认完成的续播点，未完成且停在尾段时提供明确回看/继续策略，不循环触发结束。关闭「记住位置」不跨会话记断点，不等于关闭合规的完播事件。
- **P9.3 重试、活跃重连、冷恢复分开。** 活跃会话直接绑定，保留倍速、暂停和位置；短断网重试保留用户当次意图，不用设置默认值覆盖。新操作使旧恢复作废。进程退出后只恢复有持久身份且有权读取的来源：本地验证 SAF，WebDAV 用当前配置重建，网页重新解析可靠剧集页而非重用历史短期媒体 URL；信息不足就返回可操作的入口。标准系统控制保留；业务自定义命令只给有资格的控制器，不因服务导出而开放。需要系统冷恢复时再评估 `onPlaybackResumption`，不因启动 App/点通知就自动播旧内容。

**验收：** 播放或暂停中回桌面/锁屏、通知回跳、进程杀死后显式继续、拔耳机、音频焦点变化、快速换源、主动退出。播放意图和断点正确，跳集、拖末尾、失败重试不误推 Bangumi；关闭后无残留播放、通知或绑定 Surface。

### P10｜交互、画面与发布

- **P10.1 使用同一套操作语义。** 保留[手势层](../app/src/main/java/com/aiexile/animetrack/ui/player/components/PlayerGestureLayer.kt#L69)与面板。拖动先预览时间，松手提交一次，取消不发 seek；补音量/亮度、长按结束、弹层、系统边缘手势的冲突用例。可增加可配置跳转秒数，但不默认改变双击播放/暂停；现有固定 85 秒不是片头识别。补无障碍标签与焦点顺序，给手势提供可点击的等价操作；不先上实时缩略图。
- **P10.2 Surface 与画中画。** 保留[已有 PlayerView/Surface 回退](../app/src/main/java/com/aiexile/animetrack/ui/player/components/PlayerVideoSurface.kt#L23)，覆盖暂停帧、竖屏、API 34/较新系统、全屏往返、HDR 与 ASS；纯 Compose Surface 只有实测同等能力才迁移。PiP 放在 P9 后，先做显式入口且只在活跃视频会话启用；隐藏无关控件，恢复保留队列和宽高比，关闭行为遵循 P9。旋转/转场/PiP 不重复起播或误触发进度结算。
- **P10.3 诊断与缓冲以数据为准。** 对[现有缓冲策略](../app/src/main/java/com/aiexile/animetrack/data/player/service/PlaybackBufferControl.kt#L10)以 1080p 和高码率 4K 验证，区分字幕、样本、原生和渲染开销。采集首帧、首条字幕、seek 恢复、重缓冲、丢帧、内存与传输量；先本地诊断，不默认上传，不记完整 URL、用户文件名或认证头。只在分析证实有收益时调整缓冲和高频 UI 更新。
- **P10.4 正式入口。** §7 核心矩阵通过后，才逐步放开受开发者开关控制的视频入口。设置/浏览等二级页沿用 `AppScaffold` 与 `chrome.layout/content`，状态 `StateFlow` + `collectAsStateWithLifecycle()`，新增依赖走 version catalog。功能按阶段独立关停；回退不删除续播和追番数据、不要求降数据库版本。

### P11｜有实际数据或需求再排期

| 候选 | 启动条件与范围 |
| --- | --- |
| 字幕延时、样式和时间轴校准 | 有持续的同步需求；先验证现管线正负延时在片头/中段/尾段的效果，只对通过的格式与渲染模式开放。 |
| 视频章节、片头片尾、预览帧 | 优先已知章节或用户配置；没有元数据不猜时间点。预览独立限频、可取消，不跟 WebDAV 主播放抢资源。 |
| WebDAV 正片与追番绑定 | 能由用户确认 `番剧 + 季/集 + 媒体身份` 并处理冲突才进入；复用进度规则，不依赖全量远程建库。 |
| 凭据存储加固 | 独立迁移到设备密钥保护的存储，覆盖旧记录、换机和密钥失效，明确备份边界；长期优先按服务器明确信任自签名证书。 |
| 原生软解 / 第二内核 / 持久视频缓存 | 关键样片在 Media3 兼容路径仍稳定失败、外部播放不足以解决时再做原型；先核对 ABI、安装体积、功耗和许可。外部播放器如需新接入独立评估，不向外部 App 移交 WebDAV 凭据或网页会话。 |

## 七、验收与发布门槛

| 层级 | 必须覆盖 | 通过标准 |
| --- | --- | --- |
| JVM | 稳定键与旧进度、范围头解析、目录链接、字幕匹配、`8 MiB ± 1`、失败与取消、进度状态机 | 断言字节和状态，不依赖外部站点。扩展 `WebDavPlaybackIdentityTest`、`WebDavSubtitleByteLimitTest`、`WebDavSubtitleDirectoryTest`、`SubtitleSpecTest`、`PlaybackProgressTrackerTest`、`WatchProgressRulesTest`。 |
| Android 仪器测试 | 实际 Media3 会话 + 本地 HTTP/DAV 服务器；MP4、HLS、DASH；短读、分块、Range、字幕超限/慢传输、手动外挂、切轨、取消和导航 | 复用 `WebDavDataSourceCleanupTest`、`WebDavSubtitlePlaybackTest`、`PlaybackBufferControlTest`、`WebDavPlaybackNavigationTest`；断言实际响应内容、播放状态/位置、事件归属和响应关闭，不只断言提示文本。 |
| 真机兼容性 | 较老或低内存设备、当前 ColorOS、另一厂商，覆盖 API 34 与较新系统；真实 NAS 加独立 WebDAV 服务。覆盖本地 SAF 重授权、1080p H.264、受支持的 HEVC/HDR、MKV 多轨与 ASS，正常/慢网/断网 | 每种来源至少一条完整「起播 → seek → 暂停 → 恢复 → 切集/关闭」。用同一片源在 mpv 对照字体、定位、动画和色彩。回归扫描与分页、本地和网页详情页续播。 |
| 性能、泄漏与隐私 | 同机同素材、相同码率/网络分冷/热，记录首帧、字幕首次可见、seek 恢复、卡顿、Java 堆和 Native/进程 PSS 峰值、数据量与丢帧；每档至少重复 20 次报告 P50/P95，另做反复切集/换轨和开关页面的长跑 | 硬门槛：无 OOM/崩溃/静默错位、无旧请求覆盖新会话、无越界认证或明文敏感日志。建议核心首帧/seek P95 相对基线不退化超过 10%，长跑内存回到稳定区间；若接受兼容性例外，写明设备、数据和理由。这是拟定门槛，不是实测成绩。 |

测试用已有合成样片、自制或有权使用的素材；JVM 通过不能替代设备验收。先在 debug 定位，再用 benchmark 或 release 等价构建测性能，保留可回退构建。开发者入口维持现状，直到本地、NAS、网页核心场景均通过。

## 八、改动面与落地顺序

| 批次 | 主要改动范围（先复用） | 独立交付 |
| --- | --- | --- |
| 1：P7.2 | `data/player/webdav` 的数据源与错误、认证边界、测试；必要时 `PlaybackDataSourceFactory` | Range 正确性，不动功能入口、字幕限额、Room 或界面样式。 |
| 2：P7.3 | 目录定位/缓存、`WebDAVBrowseViewModel`、配置/浏览页、`WebDavDirectoryQueue`、现有偏好和导航 | 测试连接、目录记忆、有效续播，逐项验收。 |
| 3：P8 | `SubtitleSpec`、本地/WebDAV 定位、`PlaybackSessionCoordinator` 与服务、共用字幕/轨道组件及测试 | 先手动外挂与选轨，再按需加载与分轨降级；16 MiB 评估和功能交付解耦。 |
| 4：P9 | `PlaybackService`、`PlaybackSessionCommands`、进度跟踪/仓库、来源协调器和导航 | 活跃重连与准确结算先于冷恢复，服务有能力才开放脱页切集。 |
| 5：P10 | `PlayerGestureLayer`、`PlayerVideoSurface`、控制面板、`MainActivity`/Manifest（仅 PiP 需要时）、性能与设备回归 | 交互和发布门槛独立验收，不新增内核依赖。 |

只在浏览、探测和播放确实共享同一份请求配置/规则时，才拆专职模块；轨道或进度规则需要独立单测再拆纯逻辑。P7 不改 Room 32；后续新增持久结构须附迁移和回退。每批记录环境、样片、通过/失败/未测项，不沿用历史的通过数。
