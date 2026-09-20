package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.background
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.StatusCount
import com.aiexile.animetrack.data.auth.DeviceInfo
import com.aiexile.animetrack.data.network.ChangeEmailRequest
import com.aiexile.animetrack.data.network.ChangePasswordRequest
import com.aiexile.animetrack.data.network.DeviceSession
import com.aiexile.animetrack.data.network.EmailCodePurpose
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.RevokeDeviceRequest
import com.aiexile.animetrack.data.network.SendCodeRequest
import com.aiexile.animetrack.data.network.UserAuthLoginRequest
import com.aiexile.animetrack.data.network.UserAuthLogoutRequest
import com.aiexile.animetrack.data.network.serverErrorJson
import com.aiexile.animetrack.data.network.serverMessage
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.push.PushRegistrationHelper
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.VerificationCodeField
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserLoginScreen(
    onBack: () -> Unit,
    onNavigateRegister: () -> Unit,
    onNavigateForgotPassword: () -> Unit,
    onNavigateEmailBind: (bindToken: String) -> Unit
) {
    val userAuthManager = remember { AppContainer.getUserAuthManager() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isLoggedIn by userAuthManager.isLoggedIn.collectAsState(initial = false)
    val tokenExpired by userAuthManager.tokenExpired.collectAsState(initial = false)
    val userId by userAuthManager.userId.collectAsState(initial = null)
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
    var changePwdCode by remember { mutableStateOf("") }
    var isSendingChangePwdCode by remember { mutableStateOf(false) }
    var isChangingPassword by remember { mutableStateOf(false) }
    var changePasswordError by remember { mutableStateOf<String?>(null) }

    // 更换邮箱 Dialog 状态
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var changeEmailPassword by remember { mutableStateOf("") }
    var changeEmailNewEmail by remember { mutableStateOf("") }
    var changeEmailCode by remember { mutableStateOf("") }
    var isSendingChangeEmailCode by remember { mutableStateOf(false) }
    var isChangingEmail by remember { mutableStateOf(false) }
    var changeEmailError by remember { mutableStateOf<String?>(null) }

    // 多端登录：登录超限时选择要下线的设备
    var showDevicePicker by remember { mutableStateOf(false) }
    var pickerDevices by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }

    // 多端登录：当前登录设备列表与数量（个人界面展示 + 管理弹窗）
    var deviceCount by remember { mutableStateOf<Int?>(null) }
    var manageDevices by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }
    var showDeviceManageDialog by remember { mutableStateOf(false) }
    var revokingSessionId by remember { mutableStateOf<String?>(null) }

    // 注销账号 / 恢复账号
    var showDeleteAccount by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePurgeAt by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    /** 拉取当前登录设备列表（登录后 / 下线设备后刷新） */
    fun refreshDevices() {
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.getDevices()
                if (response.success) {
                    withContext(Dispatchers.Main) {
                        manageDevices = response.devices
                        deviceCount = response.devices.size
                    }
                }
            } catch (_: Exception) {
                // 静默失败，个人界面数量显示保持上次结果
            }
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            refreshDevices()
        } else {
            deviceCount = null
            manageDevices = emptyList()
        }
    }

    /** 修改密码：向当前绑定邮箱发送验证码 */
    fun sendChangePasswordCode() {
        if (isSendingChangePwdCode) return
        isSendingChangePwdCode = true
        changePasswordError = null
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.sendCode(
                    SendCodeRequest(email = null, purpose = EmailCodePurpose.CHANGE_PASSWORD)
                )
                if (!response.success) {
                    withContext(Dispatchers.Main) {
                        changePasswordError = response.message
                            ?: context.getString(R.string.verification_code_send_failed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    changePasswordError = e.serverMessage()
                        ?: context.getString(R.string.verification_code_send_failed)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    changePasswordError = context.getString(R.string.user_login_network_error)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSendingChangePwdCode = false
                }
            }
        }
    }

    /** 更换邮箱：向新邮箱发送验证码 */
    fun sendChangeEmailCode() {
        val newEmail = changeEmailNewEmail.trim()
        if (newEmail.isEmpty()) {
            changeEmailError = context.getString(R.string.user_login_enter_new_email)
            return
        }
        if (isSendingChangeEmailCode) return
        isSendingChangeEmailCode = true
        changeEmailError = null
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.sendCode(
                    SendCodeRequest(email = newEmail, purpose = EmailCodePurpose.CHANGE_EMAIL)
                )
                if (!response.success) {
                    withContext(Dispatchers.Main) {
                        changeEmailError = response.message
                            ?: context.getString(R.string.verification_code_send_failed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    changeEmailError = e.serverMessage()
                        ?: context.getString(R.string.verification_code_send_failed)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    changeEmailError = context.getString(R.string.user_login_network_error)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSendingChangeEmailCode = false
                }
            }
        }
    }

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
                            avatarError = context.getString(R.string.user_login_read_image_failed)
                            isUploadingAvatar = false
                        }
                        return@launch
                    }

                    // 大小校验（≤2MB）
                    if (tempFile.length() > 2 * 1024 * 1024) {
                        tempFile.delete()
                        withContext(Dispatchers.Main) {
                            avatarError = context.getString(R.string.user_login_image_too_large)
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
                            avatarError = response.message ?: context.getString(R.string.user_login_upload_failed)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        avatarError = context.getString(R.string.user_login_network_upload_failed)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                    }
                }
            }
        }
    }

    /**
     * 认证成功后的统一处理（login / restore-account 共用）：
     * 保存登录态 → 补全 profile → 上报推送 ID → 触发订阅同步。
     * 必须在 IO 协程中调用。
     */
    suspend fun processAuthSuccess(accessToken: String, refreshToken: String, userId: Int, uname: String, uemail: String?, ucreatedAt: String?, uavatar: String?) {
        userAuthManager.saveLogin(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            username = uname,
            email = uemail,
            createdAt = ucreatedAt,
            avatar = uavatar
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
            AppContainer.getAnimeRepository().triggerSyncSubscriptionsFromServer()
        } catch (e: Exception) {
            android.util.Log.w("UserLogin", "Trigger sync subscriptions failed (non-fatal)", e)
        }
    }

    /**
     * 执行登录：携带设备信息创建多端登录会话；
     * 设备超限时服务端返回 deviceLimitReached + 设备列表，弹出选择框后
     * 携带所选 kickDeviceIds 重试。
     */
    fun performLogin(kickDeviceIds: List<String>? = null) {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.login(
                    UserAuthLoginRequest(
                        username = inputUsername.trim(),
                        password = inputPassword,
                        deviceId = DeviceInfo.getDeviceId(),
                        deviceName = DeviceInfo.deviceName,
                        platform = DeviceInfo.PLATFORM,
                        kickDeviceIds = kickDeviceIds
                    )
                )
                // 存量用户未绑定邮箱：跳转绑定邮箱页（bindToken 15 分钟有效）
                if (response.requireEmailBind == true && response.bindToken != null) {
                    val bindToken = response.bindToken
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        onNavigateEmailBind(bindToken)
                    }
                    return@launch
                }
                // 登录设备超限：弹出设备选择框，用户选择后重试
                if (response.deviceLimitReached == true && !response.devices.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        pickerDevices = response.devices
                        showDevicePicker = true
                        isLoading = false
                    }
                    return@launch
                }
                if (response.success && response.accessToken != null && response.refreshToken != null && response.user != null) {
                    processAuthSuccess(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        userId = response.user.id,
                        uname = response.user.username,
                        uemail = response.user.email,
                        ucreatedAt = response.user.createdAt,
                        uavatar = response.user.avatar
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = response.message ?: context.getString(R.string.user_login_login_failed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    // errorBody 只能读一次：一次解析后从 JsonObject 取所有字段
                    val errorJson = e.serverErrorJson()
                    val errorCode = errorJson?.get("code")?.takeIf { !it.isJsonNull }?.asString
                    if (errorCode == "DELETION_PENDING") {
                        // 账号处于注销宽限期：弹出恢复账号弹窗
                        restorePurgeAt = errorJson?.get("purgeAt")?.takeIf { !it.isJsonNull }?.asString
                        showRestoreDialog = true
                    } else {
                        errorMessage = errorJson?.get("message")?.takeIf { !it.isJsonNull }?.asString
                            ?: context.getString(R.string.user_login_login_failed)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = context.getString(R.string.user_login_network_error)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    /**
     * 恢复注销中的账号：用当前输入的用户名/密码调用 restore-account，
     * 验证通过即撤销注销并完成登录（设备处理与 login 一致）。
     */
    fun performRestore() {
        if (isRestoring) return
        isRestoring = true
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.restoreAccount(
                    UserAuthLoginRequest(
                        username = inputUsername.trim(),
                        password = inputPassword,
                        deviceId = DeviceInfo.getDeviceId(),
                        deviceName = DeviceInfo.deviceName,
                        platform = DeviceInfo.PLATFORM
                    )
                )
                if (response.success && response.accessToken != null && response.refreshToken != null && response.user != null) {
                    processAuthSuccess(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        userId = response.user.id,
                        uname = response.user.username,
                        uemail = response.user.email,
                        ucreatedAt = response.user.createdAt,
                        uavatar = response.user.avatar
                    )
                    withContext(Dispatchers.Main) {
                        showRestoreDialog = false
                    }
                } else if (response.deviceLimitReached == true) {
                    // 恢复成功但设备超限：提示用户走登录流程选择设备
                    withContext(Dispatchers.Main) {
                        showRestoreDialog = false
                        errorMessage = response.message ?: context.getString(R.string.user_login_login_failed)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = response.message ?: context.getString(R.string.user_login_login_failed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.serverMessage() ?: context.getString(R.string.user_login_login_failed)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = context.getString(R.string.user_login_network_error)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isRestoring = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.user_login_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppIconPainter(AppIcon.ARROW_BACK), contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoggedIn && !tokenExpired) {
                Spacer(modifier = Modifier.height(20.dp))
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
                            contentDescription = stringResource(R.string.user_login_avatar),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.ACCOUNT_CIRCLE),
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
                    text = username ?: stringResource(R.string.user_login_user_default),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 用户 ID + 内测用户标识（版本名包含 beta 时显示徽章）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    userId?.let { id ->
                        Text(
                            text = stringResource(R.string.user_login_user_id, id),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true)) {
                        if (userId != null) Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = SquircleShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.user_login_beta_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = email ?: stringResource(R.string.user_login_email_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (createdAt != null) {
                    // 注册时间显示到日（截取 yyyy-MM-dd 部分）
                    val joinedDate = createdAt!!.take(10)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.user_login_joined_date, joinedDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 追番统计栏：单容器四分栏（总追番/在看/计划/已看完）
                val statusCounts by remember {
                    AppContainer.getAnimeRepository().getStatusCounts()
                }.collectAsState(initial = emptyList())
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    shape = SquircleShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val countsMap = statusCounts.associate { it.status to it.count }
                    val watchingCount = countsMap[AnimeStatus.WATCHING] ?: 0
                    val plannedCount = countsMap[AnimeStatus.PLANNED] ?: 0
                    val completedCount = countsMap[AnimeStatus.COMPLETED] ?: 0
                    val totalCount = watchingCount + plannedCount + completedCount +
                        (countsMap[AnimeStatus.DROPPED] ?: 0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem(count = totalCount, label = stringResource(R.string.user_login_stat_total), isPrimary = false, modifier = Modifier.weight(1f))
                        StatDivider()
                        StatItem(count = watchingCount, label = stringResource(R.string.user_login_stat_watching), isPrimary = true, modifier = Modifier.weight(1f))
                        StatDivider()
                        StatItem(count = plannedCount, label = stringResource(R.string.user_login_stat_planned), isPrimary = false, modifier = Modifier.weight(1f))
                        StatDivider()
                        StatItem(count = completedCount, label = stringResource(R.string.user_login_stat_completed), isPrimary = false, modifier = Modifier.weight(1f))
                    }
                }
                // 账号安全：列表行
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = SquircleShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SecurityRow(
                            icon = { Icon(rememberAppIconPainter(AppIcon.DEVICES), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) },
                            label = stringResource(R.string.device_manage_title),
                            trailing = deviceCount?.let { stringResource(R.string.device_manage_count_format, it) },
                            onClick = {
                                showDeviceManageDialog = true
                                refreshDevices()
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 52.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        SecurityRow(
                            icon = { Icon(rememberAppIconPainter(AppIcon.LOCK), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) },
                            label = stringResource(R.string.user_login_change_password),
                            onClick = {
                                showChangePasswordDialog = true
                                oldPassword = ""
                                newPassword = ""
                                confirmNewPassword = ""
                                changePwdCode = ""
                                changePasswordError = null
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 52.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        SecurityRow(
                            icon = { Icon(rememberAppIconPainter(AppIcon.MAIL), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) },
                            label = stringResource(R.string.user_login_change_email),
                            onClick = {
                                showChangeEmailDialog = true
                                changeEmailPassword = ""
                                changeEmailNewEmail = ""
                                changeEmailCode = ""
                                changeEmailError = null
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
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
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(stringResource(R.string.user_login_logout))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 注销账号入口（危险操作，红色弱化展示）
                TextButton(
                    onClick = { showDeleteAccount = true },
                    enabled = !isLoggingOut
                ) {
                    Text(
                        text = stringResource(R.string.user_login_delete_account),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                // 未登录 / token 已失效（强制重新登录）
                if (tokenExpired) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.ERROR),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.token_expired_form_hint),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Spacer(modifier = Modifier.height(40.dp))
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ACCOUNT_CIRCLE),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = inputUsername,
                    onValueChange = { inputUsername = it },
                    label = { Text(stringResource(R.string.user_login_username)) },
                    singleLine = true,
                    shape = SquircleShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputPassword,
                    onValueChange = { inputPassword = it },
                    label = { Text(stringResource(R.string.user_login_password)) },
                    singleLine = true,
                    shape = SquircleShape(12.dp),
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
                    onClick = { performLogin() },
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
                        Text(stringResource(R.string.user_login_login))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onNavigateRegister) {
                    Text(stringResource(R.string.user_login_no_account_register))
                }
                TextButton(onClick = onNavigateForgotPassword) {
                    Text(
                        text = stringResource(R.string.user_login_forgot_password),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 注销账号流程弹窗（验证 → 确认 → 本地数据选择）
    if (showDeleteAccount) {
        DeleteAccountFlowDialogs(
            hasVerifiedEmail = !email.isNullOrBlank(),
            onDismiss = { showDeleteAccount = false },
            onDeletionConfirmed = { clearLocalData ->
                scope.launch(Dispatchers.IO) {
                    try {
                        if (clearLocalData) {
                            // 清空本机追番数据（Room anime 表）
                            AppContainer.getAnimeDatabase().animeDao().deleteAllAnimes()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("UserLogin", "Clear local data failed (non-fatal)", e)
                    } finally {
                        // 服务端已撤销全部会话，本地统一执行登出清理
                        userAuthManager.logout()
                        withContext(Dispatchers.Main) {
                            showDeleteAccount = false
                        }
                    }
                }
            }
        )
    }

    // 恢复账号弹窗（登录时服务端返回 DELETION_PENDING 触发）
    if (showRestoreDialog) {
        RestoreAccountDialog(
            purgeAt = restorePurgeAt,
            isRestoring = isRestoring,
            onRestore = { performRestore() },
            onDismiss = { showRestoreDialog = false }
        )
    }

    // 修改密码 Dialog
    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isChangingPassword) showChangePasswordDialog = false
            },
            shape = SquircleShape(24.dp),
            title = {
                Text(
                    text = stringResource(R.string.user_login_change_password),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text(stringResource(R.string.user_login_old_password)) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChangingPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.user_login_new_password)) },
                        placeholder = { Text(stringResource(R.string.user_login_password_min_hint)) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChangingPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        label = { Text(stringResource(R.string.user_login_confirm_new_password)) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChangingPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    VerificationCodeField(
                        code = changePwdCode,
                        onCodeChange = { changePwdCode = it },
                        onSendCode = { sendChangePasswordCode() },
                        isSending = isSendingChangePwdCode,
                        enabled = !isChangingPassword
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
                            changePasswordError = context.getString(R.string.user_login_enter_old_password)
                            return@TextButton
                        }
                        if (newPassword.length < 6) {
                            changePasswordError = context.getString(R.string.user_login_password_too_short)
                            return@TextButton
                        }
                        if (newPassword != confirmNewPassword) {
                            changePasswordError = context.getString(R.string.user_login_password_mismatch)
                            return@TextButton
                        }
                        if (newPassword == oldPassword) {
                            changePasswordError = context.getString(R.string.user_login_password_same_as_old)
                            return@TextButton
                        }
                        if (changePwdCode.isBlank()) {
                            changePasswordError = context.getString(R.string.verification_code_required)
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
                                        newPassword = newPassword,
                                        code = changePwdCode.trim()
                                    )
                                )
                                if (response.success) {
                                    withContext(Dispatchers.Main) {
                                        isChangingPassword = false
                                        showChangePasswordDialog = false
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        changePasswordError = response.message ?: context.getString(R.string.user_login_change_failed)
                                        isChangingPassword = false
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: HttpException) {
                                withContext(Dispatchers.Main) {
                                    changePasswordError = e.serverMessage() ?: context.getString(R.string.user_login_change_failed)
                                    isChangingPassword = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    changePasswordError = context.getString(R.string.user_login_network_error)
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
                        Text(stringResource(R.string.user_login_confirm_change))
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
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 更换邮箱 Dialog
    if (showChangeEmailDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isChangingEmail) showChangeEmailDialog = false
            },
            shape = SquircleShape(24.dp),
            title = {
                Text(
                    text = stringResource(R.string.user_login_change_email),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = changeEmailPassword,
                        onValueChange = { changeEmailPassword = it },
                        label = { Text(stringResource(R.string.user_login_current_password)) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChangingEmail,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = changeEmailNewEmail,
                        onValueChange = { changeEmailNewEmail = it },
                        label = { Text(stringResource(R.string.user_login_new_email)) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !isChangingEmail,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    VerificationCodeField(
                        code = changeEmailCode,
                        onCodeChange = { changeEmailCode = it },
                        onSendCode = { sendChangeEmailCode() },
                        isSending = isSendingChangeEmailCode,
                        enabled = !isChangingEmail
                    )
                    changeEmailError?.let { err ->
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
                        if (changeEmailPassword.isBlank()) {
                            changeEmailError = context.getString(R.string.user_login_enter_password)
                            return@TextButton
                        }
                        if (changeEmailNewEmail.trim().isEmpty()) {
                            changeEmailError = context.getString(R.string.user_login_enter_new_email)
                            return@TextButton
                        }
                        if (changeEmailCode.isBlank()) {
                            changeEmailError = context.getString(R.string.verification_code_required)
                            return@TextButton
                        }

                        if (isChangingEmail) return@TextButton
                        isChangingEmail = true
                        changeEmailError = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val response = RetrofitClient.userAuthApi.changeEmail(
                                    ChangeEmailRequest(
                                        password = changeEmailPassword,
                                        newEmail = changeEmailNewEmail.trim(),
                                        code = changeEmailCode.trim()
                                    )
                                )
                                if (response.success) {
                                    // 更新本地缓存的邮箱
                                    if (response.email != null) {
                                        userAuthManager.updateEmail(response.email)
                                    }
                                    withContext(Dispatchers.Main) {
                                        isChangingEmail = false
                                        showChangeEmailDialog = false
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        changeEmailError = response.message ?: context.getString(R.string.user_login_change_failed)
                                        isChangingEmail = false
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: HttpException) {
                                withContext(Dispatchers.Main) {
                                    changeEmailError = e.serverMessage() ?: context.getString(R.string.user_login_change_failed)
                                    isChangingEmail = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    changeEmailError = context.getString(R.string.user_login_network_error)
                                    isChangingEmail = false
                                }
                            }
                        }
                    },
                    enabled = !isChangingEmail
                ) {
                    if (isChangingEmail) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.user_login_confirm_change))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isChangingEmail) showChangeEmailDialog = false
                    },
                    enabled = !isChangingEmail
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 登录超限：选择要下线的设备后重试登录
    if (showDevicePicker) {
        DevicePickerDialog(
            devices = pickerDevices,
            onConfirm = { kickDeviceIds ->
                showDevicePicker = false
                if (kickDeviceIds.isNotEmpty()) {
                    performLogin(kickDeviceIds)
                }
            },
            onDismiss = { showDevicePicker = false }
        )
    }

    // 已登录：登录设备管理（查看当前设备并下线）
    if (showDeviceManageDialog) {
        DeviceManageDialog(
            devices = manageDevices,
            revokingSessionId = revokingSessionId,
            onRevoke = { device ->
                if (revokingSessionId != null) return@DeviceManageDialog
                revokingSessionId = device.sessionId
                scope.launch(Dispatchers.IO) {
                    var toastMessage: String? = null
                    try {
                        val response = RetrofitClient.userAuthApi.revokeDevice(
                            RevokeDeviceRequest(sessionId = device.sessionId)
                        )
                        if (response.success) {
                            // 明确的成功反馈：被下线设备名 + 已下线提示
                            toastMessage = context.getString(
                                R.string.device_manage_revoke_success,
                                device.deviceName
                                    ?: context.getString(R.string.device_manage_unknown_device)
                            )
                            refreshDevices()
                        } else {
                            toastMessage = response.message
                                ?: context.getString(R.string.device_manage_revoke_failed)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toastMessage = context.getString(R.string.device_manage_revoke_failed)
                    } finally {
                        withContext(Dispatchers.Main) {
                            revokingSessionId = null
                            toastMessage?.let {
                                android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            onDismiss = { showDeviceManageDialog = false }
        )
    }
}

/** 追番统计栏的单项（数字 + 标签，在看项用主题色强调） */
@Composable
private fun StatItem(
    count: Int,
    label: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 追番统计栏的分隔细线 */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .size(width = 0.5.dp, height = 28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** 账号安全列表行（图标 + 标题 + 可选右侧文本 + 右箭头） */
@Composable
private fun SecurityRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            painter = rememberAppIconPainter(AppIcon.KEYBOARD_ARROW_RIGHT),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
