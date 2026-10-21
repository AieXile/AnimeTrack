package com.aiexile.animetrack.ui.theme

import android.os.Build
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * 莫奈取色（Material You 动态配色 / Monet）读取。
 *
 * Android 12+ 由系统从壁纸提取一组配色并作为资源下发，无需任何权限；
 * 本应用只取其中的色相/彩度作为主题 seed，明暗与表面分层仍交由
 * [seedColorScheme] 生成，保证与固定预设的 duotone 观感一致。
 */
internal fun isMonetColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 壁纸取色结果：主题 seed + 色卡预览用的多色渐变。 */
internal class MonetPalette(
    val seed: Color,
    val swatchBrush: Brush,
)

/**
 * 读取系统动态配色；不支持或读取失败时返回 null，由调用方回退固定预设。
 *
 * 换壁纸不会重建 Activity，因此回到前台（ON_RESUME）时重读一次，避免配色长期陈旧。
 */
@Composable
internal fun rememberMonetPalette(): MonetPalette? {
    if (!isMonetColorSupported()) return null
    val context = LocalContext.current
    var refreshToken by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshToken++ }
    return remember(context, refreshToken) {
        // 恒用浅色动态配色的 primary：其色相/彩度最能代表壁纸，
        // 深色下的明度由本应用深色色板自行决定
        val scheme = dynamicLightColorScheme(context)
        MonetPalette(
            seed = scheme.primary,
            swatchBrush = Brush.sweepGradient(
                listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary)
            ),
        )
    }
}
