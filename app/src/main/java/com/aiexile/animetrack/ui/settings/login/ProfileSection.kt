package com.aiexile.animetrack.ui.settings.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 已登录账号资料区：可点击上传的头像、用户名、ID/徽章、邮箱、注册时间与追番统计栏。
 */
@Composable
internal fun AccountProfileSection(
    avatar: String?,
    isUploadingAvatar: Boolean,
    avatarError: String?,
    username: String?,
    userId: Int?,
    email: String?,
    createdAt: String?,
    userLevel: Int?,
    onAvatarClick: () -> Unit,
    animeRepository: AnimeRepository
) {
    Spacer(modifier = Modifier.height(20.dp))
    // 已登录状态 - 头像区域（可点击上传）
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = !isUploadingAvatar) {
                onAvatarClick()
            },
        contentAlignment = Alignment.Center
    ) {
        val avatarUrl = avatar?.let { "https://www.aiexile.top$it" }
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = stringResource(R.string.user_login_avatar),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                painter = rememberAppIconPainter(AppIcon.ACCOUNT_CIRCLE),
                contentDescription = null,
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        if (isUploadingAvatar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
    avatarError?.let { err ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = err,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = username ?: stringResource(R.string.user_login_user_default),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
    // 用户 ID + 内测/正式版用户标识（ID ≤ 36 为内测用户，之后为正式版用户）
    Row(verticalAlignment = Alignment.CenterVertically) {
        userId?.let { id ->
            Text(
                text = stringResource(R.string.user_login_user_id, id),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (userId != null) {
            val uid = userId!!
            val isBetaUser = uid <= 36
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = SquircleShape(6.dp),
                color = if (isBetaUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = stringResource(
                        if (isBetaUser) R.string.user_login_beta_badge
                        else R.string.user_login_official_badge
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBetaUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            // 贡献等级徽章（服务端下发 level，含 0 即展示；样式与内测徽章同款）
            userLevel?.let { level ->
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = SquircleShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.user_login_level_badge, level),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = email ?: stringResource(R.string.user_login_email_not_set),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (createdAt != null) {
        // 注册时间显示到日（截取 yyyy-MM-dd 部分）
        val joinedDate = createdAt!!.take(10)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.user_login_joined_date, joinedDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    FollowStatsSection(animeRepository)
}
