package com.aiexile.animetrack.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.remote.AnimeQuote
import com.aiexile.animetrack.di.ImportBannerState
import com.aiexile.animetrack.di.TopNoticeState
import com.aiexile.animetrack.di.TopNoticeType
import com.aiexile.animetrack.model.AuthSource
import com.aiexile.animetrack.ui.components.TokenExpiredBanner
import com.aiexile.animetrack.ui.components.TopNoticeBanner
import com.aiexile.animetrack.ui.icons.AppIcon

/**
 * 顶部横幅组：Token 失效 / 彩蛋语录 / 数据导入 / 通用顶部通知。
 *
 * 仅负责渲染与文案拼接；可见性判断、自动消失计时与手动 dismiss 状态机
 * 保留在 AnimeTrackApp（本文件不持有任何状态）。调用顺序即横幅堆叠顺序。
 */
@Composable
internal fun OverlayBanners(
    bannerActuallyVisible: Boolean,
    bannerVisibleSources: Set<AuthSource>,
    userTokenKicked: Boolean,
    onTokenBannerClick: () -> Unit,
    onTokenBannerDismiss: () -> Unit,
    easterEggVisible: Boolean,
    quoteForDisplay: AnimeQuote?,
    onEasterEggDismiss: () -> Unit,
    importBannerVisible: Boolean,
    importBannerForDisplay: ImportBannerState?,
    onImportBannerDismiss: () -> Unit,
    topNoticeVisible: Boolean,
    topNoticeForDisplay: TopNoticeState?,
    onTopNoticeDismiss: () -> Unit
) {
    // Token 失效横幅文案：设备被下线（kicked）单独文案；单源带源名；多源只报数量
    val bannerText = when {
        bannerVisibleSources == setOf(AuthSource.USER) && userTokenKicked ->
            stringResource(R.string.token_expired_banner_kicked)
        bannerVisibleSources.size == 1 ->
            stringResource(R.string.token_expired_banner_single, bannerVisibleSources.first().displayName)
        else ->
            stringResource(R.string.token_expired_banner_multi, bannerVisibleSources.size)
    }
    // 顶部横幅纵向堆叠（Token 失效与彩蛋同时出现概率极低）
    // statusBarsPadding 统一加在堆叠容器上：各横幅组件内部不带状态栏 padding，
    // 避免多条横幅同时显示时每条重复叠加状态栏高度导致间距过大
    Column(modifier = Modifier.statusBarsPadding()) {
        TokenExpiredBanner(
            visible = bannerActuallyVisible,
            text = bannerText,
            onClick = onTokenBannerClick,
            onDismiss = onTokenBannerDismiss
        )
        TopNoticeBanner(
            visible = easterEggVisible,
            icon = AppIcon.STAR_SHINE,
            iconTint = MaterialTheme.colorScheme.primary,
            text = quoteForDisplay?.text.orEmpty(),
            secondaryText = quoteForDisplay?.from
                ?.takeIf { it.isNotBlank() }
                ?.let { "—— $it" },
            onDismiss = onEasterEggDismiss
        )
        TopNoticeBanner(
            visible = importBannerVisible,
            icon = if (importBannerForDisplay?.isError == true) AppIcon.ERROR else AppIcon.CHECK_CIRCLE,
            iconTint = if (importBannerForDisplay?.isError == true) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            text = importBannerForDisplay?.text.orEmpty(),
            loading = importBannerForDisplay?.loading == true,
            onDismiss = onImportBannerDismiss
        )
        TopNoticeBanner(
            visible = topNoticeVisible,
            icon = when (topNoticeForDisplay?.type) {
                TopNoticeType.SUCCESS -> AppIcon.CHECK_CIRCLE
                TopNoticeType.CELEBRATION -> AppIcon.STAR_SHINE
                null -> AppIcon.STAR_SHINE
            },
            iconTint = MaterialTheme.colorScheme.primary,
            text = topNoticeForDisplay?.text.orEmpty(),
            secondaryText = topNoticeForDisplay?.secondaryText,
            onDismiss = onTopNoticeDismiss
        )
    }
}
