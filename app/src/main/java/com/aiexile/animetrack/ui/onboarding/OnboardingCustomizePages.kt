package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.data.NavigationLabelMode
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.BottomNavItem
import com.aiexile.animetrack.ui.components.LiquidGlassKind
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.advancedHazeEffect
import com.aiexile.animetrack.ui.components.chrome.LocalAdvancedBlurConfig
import com.aiexile.animetrack.ui.components.liquidGlassSpec
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.IconPack
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.ThemePreset
import com.aiexile.animetrack.ui.theme.availableThemePresets
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

// ==========================================
// 自定义页 1: 主题外观（选择即时生效，整个向导界面即实时预览）
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingThemePage(
    settingsRepository: SettingsRepository,
    gateState: OnboardingGateState
) {
    val scope = rememberCoroutineScope()
    val themeMode by settingsRepository.themeMode.collectAsState(ThemeMode.SYSTEM)
    // 高亮取点选结果，未点选时回落到持久化值：避免异步写入导致先按旧值渲染再跳变
    val selectedThemeMode = gateState.selectedThemeMode ?: themeMode

    OnboardingStepScaffold(
        step = 1,
        title = stringResource(R.string.onboarding_theme_title),
        description = stringResource(R.string.onboarding_theme_description),
        requirement = OnboardingRequirement(
            text = stringResource(
                if (gateState.selectedThemePreset != null) R.string.onboarding_requirement_theme_done
                else R.string.onboarding_requirement_theme
            ),
            satisfied = gateState.selectedThemePreset != null
        )
    ) {
        Column {
            OnboardingSectionLabel(stringResource(R.string.onboarding_theme_preset_label))
            Spacer(Modifier.height(10.dp))
            // 主题配色：点选后整个向导界面立即变色预览；
            // FlowRow 在窄屏上自动换行，避免末尾色卡被挤压出界
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                availableThemePresets.forEach { preset ->
                    OnboardingSwatchOption(
                        label = preset.displayName,
                        selected = gateState.selectedThemePreset == preset,
                        seedColor = preset.seedColor,
                        onClick = {
                            gateState.selectThemePreset(preset)
                            scope.launch { settingsRepository.setThemePreset(preset) }
                        }
                    )
                }
            }
        }

        Column {
            OnboardingSectionLabel(stringResource(R.string.onboarding_theme_mode_label))
            Spacer(Modifier.height(10.dp))
            // 深浅模式沿用已持久化的默认值（SYSTEM），不参与完成判定
            OnboardingSegmentedRow(
                options = ThemeMode.entries,
                selected = selectedThemeMode,
                label = { stringResource(themeModeLabelRes(it)) },
                onSelect = {
                    gateState.selectThemeMode(it)
                    scope.launch { settingsRepository.setThemeMode(it) }
                }
            )
        }

        Text(
            text = stringResource(R.string.onboarding_requirement_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ==========================================
// 自定义页 2: 导航定制（迷你手机预览框实时渲染所选效果）
// ==========================================
@Composable
internal fun OnboardingNavPage(
    settingsRepository: SettingsRepository,
    gateState: OnboardingGateState
) {
    val scope = rememberCoroutineScope()
    val navigationStyle by settingsRepository.navigationStyle.collectAsState(NavigationStyle.CAPSULE)
    val labelMode by settingsRepository.navigationLabelMode.collectAsState(NavigationLabelMode.ICON_AND_TEXT)
    // 玻璃设置：胶囊预览跟随真实效果渲染（初始值取同步缓存避免首帧闪变）
    val chromeStyle by settingsRepository.chromeStyle
        .collectAsState(settingsRepository.cachedChromeStyle())

    // 预览与高亮都取点选结果，未点选时回落到持久化值：避免异步写入造成「先落第一项再跳变」
    val previewStyle = gateState.selectedNavStyle ?: navigationStyle
    val previewLabelMode = gateState.selectedNavLabelMode ?: labelMode
    val navComplete = gateState.selectedNavStyle != null && gateState.selectedNavLabelMode != null

    OnboardingStepScaffold(
        step = 3,
        title = stringResource(R.string.onboarding_nav_title),
        description = stringResource(R.string.onboarding_nav_description),
        requirement = OnboardingRequirement(
            text = stringResource(
                if (navComplete) R.string.onboarding_requirement_nav_done
                else R.string.onboarding_requirement_nav
            ),
            satisfied = navComplete
        ),
        // 该页内容必然超出小屏视口，只有此页需要滚动提示
        showScrollHint = true
    ) {
        // 迷你手机预览：样式与内容同页展示，改选后立即刷新
        MiniPhonePreview(
            navigationStyle = previewStyle,
            labelMode = previewLabelMode,
            chromeStyle = chromeStyle,
            height = 200.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Column {
            OnboardingSectionLabel(stringResource(R.string.onboarding_nav_style_label))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NavigationStyle.entries.forEach { style ->
                    OnboardingOptionCard(
                        label = style.displayName,
                        selected = gateState.selectedNavStyle == style,
                        onClick = {
                            gateState.selectNavStyle(style)
                            scope.launch { settingsRepository.setNavigationStyle(style) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        NavStylePreview(style = style)
                    }
                }
            }
        }

        Column {
            OnboardingSectionLabel(stringResource(R.string.onboarding_nav_content_label))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NavigationLabelMode.entries.forEach { mode ->
                    OnboardingOptionCard(
                        label = stringResource(mode.labelRes),
                        selected = gateState.selectedNavLabelMode == mode,
                        onClick = {
                            gateState.selectNavLabelMode(mode)
                            scope.launch { settingsRepository.setNavigationLabelMode(mode) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        NavLabelModePreview(mode = mode)
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.onboarding_requirement_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ==========================================
// 完成页：按用户配置渲染的综合预览 + 已选配置摘要
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingReadyPage(settingsRepository: SettingsRepository) {
    val themePreset by settingsRepository.themePreset.collectAsState(ThemePreset.MONO_BLACK)
    val iconPack by settingsRepository.iconPack.collectAsState(IconPack.MATERIAL_SYMBOLS)
    val navigationStyle by settingsRepository.navigationStyle.collectAsState(NavigationStyle.CAPSULE)
    val labelMode by settingsRepository.navigationLabelMode.collectAsState(NavigationLabelMode.ICON_AND_TEXT)
    // 玻璃设置：完成页综合预览同样跟随真实界面风格
    val chromeStyle by settingsRepository.chromeStyle
        .collectAsState(settingsRepository.cachedChromeStyle())

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        // 预览高度随剩余空间伸缩：大屏撑满可用区域，小屏自动收缩避免溢出
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            MiniPhonePreview(
                navigationStyle = navigationStyle,
                labelMode = labelMode,
                chromeStyle = chromeStyle,
                height = (maxHeight - 24.dp).coerceIn(180.dp, 280.dp),
                modifier = Modifier.fillMaxWidth(),
                content = { MiniHomeContentPreview() }
            )
        }

        Spacer(Modifier.height(24.dp))
        OnboardingPageHeader(
            title = stringResource(R.string.onboarding_ready_title),
            description = stringResource(R.string.onboarding_ready_description),
            center = true
        )
        Spacer(Modifier.height(16.dp))

        OnboardingSectionLabel(stringResource(R.string.onboarding_ready_summary))
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnboardingSummaryChip(
                label = stringResource(R.string.onboarding_summary_theme),
                value = themePreset.displayName
            )
            OnboardingSummaryChip(
                label = stringResource(R.string.onboarding_summary_icon),
                value = stringResource(iconPackLabelRes(iconPack))
            )
            OnboardingSummaryChip(
                label = stringResource(R.string.onboarding_summary_chrome),
                value = stringResource(chromeStyle.labelRes)
            )
            OnboardingSummaryChip(
                label = stringResource(R.string.onboarding_summary_nav_style),
                value = navigationStyle.displayName
            )
            OnboardingSummaryChip(
                label = stringResource(R.string.onboarding_summary_nav_content),
                value = stringResource(labelMode.labelRes)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ==========================================
// 公共组件
// ==========================================

private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/** 导航样式选项卡预览：沉底为全宽底条，胶囊为悬浮胶囊 */
@Composable
private fun BoxScope.NavStylePreview(style: NavigationStyle) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    if (style == NavigationStyle.BOTTOM) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(12.dp)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            PreviewDots()
        }
    } else {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .width(64.dp)
                .height(16.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            PreviewDots()
        }
    }
}

/** 导航内容选项卡预览：仅图标 / 图文 / 仅文字 */
@Composable
private fun BoxScope.NavLabelModePreview(mode: NavigationLabelMode) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when (mode) {
        NavigationLabelMode.ICON_ONLY -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.HOME),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        NavigationLabelMode.ICON_AND_TEXT -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.HOME),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    PreviewBar(width = 10.dp)
                }
            }
        }

        NavigationLabelMode.TEXT_ONLY -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                PreviewBar(width = 12.dp)
            }
        }
    }
}

@Composable
private fun PreviewDots() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun PreviewBar(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    )
}

/** 预览中的导航项：与真实胶囊的四项保持一致 */
private val previewItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Timeline,
    BottomNavItem.Schedule,
    BottomNavItem.Settings
)

/** 预览中悬浮胶囊的高度：真实胶囊为 54dp，此处按预览比例放大到便于辨识的 40dp */
private val CapsulePreviewHeight = 40.dp

/**
 * 迷你手机预览框：顶栏骨架 + 内容区 + 按当前设置渲染的导航栏。
 * 导航栏使用真实图标与文字（首页高亮），直观呈现样式与显示模式组合效果。
 */
@Composable
internal fun MiniPhonePreview(
    navigationStyle: NavigationStyle,
    labelMode: NavigationLabelMode,
    chromeStyle: ChromeStyle = ChromeStyle.STANDARD,
    height: Dp = 240.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { MiniDefaultContentPreview() }
) {
    val advancedBlur = chromeStyle == ChromeStyle.HAZE
    val liquidGlass = chromeStyle == ChromeStyle.LIQUID_GLASS
    // 玻璃采样状态：高级模糊 haze / 液态玻璃 backdrop
    val hazeState = rememberHazeState()
    val glassBackdrop = rememberLayerBackdrop()
    // 液态玻璃规格与真实胶囊同源（Capsule 档），折射按预览高度等比缩小
    val glassSpec = liquidGlassSpec(LiquidGlassKind.Capsule)
    val glassScale = CapsulePreviewHeight.value / 54f

    Column(
        modifier = modifier
            .height(height)
            .shadow(6.dp, SquircleShape(20.dp))
            .clip(SquircleShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleShape(20.dp))
    ) {
        // 内容捕获层：包住顶栏与内容区，玻璃效果开启时登记为采样源，
        // 胶囊（兄弟节点）即可模糊/折射出其内容
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (advancedBlur) Modifier.hazeSource(state = hazeState) else Modifier)
                .then(if (liquidGlass) Modifier.layerBackdrop(glassBackdrop) else Modifier)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏骨架：头像 + 问候语条 + 操作图标
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(9.dp)
                            .clip(SquircleShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.SEARCH),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.ADD),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 内容区
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                    content()
                }
            }
        }

        // 底部导航栏：按所选样式与显示模式渲染
        if (navigationStyle == NavigationStyle.BOTTOM) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    previewItems.forEachIndexed { index, item ->
                        MiniNavItem(
                            item = item,
                            isSelected = index == 0,
                            labelMode = labelMode
                        )
                    }
                }
            }
        } else {
            // 悬浮胶囊预览：占满预览宽度（只留悬浮边距）并让 Tab 等分排布，
            // 与真实胶囊「左右 32dp 边距 + 占满剩余宽度」的画法一致；
            // 高度与图标/文字按真实 54dp 胶囊等比放大到可辨识的尺寸，
            // 避免内容缩在中间显得局促。
            // 按玻璃设置渲染（液态玻璃 backdrop 折射 / 高级模糊 haze / 标准实色）
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                // 三种玻璃形态共用的 Tab 行：等分宽度、逐项居中
                val tabRow: @Composable RowScope.() -> Unit = {
                    previewItems.forEachIndexed { index, item ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            MiniNavItem(
                                item = item,
                                isSelected = index == 0,
                                labelMode = labelMode
                            )
                        }
                    }
                }
                when {
                    liquidGlass -> Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(CapsulePreviewHeight)
                            .drawBackdrop(
                                backdrop = glassBackdrop,
                                shape = { Capsule() },
                                effects = {
                                    if (glassSpec.vibrancy) vibrancy()
                                    if (glassSpec.blurRadius > 0.dp) blur(glassSpec.blurRadius.toPx())
                                    if (glassSpec.lensRefractionHeight > 0.dp) {
                                        lens(
                                            glassSpec.lensRefractionHeight.toPx() * glassScale,
                                            glassSpec.lensRefractionAmount.toPx() * glassScale
                                        )
                                    }
                                },
                                onDrawSurface = { drawRect(glassSpec.containerColor) }
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tabRow
                    )
                    advancedBlur -> Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(CapsulePreviewHeight)
                            .then(
                                advancedHazeEffect(
                                    hazeState,
                                    LocalAdvancedBlurConfig.current,
                                    CircleShape
                                )
                            )
                            .border(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                CircleShape
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tabRow
                    )
                    else -> Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(CapsulePreviewHeight),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = tabRow
                        )
                    }
                }
            }
        }
    }
}

/** 预览中的导航项：按显示模式渲染图标/文字，选中项使用主色 */
@Composable
private fun MiniNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    labelMode: NavigationLabelMode
) {
    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(item.titleRes)

    when (labelMode) {
        NavigationLabelMode.ICON_ONLY -> {
            Icon(
                painter = rememberAppIconPainter(if (isSelected) item.selectedIcon else item.icon),
                contentDescription = label,
                modifier = Modifier.size(19.dp),
                tint = tint
            )
        }
        NavigationLabelMode.ICON_AND_TEXT -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = rememberAppIconPainter(if (isSelected) item.selectedIcon else item.icon),
                    contentDescription = label,
                    modifier = Modifier.size(17.dp),
                    tint = tint
                )
                Text(text = label, fontSize = 10.sp, color = tint, maxLines = 1)
            }
        }
        NavigationLabelMode.TEXT_ONLY -> {
            Text(
                text = label,
                fontSize = 12.sp,
                color = tint,
                maxLines = 1,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/** 预览默认内容：两列灰阶卡片骨架 */
@Composable
private fun MiniDefaultContentPreview() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(2) { index ->
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(SquircleShape(10.dp))
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (index == 0) 0.12f else 0.07f
                            )
                        )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(8.dp)
                        .clip(SquircleShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                )
            }
        }
    }
}

/** 完成页预览内容：问候语 + 卡片墙骨架 */
@Composable
private fun MiniHomeContentPreview() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(10.dp)
                    .clip(SquircleShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { index ->
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(SquircleShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (index == 0) 0.12f else 0.07f
                                )
                            )
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(7.dp)
                            .clip(SquircleShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}
