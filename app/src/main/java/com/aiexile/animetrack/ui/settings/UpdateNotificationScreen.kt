package com.aiexile.animetrack.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.notification.UpdateNotificationManager
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.UpdatePushSettingsRequest
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.util.TimeZoneHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateNotificationScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by settingsRepository.updateNotificationEnabled.collectAsState(false)
    val hour by settingsRepository.updateNotificationHour.collectAsState(9)
    val minute by settingsRepository.updateNotificationMinute.collectAsState(0)

    val userAuthManager = remember { AppContainer.getUserAuthManager() }
    val isLoggedIn by userAuthManager.isLoggedIn.collectAsState(initial = false)

    var showTimePicker by remember { mutableStateOf(false) }

    // 登录后从后端拉取推送设置
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.userAuthApi.getPushSettings()
                }
                if (response.success && response.settings != null) {
                    val settings = response.settings
                    val beijingHour = TimeZoneHelper.utcHourToBeijing(settings.preferredHour)
                    withContext(Dispatchers.Main) {
                        settingsRepository.setUpdateNotificationEnabled(settings.dailyPushEnabled)
                        settingsRepository.setUpdateNotificationHour(beijingHour)
                        settingsRepository.setUpdateNotificationMinute(settings.preferredMinute)
                    }
                    if (settings.dailyPushEnabled) {
                        UpdateNotificationManager.scheduleDailyNotification(context, beijingHour, settings.preferredMinute)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // 预设时间选项
    data class PresetTime(val hour: Int, val minute: Int, val label: String)
    val presetTimes = listOf(
        PresetTime(9, 0, stringResource(R.string.update_notif_morning_9)),
        PresetTime(12, 0, stringResource(R.string.update_notif_noon_12)),
        PresetTime(19, 0, stringResource(R.string.update_notif_evening_7))
    )
    val isCustomTime = presetTimes.none { it.hour == hour && it.minute == minute }

    // 同步推送设置到后端
    fun syncPushSettingsToServer(pushEnabled: Boolean, dailyPushEnabled: Boolean, beijingHour: Int, beijingMinute: Int) {
        if (!isLoggedIn) return
        scope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.userAuthApi.updatePushSettings(
                    UpdatePushSettingsRequest(
                        pushEnabled = pushEnabled,
                        dailyPushEnabled = dailyPushEnabled,
                        preferredHour = TimeZoneHelper.beijingHourToUtc(beijingHour),
                        preferredMinute = beijingMinute
                    )
                )
            } catch (_: Exception) { }
        }
    }

    // 自定义时间选择器
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = {
                Text(
                    text = stringResource(R.string.update_notif_select_time),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            settingsRepository.setUpdateNotificationHour(timePickerState.hour)
                            settingsRepository.setUpdateNotificationMinute(timePickerState.minute)
                            UpdateNotificationManager.scheduleDailyNotification(
                                context, timePickerState.hour, timePickerState.minute
                            )
                            syncPushSettingsToServer(true, true, timePickerState.hour, timePickerState.minute)
                        }
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Android 13+ 通知权限请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            )
            if (status != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.update_notif_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_notif_anime_update),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.update_notif_anime_update_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { isChecked ->
                            scope.launch {
                                settingsRepository.setUpdateNotificationEnabled(isChecked)
                                if (isChecked) {
                                    requestNotificationPermission()
                                    UpdateNotificationManager.scheduleDailyNotification(
                                        context, hour, minute
                                    )
                                } else {
                                    UpdateNotificationManager.cancelDailyNotification(context)
                                }
                                syncPushSettingsToServer(isChecked, isChecked, hour, minute)
                            }
                        }
                    )
                }
            }

            // 通知时间选择
            if (enabled) {
                item {
                    Text(
                        text = stringResource(R.string.update_notif_time),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.update_notif_time_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(presetTimes.size) { index ->
                    val preset = presetTimes[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    settingsRepository.setUpdateNotificationHour(preset.hour)
                                    settingsRepository.setUpdateNotificationMinute(preset.minute)
                                    UpdateNotificationManager.scheduleDailyNotification(
                                        context, preset.hour, preset.minute
                                    )
                                    syncPushSettingsToServer(true, true, preset.hour, preset.minute)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = hour == preset.hour && minute == preset.minute,
                            onClick = {
                                scope.launch {
                                    settingsRepository.setUpdateNotificationHour(preset.hour)
                                    settingsRepository.setUpdateNotificationMinute(preset.minute)
                                    UpdateNotificationManager.scheduleDailyNotification(
                                        context, preset.hour, preset.minute
                                    )
                                    syncPushSettingsToServer(true, true, preset.hour, preset.minute)
                                }
                            }
                        )
                        Text(
                            text = preset.label,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 自定义时间
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustomTime,
                            onClick = { showTimePicker = true }
                        )
                        Text(
                            text = if (isCustomTime) {
                                stringResource(R.string.update_notif_custom_time_format, hour, minute)
                            } else {
                                stringResource(R.string.update_notif_custom_time)
                            },
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
