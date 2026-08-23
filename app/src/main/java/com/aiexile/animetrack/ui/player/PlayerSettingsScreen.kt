package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.aiexile.animetrack.ui.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.launch

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
    onNavigateToWebDAVSync: () -> Unit = {},
    settingsRepository: SettingsRepository = remember { AppContainer.getSettingsRepository() }
) {
    val scope = rememberCoroutineScope()

    val defaultSpeed by settingsRepository.playerDefaultSpeed.collectAsState(initial = 1f)
    val hardwareAcceleration by settingsRepository.playerHardwareAcceleration.collectAsState(initial = true)
    val rememberPosition by settingsRepository.playerRememberPosition.collectAsState(initial = true)
    val autoPlayNext by settingsRepository.playerAutoPlayNext.collectAsState(initial = false)
    val longPressSpeed by settingsRepository.playerLongPressSpeed.collectAsState(initial = 2f)
    val webdavMediaPath by settingsRepository.webdavMediaPath.collectAsState(initial = "")
    val webdavUrl by settingsRepository.webdavUrl.collectAsState(initial = "")

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showLongPressSpeedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_playback),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.size(8.dp))

            // ==================== 播放行为 ====================
            SectionTitle(text = stringResource(R.string.player_section_behavior))

            // 默认播放速度
            SettingRow(
                icon = Icons.Rounded.Speed,
                title = stringResource(R.string.player_settings_default_speed),
                subtitle = "${defaultSpeed}x",
                onClick = { showSpeedDialog = true }
            )

            // 长按加速速度
            SettingRow(
                icon = Icons.Rounded.FastForward,
                title = stringResource(R.string.player_settings_long_press_speed),
                subtitle = stringResource(R.string.player_settings_long_press_speed_hint, longPressSpeed),
                onClick = { showLongPressSpeedDialog = true }
            )

            SettingDivider()

            // 记忆播放位置
            SwitchRow(
                icon = Icons.Rounded.Bookmarks,
                title = stringResource(R.string.player_settings_remember_position),
                subtitle = stringResource(R.string.player_settings_remember_position_hint),
                checked = rememberPosition,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setPlayerRememberPosition(enabled) }
                }
            )

            // 自动播放下一集
            SwitchRow(
                icon = Icons.Rounded.SkipNext,
                title = stringResource(R.string.player_settings_auto_play_next),
                subtitle = stringResource(R.string.player_settings_auto_play_next_hint),
                checked = autoPlayNext,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setPlayerAutoPlayNext(enabled) }
                }
            )

            // 硬件加速
            SwitchRow(
                icon = Icons.Rounded.Memory,
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
            SettingRow(
                icon = Icons.Rounded.FolderOpen,
                title = stringResource(R.string.player_webdav_browse),
                subtitle = if (webdavMediaPath.isBlank()) {
                    stringResource(R.string.common_not_set)
                } else {
                    webdavMediaPath
                },
                onClick = onNavigateToWebDAVBrowse
            )

            // WebDAV 服务器配置（复用同步设置页）
            SettingRow(
                icon = Icons.Rounded.CloudQueue,
                title = stringResource(R.string.player_webdav_server),
                subtitle = if (webdavUrl.isBlank()) {
                    stringResource(R.string.common_not_set)
                } else {
                    webdavUrl
                },
                onClick = onNavigateToWebDAVSync
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
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.developer_video_player))
            }

            Spacer(modifier = Modifier.size(24.dp))
        }
    }

    // 速度选择对话框
    if (showSpeedDialog) {
        SpeedSelectionDialog(
            currentSpeed = defaultSpeed,
            onSpeedSelected = { speed ->
                scope.launch { settingsRepository.setPlayerDefaultSpeed(speed) }
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // 长按加速速度选择对话框
    if (showLongPressSpeedDialog) {
        SpeedSelectionDialog(
            title = stringResource(R.string.player_settings_long_press_speed),
            currentSpeed = longPressSpeed,
            speeds = listOf(1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 5.0f),
            onSpeedSelected = { speed ->
                scope.launch { settingsRepository.setPlayerLongPressSpeed(speed) }
                showLongPressSpeedDialog = false
            },
            onDismiss = { showLongPressSpeedDialog = false }
        )
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
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
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
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 开关型设置行（图标 + 标题 + 描述 + 开关） */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
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

@Composable
private fun SpeedSelectionDialog(
    title: String = stringResource(R.string.player_settings_default_speed),
    currentSpeed: Float,
    speeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = SquircleShape(24.dp),
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeedSelected(speed) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${speed}x",
                            fontSize = 15.sp,
                            fontWeight = if (speed == currentSpeed) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (speed == currentSpeed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Text(
                text = stringResource(R.string.common_cancel),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
            )
        }
    )
}
