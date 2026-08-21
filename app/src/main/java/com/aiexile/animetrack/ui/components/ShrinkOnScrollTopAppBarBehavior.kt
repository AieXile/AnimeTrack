package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * 详情页顶栏「下滑收缩」滚动行为。
 *
 * 内容向上滑动（浏览下方内容）时，TopAppBar 高度从 [expandedHeight] 平滑收缩到
 * [collapsedHeight]；内容滚回顶部后才重新展开（exit-until-collapsed 风格，
 * 而不是稍微上滑就立即展开）。与 Material3 自带的 enterAlways
 * （顶栏整条滑出隐藏）不同，顶栏始终保持可见、仅变窄。
 *
 * 用法：将返回值传给 TopAppBar 的 scrollBehavior 参数，并通过
 * Modifier.nestedScroll(behavior.nestedScrollConnection) 挂到可滚动内容所在的容器上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberShrinkOnScrollTopAppBarBehavior(
    collapsedHeight: Dp,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
): TopAppBarScrollBehavior {
    val maxCollapsePx = with(LocalDensity.current) {
        (expandedHeight - collapsedHeight).coerceAtLeast(0.dp).toPx()
    }
    val state = rememberTopAppBarState()
    return remember(state, maxCollapsePx) {
        ShrinkOnScrollTopAppBarBehavior(state, maxCollapsePx)
    }
}

/**
 * 仅在 [-maxCollapsePx, 0] 范围内调整顶栏高度偏移的滚动行为。
 *
 * [isPinned] 返回 true 以关闭 M3 顶栏自带的「拖拽顶栏调高」手势，
 * 收缩完全由内容滚动驱动，避免拖拽越过收缩上限把顶栏整条拖没。
 */
@OptIn(ExperimentalMaterial3Api::class)
private class ShrinkOnScrollTopAppBarBehavior(
    override val state: TopAppBarState,
    private val maxCollapsePx: Float
) : TopAppBarScrollBehavior {

    override val isPinned: Boolean = true

    override val snapAnimationSpec: AnimationSpec<Float>? = null

    override val flingAnimationSpec: DecayAnimationSpec<Float>? = null

    override val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 手指上滑（浏览下方内容，available.y < 0）→ 顶栏立即收缩
                if (maxCollapsePx <= 0f || available.y >= 0f) return Offset.Zero
                return collapseBy(available.y)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 手指下滑且内容已滚回顶部（滚动容器消耗后仍有剩余）→ 顶栏才展开
                if (maxCollapsePx <= 0f || available.y <= 0f) return Offset.Zero
                return collapseBy(available.y)
            }

            /** 按 delta 调整高度偏移并返回实际消耗量。 */
            private fun collapseBy(delta: Float): Offset {
                val collapseLimit = -maxCollapsePx
                val previous = state.heightOffset
                state.heightOffset = (previous + delta).coerceIn(collapseLimit, 0f)
                val consumed = state.heightOffset - previous
                return if (consumed != 0f) {
                    Offset(0f, consumed)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (maxCollapsePx <= 0f) return Velocity.Zero
                val collapseLimit = -maxCollapsePx
                // 惯性方向优先：向下滑到底 → 展开，向上滑 → 收缩；无惯性 → 吸附到较近一端
                val target = when {
                    available.y > 0f -> 0f
                    available.y < 0f -> collapseLimit
                    state.heightOffset > collapseLimit / 2f -> 0f
                    else -> collapseLimit
                }
                if (target == state.heightOffset) return Velocity.Zero
                Animatable(state.heightOffset).animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) {
                    state.heightOffset = value
                }
                return Velocity.Zero
            }
        }
}
