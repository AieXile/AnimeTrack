package com.aiexile.animetrack.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayerId
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.dialogBlurBehind

/**
 * 外部播放应用选择器。
 *
 * 只在歧义场景出现（自动模式下两个应用都安装且没有 B 站关联、或用户选择「每次询问」）；
 * 未安装的条目仍可点击，由调用方走「提示未安装 + 打开下载页」的降级。
 */
@Composable
fun ExternalPlayerChoiceDialog(
    installed: Set<ExternalPlayerId>,
    onSelect: (ExternalPlayerId) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogBlurBehind(),
        shape = SquircleShape(24.dp),
        title = { Text(stringResource(R.string.external_player_choose_title)) },
        text = {
            Column {
                PlayerChoiceRow(
                    title = "Animeko",
                    subtitle = if (installed.contains(ExternalPlayerId.ANIMEKO)) {
                        stringResource(R.string.external_player_watch_in_animeko)
                    } else {
                        stringResource(R.string.external_player_not_installed_action)
                    },
                    onClick = { onSelect(ExternalPlayerId.ANIMEKO) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                PlayerChoiceRow(
                    title = stringResource(R.string.external_player_bilibili),
                    subtitle = if (installed.contains(ExternalPlayerId.BILIBILI)) {
                        stringResource(R.string.external_player_watch_in_bilibili)
                    } else {
                        stringResource(R.string.external_player_not_installed_action)
                    },
                    onClick = { onSelect(ExternalPlayerId.BILIBILI) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun PlayerChoiceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
