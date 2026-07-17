<h2 align="center">AnimeTrack</h2>

<p align="center">
  <img src="https://img.shields.io/badge/version-v0.4.5--beta-blue?style=flat-square" alt="Version">
  <img src="https://img.shields.io/badge/platform-Android-green?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square" alt="License">
</p>

**AnimeTrack** 是一款遵循 Material Design 3 规范的安卓追番记录工具。它围绕"看过什么"以及"何时观看"这两个核心需求，提供了一套从搜索、追番、观看到回顾的完整闭环。

你可以通过 Bangumi、TMDB 搜索并添加动画，标记为 **想看**、**在看** 或 **已看**，应用会自动记下每次完成观看的时间。所有已看完的记录汇聚在时间线中，让你能回顾自己的补番轨迹；看板界面则会根据已添加的连载作品，展示当日更新的动画列表。内置播放器支持从 WebDAV 直接播放，多端同步让数据不再局限于本地。

---

<h2 align="center">主要功能</h2>

### 追番与播放
- **多数据源搜索** – 支持 Bangumi、TMDB 双数据源搜索匹配，自动拉取封面、集数、开播日期等基本信息。当某部作品缺失数据源时，可在详情页点击匹配按钮手动搜索补全，支持按 Bangumi 或 TMDB 分别匹配。
- **多状态管理** – 将动画归类为「想看」「在看」「已看」三种状态，满足完整的追踪流程。在看作品可记录当前观看集数，看完时自动写入完成时间。
- **多季合集** – 自动识别同系列多季作品（支持「第X季/期/章」、罗马数字、Final Season、最终季等多种命名规则），按系列分组并以卡片堆叠形式展示。左右滑动可在季与季之间切换，无需在列表中翻找。
- **内置播放器** – 集成 ExoPlayer 播放器，支持从 WebDAV 远程目录直接播放本地番剧资源，观看进度与本地记录联动，看完自动更新集数。
- **时间线回顾** – 按时间顺序查看自己什么时候看完了哪部动画，方便回顾补番历程，支持按月份浏览历史记录。
- **追番看板** – 已添加的连载作品会展示每周几更新，并在看板中按星期分组列出今日更新的动画列表，一键跳转到对应作品详情。
- ~~**更新推送提醒** – 通过 WorkManager 与极光推送，在番剧更新当日按时提醒。（暂时不开放）~~

### 同步与备份
- **AnimeTrack 账号同步** – 注册登录自有后端，订阅数据云端双向同步。登录后自动拉取云端数据，本地增删改也会实时上传，多设备数据保持一致。支持头像上传、修改密码等账号管理。
- **Bangumi 同步** – 双向同步 Bangumi 收藏状态与观看进度。可将本地记录推送到 Bangumi 收藏夹，也可拉取 Bangumi 的标记合并到本地，避免重复维护。
- **Bilibili 同步** – 一键拉取 B 站追番列表并合并到本地。登录 B 站账号后选择要同步的番剧，自动拉取封面、集数、状态等信息，支持选择性导入。
- **WebDAV 同步** – 通过 WebDAV 远程备份数据库与封面到自建网盘（坚果云、Nextcloud 等），支持自动定时同步，数据掌握在自己手中。
- **Markdown 导入 / 导出** – 支持 Markdown 批量导入已看记录（兼容状态分组、集数信息、完成日期、备注等字段，中英双语关键词均可识别），也可将本地记录按时间线导出为可读的 Markdown 文件，方便备份或迁移到其他工具。
- **ZIP 备份 / 恢复** – 将本地数据库（含 WAL 日志）与封面目录打包为 ZIP 备份，恢复时支持覆盖与合并两种模式（按 bangumiId 或标题去重），换机无忧。

### 个性化与工具
- **Material Design 3** – 基于 Jetpack Compose 构建，适配动态取色与深色模式，遵循 M3 设计规范。
- **多套主题预设** – 内置清透蓝、海洋青、薄荷绿、石板靛、黑白简洁五种配色风格，每种主题采用不同的调色板策略（TONAL_SPOT / VIBRANT / CONTENT / NEUTRAL），呈现差异化的视觉气质。
- **可定制导航栏** – 提供传统沉底与悬浮胶囊两种导航栏样式，支持在导航栏区域左右滑动手指切换页面，选中指示器带弹簧动画跟随手势。
- **引导页** – 首次安装引导流程，快速了解核心功能与权限说明。
- **代理设置** – 内置 Bangumi 反向代理（解决 Bangumi 被墙无法搜索）与全局 HTTP 代理（适配网络受限环境），修改后重启生效。
- **分享卡片** – 生成包含封面、标题、评分、进度的番剧信息卡片，一键分享到社交平台。
- **封面编辑** – 详情页编辑模式支持搜索在线封面、从相册上传自定义封面、保存当前封面到本地相册，三件套满足个性化需求。
- **数据统计** – 记录应用使用时长、添加与完结的番剧数量，可按日 / 月 / 年查看统计，量化你的追番足迹。
- **版本更新检查** – 通过 GitHub Releases 自动检查新版本，对比版本号提示更新，支持查看更新日志。

---

<h2 align="center">应用截图</h2>

<h3>主要界面</h3>
<table width="100%">
  <tr>
    <td width="50%">
      <img src="assets/Screenshot1.png" alt="主界面" style="width: 100%; border-radius: 12px;">
      <br><b>主界面</b>
    </td>
    <td width="50%">
      <img src="assets/Screenshot2.png" alt="时间线" style="width: 100%; border-radius: 12px;">
      <br><b>时间线</b>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="assets/Screenshot3.png" alt="看板" style="width: 100%; border-radius: 12px;">
      <br><b>追番看板</b>
    </td>
    <td width="50%">
      <img src="assets/Screenshot4.png" alt="设置" style="width: 100%; border-radius: 12px;">
      <br><b>设置</b>
    </td>
  </tr>
</table>

<h3>更多细节</h3>
<table width="100%">
  <tr>
    <td width="33%">
      <img src="assets/Screenshot5.png" alt="定制导航栏" style="width: 100%; border-radius: 8px;">
      <br><b>定制导航栏</b>
    </td>
    <td width="33%">
      <img src="assets/Screenshot6.png" alt="功能界面" style="width: 100%; border-radius: 8px;">
      <br><b>功能界面</b>
    </td>
    <td width="33%">
      <img src="assets/Screenshot7.png" alt="导入MD文件" style="width: 100%; border-radius: 8px;">
      <br><b>MD 文件导入</b>
    </td>
  </tr>
</table>

---

<h2 align="center">未来规划</h2>

**已完成**
- [x] **Bangumi 账号同步** – 登录后直接同步云端记录，避免丢失，并支持双向更新。
- [x] **观看记录导出为 MD** – 将本地记录按时间线导出为可读的 Markdown 文件，方便备份或分享。
- [x] **时间线报表** – 基于观看历史自动生成周报、月报或年报，以简单图表呈现追番趋势。

**进行中 / 计划中**
- [ ] **网页与 App 多端同步** – 提供 Web 端访问能力，实现网页与 App 数据实时同步，跨设备无缝衔接。
- [ ] **本地播放与自动记录** – 强化本地播放器，观看进度自动记录到时间线，无需手动标记。

---

<h2 align="center">快速开始</h2>

### 环境要求
- Android 8.0 及以上设备

### 安装
前往 [Releases 页面](https://github.com/AieXile/AnimeTrack/releases) 下载最新 APK 文件，直接安装即可。

> 注意：当前为 beta 版本，功能仍在完善中，使用过程中如遇问题欢迎提交 Issue。

---

<h2 align="center">许可证</h2>

本项目基于 [MIT License](LICENSE) 开源。
