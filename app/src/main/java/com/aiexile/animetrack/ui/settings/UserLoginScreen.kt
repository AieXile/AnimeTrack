package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.auth.DeviceInfo
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.ChangeEmailRequest
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.auth.UserAuthManager
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
import com.aiexile.animetrack.push.PushRegistrationHelper
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.settings.login.AccountActionsSection
import com.aiexile.animetrack.ui.settings.login.AccountProfileSection
import com.aiexile.animetrack.ui.settings.login.ChangeEmailDialogSection
import com.aiexile.animetrack.ui.settings.login.ChangePasswordDialogSection
import com.aiexile.animetrack.ui.settings.login.DeviceManageSection
import com.aiexile.animetrack.ui.settings.login.DevicePickerSection
import com.aiexile.animetrack.ui.settings.login.LoginFormSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    onNavigateEmailBind: (bindToken: String) -> Unit,
    userAuthManager: UserAuthManager,
    animeRepository: AnimeRepository
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isLoggedIn by userAuthManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val tokenExpired by userAuthManager.tokenExpired.collectAsStateWithLifecycle(initialValue = false)
    val userId by userAuthManager.userId.collectAsStateWithLifecycle(initialValue = null)
    val username by userAuthManager.username.collectAsStateWithLifecycle(initialValue = null)
    val email by userAuthManager.email.collectAsStateWithLifecycle(initialValue = null)
    val createdAt by userAuthManager.createdAt.collectAsStateWithLifecycle(initialValue = null)
    val avatar by userAuthManager.avatar.collectAsStateWithLifecycle(initialValue = null)
    val userLevel by userAuthManager.userLevel.collectAsStateWithLifecycle(initialValue = null)

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
            } catch (e: Exception) {
                // 静默失败，个人界面数量显示保持上次结果
                AppLogManager.e("UserLogin", "获取登录设备列表失败", e)
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

    // 进入个人页时静默刷新用户资料（等级/头像/用户名等），失败不提示、不阻塞 UI
    LaunchedEffect(isLoggedIn && !tokenExpired) {
        if (isLoggedIn && !tokenExpired) {
            val token = userAuthManager.getCachedAccessToken() ?: return@LaunchedEffect
            try {
                val profileResponse = RetrofitClient.userAuthApi.getProfile("Bearer $token")
                val profileUser = profileResponse.user
                if (profileResponse.success && profileUser != null) {
                    userAuthManager.saveUserProfile(
                        username = profileUser.username,
                        email = profileUser.email,
                        createdAt = profileUser.createdAt,
                        avatar = profileUser.avatar,
                        level = profileUser.level
                    )
                }
            } catch (e: Exception) {
                // 静默失败：保留本地缓存资料
                AppLogManager.e("UserLogin", "刷新用户资料失败", e)
            }
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
                AppLogManager.e("UserLogin", "发送修改密码验证码失败", e)
                withContext(Dispatchers.Main) {
                    changePasswordError = e.serverMessage()
                        ?: context.getString(R.string.verification_code_send_failed)
                }
            } catch (e: Exception) {
                AppLogManager.e("UserLogin", "发送修改密码验证码失败", e)
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
                AppLogManager.e("UserLogin", "发送更换邮箱验证码失败", e)
                withContext(Dispatchers.Main) {
                    changeEmailError = e.serverMessage()
                        ?: context.getString(R.string.verification_code_send_failed)
                }
            } catch (e: Exception) {
                AppLogManager.e("UserLogin", "发送更换邮箱验证码失败", e)
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
                    AppLogManager.e("UserLogin", "上传头像失败", e)
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
    suspend fun processAuthSuccess(accessToken: String, refreshToken: String, userId: Int, uname: String, uemail: String?, ucreatedAt: String?, uavatar: String?, ulevel: Int? = null) {
        userAuthManager.saveLogin(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            username = uname,
            email = uemail,
            createdAt = ucreatedAt,
            avatar = uavatar,
            level = ulevel
        )
        // 获取完整用户信息（含 created_at / level）
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
                    avatar = profileUser.avatar,
                    level = profileUser.level
                )
            }
        } catch (e: Exception) {
            // 获取 profile 失败不影响登录
            AppLogManager.w("UserLogin", "登录后获取用户资料失败", e)
        }
        // 登录成功后上报极光推送 registrationId
        try {
            PushRegistrationHelper.reportRegistrationIdIfNeeded(context)
        } catch (e: Exception) {
            AppLogManager.w("UserLogin", "登录后上报推送 registrationId 失败", e)
        }
        // 登录成功后拉取后端订阅列表，同步到本地数据库
        // 使用应用级协程，避免登录后 UI 切换导致同步被取消
        try {
            animeRepository.triggerSyncSubscriptionsFromServer()
        } catch (e: Exception) {
            AppLogManager.w("UserLogin", "登录后触发订阅同步失败（非致命）", e)
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
                        uavatar = response.user.avatar,
                        ulevel = response.user.level
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = response.message ?: context.getString(R.string.user_login_login_failed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                AppLogManager.w("UserLogin", "登录失败: HTTP ${e.code()}", e)
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
                AppLogManager.e("UserLogin", "登录请求异常（网络不可达/解析失败）", e)
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
                        uavatar = response.user.avatar,
                        ulevel = response.user.level
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
                AppLogManager.w("UserLogin", "恢复注销账号失败: HTTP ${e.code()}", e)
                withContext(Dispatchers.Main) {
                    errorMessage = e.serverMessage() ?: context.getString(R.string.user_login_login_failed)
                }
            } catch (e: Exception) {
                AppLogManager.e("UserLogin", "恢复注销账号请求异常", e)
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

    AppScaffold(
        title = stringResource(R.string.user_login_title),
        onBack = onBack
    ) { chrome ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                // 玻璃档内容视口全高，顶/底内边距由滚动内容自垫
                .padding(chrome.content),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoggedIn && !tokenExpired) {
                AccountProfileSection(
                    avatar = avatar,
                    isUploadingAvatar = isUploadingAvatar,
                    avatarError = avatarError,
                    username = username,
                    userId = userId,
                    email = email,
                    createdAt = createdAt,
                    userLevel = userLevel,
                    onAvatarClick = { pickImageLauncher.launch("image/*") },
                    animeRepository = animeRepository
                )
                AccountActionsSection(
                    deviceCount = deviceCount,
                    isLoggingOut = isLoggingOut,
                    onManageDevices = {
                        showDeviceManageDialog = true
                        refreshDevices()
                    },
                    onChangePassword = {
                        showChangePasswordDialog = true
                        oldPassword = ""
                        newPassword = ""
                        confirmNewPassword = ""
                        changePwdCode = ""
                        changePasswordError = null
                    },
                    onChangeEmail = {
                        showChangeEmailDialog = true
                        changeEmailPassword = ""
                        changeEmailNewEmail = ""
                        changeEmailCode = ""
                        changeEmailError = null
                    },
                    onLogout = logout@{
                        if (isLoggingOut) return@logout
                        isLoggingOut = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val refreshToken = userAuthManager.getCachedRefreshToken()
                                if (refreshToken != null) {
                                    try {
                                        RetrofitClient.userAuthApi.logout(
                                            UserAuthLogoutRequest(refreshToken = refreshToken)
                                        )
                                    } catch (e: Exception) {
                                        // 后端 logout 失败也继续本地清除
                                        AppLogManager.w("UserLogin", "服务端登出失败（本地仍继续清理）", e)
                                    }
                                }
                                userAuthManager.logout()
                            } catch (e: Exception) {
                                AppLogManager.e("UserLogin", "登出流程异常（已强制本地清理）", e)
                                userAuthManager.logout()
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoggingOut = false
                                }
                            }
                        }
                    },
                    onDeleteAccount = { showDeleteAccount = true }
                )
            } else {
                LoginFormSection(
                    tokenExpired = tokenExpired,
                    username = inputUsername,
                    onUsernameChange = { inputUsername = it },
                    password = inputPassword,
                    onPasswordChange = { inputPassword = it },
                    errorMessage = errorMessage,
                    isLoading = isLoading,
                    onSubmit = { performLogin() },
                    onNavigateRegister = onNavigateRegister,
                    onNavigateForgotPassword = onNavigateForgotPassword
                )
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
                            animeRepository.deleteAllAnimes()
                        }
                    } catch (e: Exception) {
                        AppLogManager.w("UserLogin", "注销账号后清理本地数据失败（非致命）", e)
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

    ChangePasswordDialogSection(
        visible = showChangePasswordDialog,
        oldPassword = oldPassword,
        onOldPasswordChange = { oldPassword = it },
        newPassword = newPassword,
        onNewPasswordChange = { newPassword = it },
        confirmNewPassword = confirmNewPassword,
        onConfirmNewPasswordChange = { confirmNewPassword = it },
        code = changePwdCode,
        onCodeChange = { changePwdCode = it },
        onSendCode = { sendChangePasswordCode() },
        isSendingCode = isSendingChangePwdCode,
        errorMessage = changePasswordError,
        isChanging = isChangingPassword,
        onConfirm = changePassword@{
            // 本地校验
            if (oldPassword.isBlank()) {
                changePasswordError = context.getString(R.string.user_login_enter_old_password)
                return@changePassword
            }
            if (newPassword.length < 6) {
                changePasswordError = context.getString(R.string.user_login_password_too_short)
                return@changePassword
            }
            if (newPassword != confirmNewPassword) {
                changePasswordError = context.getString(R.string.user_login_password_mismatch)
                return@changePassword
            }
            if (newPassword == oldPassword) {
                changePasswordError = context.getString(R.string.user_login_password_same_as_old)
                return@changePassword
            }
            if (changePwdCode.isBlank()) {
                changePasswordError = context.getString(R.string.verification_code_required)
                return@changePassword
            }

            if (isChangingPassword) return@changePassword
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
                    AppLogManager.w("UserLogin", "修改密码失败: HTTP ${e.code()}", e)
                    withContext(Dispatchers.Main) {
                        changePasswordError = e.serverMessage() ?: context.getString(R.string.user_login_change_failed)
                        isChangingPassword = false
                    }
                } catch (e: Exception) {
                    AppLogManager.e("UserLogin", "修改密码请求异常", e)
                    withContext(Dispatchers.Main) {
                        changePasswordError = context.getString(R.string.user_login_network_error)
                        isChangingPassword = false
                    }
                }
            }
        },
        onDismiss = { showChangePasswordDialog = false }
    )

    ChangeEmailDialogSection(
        visible = showChangeEmailDialog,
        password = changeEmailPassword,
        onPasswordChange = { changeEmailPassword = it },
        newEmail = changeEmailNewEmail,
        onNewEmailChange = { changeEmailNewEmail = it },
        code = changeEmailCode,
        onCodeChange = { changeEmailCode = it },
        onSendCode = { sendChangeEmailCode() },
        isSendingCode = isSendingChangeEmailCode,
        errorMessage = changeEmailError,
        isChanging = isChangingEmail,
        onConfirm = changeEmail@{
            // 本地校验
            if (changeEmailPassword.isBlank()) {
                changeEmailError = context.getString(R.string.user_login_enter_password)
                return@changeEmail
            }
            if (changeEmailNewEmail.trim().isEmpty()) {
                changeEmailError = context.getString(R.string.user_login_enter_new_email)
                return@changeEmail
            }
            if (changeEmailCode.isBlank()) {
                changeEmailError = context.getString(R.string.verification_code_required)
                return@changeEmail
            }

            if (isChangingEmail) return@changeEmail
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
                    AppLogManager.w("UserLogin", "更换邮箱失败: HTTP ${e.code()}", e)
                    withContext(Dispatchers.Main) {
                        changeEmailError = e.serverMessage() ?: context.getString(R.string.user_login_change_failed)
                        isChangingEmail = false
                    }
                } catch (e: Exception) {
                    AppLogManager.e("UserLogin", "更换邮箱请求异常", e)
                    withContext(Dispatchers.Main) {
                        changeEmailError = context.getString(R.string.user_login_network_error)
                        isChangingEmail = false
                    }
                }
            }
        },
        onDismiss = { showChangeEmailDialog = false }
    )

    DevicePickerSection(
        visible = showDevicePicker,
        devices = pickerDevices,
        onConfirm = { kickDeviceIds ->
            showDevicePicker = false
            if (kickDeviceIds.isNotEmpty()) {
                performLogin(kickDeviceIds)
            }
        },
        onDismiss = { showDevicePicker = false }
    )

    DeviceManageSection(
        visible = showDeviceManageDialog,
        devices = manageDevices,
        revokingSessionId = revokingSessionId,
        onRevoke = revoke@{ device ->
            if (revokingSessionId != null) return@revoke
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
                    AppLogManager.w("UserLogin", "撤销登录设备失败", e)
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
