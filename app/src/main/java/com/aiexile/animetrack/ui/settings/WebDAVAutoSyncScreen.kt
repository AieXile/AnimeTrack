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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import com.aiexile.animetrack.ui.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.aiexile.animetrack.R
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
                        text = stringResource(R.string.webdav_auto_title),
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
                SettingsGroup(title = stringResource(R.string.webdav_auto_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.webdav_auto_enable))
                            Text(
                                text = stringResource(R.string.webdav_auto_enable_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppSwitch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncEnabled(it) } }
                        )
                    }
                }
            }

            // 同步触发条件
            item {
                SettingsGroup(
                    title = stringResource(R.string.webdav_auto_trigger_section),
                    subtitle = if (autoSyncEnabled) null else stringResource(R.string.webdav_auto_enable_first)
                ) {
                    Column {
                        // 当番剧数据发生变化时
                        TriggerItem(
                            title = stringResource(R.string.webdav_auto_trigger_data_change),
                            description = stringResource(R.string.webdav_auto_trigger_data_change_desc),
                            enabled = autoSyncEnabled,
                            checked = onDataChange,
                            onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncOnDataChange(it) } }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 每次打开App时
                        TriggerItem(
                            title = stringResource(R.string.webdav_auto_trigger_app_open),
                            description = stringResource(R.string.webdav_auto_trigger_app_open_desc),
                            enabled = autoSyncEnabled,
                            checked = onAppOpen,
                            onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncOnAppOpen(it) } }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 定时同步
                        TriggerItem(
                            title = stringResource(R.string.webdav_auto_trigger_scheduled),
                            description = stringResource(R.string.webdav_auto_trigger_scheduled_desc),
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
                                    text = stringResource(R.string.webdav_auto_sync_interval),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                IntervalOption(
                                    label = stringResource(R.string.webdav_auto_interval_6h),
                                    selected = interval == 0,
                                    enabled = autoSyncEnabled,
                                    onClick = { scope.launch { settingsRepository.setWebdavAutoSyncInterval(0) } }
                                )
                                IntervalOption(
                                    label = stringResource(R.string.webdav_auto_interval_12h),
                                    selected = interval == 1,
                                    enabled = autoSyncEnabled,
                                    onClick = { scope.launch { settingsRepository.setWebdavAutoSyncInterval(1) } }
                                )
                                IntervalOption(
                                    label = stringResource(R.string.webdav_auto_interval_daily),
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
                                    text = stringResource(R.string.webdav_auto_wifi_only),
                                    color = if (autoSyncEnabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Text(
                                    text = stringResource(R.string.webdav_auto_wifi_only_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AppSwitch(
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
                    title = stringResource(R.string.webdav_auto_strategy_section),
                    subtitle = if (autoSyncEnabled) null else stringResource(R.string.webdav_auto_enable_first)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.webdav_auto_custom_strategy),
                                    color = if (autoSyncEnabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Text(
                                    text = if (useCustomStrategy) stringResource(R.string.webdav_auto_custom_strategy_on) else stringResource(R.string.webdav_auto_custom_strategy_off),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AppSwitch(
                                checked = useCustomStrategy,
                                onCheckedChange = { scope.launch { settingsRepository.setWebdavAutoSyncUseCustomStrategy(it) } },
                                enabled = autoSyncEnabled
                            )
                        }

                        if (useCustomStrategy && autoSyncEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.webdav_auto_backup_format),
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
                                    Text(text = stringResource(R.string.webdav_auto_format_json))
                                    Text(
                                        text = stringResource(R.string.webdav_auto_format_json_desc),
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
                                    Text(text = stringResource(R.string.webdav_auto_format_zip))
                                    Text(
                                        text = stringResource(R.string.webdav_auto_format_zip_desc),
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
        AppSwitch(
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
