package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ThemeMode
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
    val currentThemeMode by settingsRepository.themeMode.collectAsState(ThemeMode.SYSTEM)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "外观与主题",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Text(
                    text = "外观模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeModePreviewCard(
                        modifier = Modifier.weight(1f),
                        label = "浅色",
                        selected = currentThemeMode == ThemeMode.LIGHT,
                        onClick = { scope.launch { settingsRepository.setThemeMode(ThemeMode.LIGHT) } }
                    ) {
                        LightPreviewContent()
                    }
                    ThemeModePreviewCard(
                        modifier = Modifier.weight(1f),
                        label = "深色",
                        selected = currentThemeMode == ThemeMode.DARK,
                        onClick = { scope.launch { settingsRepository.setThemeMode(ThemeMode.DARK) } }
                    ) {
                        DarkPreviewContent()
                    }
                    ThemeModePreviewCard(
                        modifier = Modifier.weight(1f),
                        label = "自动",
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
                    title = "主题配色",
                    subtitle = "选择你喜欢的配色方案"
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
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.6f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick)
        ) {
            previewContent()
        }
        Spacer(modifier = Modifier.height(6.dp))
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                    .clip(RoundedCornerShape(1.5.dp))
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
                    .clip(RoundedCornerShape(1.5.dp))
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
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp))) {
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
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(imageColor, RoundedCornerShape(5.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(lineColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
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
            .clip(RoundedCornerShape(8.dp))
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
                .background(color, RoundedCornerShape(2.5.dp))
        )
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(2.dp)
                .background(color, RoundedCornerShape(1.dp))
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
                        imageVector = Icons.Default.Check,
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
