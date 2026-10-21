package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.components.AppSwitch
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.settings.SettingsOption
import com.aiexile.animetrack.ui.components.settings.SettingsOptionHost
import com.aiexile.animetrack.ui.components.settings.SettingsOptionRow
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlinx.coroutines.launch

/** 设置项选项弹层的行距：与播放器设置已有的行距保持一致 */
private val PlayerSettingRowVerticalPadding = 14.dp

/** 默认播放速度候选：0.5x – 2.0x */
private val DefaultSpeedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 长按加速速度候选：1.5x – 5.0x（7 项，正好是选项菜单的最大可见行数） */
private val LongPressSpeedOptions = listOf(1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 5.0f)

/**
 * 视频播放聚合页：设置中「视频播放」入口的目标页面。
 *
 * 汇总所有与播放相关的功能，分三组：
 * - 播放行为：默认倍速、长按加速、记忆位置、自动连播、硬件加速
 * - 媒体来源：WebDAV 媒体浏览、WebDAV 服务器配置
 * - 打开播放器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToWebDAVBrowse: () -> Unit = {},
    onNavigateToPlayerWebDavConfig: () -> Unit = {},
    settingsRepository: SettingsRepository = remember { AppContainer.getSettingsRepository() }
) {
    val scope = rememberCoroutineScope()

    val defaultSpeed by settingsRepository.playerDefaultSpeed.collectAsState(initial = 1f)
    val hardwareAcceleration by settingsRepository.playerHardwareAcceleration.collectAsState(initial = true)
    val rememberPosition by settingsRepository.playerRememberPosition.collectAsState(initial = true)
    val autoPlayNext by settingsRepository.playerAutoPlayNext.collectAsState(initial = false)
    val longPressSpeed by settingsRepository.playerLongPressSpeed.collectAsState(initial = 2f)
    val webdavMediaPath by settingsRepository.webdavMediaPath.collectAsState(initial = "")
    val playerWebdavUrl by settingsRepository.playerWebdavUrl.collectAsState(initial = "")

    AppScaffold(
        title = stringResource(R.string.settings_playback),
        onBack = onBack
    ) { chrome ->
        // 选项菜单宿主包住内容；玻璃档的采样源挂在内部内容根上，
        // 菜单自身在采样源之外，避免自采样（规范 §7.2）
        SettingsOptionHost(
            modifier = Modifier.fillMaxSize(),
            topInset = chrome.topInset,
            bottomInset = chrome.bottomInset,
            sources = chrome.sources
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(chrome.layout)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
                    // 玻璃档内容层自垫顶/底内边距
                    .padding(chrome.content)
            ) {
                Spacer(modifier = Modifier.size(8.dp))

                // ==================== 播放行为 ====================
                SectionTitle(text = stringResource(R.string.player_section_behavior))

                // 默认播放速度
                SettingsOptionRow(
                    title = stringResource(R.string.player_settings_default_speed),
                    options = DefaultSpeedOptions.map { SettingsOption(it, "${it}x") },
                    selected = defaultSpeed,
                    onSelected = { speed ->
                        scope.launch { settingsRepository.setPlayerDefaultSpeed(speed) }
                    },
                    leading = rememberAppIconPainter(AppIcon.SPEED),
                    verticalPadding = PlayerSettingRowVerticalPadding
                )

                // 长按加速速度
                SettingsOptionRow(
                    title = stringResource(R.string.player_settings_long_press_speed),
                    subtitle = stringResource(R.string.player_settings_long_press_speed_hint),
                    options = LongPressSpeedOptions.map { SettingsOption(it, "${it}x") },
                    selected = longPressSpeed,
                    onSelected = { speed ->
                        scope.launch { settingsRepository.setPlayerLongPressSpeed(speed) }
                    },
                    leading = rememberAppIconPainter(AppIcon.FAST_FORWARD),
                    verticalPadding = PlayerSettingRowVerticalPadding
                )

                SettingDivider()

                // 记忆播放位置
                SwitchRow(
                    icon = rememberAppIconPainter(AppIcon.BOOKMARKS),
                    title = stringResource(R.string.player_settings_remember_position),
                    subtitle = stringResource(R.string.player_settings_remember_position_hint),
                    checked = rememberPosition,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerRememberPosition(enabled) }
                    }
                )

                // 自动播放下一集
                SwitchRow(
                    icon = rememberAppIconPainter(AppIcon.SKIP_NEXT),
                    title = stringResource(R.string.player_settings_auto_play_next),
                    subtitle = stringResource(R.string.player_settings_auto_play_next_hint),
                    checked = autoPlayNext,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerAutoPlayNext(enabled) }
                    }
                )

                // 硬件加速
                SwitchRow(
                    icon = rememberAppIconPainter(AppIcon.MEMORY),
                    title = stringResource(R.string.player_settings_hardware_acceleration),
                    subtitle = stringResource(R.string.player_settings_hardware_acceleration_hint),
                    checked = hardwareAcceleration,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerHardwareAcceleration(enabled) }
                    }
                )

                Spacer(modifier = Modifier.size(20.dp))

                // ==================== 媒体来源 ====================
                SectionTitle(text = stringResource(R.string.player_section_source))

                // WebDAV 媒体浏览
                WebDavEntryRow(
                    icon = rememberAppIconPainter(AppIcon.FOLDER_OPEN),
                    title = stringResource(R.string.player_webdav_browse),
                    subtitle = if (webdavMediaPath.isBlank()) {
                        stringResource(R.string.common_not_set)
                    } else {
                        webdavMediaPath
                    },
                    onClick = onNavigateToWebDAVBrowse
                )

                // 播放器专属 WebDAV 配置（与备份同步独立）
                WebDavEntryRow(
                    icon = rememberAppIconPainter(AppIcon.CLOUD),
                    title = stringResource(R.string.player_webdav_server),
                    subtitle = if (playerWebdavUrl.isBlank()) {
                        stringResource(R.string.common_not_set)
                    } else {
                        playerWebdavUrl
                    },
                    onClick = onNavigateToPlayerWebDavConfig
                )

                Spacer(modifier = Modifier.size(28.dp))

                // ==================== 打开播放器 ====================
                Button(
                    onClick = onNavigateToPlayer,
                    shape = SquircleShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.player_settings_open_player))
                }

                Spacer(modifier = Modifier.size(24.dp))
            }
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

/** 行间细分隔线 */
@Composable
private fun SettingDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

/** 点击进入型设置行（图标 + 标题 + 当前值摘要） */
@Composable
private fun WebDavEntryRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = PlayerSettingRowVerticalPadding),
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

/** 开关型设置行（图标 + 标题 + 描述 + 开关） */
@Composable
private fun SwitchRow(
    icon: Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PlayerSettingRowVerticalPadding),
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
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
