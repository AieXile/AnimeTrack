package com.aiexile.animetrack.ui.player.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.isExpandedWidth

/** 小窗视频卡片的圆角半径 */
internal val VideoCornerRadius = 16.dp

/**
 * 用页面底色在四角补画「矩形减去 [shape]」的补集，代替对视频做裁剪。
 *
 * 视频是 SurfaceView：Compose 的路径裁剪（Outline.Generic）在硬件层上锯齿明显，
 * 而补集是通过普通 drawPath 画的，边缘由 Skia 抗锯齿，能保住 squircle 的连续曲率。
 */
private fun Modifier.squircleCornerMask(
    shape: Shape,
    backgroundColor: Color,
    enabled: () -> Boolean = { true }
): Modifier =
    drawWithContent {
        drawContent()
        if (!enabled()) return@drawWithContent
        val outline = shape.createOutline(size, layoutDirection, this)
        if (outline is Outline.Generic) {
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addPath(outline.path)
            }
            drawPath(mask, backgroundColor)
        }
    }

/** 固定视频槽位，不在小窗与全屏间迁移 AndroidView，避免暂停切全屏时重绑 Surface 导致黑屏或画面偏移。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun PlayerPageLayout(
    isFullscreen: Boolean,
    title: String,
    /** 共享元素配对用的番剧 id / 命名空间前缀；id 为 0 或前缀为 null 时不参与 Morph */
    animeId: Int,
    sharedKeyPrefix: String?,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onBack: () -> Unit,
    video: @Composable (isCoverMorphing: Boolean) -> Unit,
    details: @Composable () -> Unit
) {
    val expandedWidth = isExpandedWidth()
    val sharedScope = sharedTransitionScope
    val sharedVisibility = animatedVisibilityScope
    val coverSharedState = if (
        !isFullscreen && animeId > 0 && sharedKeyPrefix != null &&
        sharedScope != null && sharedVisibility != null
    ) {
        with(sharedScope) {
            rememberSharedContentState(key = "${sharedKeyPrefix}cover_$animeId")
        }
    } else {
        null
    }

    AppScaffold(
        title = title,
        onBack = onBack,
        showTopBar = !isFullscreen,
        contentWindowInsets = if (isFullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        // 与详情页顶栏一致：16sp Medium 单行，播放器页标题普遍较长，用小字号更稳
        titleContent = {
            Text(
                text = title,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    ) { chrome ->
        BoxWithConstraints(Modifier.fillMaxSize().then(chrome.layout).padding(chrome.content)) {
            val expanded = expandedWidth && maxWidth > maxHeight
            // 同一个 Shape 实例复用 outline 缓存；页面底色用于四角补画
            val cornerShape = remember { SquircleShape(VideoCornerRadius) }
            val pageBackground = MaterialTheme.colorScheme.background
            Layout(
                content = {
                    // 非全屏时视频是一张圆角卡片：与下半区的卡片语言一致，
                    // 也不再让黑色视频区直接顶到白色顶栏上。
                    //
                    // 圆角不用 clip：视频是 SurfaceView，Outline.Generic（SquircleShape）的路径裁剪
                    // 在硬件层上会出现锯齿；裁剪成纯圆弧又失去 squircle 的连续曲率，边缘显得突兀。
                    // 改为不做裁剪，用页面底色在四角补画「矩形减去 squircle」的补集，
                    // 由 Skia 正常抗锯齿，既有平滑圆角又不会有硬边。
                    val isCoverMorphing =
                        coverSharedState != null && sharedScope != null && sharedVisibility != null && (
                            sharedVisibility.transition.currentState != sharedVisibility.transition.targetState ||
                                (sharedScope.isTransitionActive && coverSharedState.isMatchFound)
                            )
                    val sharedModifier = if (
                        coverSharedState != null && sharedScope != null && sharedVisibility != null
                    ) {
                        with(sharedScope) {
                            // 两侧内容不同（详情页封面 vs 播放器视频卡），用 sharedBounds 按包围盒缩放。
                            // 用 FillBounds 而不是 Crop：Crop 在 2:3 → 16:9 的中间形态会把卡片左右
                            // （或上下）裁掉，被裁掉的正是圆角所在的边缘，飞行中看起来就成了直角。
                            // clipInOverlayDuringTransition 直接按当前动画包围盒裁成 16dp squircle，
                            // 避免把 squircleCornerMask 的底色块带进 Overlay 或在 16:9↔2:3 缩放中变形；
                            // zIndexInOverlay = 1f 让退出时播放卡在详情封面上方平滑淡出。
                            Modifier.sharedBounds(
                                sharedContentState = coverSharedState,
                                animatedVisibilityScope = sharedVisibility,
                                resizeMode = scaleToBounds(ContentScale.FillBounds, Alignment.Center),
                                zIndexInOverlay = 1f,
                                clipInOverlayDuringTransition = OverlayClip(cornerShape)
                            )
                        }
                    } else {
                        Modifier
                    }
                    // 静止态由 squircleCornerMask 用页面底色盖住 SurfaceView 的直角；
                    // 进入 Overlay 飞行期间由 OverlayClip 统一裁切，不再绘制四角底色块，
                    // 并且同步由 video(isCoverMorphing) 隐藏 SurfaceView 避免在原位挖出直角透明洞。
                    val videoSurface = if (isFullscreen) Modifier
                    else Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        .then(sharedModifier)
                        .background(Color.Black, cornerShape)
                        .squircleCornerMask(cornerShape, pageBackground) {
                            !(sharedScope?.isTransitionActive == true && coverSharedState?.isMatchFound == true)
                        }
                    Box(
                        videoSurface
                            .clipToBounds()
                    ) {
                        video(isCoverMorphing)
                    }
                    if (!isFullscreen) {
                        Box(Modifier.fillMaxSize()) {
                            details()
                        }
                    }
                }
            ) { measurables, constraints ->
                val videoMeasurable = measurables[0]
                val detailsMeasurable = measurables.getOrNull(1)
                val totalWidth = constraints.maxWidth
                val totalHeight = constraints.maxHeight
                if (isFullscreen || detailsMeasurable == null) {
                    val videoPlaceable = videoMeasurable.measure(
                        Constraints.fixed(totalWidth, totalHeight)
                    )
                    layout(totalWidth, totalHeight) {
                        videoPlaceable.placeRelative(0, 0)
                    }
                } else if (expanded) {
                    val leftWidth = totalWidth / 2
                    val rightWidth = totalWidth - leftWidth
                    val videoPlaceable = videoMeasurable.measure(
                        Constraints.fixed(leftWidth, totalHeight)
                    )
                    val detailsPlaceable = detailsMeasurable.measure(
                        Constraints.fixed(rightWidth, totalHeight)
                    )
                    layout(totalWidth, totalHeight) {
                        videoPlaceable.placeRelative(0, 0)
                        detailsPlaceable.placeRelative(leftWidth, 0)
                    }
                } else {
                    // 视频卡片左右各内缩 16dp、顶部留 8dp，按内宽算 16:9 才不会多出黑边
                    val horizontalInset = 16.dp.roundToPx()
                    val topInset = 8.dp.roundToPx()
                    val videoHeight = minOf(
                        ((totalWidth - horizontalInset * 2) * 9f / 16f).toInt() + topInset,
                        (totalHeight * 0.5f).toInt()
                    ).coerceAtLeast(0)
                    val detailsHeight = (totalHeight - videoHeight).coerceAtLeast(0)
                    val videoPlaceable = videoMeasurable.measure(
                        Constraints.fixed(totalWidth, videoHeight)
                    )
                    val detailsPlaceable = detailsMeasurable.measure(
                        Constraints.fixed(totalWidth, detailsHeight)
                    )
                    layout(totalWidth, totalHeight) {
                        videoPlaceable.placeRelative(0, 0)
                        detailsPlaceable.placeRelative(0, videoHeight)
                    }
                }
            }
        }
    }
}
