package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit

/**
 * 微光文字：文字本身不动，一道柔和高光沿对角线持续循环扫过，
 * 用于加载状态与「进行中」文案，替代文字位移或转圈。
 *
 * 文字的大多数部分保持 [color]，只有移动中的光带提亮（近白文字则压暗两端、
 * 用原色光带制造对比），因此任何主题下都能读出扫光方向。
 * [durationMillis] 为一轮扫光的时长，两端各含一段短暂停顿。
 */
@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    style: TextStyle = LocalTextStyle.current,
    durationMillis: Int = ShimmerCycleDurationMs,
) {
    val baseColor = if (color.isSpecified) color else style.color.takeOrElse { LocalContentColor.current }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val transition = rememberInfiniteTransition(label = "shimmerText")
    val progress by transition.animateFloat(
        initialValue = ShimmerProgressStart,
        targetValue = ShimmerProgressEnd,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val brush = if (size != IntSize.Zero) {
        shimmerBrush(
            size = size,
            progress = progress,
            baseColor = baseColor,
            glowColor = shimmerGlowColor(baseColor)
        )
    } else {
        null
    }

    Text(
        text = text,
        modifier = modifier.onSizeChanged { size = it },
        color = baseColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        maxLines = maxLines,
        softWrap = softWrap,
        // 画笔存在时优先于 color 渲染
        style = if (brush != null) style.copy(brush = brush) else style
    )
}
