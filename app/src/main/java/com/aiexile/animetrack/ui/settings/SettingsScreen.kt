package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.theme.ThemePreset
import com.aiexile.animetrack.ui.components.BottomNavigationBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    showBottomBar: Boolean = true,
    onNavigateAbout: () -> Unit,
    onNavigateCustomize: () -> Unit = {},
    onNavigateAppearance: () -> Unit = {},
    onNavigateFeatures: () -> Unit = {},
    onNavigateDataManage: () -> Unit = {},
    onNavigateLogin: () -> Unit = {},
    onNavigateUpdateNotification: () -> Unit = {},
    onNavigateBangumiProxy: () -> Unit = {},
    onNavigateFontSettings: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    settingsRepository: com.aiexile.animetrack.data.SettingsRepository? = null
) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory())
    val tmdbApiKey by settingsViewModel.tmdbApiKey.collectAsState()
    var showTmdbApiKeyDialog by remember { mutableStateOf(false) }
    var tmdbApiKeyInput by remember { mutableStateOf("") }

    val updateNotificationVisible by settingsRepository?.updateNotificationVisible?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val updateNotificationEnabled by settingsRepository?.updateNotificationEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val updateNotificationHour by settingsRepository?.updateNotificationHour?.collectAsState(initial = 9)
        ?: remember { mutableStateOf(9) }
    val updateNotificationMinute by settingsRepository?.updateNotificationMinute?.collectAsState(initial = 0)
        ?: remember { mutableStateOf(0) }

    if (showTmdbApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showTmdbApiKeyDialog = false },
            title = { Text("TMDB API Key") },
            text = {
                OutlinedTextField(
                    value = tmdbApiKeyInput,
                    onValueChange = { tmdbApiKeyInput = it },
                    label = { Text("API Key") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.setTmdbApiKey(tmdbApiKeyInput)
                        showTmdbApiKeyDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTmdbApiKeyDialog = false }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = "settings",
                    onNavigate = onNavigate
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    val bilibiliAuthManager = remember { AppContainer.getBilibiliAuthManager() }
                    val authManager = remember { AppContainer.getAuthManager() }
                    val bilibiliLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsState(initial = false)
                    val bangumiLoggedIn by authManager.isLoggedIn.collectAsState(initial = false)

                    val statusParts = mutableListOf<String>()
                    val bilibiliConnectedText = stringResource(R.string.settings_bilibili_connected)
                    val bangumiConnectedText = stringResource(R.string.settings_bangumi_connected)
                    val loginSubtitle = stringResource(R.string.settings_login_subtitle)
                    if (bilibiliLoggedIn) statusParts.add(bilibiliConnectedText)
                    if (bangumiLoggedIn) statusParts.add(bangumiConnectedText)

                    SettingCard(
                        title = stringResource(R.string.settings_login),
                        subtitle = if (statusParts.isEmpty()) loginSubtitle else statusParts.joinToString(" · "),
                        icon = Icons.AutoMirrored.Filled.Login,
                        onClick = onNavigateLogin
                    )
                }
                item {
                    val currentPreset = settingsRepository?.themePreset?.collectAsState(ThemePreset.MONO_BLACK)?.value
                    val currentMode = settingsRepository?.themeMode?.collectAsState(ThemeMode.SYSTEM)?.value
                    val modeLabel = when (currentMode) {
                        com.aiexile.animetrack.model.ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        com.aiexile.animetrack.model.ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        else -> stringResource(R.string.settings_theme_system)
                    }
                    val themeSubtitleFormat = stringResource(R.string.settings_theme_subtitle_format)
                    val defaultAppearanceSubtitle = stringResource(R.string.settings_appearance_default_subtitle)
                    SettingCard(
                        title = stringResource(R.string.settings_appearance),
                        subtitle = currentPreset?.let { themeSubtitleFormat.format(it.displayName, modeLabel) } ?: defaultAppearanceSubtitle,
                        icon = Icons.Default.Palette,
                        onClick = onNavigateAppearance
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(R.string.settings_font),
                        subtitle = stringResource(R.string.settings_font_subtitle),
                        icon = Icons.Default.TextFields,
                        onClick = onNavigateFontSettings
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(R.string.settings_customize_nav),
                        icon = Icons.Default.Navigation,
                        onClick = onNavigateCustomize
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(R.string.settings_features),
                        icon = Icons.Default.Tune,
                        onClick = onNavigateFeatures
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(R.string.settings_proxy),
                        subtitle = stringResource(R.string.settings_proxy_subtitle),
                        icon = Icons.Default.CloudQueue,
                        onClick = onNavigateBangumiProxy
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(R.string.settings_data_manage),
                        subtitle = stringResource(R.string.settings_data_manage_subtitle),
                        icon = Icons.Default.Storage,
                        onClick = onNavigateDataManage
                    )
                }
                // 更新通知入口（受开发者开关控制）
                if (updateNotificationVisible == true) {
                    item {
                        val updateNotificationSummaryFormat = stringResource(R.string.settings_update_notification_summary)
                        SettingCard(
                            title = stringResource(R.string.settings_update_notification),
                            subtitle = if (updateNotificationEnabled) {
                                String.format(updateNotificationSummaryFormat, updateNotificationHour, updateNotificationMinute)
                            } else {
                                stringResource(R.string.settings_update_notification_disabled)
                            },
                            icon = Icons.Default.Notifications,
                            onClick = onNavigateUpdateNotification
                        )
                    }
                }
                item {
                    val maskedKey = tmdbApiKey?.let { key ->
                        if (key.length > 8) {
                            key.take(4) + "****" + key.takeLast(4)
                        } else if (key.isNotBlank()) {
                            "****"
                        } else null
                    }
                    SettingCard(
                        title = "TMDB API Key",
                        subtitle = maskedKey ?: stringResource(R.string.common_not_set),
                        icon = Icons.Default.Key,
                        onClick = {
                            tmdbApiKeyInput = tmdbApiKey ?: ""
                            showTmdbApiKeyDialog = true
                        }
                    )
                }
                item {
                    SettingCard(
                        title = stringResource(R.string.settings_about),
                        icon = Icons.Default.Info,
                        onClick = onNavigateAbout
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(
                elevation = 2.dp,
                shape = SquircleShape(16.dp),
                spotColor = MaterialTheme.colorScheme.outlineVariant
            )
            .clip(SquircleShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
