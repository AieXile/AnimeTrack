# Bilibili 登录与追番同步功能

## Summary
在设置页顶部添加 Bilibili 登录入口，支持 Web 二维码登录，登录后可拉取用户追番列表（名字、封面、简介、更新状态、观看进度）并同步到本地数据库。

## Current State Analysis
- **AuthManager**：现有 Bangumi OAuth 登录体系，DataStore 持久化，AuthInterceptor 自动注入 Bearer Token
- **RetrofitClient**：Bangumi API 客户端（bgm.tv），6s 超时，AuthInterceptor 仅对 bgm.tv 域名注入 Token
- **BangumiSyncManager**：现有 Bangumi 同步逻辑，拉取远程收藏列表 → mergeCollectionItem → 入库
- **Anime 模型**：Room Entity，含 bangumiId（唯一索引）、title、coverUrl、summary、watchedEpisodes、status、isFinished 等
- **SettingsScreen**：LazyColumn 卡片列表，顶部无登录区域
- **AppContainer**：单例依赖注入，初始化 AuthManager、BangumiSyncManager 等
- **技术文档**：bilibili-auth-wbi-bangumi-technical-analysis.md 提供了完整的 Wbi 签名算法、追番 API 数据模型、Cookie 管理方案

## Proposed Changes

### 1. 新建 BilibiliAuthManager.kt — B站认证管理
- 路径：`data/auth/BilibiliAuthManager.kt`
- 使用独立 DataStore（`bilibili_auth`）存储 SESSDATA、bili_jct、mid
- 提供 `isLoggedIn: Flow<Boolean>`、`sessData: Flow<String?>`、`mid: Flow<Long?>`
- 提供 `saveSession(sessData, biliJct, mid)`、`logout()`
- 内存缓存 + DataStore 双写（参考现有 AuthManager 模式）

### 2. 新建 BilibiliApiService.kt — B站 API 接口
- 路径：`data/network/BilibiliApiService.kt`
- 定义 B站追番列表相关数据模型：
  - `BilibiliFollowResponse`、`BilibiliFollowData`、`BilibiliFollowItem`
  - `BilibiliNavResponse`、`BilibiliWbiImg`（Wbi 密钥）
  - `BilibiliQrCodeResponse`、`BilibiliQrCodePollResponse`（二维码登录）
- 定义 Retrofit 接口 `BilibiliApiService`：
  - `generateQrCode()` — 生成二维码
  - `pollQrCode(qrCodeKey)` — 轮询二维码状态
  - `getNavInfo()` — 获取 Wbi 密钥
  - `getMyFollowBangumi(vmid, type, pn, ps)` — 获取追番列表

### 3. 新建 WbiUtils.kt — Wbi 签名算法
- 路径：`util/WbiUtils.kt`
- 完整实现技术文档中的 WbiUtils.sign() 算法
- mixinKeyEncTab 混淆表、getMixinKey、filterIllegalChars、MD5

### 4. 新建 WbiKeyManager.kt — Wbi 密钥缓存
- 路径：`data/network/WbiKeyManager.kt`
- 24h 缓存 + Mutex 防并发
- `getWbiKeys(): Result<Pair<String, String>>` — 获取密钥对
- 冷启动时从 SP 恢复

### 5. 修改 RetrofitClient.kt — 添加 B站 API 客户端
- 新增 `BILIBILI_BASE_URL = "https://api.bilibili.com/"`
- 新增 `bilibiliOkHttpClient`：配置 CookieJar（自动注入 SESSDATA/bili_jct/buvid3）、User-Agent、Referer
- 新增 `bilibiliApi: BilibiliApiService` Retrofit 实例

### 6. 新建 BilibiliSyncManager.kt — B站追番同步
- 路径：`data/sync/BilibiliSyncManager.kt`
- `syncFollowListToDb()` — 分页拉取追番列表，转换为 Anime 对象入库
- B站 followStatus 映射：2=正在观看, 3=已看完, 1=计划观看, 4=已弃番
- **【防崩溃】progress 解析**：B站进度文案格式多变（"看到第5话"、"看到第5.5话"、"看到OVA2"、"看到SP"、空字符串），必须用正则提取数字后 `toIntOrNull()` 做极端异常捕获。解析失败或非数字时 watchedEpisodes 默认设为 0，绝不崩溃
- B站 isFinish 映射到 isFinished
- **【防盗链】封面下载**：B站图片服务器（*.hdslb.com）有严格防盗链，CoverDownloader 下载 B站图片时必须附带 `Referer: https://www.bilibili.com/`，否则返回 403
- **标题匹配关联**：用 B站追番标题在本地数据库模糊匹配已有番剧，匹配到则合并（取 max watchedEpisodes），未匹配则新建（bangumiId=null）

### 7. 修改 AppContainer.kt — 注册 Bilibili 组件
- 新增 `bilibiliAuthManager: BilibiliAuthManager`
- 新增 `bilibiliSyncManager: BilibiliSyncManager`
- 新增 getter 方法

### 8. 新建 BilibiliLoginScreen.kt — B站登录界面
- 路径：`ui/settings/BilibiliLoginScreen.kt`
- 显示二维码（使用 Coil 加载二维码图片）
- **【防泄漏】轮询逻辑**：必须包裹在 `LaunchedEffect` 生命周期块中，使用 `while(isActive)` 循环 + `delay(3000)` 轮询扫码状态。当用户退出页面时，LaunchedEffect 自动 Cancel 协程，确保轮询立即停止，绝不泄漏
- 扫码成功后提取 Cookie 保存到 BilibiliAuthManager
- 显示登录状态（已登录显示头像和昵称、退出登录按钮、同步追番按钮）

### 9. 修改 SettingsScreen.kt — 添加 Bilibili 登录入口
- 在 LazyColumn 顶部（SettingCard 列表之前）添加 Bilibili 登录卡片
- 未登录：显示"登录 Bilibili"按钮，点击跳转 BilibiliLoginScreen
- 已登录：显示头像、昵称、"同步追番"按钮
- 样式：与现有 Bangumi 登录头像区域类似

### 10. 修改 MainActivity.kt — 添加 BilibiliLogin 页面路由
- 新增 `Screen.BilibiliLogin` sealed 子类
- 添加 BilibiliLoginScreen 的 Composable 渲染
- 添加导航回调 `onNavigateBilibiliLogin`
- 添加过渡动画（与其他设置子页面一致）

### 11. 修改 CoverDownloader.kt — B站图片防盗链处理
- 检查现有 CoverDownloader 的 OkHttpClient
- 当下载 URL 的 host 包含 `hdslb.com` 时，请求头必须附带 `Referer: https://www.bilibili.com/`
- 可通过添加 OkHttp Interceptor 实现：检测 URL host，若为 hdslb.com 则添加 Referer header

## Assumptions & Decisions
- 登录方式：仅 Web 二维码登录（最简单、最安全，无需手机号）
- B站追番为**单向同步**（B站→本地），不推送本地修改到 B站
- **标题匹配关联**：用 B站追番标题在本地模糊匹配，匹配到则合并，未匹配则新建（bangumiId=null）
- Wbi 签名仅用于需要签名的接口，追番列表接口目前不需要 Wbi 签名
- B站 Cookie 使用独立 DataStore 存储，与 Bangumi Token 隔离
- 追番同步为手动触发（点击按钮），不自动定时同步
- **progress 解析安全**：toIntOrNull() 兜底，非数字进度默认 0，绝不崩溃
- **协程生命周期**：轮询必须在 LaunchedEffect 中，退出页面自动取消
- **B站图片防盗链**：hdslb.com 必须带 Referer

## Verification
- 构建成功
- 未登录时设置页显示"登录 Bilibili"入口
- 二维码扫码登录成功后显示用户信息
- 退出二维码页面时轮询协程立即停止（无泄漏）
- 点击同步追番能拉取列表并入库
- progress 为非数字文案（如"看到OVA2"）时不崩溃，watchedEpisodes 为 0
- B站封面图片能正常下载（Referer 防盗链生效）
- 标题匹配能正确关联已有番剧
- 退出登录后清除 Cookie 和本地状态
