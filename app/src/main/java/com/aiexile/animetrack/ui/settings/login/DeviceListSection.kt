package com.aiexile.animetrack.ui.settings.login

import androidx.compose.runtime.Composable
import com.aiexile.animetrack.data.network.DeviceSession
import com.aiexile.animetrack.ui.settings.DeviceManageDialog
import com.aiexile.animetrack.ui.settings.DevicePickerDialog

/**
 * 多端登录设备区：登录超限时的设备选择弹窗，以及已登录后的设备管理弹窗。
 */
@Composable
internal fun DevicePickerSection(
    visible: Boolean,
    devices: List<DeviceSession>,
    onConfirm: (kickDeviceIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // 登录超限：选择要下线的设备后重试登录
    if (visible) {
        DevicePickerDialog(
            devices = devices,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun DeviceManageSection(
    visible: Boolean,
    devices: List<DeviceSession>,
    revokingSessionId: String?,
    onRevoke: (device: DeviceSession) -> Unit,
    onDismiss: () -> Unit
) {
    // 已登录：登录设备管理（查看当前设备并下线）
    if (visible) {
        DeviceManageDialog(
            devices = devices,
            revokingSessionId = revokingSessionId,
            onRevoke = onRevoke,
            onDismiss = onDismiss
        )
    }
}
