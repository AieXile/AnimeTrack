package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.VerificationCodeField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * 强制绑定邮箱 Dialog（已登录存量会话未绑定邮箱时由全局检测触发）
 *
 * 不可点外部关闭，只有「退出登录」和「立即绑定」两个选择。
 * 绑定成功后服务端签发新登录凭证，Dialog 内部完成保存与登录后处理（推送上报/订阅同步）。
 * 登录流程触发的绑定（bindToken）走 EmailBindScreen 页面，不使用此 Dialog。
 *
 * @param token accessToken（已登录但未绑定邮箱的存量会话）
 * @param onDismiss 用户选择退出登录
 * @param onBound 绑定成功且登录凭证保存完毕
 */
@Composable
fun EmailBindDialog(
    token: String,
    onDismiss: () -> Unit,
    onBound: () -> Unit
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
    var bindSucceeded by remember { mutableStateOf(false) }

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

    AlertDialog(
        onDismissRequest = {
            // 强制绑定不允许点外部关闭；请求中/成功后也不可关闭
        },
        shape = SquircleShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.email_bind_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.email_bind_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputEmail,
                    onValueChange = { inputEmail = it },
                    label = { Text(stringResource(R.string.email_bind_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isLoading && !bindSucceeded,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                VerificationCodeField(
                    code = inputCode,
                    onCodeChange = { inputCode = it },
                    onSendCode = { sendVerificationCode() },
                    isSending = isSendingCode,
                    enabled = !isLoading && !bindSucceeded
                )
                message?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = if (isMessageError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val email = inputEmail.trim()
                    if (email.isEmpty()) {
                        message = context.getString(R.string.email_bind_enter_email)
                        isMessageError = true
                        return@TextButton
                    }
                    if (inputCode.isBlank()) {
                        message = context.getString(R.string.verification_code_required)
                        isMessageError = true
                        return@TextButton
                    }

                    if (isLoading || bindSucceeded) return@TextButton
                    isLoading = true
                    message = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val response = RetrofitClient.userAuthApi.bindEmail(
                                "Bearer $token",
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
                                    isLoading = false
                                    bindSucceeded = true
                                    message = context.getString(R.string.email_bind_success)
                                    isMessageError = false
                                }
                                delay(1200)
                                withContext(Dispatchers.Main) {
                                    onBound()
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
                enabled = !isLoading && !bindSucceeded
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.email_bind_button))
                }
            }
        },
        dismissButton = {
            // 绑定成功展示期间隐藏负向按钮
            if (!bindSucceeded) {
                TextButton(
                    onClick = { if (!isLoading) onDismiss() },
                    enabled = !isLoading
                ) {
                    Text(
                        text = stringResource(R.string.user_login_logout),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
