package com.aiexile.animetrack.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.LiquidGlassKind
import com.aiexile.animetrack.ui.components.drawLiquidGlass
import com.aiexile.animetrack.ui.components.liquidGlassSpec
import com.aiexile.animetrack.ui.components.liquidglass.InteractiveHighlight
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.LocalChromeStyle
import com.kyant.backdrop.Backdrop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 三档统一返回按钮：
 *
 * - [ChromeStyle.STANDARD]：标准 IconButton（与 M3 TopAppBar 一致）；
 * - [ChromeStyle.HAZE]：48dp 触控区内 40dp 轻量圆底 + 细描边；
 * - [ChromeStyle.LIQUID_GLASS]：40dp 液态玻璃圆钮，渲染规格与主界面悬浮 FAB
 *   完全一致（[LiquidGlassKind.Fab]：40% 底色 + 2dp 模糊 + 12/24 折射），
 *   并保留按压放大 / 拖拽形变 / 指尖跟随高光。
 *
 * 用 FAB 规格而不是顶栏整条玻璃的规格：FAB 底色更实、模糊更轻，
 * 不会因背后封面/卡片颜色过重而把返回图标压得看不清。
 *
 * @param backdrop 液态玻璃档的采样源；为 null 时退回轻量圆底
 */
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChromeStyle = LocalChromeStyle.current,
    backdrop: Backdrop? = null,
    contentDescription: String? = null
) {
    val desc = contentDescription ?: stringResource(R.string.common_back)
    when {
        style == ChromeStyle.LIQUID_GLASS && backdrop != null -> {
            LiquidGlassCircleBackButton(
                onClick = onClick,
                backdrop = backdrop,
                contentDescription = desc,
                modifier = modifier
            )
        }

        style == ChromeStyle.STANDARD -> {
            IconButton(onClick = onClick, modifier = modifier) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                    contentDescription = desc,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        else -> {
            FlatCircleBackButton(
                onClick = onClick,
                contentDescription = desc,
                modifier = modifier
            )
        }
    }
}

/**
 * 圆形液态玻璃返回按钮：48dp 触控区内 40dp 玻璃圆。
 *
 * 渲染规格取 [LiquidGlassKind.Fab]（与右下角 FAB、顶栏搜索/添加 FAB 同源），
 * 交互物理与 FAB 一致：按压放大、拖动朝手指方向平移并沿角度拉伸（tanh 限幅）、
 * 指尖跟随高光；手势仅观察不消费，不影响点击返回。
 */
@Composable
private fun LiquidGlassCircleBackButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val glassSpec = liquidGlassSpec(LiquidGlassKind.Fab)
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope)
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .drawLiquidGlass(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    spec = glassSpec,
                    layerBlock = {
                        // 交互物理反馈：按压放大 + 拖拽朝手指方向平移（tanh 限幅）
                        // + 沿拖拽角度果冻拉伸
                        val width = size.width
                        val height = size.height
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4.dp.toPx() / height, progress)
                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX =
                            maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY =
                            maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                        val maxDragScale = 4.dp.toPx() / height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale *
                            abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                        scaleY = scale + maxDragScale *
                            abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                    }
                )
                // 高光层绘制在玻璃之上、图标之下；手势仅观察不消费，不影响点击
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** 轻量圆底返回按钮：毛玻璃档与无采样源时的兜底。
 *  底色用 surface 62% + 10% 细描边——毛玻璃顶栏本身不重，圆底不会被背景压暗。 */
@Composable
private fun FlatCircleBackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                .border(
                    width = 0.5.dp,
                    color = baseColor.copy(alpha = 0.10f),
                    shape = CircleShape
                )
        )
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
