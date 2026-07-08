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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToWebDAVBrowse: () -> Unit = {},
    settingsRepository: SettingsRepository = remember { AppContainer.getSettingsRepository() }
) {
    val scope = rememberCoroutineScope()

    val defaultSpeed by settingsRepository.playerDefaultSpeed.collectAsState(initial = 1f)
    val hardwareAcceleration by settingsRepository.playerHardwareAcceleration.collectAsState(initial = true)
    val rememberPosition by settingsRepository.playerRememberPosition.collectAsState(initial = true)
    val autoPlayNext by settingsRepository.playerAutoPlayNext.collectAsState(initial = false)
    val longPressSpeed by settingsRepository.playerLongPressSpeed.collectAsState(initial = 2f)
    val webdavMediaPath by settingsRepository.webdavMediaPath.collectAsState(initial = "")

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showLongPressSpeedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "播放器设置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
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
            Spacer(modifier = Modifier.size(16.dp))

            // 默认播放速度
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSpeedDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "默认播放速度",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${defaultSpeed}x",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // 长按加速速度
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLongPressSpeedDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "长按加速速度",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "长按屏幕时以 ${longPressSpeed}x 播放，松手恢复",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // 硬件加速
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "硬件加速",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "使用硬件解码器提升播放性能",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hardwareAcceleration,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerHardwareAcceleration(enabled) }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // 记忆播放位置
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "记忆播放位置",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "继续上次播放进度",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rememberPosition,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerRememberPosition(enabled) }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // 自动播放下一集
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动播放下一集",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "当前视频播放结束后自动播放下一个",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoPlayNext,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerAutoPlayNext(enabled) }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // WebDAV 媒体路径
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToWebDAVBrowse() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WebDAV 媒体路径",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (webdavMediaPath.isBlank()) "未设置" else webdavMediaPath,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.size(32.dp))

            // 打开播放器
            Button(
                onClick = onNavigateToPlayer,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "打开播放器")
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
            title = "长按加速速度",
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

@Composable
private fun SpeedSelectionDialog(
    title: String = "默认播放速度",
    currentSpeed: Float,
    speeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
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
                text = "取消",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
            )
        }
    )
}
