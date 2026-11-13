package com.aiexile.animetrack.ui.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.AppFabMorphSurface
import com.aiexile.animetrack.ui.components.FabSize
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState

/** 展开卡片高度：上下 14dp 内边距 + 40dp 图标圆 */
private val ExternalPlayCardHeight = 68.dp

/** 播放入口距屏幕右/下的留白（FAB 与展开卡片共用底边距） */
private val ExternalPlayFabMargin = 16.dp

/**
 * 展开卡片预留的滚动空间（替代内容底部原本的 24dp）：
 * 12dp 间隔 + 卡片高度 68dp + 16dp 底部留白，滚到底时卡片正好落在状态卡下方。
 */
internal val ExternalPlayActionReserveHeight = 96.dp

/** FAB 内容（纯图标）到展开内容的淡出进度：之后卡片内容开始淡入 */
private const val FabContentFadeOutFraction = 0.35f

/**
 * 详情页外部播放入口：以右下角 FAB 为常态，滚到内容末端（状态卡下方）后
 * 同一块表面通过尺寸/材质形变（Shape Morphing）展开为整行卡片，而不是淡出再淡入。
 *
 * @param expanded 是否处于展开态（内容已滚到底）
 * @param expandedWidth 展开卡片的宽度（= 内容卡片宽度，单栏/双栏由页面测量）
 * @param expandedEndInset 展开卡片右缘距浮层右缘的距离（单栏 16dp / 双栏 24dp）
 */
@Composable
fun DetailExternalPlayAction(
    expanded: Boolean,
    style: ChromeStyle,
    hazeState: HazeState?,
    backdrop: Backdrop?,
    blurConfig: AdvancedBlurConfig,
    isResolving: Boolean,
    episodeText: String,
    expandedWidth: Dp,
    expandedEndInset: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "detail_external_play_morph"
    )

    val width = lerp(FabSize, expandedWidth.coerceAtLeast(FabSize), progress)
    val height = lerp(FabSize, ExternalPlayCardHeight, progress)
    val endInset = lerp(ExternalPlayFabMargin, expandedEndInset.coerceAtLeast(ExternalPlayFabMargin), progress)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // 展开成卡片后不再做按压缩放，避免大面积表面整体收缩
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && progress < FabContentFadeOutFraction) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "detail_external_play_press"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .padding(end = endInset, bottom = ExternalPlayFabMargin)
                .width(width)
                .height(height)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isResolving,
                    onClick = onClick
                )
        ) {
            AppFabMorphSurface(
                progress = progress,
                style = style,
                hazeState = hazeState,
                backdrop = backdrop,
                blurConfig = blurConfig,
                modifier = Modifier.matchParentSize()
            ) {
                ExternalPlayMorphContent(
                    progress = progress,
                    isResolving = isResolving,
                    episodeText = episodeText
                )
            }
        }
    }
}

/**
 * 形变中的内容层：FAB 图标先淡出、卡片内容后淡入，重叠区间很短，
 * 因此图层切换时不会出现两个图标叠影。目标集数文案为空时只显示标题。
 */
@Composable
internal fun ExternalPlayMorphContent(
    progress: Float,
    isResolving: Boolean,
    episodeText: String
) {
    val fabAlpha = (1f - progress / FabContentFadeOutFraction).coerceIn(0f, 1f)
    val cardAlpha = ((progress - FabContentFadeOutFraction) / (1f - FabContentFadeOutFraction)).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        // 完全淡出的图层不再组合，避免读屏在收起/展开态读到另一份内容
        if (fabAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fabAlpha },
                contentAlignment = Alignment.Center
            ) {
                ExternalPlayIcon(isResolving = isResolving, iconSize = 24.dp)
            }
        }

        if (cardAlpha > 0f) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = cardAlpha }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    ExternalPlayIcon(isResolving = isResolving, iconSize = 22.dp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.external_player_watch),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (episodeText.isNotBlank()) {
                        Text(
                            text = episodeText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    painter = rememberAppIconPainter(AppIcon.KEYBOARD_ARROW_RIGHT),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 播放图标 / 解析中的加载指示器，FAB 与卡片内容共用同一视觉 */
@Composable
private fun ExternalPlayIcon(isResolving: Boolean, iconSize: Dp) {
    if (isResolving) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        Icon(
            painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
            contentDescription = stringResource(R.string.external_player_watch),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
    }
}
