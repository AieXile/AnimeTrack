# 定制导航栏页面 UI 统一优化计划

## 现状问题分析

当前 `NavigationCustomizeScreen.kt` 存在以下 UI 不统一问题：

1. **分组标题样式不一致**：使用裸 `Text` + primary 色 + 手动 padding 作为分组标题，与 `AppearanceScreen` 中的 `SettingsGroup` 组件风格完全不同
2. **选项卡片样式各异**：
   - `NavigationStyleCard`：带预览图 + 文字 + RadioButton，使用 `surfaceContainer` 背景 + border
   - `FabLocationCard`：纯文字 + RadioButton，同样 `surfaceContainer` + border，但无预览图
   - `NavigationItem`（Switch 项）：使用 M3 `ListItem`，无卡片容器，裸露在 LazyColumn 中
   - `CustomGreetingField`：`surfaceContainer` 卡片内嵌 `TextField`
3. **视觉层次混乱**：RadioButton 选项卡和 Switch 列表项混排，没有统一的容器包裹
4. **间距不统一**：各 section 之间用 `Spacer(8.dp)` 分隔，但内容项之间用 `Arrangement.spacedBy(12.dp)`，缺乏一致的节奏感

## 优化方案

### 核心思路

将 `SettingsGroup` 从 `AppearanceScreen` 提取为共享组件，所有设置子页面统一使用 `SettingsGroup` 作为分组容器，内部项统一为两种类型：
- **选择项**（RadioButton）：卡片 + 预览图/图标 + 文字 + RadioButton
- **开关项**（Switch）：ListItem 样式，统一在 SettingsGroup 容器内

### 具体改动

#### 1. 提取 `SettingsGroup` 为共享组件

将 `AppearanceScreen.kt` 中的 `SettingsGroup` 移至独立文件 `SettingsGroup.kt`，两个页面共用。

#### 2. 重构 `NavigationCustomizeScreen` 布局

**新布局结构：**

```
LazyColumn
├── SettingsGroup("导航栏样式")
│   ├── NavigationStyleCard(BOTTOM)    ← 保留预览图，统一卡片风格
│   ├── Spacer(8dp)
│   └── NavigationStyleCard(CAPSULE)   ← 保留预览图，统一卡片风格
│
├── SettingsGroup("导航栏内容", subtitle="选择在导航栏中显示的入口")
│   ├── SwitchItem("显示收藏", ...)
│   ├── HorizontalDivider
│   ├── SwitchItem("显示时间线", ...)
│   ├── HorizontalDivider
│   └── SwitchItem("显示追番看板", ...)
│
├── SettingsGroup("添加番剧按钮位置")
│   ├── FabLocationCard(BOTTOM_RIGHT)  ← 统一为与 NavigationStyleCard 相同风格
│   ├── Spacer(8dp)
│   └── FabLocationCard(TOP_BAR)
│
├── SettingsGroup("自定义欢迎语", subtitle="设置首页标题栏显示的欢迎语")
│   └── CustomGreetingField(...)
```

#### 3. 统一选项卡片风格

`NavigationStyleCard` 和 `FabLocationCard` 统一为 `SelectionCard` 组件：
- `surfaceContainer` 背景 + `RoundedCornerShape(16.dp)`
- 选中态：2dp primary 边框
- 未选中态：1dp outlineVariant 边框
- 内容：标题 + 描述 + 可选预览图 + RadioButton

`FabLocationCard` 增加简单图标预览（用 Box/Icon 模拟 FAB 位置），与 `NavigationStyleCard` 视觉权重一致。

#### 4. 统一 Switch 项风格

`NavigationItem` 改为 `SwitchItem`，在 `SettingsGroup` 容器内使用，项之间用 `HorizontalDivider` 分隔，不再使用 M3 `ListItem`（与 SettingsGroup 的内边距冲突），改为自定义布局：
- 标题 + 描述（左） + Switch（右）
- 统一 padding 和间距

#### 5. 统一 CustomGreetingField

移除 `CustomGreetingField` 自身的卡片容器（`surfaceContainer` 背景 + clip + padding），因为外层 `SettingsGroup` 已提供容器。只保留 `TextField` 和提示文字。

### 修改文件清单

| 文件 | 操作 |
|------|------|
| `ui/settings/SettingsGroup.kt` | 新建 — 从 AppearanceScreen 提取共享组件 |
| `ui/settings/AppearanceScreen.kt` | 修改 — 删除私有 SettingsGroup，改用共享版本 |
| `ui/settings/NavigationCustomizeScreen.kt` | 重写 — 使用 SettingsGroup 分组，统一卡片和 Switch 风格 |
