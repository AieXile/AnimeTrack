package com.aiexile.animetrack.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import com.aiexile.animetrack.data.GreetingEffectStyle
import kotlinx.coroutines.delay

/** 扫光动画时长（ms）；进度按文字宽度归一化，光带从左侧外扫到右侧外 */
private const val SWEEP_DURATION_MS = 750
private const val SWEEP_START = -0.4f
private const val SWEEP_END = 1.4f

/** 光带半宽占文字宽度比例（渐变两端为基础色，中心为白色高亮） */
private const val SWEEP_BAND_WIDTH_RATIO = 0.22f

/**
 * 欢迎语文本：按 [style] 播放入场动画。
 * - [GreetingEffectStyle.TYPING] 打字机逐字显示
 * - [GreetingEffectStyle.SCRAMBLE] 乱码逐渐解码：全文字符先显示随机乱码，从左到右逐位锁定为真实字符
 * - [GreetingEffectStyle.NONE] 直接显示全文
 *
 * 点击文字播放一次「扫光」：一道白色光带沿对角线扫过文字，任意风格下可用。
 * [shouldAnimate] 与 [onAnimated] 由 HomeViewModel 控制只播一次（文本变化时重播）。
 */
@Composable
fun GreetingText(
    greetingText: String,
    style: GreetingEffectStyle,
    shouldAnimate: Boolean,
    onAnimated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 锁存进入组合时的动画意图：动画开始即标记完成后，上层重组重新计算的
    // shouldAnimate 会翻转为 false，若直接作为 LaunchedEffect key，冷启动初期
    // uiState/DataStore 加载触发的重组会把动画打断直接跳到全文
    val animateOnce = remember { shouldAnimate }

    // 点击扫光：进度按文字宽度归一化，光带从左侧外扫到右侧外
    val baseColor = MaterialTheme.colorScheme.onBackground
    var sweepTrigger by remember { mutableIntStateOf(0) }
    val sweepProgress = remember { Animatable(SWEEP_END) }
    var textSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(sweepTrigger) {
        if (sweepTrigger > 0 && textSize != IntSize.Zero) {
            sweepProgress.snapTo(SWEEP_START)
            sweepProgress.animateTo(
                targetValue = SWEEP_END,
                animationSpec = tween(SWEEP_DURATION_MS, easing = FastOutSlowInEasing)
            )
        }
    }

    val sweep = sweepProgress.value
    val sweepBrush = if (sweep > SWEEP_START && sweep < SWEEP_END && textSize != IntSize.Zero) {
        val width = textSize.width.toFloat()
        val height = textSize.height.toFloat()
        val centerX = width * sweep
        val halfBand = width * SWEEP_BAND_WIDTH_RATIO
        Brush.linearGradient(
            colorStops = arrayOf(
                0.35f to baseColor,
                0.5f to Color.White,
                0.65f to baseColor
            ),
            start = Offset(centerX - halfBand, height),
            end = Offset(centerX + halfBand, 0f)
        )
    } else {
        null
    }

    val sweepModifier = Modifier
        .onSizeChanged { textSize = it }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { sweepTrigger++ }

    val textModifier = modifier.then(sweepModifier)

    when (style) {
        GreetingEffectStyle.TYPING -> TypingBody(
            greetingText = greetingText,
            animateOnce = animateOnce,
            onAnimated = onAnimated,
            sweepBrush = sweepBrush,
            modifier = textModifier
        )
        GreetingEffectStyle.SCRAMBLE -> ScrambleBody(
            greetingText = greetingText,
            animateOnce = animateOnce,
            onAnimated = onAnimated,
            sweepBrush = sweepBrush,
            modifier = textModifier
        )
        GreetingEffectStyle.NONE -> StaticBody(greetingText, sweepBrush, textModifier)
    }
}

@Composable
private fun TypingBody(
    greetingText: String,
    animateOnce: Boolean,
    onAnimated: (String) -> Unit,
    sweepBrush: Brush?,
    modifier: Modifier = Modifier
) {
    var visibleCharCount by remember { mutableIntStateOf(if (animateOnce) 0 else greetingText.length) }

    LaunchedEffect(greetingText, animateOnce) {
        if (animateOnce) {
            // 动画开始即标记完成：开启「下滑隐藏顶栏」时顶栏收拢会移除本组件并取消
            // 打字协程，若完成时才标记，重新展开会从头重播；提前标记后改为直接显示全文
            onAnimated(greetingText)
            delay(500)
            for (i in 1..greetingText.length) {
                visibleCharCount = i
                delay(100)
            }
        } else {
            visibleCharCount = greetingText.length
        }
    }

    GreetingLabel(
        text = greetingText.substring(0, visibleCharCount.coerceAtMost(greetingText.length)),
        sweepBrush = sweepBrush,
        modifier = modifier
    )
}

/** 乱码字符池：等宽符号为主，避免乱码与真实字符宽度差异过大导致文本剧烈跳动 */
private const val SCRAMBLE_CHARS = "!<>-_\\/[]{}=+*^?#@$%&"

/** 每帧间隔（ms）与每多少帧锁定一个字符 */
private const val SCRAMBLE_FRAME_DELAY = 50L
private const val SCRAMBLE_LOCK_EVERY_FRAMES = 2

@Composable
private fun ScrambleBody(
    greetingText: String,
    animateOnce: Boolean,
    onAnimated: (String) -> Unit,
    sweepBrush: Brush?,
    modifier: Modifier = Modifier
) {
    var displayText by remember {
        mutableStateOf(if (animateOnce) scrambleText(greetingText, 0) else greetingText)
    }

    LaunchedEffect(greetingText, animateOnce) {
        if (animateOnce) {
            // 与打字机一致：动画开始即标记完成，顶栏收拢重建后直接显示全文
            onAnimated(greetingText)
            delay(300)
            var lockedCount = 0
            var frame = 0
            while (lockedCount < greetingText.length) {
                displayText = scrambleText(greetingText, lockedCount)
                delay(SCRAMBLE_FRAME_DELAY)
                frame++
                if (frame % SCRAMBLE_LOCK_EVERY_FRAMES == 0) lockedCount++
            }
            displayText = greetingText
        } else {
            displayText = greetingText
        }
    }

    GreetingLabel(
        text = displayText,
        sweepBrush = sweepBrush,
        modifier = modifier
    )
}

/** 生成乱码文本：前 [lockedCount] 位为真实字符，其余位随机取自乱码池 */
private fun scrambleText(text: String, lockedCount: Int): String {
    if (text.isEmpty()) return text
    return buildString(text.length) {
        text.forEachIndexed { index, char ->
            if (index < lockedCount) append(char)
            else append(SCRAMBLE_CHARS.random())
        }
    }
}

@Composable
private fun StaticBody(
    greetingText: String,
    sweepBrush: Brush?,
    modifier: Modifier = Modifier
) {
    GreetingLabel(greetingText, sweepBrush, modifier)
}

/**
 * 欢迎语统一渲染：[sweepBrush] 非空时以渐变画笔绘制文字（扫光光带扫过高亮处），
 * 否则使用常规颜色。
 */
@Composable
private fun GreetingLabel(
    text: String,
    sweepBrush: Brush?,
    modifier: Modifier = Modifier
) {
    val baseStyle = MaterialTheme.typography.headlineSmall
    Text(
        text = text,
        // 画笔存在时覆盖颜色渲染（Compose 中 brush 优先于 color）
        style = if (sweepBrush != null) baseStyle.copy(brush = sweepBrush) else baseStyle,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
