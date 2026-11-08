package com.aiexile.animetrack.ui.settings.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 已登录账号操作区：账号安全列表（设备管理 / 修改密码 / 更换邮箱）、登出与注销账号入口。
 */
@Composable
internal fun AccountActionsSection(
    deviceCount: Int?,
    isLoggingOut: Boolean,
    onManageDevices: () -> Unit,
    onChangePassword: () -> Unit,
    onChangeEmail: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    // 账号安全：列表行
    Spacer(modifier = Modifier.height(16.dp))
    Surface(
        shape = SquircleShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            SecurityRow(
                icon = { Icon(rememberAppIconPainter(AppIcon.DEVICES), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) },
                label = stringResource(R.string.device_manage_title),
                trailing = deviceCount?.let { stringResource(R.string.device_manage_count_format, it) },
                onClick = onManageDevices
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 52.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SecurityRow(
                icon = { Icon(rememberAppIconPainter(AppIcon.LOCK), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) },
                label = stringResource(R.string.user_login_change_password),
                onClick = onChangePassword
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 52.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SecurityRow(
                icon = { Icon(rememberAppIconPainter(AppIcon.MAIL), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) },
                label = stringResource(R.string.user_login_change_email),
                onClick = onChangeEmail
            )
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedButton(
        onClick = onLogout,
        enabled = !isLoggingOut,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoggingOut) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(stringResource(R.string.user_login_logout))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    // 注销账号入口（危险操作，红色弱化展示）
    TextButton(
        onClick = onDeleteAccount,
        enabled = !isLoggingOut
    ) {
        Text(
            text = stringResource(R.string.user_login_delete_account),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
}

/** 账号安全列表行（图标 + 标题 + 可选右侧文本 + 右箭头） */
@Composable
private fun SecurityRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            painter = rememberAppIconPainter(AppIcon.KEYBOARD_ARROW_RIGHT),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
