package com.aiexile.animetrack.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.aiexile.animetrack.data.StatusBadgeTransitionMode
import com.aiexile.animetrack.model.AnimeStatus

/**
 * 当前生效的状态角标转场策略：由 MainActivity 根组合下发，运行时可切换。
 *
 * 默认 [StatusBadgeTransitionMode.FLY_TO_TITLE]，与未设置过该项的线上行为一致；
 * @Preview / 单测未显式提供时同样走默认策略。
 */
val LocalStatusBadgeTransitionMode = staticCompositionLocalOf {
    StatusBadgeTransitionMode.FLY_TO_TITLE
}

/**
 * 卡片侧与详情页标题行共用的共享元素 key。
 *
 * 两套策略必须生成完全一致的 key 才能配对；集中在此避免各自拼接漂移。
 */
internal fun statusBadgeSharedKey(sharedElementKeyPrefix: String, animeId: Int): String =
    "${sharedElementKeyPrefix}status_$animeId"

/**
 * 卡片右上角的状态角标（调用方必须把它放在封面 sharedElement 的内容层内）。
 *
 * - [StatusBadgeTransitionMode.FOLLOW_COVER]：直接作为封面内容绘制，随封面飞行
 * - [StatusBadgeTransitionMode.FLY_TO_TITLE]：由独立 sharedBounds 节点绘制并飞向标题行
 *
 * 无共享元素场景（列表非顶卡、@Preview 等）由各策略内部退化为普通角标。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun CardStatusBadge(
    status: AnimeStatus,
    unaired: Boolean,
    animeId: Int,
    sharedElementKeyPrefix: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    when (LocalStatusBadgeTransitionMode.current) {
        StatusBadgeTransitionMode.FOLLOW_COVER -> FollowCoverCardStatusBadge(
            status = status,
            unaired = unaired,
            modifier = modifier
        )

        StatusBadgeTransitionMode.FLY_TO_TITLE -> FlyToTitleStatusBadge(
            status = status,
            unaired = unaired,
            size = StatusBadgeSize.Corner,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = statusBadgeSharedKey(sharedElementKeyPrefix, animeId),
            modifier = modifier
        )
    }
}

/**
 * 详情页标题行的状态徽章（真实布局节点）。
 *
 * - [StatusBadgeTransitionMode.FOLLOW_COVER]：静态徽章，不参与共享元素
 * - [StatusBadgeTransitionMode.FLY_TO_TITLE]：共享元素落点，角标从卡片飞到这里
 */
@Composable
internal fun DetailTitleStatusBadge(
    status: AnimeStatus,
    unaired: Boolean,
    animeId: Int,
    sharedElementKeyPrefix: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    when (LocalStatusBadgeTransitionMode.current) {
        StatusBadgeTransitionMode.FOLLOW_COVER -> FollowCoverDetailTitleBadge(
            status = status,
            unaired = unaired,
            modifier = modifier
        )

        StatusBadgeTransitionMode.FLY_TO_TITLE -> FlyToTitleStatusBadge(
            status = status,
            unaired = unaired,
            size = StatusBadgeSize.Inline,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = statusBadgeSharedKey(sharedElementKeyPrefix, animeId),
            modifier = modifier
        )
    }
}

/**
 * 详情页封面内的状态角标（绘制在封面 sharedElement 的内容层内）。
 *
 * 仅 [StatusBadgeTransitionMode.FOLLOW_COVER] 需要它：飞行期间角标与封面同层；
 * [StatusBadgeTransitionMode.FLY_TO_TITLE] 的角标落在标题行，封面内容里不绘制。
 *
 * @param coverContentState 封面共享元素状态，用于确认确实匹配到卡片侧，
 *   避免从无共享元素的入口（如搜索结果）进入详情时角标闪现。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DetailCoverStatusBadge(
    status: AnimeStatus,
    unaired: Boolean,
    sharedElementKeyPrefix: String,
    animeId: Int,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    coverContentState: SharedContentState?,
    modifier: Modifier = Modifier
) {
    if (LocalStatusBadgeTransitionMode.current != StatusBadgeTransitionMode.FOLLOW_COVER) return
    FollowCoverDetailCoverStatusBadge(
        status = status,
        unaired = unaired,
        animatedVisibilityScope = animatedVisibilityScope,
        coverContentState = coverContentState,
        modifier = modifier
    )
}
