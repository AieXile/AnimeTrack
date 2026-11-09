package com.aiexile.animetrack.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.GreetingEffectStyle
import com.aiexile.animetrack.ui.components.ShimmerSweepDurationMs
import com.aiexile.animetrack.ui.components.ShimmerSweepEnd
import com.aiexile.animetrack.ui.components.ShimmerSweepStart
import com.aiexile.animetrack.ui.components.shimmerBrush
import com.aiexile.animetrack.ui.components.shimmerGlowColor
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

/** 扫光参数与通用 [com.aiexile.animetrack.ui.components.sweepHighlight]、加载态微光共用同一套令牌 */
private const val SWEEP_DURATION_MS = ShimmerSweepDurationMs
private const val SWEEP_START = ShimmerSweepStart
private const val SWEEP_END = ShimmerSweepEnd

/**
 * 欢迎语文本：按 [style] 播放入场动画。
 * - [GreetingEffectStyle.TYPING] 打字机逐字显示
 * - [GreetingEffectStyle.SCRAMBLE] 密文逐渐解码：整句先显示为静止的卢恩密文，扫描带从左到右经过时，字符才快速闪变并落定为真实文字
 * - [GreetingEffectStyle.NONE] 直接显示全文
 *
 * 点击文字播放一次「扫光」：一道柔和高光沿对角线扫过文字，任意风格下可用。
 * 光带与加载态 [com.aiexile.animetrack.ui.components.ShimmerText] 共用同一套画笔。
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
        shimmerBrush(
            size = textSize,
            progress = sweep,
            baseColor = baseColor,
            glowColor = shimmerGlowColor(baseColor)
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

/**
 * 解码阶段的字形池：Elder Futhark 卢恩字母。
 * 用真实文字字形而非 ASCII 标点，密文阶段看起来像一门未知语言，而不是符号闪烁。
 */
private const val RUNIC_GLYPHS =
    "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛟᛞᛠᛡᛢᛣᛤᛥᛦᛧᛨᛩᛪ"

/** 解码节奏：起播停顿、扫描线出发前的停顿、扫描全程、收尾停顿 */
private const val SCRAMBLE_START_DELAY_MS = 220L
private const val SCRAMBLE_SWEEP_LEAD_MS = 100L
private const val SCRAMBLE_SWEEP_MS = 850L
private const val SCRAMBLE_END_PAD_MS = 120L

/** 扫描线临近多久开始抖动字形、落定前多久开始与真实字符交替闪现 */
private const val SCRAMBLE_CHURN_WINDOW_MS = 200L
private const val SCRAMBLE_CONVERGE_MS = 130L
private const val SCRAMBLE_CHURN_MS = 45L
private const val SCRAMBLE_FLICKER_MS = 30L
private const val SCRAMBLE_JITTER_MS = 35L

/** 密文字形透明度与扫描带半宽（占文字总宽比例） */
private const val SCRAMBLE_CIPHER_ALPHA = 0.42f
private const val SCAN_BAND_WIDTH_FRACTION = 0.12f

/** 空白、标点与代理对保持原样，只有文字参与解码 */
private fun isScrambleable(char: Char): Boolean =
    !char.isWhitespace() && char.isLetterOrDigit() && !char.isSurrogate()

private fun randomRune(): Char = RUNIC_GLYPHS.random()

@Composable
private fun ScrambleBody(
    greetingText: String,
    animateOnce: Boolean,
    onAnimated: (String) -> Unit,
    sweepBrush: Brush?,
    modifier: Modifier = Modifier
) {
    val typography = MaterialTheme.typography.headlineSmall
    val baseStyle = remember(typography) {
        typography.copy(fontWeight = FontWeight.SemiBold)
    }
    // 随 App 打包，避免部分机型缺少系统卢恩字体时显示为方框
    val runicFamily = remember { FontFamily(Font(R.font.noto_sans_runic)) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val darkTheme = isAppDarkTheme()
    val baseColor = MaterialTheme.colorScheme.onBackground
    val cipherColor = baseColor.copy(alpha = SCRAMBLE_CIPHER_ALPHA)
    val scanColor = if (darkTheme) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.05f)
    }

    // 每个真实字符在整行中的占位宽度；卢恩字形通过 letterSpacing 补齐到该宽度，
    // 解码过程中整行不会左右伸缩
    val charWidths = remember(greetingText, baseStyle) {
        val layout = measurer.measure(AnnotatedString(greetingText), baseStyle)
        FloatArray(greetingText.length) { index ->
            layout.getHorizontalPosition(index + 1, usePrimaryDirection = false) -
                layout.getHorizontalPosition(index, usePrimaryDirection = false)
        }
    }
    val runeStyle = remember(baseStyle, runicFamily) {
        baseStyle.copy(
            fontFamily = runicFamily,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp
        )
    }
    val runeWidths = remember(runeStyle) {
        RUNIC_GLYPHS.toSet().associateWith { rune ->
            measurer.measure(AnnotatedString(rune.toString()), runeStyle).size.width.toFloat()
        }
    }

    // 扫描线在文字宽度上的归一化位置；0 表示尚未出发，1 表示已扫到最右侧
    var scanProgress by remember { mutableStateOf(0f) }
    var display by remember(greetingText, animateOnce) {
        mutableStateOf(
            if (animateOnce) {
                buildScrambleText(
                    greetingText = greetingText,
                    elapsed = 0L,
                    lockAt = LongArray(greetingText.length) { Long.MAX_VALUE },
                    runes = CharArray(greetingText.length) { randomRune() },
                    charWidths = charWidths,
                    runeWidths = runeWidths,
                    runicFamily = runicFamily,
                    cipherColor = cipherColor,
                    density = density
                )
            } else {
                null
            }
        )
    }

    LaunchedEffect(greetingText, animateOnce) {
        if (!animateOnce) {
            display = null
            return@LaunchedEffect
        }
        // 与打字机一致：动画开始即标记完成，顶栏收拢重建后直接显示全文
        onAnimated(greetingText)

        val length = greetingText.length
        val totalWidth = charWidths.sum().takeIf { it > 0f } ?: 1f
        val lockAt = LongArray(length) { Long.MAX_VALUE }
        var maxLock = 0L
        var hasScramble = false
        var accumulated = 0f
        for (index in 0 until length) {
            val width = charWidths.getOrElse(index) { 0f }
            val center = accumulated + width / 2f
            accumulated += width
            if (!isScrambleable(greetingText[index])) continue
            hasScramble = true
            // 扫描线经过字符中心的时刻即落定时刻，叠加小幅抖动避免节奏机械
            val sweepAt = SCRAMBLE_SWEEP_LEAD_MS +
                (center / totalWidth * SCRAMBLE_SWEEP_MS).toLong() +
                Random.nextLong(-SCRAMBLE_JITTER_MS, SCRAMBLE_JITTER_MS + 1)
            maxLock = maxOf(maxLock, sweepAt)
            lockAt[index] = maxLock
        }
        if (!hasScramble) {
            display = null
            return@LaunchedEffect
        }

        delay(SCRAMBLE_START_DELAY_MS)
        val startedAt = withFrameMillis { it }
        val runes = CharArray(length) { randomRune() }
        var lastChurnAt = Long.MIN_VALUE

        while (true) {
            val now = withFrameMillis { it }
            val elapsed = now - startedAt
            val sweepElapsed = (elapsed - SCRAMBLE_SWEEP_LEAD_MS).coerceAtLeast(0L)
            scanProgress = (sweepElapsed.toFloat() / SCRAMBLE_SWEEP_MS).coerceIn(0f, 1f)

            if (elapsed - lastChurnAt >= SCRAMBLE_CHURN_MS) {
                for (index in 0 until length) {
                    val untilLock = lockAt[index] - elapsed
                    // 扫描线临近时才抖动；未被扫到的部分保持静止密文，读起来像“正在解析”
                    if (untilLock in 1..SCRAMBLE_CHURN_WINDOW_MS &&
                        isScrambleable(greetingText[index])
                    ) {
                        runes[index] = randomRune()
                    }
                }
                lastChurnAt = elapsed
            }

            if (elapsed >= maxLock + SCRAMBLE_END_PAD_MS) {
                display = null
                break
            }
            display = buildScrambleText(
                greetingText = greetingText,
                elapsed = elapsed,
                lockAt = lockAt,
                runes = runes,
                charWidths = charWidths,
                runeWidths = runeWidths,
                runicFamily = runicFamily,
                cipherColor = cipherColor,
                density = density
            )
        }
    }

    if (display == null) {
        GreetingLabel(greetingText, sweepBrush, modifier)
    } else {
        // 扫描带只做极淡的单色提亮/压暗，跟随扫描线移动，不改变文字颜色
        val scanModifier = Modifier.drawWithContent {
            drawContent()
            if (scanProgress <= 0f || scanProgress >= 1f) return@drawWithContent
            val halfBand = size.width * SCAN_BAND_WIDTH_FRACTION
            val centerX = size.width * scanProgress
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.5f to scanColor,
                        1f to Color.Transparent
                    ),
                    startX = centerX - halfBand,
                    endX = centerX + halfBand
                )
            )
        }
        Box(modifier = modifier.then(scanModifier)) {
            Text(
                text = display!!,
                style = if (sweepBrush != null) baseStyle.copy(brush = sweepBrush) else baseStyle,
                color = baseColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 按当前进度拼出解码文本：已落定的位置显示真实字符；落定前的短暂窗口内
 * 真实字符与卢恩字形交替闪现；其余文字显示为低透明度卢恩密文，
 * 并用 [SpanStyle.letterSpacing] 补齐到真实字符的宽度。
 */
private fun buildScrambleText(
    greetingText: String,
    elapsed: Long,
    lockAt: LongArray,
    runes: CharArray,
    charWidths: FloatArray,
    runeWidths: Map<Char, Float>,
    runicFamily: FontFamily,
    cipherColor: Color,
    density: Density
): AnnotatedString = buildAnnotatedString {
    greetingText.forEachIndexed { index, char ->
        val targetWidth = charWidths.getOrElse(index) { 0f }
        if (!isScrambleable(char)) {
            append(char)
            return@forEachIndexed
        }
        val untilLock = lockAt.getOrElse(index) { Long.MAX_VALUE } - elapsed
        val locked = untilLock <= 0L
        // 落定前 130ms 内，真实字符与密文交替闪现，模拟解码器锁定信号的过程
        val converging = !locked && untilLock <= SCRAMBLE_CONVERGE_MS &&
            (untilLock / SCRAMBLE_FLICKER_MS) % 2L == 0L
        if (locked || converging) {
            append(char)
        } else {
            val rune = runes.getOrElse(index) { randomRune() }
            val runeWidth = runeWidths[rune] ?: targetWidth
            withStyle(
                SpanStyle(
                    color = cipherColor,
                    fontFamily = runicFamily,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = with(density) { (targetWidth - runeWidth).toSp() }
                )
            ) {
                append(rune)
            }
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
