package com.aiexile.animetrack.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 高级模糊（毛玻璃）可自定义参数。
 *
 * 悬浮胶囊与悬浮按钮共用这一套参数：
 * - [blurRadius] 模糊半径，越大越"糊"；
 * - [backgroundColorAlpha] 毛玻璃底色不透明度，调低后底层内容更透；
 * - [tintAlpha] 着色不透明度，调高后在白色等浅色背景下玻璃质感更明显；
 * - [noiseFactor] 噪点强度（0..1），增加磨砂颗粒感，让浅色背景下的模糊更易察觉。
 */
data class AdvancedBlurConfig(
    val blurRadius: Dp = 24.dp,
    val backgroundColorAlpha: Float = 1f,
    val tintAlpha: Float = 0.4f,
    val noiseFactor: Float = 0f
) {
    companion object {
        /** 默认参数：与历史硬编码值保持一致 */
        val DEFAULT = AdvancedBlurConfig()
    }
}

/**
 * 按 [config] 生成共享的 haze 毛玻璃修饰符。
 *
 * @param hazeState 与页面内容 hazeSource 关联的共享状态
 * @param config 模糊参数，由 SettingsRepository 持久化并传入
 * @param shape 可选裁剪形状；传 null 时不裁剪（调用方自行裁剪）
 */
@Composable
fun advancedHazeEffect(
    hazeState: HazeState,
    config: AdvancedBlurConfig,
    shape: Shape? = null
): Modifier {
    val colorScheme = MaterialTheme.colorScheme
    var modifier: Modifier = Modifier
    if (shape != null) modifier = modifier.clip(shape)
    return modifier.hazeEffect(state = hazeState) {
        blurRadius = config.blurRadius
        backgroundColor = colorScheme.surfaceContainer.copy(alpha = config.backgroundColorAlpha.coerceIn(0f, 1f))
        tints = listOf(HazeTint(colorScheme.surfaceContainer.copy(alpha = config.tintAlpha.coerceIn(0f, 1f))))
        noiseFactor = config.noiseFactor.coerceIn(0f, 1f)
    }
}
