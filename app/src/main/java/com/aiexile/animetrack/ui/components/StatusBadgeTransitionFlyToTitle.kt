package com.aiexile.animetrack.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.aiexile.animetrack.model.AnimeStatus

/**
 * 转场策略 B：状态角标作为独立共享元素，单独飞向详情页标题行（线上默认行为）。
 *
 * 主页/收藏页卡片右上角与详情页标题行的徽章使用同一个 [sharedKey]，飞出/飞回时
 * 由 [SharedTransitionScope.sharedBounds] 把两侧角标的矩形（位置 + 尺寸）连续
 * 衔接，落点就是详情页标题行徽章本身的真实布局位置。
 *
 * 为什么用 `sharedBounds` 而不是 `sharedElement`：两侧角标的排版并不相同
 * （小号 9sp / 大号 12sp），`sharedBounds` 配合 [scaleToBounds] 按动画包围盒
 * 缩放内容，不会逐帧重新排版文字；起始/落点分别回到两侧原生尺寸，落位零跳变。
 *
 * 重影处理：`sharedBounds` 默认会让离场侧内容与进入侧内容在同一动画包围盒上
 * 淡入淡出。这里改为底层 `enter/exit` 关闭 + 离场侧内容透明，全程只有
 * "成为目标"的一侧被绘制；无共享匹配时（如从搜索入口进入详情、或离开详情
 * 去播放器）保持原样，不闪现也不提前消失。
 *
 * @param sharedKey 两侧必须完全一致的共享元素 key（由 [statusBadgeSharedKey] 生成）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FlyToTitleStatusBadge(
    status: AnimeStatus,
    unaired: Boolean,
    size: StatusBadgeSize,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedKey: String,
    modifier: Modifier = Modifier
) {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) {
        StatusBadge(status = status, unaired = unaired, size = size, modifier = modifier)
        return
    }

    val sharedModifier = with(sharedTransitionScope) {
        val sharedContentState = rememberSharedContentState(key = sharedKey)
        // 当前页面是否是"共享元素目标页"（正在进入/已可见）。
        // 只有确实匹配到对侧、且本页是离场页时，才把本页内容透明化，避免
        // 两侧内容同时叠绘；没有匹配的普通过渡（如详情 → 播放器）不隐藏，
        // 让角标跟随页面整体淡出。
        val isTransitionTarget =
            animatedVisibilityScope.transition.targetState == EnterExitState.Visible
        val contentAlpha =
            if (sharedContentState.isMatchFound && !isTransitionTarget) 0f else 1f

        Modifier
            .sharedBounds(
                sharedContentState = sharedContentState,
                animatedVisibilityScope = animatedVisibilityScope,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                resizeMode = scaleToBounds(ContentScale.FillBounds, Alignment.Center),
                // 角标始终绘制在封面共享元素之上（飞行前半程两者区域重叠）
                zIndexInOverlay = 1f
            )
            .graphicsLayer { alpha = contentAlpha }
    }

    StatusBadge(
        status = status,
        unaired = unaired,
        size = size,
        modifier = modifier.then(sharedModifier)
    )
}
