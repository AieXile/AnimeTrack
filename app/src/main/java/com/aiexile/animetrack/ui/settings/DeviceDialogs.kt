package com.aiexile.animetrack.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.DeviceSession
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 多端登录设备管理弹窗：
 * - [DevicePickerDialog]：登录超限时选择要下线的设备（登录/绑定邮箱流程共用）
 * - [DeviceManageDialog]：已登录状态查看当前登录设备并下线
 */

/** 服务端时间（"YYYY-MM-DD HH:mm"）→ "MM-dd HH:mm"，解析失败返回 null */
private fun formatDeviceTime(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val normalized = raw.take(19).replace(' ', 'T')
        LocalDateTime.parse(normalized).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }.getOrNull()
}

/** 平台显示标签（手机端 / 网页端） */
@Composable
private fun platformLabel(platform: String?): String = when (platform) {
    "web" -> stringResource(R.string.device_platform_web)
    else -> stringResource(R.string.device_platform_mobile)
}

/**
 * 设备行信息列：名称（单行省略，超长不挤压右侧按钮）+ 平台与最近登录时间小字。
 * 右侧操作区由调用方放置（下线按钮 / 勾选框），保证始终可见。
 */
@Composable
private fun DeviceInfoColumn(device: DeviceSession, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = device.deviceName ?: stringResource(R.string.device_manage_unknown_device),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (device.isCurrent) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = SquircleShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.device_manage_current_device),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        // 平台 · 最近登录时间（设备上线 App 的时间）
        val platform = platformLabel(device.platform)
        val lastUsed = formatDeviceTime(device.lastUsedAt)
        val lastActiveText = lastUsed?.let {
            stringResource(R.string.device_manage_last_active, it)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (lastActiveText != null) "$platform · $lastActiveText" else platform,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 登录超限设备选择弹窗：列出当前同平台在线设备，用户勾选后确认下线并继续登录。
 */
@Composable
fun DevicePickerDialog(
    devices: List<DeviceSession>,
    onConfirm: (kickDeviceIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SquircleShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.device_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.device_picker_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .height(240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    devices.forEach { device ->
                        val checked = device.sessionId in selectedIds.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds.value =
                                        if (checked) selectedIds.value - device.sessionId
                                        else selectedIds.value + device.sessionId
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = rememberAppIconPainter(AppIcon.DEVICES),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            DeviceInfoColumn(
                                device = device,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painter = rememberAppIconPainter(
                                    if (checked) AppIcon.CHECK_BOX else AppIcon.CHECK_BOX_OUTLINE_BLANK
                                ),
                                contentDescription = null,
                                tint = if (checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.value.toList()) },
                enabled = selectedIds.value.isNotEmpty()
            ) {
                Text(stringResource(R.string.device_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 已登录设备管理弹窗：查看全部在线设备（本机带标记），可下线其他设备。
 */
@Composable
fun DeviceManageDialog(
    devices: List<DeviceSession>,
    revokingSessionId: String?,
    onRevoke: (device: DeviceSession) -> Unit,
    onDismiss: () -> Unit
) {
    // 当前处于展开态（露出删除块）的设备，同一时刻只允许一个
    var revealedSessionId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SquircleShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.device_manage_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.device_manage_limit_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.device_manage_swipe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .height(320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    devices.forEach { device ->
                        key(device.sessionId) {
                            if (device.isCurrent) {
                                DeviceRowContent(
                                    device = device,
                                    showRevoking = false
                                )
                            } else {
                                SwipeToRevealRow(
                                    revealed = revealedSessionId == device.sessionId,
                                    onRevealChange = { revealed ->
                                        revealedSessionId = when {
                                            revealed -> device.sessionId
                                            revealedSessionId == device.sessionId -> null
                                            else -> revealedSessionId
                                        }
                                    },
                                    onAction = { onRevoke(device) }
                                ) {
                                    DeviceRowContent(
                                        device = device,
                                        showRevoking = revokingSessionId == device.sessionId
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

/**
 * 设备行内容：图标 + 名称/平台/最近登录时间（信息列独占剩余宽度）+ 下线中 loading。
 */
@Composable
private fun DeviceRowContent(
    device: DeviceSession,
    showRevoking: Boolean
) {
    Surface(
        shape = SquircleShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.DEVICES),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            DeviceInfoColumn(
                device = device,
                modifier = Modifier.weight(1f)
            )
            if (showRevoking) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

/**
 * 微信式左滑操作行：左滑露出右侧红色删除块，松手后展开态保留；
 * 点击删除块才执行 [onAction]，点击前景或右滑收回。
 * 背景红色块与行同高同宽（matchParentSize），前景整体平移，行宽始终一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeToRevealRow(
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    revealWidth: Dp = 64.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val revealPx = with(density) { revealWidth.toPx() }
    // 锚点：0 = 收起，1 = 展开（前景左移 revealPx）
    val state = remember {
        AnchoredDraggableState(
            initialValue = 0,
            anchors = DraggableAnchors {
                0 at 0f
                1 at -revealPx
            },
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            // 松手吸附带轻微回弹，展开/收起更有手感
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            decayAnimationSpec = exponentialDecay()
        )
    }
    // 外部要求展开/收起（例如另一行展开时收回本行）
    val snapSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }
    LaunchedEffect(revealed) {
        val target = if (revealed) 1 else 0
        if (state.settledValue != target) {
            state.animateTo(target, snapSpec)
        }
    }
    // 本行拖动 settle 后同步外部状态，并在展开/收起时给出轻微触觉反馈
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(state.settledValue) {
        if (state.settledValue == 1 && !revealed) {
            onRevealChange(true)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (state.settledValue == 0 && revealed) {
            onRevealChange(false)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .anchoredDraggable(state, Orientation.Horizontal)
    ) {
        // 背景操作层：整体深红底（四周缩 2dp 避免与前景边缘抗锯齿重叠透出描边），
        // 前景滑开后红色连片延伸而非独立按钮；点击露出的红色区域即触发下线
        Surface(
            shape = SquircleShape(14.dp),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .matchParentSize()
                .padding(2.dp)
                .clickable { onAction() }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                // 删除图标随展开进度淡入放大、收起时缩小消失；偏移读取放在 graphicsLayer 内随帧更新
                Icon(
                    painter = rememberAppIconPainter(AppIcon.DELETE),
                    contentDescription = stringResource(R.string.device_manage_revoke),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .padding(end = revealWidth / 2 - 11.dp)
                        .size(22.dp)
                        .graphicsLayer {
                            val progress = (-state.requireOffset() / revealPx).coerceIn(0f, 1f)
                            alpha = progress
                            val scale = 0.6f + 0.4f * progress
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
        }
        // 前景内容：随拖动整体平移
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
        ) {
            content()
        }
    }
}
