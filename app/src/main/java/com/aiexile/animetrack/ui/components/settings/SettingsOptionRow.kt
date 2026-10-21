package com.aiexile.animetrack.ui.components.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlin.math.roundToInt

/**
 * 设置项「一行一个当前值」行：标题 / 副标题 / 前置图标 / 当前值 + 上下双箭头。
 *
 * 点击整行在右侧「当前值」位置展开 [SettingsOptionMenu]（由页面外层的
 * [SettingsOptionHost] 承载）。组件不读 DataStore、不写库、不感知设置搜索高亮——
 * 页面照常把 `rememberHighlightModifier(...)` 传给 [modifier] 即可。
 *
 * - 候选项 < 2 或 [enabled] = false 时按静态行渲染：不显示箭头、不可点；
 * - [selected] 不在 [options] 里时行内回退显示 `selected.toString()`，菜单无勾选；
 * - 选中回调恰好一次，关闭动画期间不重复触发。
 *
 * @param verticalPadding 行内上下留白；默认对齐 SettingsGroup 内既有行距（8dp），
 *   独立布局的页面（如播放器设置为 14dp）可按自己的节奏覆盖
 */
@Composable
fun <T> SettingsOptionRow(
    title: String,
    options: List<SettingsOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: Painter? = null,
    enabled: Boolean = true,
    verticalPadding: Dp = 8.dp,
) {
    val host = LocalSettingsOptionHost.current
    val owner = remember { Any() }
    val anchorHolder = remember { AnchorHolder() }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    val selectedIndex = options.indexOfFirst { it.value == selected }
    val selectedLabel = options.getOrNull(selectedIndex)?.label ?: selected.toString()
    // 候选项 ≥ 2 才显示展开箭头；是否可点还要看 enabled
    val hasOptions = options.size >= 2
    val canOpen = enabled && hasOptions

    val colorScheme = MaterialTheme.colorScheme
    val contentAlpha = if (enabled) 1f else 0.38f
    val expandLabel = stringResource(R.string.settings_option_expand)
    val currentLabel = stringResource(R.string.settings_option_current, selectedLabel)
    val optionsKey = options.map { it.value }

    // 选项 / 选中值被外部改变（DataStore 回流、其他入口修改）→ 关闭菜单，避免显示旧数据
    LaunchedEffect(selected, optionsKey) {
        host?.onDataChanged(owner, selected, optionsKey)
    }
    // 列表项被回收 / 行离开组合 → 锚点失效，关闭菜单
    DisposableEffect(Unit) {
        onDispose { host?.onRowDisposed(owner) }
    }

    val clickModifier = if (canOpen) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClickLabel = expandLabel,
            role = Role.Button,
            onClick = {
                val anchor = anchorHolder.anchor
                if (anchor.width > 0 && anchor.height > 0) {
                    host?.show(
                        owner = owner,
                        anchor = anchor,
                        paneTitle = title,
                        options = options,
                        selected = selected,
                        onSelected = onSelected,
                        onFocusReturn = { runCatching { focusRequester.requestFocus() } }
                    )
                }
            }
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .then(clickModifier)
            .defaultMinSize(minHeight = SettingsOptionSpec.RowMinHeight)
            .padding(vertical = verticalPadding)
            .semantics(mergeDescendants = true) { stateDescription = currentLabel },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(
                painter = leading,
                contentDescription = null,
                tint = colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(SettingsOptionSpec.LeadingIcon)
            )
            Spacer(modifier = Modifier.width(SettingsOptionSpec.LeadingGap))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = optionRowTitleStyle(),
                color = colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = optionRowSubtitleStyle(),
                    color = colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 锚点 = 当前值 + chevron 的合并边界（不是整行），菜单从这里长出来。
        // 值文本不进入无障碍树，当前值由整行的 stateDescription 播报，避免重复朗读
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clearAndSetSemantics { }
                .onGloballyPositioned { coordinates ->
                    if (!hasOptions) return@onGloballyPositioned
                    val bounds = coordinates.boundsInWindow()
                    val rect = IntRect(
                        left = bounds.left.roundToInt(),
                        top = bounds.top.roundToInt(),
                        right = bounds.right.roundToInt(),
                        bottom = bounds.bottom.roundToInt()
                    )
                    if (rect != anchorHolder.anchor) {
                        anchorHolder.anchor = rect
                        host?.onAnchorMoved(owner, rect)
                    }
                }
        ) {
            Crossfade(
                targetState = selectedLabel,
                animationSpec = tween(durationMillis = 160),
                label = "optionValueCrossfade"
            ) { label ->
                Text(
                    text = label,
                    style = optionValueStyle(),
                    color = colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = SettingsOptionSpec.ValueMaxWidth)
                )
            }
            if (hasOptions) {
                Spacer(modifier = Modifier.width(SettingsOptionSpec.ValueGap))
                // 上下双箭头：同时表达「可展开」与「可收起」，比单向下箭头更准确；
                // 图标本身对称，不需要随开合旋转
                Icon(
                    painter = rememberAppIconPainter(AppIcon.UNFOLD_MORE),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.size(SettingsOptionSpec.ValueChevron)
                )
            }
        }
    }
}

/** 锚点持有者：只在布局阶段读写，不参与重组 */
private class AnchorHolder {
    var anchor: IntRect = IntRect.Zero
}
