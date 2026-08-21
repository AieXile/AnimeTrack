package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.aiexile.animetrack.ui.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.R
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onNavigateBilibiliLogin: () -> Unit,
    onNavigateBangumiLogin: () -> Unit,
    onNavigateBangumiAccount: () -> Unit = {},
    onNavigateUserLogin: () -> Unit = {},
    settingsRepository: SettingsRepository? = null
) {
    val bilibiliAuthManager = remember { AppContainer.getBilibiliAuthManager() }
    val bilibiliLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsState(initial = false)
    val bilibiliNickname by bilibiliAuthManager.userNickname.collectAsState(initial = null)
    val bilibiliAvatar by bilibiliAuthManager.userAvatar.collectAsState(initial = null)

    val authManager = remember { AppContainer.getAuthManager() }
    val bangumiLoggedIn by authManager.isLoggedIn.collectAsState(initial = false)
    val bangumiNickname by authManager.userNickname.collectAsState(initial = null)
    val bangumiAvatar by authManager.userAvatar.collectAsState(initial = null)

    val scope = rememberCoroutineScope()
    val hideAvatar by (settingsRepository?.hideBangumiAvatar?.collectAsState(false) ?: remember { mutableStateOf(false) })
    val autoSyncVisible by (settingsRepository?.autoSyncVisible?.collectAsState(false) ?: remember { mutableStateOf(false) })

    // 搜索定位：高亮目标项并滚动到位
    val highlightKey = rememberSettingsHighlight(Routes.LOGIN)
    val listState = rememberLazyListState()
    // LazyColumn 索引：0=AnimeTrack 1=Bilibili 2=Bangumi 3=自动同步 4=隐藏头像
    val highlightAnchors = mapOf(
        "auto_sync" to 3,
        "hide_avatar" to 4
    )
    LaunchedEffect(highlightKey) {
        highlightAnchors[highlightKey]?.let { listState.animateScrollToItem(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.login_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            item {
                val userAuthManager = remember { AppContainer.getUserAuthManager() }
                val userLoggedIn by userAuthManager.isLoggedIn.collectAsState(initial = false)
                val userUsername by userAuthManager.username.collectAsState(initial = null)
                val userAvatar by userAuthManager.avatar.collectAsState(initial = null)
                // 服务器头像存储的是相对路径，需拼接为完整 URL
                val userAvatarUrl = userAvatar?.let { if (it.startsWith("http")) it else "https://www.aiexile.top$it" }

                LoginServiceCard(
                    title = "AnimeTrack",
                    subtitle = if (userLoggedIn) (userUsername ?: stringResource(R.string.login_screen_connected)) else stringResource(R.string.login_screen_sync_data),
                    icon = Icons.Rounded.Person,
                    avatarUrl = if (userLoggedIn) userAvatarUrl else null,
                    onClick = onNavigateUserLogin
                )
            }
            item {
                LoginServiceCard(
                    title = "Bilibili",
                    subtitle = if (bilibiliLoggedIn) (bilibiliNickname ?: stringResource(R.string.login_screen_logged_in)) else stringResource(R.string.login_screen_bilibili_subtitle),
                    icon = Icons.Rounded.Person,
                    avatarUrl = if (bilibiliLoggedIn) bilibiliAvatar else null,
                    onClick = onNavigateBilibiliLogin
                )
            }
            item {
                LoginServiceCard(
                    title = "Bangumi",
                    subtitle = if (bangumiLoggedIn) (bangumiNickname ?: stringResource(R.string.login_screen_logged_in)) else stringResource(R.string.login_screen_bangumi_subtitle),
                    icon = Icons.Rounded.Person,
                    avatarUrl = if (bangumiLoggedIn) bangumiAvatar else null,
                    onClick = if (bangumiLoggedIn) onNavigateBangumiAccount else onNavigateBangumiLogin
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(rememberHighlightModifier("auto_sync", highlightKey)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.login_screen_auto_sync),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.login_screen_auto_sync_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = autoSyncVisible,
                        onCheckedChange = { scope.launch { settingsRepository?.setAutoSyncVisible(it) } }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(rememberHighlightModifier("hide_avatar", highlightKey)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.login_screen_hide_avatar),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.login_screen_hide_avatar_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = hideAvatar,
                        onCheckedChange = { scope.launch { settingsRepository?.setHideBangumiAvatar(it) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginServiceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    avatarUrl: String? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current

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
            if (avatarUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .bitmapConfig(android.graphics.Bitmap.Config.HARDWARE)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

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
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
