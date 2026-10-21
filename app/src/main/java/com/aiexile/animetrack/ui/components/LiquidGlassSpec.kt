package com.aiexile.animetrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * 液态玻璃渲染规格：底色（含透明度）+ 模糊 + 折射参数。
 *
 * 此前胶囊、悬浮 FAB、顶栏玻璃条各自手写 `vibrancy + blur + lens + drawRect(底色)`，
 * 参数分散在多个文件里，同一套「液态玻璃」开关下出现「导航栏更白、按钮更透」的
 * 观感分裂。此处收敛为唯一出处：所有玻璃消费方按 [LiquidGlassKind] 取规格，
 * 需要调整白度/磨砂感时只改 [liquidGlassSpec] 一处。
 */
@Immutable
data class LiquidGlassSpec(
    val containerColor: Color,
    val blurRadius: Dp,
    val lensRefractionHeight: Dp,
    val lensRefractionAmount: Dp,
    val vibrancy: Boolean = true
)

/**
 * 玻璃使用场景（决定白度/磨砂强度）：
 * - [Capsule]：悬浮胶囊导航。54dp 高宽条，8dp 模糊 + 24dp 折射，
 *   参照 Kyant0 LiquidBottomTabs 示例，是整套液态玻璃的「白度基准」。
 * - [CapsuleCompanion]：「导航栏旁」的加号/回到顶部按钮。与胶囊同底色、同模糊
 *   （= 同白度），折射按 52dp 小面积收敛到 12dp，避免折射区互相重叠把按钮洗成色块。
 * - [Fab]：其余悬浮按钮（右下角 FAB / 顶栏搜索条与添加按钮）。保持轻模糊通透感，
 *   参照 Kyant0 LiquidButton 示例。
 * - [Bar]：状态栏玻璃条。细条不做折射（避免边缘畸变），只模糊 + 底色。
 * - [Menu]：设置项选项菜单卡片。独立成档：16dp 模糊 + 低底色（比毛玻璃档更透），
 *   不做折射与边缘高光——两者在大卡片上均触发真机 RenderThread 闪退（2026-10-20）。
 * - [Scrim]：导航栏四周的磨砂背板。底色更淡且不加 vibrancy
 *   （vibrancy 的 ColorFilterEffect 叠加在该层会触发 GPU 水平细线伪影）。
 */
enum class LiquidGlassKind { Capsule, CapsuleCompanion, Fab, Bar, Menu, Scrim }

/** 玻璃底色：胶囊 / 悬浮按钮共用（40% 白或 40% 近黑） */
private val SurfaceTintLight = Color(0xFFFAFAFA).copy(alpha = 0.4f)
private val SurfaceTintDark = Color(0xFF121212).copy(alpha = 0.4f)

/** 磨砂背板底色：比玻璃本体淡一档（30%），避免整片发白 */
private val ScrimTintLight = Color(0xFFFAFAFA).copy(alpha = 0.3f)
private val ScrimTintDark = Color(0xFF121212).copy(alpha = 0.3f)

/**
 * 菜单卡片底色：大面积浮层与「胶囊 / 按钮」不同——40% 白叠在白色设置页上会被洗成
 * 一块白板（模糊完全看不见），因此压到 22%（暗色 30%）：比毛玻璃档（40% tint +
 * 不透明底）更透，让「液态玻璃 vs 毛玻璃」两档拉开通透度差异。
 */
private val MenuTintLight = Color(0xFFFAFAFA).copy(alpha = 0.22f)
private val MenuTintDark = Color(0xFF121212).copy(alpha = 0.30f)

/**
 * 按场景取玻璃规格。[darkTheme] 需传应用内主题明暗（[isAppDarkTheme]），
 * 而非系统暗色，保证「应用暗色 ≠ 系统暗色」时底色同步切换。
 */
fun liquidGlassSpec(kind: LiquidGlassKind, darkTheme: Boolean): LiquidGlassSpec {
    val surface = if (darkTheme) SurfaceTintDark else SurfaceTintLight
    return when (kind) {
        LiquidGlassKind.Capsule -> LiquidGlassSpec(
            containerColor = surface,
            blurRadius = 8.dp,
            lensRefractionHeight = 24.dp,
            lensRefractionAmount = 24.dp
        )

        LiquidGlassKind.CapsuleCompanion -> LiquidGlassSpec(
            containerColor = surface,
            blurRadius = 8.dp,
            lensRefractionHeight = 12.dp,
            lensRefractionAmount = 24.dp
        )

        LiquidGlassKind.Fab -> LiquidGlassSpec(
            containerColor = surface,
            blurRadius = 2.dp,
            lensRefractionHeight = 12.dp,
            lensRefractionAmount = 24.dp
        )

        LiquidGlassKind.Bar -> LiquidGlassSpec(
            containerColor = surface,
            blurRadius = 8.dp,
            lensRefractionHeight = 0.dp,
            lensRefractionAmount = 0.dp
        )

        LiquidGlassKind.Menu -> LiquidGlassSpec(
            containerColor = if (darkTheme) MenuTintDark else MenuTintLight,
            // 大卡片用比胶囊更强的模糊（16dp）：页面文字被糊成柔和的磨砂纹理。
            // 折射关闭：大卡片上开 lens(16,24) 真机打开弹层即 RenderThread 闪退
            // （2026-10-20 试验，单独 lens、已无 Highlight 仍崩），Menu 档与 Bar 同策略不做畸变
            blurRadius = 16.dp,
            lensRefractionHeight = 0.dp,
            lensRefractionAmount = 0.dp
        )

        LiquidGlassKind.Scrim -> LiquidGlassSpec(
            containerColor = if (darkTheme) ScrimTintDark else ScrimTintLight,
            blurRadius = 8.dp,
            lensRefractionHeight = 0.dp,
            lensRefractionAmount = 0.dp,
            vibrancy = false
        )
    }
}

/** 组合内取规格：跟随应用内主题明暗 */
@Composable
fun liquidGlassSpec(kind: LiquidGlassKind): LiquidGlassSpec =
    liquidGlassSpec(kind, isAppDarkTheme())

/**
 * 按 [spec] 渲染一块液态玻璃（采样 [backdrop] 内容 + vibrancy + blur + lens + 底色）。
 *
 * 保留 kyant `drawBackdrop` 的默认边缘高光/外阴影（与改造前一致）；磨砂背板这类
 * 不需要高光的场景显式传 null。[layerBlock] 用于按压放大 / 拖拽形变等交互物理。
 */
internal fun Modifier.drawLiquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape,
    spec: LiquidGlassSpec,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    highlight: (() -> Highlight?)? = { Highlight.Default },
    shadow: (() -> Shadow?)? = { Shadow.Default },
    innerShadow: (() -> InnerShadow?)? = null
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        if (spec.vibrancy) vibrancy()
        if (spec.blurRadius > 0.dp) blur(spec.blurRadius.toPx())
        if (spec.lensRefractionHeight > 0.dp && spec.lensRefractionAmount > 0.dp) {
            lens(spec.lensRefractionHeight.toPx(), spec.lensRefractionAmount.toPx())
        }
    },
    highlight = highlight,
    shadow = shadow,
    innerShadow = innerShadow,
    layerBlock = layerBlock,
    onDrawSurface = { drawRect(spec.containerColor) }
)
