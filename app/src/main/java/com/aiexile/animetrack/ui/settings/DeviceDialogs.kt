package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.DeviceSession
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .height(320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    devices.forEach { device ->
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
                                if (!device.isCurrent) {
                                    if (revokingSessionId == device.sessionId) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        TextButton(onClick = { onRevoke(device) }) {
                                            Text(
                                                text = stringResource(R.string.device_manage_revoke),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
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
