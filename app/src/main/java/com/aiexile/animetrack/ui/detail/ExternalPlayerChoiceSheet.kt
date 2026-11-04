package com.aiexile.animetrack.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.launch

/**
 * 外部播放应用选择底部弹层。
 *
 * 播放入口位于详情页右下角（滚到底后形变为状态卡下方的卡片），点击后从屏幕底部
 * 弹出选择器，与点击位置一致；不再使用居中 AlertDialog。
 *
 * 只在歧义场景出现（自动模式下两个应用都安装且没有 B 站关联、或用户选择「每次询问」）；
 * 未安装的条目仍可点击，由调用方走「提示未安装 + 打开下载页」的降级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPlayerChoiceSheet(
    installed: Set<ExternalPlayerId>,
    onSelect: (ExternalPlayerId) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // 收起动画期间屏蔽重复点击，避免连点两次打开两个播放器
    val isDismissing = remember { mutableStateOf(false) }

    // 先播放下滑收起再执行回调：选择播放应用时弹层不会瞬间消失
    fun dismissThen(action: () -> Unit) {
        if (isDismissing.value) return
        isDismissing.value = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        // 遮罩/返回键由 Material3 先收起弹层再回调 onDismissRequest
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.external_player_choose_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            PlayerChoiceRow(
                title = "Animeko",
                subtitle = if (installed.contains(ExternalPlayerId.ANIMEKO)) {
                    stringResource(R.string.external_player_watch_in_animeko)
                } else {
                    stringResource(R.string.external_player_not_installed_action)
                },
                onClick = { dismissThen { onSelect(ExternalPlayerId.ANIMEKO) } }
            )

            Spacer(modifier = Modifier.height(4.dp))

            PlayerChoiceRow(
                title = stringResource(R.string.external_player_bilibili),
                subtitle = if (installed.contains(ExternalPlayerId.BILIBILI)) {
                    stringResource(R.string.external_player_watch_in_bilibili)
                } else {
                    stringResource(R.string.external_player_not_installed_action)
                },
                onClick = { dismissThen { onSelect(ExternalPlayerId.BILIBILI) } }
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { dismissThen(onDismiss) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    }
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
