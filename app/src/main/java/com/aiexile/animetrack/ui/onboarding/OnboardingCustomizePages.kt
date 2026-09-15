package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.background
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.NavigationLabelMode
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.BottomNavItem
import com.aiexile.animetrack.ui.components.advancedHazeEffect
import com.aiexile.animetrack.ui.theme.ThemePreset
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
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
@Composable
internal fun OnboardingThemePage(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val themePreset by settingsRepository.themePreset.collectAsState(ThemePreset.MONO_BLACK)
    val themeMode by settingsRepository.themeMode.collectAsState(ThemeMode.SYSTEM)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        PageHeader(
            title = stringResource(R.string.onboarding_theme_title),
            description = stringResource(R.string.onboarding_theme_description)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 主题配色：点选后整个向导界面立即变色预览
        SectionLabel(text = stringResource(R.string.onboarding_theme_preset_label))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemePreset.entries.forEach { preset ->
                ThemePresetOption(
                    preset = preset,
                    isSelected = themePreset == preset,
                    onClick = { scope.launch { settingsRepository.setThemePreset(preset) } }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 深浅模式
        SectionLabel(text = stringResource(R.string.onboarding_theme_mode_label))
        Spacer(modifier = Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { scope.launch { settingsRepository.setThemeMode(mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                    icon = {},
                    label = {
                        Text(
                            text = stringResource(themeModeLabelRes(mode)),
                            maxLines = 1,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }
    }
}

// ==========================================
// 自定义页 2: 导航定制（迷你手机预览框实时渲染所选效果）
// ==========================================
@Composable
internal fun OnboardingNavPage(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val navigationStyle by settingsRepository.navigationStyle.collectAsState(NavigationStyle.CAPSULE)
    val labelMode by settingsRepository.navigationLabelMode.collectAsState(NavigationLabelMode.ICON_AND_TEXT)
    // 玻璃设置：胶囊预览跟随真实效果渲染（初始值取同步缓存避免首帧闪变）
    val capsuleAdvancedBlur by settingsRepository.capsuleAdvancedBlurEnabled
        .collectAsState(settingsRepository.cachedCapsuleAdvancedBlur())
    val capsuleLiquidGlass by settingsRepository.capsuleLiquidGlassEnabled
        .collectAsState(settingsRepository.cachedCapsuleLiquidGlass())

    // 子步：0=导航样式，1=导航内容。初次进入样式步为未选中态，点选样式后才进入内容步；
    // 顶部步骤标签让首次使用者一眼看到有两步可选，并可点击来回切换。
    var navStep by rememberSaveable { mutableIntStateOf(0) }
    var styleChosen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        PageHeader(
            title = stringResource(R.string.onboarding_nav_title),
            description = stringResource(R.string.onboarding_nav_description)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 步骤标签：导航样式 / 导航内容，当前步高亮，提示后面还有一步
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepChip(
                label = stringResource(R.string.onboarding_nav_style_label),
                active = navStep == 0,
                onClick = { navStep = 0 }
            )
            StepChip(
                label = stringResource(R.string.onboarding_nav_content_label),
                active = navStep == 1,
                onClick = { navStep = 1 }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 迷你手机预览：占据剩余空间，大屏保持 240dp，小屏最多微缩到 200dp，
        // 分步后剩余空间充足，预览依然清晰可辨
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            MiniPhonePreview(
                navigationStyle = navigationStyle,
                labelMode = labelMode,
                advancedBlur = capsuleAdvancedBlur,
                liquidGlass = capsuleLiquidGlass,
                height = maxHeight.coerceIn(200.dp, 240.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (navStep == 0) {
            // 第一步：导航样式，初次进入未选中，点选后才高亮并自动进入内容步
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NavigationStyle.entries.forEach { style ->
                    NavStyleOptionCard(
                        style = style,
                        isSelected = styleChosen && navigationStyle == style,
                        onClick = {
                            scope.launch {
                                settingsRepository.setNavigationStyle(style)
                                styleChosen = true
                                navStep = 1
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // 第二步：导航内容（图标/文字显示模式）
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                NavigationLabelMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = labelMode == mode,
                        onClick = { scope.launch { settingsRepository.setNavigationLabelMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = NavigationLabelMode.entries.size),
                        icon = {},
                        label = {
                            Text(
                                text = stringResource(mode.labelRes),
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ==========================================
// 完成页：按用户配置渲染的综合预览
// ==========================================
@Composable
internal fun OnboardingReadyPage(settingsRepository: SettingsRepository) {
    val navigationStyle by settingsRepository.navigationStyle.collectAsState(NavigationStyle.CAPSULE)
    val labelMode by settingsRepository.navigationLabelMode.collectAsState(NavigationLabelMode.ICON_AND_TEXT)
    // 玻璃设置：完成页综合预览同样跟随真实玻璃效果
    val capsuleAdvancedBlur by settingsRepository.capsuleAdvancedBlurEnabled
        .collectAsState(settingsRepository.cachedCapsuleAdvancedBlur())
    val capsuleLiquidGlass by settingsRepository.capsuleLiquidGlassEnabled
        .collectAsState(settingsRepository.cachedCapsuleLiquidGlass())

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
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
                advancedBlur = capsuleAdvancedBlur,
                liquidGlass = capsuleLiquidGlass,
                height = (maxHeight - 24.dp).coerceIn(200.dp, 280.dp),
                modifier = Modifier.fillMaxWidth(),
                content = { MiniHomeContentPreview() }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_ready_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// 公共组件
// ==========================================

@Composable
internal fun PageHeader(title: String, description: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 步骤标签：导航样式 / 导航内容，当前步高亮，可点击切换，提示首次使用者有多个步骤 */
@Composable
private fun StepChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/** 主题色卡：圆形色块 + 名称，选中时 primary 描边 + 对勾 */
@Composable
private fun ThemePresetOption(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(preset.seedColor)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CHECK),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = preset.displayName,
            fontSize = 12.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 导航样式选项卡：迷你示意 + 名称 */
@Composable
private fun NavStyleOptionCard(
    style: NavigationStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(SquircleShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = SquircleShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 迷你示意图：沉底为全宽底条，胶囊为悬浮胶囊
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(44.dp)
                .clip(SquircleShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleShape(8.dp))
        ) {
            if (style == NavigationStyle.BOTTOM) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
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
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .width(68.dp)
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
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
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = style.displayName,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
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
    advancedBlur: Boolean = false,
    liquidGlass: Boolean = false,
    height: Dp = 240.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { MiniDefaultContentPreview() }
) {
    // 玻璃采样状态：高级模糊 haze / 液态玻璃 backdrop
    val hazeState = rememberHazeState()
    val glassBackdrop = rememberLayerBackdrop()
    // 液态玻璃容器底色（与真实 LiquidGlassNavBar 同源）
    val glassContainerColor = if (isAppDarkTheme()) Color(0xFF121212).copy(alpha = 0.4f)
    else Color(0xFFFAFAFA).copy(alpha = 0.4f)

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
                                    vibrancy()
                                    blur(8f.dp.toPx())
                                    // lens 半径按胶囊高度等比缩小（真实 54dp 胶囊用 24dp）
                                    lens(18f.dp.toPx(), 18f.dp.toPx())
                                },
                                onDrawSurface = { drawRect(glassContainerColor) }
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
                            .then(advancedHazeEffect(hazeState, AdvancedBlurConfig.DEFAULT, CircleShape))
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
