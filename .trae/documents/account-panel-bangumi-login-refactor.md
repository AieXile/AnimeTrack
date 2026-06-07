# 账号状态管理面板 & Bangumi 登录下沉计划

## 摘要
将主界面头像按钮点击后的 Bangumi 网页登录逻辑拆除，改为弹出「账号状态管理面板」Dialog，同时包含 Bilibili 和 Bangumi 两个条目。将 Bangumi 登录从 HomeScreen 的 Dialog 下沉到设置界面 LoginScreen 的三级界面。

## 当前状态分析

### 现有架构
- **HomeScreen.kt**：头像按钮点击 → `if (isLoggedIn) showProfileDialog else showLoginDialog`
- **LoginDialog.kt** (`ui/login/LoginScreen.kt`)：Bangumi WebView OAuth 登录弹窗
- **ProfileDialog.kt** (`ui/login/ProfileDialog.kt`)：Bangumi 个人资料弹窗（头像、昵称、Bangumi ID、同步按钮、退出登录）
- **AuthManager.kt** (`data/auth/AuthManager.kt`)：Bangumi 认证管理（access_token, refresh_token, isLoggedIn, userAvatar, userNickname, userBangumiId, customAvatarUri）
- **BilibiliAuthManager.kt** (`data/auth/BilibiliAuthManager.kt`)：B站认证管理（sessData, biliJct, mid, isLoggedIn, userAvatar, userNickname, lastSyncTime）
- **LoginScreen.kt** (`ui/settings/LoginScreen.kt`)：设置二级界面，目前只有 Bilibili 登录入口
- **Screen 路由**：`Screen.Login` → `Screen.BilibiliLogin`（二级→三级）

### 关键数据流
- `AuthManager.userAvatar`：优先返回 customAvatarUri（本地文件路径），否则返回 Bangumi 头像 URL
- `AuthManager.isLoggedIn`：Bangumi 登录状态
- `BilibiliAuthManager.isLoggedIn`：B站登录状态
- HomeScreen 中 `headerState.isLoggedIn` 和 `headerState.userAvatar` 仅来自 Bangumi AuthManager

## 修改方案

### 1. 新建 BangumiLoginScreen（三级界面）
**文件**: `ui/settings/BangumiLoginScreen.kt`（新建）

将 `LoginDialog.kt` 中的 WebView OAuth 登录逻辑迁移为独立 Screen：
- 使用 `Scaffold` + `TopAppBar`（与其他设置界面一致）
- 内嵌 WebView 进行 Bangumi OAuth 登录
- 登录成功后自动返回上一页
- 复用 `LoginViewModel` 的 `fetchAccessToken` 逻辑
- 参数：`onBack: () -> Unit`
- 登录成功回调：`onLoginSuccess: () -> Unit`

### 2. 修改 LoginScreen（添加 Bangumi 条目）
**文件**: `ui/settings/LoginScreen.kt`

- 添加 `onNavigateBangumiLogin: () -> Unit` 回调参数
- 添加 Bangumi LoginServiceCard，显示 Bangumi 登录状态（读取 AuthManager.isLoggedIn / userNickname）
- Bangumi 卡片图标使用 `Icons.Default.Person`

### 3. 添加 Screen.BangumiLogin 路由
**文件**: `MainActivity.kt`

- 添加 `data object BangumiLogin : Screen()` 到 sealed class
- settingsSubPages 添加 `Screen.BangumiLogin::class`
- 添加 BangumiLogin ↔ Login 过渡动画
- BackHandler 添加 `is Screen.BangumiLogin -> Screen.Login`
- 渲染 BangumiLoginScreen Composable
- LoginScreen 的 onNavigateBangumiLogin 传递

### 4. 新建 AccountPanelDialog（账号状态管理面板）
**文件**: `ui/home/AccountPanelDialog.kt`（新建）

替换 HomeScreen 中的 LoginDialog + ProfileDialog，统一为一个 Dialog：

**UI 结构**：
```
AlertDialog
├── 顶部：大头像（可点击选择本地图片）+ 显示名称
├── Bilibili 条目行
│   ├── 左：B站图标（未登录灰色/已登录默认色）
│   ├── 中："Bilibili" + 状态文字（未登录:"点击绑定B站账号" / 已登录:昵称）
│   ├── 右：状态小点（绿=已连接 ⭕，灰=未连接 ❌）+ 箭头
│   └── 点击交互：
│       未登录 → 关闭Dialog → 直接导航 Screen.BilibiliLogin
│       已登录 → 弹窗"解除绑定"/"重新登录"
├── Bangumi 条目行
│   ├── 左：Bangumi 图标（未登录灰色/已登录默认色）
│   ├── 中："Bangumi" + 状态文字（未登录:"点击绑定Bangumi" / 已登录:昵称）
│   ├── 右：状态小点（绿=已连接 ⭕，灰=未连接 ❌）+ 箭头
│   └── 点击交互：
│       未登录 → 关闭Dialog → 直接导航 Screen.BangumiLogin
│       已登录 → 弹窗"注销登录"
└── 底部：自定义头像按钮（选择本地图片/清除自定义头像）
```

**状态逻辑**：
- 读取 `AuthManager.isLoggedIn` / `BilibiliAuthManager.isLoggedIn`
- 读取两个 AuthManager 的 `userAvatar` / `userNickname`
- 读取 `AuthManager.customAvatarUri`
- 头像显示优先级：customAvatarUri > Bilibili头像 > Bangumi头像 > 默认Person图标
- 显示名称：customAvatarUri存在时用Bangumi昵称或Bilibili昵称，否则用已登录的昵称

**交互逻辑**：
- Bilibili 未登录：回调 `onNavigateBilibiliLogin()`，Dialog 关闭
- Bilibili 已登录：弹出 AlertDialog 选项"解除绑定"（logout）/ "重新登录"（logout后导航）
- Bangumi 未登录：回调 `onNavigateBangumiLogin()`，Dialog 关闭
- Bangumi 已登录：弹出 AlertDialog 选项"注销登录"（logout）
- 点击头像：打开图片选择器，保存为 customAvatarUri
- 已有自定义头像时：显示"更换头像"和"恢复默认"选项

**回调参数**：
```kotlin
@Composable
fun AccountPanelDialog(
    onDismiss: () -> Unit,
    onNavigateBilibiliLogin: () -> Unit,
    onNavigateBangumiLogin: () -> Unit
)
```

### 5. 修改 HomeScreen
**文件**: `ui/home/HomeScreen.kt`

- 删除 `showLoginDialog` / `showProfileDialog` 两个状态
- 删除 `LoginDialog` / `ProfileDialog` 的 import 和调用
- 添加 `showAccountPanel` 状态
- 头像按钮点击统一：`onAvatarClick = { showAccountPanel = true }`
- 添加 `onNavigateBilibiliLogin: () -> Unit` 和 `onNavigateBangumiLogin: () -> Unit` 参数
- AccountPanelDialog 中的导航回调：关闭 Dialog + 设置 currentScreen
- **头像显示逻辑修改**：
  - 新增读取 `BilibiliAuthManager.isLoggedIn` 和 `BilibiliAuthManager.userAvatar`
  - `isLoggedIn` 改为：Bilibili 或 Bangumi 任一已登录即为 true
  - `userAvatar` 改为优先级：customAvatarUri > Bilibili头像 > Bangumi头像

### 6. 修改 AnimeGridHeaderState
**文件**: `ui/home/HomeScreen.kt`

- `isLoggedIn` 改为综合判断（Bilibili || Bangumi）
- `userAvatar` 改为优先级：customAvatarUri > Bilibili头像 > Bangumi头像

### 7. 修改 MainActivity
**文件**: `MainActivity.kt`

- HomeScreen 的 MainPagerContent 添加 `onNavigateBilibiliLogin` / `onNavigateBangumiLogin` 回调
- MainPagerContent 参数列表添加这两个回调
- 回调实现：`lastMainPageIndex = pagerState.currentPage; currentScreen = Screen.BilibiliLogin/BangumiLogin`
- 注意：从 Main 直接跳到三级界面，需要确保过渡动画正确

### 8. 清理旧文件
- `ui/login/LoginDialog.kt`（即 `ui/login/LoginScreen.kt`）→ 删除（逻辑已迁移到 BangumiLoginScreen）
- `ui/login/ProfileDialog.kt` → 删除（逻辑已合并到 AccountPanelDialog）
- `ui/login/LoginViewModel.kt` → 保留（BangumiLoginScreen 复用）

## 导航层级

```
主界面头像 → AccountPanelDialog（弹窗）
  ├── Bilibili 未登录点击 → 关闭弹窗 → 直接跳 Screen.BilibiliLogin（三级）
  ├── Bangumi 未登录点击 → 关闭弹窗 → 直接跳 Screen.BangumiLogin（三级）
  ├── Bilibili 已登录点击 → 弹出操作选项（解除绑定/重新登录）
  ├── Bangumi 已登录点击 → 弹出操作选项（注销登录）
  └── 自定义头像 → 图片选择器

设置 → "登录" → LoginScreen（二级）
  ├── Bilibili 卡片 → BilibiliLoginScreen（三级）
  └── Bangumi 卡片 → BangumiLoginScreen（三级）
```

## 假设与决策

1. **BangumiLoginScreen 直接复用 LoginViewModel**：不新建 ViewModel，因为 fetchAccessToken 逻辑完全一致
2. **AccountPanelDialog 中的"解除绑定"**：对于 Bilibili，执行 `bilibiliAuthManager.logout()`；对于 Bangumi，执行 `authManager.logout()`
3. **"重新登录"**：解除绑定后自动跳转到对应登录界面
4. **头像优先级**：customAvatarUri > Bilibili > Bangumi > 默认图标（用户确认：自定义优先）
5. **Dialog 中导航**：AccountPanelDialog 通过回调通知 HomeScreen 执行导航，Dialog 自身先关闭
6. **BangumiLoginScreen 的 WebView**：与 LoginDialog 相同的 WebView 实现，但包裹在 Scaffold 中作为全屏页面
7. **从主界面直接跳三级页面**：AccountPanelDialog 关闭后直接导航到 Screen.BilibiliLogin 或 Screen.BangumiLogin，不经过 LoginScreen 中间层

## 验证步骤

1. 构建验证：`gradlew assembleDebug` 无错误
2. 功能验证：
   - 主界面头像点击 → 弹出 AccountPanelDialog
   - Dialog 中 Bilibili/Bangumi 状态正确显示（小点、文字）
   - 未登录点击 → 关闭弹窗 → 直接跳到对应登录页
   - 已登录点击 → 弹出操作选项
   - 自定义头像选择、更换、恢复默认
   - BangumiLoginScreen WebView 登录流程正常
   - 设置中 LoginScreen 两个条目均可正常导航
   - 返回键逻辑正确
