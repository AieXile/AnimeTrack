package com.aiexile.animetrack.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 通用顶部悬浮通知横幅:状态栏下方滑入的悬浮提示条,
 * 图标 + 主文本(可选次文本)+ 关闭按钮,整条可选点击。
 *
 * TokenExpiredBanner 与彩蛋语录横幅共用此组件。
 * loading=true 时图标位置展示转圈进度且不显示关闭按钮(任务进行中,不可取消)。
 */
@Composable
fun TopNoticeBanner(
    visible: Boolean,
    icon: AppIcon,
    iconTint: Color,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    onBannerClick: (() -> Unit)? = null,
    loading: Boolean = false
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = SquircleShape(16.dp),
                    spotColor = MaterialTheme.colorScheme.outlineVariant
                )
                .clip(SquircleShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .let { m -> if (onBannerClick != null) m.clickable { onBannerClick() } else m }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = iconTint
                    )
                } else {
                    Icon(
                        painter = rememberAppIconPainter(icon),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    if (!secondaryText.isNullOrBlank()) {
                        Text(
                            text = secondaryText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (!loading) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.CLOSE),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(SquircleShape(11.dp))
                            .clickable { onDismiss() }
                            .padding(3.dp)
                    )
                }
            }
        }
    }
}
