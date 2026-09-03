package com.aiexile.animetrack.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.navigation.SIDE_PANEL_ANIM_DURATION
import com.aiexile.animetrack.ui.navigation.sidePanelEnter
import com.aiexile.animetrack.ui.navigation.sidePanelExit
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource

/** 播放器浮层面板通用底色：半透明黑 */
internal val PlayerPanelScrim = Color.Black.copy(alpha = 0.72f)

/** 面板左缘渐隐宽度：从透明平滑过渡到实色，消除生硬的直边界 */
private val PanelEdgeFadeWidth = 28.dp

/** 抽屉内容起始缩进：必须大于左缘渐隐宽度，避免文字压在模糊渐变区上 */
private val DrawerContentStartPadding = PanelEdgeFadeWidth + 16.dp

/**
 * 面板背景修饰符：整块半透明黑 + 左缘 [PanelEdgeFadeWidth] 内由透明渐入实色。
 * 字幕/音轨抽屉与右上角菜单共用同一视觉语言。
 */
internal fun Modifier.playerPanelBackground(): Modifier = drawBehind {
    val fadePx = PanelEdgeFadeWidth.toPx()
    val endStop = if (size.width > 0f) (fadePx / size.width).coerceAtMost(1f) else 1f
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            endStop to PlayerPanelScrim,
            1f to PlayerPanelScrim
        )
    )
}

/**
 * 播放器浮层面板通用的进出场关闭逻辑：请求关闭时先播退场动画，
 * 结束后才回调 [onDismissed] 移除组合。
 */
@Composable
private fun rememberPanelCloseController(onDismissed: () -> Unit): Pair<() -> Unit, Boolean> {
    var visible by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            visible = false
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(SIDE_PANEL_ANIM_DURATION + 60L)
            onDismissed()
        }
    }
    return close to visible
}

/** 遮罩层：点击空白处关闭（淡入淡出），无涟漪 */
@Composable
private fun PanelScrimLayer(visible: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClose() }
        )
    }
}

/**
 * 播放器右侧抽屉面板容器：约 1/2 屏宽、全高，半透明黑底 + 左缘渐隐 + 右侧滑入，
 * 点击面板外任意处关闭。字幕/音轨选择与右上角「更多」菜单共用同一视觉语言。
 *
 * 内容起始缩进大于左缘渐隐宽度，保证条目文字不压在模糊渐变区上。
 * 内容 lambda 的参数为「带退场动画的关闭回调」，条目点击后调用它可平滑收起面板。
 */
@Composable
private fun SideDrawerPanel(
    title: String,
    onDismissed: () -> Unit,
    content: @Composable ColumnScope.(requestClose: () -> Unit) -> Unit
) {
    val (close, visible) = rememberPanelCloseController(onDismissed)

    // 面板宽度：横屏约 1/2 屏；竖屏按 300dp 下限兜底（保证竖屏也有足够宽度）
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val panelWidth = maxOf(screenWidthDp * 0.5f, 300.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        PanelScrimLayer(visible = visible, onClose = close)

        // 右侧半透明黑色面板，从右滑入/向右滑出（动画规格统一在 TransitionSpecs.kt）
        AnimatedVisibility(
            visible = visible,
            enter = sidePanelEnter(),
            exit = sidePanelExit(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .playerPanelBackground()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { close() }
                    .padding(start = DrawerContentStartPadding, end = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                content(close)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 字幕/音轨选择面板（右侧半透明抽屉，自建 Compose 实现）。
 *
 * 不使用 media3 的 TrackSelectionDialogBuilder：它要求 Context 为 FragmentActivity，
 * 本项目 MainActivity 是 ComponentActivity，调用会直接 ClassCastException 闪退。
 */
@Composable
fun TrackSelectionPanel(
    trackType: Int,
    player: Player,
    onDismiss: () -> Unit
) {
    // 打开时快照轨道组（模态短生命周期，无需持续订阅）
    val groups = remember(trackType) {
        player.currentTracks.groups.filter { it.type == trackType }
    }

    SideDrawerPanel(
        title = stringResource(
            if (trackType == C.TRACK_TYPE_TEXT) R.string.player_subtitles
            else R.string.player_audio_track
        ),
        onDismissed = onDismiss
    ) { requestClose ->
        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.player_track_none_available),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 20.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 字幕轨提供「关闭」选项（与 media3 官方弹窗行为一致）
                if (trackType == C.TRACK_TYPE_TEXT) {
                    val disabled =
                        player.trackSelectionParameters.disabledTrackTypes.contains(trackType)
                    TrackPanelItem(
                        label = stringResource(R.string.player_track_disable),
                        selected = disabled,
                        onClick = {
                            player.trackSelectionParameters =
                                player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(trackType, true)
                                    .build()
                            requestClose()
                        }
                    )
                }

                groups.forEach { group ->
                    repeat(group.length) { trackIndex ->
                        val selected = group.isTrackSelected(trackIndex)
                        val label = trackLabel(group, trackIndex, trackIndex + 1)
                        TrackPanelItem(
                            label = label,
                            selected = selected,
                            onClick = {
                                player.trackSelectionParameters =
                                    player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(trackType, false)
                                        .setOverrideForType(
                                            TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                listOf(trackIndex)
                                            )
                                        )
                                        .build()
                                requestClose()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 面板内的单个轨道条目：选中项白色高亮加勾，未选中半透明白 */
@Composable
private fun TrackPanelItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.sym_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 轨道显示名：优先 format.label，其次语言码，最后序号兜底 */
private fun trackLabel(group: Tracks.Group, trackIndex: Int, fallbackIndex: Int): String {
    val format = group.getTrackFormat(trackIndex)
    return format.label?.takeIf { it.isNotBlank() }
        ?: format.language?.takeIf { it.isNotBlank() }
        ?: "#${fallbackIndex}"
}

/**
 * 播放器右上角「更多」菜单面板：与字幕/音轨抽屉同一套视觉语言——
 * 右侧约 1/2 屏宽的全高抽屉（半透明黑底 + 左缘渐隐 + 右侧滑入），
 * 打开期间由调用方联动隐藏播放控制条，点击面板外任意处关闭。
 */
@Composable
fun PlayerMenuPanel(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(requestClose: () -> Unit) -> Unit
) {
    SideDrawerPanel(
        title = stringResource(R.string.player_menu_more),
        onDismissed = onDismiss
    ) { requestClose ->
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            content(requestClose)
        }
    }
}

/** 菜单面板的单个条目：图标 + 文字，风格对齐字幕抽屉条目 */
@Composable
fun PlayerMenuItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 菜单面板的单个开关条目：图标 + 文字 + Switch。
 * 整行响应点击切换；Switch 自身不再处理点击，避免双触发。
 */
@Composable
fun PlayerMenuItemToggle(
    icon: Painter,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}
