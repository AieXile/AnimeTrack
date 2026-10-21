package com.aiexile.animetrack.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.LiquidGlassKind
import com.aiexile.animetrack.ui.components.advancedHazeEffect
import com.aiexile.animetrack.ui.components.chrome.ChromeSources
import com.aiexile.animetrack.ui.components.chrome.LocalAdvancedBlurConfig
import com.aiexile.animetrack.ui.components.drawLiquidGlass
import com.aiexile.animetrack.ui.components.liquidGlassSpec
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import kotlin.math.roundToInt

/**
 * 选项菜单内容：卡片 + 行 + 勾选 + 按压/焦点态 + 内部滚动。
 *
 * 与宿主解耦：位置、动画、生命周期都在 [SettingsOptionHost]；这里只负责「长什么样、
 * 怎么选」。材质按当前 [ChromeStyle] 走实色 / haze / 液态玻璃三档，采样源由宿主传入。
 *
 * Morph 动画分工：外框（卡片表面）由宿主的 graphicsLayer 做矩形插值展开；
 * [progress] 驱动菜单内容延迟淡入，掩盖展开过程中的拉伸形变。
 */
@Composable
internal fun SettingsOptionMenu(
    request: OptionMenuRequest,
    style: ChromeStyle,
    sources: ChromeSources?,
    progress: () -> Float,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    onSelect: (Any?) -> Unit,
    onDismiss: () -> Unit,
) {
    val labelStyle = optionLabelStyle()
    val supportingStyle = optionSupportingStyle()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // 宽度按选项文案测量后夹在 160–280dp；副文案按约两行的份额参与，
    // 长描述换行到 2 行而不是把菜单撑宽（文案过长时菜单内换行到 2 行）
    val menuWidth = remember(request.entries, density, labelStyle, supportingStyle) {
        val contentWidthPx = request.entries.maxOf { entry ->
            val labelPx = textMeasurer.measure(entry.label, style = labelStyle, maxLines = 2).size.width
            val supportingPx = entry.supportingText
                ?.let { textMeasurer.measure(it, style = supportingStyle, maxLines = 2).size.width }
                ?.let { (it * SettingsOptionSpec.SupportingWidthShare).roundToInt() }
                ?: 0
            maxOf(labelPx, supportingPx)
        }
        val chromeWidthPx = with(density) {
            (SettingsOptionSpec.MenuItemHPadding * 2 +
                SettingsOptionSpec.MenuItemGap +
                SettingsOptionSpec.MenuItemCheck +
                // 卡片内衬由 LazyColumn 的 contentPadding 提供，也要计入宽度
                SettingsOptionSpec.MenuInset * 2).roundToPx()
        }
        with(density) { (contentWidthPx + chromeWidthPx).toDp() }
            .coerceIn(SettingsOptionSpec.MenuMinWidth, SettingsOptionSpec.MenuMaxWidth)
    }

    val listState = rememberLazyListState()
    val focusRequesters = remember(request) { List(request.entries.size) { FocusRequester() } }

    // 打开时滚到当前选中项并把焦点落在它上面；选中项不可用时退回第一个可用项
    LaunchedEffect(Unit) {
        withFrameNanos { }
        val firstEnabled = request.entries.indexOfFirst { it.enabled }
        val scrollTarget = request.selectedIndex.takeIf { it >= 0 } ?: firstEnabled
        if (scrollTarget >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == scrollTarget }) {
            listState.scrollToItem(scrollTarget)
            withFrameNanos { }
        }
        listOf(scrollTarget, firstEnabled)
            .filter { it >= 0 }
            .distinct()
            .firstOrNull { index ->
                runCatching { focusRequesters.getOrNull(index)?.requestFocus() }.isSuccess
            }
    }

    val cardModifier = modifier
        .width(menuWidth)
        .semantics { paneTitle = request.paneTitle }
        .focusGroup()
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        }

    OptionMenuSurface(style = style, sources = sources, modifier = cardModifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .heightIn(max = maxHeight)
                .graphicsLayer {
                    // 内容延迟淡入：外框从触发行展开到 30% 时内容开始出现，85% 全显；
                    // 关闭时随 progress 反向自然淡出
                    val reveal = (progress() - SettingsOptionSpec.ContentRevealStart) /
                        (SettingsOptionSpec.ContentRevealEnd - SettingsOptionSpec.ContentRevealStart)
                    alpha = reveal.coerceIn(0f, 1f)
                }
                .selectableGroup(),
            contentPadding = PaddingValues(SettingsOptionSpec.MenuInset),
            verticalArrangement = Arrangement.spacedBy(SettingsOptionSpec.ItemSpacing)
        ) {
            itemsIndexed(request.entries, key = { index, _ -> index }) { index, entry ->
                MenuOptionItem(
                    entry = entry,
                    selected = index == request.selectedIndex,
                    focusRequester = focusRequesters[index],
                    onSelect = onSelect
                )
            }
        }
    }
}

/** 单个菜单项：无 ripple 按压填充、键盘焦点内描边、右侧勾选槽位常驻 */
@Composable
private fun MenuOptionItem(
    entry: MenuEntry,
    selected: Boolean,
    focusRequester: FocusRequester,
    onSelect: (Any?) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val contentAlpha = if (entry.enabled) 1f else 0.38f
    val labelStyle = optionLabelStyle()
    val supportingStyle = optionSupportingStyle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MenuItemShape)
            .background(
                if (pressed) colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
                else Color.Transparent
            )
            .then(
                if (focused) Modifier.border(2.dp, colorScheme.primary, MenuItemShape) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) { }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = entry.enabled,
                role = Role.RadioButton,
                onClick = { onSelect(entry.value) }
            )
            .defaultMinSize(minHeight = SettingsOptionSpec.MenuItemMinHeight)
            .padding(
                horizontal = SettingsOptionSpec.MenuItemHPadding,
                vertical = SettingsOptionSpec.MenuItemVPadding
            ),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = labelStyle,
                    color = colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                entry.supportingText?.let { supporting ->
                    Text(
                        text = supporting,
                        style = supportingStyle,
                        color = colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(SettingsOptionSpec.MenuItemGap))
            // 勾选槽位常驻，选中项切换时文案不抖动；状态由 selectable 语义表达，
            // 图标本身不带 contentDescription，避免读屏重复播报
            Box(
                modifier = Modifier.size(SettingsOptionSpec.MenuItemCheck),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.CHECK),
                        contentDescription = null,
                        tint = colorScheme.primary.copy(alpha = contentAlpha),
                        modifier = Modifier.size(SettingsOptionSpec.MenuItemCheck)
                    )
                }
            }
        }
    }
}

/**
 * 卡片材质：
 * - [ChromeStyle.STANDARD]（或无采样源）→ `surfaceContainerHigh` 实色 + 1dp 描边 + 阴影；
 * - [ChromeStyle.HAZE] → 同层 haze 采样 + 现有 [AdvancedBlurConfig]；
 * - [ChromeStyle.LIQUID_GLASS] → 同层 backdrop 采样，规格取 [LiquidGlassKind.Menu]。
 *
 * 浅色玻璃卡与页面底色（#FAFAFA）几乎同色，靠三件事拉开层次：
 * - 描边：`onSurface @7%` 的柔和轮廓（只比卡面深一档，避免“白卡套灰圈”）；
 * - 叠加：浅色用 5% 黑轻微压暗（而不是叠白），让卡面呈磨砂灰、透出背景纹理；
 * - 阴影：10dp、黑色 8% / 22%。
 * 暗色档维持原参数（暗底上现有描边 + 阴影已足够）。
 *
 * 结构上「玻璃采样层」与「叠加 / 描边」是兄弟层：叠加和描边不再放在玻璃的
 * `clip(shape)` 子树里，1dp 描边不会被圆角裁掉一半。玻璃的 Highlight/Shadow 置 null
 * （kyant Highlight 在真机触发 RenderThread 崩溃，阴影由框架 Modifier.shadow 代替）。
 */
@Composable
private fun OptionMenuSurface(
    style: ChromeStyle,
    sources: ChromeSources?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = isAppDarkTheme()
    val surfaceTint = colorScheme.surfaceContainerHigh
    // 实色卡沿用原来的细描边
    val solidBorderColor = colorScheme.outlineVariant.copy(alpha = if (darkTheme) 0.35f else 0.5f)
    // 玻璃卡：浅色下描边只比卡面深一档（onSurface 7%，约 #E1E1E1），
    // 保证是「同一块玻璃的柔和轮廓」，而不是白卡外面套一圈灰线；暗色仍用 outlineVariant
    val glassBorderColor = if (darkTheme) {
        colorScheme.outlineVariant.copy(alpha = 0.35f)
    } else {
        colorScheme.onSurface.copy(alpha = 0.07f)
    }
    // 浅色玻璃卡与白色页面几乎同色：叠加层用 5% 黑轻微压暗（规范允许的上限附近），
    // 让卡面比页面略灰、像磨砂玻璃；暗色保持 10% 表面色
    val glassReadabilityTint = if (darkTheme) {
        surfaceTint.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.05f)
    }
    val hazeReadabilityTint = surfaceTint.copy(alpha = 0.10f)
    val glassShadowAmbient = Color.Black.copy(alpha = if (darkTheme) 0.06f else 0.08f)
    val glassShadowSpot = Color.Black.copy(alpha = if (darkTheme) 0.12f else 0.22f)
    val backdrop = sources?.backdrop
    val hazeState = sources?.hazeState

    when {
        style == ChromeStyle.LIQUID_GLASS && backdrop != null -> {
            val glassSpec = liquidGlassSpec(LiquidGlassKind.Menu)
            // 分层顺序：阴影 → 玻璃采样 → 可读性叠加 → 描边 → 内容，全部是**兄弟层**。
            // - 阴影单独成层（不再是包住整卡的父层），玻璃的 RenderEffect 不落在带阴影的
            //   图层下面（顶栏玻璃没有阴影父层，模糊正常，这是它与顶栏最大的结构差异）；
            // - 叠加与描边也必须是玻璃的兄弟层：放进玻璃的 clip(shape) 子树，1dp 描边会被
            //   圆角裁掉一半而看不见；
            // - Highlight/Shadow 置 null：kyant 的 lens/Highlight（AGSL RuntimeShader）在
            //   真机大面积卡片上触发 RenderThread SIGSEGV（2026-10-20 试验复现，自绘 rim/
            //   折射带替代方案实测观感差也已放弃），Shadow 由外部框架阴影代替。
            Box(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .shadow(
                            elevation = SettingsOptionSpec.GlassShadowElevation,
                            shape = MenuCardShape,
                            clip = false,
                            ambientColor = glassShadowAmbient,
                            spotColor = glassShadowSpot
                        )
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawLiquidGlass(
                            backdrop = backdrop,
                            shape = { MenuCardShape },
                            spec = glassSpec,
                            highlight = null,
                            shadow = null
                        )
                )
                Box(Modifier.matchParentSize().background(glassReadabilityTint, MenuCardShape))
                Box(Modifier.matchParentSize().border(1.dp, glassBorderColor, MenuCardShape))
                content()
            }
        }

        style == ChromeStyle.HAZE && hazeState != null -> {
            val hazeModifier = advancedHazeEffect(
                hazeState = hazeState,
                config = LocalAdvancedBlurConfig.current,
                shape = MenuCardShape
            )
            // 同样把叠加与描边放在 haze 裁剪层之外，避免描边被裁半
            Box(modifier = modifier) {
                Box(modifier = Modifier.matchParentSize().then(hazeModifier))
                Box(Modifier.matchParentSize().background(hazeReadabilityTint, MenuCardShape))
                Box(Modifier.matchParentSize().border(1.dp, glassBorderColor, MenuCardShape))
                content()
            }
        }

        else -> Box(
            modifier = modifier
                .shadow(SettingsOptionSpec.ShadowElevation, MenuCardShape, clip = false)
                .clip(MenuCardShape)
                .background(surfaceTint)
                .border(1.dp, solidBorderColor, MenuCardShape)
        ) {
            content()
        }
    }
}
