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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun AccountPanelDialog(
    onDismiss: () -> Unit,
    onNavigateBilibiliLogin: () -> Unit,
    onNavigateBangumiLogin: () -> Unit
) {
    val authManager = remember { AppContainer.getAuthManager() }
    val bilibiliAuthManager = remember { AppContainer.getBilibiliAuthManager() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val bangumiLoggedIn by authManager.isLoggedIn.collectAsState(initial = false)
    val bangumiNickname by authManager.userNickname.collectAsState(initial = null)
    val bangumiAvatar by authManager.userAvatar.collectAsState(initial = null)
    val customAvatarUri by authManager.customAvatarUri.collectAsState(initial = null)

    val bilibiliLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsState(initial = false)
    val bilibiliNickname by bilibiliAuthManager.userNickname.collectAsState(initial = null)
    val bilibiliAvatar by bilibiliAuthManager.userAvatar.collectAsState(initial = null)

    val anyLoggedIn = bangumiLoggedIn || bilibiliLoggedIn
    val displayAvatar = customAvatarUri ?: bilibiliAvatar ?: bangumiAvatar
    val displayName = bilibiliNickname ?: bangumiNickname ?: "未登录"

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

    // Bilibili 操作弹窗
    if (showBilibiliActions) {
        AlertDialog(
            onDismissRequest = { showBilibiliActions = false },
            title = { Text("Bilibili 账号") },
            text = { Text("已登录为 ${bilibiliNickname ?: "未知用户"}") },
            confirmButton = {
                TextButton(onClick = {
                    showBilibiliActions = false
                    scope.launch { bilibiliAuthManager.logout() }
                    onDismiss()
                    onNavigateBilibiliLogin()
                }) { Text("重新登录") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showBilibiliActions = false }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        showBilibiliActions = false
                        scope.launch { bilibiliAuthManager.logout() }
                    }) {
                        Text("解除绑定", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // Bangumi 操作弹窗
    if (showBangumiActions) {
        AlertDialog(
            onDismissRequest = { showBangumiActions = false },
            title = { Text("Bangumi 账号") },
            text = { Text("已登录为 ${bangumiNickname ?: "未知用户"}") },
            confirmButton = {
                TextButton(onClick = {
                    showBangumiActions = false
                    scope.launch { authManager.logout() }
                    onDismiss()
                    onNavigateBangumiLogin()
                }) { Text("重新登录") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showBangumiActions = false }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        showBangumiActions = false
                        scope.launch { authManager.logout() }
                    }) {
                        Text("注销登录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // 头像操作弹窗
    if (showAvatarActions) {
        AlertDialog(
            onDismissRequest = { showAvatarActions = false },
            title = { Text("更换头像") },
            text = {
                Column {
                    if (customAvatarUri != null) {
                        TextButton(onClick = {
                            showAvatarActions = false
                            scope.launch { authManager.saveCustomAvatarUri(null) }
                        }) {
                            Text("恢复默认头像")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAvatarActions = false
                    pickImageLauncher.launch("image/*")
                }) { Text("选择图片") }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarActions = false }) { Text("取消") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
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
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = R.drawable.ic_launcher_foreground),
                            fallback = painterResource(id = R.drawable.ic_launcher_foreground)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "默认头像",
                            modifier = Modifier.size(32.dp),
                            tint = if (anyLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (anyLoggedIn) displayName else "未登录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Bilibili 条目
                AccountServiceRow(
                    title = "Bilibili",
                    subtitle = if (bilibiliLoggedIn) (bilibiliNickname ?: "已连接") else "点击绑定B站账号",
                    icon = Icons.AutoMirrored.Filled.Login,
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
                    subtitle = if (bangumiLoggedIn) (bangumiNickname ?: "已连接") else "点击绑定Bangumi",
                    icon = Icons.Default.Person,
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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (customAvatarUri != null) "更换头像" else "自定义头像",
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
            .clip(RoundedCornerShape(16.dp))
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
