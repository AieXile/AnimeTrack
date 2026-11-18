package com.aiexile.animetrack.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.util.coverImageRequestForList

/**
 * 播放页下半区的身份卡：封面 + 番剧标题 + 当前集/来源 + 追番进度。
 *
 * 只在使用方传入番剧记录时展示封面与进度；全局网页入口（未关联番剧）退化为纯文字标题卡。
 * 有关联番剧时可点击回到详情页。
 */
@Composable
internal fun PlayerNowPlayingCard(
    anime: Anime?,
    title: String,
    episodeLabel: String?,
    sourceLabel: String?,
    onOpenDetail: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val coverUrl = anime?.coverUrl
    val context = LocalContext.current
    val episodeText = episodeLabel?.takeIf { it.isNotBlank() }
    val sourceText = sourceLabel?.takeIf { it.isNotBlank() }
    // 追番进度只在已有观看记录时展示，未开看的番剧保持两行文字，避免无信息量的第三行
    val watched = anime?.takeIf { it.totalEpisodes > 0 && it.watchedEpisodes > 0 }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(16.dp))
            .then(if (onOpenDetail != null) Modifier.clickable(onClick = onOpenDetail) else Modifier),
        shape = SquircleShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!coverUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(60.dp)
                        .clip(SquircleShape(10.dp))
                ) {
                    AsyncImage(
                        model = coverImageRequestForList(context, coverUrl),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episodeText != null) {
                    Text(
                        text = episodeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (sourceText != null) {
                    Text(
                        text = stringResource(R.string.player_source_line, sourceText),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (watched != null) {
                    Text(
                        text = stringResource(
                            R.string.player_watched_progress,
                            watched.watchedEpisodes,
                            watched.totalEpisodes
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (onOpenDetail != null) {
                AppIcon(
                    icon = AppIcon.KEYBOARD_ARROW_RIGHT,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
