package com.aiexile.animetrack.ui.player.settings

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
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
import com.aiexile.animetrack.ui.components.AppSwitch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPreferencesScreen(
    onBack: () -> Unit,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()

    val defaultSpeed by settingsRepository.playerDefaultSpeed.collectAsStateWithLifecycle(initialValue = 1f)
    val defaultVideoScale by settingsRepository.playerDefaultVideoScale.collectAsStateWithLifecycle(
        initialValue = settingsRepository.cachedPlayerDefaultVideoScale()
    )
    val autoLandscape by settingsRepository.playerAutoLandscape.collectAsStateWithLifecycle(initialValue = false)
    val hardwareAcceleration by settingsRepository.playerHardwareAcceleration.collectAsStateWithLifecycle(initialValue = true)
    val rememberPosition by settingsRepository.playerRememberPosition.collectAsStateWithLifecycle(initialValue = true)
    val autoPlayNext by settingsRepository.playerAutoPlayNext.collectAsStateWithLifecycle(initialValue = true)
    val updateProgressOnComplete by settingsRepository.playerUpdateProgressOnComplete.collectAsStateWithLifecycle(initialValue = true)
    val longPressSpeed by settingsRepository.playerLongPressSpeed.collectAsStateWithLifecycle(initialValue = 2f)

    AppScaffold(
        title = stringResource(R.string.player_settings_title),
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

                SettingsOptionRow(
                    title = stringResource(R.string.player_settings_default_video_scale),
                    options = listOf(
                        SettingsOption(0, stringResource(R.string.player_scale_fit)),
                        SettingsOption(1, stringResource(R.string.player_scale_fill)),
                        SettingsOption(2, stringResource(R.string.player_scale_crop))
                    ),
                    selected = defaultVideoScale,
                    onSelected = { scale ->
                        scope.launch { settingsRepository.setPlayerDefaultVideoScale(scale) }
                    },
                    leading = rememberAppIconPainter(AppIcon.FULLSCREEN),
                    verticalPadding = PlayerSettingRowVerticalPadding
                )

                SwitchRow(
                    icon = rememberAppIconPainter(AppIcon.SCREEN_ROTATION),
                    title = stringResource(R.string.player_auto_landscape),
                    subtitle = stringResource(R.string.player_settings_auto_landscape_hint),
                    checked = autoLandscape,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerAutoLandscape(enabled) }
                    }
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

                // 播放完成后更新追番进度
                SwitchRow(
                    icon = rememberAppIconPainter(AppIcon.CHECK_CIRCLE),
                    title = stringResource(R.string.player_settings_update_progress),
                    subtitle = stringResource(R.string.player_settings_update_progress_hint),
                    checked = updateProgressOnComplete,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerUpdateProgressOnComplete(enabled) }
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

                Spacer(modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** 行间细分隔线 */
@Composable
private fun SettingDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(vertical = 6.dp)
    )
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
