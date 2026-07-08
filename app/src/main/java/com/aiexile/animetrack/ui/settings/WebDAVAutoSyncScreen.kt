package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.aiexile.animetrack.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDAVAutoSyncScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val autoSyncEnabled by settingsRepository.webdavAutoSyncEnabled.collectAsState(false)
    val onDataChange by settingsRepository.webdavAutoSyncOnDataChange.collectAsState(true)
    val onAppOpen by settingsRepository.webdavAutoSyncOnAppOpen.collectAsState(false)
    val scheduled by settingsRepository.webdavAutoSyncScheduled.collectAsState(false)
    val interval by settingsRepository.webdavAutoSyncInterval.collectAsState(2)
    val wifiOnly by settingsRepository.webdavAutoSyncWifiOnly.collectAsState(true)
    val useCustomStrategy by settingsRepository.webdavAutoSyncUseCustomStrategy.collectAsState(false)
    val backupStrategy by settingsRepository.webdavAutoSyncBackupStrategy.collectAsState(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "自动同步",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 总开关
            item {
                SettingsGroup(title = "自动同步") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "启用自动同步")
                            Text(
                                text = "开启后将按设定条件自动备份到 WebDAV",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncEnabled(it) } }
                        )
                    }
                }
            }

            // 同步触发条件
            item {
                SettingsGroup(
                    title = "同步触发条件",
                    subtitle = if (autoSyncEnabled) null else "请先开启自动同步"
                ) {
                    Column {
                        // 当番剧数据发生变化时
                        TriggerItem(
                            title = "数据变化时",
                            description = "添加、编辑或删除番剧后自动同步",
                            enabled = autoSyncEnabled,
                            checked = onDataChange,
                            onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncOnDataChange(it) } }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 每次打开App时
                        TriggerItem(
                            title = "打开App时",
                            description = "每次启动应用时自动同步",
                            enabled = autoSyncEnabled,
                            checked = onAppOpen,
                            onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncOnAppOpen(it) } }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 定时同步
                        TriggerItem(
                            title = "定时同步",
                            description = "按固定时间间隔自动同步",
                            enabled = autoSyncEnabled,
                            checked = scheduled,
                            onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncScheduled(it) } }
                        )

                        if (scheduled && autoSyncEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = "同步间隔",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                IntervalOption(
                                    label = "每 6 小时",
                                    selected = interval == 0,
                                    enabled = autoSyncEnabled,
                                    onClick = { scope.launch { settingsRepository.setWebdavAutoSyncInterval(0) } }
                                )
                                IntervalOption(
                                    label = "每 12 小时",
                                    selected = interval == 1,
                                    enabled = autoSyncEnabled,
                                    onClick = { scope.launch { settingsRepository.setWebdavAutoSyncInterval(1) } }
                                )
                                IntervalOption(
                                    label = "每天",
                                    selected = interval == 2,
                                    enabled = autoSyncEnabled,
                                    onClick = { scope.launch { settingsRepository.setWebdavAutoSyncInterval(2) } }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 仅Wi-Fi下同步
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "仅在 Wi-Fi 下同步",
                                    color = if (autoSyncEnabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Text(
                                    text = "避免移动网络下消耗流量",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = wifiOnly,
                                onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncWifiOnly(it) } },
                                enabled = autoSyncEnabled
                            )
                        }
                    }
                }
            }

            // 同步策略
            item {
                SettingsGroup(
                    title = "同步策略",
                    subtitle = if (autoSyncEnabled) null else "请先开启自动同步"
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "使用自定义策略",
                                    color = if (autoSyncEnabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Text(
                                    text = if (useCustomStrategy) "使用下方独立配置" else "与同步界面保持一致",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useCustomStrategy,
                                onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncUseCustomStrategy(it) } },
                                enabled = autoSyncEnabled
                            )
                        }

                        if (useCustomStrategy && autoSyncEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "备份格式",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = backupStrategy == 0,
                                    onClick = { scope.launch { settingsRepository.setWebdavAutoSyncBackupStrategy(0) } }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { scope.launch { settingsRepository.setWebdavAutoSyncBackupStrategy(0) } }
                                ) {
                                    Text(text = "JSON")
                                    Text(
                                        text = "仅数据，传输速度快",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = backupStrategy == 1,
                                    onClick = { scope.launch { settingsRepository.setWebdavAutoSyncBackupStrategy(1) } }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { scope.launch { settingsRepository.setWebdavAutoSyncBackupStrategy(1) } }
                                ) {
                                    Text(text = "完整 ZIP")
                                    Text(
                                        text = "带图片，传输速度慢",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun TriggerItem(
    title: String,
    description: String,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun IntervalOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = { onClick() },
            enabled = enabled
        )
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
