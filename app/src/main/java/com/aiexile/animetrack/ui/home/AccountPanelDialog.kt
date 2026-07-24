package com.aiexile.animetrack.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.auth.UserAuthManager
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun AccountPanelDialog(
    onDismiss: () -> Unit,
    onNavigateUserLogin: () -> Unit,
    onNavigateBilibiliLogin: () -> Unit,
    onNavigateBangumiLogin: () -> Unit
) {
    val authManager = remember { AppContainer.getAuthManager() }
    val bilibiliAuthManager = remember { AppContainer.getBilibiliAuthManager() }
    val userAuthManager = remember { AppContainer.getUserAuthManager() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val notLoggedInText = stringResource(R.string.account_panel_not_logged_in)
    val unknownUserText = stringResource(R.string.account_panel_unknown_user)
    val connectedText = stringResource(R.string.account_panel_connected)

    val bangumiLoggedIn by authManager.isLoggedIn.collectAsState(initial = false)
    val bangumiNickname by authManager.userNickname.collectAsState(initial = null)
    val bangumiAvatar by authManager.userAvatar.collectAsState(initial = null)
    val customAvatarUri by authManager.customAvatarUri.collectAsState(initial = null)

    val bilibiliLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsState(initial = false)
    val bilibiliNickname by bilibiliAuthManager.userNickname.collectAsState(initial = null)
    val bilibiliAvatar by bilibiliAuthManager.userAvatar.collectAsState(initial = null)

    val userLoggedIn by userAuthManager.isLoggedIn.collectAsState(initial = false)
    val userUsername by userAuthManager.username.collectAsState(initial = null)
    val userAvatarPath by userAuthManager.avatar.collectAsState(initial = null)
    // 服务器头像存储的是相对路径，需拼接为完整 URL
    val userAvatar = userAvatarPath?.let { if (it.startsWith("http")) it else "https://www.aiexile.top$it" }

    val anyLoggedIn = userLoggedIn || bangumiLoggedIn || bilibiliLoggedIn
    // 头像优先级：自定义头像 > 服务器头像 > Bilibili 头像 > Bangumi 头像
    val displayAvatar = customAvatarUri ?: userAvatar ?: bilibiliAvatar ?: bangumiAvatar
    val displayName = userUsername ?: bilibiliNickname ?: bangumiNickname ?: notLoggedInText

    var showUserActions by remember { mutableStateOf(false) }
    var showBilibiliActions by remember { mutableStateOf(false) }
    var showBangumiActions by remember { mutableStateOf(false) }
    var showAvatarActions by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                authManager.saveCustomAvatarUri(uri.toString())
            }
        }
    }

    // AnimeTrack 服务器账号操作弹窗
    if (showUserActions) {
        AlertDialog(
            onDismissRequest = { showUserActions = false },
            title = { Text(stringResource(R.string.account_panel_anime_track_account)) },
            text = { Text(stringResource(R.string.account_panel_logged_in_as, userUsername ?: unknownUserText)) },
            confirmButton = {
                TextButton(onClick = {
                    showUserActions = false
                    onDismiss()
                    onNavigateUserLogin()
                }) { Text(stringResource(R.string.account_panel_account_management)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showUserActions = false }) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        showUserActions = false
                        scope.launch { userAuthManager.logout() }
                    }) {
                        Text(stringResource(R.string.account_panel_logout), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // Bilibili 操作弹窗
    if (showBilibiliActions) {
        AlertDialog(
            onDismissRequest = { showBilibiliActions = false },
            title = { Text(stringResource(R.string.account_panel_bilibili_account)) },
            text = { Text(stringResource(R.string.account_panel_logged_in_as, bilibiliNickname ?: unknownUserText)) },
            confirmButton = {
                TextButton(onClick = {
                    showBilibiliActions = false
                    onDismiss()
                    onNavigateBilibiliLogin()
                }) { Text(stringResource(R.string.account_panel_sync)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showBilibiliActions = false }) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        showBilibiliActions = false
                        scope.launch { bilibiliAuthManager.logout() }
                    }) {
                        Text(stringResource(R.string.account_panel_unbind), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // Bangumi 操作弹窗
    if (showBangumiActions) {
        AlertDialog(
            onDismissRequest = { showBangumiActions = false },
            title = { Text(stringResource(R.string.account_panel_bangumi_account)) },
            text = { Text(stringResource(R.string.account_panel_logged_in_as, bangumiNickname ?: unknownUserText)) },
            confirmButton = {
                TextButton(onClick = {
                    showBangumiActions = false
                    scope.launch { authManager.logout() }
                    onDismiss()
                    onNavigateBangumiLogin()
                }) { Text(stringResource(R.string.account_panel_relogin)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showBangumiActions = false }) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        showBangumiActions = false
                        scope.launch { authManager.logout() }
                    }) {
                        Text(stringResource(R.string.account_panel_logout_account), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // 头像操作弹窗
    if (showAvatarActions) {
        AlertDialog(
            onDismissRequest = { showAvatarActions = false },
            title = { Text(stringResource(R.string.account_panel_change_avatar)) },
            text = {
                Column {
                    if (customAvatarUri != null) {
                        TextButton(onClick = {
                            showAvatarActions = false
                            scope.launch { authManager.saveCustomAvatarUri(null) }
                        }) {
                            Text(stringResource(R.string.account_panel_reset_avatar))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAvatarActions = false
                    pickImageLauncher.launch("image/*")
                }) { Text(stringResource(R.string.account_panel_select_image)) }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarActions = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SquircleShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像区域
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (anyLoggedIn) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape
                        )
                        .clickable { showAvatarActions = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (displayAvatar != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(
                                    if (customAvatarUri != null) customAvatarUri!!.toUri()
                                    else displayAvatar
                                )
                                .build(),
                            contentDescription = stringResource(R.string.account_panel_avatar),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = R.drawable.ic_launcher_foreground),
                            fallback = painterResource(id = R.drawable.ic_launcher_foreground)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = stringResource(R.string.account_panel_default_avatar),
                            modifier = Modifier.size(32.dp),
                            tint = if (anyLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (anyLoggedIn) displayName else notLoggedInText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                // AnimeTrack 服务器账号条目（排第一）
                AccountServiceRow(
                    title = "AnimeTrack",
                    subtitle = if (userLoggedIn) (userUsername ?: connectedText) else stringResource(R.string.account_panel_login_to_sync),
                    icon = Icons.Rounded.AccountCircle,
                    isConnected = userLoggedIn,
                    onClick = {
                        if (userLoggedIn) {
                            showUserActions = true
                        } else {
                            onDismiss()
                            onNavigateUserLogin()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bilibili 条目
                AccountServiceRow(
                    title = "Bilibili",
                    subtitle = if (bilibiliLoggedIn) (bilibiliNickname ?: connectedText) else stringResource(R.string.account_panel_bind_bilibili),
                    icon = Icons.AutoMirrored.Rounded.Login,
                    isConnected = bilibiliLoggedIn,
                    onClick = {
                        if (bilibiliLoggedIn) {
                            showBilibiliActions = true
                        } else {
                            onDismiss()
                            onNavigateBilibiliLogin()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bangumi 条目
                AccountServiceRow(
                    title = "Bangumi",
                    subtitle = if (bangumiLoggedIn) (bangumiNickname ?: connectedText) else stringResource(R.string.account_panel_bind_bangumi),
                    icon = Icons.Rounded.Person,
                    isConnected = bangumiLoggedIn,
                    onClick = {
                        if (bangumiLoggedIn) {
                            showBangumiActions = true
                        } else {
                            onDismiss()
                            onNavigateBangumiLogin()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 自定义头像按钮
                OutlinedButton(
                    onClick = { showAvatarActions = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = SquircleShape(12.dp)
                ) {
                    Text(
                        if (customAvatarUri != null) stringResource(R.string.account_panel_change_avatar) else stringResource(R.string.account_panel_custom_avatar),
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun AccountServiceRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(SquircleShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // 状态小点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isConnected) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
