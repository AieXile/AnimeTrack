<h2 align="center">AnimeTrack</h2>

<p align="center">
  <img src="https://img.shields.io/badge/version-v0.3.1--alpha-blue?style=flat-square" alt="Version">
  <img src="https://img.shields.io/badge/platform-Android-green?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square" alt="License">
</p>

**AnimeTrack** 是一款遵循 Material Design 3 规范的安卓追番记录工具。它围绕“看过什么”以及“何时观看”这两个核心需求，提供了一套轻量但完整的本地管理流程。

你可以通过 Bangumi 搜索并添加感兴趣的动画，标记为 **想看**、**在看** 或 **已看**，应用会自动记下每次完成观看的时间。所有已看完的记录汇聚在时间线中，让你能回顾自己的补番轨迹；看板界面则会根据已添加的连载作品，展示当日更新的动画。

---

<h2 align="center">主要功能</h2>

- **Bangumi 搜索添加** – 直接搜索动画名称，自动拉取封面与基本信息，无需手动填写。
- **多状态管理** – 将动画归类为「想看」「在看」「已看」，满足完整的追踪流程。
- **时间线回顾** – 按照时间顺序查看自己什么时候看完了哪部动画，方便回顾补番历程。
- **追番看板** – 已添加的连载作品会展示每周几更新，并在看板中列出今日更新的动画列表。
- **MD 文件导入** – 支持通过 Markdown 文件批量导入已看记录，适合从其他工具迁移数据。
- **Material Design 3** – 基于 Jetpack Compose 构建，界面适配动态取色与深色模式，交互顺手。
- **持续扩展的自定义选项** – 更多个性化设置正在开发中，后续将陆续开放。

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

- [ ] **Bangumi 账号同步** – 登录后直接同步云端记录，避免丢失，并支持双向更新。
- [ ] **观看记录导出为 MD** – 将本地记录按时间线导出为可读的 Markdown 文件，方便备份或分享。
- [ ] **时间线报表** – 基于观看历史自动生成周报、月报或年报，以简单图表呈现追番趋势。

---

<h2 align="center">快速开始</h2>

### 环境要求
- Android 8.0 及以上设备

### 安装
前往 [Releases 页面](https://github.com/AieXile/AnimeTrack/releases) 下载最新 APK 文件，直接安装即可。

> 注意：当前为 alpha 版本，功能仍在完善中，使用过程中如遇问题欢迎提交 Issue。

---

<h2 align="center">许可证</h2>

本项目基于 [MIT License](LICENSE) 开源。