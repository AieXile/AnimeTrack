package com.aiexile.animetrack.ui.player

import androidx.compose.animation.AnimatedVisibility
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
 * 点击面板外任意处关闭。
 *
 * [stepBack] 非空表示当前处于二级界面：点击面板外只回退到一级，抽屉本身保持挂载；
 * 一级界面传 null，点击外部才播退场动画并关闭。
 *
 * 内容 lambda 的参数为关闭回调：二级界面为「回退一级」，一级界面为「关闭抽屉」。
 */
@Composable
internal fun SideDrawerPanel(
    onDismissed: () -> Unit,
    stepBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.(requestClose: () -> Unit) -> Unit
) {
    val (close, visible) = rememberPanelCloseController(onDismissed)
    val dismissRequest: () -> Unit = { stepBack?.invoke() ?: close() }

    // 面板宽度：横屏约 1/2 屏；竖屏按 300dp 下限兜底（保证竖屏也有足够宽度）
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val panelWidth = maxOf(screenWidthDp * 0.5f, 300.dp).coerceAtMost(screenWidthDp)

    Box(modifier = Modifier.fillMaxSize()) {
        PanelScrimLayer(visible = visible, onClose = dismissRequest)

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
                    ) { dismissRequest() }
                    .padding(start = DrawerContentStartPadding, end = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                content(dismissRequest)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 字幕/音轨选择内容（自建 Compose 实现，由 [PlayerDrawerHost] 套抽屉框）。
 *
 * 不使用 media3 的 TrackSelectionDialogBuilder：它要求 Context 为 FragmentActivity，
 * 本项目 MainActivity 是 ComponentActivity，调用会直接 ClassCastException 闪退。
 */
@Composable
internal fun ColumnScope.TrackSelectionContent(
    trackType: Int,
    player: Player,
    requestClose: () -> Unit
) {
    // 打开时快照轨道组（模态短生命周期，无需持续订阅）
    val groups = remember(trackType) {
        player.currentTracks.groups.filter { it.type == trackType }
    }

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

/**
 * 本地媒体选集内容：只列出本地队列中的正片；点击即切换播放项。
 */
@Composable
internal fun ColumnScope.EpisodeSelectionContent(
    episodes: List<PlayerEpisode>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    progress: Map<Int, com.aiexile.animetrack.model.PlaybackProgress> = emptyMap()
) {
    val labels = episodes.map { episode ->
        val record = progress[episode.index]
        val suffix = when {
            record?.completed == true -> stringResource(R.string.web_watch_completed)
            record != null && record.positionMs > 0 -> stringResource(R.string.web_watch_position,
                formatDuration(record.positionMs))
            else -> null
        }
        listOfNotNull(episode.label, suffix).joinToString(" · ")
    }
    PlayerSelectionContent(labels, episodes.indexOfFirst { it.index == selectedIndex }, onSelect)
}

/** 复用同一抽屉选择线路、剧集和倍速；长列表仅组合可见项。 */
@Composable
internal fun ColumnScope.PlayerSelectionContent(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    LazyColumn(Modifier.weight(1f), state = state) {
        itemsIndexed(labels) { index, label ->
            TrackPanelItem(label, index == selectedIndex) { onSelect(index) }
        }
    }
}

/** 面板内的单个轨道条目：选中项白色高亮加勾，未选中半透明白 */@Composable
internal fun TrackPanelItem(
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
                painter = rememberAppIconPainter(AppIcon.CHECK),
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

/** 更多面板中的常用功能卡片，使用紧凑网格减少层级和点击距离。 */
@Composable
fun PlayerMenuGridItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(60.dp)
            .clip(SquircleShape(12.dp))
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.10f),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 更多面板的分组标题。 */
@Composable
fun PlayerMenuSectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
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
