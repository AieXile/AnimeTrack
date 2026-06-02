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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.theme.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val autoCompleteEnabled by themeViewModel.autoCompleteEnabled.collectAsState()
    val completedToastEnabled by themeViewModel.completedToastEnabled.collectAsState()
    val hideBangumiAvatar by themeViewModel.hideBangumiAvatar.collectAsState()
    val showUpdateBanner by themeViewModel.showUpdateBanner.collectAsState()
    val showCalendarButton by themeViewModel.showCalendarButton.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "功能",
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

            item {
                SettingsGroup(title = "观看") {
                    Column {
                        SwitchItem(
                            title = "自动完结",
                            description = "观看进度拉满时自动标记为已看完",
                            checked = autoCompleteEnabled,
                            onCheckedChange = { themeViewModel.setAutoCompleteEnabled(it) }
                        )
                        SwitchItem(
                            title = "完结撒花提示",
                            description = "标记为已看完时显示完结撒花提示",
                            checked = completedToastEnabled,
                            onCheckedChange = { themeViewModel.setCompletedToastEnabled(it) }
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "界面") {
                    Column {
                        SwitchItem(
                            title = "隐藏 Bangumi 头像",
                            description = "隐藏主界面顶部的 Bangumi 登录头像",
                            checked = hideBangumiAvatar,
                            onCheckedChange = { themeViewModel.setHideBangumiAvatar(it) },
                            badge = "未完成"
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "看板") {
                    Column {
                        SwitchItem(
                            title = "今日更新提醒",
                            description = "在主界面顶部显示今日番剧更新提醒卡片",
                            checked = showUpdateBanner,
                            onCheckedChange = { themeViewModel.setShowUpdateBanner(it) }
                        )
                        SwitchItem(
                            title = "日程预览按钮",
                            description = "在看板右上角显示日历按钮，查看今明日更新",
                            checked = showCalendarButton,
                            onCheckedChange = { themeViewModel.setShowCalendarButton(it) }
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
                                shape = RoundedCornerShape(4.dp)
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}