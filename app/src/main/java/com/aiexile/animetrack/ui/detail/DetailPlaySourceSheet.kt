package com.aiexile.animetrack.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlinx.coroutines.launch

/** 详情页播放来源。 */
enum class DetailPlaySource { LOCAL, EXTERNAL, WEB }

/**
 * 详情页播放来源选择底部弹层。
 *
 * 入口与「外部播放」共用同一块 FAB/卡片表面；有多个来源可用时先选来源，
 * 只有一种来源时由调用方直接执行，不弹层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPlaySourceSheet(
    hasLocal: Boolean,
    hasExternal: Boolean,
    hasWeb: Boolean,
    localEpisodeLabel: String?,
    externalEpisodeLabel: String?,
    /** 该番剧的网页续播集数；为空表示只提供网页播放入口 */
    webEpisodeLabel: String?,
    onSelect: (DetailPlaySource) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val isDismissing = remember { mutableStateOf(false) }

    fun dismissThen(action: () -> Unit) {
        if (isDismissing.value) return
        isDismissing.value = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
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
                text = stringResource(R.string.detail_play_source_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (hasLocal) {
                PlaySourceRow(
                    icon = AppIcon.INSERT_DRIVE_FILE,
                    title = stringResource(R.string.detail_play_local),
                    subtitle = localEpisodeLabel,
                    onClick = { dismissThen { onSelect(DetailPlaySource.LOCAL) } }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (hasExternal) {
                PlaySourceRow(
                    icon = AppIcon.PLAY_CIRCLE,
                    title = stringResource(R.string.external_player_watch),
                    subtitle = externalEpisodeLabel,
                    onClick = { dismissThen { onSelect(DetailPlaySource.EXTERNAL) } }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (hasWeb) {
                PlaySourceRow(
                    icon = AppIcon.LINK,
                    title = stringResource(R.string.web_playback_title),
                    subtitle = webEpisodeLabel ?: stringResource(R.string.detail_play_web_subtitle),
                    onClick = { dismissThen { onSelect(DetailPlaySource.WEB) } }
                )
            }

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
private fun PlaySourceRow(
    icon: AppIcon,
    title: String,
    subtitle: String?,
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = rememberAppIconPainter(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
