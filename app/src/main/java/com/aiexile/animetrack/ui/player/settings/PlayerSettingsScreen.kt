package com.aiexile.animetrack.ui.player.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.player.guide.PlaybackGuideAction
import com.aiexile.animetrack.ui.player.guide.PlaybackGuidePage
import com.aiexile.animetrack.ui.player.guide.PlaybackGuideScaffold
import com.aiexile.animetrack.ui.player.guide.PlaybackGuideStep
import com.aiexile.animetrack.ui.player.guide.rememberPlaybackGuide
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    onNavigateToPlayerPreferences: () -> Unit = {},
    onNavigateToMediaLibrary: () -> Unit = {},
    onNavigateToLocalMedia: () -> Unit = {},
    onNavigateToWebPlayback: () -> Unit = {},
    onNavigateToWebDAVBrowse: () -> Unit = {},
    onNavigateToPlayerWebDavConfig: () -> Unit = {},
    settingsRepository: SettingsRepository
) {
    val playerWebdavUrl by settingsRepository.playerWebdavUrl.collectAsStateWithLifecycle(initialValue = "")
    val webDavConfigured by remember(settingsRepository) {
        settingsRepository.playerWebdavDirectoryConfig.map { it != null }
    }.collectAsStateWithLifecycle(initialValue = null)
    val guide = rememberPlaybackGuide(
        PlaybackGuidePage.OVERVIEW,
        ready = webDavConfigured != null,
        settingsRepository = settingsRepository
    )
    val scrollState = rememberScrollState()
    val atFirstGuideStep = guide.index == 0
    LaunchedEffect(atFirstGuideStep) {
        if (atFirstGuideStep) scrollState.scrollTo(0)
    }
    val webDavStep = if (webDavConfigured == true) {
        PlaybackGuideStep(R.string.webdav_library_title, R.string.player_guide_overview_webdav)
    } else {
        PlaybackGuideStep(R.string.player_webdav_server, R.string.player_guide_webdav_setup)
    }

    PlaybackGuideScaffold(
        guide = guide,
        steps = listOf(
            PlaybackGuideStep(R.string.media_library_title, R.string.player_guide_overview_library),
            PlaybackGuideStep(R.string.local_media_title, R.string.player_guide_overview_local),
            PlaybackGuideStep(R.string.web_playback_title, R.string.player_guide_overview_web),
            webDavStep
        ),
        title = stringResource(R.string.settings_playback),
        onBack = onBack,
        actions = { PlaybackGuideAction(guide::start) }
    ) { chrome ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
                .padding(chrome.content)
        ) {
            Spacer(modifier = Modifier.size(8.dp))

            SectionTitle(text = stringResource(R.string.player_section_watch))

            PlaybackEntryRow(
                icon = rememberAppIconPainter(AppIcon.COLLECTIONS_BOOKMARK),
                title = stringResource(R.string.media_library_title),
                subtitle = stringResource(R.string.media_library_subtitle),
                onClick = onNavigateToMediaLibrary,
                modifier = guide.anchor(R.string.media_library_title)
            )

            PlaybackEntryRow(
                icon = rememberAppIconPainter(AppIcon.INSERT_DRIVE_FILE),
                title = stringResource(R.string.local_media_title),
                subtitle = stringResource(R.string.player_local_media_subtitle),
                onClick = onNavigateToLocalMedia,
                modifier = guide.anchor(R.string.local_media_title)
            )

            PlaybackEntryRow(
                icon = rememberAppIconPainter(AppIcon.LINK),
                title = stringResource(R.string.web_playback_title),
                subtitle = stringResource(R.string.web_playback_entry_subtitle),
                onClick = onNavigateToWebPlayback,
                modifier = guide.anchor(R.string.web_playback_title)
            )

            if (webDavConfigured == true) {
                PlaybackEntryRow(
                    icon = rememberAppIconPainter(AppIcon.FOLDER_OPEN),
                    title = stringResource(R.string.webdav_library_title),
                    subtitle = stringResource(R.string.player_webdav_browse_desc),
                    onClick = onNavigateToWebDAVBrowse,
                    modifier = guide.anchor(R.string.webdav_library_title)
                )
            }

            PlaybackEntryRow(
                icon = rememberAppIconPainter(AppIcon.CLOUD),
                title = stringResource(R.string.player_webdav_server),
                subtitle = if (playerWebdavUrl.isBlank()) {
                    stringResource(R.string.player_webdav_setup_subtitle)
                } else {
                    playerWebdavUrl
                },
                onClick = onNavigateToPlayerWebDavConfig,
                modifier = guide.anchor(R.string.player_webdav_server)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            PlaybackEntryRow(
                icon = rememberAppIconPainter(AppIcon.TUNE),
                title = stringResource(R.string.player_settings_title),
                subtitle = stringResource(R.string.player_settings_subtitle),
                onClick = onNavigateToPlayerPreferences
            )

            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

/** 分组标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
    )
}

/** 点击进入型设置行（图标 + 标题 + 当前值摘要） */
@Composable
private fun PlaybackEntryRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            painter = rememberAppIconPainter(AppIcon.KEYBOARD_ARROW_RIGHT),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
