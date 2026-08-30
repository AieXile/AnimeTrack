package com.aiexile.animetrack.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.FEEDBACK_ATTACHMENT_TYPE_IMAGE
import com.aiexile.animetrack.data.network.FEEDBACK_ROLE_ASSISTANT
import com.aiexile.animetrack.data.network.FEEDBACK_ROLE_SYSTEM
import com.aiexile.animetrack.data.network.FEEDBACK_ROLE_USER
import com.aiexile.animetrack.data.network.FeedbackMessageAttachment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 聊天界面消息 UI 模型：本地消息 id 为负数，服务端消息用服务端 id */
data class FeedbackChatMessage(
    val id: Long,
    val role: String,
    val content: String,
    /** 消息附件（服务端消息；本地乐观消息为空） */
    val attachments: List<FeedbackMessageAttachment> = emptyList(),
    /** ISO 8601 时间，本地消息可为 null */
    val createdAt: String? = null
)

/** 发送失败类型（UI 层映射为文案） */
sealed class FeedbackSendError {
    data class RateLimited(val retryAfterSeconds: Int) : FeedbackSendError()
    data object HourlyLimit : FeedbackSendError()
    data object Sensitive : FeedbackSendError()
    data object SessionClosed : FeedbackSendError()
    data object SessionNotFound : FeedbackSendError()
    data object AttachmentFailed : FeedbackSendError()
    /** 日志导出失败：反馈不带日志继续发送（降级提示，非发送失败） */
    data object LogGenerateFailed : FeedbackSendError()
    data object AuthExpired : FeedbackSendError()
    data object Network : FeedbackSendError()
    data object Empty : FeedbackSendError()
    data object TooLong : FeedbackSendError()
}

/** ISO 8601 → 展示时间：今天显示 HH:mm，更早显示 yyyy-MM-dd（解析失败原样返回） */
fun formatFeedbackTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(raw)
        val local = instant.atZone(ZoneId.systemDefault())
        if (local.toLocalDate() == LocalDate.now()) {
            local.format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
    } catch (_: Exception) {
        raw
    }
}

/** 反馈消息气泡：用户消息靠右主色底，助手回复靠左容器底，系统消息居中灰字 */
@Composable
fun FeedbackMessageBubble(message: FeedbackChatMessage, modifier: Modifier = Modifier) {
    FeedbackMessageBubbleContent(message, modifier)
}

@Composable
private fun FeedbackMessageBubbleContent(message: FeedbackChatMessage, modifier: Modifier = Modifier) {
    when (message.role) {
        FEEDBACK_ROLE_USER -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            MessageBubble(
                content = message.content,
                attachments = message.attachments,
                time = formatFeedbackTime(message.createdAt),
                containerColor = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.onPrimary,
                timeColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                attachmentChipColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                attachmentChipTextColor = MaterialTheme.colorScheme.onPrimary,
                tailEnd = true
            )
        }
        FEEDBACK_ROLE_ASSISTANT -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            MessageBubble(
                content = message.content,
                attachments = message.attachments,
                time = formatFeedbackTime(message.createdAt),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                textColor = MaterialTheme.colorScheme.onSurface,
                timeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                attachmentChipColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                attachmentChipTextColor = MaterialTheme.colorScheme.onSurface,
                tailEnd = false
            )
        }
        else -> Text(
            text = message.content.ifBlank { stringResource(R.string.feedback_received) },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MessageBubble(
    content: String,
    attachments: List<FeedbackMessageAttachment>,
    time: String,
    containerColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    timeColor: androidx.compose.ui.graphics.Color,
    attachmentChipColor: androidx.compose.ui.graphics.Color,
    attachmentChipTextColor: androidx.compose.ui.graphics.Color,
    tailEnd: Boolean
) {
    Column(
        modifier = Modifier.widthIn(max = 280.dp),
        horizontalAlignment = if (tailEnd) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .clip(SquircleShape(20.dp))
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // 附件区：图片缩略图（点击跳转浏览器查看原图）/ 文件 chip
            attachments.forEach { attachment ->
                MessageAttachmentView(
                    attachment = attachment,
                    chipColor = attachmentChipColor,
                    chipTextColor = attachmentChipTextColor
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (content.isNotBlank()) {
                Text(
                    text = content,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = textColor
                )
            }
        }
        if (time.isNotBlank()) {
            Text(
                text = time,
                fontSize = 11.sp,
                color = timeColor,
                modifier = Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp)
            )
        }
    }
}

/** 消息内附件：image 显示缩略图（点击用浏览器打开原图），其余显示文件名 chip */
@Composable
private fun MessageAttachmentView(
    attachment: FeedbackMessageAttachment,
    chipColor: androidx.compose.ui.graphics.Color,
    chipTextColor: androidx.compose.ui.graphics.Color
) {
    val uriHandler = LocalUriHandler.current
    if (attachment.type == FEEDBACK_ATTACHMENT_TYPE_IMAGE && !attachment.url.isNullOrBlank()) {
        AsyncImage(
            model = attachment.url,
            contentDescription = attachment.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(180.dp)
                .clip(SquircleShape(14.dp))
                .clickable { attachment.url?.let { uriHandler.openUri(it) } }
        )
    } else {
        Row(
            modifier = Modifier
                .clip(SquircleShape(10.dp))
                .background(chipColor)
                .clickable { attachment.url?.let { uriHandler.openUri(it) } }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = chipTextColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = attachment.fileName ?: stringResource(R.string.feedback_attachment_file),
                fontSize = 13.sp,
                color = chipTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp)
            )
        }
    }
}

/** 发送按钮：圆形主色底箭头图标（禁用灰），聊天主页与会话详情复用 */
@Composable
fun FeedbackSendButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Send,
            contentDescription = stringResource(R.string.feedback_send),
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
