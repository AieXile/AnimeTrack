package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.util.resolveCoverModel

private val CardCornerRadius = 16.dp
private val CoverAspectRatio = 2f / 3f

/**
 * 多季番剧堆叠卡片（仅收起态）。
 *
 * 以"扑克牌堆叠"样式渲染：顶层为完整卡片，底层渲染完整卡片（封面+标题），
 * 通过透明度+缩放+阴影露出清晰的边缘层次。展开态由
 * [com.aiexile.animetrack.ui.home.HomeScreen] 通过列表数据结构变化驱动。
 *
 * @param baseTitle 合并后的主标题（顶层卡片显示）
 * @param animeList 该系列所有季，首项作为顶层卡片，后续项作为底层露边卡片
 * @param onClick 单击堆叠卡片（默认打开第一季）
 * @param onLongPress 长按堆叠卡片（触发展开）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeCardStack(
    baseTitle: String,
    animeList: List<Anime>,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        val maxLayers = minOf(animeList.size, 4)

        // 从后往前渲染（底层先画，顶层后画）
        // 顶部对齐 + 右侧错位：去掉垂直偏移避免阶梯式下坠；
        // scale 锚点 X=0/Y=0.5：左侧对齐保留右侧错位，垂直居中使上下均匀收缩。
        for (delta in (maxLayers - 1) downTo 0) {
            val offsetX = delta * 6f
            val scale = 1f - delta * 0.03f
            val alpha = when (delta) {
                0 -> 1f
                1 -> 0.7f
                2 -> 0.5f
                else -> 0.35f
            }
            val elevation = if (delta == 0) 2.dp else (delta + 1).dp
            val anime = animeList.getOrElse(delta) { animeList.first() }
            val title = if (delta == 0) baseTitle else anime.title

            StackCardLayer(
                anime = anime,
                title = title,
                offsetX = offsetX,
                scale = scale,
                alpha = alpha,
                elevation = elevation
            )
        }

        // 季数标识
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = 6.dp),
            shape = SquircleShape(6.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = stringResource(R.string.card_stack_seasons, animeList.size),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 点击区域覆盖整个堆叠
        Box(
            modifier = Modifier
                .matchParentSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
        )
    }
}

/**
 * 堆叠中的单层卡片：完整渲染封面+标题，通过 [alpha]/[scale]/[elevation]/偏移
 * 营造层次。底层因透明度与被顶层遮挡，只露出清晰的封面+标题边缘与阴影。
 */
@Composable
private fun StackCardLayer(
    anime: Anime,
    title: String,
    offsetX: Float,
    scale: Float,
    alpha: Float,
    elevation: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = offsetX.dp)
            .graphicsLayer {
                // 锚点：X=0 左侧对齐（保留右侧错位露边），Y=0.5 垂直居中（上下均匀收缩）
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(
                elevation = elevation,
                shape = SquircleShape(CardCornerRadius),
                clip = false
            )
            .clip(SquircleShape(CardCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CoverAspectRatio)
        ) {
            if (anime.coverUrl != null) {
                AsyncImage(
                    model = resolveCoverModel(anime.coverUrl),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(SquircleShape(topStart = CardCornerRadius, topEnd = CardCornerRadius))
                )
            } else {
                EmptyCoverPlaceholder(
                    shape = SquircleShape(topStart = CardCornerRadius, topEnd = CardCornerRadius)
                )
            }
        }

        // 标题区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
