package com.aiexile.animetrack.ui.login

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun ProfileDialog(
    onDismiss: () -> Unit,
    onNavigateToSync: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val authManager = remember { AppContainer.getAuthManager() }
    val userNickname by authManager.userNickname.collectAsState(initial = null)
    val userBangumiId by authManager.userBangumiId.collectAsState(initial = null)
    val userAvatar by authManager.userAvatar.collectAsState(initial = null)
    val customAvatarUri by authManager.customAvatarUri.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showLogoutConfirm by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                authManager.saveCustomAvatarUri(uri.toString())
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出 Bangumi 账号吗？退出后将无法同步数据。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    scope.launch { authManager.logout() }
                    onLogout()
                    onDismiss()
                }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
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
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        )
                        .clickable { pickImageLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val displayAvatar = customAvatarUri ?: userAvatar
                    if (displayAvatar != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(displayAvatar.toUri())
                                .build(),
                            contentDescription = "头像",
                            modifier = Modifier
                                .fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = R.drawable.ic_launcher_foreground),
                            fallback = painterResource(id = R.drawable.ic_launcher_foreground)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "默认头像",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = userNickname ?: "未知用户",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (userBangumiId != null) "Bangumi ID: ${userBangumiId}" else "Bangumi ID: --",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                FilledTonalButton(
                    onClick = onNavigateToSync,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("同步", fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("退出登录", fontSize = 15.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
