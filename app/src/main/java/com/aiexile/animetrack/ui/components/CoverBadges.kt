package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.LocalAnimeColors

private val BadgeShape = SquircleShape(6.dp)

/** 状态角标的两种尺寸规格：卡片右上角（小号）与详情页标题行（大号）。 */
internal enum class StatusBadgeSize(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val fontSize: TextUnit
) {
    /** 主页/收藏页/堆叠卡右上角 */
    Corner(horizontalPadding = 6.dp, verticalPadding = 2.dp, fontSize = 9.sp),

    /** 详情页标题行 */
    Inline(horizontalPadding = 8.dp, verticalPadding = 4.dp, fontSize = 12.sp)
}

/**
 * 状态角标（纯展示）：[size] 决定卡片角标 / 详情页行内徽章两套既有规格。
 */
@Composable
internal fun StatusBadge(
    status: AnimeStatus,
    unaired: Boolean,
    modifier: Modifier = Modifier,
    size: StatusBadgeSize = StatusBadgeSize.Corner
) {
    // 黑白简洁暗色下为半透明黑 scrim + 浅字（见 Theme.kt 中 AnimeColors 的计算），
    // 其余主题/明暗模式保持 primary 实底样式
    val containerColor = LocalAnimeColors.current.statusBadgeContainer
    val contentColor = LocalAnimeColors.current.statusBadgeContent
    val text = if (unaired) {
        stringResource(R.string.status_unaired)
    } else {
        status.displayName
    }
    Surface(
        modifier = modifier,
        shape = BadgeShape,
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = size.horizontalPadding,
                vertical = size.verticalPadding
            ),
            color = contentColor,
            fontSize = size.fontSize,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 封面角落的小图标角标叠层：左下置顶 / 右下收藏。
 *
 * 它们没有详情页的对应标识可落点，因此并入封面共享元素内容一起飞行
 * （详情页封面侧由 [rememberSharedCoverBadgeAlpha] 在末段淡出）。
 * 透明度由调用方在外层套 `graphicsLayer`，常规静态场景保持 identity。
 */
@Composable
internal fun CoverCornerBadges(
    isPinned: Boolean,
    isFavorite: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 置顶角标：左下角小图钉，标识该卡片固定排在列表最前
        if (isPinned) {
            CornerBadgeIcon(
                icon = AppIcon.VERTICAL_ALIGN_TOP,
                contentDescription = stringResource(R.string.common_pin),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        }

        // 收藏角标：右下角小心形，标识该番剧已加入收藏
        if (isFavorite) {
            CornerBadgeIcon(
                icon = AppIcon.FAVORITE,
                contentDescription = stringResource(R.string.common_favorite),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
    }
}

/** 封面角落的小图标角标（置顶/收藏）：与状态徽章同色系、同圆角规格 */
@Composable
private fun CornerBadgeIcon(
    icon: AppIcon,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = BadgeShape,
        color = LocalAnimeColors.current.statusBadgeContainer
    ) {
        Icon(
            painter = rememberAppIconPainter(icon),
            contentDescription = contentDescription,
            tint = LocalAnimeColors.current.statusBadgeContent,
            modifier = Modifier
                .padding(2.dp)
                .size(12.dp)
        )
    }
}
