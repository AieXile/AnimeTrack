package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.aiexile.animetrack.ui.navigation.Routes
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
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
            shape = SquircleShape(24.dp),
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
                if (searchActive) {
                    SettingsSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            searchActive = false
                            searchQuery = ""
                        }
                    )
                } else {
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
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.common_search),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
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
        // ---- 登录状态（登录卡片副标题） ----
        val bilibiliAuthManager = remember { AppContainer.getBilibiliAuthManager() }
        val authManager = remember { AppContainer.getAuthManager() }
        val userAuthManager = remember { AppContainer.getUserAuthManager() }
        val bilibiliLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsState(initial = false)
        val bangumiLoggedIn by authManager.isLoggedIn.collectAsState(initial = false)
        val userLoggedIn by userAuthManager.isLoggedIn.collectAsState(initial = false)

        // ---- 各设置项的标题 / 副标题 ----
        val loginTitle = stringResource(R.string.settings_login)
        val animetrackConnectedText = stringResource(R.string.settings_animetrack_connected)
        val bilibiliConnectedText = stringResource(R.string.settings_bilibili_connected)
        val bangumiConnectedText = stringResource(R.string.settings_bangumi_connected)
        val connectedSuffix = stringResource(R.string.settings_connected_suffix)
        val loginDefaultSubtitle = stringResource(R.string.settings_login_subtitle)
        val loginStatusParts = buildList {
            if (animetrackConnectedText.isNotEmpty() && userLoggedIn) add(animetrackConnectedText)
            if (bilibiliLoggedIn) add(bilibiliConnectedText)
            if (bangumiLoggedIn) add(bangumiConnectedText)
        }
        val loginSubtitle = if (loginStatusParts.isEmpty()) {
            loginDefaultSubtitle
        } else {
            "${loginStatusParts.joinToString(" · ")} $connectedSuffix"
        }

        val currentPreset = settingsRepository?.themePreset?.collectAsState(ThemePreset.MONO_BLACK)?.value
        val currentMode = settingsRepository?.themeMode?.collectAsState(ThemeMode.SYSTEM)?.value
        val modeLabel = when (currentMode) {
            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
            else -> stringResource(R.string.settings_theme_system)
        }
        val themeSubtitleFormat = stringResource(R.string.settings_theme_subtitle_format)
        val appearanceTitle = stringResource(R.string.settings_appearance)
        val appearanceSubtitle = currentPreset?.let { themeSubtitleFormat.format(it.displayName, modeLabel) }
            ?: stringResource(R.string.settings_appearance_default_subtitle)

        val fontTitle = stringResource(R.string.settings_font)
        val fontSubtitle = stringResource(R.string.settings_font_subtitle)
        val customizeNavTitle = stringResource(R.string.settings_customize_nav)
        val featuresTitle = stringResource(R.string.settings_features)
        val proxyTitle = stringResource(R.string.settings_proxy)
        val proxySubtitle = stringResource(R.string.settings_proxy_subtitle)
        val dataManageTitle = stringResource(R.string.settings_data_manage)
        val dataManageSubtitle = stringResource(R.string.settings_data_manage_subtitle)

        val updateNotificationTitle = stringResource(R.string.settings_update_notification)
        val updateNotificationSummaryFormat = stringResource(R.string.settings_update_notification_summary)
        val updateNotificationSubtitle = if (updateNotificationEnabled) {
            String.format(updateNotificationSummaryFormat, updateNotificationHour, updateNotificationMinute)
        } else {
            stringResource(R.string.settings_update_notification_disabled)
        }

        val tmdbTitle = stringResource(R.string.settings_tmdb_api_key)
        val tmdbSubtitle = tmdbApiKey?.let { key ->
            if (key.length > 8) {
                key.take(4) + "****" + key.takeLast(4)
            } else if (key.isNotBlank()) {
                "****"
            } else null
        } ?: stringResource(R.string.common_not_set)

        val aboutTitle = stringResource(R.string.settings_about)

        // ---- 搜索结果：具体设置项（标题/描述/关键词模糊匹配，含拼音） ----
        val context = androidx.compose.ui.platform.LocalContext.current
        val searchResults = remember(searchQuery) {
            settingsSearchIndex.filter { item ->
                val title = context.getString(item.titleRes)
                val desc = item.descRes?.let { context.getString(it) } ?: ""
                fuzzyMatch(searchQuery, title) || fuzzyMatch(searchQuery, desc) ||
                    item.keywords.any { fuzzyMatch(searchQuery, it) }
            }
        }

        // 搜索结果点击：发起高亮定位请求并跳转对应子页面
        val onSearchResultClick: (SearchableSetting) -> Unit = { item ->
            searchActive = false
            searchQuery = ""
            // 先登记定位请求再导航，确保目标页面进入组合时能立即消费
            if (item.route != SETTINGS_MAIN_ROUTE) {
                SettingsHighlightBus.request(item.route, item.key)
            }
            when (item.route) {
                Routes.ABOUT -> onNavigateAbout()
                Routes.NAVIGATION_CUSTOMIZE -> onNavigateCustomize()
                Routes.APPEARANCE -> onNavigateAppearance()
                Routes.FEATURES -> onNavigateFeatures()
                Routes.DATA_MANAGE -> onNavigateDataManage()
                Routes.UPDATE_NOTIFICATION -> onNavigateUpdateNotification()
                Routes.LOGIN -> onNavigateLogin()
                Routes.BANGUMI_PROXY -> onNavigateBangumiProxy()
                Routes.FONT_SETTINGS -> onNavigateFontSettings()
                Routes.WEBDAV_SYNC -> onNavigate(Routes.WEBDAV_SYNC)
                // TMDB API Key 是主页弹窗：直接打开，无需高亮
                SETTINGS_MAIN_ROUTE -> {
                    if (item.key == "tmdb") {
                        tmdbApiKeyInput = tmdbApiKey ?: ""
                        showTmdbApiKeyDialog = true
                    }
                }
                else -> onNavigate(item.route)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 16.dp)
        ) {
            LazyColumn(
                // 大屏适配：限制内容最大宽度并居中，避免平板上表单拉伸过宽（手机宽度 <720dp 不受影响）
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (searchActive) {
                    // ---- 搜索模式：展示具体设置项结果（空查询不显示推荐列表） ----
                    if (searchQuery.isNotBlank()) {
                        if (searchResults.isEmpty()) {
                            item(key = "search_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_search_no_result),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(
                                count = searchResults.size,
                                key = { i -> searchResults[i].route + ":" + (searchResults[i].key ?: "") }
                            ) { i ->
                                val item = searchResults[i]
                                SearchSettingResultItem(
                                    item = item,
                                    onClick = { onSearchResultClick(item) }
                                )
                            }
                        }
                    }
                } else {
                    // ---- 浏览模式：设置入口列表 ----
                    item(key = "login") {
                        SettingCard(
                            title = loginTitle,
                            subtitle = loginSubtitle,
                            icon = Icons.AutoMirrored.Rounded.Login,
                            onClick = onNavigateLogin
                        )
                    }
                    item(key = "appearance") {
                        SettingCard(
                            title = appearanceTitle,
                            subtitle = appearanceSubtitle,
                            icon = Icons.Rounded.Palette,
                            onClick = onNavigateAppearance
                        )
                    }
                    item(key = "font") {
                        SettingCard(
                            title = fontTitle,
                            subtitle = fontSubtitle,
                            icon = Icons.Rounded.TextFields,
                            onClick = onNavigateFontSettings
                        )
                    }
                    item(key = "customize_nav") {
                        SettingCard(
                            title = customizeNavTitle,
                            icon = Icons.Rounded.Navigation,
                            onClick = onNavigateCustomize
                        )
                    }
                    item(key = "features") {
                        SettingCard(
                            title = featuresTitle,
                            icon = Icons.Rounded.Tune,
                            onClick = onNavigateFeatures
                        )
                    }
                    item(key = "proxy") {
                        SettingCard(
                            title = proxyTitle,
                            subtitle = proxySubtitle,
                            icon = Icons.Rounded.CloudQueue,
                            onClick = onNavigateBangumiProxy
                        )
                    }
                    item(key = "data_manage") {
                        SettingCard(
                            title = dataManageTitle,
                            subtitle = dataManageSubtitle,
                            icon = Icons.Rounded.Storage,
                            onClick = onNavigateDataManage
                        )
                    }
                    // 更新通知入口（受开发者开关控制）
                    if (updateNotificationVisible == true) {
                        item(key = "update_notification") {
                            SettingCard(
                                title = updateNotificationTitle,
                                subtitle = updateNotificationSubtitle,
                                icon = Icons.Rounded.Notifications,
                                onClick = onNavigateUpdateNotification
                            )
                        }
                    }
                    item(key = "tmdb_api_key") {
                        SettingCard(
                            title = tmdbTitle,
                            subtitle = tmdbSubtitle,
                            icon = Icons.Rounded.Key,
                            onClick = {
                                tmdbApiKeyInput = tmdbApiKey ?: ""
                                showTmdbApiKeyDialog = true
                            }
                        )
                    }
                    item(key = "about") {
                        SettingCard(
                            title = aboutTitle,
                            icon = Icons.Rounded.Info,
                            onClick = onNavigateAbout
                        )
                    }
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
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 搜索结果项：具体设置项卡片（标题 + 描述），
 * 点击后跳转到所属页面并高亮定位到该选项
 */
@Composable
private fun SearchSettingResultItem(
    item: SearchableSetting,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = SquircleShape(16.dp),
                spotColor = MaterialTheme.colorScheme.outlineVariant
            )
            .clip(SquircleShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                text = stringResource(item.titleRes),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.descRes?.let { descRes ->
                Text(
                    text = stringResource(descRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}
