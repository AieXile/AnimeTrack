package com.aiexile.animetrack.ui.components.chrome

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 界面外壳采样源：按当前档位只暴露需要的那一个。
 *
 * - [ChromeStyle.STANDARD]：两者皆 null（不挂采样源，避免无消费者时的全屏 layer 录制）；
 * - [ChromeStyle.HAZE]：haze 毛玻璃采样源；
 * - [ChromeStyle.LIQUID_GLASS]：backdrop 折射采样源。
 */
@Immutable
class ChromeSources(
    val backdrop: LayerBackdrop?,
    val hazeState: HazeState?
)

/**
 * 创建当前档位需要的采样源。
 *
 * 两个 remember 对象本身开销很小，录制只发生在 [chromeSource] 实际挂载时，
 * 因此按档暴露 null 即等价于「只有消费者时才录制」。
 */
@Composable
fun rememberChromeSources(style: ChromeStyle): ChromeSources {
    val backdrop = rememberLayerBackdrop()
    val hazeState = rememberHazeState()
    return remember(style, backdrop, hazeState) {
        ChromeSources(
            backdrop = backdrop.takeIf { style == ChromeStyle.LIQUID_GLASS },
            hazeState = hazeState.takeIf { style == ChromeStyle.HAZE }
        )
    }
}

/**
 * 把采样源挂到页面内容根上：
 * - 标准档原样返回（内容不需要为玻璃准备任何东西）；
 * - 毛玻璃档挂 hazeSource；
 * - 液态玻璃档挂 layerBackdrop。
 */
fun Modifier.chromeSource(style: ChromeStyle, sources: ChromeSources): Modifier = when (style) {
    ChromeStyle.STANDARD -> this
    ChromeStyle.HAZE -> sources.hazeState?.let { hazeSource(state = it) } ?: this
    ChromeStyle.LIQUID_GLASS -> sources.backdrop?.let { layerBackdrop(it) } ?: this
}

/**
 * 设备是否支持实时模糊：haze 与 kyant backdrop 都依赖 RenderEffect（API 31+）。
 * 不支持时由 MainActivity 统一把档位降级为 [ChromeStyle.STANDARD]。
 */
fun chromeSupportsBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 高级模糊（HAZE 档）自定义参数：MainActivity 统一收集设置并下发，
 * 悬浮胶囊、悬浮按钮、主页顶栏与二级页顶栏共用同一份，避免各处重复 collect。
 */
val LocalAdvancedBlurConfig = staticCompositionLocalOf { AdvancedBlurConfig.DEFAULT }
