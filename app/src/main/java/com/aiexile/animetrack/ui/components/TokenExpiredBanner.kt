package com.aiexile.animetrack.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiexile.animetrack.ui.icons.AppIcon

/**
 * Token 失效全局横幅:屏幕顶部悬浮提示条,点击直达登录页,可手动关闭。
 *
 * 由持久化失效状态驱动(各 AuthManager.tokenExpired),非一次性事件:
 * 冷启动、后台失效恢复前台后均可见;会话内关闭后,新数据源失效会再次出现。
 */
@Composable
fun TokenExpiredBanner(
    visible: Boolean,
    text: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopNoticeBanner(
        visible = visible,
        icon = AppIcon.ERROR,
        iconTint = MaterialTheme.colorScheme.error,
        text = text,
        onDismiss = onDismiss,
        modifier = modifier,
        onBannerClick = onClick
    )
}
