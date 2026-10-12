# Anime Play Link —— 番剧播放跳转开放协议

> 版本:v1.0 草案 | 日期:2026-10-13 | 发起方:AnimeTrack
> 本文档为开放规范,任何追番/播放类应用均可自由实现。欢迎提交反馈与实现案例。

---

## 1. 概述

### 1.1 目的

让任意"番剧信息管理类应用"(追番列表、收藏库、详情页)能够**一键唤起任意"番剧播放类应用"并定位到指定集数播放**,同时支持播放方**回传观看进度**给发起方。解决两个长期痛点:

1. 用户在管理 App 里记录了进度,却要手动去播放 App 里搜索番剧、翻到对应集数;
2. 在播放 App 里看完后,管理 App 里的进度仍是旧数据。

### 1.2 角色与术语

| 术语 | 含义 | 示例 |
|---|---|---|
| **发送方(Sender)** | 发起跳转的应用 | AnimeTrack、Bangumi 客户端、追番列表工具 |
| **接收方(Receiver)** | 接收跳转并播放的应用 | Animeko、其他番剧播放器 |
| **条目(Subject)** | 一部番剧,以 **Bangumi 条目 ID** 全局唯一标识 | `subject 12` |
| **集数(episode)** | 条目下的某一集,有两种标识(见 §2.3) | 第 5 集 |

### 1.3 为什么用 Bangumi ID 做主键

Bangumi(bgm.tv)是中文番剧圈的事实标准:Animeko、弹弹play、AnimeTrack 等均以 Bangumi 条目 ID 作为番剧主键,且其 API 免费开放([文档](https://bangumi.github.io/api/))。以它做协议主键,各方**无需维护任何私有映射表**。

### 1.4 设计原则

1. **渐进兼容**:接收方"能处理的参数就处理,不能处理就忽略",任何参数缺失都不应导致崩溃;
2. **单向依赖**:发送方无需知道接收方内部实现,接收方无需认识发送方;
3. **参数即能力**:能力边界通过参数协商,而非版本号硬切;
4. **零服务端**:纯本地 Intent / URL Scheme 机制,不依赖任何中转服务器。

---

## 2. 跳转 URI 规范

### 2.1 格式

```
anime-play://subject/{bangumiSubjectId}?ep={episodeSort}&ep_id={bangumiEpisodeId}&title={title}&src={senderId}&callback={callbackUri}
```

最小合法形式:

```
anime-play://subject/12
```

带集数与回传的完整形式:

```
anime-play://subject/12?ep=5&title=%E6%94%BB%E5%A3%B3%E6%9C%BA%E5%8A%A8%E9%98%9F&src=animetrack&callback=animetrack%3A%2F%2Fprogress
```

### 2.2 字段定义

| 字段 | 位置 | 必填 | 类型 | 语义 |
|---|---|---|---|---|
| `bangumiSubjectId` | path 首段 | **必填** | 正整数 | Bangumi 条目 ID。非正整数视为非法 URI,接收方应忽略 |
| `ep` | query | 推荐 | 正整数(从 1 起) | 目标集数的 **sort 序号**(第几集)。缺省 → 打开条目详情页 |
| `ep_id` | query | 可选 | 正整数 | **Bangumi episode ID**(精确定位,含 SP/特别篇)。与 `ep` 同时存在且冲突时 **`ep_id` 优先** |
| `title` | query | 可选 | 字符串(URL 编码) | 番剧标题。用于接收方无 Bangumi 数据时的搜索兜底与展示 |
| `src` | query | 可选 | 字符串 | 发送方标识(如 `animetrack`),供接收方统计或差异化适配 |
| `callback` | query | 可选 | URI(URL 编码) | 进度回传地址,见 §4。发送方提供即表示期望接收回传 |

### 2.3 集数标识的两种粒度(重要)

| 标识 | 优点 | 缺点 | 协议定位 |
|---|---|---|---|
| `ep`(sort 序号) | 接收方零成本消费,人类可读 | 含 SP/特别篇/分段放送时可能与播放资源实际集数**偏移** | **推荐必填** |
| `ep_id`(Bangumi episode ID) | 绝对精确无偏移 | 接收方需调用 Bangumi API(`/v0/episodes`)换取 sort | 可选增强 |

发送方若可获取 `ep_id`(如已集成 Bangumi API),建议两个都带;接收方按自身能力选用其一。

### 2.4 接收方行为要求(降级矩阵)

| 情况 | 要求行为 |
|---|---|
| URI 非法(scheme/host/ID 不符) | 静默忽略,不得崩溃 |
| 条目存在、无 `ep` | 打开条目详情页 |
| 条目存在、`ep` 有效 | **直接进入对应集数播放页**(推荐)或详情页并预选该集(最低要求) |
| `ep` 超出可用集数 | 取最近可用集数,或回退详情页;不得报错中断 |
| 本地无该条目数据 | 提示"未找到该番剧";有 `title` 时可提供一键搜索 |
| 无法识别参数(含未来新增) | **必须忽略**,按已识别参数执行 |

---

## 3. 接收方适配指南(播放类应用)

### 3.1 Android

**第一步:声明 intent-filter**(AndroidManifest.xml)

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter android:autoVerify="false">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="anime-play" android:host="subject" />
    </intent-filter>
</activity>
```

**第二步:解析参数**(Kotlin 示例)

```kotlin
data class AnimePlayLink(
    val subjectId: Int,
    val episodeSort: Int?,
    val episodeId: Int?,
    val title: String?,
    val source: String?,
    val callback: String?,
)

fun parseAnimePlayLink(intent: Intent): AnimePlayLink? {
    val data = intent.data ?: return null
    if (data.scheme != "anime-play" || data.host != "subject") return null
    val subjectId = data.pathSegments.firstOrNull()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return AnimePlayLink(
        subjectId = subjectId,
        episodeSort = data.getQueryParameter("ep")?.toIntOrNull(),
        episodeId = data.getQueryParameter("ep_id")?.toIntOrNull(),
        title = data.getQueryParameter("title"),
        source = data.getQueryParameter("src"),
        callback = data.getQueryParameter("callback"),
    )
}
```

注意在 `onCreate` 与 `onNewIntent`(singleTask/singleTop 时)两处都处理。

**第三步:导航**

按 §2.4 降级矩阵导航到播放页/详情页。建议在应用冷启动场景做好"导航栈就绪后再跳转"的延迟处理(等待首帧渲染后再执行导航,避免跳转丢失)。

### 3.2 iOS

在 `Info.plist` 注册 `CFBundleURLTypes`(scheme `anime-play`),于 `SceneDelegate`/`AppDelegate` 的 URL 回调中按相同规则解析。

### 3.3 桌面端

- Windows:注册表 `HKCU\Software\Classes\anime-play` 协议注册;
- macOS:`CFBundleURLTypes`;
- Linux:`.desktop` 文件 `MimeType=x-scheme-handler/anime-play`。

### 3.4 关于"多个播放器声明同一 scheme"

这是协议的**预期行为而非问题**:用户安装多个播放器时,Android 会弹出系统选择器(或发送方自建选择器),由用户选择用哪个播放器观看。请**不要**通过 `android:priority` 等手段争抢默认,选择权属于用户。

---

## 4. 进度回传规范(推荐实现)

### 4.1 机制

发送方在跳转 URI 中携带 `callback` 参数(类似 OAuth 的 `redirect_uri`)。接收方在**用户看完一集或退出播放**时,以 `ACTION_VIEW` 调起回调 URI 并附加进度参数。此设计使**任何发送方**都能收到回传,无需接收方硬编码特定应用。

### 4.2 回传 URI 格式

以发送方提供的 `callback` 为基础 URI,附加以下 query 参数:

```
{callback}?subject={bangumiSubjectId}&ep={episodeSort}&completed={true|false}&position_ms={可选}
```

| 参数 | 必填 | 语义 |
|---|---|---|
| `subject` | 必填 | 原样回传条目 ID |
| `ep` | 必填 | **刚看完/正在看的那一集**的 sort 序号 |
| `completed` | 必填 | `true` = 该集看完;`false` = 中途退出 |
| `position_ms` | 可选 | 中途退出时的播放位置(毫秒),发送方无单集内进度模型时可忽略 |

### 4.3 接收方实现示例(Kotlin)

```kotlin
fun reportProgress(link: AnimePlayLink, ep: Int, completed: Boolean, positionMs: Long? = null) {
    val callback = link.callback ?: return
    val uri = Uri.parse(callback).buildUpon()
        .appendQueryParameter("subject", link.subjectId.toString())
        .appendQueryParameter("ep", ep.toString())
        .appendQueryParameter("completed", completed.toString())
        .apply { positionMs?.let { appendQueryParameter("position_ms", it.toString()) } }
        .build()
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } // 回传失败静默,不影响播放
}
```

### 4.4 发送方处理要求(防伪造与一致性)

1. **只增不减**:仅当回传的 `ep > 本地当前进度` 且 `completed=true` 时更新进度;数值回退视为异常,忽略;
2. **范围校验**:`ep` 超过条目总集数(若已知)时忽略;
3. **不执行任意动作**:回调 URI 只承载进度数据,发送方不得通过它开放任意功能;
4. 接收方同理:`callback` 只用于 `ACTION_VIEW` 唤起,不得向其 POST 或附加敏感信息。

---

## 5. 发送方适配指南(管理类应用)

1. **构造 URI**:以本地 Bangumi 条目 ID 为主键;目标集数按业务语义计算(常见为 `已看集数 + 1`;已看完则不带 `ep` 回退详情页级);
2. **携带 callback**:若自身实现了回传接收(声明自有 scheme 的 intent-filter),附 `callback` 参数;
3. **安装探测**(Android 11+):在自身 Manifest 的 `<queries>` 中声明

```xml
<intent>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="anime-play" android:host="subject" />
</intent>
```

再用 `PackageManager.resolveActivity` 检测;未安装时给出引导文案而非静默失败;
4. **启动**:`Intent(ACTION_VIEW, uri)`,建议加 `FLAG_ACTIVITY_NEW_TASK` 保证任意 Context 安全;
5. **多播放器选择**(可选):生态内有多个接收方时,发送方可自建选择器(用 `queryIntentActivities` 枚举),避免依赖系统弹窗。

---

## 6. 兼容性与演进规则

1. 接收方**必须忽略**无法识别的 query 参数(前向兼容的根本保证);
2. 新增参数只加不改:已有参数的语义永不变更;语义变更必须新增参数名;
3. `anime-play` scheme 与 `subject` host 为协议根,仅在重大版本(v2)时变更;
4. 接收方可同时声明本协议 scheme 与自有品牌 scheme(如 Animeko 的 `ani://`),互不冲突。

---

## 7. 测试与验证

### 7.1 接收方自测(adb)

> 前提:设备上需已安装声明了本协议的接收方,否则报 `Activity not started`(生态早期可先用最小测试接收器自测)。命令末尾的包名用于强制定向到指定接收方,省略时走系统解析(存在多个接收方时会弹出选择框)。

```bash
# 详情页级
adb shell am start -a android.intent.action.VIEW -d "anime-play://subject/12" <你的包名>

# 集数级(第 5 集)
adb shell am start -a android.intent.action.VIEW -d "anime-play://subject/12?ep=5" <你的包名>

# 非法输入防御(不应崩溃)
adb shell am start -a android.intent.action.VIEW -d "anime-play://subject/abc" <你的包名>
# URI 含 & 时必须整条命令包裹引号,否则 & 会被 shell 当作命令分隔符截断 URI
adb shell "am start -a android.intent.action.VIEW -d 'anime-play://subject/12?ep=xyz&unknown_param=1' <你的包名>"
```

**验收清单**:□ 详情页可达 □ 集数直达(或预选)□ ep 越界优雅降级 □ 非法 URI 静默 □ 未知参数忽略 □ 冷启动/热启动均可达

### 7.2 回传链路联测

发送方与接收方各装一台设备(或同机),跳转→播放→退出,验证:
□ 接收方发出回调(logcat 可见)□ 发送方进度只增不减 □ 伪造回传(adb 手动发送恶意 ep=999)被忽略

### 7.3 发送方自测

`<queries>` 安装探测需在应用内验证:Android 11+ 包可见性限制只作用于应用自身进程,应用内 `PackageManager.resolveActivity` 在未安装接收方时应返回 `null` 并走引导文案分支(而非静默失败)。

```bash
# 确认设备上是否有应用声明了本协议。以 shell 身份运行,不受 <queries> 包可见性限制,
# 不能用于验证 <queries> 是否生效,仅用于确认接收方 intent-filter 已正确注册
adb shell cmd package resolve-activity -a android.intent.action.VIEW -d "anime-play://subject/12"
```

---

## 8. 参考实现与生态

| 应用 | 角色 | 状态 |
|---|---|---|
| AnimeTrack | 发送方(跳转 URI 构造、安装探测、降级) | ✅ 已实现(2026-10,`domain/externalplayer` 框架);回传接收端规划中 |
| [Animeko](https://github.com/open-ani/animeko) | 接收方(现有 `ani://subjects/{id}` 详情页级能力,本协议适配待社区推动) | 提议中,欢迎向 open-ani/animeko 提交 issue/PR |

> 实现了本协议的应用(无论发送方还是接收方),欢迎补充到本表。

---

## 9. FAQ

**Q:为什么不直接用 Animeko 的 `ani://`?**
`ani://` 是 Animeko 的私有 scheme,其他播放器声明它会与 Animeko 冲突。开放协议使用中性 scheme,任何应用均可平等加入。

**Q:为什么不是 App Links(https)?**
App Links 需要域名所有权验证,而本协议无主域名——它是一个社区协议,不是某家公司的私有服务。自定义 scheme 无此门槛。

**Q:番剧在 Bangumi 上没有条目怎么办?**
以 Bangumi ID 为主键是协议的基本前提。无 Bangumi 条目的内容不在本协议范围;`title` 参数仅作展示兜底,不构成定位依据。

**Q:回传必须有 Bangumi 账号吗?**
不需要。回传是纯 App 间 URI 机制。Bangumi 云端同步(如 Animeko 内置)可作为并行的另一条链路,与本协议互不依赖。

---

## 10. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 草案 | 2026-10-13 | 初版:跳转 URI + 进度回传 + 双方适配指南 |
