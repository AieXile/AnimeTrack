package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.BindEmailRequest
import com.aiexile.animetrack.data.network.EmailCodePurpose
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.SendCodeRequest
import com.aiexile.animetrack.data.network.serverMessage
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.push.PushRegistrationHelper
import com.aiexile.animetrack.ui.components.VerificationCodeField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import androidx.compose.ui.res.painterResource

/**
 * 绑定邮箱页（登录/注册流程触发）：登录时服务端返回 requireEmailBind + bindToken 后跳转此页。
 * 已登录存量用户的强制绑定走 EmailBindDialog（全局检测触发），不经过此页。
 * 绑定成功后服务端直接签发正式登录凭证，页面保存凭证并返回。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailBindScreen(
    bindToken: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userAuthManager = remember { AppContainer.getUserAuthManager() }

    var inputEmail by remember { mutableStateOf("") }
    var inputCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSendingCode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isMessageError by remember { mutableStateOf(true) }

    /** 发送验证码（60 秒倒计时由 VerificationCodeField 管理） */
    fun sendVerificationCode() {
        val email = inputEmail.trim()
        if (email.isEmpty()) {
            message = context.getString(R.string.email_bind_enter_email)
            isMessageError = true
            return
        }
        if (isSendingCode) return
        isSendingCode = true
        message = null
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.sendCode(
                    SendCodeRequest(email = email, purpose = EmailCodePurpose.BIND)
                )
                withContext(Dispatchers.Main) {
                    message = response.message ?: context.getString(R.string.verification_code_send_failed)
                    isMessageError = !response.success
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    message = e.serverMessage() ?: context.getString(R.string.verification_code_send_failed)
                    isMessageError = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    message = context.getString(R.string.user_login_network_error)
                    isMessageError = true
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSendingCode = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.email_bind_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.sym_arrow_back), contentDescription = stringResource(R.string.common_back))
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
            Icon(
                painter = painterResource(R.drawable.sym_email),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.email_bind_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = inputEmail,
                onValueChange = { inputEmail = it },
                label = { Text(stringResource(R.string.email_bind_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            VerificationCodeField(
                code = inputCode,
                onCodeChange = { inputCode = it },
                onSendCode = { sendVerificationCode() },
                isSending = isSendingCode,
                enabled = !isLoading
            )
            Spacer(modifier = Modifier.height(8.dp))
            message?.let { msg ->
                Text(
                    text = msg,
                    color = if (isMessageError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val email = inputEmail.trim()
                    if (email.isEmpty()) {
                        message = context.getString(R.string.email_bind_enter_email)
                        isMessageError = true
                        return@Button
                    }
                    if (inputCode.isBlank()) {
                        message = context.getString(R.string.verification_code_required)
                        isMessageError = true
                        return@Button
                    }

                    if (isLoading) return@Button
                    isLoading = true
                    message = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val response = RetrofitClient.userAuthApi.bindEmail(
                                "Bearer $bindToken",
                                BindEmailRequest(email = email, code = inputCode.trim())
                            )
                            if (response.success && response.accessToken != null
                                && response.refreshToken != null && response.user != null
                            ) {
                                val user = response.user
                                userAuthManager.saveLogin(
                                    accessToken = response.accessToken,
                                    refreshToken = response.refreshToken,
                                    userId = user.id,
                                    username = user.username,
                                    email = user.email,
                                    createdAt = user.createdAt,
                                    avatar = user.avatar
                                )
                                // 与登录流程一致：上报推送 ID + 拉取云端订阅
                                try {
                                    PushRegistrationHelper.reportRegistrationIdIfNeeded(context)
                                } catch (_: Exception) { }
                                try {
                                    AppContainer.getAnimeRepository()
                                        .triggerSyncSubscriptionsFromServer()
                                } catch (e: Exception) {
                                    android.util.Log.w("EmailBind", "Trigger sync subscriptions failed (non-fatal)", e)
                                }
                                withContext(Dispatchers.Main) {
                                    message = context.getString(R.string.email_bind_success)
                                    isMessageError = false
                                }
                                delay(1200)
                                withContext(Dispatchers.Main) {
                                    onBack()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    message = response.message ?: context.getString(R.string.email_bind_failed)
                                    isMessageError = true
                                    isLoading = false
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: HttpException) {
                            withContext(Dispatchers.Main) {
                                message = when (e.code()) {
                                    401, 403 -> context.getString(R.string.email_bind_token_expired)
                                    else -> e.serverMessage() ?: context.getString(R.string.email_bind_failed)
                                }
                                isMessageError = true
                                isLoading = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                message = context.getString(R.string.user_login_network_error)
                                isMessageError = true
                                isLoading = false
                            }
                        }
                    }
                },
                enabled = !isLoading && inputEmail.isNotBlank() && inputCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.email_bind_button))
                }
            }
        }
    }
}
