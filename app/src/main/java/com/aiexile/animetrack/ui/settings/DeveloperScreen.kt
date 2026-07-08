package com.aiexile.animetrack.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.remote.UpdateRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.update.UpdateDialog
import com.aiexile.animetrack.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    onBack: () -> Unit,
    onNavigateToPlayerSettings: () -> Unit = {}
) {
    val settingsRepository = remember { AppContainer.getSettingsRepository() }
    val animeRepository = remember { AppContainer.getAnimeRepository() }
    val developerMode by settingsRepository.developerMode.collectAsState(initial = true)
    val shareButtonEnabled by settingsRepository.shareButtonEnabled.collectAsState(initial = false)
    val updateNotificationVisible by settingsRepository.updateNotificationVisible.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val updateViewModel: UpdateViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val updateRepository = UpdateRepository()
                val settingsRepo = AppContainer.getSettingsRepository()
                return UpdateViewModel(updateRepository, settingsRepo) as T
            }
        }
    )
    val uiState by updateViewModel.uiState.collectAsState()

    var debugCardCount by remember { mutableIntStateOf(5) }

    UpdateDialog(viewModel = updateViewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "开发者选项",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开发者模式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "关闭后将隐藏开发者选项",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = developerMode,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setDeveloperMode(enabled)
                            if (!enabled) {
                                Toast.makeText(context, "已关闭开发者模式", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "分享按钮",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "在详情页显示分享番剧按钮",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = shareButtonEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setShareButtonEnabled(enabled)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "更新通知",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "在设置界面显示更新通知入口",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = updateNotificationVisible,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setUpdateNotificationVisible(enabled)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = "测试通知",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "立即发送一条测试通知",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = {
                    com.aiexile.animetrack.data.notification.UpdateNotificationManager
                        .triggerTestNotification(context)
                    Toast.makeText(context, "3秒后发送测试通知", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = "发送测试通知")
            }

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = "调试卡片",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "添加占位番剧卡片用于界面调试",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { if (debugCardCount > 1) debugCardCount-- }
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "减少",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "$debugCardCount",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                IconButton(
                    onClick = { if (debugCardCount < 50) debugCardCount++ }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "增加",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val statuses = AnimeStatus.entries
                            repeat(debugCardCount) { i ->
                                animeRepository.insertAnime(
                                    Anime(
                                        title = "调试番剧 ${i + 1}",
                                        totalEpisodes = 12,
                                        watchedEpisodes = (0..12).random(),
                                        status = statuses.random(),
                                        rating = (0..10).random().toFloat(),
                                        notes = ""
                                    )
                                )
                            }
                            Toast.makeText(context, "已添加 $debugCardCount 张调试卡片", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = "添加")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val allAnime = animeRepository.getAllAnimes().first()
                            val debugAnime = allAnime.filter { it.title.startsWith("调试番剧") }
                            debugAnime.forEach { animeRepository.deleteAnime(it) }
                            val count = debugAnime.size
                            Toast.makeText(context, if (count > 0) "已删除 $count 张调试卡片" else "没有调试卡片", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "清除")
                }
            }

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = "模拟更新",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "模拟完整更新流程，不会下载真实文件",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = { updateViewModel.simulateUpdate() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = !uiState.isDownloading && uiState.updateInfo == null
            ) {
                Text(text = "触发模拟更新")
            }

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = "视频播放器",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "播放器设置与测试",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = onNavigateToPlayerSettings,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "播放器设置")
            }

            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}
