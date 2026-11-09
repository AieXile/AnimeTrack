package com.aiexile.animetrack.ui.feedback.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.ShimmerText
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.dialogBlurBehind
import com.aiexile.animetrack.ui.feedback.formatFeedbackTime

/**
 * 管理员会话详情顶部的 Agendula 推送状态条：
 * 未发送时显示隐私提示，发送中显示进度，成功后显示已发送时间。
 * 已发送状态下按钮仍可点击，由确认弹窗说明「不会重复创建任务」。
 */
@Composable
internal fun AdminAgendulaForwardBar(
    pushedAt: String?,
    isForwarding: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alreadyPushed = !pushedAt.isNullOrBlank()

    Row(
        modifier = modifier
            .clip(SquircleShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.admin_feedback_forward_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isForwarding) {
                ShimmerText(
                    text = stringResource(R.string.admin_feedback_forwarding),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = if (alreadyPushed) {
                        stringResource(R.string.admin_feedback_forward_sent_at, formatFeedbackTime(pushedAt))
                    } else {
                        stringResource(R.string.admin_feedback_forward_hint)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        TextButton(onClick = onSend, enabled = !isForwarding) {
            if (isForwarding) {
                ShimmerText(
                    text = stringResource(R.string.admin_feedback_forwarding),
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = if (alreadyPushed) {
                        stringResource(R.string.admin_feedback_forward_sent)
                    } else {
                        stringResource(R.string.admin_feedback_forward_send)
                    },
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** 推送前的二次确认弹窗：已发送时明确说明不会重复创建任务 */
@Composable
internal fun AdminForwardConfirmDialog(
    alreadyPushed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogBlurBehind(),
        title = { Text(stringResource(R.string.admin_feedback_forward_confirm_title)) },
        text = {
            Text(
                text = stringResource(
                    if (alreadyPushed) R.string.admin_feedback_forward_confirm_already
                    else R.string.admin_feedback_forward_confirm_message
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.admin_feedback_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.admin_feedback_cancel))
            }
        }
    )
}
