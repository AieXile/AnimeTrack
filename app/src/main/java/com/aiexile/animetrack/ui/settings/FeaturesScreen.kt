package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.aiexile.animetrack.data.RatingStandard
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.components.AppSwitch
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.settings.SettingsOption
import com.aiexile.animetrack.ui.components.settings.SettingsOptionHost
import com.aiexile.animetrack.ui.components.settings.SettingsOptionRow
import com.aiexile.animetrack.ui.navigation.Routes
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
    val feedbackReplyPillEnabled by settingsRepository.feedbackReplyPillEnabled.collectAsState(true)
    val showCalendarButton by settingsRepository.showCalendarButton.collectAsState(true)
    val seriesStackEnabled by settingsRepository.seriesStackEnabled.collectAsState(false)
    val ratingStandard by settingsRepository.ratingStandard.collectAsState(RatingStandard.SOURCE)
    val crashReportEnabled by settingsRepository.crashReportEnabled.collectAsState(true)

    // 评分标准候选：两项互斥单选，改用选项弹层后两行收成一行
    val ratingOptions = listOf(
        SettingsOption(
            value = RatingStandard.SOURCE,
            label = stringResource(R.string.features_rating_standard_source),
            supportingText = stringResource(R.string.features_rating_standard_source_desc)
        ),
        SettingsOption(
            value = RatingStandard.MANUAL,
            label = stringResource(R.string.features_rating_standard_manual),
            supportingText = stringResource(R.string.features_rating_standard_manual_desc)
        )
    )

    // 搜索定位：高亮目标项并滚动到所在分组
    val highlightKey = rememberSettingsHighlight(Routes.FEATURES)
    val listState = rememberLazyListState()
    val highlightAnchors = mapOf(
        "search_button" to 1,
        "update_reminder" to 2,
        "feedback_reply_reminder" to 2,
        "calendar_button" to 2,
        "series_stack" to 3,
        "auto_complete" to 4,
        "celebration" to 4,
        "rating" to 5,
        "crash_report" to 6
    )

    // 玻璃档滚动定位需补偿顶栏高度，避免目标项被顶栏遮挡
    AppScaffold(
        title = stringResource(R.string.features_title),
        onBack = onBack
    ) { chrome ->
        SettingsOptionHost(
            modifier = Modifier.fillMaxSize(),
            topInset = chrome.topInset,
            bottomInset = chrome.bottomInset,
            sources = chrome.sources
        ) {
            LaunchedEffect(highlightKey) {
                highlightAnchors[highlightKey]?.let {
                    listState.animateScrollToItem(it, chrome.scrollCompensation)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(chrome.layout)
                    .padding(horizontal = 16.dp),
                contentPadding = chrome.content,
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
                                onCheckedChange = { scope.launch { settingsRepository.setShowSearchButton(it) } },
                                itemKey = "search_button",
                                highlightKey = highlightKey
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
                                onCheckedChange = { scope.launch { settingsRepository.setShowUpdateBanner(it) } },
                                itemKey = "update_reminder",
                                highlightKey = highlightKey
                            )
                            SwitchItem(
                                title = stringResource(R.string.features_feedback_reply_reminder),
                                description = stringResource(R.string.features_feedback_reply_reminder_desc),
                                checked = feedbackReplyPillEnabled,
                                onCheckedChange = { scope.launch { settingsRepository.setFeedbackReplyPillEnabled(it) } },
                                itemKey = "feedback_reply_reminder",
                                highlightKey = highlightKey
                            )
                            SwitchItem(
                                title = stringResource(R.string.features_calendar_preview_button),
                                description = stringResource(R.string.features_calendar_preview_button_desc),
                                checked = showCalendarButton,
                                onCheckedChange = { scope.launch { settingsRepository.setShowCalendarButton(it) } },
                                itemKey = "calendar_button",
                                highlightKey = highlightKey
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
                                itemKey = "series_stack",
                                highlightKey = highlightKey
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
                                onCheckedChange = { scope.launch { settingsRepository.setAutoCompleteEnabled(it) } },
                                itemKey = "auto_complete",
                                highlightKey = highlightKey
                            )
                            SwitchItem(
                                title = stringResource(R.string.features_completed_celebration),
                                description = stringResource(R.string.features_completed_celebration_desc),
                                checked = completedToastEnabled,
                                onCheckedChange = { scope.launch { settingsRepository.setCompletedToastEnabled(it) } },
                                itemKey = "celebration",
                                highlightKey = highlightKey
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.features_rating)) {
                        Column {
                            SettingsOptionRow(
                                title = stringResource(R.string.features_rating_standard),
                                options = ratingOptions,
                                selected = ratingStandard,
                                onSelected = { standard ->
                                    scope.launch { settingsRepository.setRatingStandard(standard) }
                                },
                                modifier = rememberHighlightModifier("rating", highlightKey)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.features_rating_standard_notice),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.features_help_improve)) {
                        Column {
                            SwitchItem(
                                title = stringResource(R.string.features_crash_report),
                                description = stringResource(R.string.features_crash_report_desc),
                                checked = crashReportEnabled,
                                onCheckedChange = { scope.launch { settingsRepository.setCrashReportEnabled(it) } },
                                itemKey = "crash_report",
                                highlightKey = highlightKey
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    itemKey: String? = null,
    highlightKey: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rememberHighlightModifier(itemKey, highlightKey))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
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
