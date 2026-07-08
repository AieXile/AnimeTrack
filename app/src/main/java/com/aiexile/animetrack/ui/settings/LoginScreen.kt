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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onNavigateBilibiliLogin: () -> Unit,
    onNavigateBangumiLogin: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "登录",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
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

                LoginServiceCard(
                    title = "AnimeTrack",
                    subtitle = if (userLoggedIn) (userUsername ?: "已连接") else "登录后可同步数据",
                    icon = Icons.Default.Person,
                    onClick = onNavigateUserLogin
                )
            }
            item {
                LoginServiceCard(
                    title = "Bilibili",
                    subtitle = if (bilibiliLoggedIn) (bilibiliNickname ?: "已登录") else "登录后可同步追番列表",
                    icon = Icons.Default.Person,
                    avatarUrl = if (bilibiliLoggedIn) bilibiliAvatar else null,
                    onClick = onNavigateBilibiliLogin
                )
            }
            item {
                LoginServiceCard(
                    title = "Bangumi",
                    subtitle = if (bangumiLoggedIn) (bangumiNickname ?: "已登录") else "登录后可同步番剧数据",
                    icon = Icons.Default.Person,
                    avatarUrl = if (bangumiLoggedIn) bangumiAvatar else null,
                    onClick = onNavigateBangumiLogin
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动同步",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "在Bilibili同步界面显示自动同步开关",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSyncVisible,
                        onCheckedChange = { scope.launch { settingsRepository?.setAutoSyncVisible(it) } }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "隐藏主界面头像",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "隐藏主界面顶部的登录头像按钮",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
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
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.outlineVariant
            )
            .clip(RoundedCornerShape(16.dp))
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
