---
name: release-update
description: 执行 AnimeTrack App 的版本发布流程。当用户要求发版、发布新版本、更新版本、打 tag、生成更新日志或 release 时使用。涵盖强制更新确认、版本号确认、生成更新日志到 update 文件夹、git 提交打标推送，推送前必须让用户检查日志。
---

# AnimeTrack 版本发布流程

按以下顺序执行，不得跳过或调换顺序。

## 1. 询问强制更新

开始编写更新日志前，先询问用户本次版本是否为强制更新。

若为强制更新，在更新日志 md 顶部或末尾添加以下任一标记（不区分大小写）：

- `[FORCE_UPDATE]`
- `<!-- force-update -->`

App 端解析时会自动从 changelog 中移除该标记，不会显示给用户；检测到标记后更新弹窗显示"强制更新"（红色），未检测到则显示"非强制更新"（灰色）。

## 2. 确认版本号

先向用户询问是否已更新 versionName 和 versionCode。

若未更新，则自动处理：versionCode 加 1，versionName 末位加 0.0.1（例如 v0.4.3-beta 更新为 v0.4.4-beta）。

## 3. 生成更新日志

文件放入 `update/` 文件夹，文件名必须与待推送的 tag 完全一致：`更新日志_<tag>.md`（例如 tag 为 `v0.7.1`，文件名为 `更新日志_v0.7.1.md`）。GitHub Actions 自动打包 Release 并按此规则读取日志，文件名不匹配会导致 Release body 回退到 commit 列表。

内容要求：

- 只写本次版本的新更新内容，按下方模板撰写
- 必须对比上个版本的实际差异，不能只凭记忆总结
- 上个版本没有的功能修复了 bug，不写入更新日志（属于已有功能的修复也不写）
- 播放器相关功能仅限内部测试，任何内容不写入更新日志

模板：

```markdown
## 📝 更新日志

- 更新版本号为v0.X.X-X

---

#### 新功能

**XXX**

- **XXX** – XXX
- XXX

**XXX**

- **XXX** – XXX

#### XXX

- **XXX** – XXX
- XXX

#### XXX

- XXX
- XXX
```

其中 `####` 小节标题按实际更新类型命名（如 新功能、优化、问题修复）。

## 4. 推送前检查（必须）

打 tag 和推送之前，将更新日志完整内容展示给用户，明确询问用户检查，确认无误后才执行下一步。未经用户确认，禁止提交、打标和推送。

## 5. 提交、打标并推送

用户确认后，灵活运用 GitHub 相关能力提交代码：

```bash
git add .
git commit -m "feat: xxx"
```

打标并推送：

```bash
git tag -a vX.X -m "Release vX.X"
git push origin main
git push origin vX.X
```

推送 tag 后 GitHub Actions 自动打包 Release，更新日志自动读取 `update/` 文件夹。
