package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()

    val autoCompleteEnabled by settingsRepository.autoCompleteEnabled.collectAsState(true)
    val completedToastEnabled by settingsRepository.completedToastEnabled.collectAsState(true)
    val showSearchButton by settingsRepository.showSearchButton.collectAsState(true)
    val showUpdateBanner by settingsRepository.showUpdateBanner.collectAsState(true)
    val showCalendarButton by settingsRepository.showCalendarButton.collectAsState(true)
    val seriesStackEnabled by settingsRepository.seriesStackEnabled.collectAsState(false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.features_title),
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

            item {
                SettingsGroup(title = stringResource(R.string.common_search)) {
                    Column {
                        SwitchItem(
                            title = stringResource(R.string.features_search_button),
                            description = stringResource(R.string.features_search_button_desc),
                            checked = showSearchButton,
                            onCheckedChange = { scope.launch { settingsRepository.setShowSearchButton(it) } }
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.features_update_reminder)) {
                    Column {
                        SwitchItem(
                            title = stringResource(R.string.features_today_update_reminder),
                            description = stringResource(R.string.features_today_update_reminder_desc),
                            checked = showUpdateBanner,
                            onCheckedChange = { scope.launch { settingsRepository.setShowUpdateBanner(it) } }
                        )
                        SwitchItem(
                            title = stringResource(R.string.features_calendar_preview_button),
                            description = stringResource(R.string.features_calendar_preview_button_desc),
                            checked = showCalendarButton,
                            onCheckedChange = { scope.launch { settingsRepository.setShowCalendarButton(it) } }
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.features_anime_display)) {
                    Column {
                        SwitchItem(
                            title = stringResource(R.string.features_series_stack),
                            description = stringResource(R.string.features_series_stack_desc),
                            checked = seriesStackEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setSeriesStackEnabled(it) } },
                            badge = stringResource(R.string.features_series_stack_badge)
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.features_watching)) {
                    Column {
                        SwitchItem(
                            title = stringResource(R.string.features_auto_complete),
                            description = stringResource(R.string.features_auto_complete_desc),
                            checked = autoCompleteEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setAutoCompleteEnabled(it) } }
                        )
                        SwitchItem(
                            title = stringResource(R.string.features_completed_celebration),
                            description = stringResource(R.string.features_completed_celebration_desc),
                            checked = completedToastEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setCompletedToastEnabled(it) } }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                badge?.let {
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                shape = SquircleShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}