package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.IconPack
import com.aiexile.animetrack.ui.navigation.Routes
import com.aiexile.animetrack.ui.theme.ThemePreset
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource

private val TopLeftTriangleShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(0f, size.height)
    close()
}

private val BottomRightTriangleShape = GenericShape { size, _ ->
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

private val LightBg = Color(0xFFFAFAFA)
private val LightSurface = Color(0xFFF2F2F2)
private val LightOutline = Color(0xFFE0E0E0)
private val LightMuted = Color(0xFFBDBDBD)
private val LightSubMuted = Color(0xFFD5D5D5)
private val LightPrimary = Color(0xFF1A1A1A)

private val DarkBg = Color(0xFF0A0A0A)
private val DarkSurface = Color(0xFF1A1A1A)
private val DarkOutline = Color(0xFF262626)
private val DarkMuted = Color(0xFF424242)
private val DarkSubMuted = Color(0xFF333333)
private val DarkPrimary = Color(0xFFE0E0E0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val currentPreset by settingsRepository.themePreset.collectAsState(ThemePreset.MONO_BLACK)
    val currentThemeMode by settingsRepository.themeMode.collectAsState(ThemeMode.SYSTEM)
    val currentIconPack by settingsRepository.iconPack.collectAsState(settingsRepository.cachedIconPack())
    // 初始值取同步缓存（已持久化的值），避免首帧渲染默认关闭态、随后跳变为已开启的闪变
    val capsuleAdvancedBlur by settingsRepository.capsuleAdvancedBlurEnabled
        .collectAsState(settingsRepository.cachedCapsuleAdvancedBlur())
    val capsuleLiquidGlass by settingsRepository.capsuleLiquidGlassEnabled
        .collectAsState(settingsRepository.cachedCapsuleLiquidGlass())

    // 搜索定位：高亮目标区块并滚动到位
    val highlightKey = rememberSettingsHighlight(Routes.APPEARANCE)
    val listState = rememberLazyListState()
    val highlightAnchors = mapOf(
        "mode" to 2, "color" to 4, "icon_pack" to 6,
        "advanced_blur" to 8, "liquid_glass" to 8
    )
    LaunchedEffect(highlightKey) {
        highlightAnchors[highlightKey]?.let { listState.animateScrollToItem(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.appearance_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Text(
                    text = stringResource(R.string.appearance_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(rememberHighlightModifier("mode", highlightKey)),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeModePreviewCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.appearance_mode_light),
                        selected = currentThemeMode == ThemeMode.LIGHT,
                        onClick = { scope.launch { settingsRepository.setThemeMode(ThemeMode.LIGHT) } }
                    ) {
                        LightPreviewContent()
                    }
                    ThemeModePreviewCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.appearance_mode_dark),
                        selected = currentThemeMode == ThemeMode.DARK,
                        onClick = { scope.launch { settingsRepository.setThemeMode(ThemeMode.DARK) } }
                    ) {
                        DarkPreviewContent()
                    }
                    ThemeModePreviewCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.appearance_mode_auto),
                        selected = currentThemeMode == ThemeMode.SYSTEM,
                        onClick = { scope.launch { settingsRepository.setThemeMode(ThemeMode.SYSTEM) } }
                    ) {
                        AutoPreviewContent()
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                SettingsGroup(
                    title = stringResource(R.string.appearance_color_title),
                    subtitle = stringResource(R.string.appearance_color_subtitle),
                    modifier = rememberHighlightModifier("color", highlightKey)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemePreset.entries.forEach { preset ->
                            ColorSwatch(
                                preset = preset,
                                isSelected = preset == currentPreset,
                                onClick = { scope.launch { settingsRepository.setThemePreset(preset) } }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                SettingsGroup(
                    title = stringResource(R.string.appearance_icon_style_title),
                    subtitle = stringResource(R.string.appearance_icon_style_subtitle),
                    modifier = rememberHighlightModifier("icon_pack", highlightKey)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconPackPreviewCard(
                            modifier = Modifier.weight(1f),
                            pack = IconPack.MATERIAL_SYMBOLS,
                            label = stringResource(R.string.appearance_icon_pack_material),
                            selected = currentIconPack == IconPack.MATERIAL_SYMBOLS,
                            onClick = { scope.launch { settingsRepository.setIconPack(IconPack.MATERIAL_SYMBOLS) } }
                        )
                        IconPackPreviewCard(
                            modifier = Modifier.weight(1f),
                            pack = IconPack.LUCIDE,
                            label = stringResource(R.string.appearance_icon_pack_lucide),
                            selected = currentIconPack == IconPack.LUCIDE,
                            onClick = { scope.launch { settingsRepository.setIconPack(IconPack.LUCIDE) } }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                SettingsGroup(
                    title = stringResource(R.string.appearance_glass_effect_title),
                    subtitle = stringResource(R.string.appearance_glass_effect_subtitle)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassEffectPreviewCard(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.appearance_glass_effect_standard),
                            selected = !capsuleAdvancedBlur && !capsuleLiquidGlass,
                            onClick = {
                                scope.launch {
                                    settingsRepository.setCapsuleAdvancedBlurEnabled(false)
                                    settingsRepository.setCapsuleLiquidGlassEnabled(false)
                                }
                            }
                        ) {
                            GlassEffectPreviewContent(advancedBlur = false, liquidGlass = false)
                        }
                        GlassEffectPreviewCard(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.nav_custom_advanced_blur),
                            selected = capsuleAdvancedBlur,
                            onClick = {
                                scope.launch {
                                    // 与液态玻璃互斥：开启高级模糊时关闭液态玻璃
                                    settingsRepository.setCapsuleAdvancedBlurEnabled(true)
                                    settingsRepository.setCapsuleLiquidGlassEnabled(false)
                                }
                            },
                            itemKey = "advanced_blur",
                            highlightKey = highlightKey
                        ) {
                            GlassEffectPreviewContent(advancedBlur = true, liquidGlass = false)
                        }
                        GlassEffectPreviewCard(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.nav_custom_liquid_glass),
                            selected = capsuleLiquidGlass,
                            onClick = {
                                scope.launch {
                                    // 与高级模糊互斥：开启液态玻璃时关闭高级模糊
                                    settingsRepository.setCapsuleLiquidGlassEnabled(true)
                                    settingsRepository.setCapsuleAdvancedBlurEnabled(false)
                                }
                            },
                            itemKey = "liquid_glass",
                            highlightKey = highlightKey
                        ) {
                            GlassEffectPreviewContent(advancedBlur = false, liquidGlass = true)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ThemeModePreviewCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewContent: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.6f)
                .clip(SquircleShape(16.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = SquircleShape(16.dp)
                )
                .clickable(onClick = onClick)
        ) {
            previewContent()
        }
    }
}

/** 图标风格预览卡：标签在上左对齐，下方以指定 pack 的示例图标展示该风格的视觉语言（不随全局 LocalIconPack 变化） */
@Composable
private fun IconPackPreviewCard(
    pack: IconPack,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(SquircleShape(16.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = SquircleShape(16.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    AppIcon.HOME,
                    AppIcon.SEARCH,
                    AppIcon.PLAY_ARROW,
                    AppIcon.CHECK_CIRCLE,
                    AppIcon.SETTINGS
                ).forEach { icon ->
                    Icon(
                        painter = painterResource(pack.resolve(icon)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 玻璃效果预览卡：标签在上，下方静态预览展示该效果下悬浮胶囊的观感，选中态仅以外框标识 */
@Composable
private fun GlassEffectPreviewCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemKey: String? = null,
    highlightKey: String? = null,
    previewContent: @Composable () -> Unit
) {
    Column(modifier = modifier.then(rememberHighlightModifier(itemKey, highlightKey))) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(SquircleShape(16.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = SquircleShape(16.dp)
                )
                .clickable(onClick = onClick)
        ) {
            previewContent()
        }
    }
}

/**
 * 玻璃效果静态预览：背景色条模拟被透出的页面内容并延伸至胶囊下方，
 * 底部悬浮胶囊分别以实色 / 半透明磨砂 / 折射高光三种质感渲染，直观区分三种效果
 */
@Composable
private fun GlassEffectPreviewContent(
    advancedBlur: Boolean,
    liquidGlass: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainerHigh)
    ) {
        // 背景内容条：延伸至胶囊区域下方，用于体现透明质感
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(6.dp)
                    .clip(SquircleShape(3.dp))
                    .background(colorScheme.primary.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(6.dp)
                    .clip(SquircleShape(3.dp))
                    .background(colorScheme.primary.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(6.dp)
                    .clip(SquircleShape(3.dp))
                    .background(colorScheme.onSurface.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(6.dp)
                    .clip(SquircleShape(3.dp))
                    .background(colorScheme.onSurface.copy(alpha = 0.12f))
            )
        }
        // 悬浮胶囊：标准为实色；高级模糊为半透明磨砂；液态玻璃为更透的折射质感（渐变 + 高光描边）
        val capsuleBackground = when {
            liquidGlass -> Brush.verticalGradient(
                listOf(
                    colorScheme.surfaceContainer.copy(alpha = 0.45f),
                    colorScheme.surfaceContainer.copy(alpha = 0.12f)
                )
            )
            advancedBlur -> SolidColor(colorScheme.surfaceContainer.copy(alpha = 0.62f))
            else -> SolidColor(colorScheme.surfaceContainer)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .fillMaxWidth(0.86f)
                .height(22.dp)
                .clip(SquircleShape(100.dp))
                .background(capsuleBackground)
                .then(
                    if (liquidGlass) {
                        Modifier.border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    colorScheme.onSurface.copy(alpha = 0.5f),
                                    colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                            ),
                            shape = SquircleShape(100.dp)
                        )
                    } else {
                        Modifier.border(
                            width = 0.5.dp,
                            color = colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = SquircleShape(100.dp)
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // 液态玻璃顶部横向光泽
            if (liquidGlass) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.55f)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    colorScheme.onSurface.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == 0) colorScheme.primary
                                else colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LightPreviewContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(LightBg).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(3.dp)
                    .clip(SquircleShape(1.5.dp))
                    .background(LightPrimary)
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(LightOutline)
            )
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PreviewMiniCard(
                modifier = Modifier.weight(1f),
                bgColor = LightSurface,
                imageColor = LightOutline,
                lineColor = LightMuted,
                subLineColor = LightSubMuted
            )
            PreviewMiniCard(
                modifier = Modifier.weight(1f),
                bgColor = LightSurface,
                imageColor = LightOutline,
                lineColor = LightMuted,
                subLineColor = LightSubMuted
            )
        }
        PreviewBottomNav(
            activeColor = LightPrimary,
            inactiveColor = LightMuted,
            bgColor = LightSurface
        )
    }
}

@Composable
private fun DarkPreviewContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(DarkBg).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(3.dp)
                    .clip(SquircleShape(1.5.dp))
                    .background(DarkPrimary)
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(DarkMuted)
            )
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PreviewMiniCard(
                modifier = Modifier.weight(1f),
                bgColor = DarkSurface,
                imageColor = DarkOutline,
                lineColor = DarkMuted,
                subLineColor = DarkSubMuted
            )
            PreviewMiniCard(
                modifier = Modifier.weight(1f),
                bgColor = DarkSurface,
                imageColor = DarkOutline,
                lineColor = DarkMuted,
                subLineColor = DarkSubMuted
            )
        }
        PreviewBottomNav(
            activeColor = DarkPrimary,
            inactiveColor = DarkMuted,
            bgColor = DarkSurface
        )
    }
}

@Composable
private fun AutoPreviewContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(SquircleShape(14.dp))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(TopLeftTriangleShape)
        ) {
            LightPreviewContent(modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(BottomRightTriangleShape)
        ) {
            DarkPreviewContent(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PreviewMiniCard(
    bgColor: Color,
    imageColor: Color,
    lineColor: Color,
    subLineColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(bgColor, SquircleShape(8.dp))
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(imageColor, SquircleShape(5.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(SquircleShape(1.5.dp))
                .background(lineColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(3.dp)
                .clip(SquircleShape(1.5.dp))
                .background(subLineColor)
        )
    }
}

@Composable
private fun PreviewBottomNav(
    activeColor: Color,
    inactiveColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviewNavItem(color = activeColor)
        PreviewNavItem(color = inactiveColor)
        PreviewNavItem(color = inactiveColor)
    }
}

@Composable
private fun PreviewNavItem(color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, SquircleShape(2.5.dp))
        )
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(2.dp)
                .background(color, SquircleShape(1.dp))
        )
    }
}

@Composable
private fun ColorSwatch(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = SpringSpec(stiffness = Spring.StiffnessMedium),
        label = "swatchScale"
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            ) {
                val seedColor = preset.seedColor
                drawArc(
                    color = seedColor.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true
                )
                drawArc(
                    color = seedColor.copy(alpha = 0.5f),
                    startAngle = 90f,
                    sweepAngle = 90f,
                    useCenter = true
                )
                drawArc(
                    color = seedColor.copy(alpha = 0.75f),
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = true
                )
                drawArc(
                    color = seedColor,
                    startAngle = 270f,
                    sweepAngle = 90f,
                    useCenter = true
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.CHECK),
                        contentDescription = null,
                        tint = preset.seedColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Text(
            text = preset.displayName,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
