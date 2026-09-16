package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.DarkStyle
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.GlassEffectPreviewCard
import com.aiexile.animetrack.ui.components.GlassEffectPreviewContent
import com.aiexile.animetrack.ui.components.IconPackPreviewCard
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.IconPack
import com.aiexile.animetrack.ui.navigation.Routes
import com.aiexile.animetrack.ui.theme.ThemePreset
import kotlinx.coroutines.launch

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
    // 用同步缓存值作初始值，避免首帧渲染默认值、随后跳变为持久化值
    // （否则 showDarkStyleSelector 会经历一次 false→true，深色风格选择器的展开动画被误触发）
    val currentThemeMode by settingsRepository.themeMode.collectAsState(settingsRepository.cachedThemeMode())
    val currentDarkStyle by settingsRepository.darkStyle.collectAsState(settingsRepository.cachedDarkStyle())
    val systemDarkTheme = isSystemInDarkTheme()
    // 深色风格选择器：深色模式、或自动模式且系统当前处于深色时才有意义
    val showDarkStyleSelector = currentThemeMode == ThemeMode.DARK ||
            (currentThemeMode == ThemeMode.SYSTEM && systemDarkTheme)
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
        "mode" to 2, "dark_style" to 2, "color" to 4, "icon_pack" to 6,
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(rememberHighlightModifier("mode", highlightKey))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                    // 深色风格：仅深色模式、或自动模式且系统当前处于深色时显示，
                    // 从上方弹性弹出/平滑收回（负偏移 = 起始/目标位置在上方）；
                    // clipToBounds 裁掉越界部分，避免滑入/滑出时飘到模式预览卡上方
                    AnimatedVisibility(
                        visible = showDarkStyleSelector,
                        modifier = Modifier.clipToBounds(),
                        enter = slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { -it } + fadeIn(),
                        exit = slideOutVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { -it } + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            DarkStyleSelectorRow(
                                current = currentDarkStyle,
                                onSelect = { style -> scope.launch { settingsRepository.setDarkStyle(style) } }
                            )
                        }
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
                    // 7 个预设色块（4 + 3 两行居中），行数随主题色增减自适应
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        maxItemsInEachRow = 4
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
                            modifier = Modifier
                                .weight(1f)
                                .then(rememberHighlightModifier("advanced_blur", highlightKey)),
                            label = stringResource(R.string.nav_custom_advanced_blur),
                            selected = capsuleAdvancedBlur,
                            onClick = {
                                scope.launch {
                                    // 与液态玻璃互斥：开启高级模糊时关闭液态玻璃
                                    settingsRepository.setCapsuleAdvancedBlurEnabled(true)
                                    settingsRepository.setCapsuleLiquidGlassEnabled(false)
                                }
                            }
                        ) {
                            GlassEffectPreviewContent(advancedBlur = true, liquidGlass = false)
                        }
                        GlassEffectPreviewCard(
                            modifier = Modifier
                                .weight(1f)
                                .then(rememberHighlightModifier("liquid_glass", highlightKey)),
                            label = stringResource(R.string.nav_custom_liquid_glass),
                            selected = capsuleLiquidGlass,
                            onClick = {
                                scope.launch {
                                    // 与高级模糊互斥：开启液态玻璃时关闭高级模糊
                                    settingsRepository.setCapsuleLiquidGlassEnabled(true)
                                    settingsRepository.setCapsuleAdvancedBlurEnabled(false)
                                }
                            }
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
    // 选中色环透明度过渡，避免选中态切换生硬
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = SpringSpec(stiffness = Spring.StiffnessMedium),
        label = "swatchRingAlpha"
    )
    // 选中勾选的缩放/透明过渡
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = SpringSpec(stiffness = Spring.StiffnessMedium),
        label = "swatchCheckScale"
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
            // 选中态色环：常驻 52dp 占位（仅透明度过渡），避免选中/取消时容器尺寸变化引起布局跳动
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = preset.seedColor.copy(alpha = ringAlpha),
                        shape = CircleShape
                    )
            )
            // 色块本体：单色圆 + 细描边（保证黑白简洁等深色色块在任何背景下可见）
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(preset.seedColor)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checkScale > 0.01f) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.CHECK),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = checkScale),
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                            }
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

/** 深色风格紧凑选择行：标签 + 四个胶囊选项，窄屏自动换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DarkStyleSelectorRow(
    current: DarkStyle,
    onSelect: (DarkStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.appearance_dark_style_title),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        DarkStyle.entries.forEach { style ->
            val selected = style == current
            Box(
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(style) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = style.displayName,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
