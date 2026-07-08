package com.aiexile.animetrack.ui.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.aiexile.animetrack.data.network.ChangePasswordRequest
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.UserAuthLoginRequest
import com.aiexile.animetrack.data.network.UserAuthLogoutRequest
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.push.PushRegistrationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserLoginScreen(
    onBack: () -> Unit,
    onNavigateRegister: () -> Unit
) {
    val userAuthManager = remember { AppContainer.getUserAuthManager() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isLoggedIn by userAuthManager.isLoggedIn.collectAsState(initial = false)
    val username by userAuthManager.username.collectAsState(initial = null)
    val email by userAuthManager.email.collectAsState(initial = null)
    val createdAt by userAuthManager.createdAt.collectAsState(initial = null)
    val avatar by userAuthManager.avatar.collectAsState(initial = null)

    var inputUsername by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 修改密码 Dialog 状态
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var isChangingPassword by remember { mutableStateOf(false) }
    var changePasswordError by remember { mutableStateOf<String?>(null) }

    // 上传头像状态
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            avatarError = null
            isUploadingAvatar = true
            scope.launch(Dispatchers.IO) {
                try {
                    // 将 Uri 内容拷贝到临时文件
                    val mimeType = context.contentResolver.getType(uri) ?: "image/*"
                    val suffix = when (mimeType) {
                        "image/jpeg" -> ".jpg"
                        "image/png" -> ".png"
                        "image/gif" -> ".gif"
                        "image/webp" -> ".webp"
                        else -> ".img"
                    }
                    val tempFile = File.createTempFile("upload_avatar", suffix, context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            avatarError = "无法读取图片"
                            isUploadingAvatar = false
                        }
                        return@launch
                    }

                    // 大小校验（≤2MB）
                    if (tempFile.length() > 2 * 1024 * 1024) {
                        tempFile.delete()
                        withContext(Dispatchers.Main) {
                            avatarError = "图片大小不能超过 2MB"
                            isUploadingAvatar = false
                        }
                        return@launch
                    }

                    val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                    val multipartBody = MultipartBody.Part.createFormData(
                        name = "avatar",
                        filename = tempFile.name,
                        body = requestFile
                    )
                    val response = RetrofitClient.userAuthApi.uploadAvatar(multipartBody)
                    tempFile.delete()

                    if (response.success && response.avatar != null) {
                        userAuthManager.updateAvatar(response.avatar)
                    } else {
                        withContext(Dispatchers.Main) {
                            avatarError = response.message ?: "上传失败"
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        avatarError = "网络错误，上传失败"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AnimeTrack 账号",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoggedIn) {
                // 已登录状态 - 头像区域（可点击上传）
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(enabled = !isUploadingAvatar) {
                            pickImageLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = avatar?.let { "https://www.aiexile.top$it" }
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                avatarError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = username ?: "用户",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = email ?: "未设置邮箱",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (createdAt != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "注册时间: $createdAt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已登录",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = {
                        showChangePasswordDialog = true
                        oldPassword = ""
                        newPassword = ""
                        confirmNewPassword = ""
                        changePasswordError = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("修改密码")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (isLoggingOut) return@OutlinedButton
                        isLoggingOut = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val refreshToken = userAuthManager.getCachedRefreshToken()
                                if (refreshToken != null) {
                                    try {
                                        RetrofitClient.userAuthApi.logout(
                                            UserAuthLogoutRequest(refreshToken = refreshToken)
                                        )
                                    } catch (_: Exception) {
                                        // 后端 logout 失败也继续本地清除
                                    }
                                }
                                userAuthManager.logout()
                            } catch (_: Exception) {
                                userAuthManager.logout()
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoggingOut = false
                                }
                            }
                        }
                    },
                    enabled = !isLoggingOut,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("退出登录")
                    }
                }
            } else {
                // 未登录状态
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = inputUsername,
                    onValueChange = { inputUsername = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputPassword,
                    onValueChange = { inputPassword = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (isLoading) return@Button
                        isLoading = true
                        errorMessage = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val response = RetrofitClient.userAuthApi.login(
                                    UserAuthLoginRequest(
                                        username = inputUsername.trim(),
                                        password = inputPassword
                                    )
                                )
                                if (response.success && response.accessToken != null && response.refreshToken != null && response.user != null) {
                                    val accessToken = response.accessToken
                                    val refreshToken = response.refreshToken
                                    val user = response.user
                                    userAuthManager.saveLogin(
                                        accessToken = accessToken,
                                        refreshToken = refreshToken,
                                        userId = user.id,
                                        username = user.username,
                                        email = user.email,
                                        createdAt = user.createdAt,
                                        avatar = user.avatar
                                    )
                                    // 获取完整用户信息（含 created_at）
                                    try {
                                        val profileResponse = RetrofitClient.userAuthApi.getProfile("Bearer $accessToken")
                                        if (profileResponse.success && profileResponse.user != null) {
                                            val profileUser = profileResponse.user
                                            userAuthManager.saveLogin(
                                                accessToken = accessToken,
                                                refreshToken = refreshToken,
                                                userId = profileUser.id,
                                                username = profileUser.username,
                                                email = profileUser.email,
                                                createdAt = profileUser.createdAt,
                                                avatar = profileUser.avatar
                                            )
                                        }
                                    } catch (_: Exception) {
                                        // 获取 profile 失败不影响登录
                                    }
                                    // 登录成功后上报极光推送 registrationId
                                    try {
                                        PushRegistrationHelper.reportRegistrationIdIfNeeded(context)
                                    } catch (_: Exception) { }
                                    // 登录成功后拉取后端订阅列表，同步到本地数据库
                                    // 使用应用级协程，避免登录后 UI 切换导致同步被取消
                                    try {
                                        com.aiexile.animetrack.di.AppContainer.getAnimeRepository()
                                            .triggerSyncSubscriptionsFromServer()
                                    } catch (e: Exception) {
                                        android.util.Log.w("UserLogin", "Trigger sync subscriptions failed (non-fatal)", e)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = response.message ?: "登录失败"
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "网络错误，请检查网络连接"
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = !isLoading && inputUsername.isNotBlank() && inputPassword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("登录")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onNavigateRegister) {
                    Text("没有账号？去注册")
                }
            }
        }
    }

    // 修改密码 Dialog
    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isChangingPassword) showChangePasswordDialog = false
            },
            title = {
                Text(
                    text = "修改密码",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text("旧密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChangingPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("新密码") },
                        placeholder = { Text("至少6位") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChangingPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        label = { Text("确认新密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChangingPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                    changePasswordError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 本地校验
                        if (oldPassword.isBlank()) {
                            changePasswordError = "请输入旧密码"
                            return@TextButton
                        }
                        if (newPassword.length < 6) {
                            changePasswordError = "新密码长度不能少于 6 位"
                            return@TextButton
                        }
                        if (newPassword != confirmNewPassword) {
                            changePasswordError = "两次密码不一致"
                            return@TextButton
                        }
                        if (newPassword == oldPassword) {
                            changePasswordError = "新密码不能与旧密码相同"
                            return@TextButton
                        }

                        if (isChangingPassword) return@TextButton
                        isChangingPassword = true
                        changePasswordError = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val response = RetrofitClient.userAuthApi.changePassword(
                                    ChangePasswordRequest(
                                        oldPassword = oldPassword,
                                        newPassword = newPassword
                                    )
                                )
                                if (response.success) {
                                    withContext(Dispatchers.Main) {
                                        isChangingPassword = false
                                        showChangePasswordDialog = false
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        changePasswordError = response.message ?: "修改失败"
                                        isChangingPassword = false
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    changePasswordError = "网络错误，请检查网络连接"
                                    isChangingPassword = false
                                }
                            }
                        }
                    },
                    enabled = !isChangingPassword
                ) {
                    if (isChangingPassword) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("确认修改")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isChangingPassword) showChangePasswordDialog = false
                    },
                    enabled = !isChangingPassword
                ) {
                    Text("取消")
                }
            }
        )
    }
}
