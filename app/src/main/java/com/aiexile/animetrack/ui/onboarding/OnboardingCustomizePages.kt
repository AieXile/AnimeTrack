package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.NavigationLabelMode
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.BottomNavItem
import com.aiexile.animetrack.ui.theme.ThemePreset
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

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        PageHeader(
            title = stringResource(R.string.onboarding_nav_title),
            description = stringResource(R.string.onboarding_nav_description)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 迷你手机预览：按当前选择实时渲染导航栏效果
        MiniPhonePreview(
            navigationStyle = navigationStyle,
            labelMode = labelMode,
            modifier = Modifier.fillMaxWidth(0.86f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 导航样式
        SectionLabel(text = stringResource(R.string.onboarding_nav_style_label))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NavigationStyle.entries.forEach { style ->
                NavStyleOptionCard(
                    style = style,
                    isSelected = navigationStyle == style,
                    onClick = { scope.launch { settingsRepository.setNavigationStyle(style) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 导航内容：图标 / 文字显示模式
        SectionLabel(text = stringResource(R.string.onboarding_nav_content_label))
        Spacer(modifier = Modifier.height(12.dp))
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
}

// ==========================================
// 完成页：按用户配置渲染的综合预览
// ==========================================
@Composable
internal fun OnboardingReadyPage(settingsRepository: SettingsRepository) {
    val navigationStyle by settingsRepository.navigationStyle.collectAsState(NavigationStyle.CAPSULE)
    val labelMode by settingsRepository.navigationLabelMode.collectAsState(NavigationLabelMode.ICON_AND_TEXT)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            MiniPhonePreview(
                navigationStyle = navigationStyle,
                labelMode = labelMode,
                modifier = Modifier.fillMaxWidth(0.9f),
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
private fun PageHeader(title: String, description: String) {
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    imageVector = Icons.Rounded.Check,
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

/**
 * 迷你手机预览框：顶栏骨架 + 内容区 + 按当前设置渲染的导航栏。
 * 导航栏使用真实图标与文字（首页高亮），直观呈现样式与显示模式组合效果。
 */
@Composable
internal fun MiniPhonePreview(
    navigationStyle: NavigationStyle,
    labelMode: NavigationLabelMode,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { MiniDefaultContentPreview() }
) {
    val previewItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Timeline,
        BottomNavItem.Schedule,
        BottomNavItem.Settings
    )

    Column(
        modifier = modifier
            .height(216.dp)
            .shadow(6.dp, SquircleShape(20.dp))
            .clip(SquircleShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleShape(20.dp))
    ) {
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
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 内容区
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            content()
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
            // 悬浮胶囊预览：固定高度（真实胶囊不随显示模式变化），
            // 内容紧凑居中，避免图标与文字模式下胶囊被撑得过于臃肿
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 280.dp)
                        .height(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = tint
            )
        }
        NavigationLabelMode.ICON_AND_TEXT -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = label,
                    modifier = Modifier.size(14.dp),
                    tint = tint
                )
                Text(text = label, fontSize = 8.sp, color = tint, maxLines = 1)
            }
        }
        NavigationLabelMode.TEXT_ONLY -> {
            Text(
                text = label,
                fontSize = 10.sp,
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
