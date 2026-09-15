package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.IconPack
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

/**
 * 外观选项预览卡：供外观设置页与首次启动向导共用，
 * 展示图标风格与玻璃效果的可视化选项。
 */

/** 图标风格预览卡：标签在上左对齐，下方以指定 pack 的示例图标展示该风格的视觉语言（不随全局 LocalIconPack 变化） */
@Composable
fun IconPackPreviewCard(
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    AppIcon.HOME,
                    AppIcon.SEARCH,
                    AppIcon.PLAY_ARROW,
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

/** 玻璃效果预览卡：标签在上，下方预览展示该效果下悬浮胶囊的观感，选中态仅以外框标识 */
@Composable
fun GlassEffectPreviewCard(
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
 * 玻璃效果预览：背景彩色内容条延伸至胶囊下方，
 * 高级模糊与液态玻璃均直接调用真实渲染库，预览即实际导航栏观感：
 * - 标准：实色胶囊 + 细描边
 * - 高级模糊：haze 毛玻璃（与真实胶囊同参数 [AdvancedBlurConfig.DEFAULT]）+ 描边
 * - 液态玻璃：kyant backdrop 真实折射（vibrancy + blur + lens），容器底色与真实同源
 */
@Composable
fun GlassEffectPreviewContent(
    advancedBlur: Boolean,
    liquidGlass: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    when {
        liquidGlass -> {
            val isDark = isAppDarkTheme()
            val containerColor = if (isDark) Color(0xFF121212).copy(alpha = 0.4f)
            else Color(0xFFFAFAFA).copy(alpha = 0.4f)
            val backdrop = rememberLayerBackdrop()
            Box(modifier = modifier.fillMaxSize()) {
                // 背景层：登记为折射采样源
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.surfaceContainerHigh)
                        .layerBackdrop(backdrop)
                ) {
                    GlassPreviewBackground()
                }
                // 液态玻璃胶囊：lens 半径按 22dp 胶囊高度等比缩小（真实 54dp 胶囊用 24dp）
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(0.86f)
                        .height(22.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur(8f.dp.toPx())
                                lens(10f.dp.toPx(), 10f.dp.toPx())
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPreviewTabs()
                }
            }
        }
        advancedBlur -> {
            val hazeState = rememberHazeState()
            Box(modifier = modifier.fillMaxSize()) {
                // 背景层：登记为毛玻璃采样源
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.surfaceContainerHigh)
                        .hazeSource(state = hazeState)
                ) {
                    GlassPreviewBackground()
                }
                // 毛玻璃胶囊：与真实胶囊同参数，同款 0.5dp 描边
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(0.86f)
                        .height(22.dp)
                        .then(advancedHazeEffect(hazeState, AdvancedBlurConfig.DEFAULT, CircleShape))
                        .border(
                            0.5.dp,
                            colorScheme.outlineVariant.copy(alpha = 0.4f),
                            CircleShape
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPreviewTabs()
                }
            }
        }
        else -> {
            // 标准：实色胶囊
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(colorScheme.surfaceContainerHigh)
            ) {
                GlassPreviewBackground()
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(0.86f)
                        .height(22.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainer)
                        .border(
                            0.5.dp,
                            colorScheme.outlineVariant.copy(alpha = 0.4f),
                            CircleShape
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPreviewTabs()
                }
            }
        }
    }
}

/** 玻璃效果预览背景：底色上的多彩内容条，延伸至胶囊下方供模糊/折射采样 */
@Composable
private fun GlassPreviewBackground(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(7.dp)
                .clip(SquircleShape(3.5.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.75f),
                            colorScheme.primary.copy(alpha = 0.25f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(6.dp)
                .clip(SquircleShape(3.dp))
                .background(colorScheme.tertiary.copy(alpha = 0.45f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(6.dp)
                .clip(SquircleShape(3.dp))
                .background(colorScheme.onSurface.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(6.dp)
                .clip(SquircleShape(3.dp))
                .background(colorScheme.secondary.copy(alpha = 0.4f))
        )
    }
}

/** 玻璃效果预览胶囊内的 Tab 点：首项主色，其余半透明 */
@Composable
private fun GlassPreviewTabs() {
    val colorScheme = MaterialTheme.colorScheme
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
