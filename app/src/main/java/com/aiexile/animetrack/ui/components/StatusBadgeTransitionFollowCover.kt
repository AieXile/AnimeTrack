package com.aiexile.animetrack.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.aiexile.animetrack.model.AnimeStatus

/**
 * 转场策略 A：状态角标随封面同层飞出（原版行为）。
 *
 * 角标不是独立共享元素，而是封面 sharedElement 内容的一部分：封面位移、
 * 缩放、圆角变化时角标自动跟着走，永远不会单独落位。详情页静止态的状态
 * 徽章仍在标题行，是普通节点。
 *
 * 修复历史：早期版本 sharedElement 只包住封面图片、角标是其兄弟节点，
 * 转场中两侧绘制不同步导致角标先停在原位、再被封面遮挡后闪到前面。
 * 因此这里必须由调用方保证角标位于封面 sharedElement 的内容层内。
 */

/** 卡片侧：普通小角标，alpha 恒为 1，作为封面内容一起飞行。 */
@Composable
internal fun FollowCoverCardStatusBadge(
    status: AnimeStatus,
    unaired: Boolean,
    modifier: Modifier = Modifier
) {
    StatusBadge(status = status, unaired = unaired, size = StatusBadgeSize.Corner, modifier = modifier)
}

/** 详情页标题行：普通大号徽章，不参与共享元素，静态展示。 */
@Composable
internal fun FollowCoverDetailTitleBadge(
    status: AnimeStatus,
    unaired: Boolean,
    modifier: Modifier = Modifier
) {
    StatusBadge(status = status, unaired = unaired, size = StatusBadgeSize.Inline, modifier = modifier)
}

/**
 * 详情页封面内容：与封面同层绘制的状态角标。
 *
 * 飞行中由共享元素 overlay 一起绘制，末段淡出；静止态 alpha=0（详情页的
 * 状态徽章在标题行）。无共享匹配时直接不绘制，避免从搜索入口进入时闪现。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FollowCoverDetailCoverStatusBadge(
    status: AnimeStatus,
    unaired: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    coverContentState: SharedContentState?,
    modifier: Modifier = Modifier
) {
    if (animatedVisibilityScope == null || coverContentState == null) return
    val alpha = rememberSharedCoverBadgeAlpha(animatedVisibilityScope, coverContentState)
    StatusBadge(
        status = status,
        unaired = unaired,
        size = StatusBadgeSize.Corner,
        modifier = modifier
            // 纯装饰性叠层：转场中不参与无障碍朗读
            .clearAndSetSemantics {}
            .graphicsLayer { this.alpha = alpha() }
    )
}
