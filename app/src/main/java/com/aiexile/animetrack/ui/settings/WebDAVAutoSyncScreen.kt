package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.components.AppSwitch
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.settings.SettingsOption
import com.aiexile.animetrack.ui.components.settings.SettingsOptionHost
import com.aiexile.animetrack.ui.components.settings.SettingsOptionRow
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

    // 同步间隔 / 备份格式：都是「一行一个当前值」的单选，收成选项弹层
    val intervalOptions = listOf(
        SettingsOption(0, stringResource(R.string.webdav_auto_interval_6h)),
        SettingsOption(1, stringResource(R.string.webdav_auto_interval_12h)),
        SettingsOption(2, stringResource(R.string.webdav_auto_interval_daily))
    )
    val formatOptions = listOf(
        SettingsOption(
            value = 0,
            label = stringResource(R.string.webdav_auto_format_json),
            supportingText = stringResource(R.string.webdav_auto_format_json_desc)
        ),
        SettingsOption(
            value = 1,
            label = stringResource(R.string.webdav_auto_format_zip),
            supportingText = stringResource(R.string.webdav_auto_format_zip_desc)
        )
    )

    AppScaffold(
        title = stringResource(R.string.webdav_auto_title),
        onBack = onBack
    ) { chrome ->
        SettingsOptionHost(
            modifier = Modifier.fillMaxSize(),
            topInset = chrome.topInset,
            bottomInset = chrome.bottomInset,
            sources = chrome.sources
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(chrome.layout)
                    .padding(horizontal = 16.dp),
                contentPadding = chrome.content,
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

                                SettingsOptionRow(
                                    title = stringResource(R.string.webdav_auto_sync_interval),
                                    options = intervalOptions,
                                    selected = interval,
                                    onSelected = { value ->
                                        scope.launch { settingsRepository.setWebdavAutoSyncInterval(value) }
                                    }
                                )
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
                                Spacer(modifier = Modifier.height(12.dp))

                                SettingsOptionRow(
                                    title = stringResource(R.string.webdav_auto_backup_format),
                                    options = formatOptions,
                                    selected = backupStrategy,
                                    onSelected = { value ->
                                        scope.launch { settingsRepository.setWebdavAutoSyncBackupStrategy(value) }
                                    }
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
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
