package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.DeleteAccountRequest
import com.aiexile.animetrack.data.network.EmailCodePurpose
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.SendCodeRequest
import com.aiexile.animetrack.data.network.serverMessage
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.VerificationCodeField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * 账号注销流程弹窗（三步状态机）：
 * 1. Verify  —— 身份验证（已绑定邮箱：验证码；否则回退密码）
 * 2. Confirm —— 红色警示二次确认（数据删除范围 + 宽限期说明）
 * 3. Result  —— 提交成功后询问是否同时清除本机追番数据
 *
 * 提交成功后服务端会撤销全部会话（本机登录态失效），
 * [onDeletionConfirmed] 回调由调用方执行 logout 与可选的本地数据清理。
 */
@Composable
fun DeleteAccountFlowDialogs(
    hasVerifiedEmail: Boolean,
    onDismiss: () -> Unit,
    onDeletionConfirmed: (clearLocalData: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(DeletionStep.VERIFY) }
    var purgeAt by remember { mutableStateOf<String?>(null) }

    // 验证输入
    var verifyCode by remember { mutableStateOf("") }
    var verifyPassword by remember { mutableStateOf("") }
    var isSendingCode by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    when (step) {
        DeletionStep.VERIFY -> {
            AlertDialog(
                onDismissRequest = { if (!isSubmitting) onDismiss() },
                shape = SquircleShape(24.dp),
                title = {
                    Text(
                        text = stringResource(R.string.user_login_delete_account),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.user_login_delete_account_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (hasVerifiedEmail) {
                            VerificationCodeField(
                                code = verifyCode,
                                onCodeChange = { verifyCode = it },
                                onSendCode = {
                                    if (isSendingCode) return@VerificationCodeField
                                    isSendingCode = true
                                    error = null
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val response = RetrofitClient.userAuthApi.sendCode(
                                                SendCodeRequest(email = null, purpose = EmailCodePurpose.DELETE_ACCOUNT)
                                            )
                                            if (!response.success) {
                                                withContext(Dispatchers.Main) {
                                                    error = response.message
                                                        ?: context.getString(R.string.verification_code_send_failed)
                                                }
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: HttpException) {
                                            withContext(Dispatchers.Main) {
                                                error = e.serverMessage()
                                                    ?: context.getString(R.string.verification_code_send_failed)
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                error = context.getString(R.string.user_login_network_error)
                                            }
                                        } finally {
                                            withContext(Dispatchers.Main) { isSendingCode = false }
                                        }
                                    }
                                },
                                isSending = isSendingCode,
                                enabled = !isSubmitting
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.user_login_delete_account_no_email_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = verifyPassword,
                                onValueChange = { verifyPassword = it },
                                label = { Text(stringResource(R.string.user_login_current_password)) },
                                singleLine = true,
                                shape = SquircleShape(12.dp),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        error?.let { err ->
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
                            if (hasVerifiedEmail && verifyCode.isBlank()) {
                                error = context.getString(R.string.verification_code_required)
                                return@TextButton
                            }
                            if (!hasVerifiedEmail && verifyPassword.isBlank()) {
                                error = context.getString(R.string.user_login_enter_password)
                                return@TextButton
                            }
                            step = DeletionStep.CONFIRM
                        },
                        enabled = !isSubmitting
                    ) {
                        Text(stringResource(R.string.common_next_step))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        DeletionStep.CONFIRM -> {
            AlertDialog(
                onDismissRequest = { if (!isSubmitting) onDismiss() },
                shape = SquircleShape(24.dp),
                title = {
                    Text(
                        text = stringResource(R.string.user_login_delete_account_confirm_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.user_login_delete_account_confirm_detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.user_login_delete_account_grace_period),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isSubmitting) return@Button
                            isSubmitting = true
                            error = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val response = RetrofitClient.userAuthApi.deleteAccount(
                                        DeleteAccountRequest(
                                            code = if (hasVerifiedEmail) verifyCode.trim() else null,
                                            password = if (hasVerifiedEmail) null else verifyPassword
                                        )
                                    )
                                    if (response.success) {
                                        withContext(Dispatchers.Main) {
                                            purgeAt = response.purgeAt
                                            step = DeletionStep.RESULT
                                            isSubmitting = false
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            error = response.message
                                                ?: context.getString(R.string.user_login_delete_failed)
                                            isSubmitting = false
                                            // 验证失败：回到验证步骤重新输入
                                            step = DeletionStep.VERIFY
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: HttpException) {
                                    withContext(Dispatchers.Main) {
                                        error = e.serverMessage()
                                            ?: context.getString(R.string.user_login_delete_failed)
                                        isSubmitting = false
                                        step = DeletionStep.VERIFY
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        error = context.getString(R.string.user_login_network_error)
                                        isSubmitting = false
                                        step = DeletionStep.VERIFY
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            Text(stringResource(R.string.user_login_delete_account_confirm))
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!isSubmitting) onDismiss() },
                        enabled = !isSubmitting
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        DeletionStep.RESULT -> {
            AlertDialog(
                onDismissRequest = {}, // 结果步骤必须做出选择，不允许点外部关闭
                shape = SquircleShape(24.dp),
                title = {
                    Text(
                        text = stringResource(R.string.user_login_delete_account_submitted),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column {
                        purgeAt?.let {
                            Text(
                                text = stringResource(R.string.user_login_delete_account_purge_at, it.take(10)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.user_login_delete_account_local_data_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onDeletionConfirmed(true) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.user_login_delete_account_clear_local))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onDeletionConfirmed(false) }) {
                        Text(stringResource(R.string.user_login_delete_account_keep_local))
                    }
                }
            )
        }
    }
}

/** 注销流程步骤 */
private enum class DeletionStep { VERIFY, CONFIRM, RESULT }

/**
 * 宽限期恢复账号弹窗：登录时服务端返回 DELETION_PENDING 触发。
 * 展示永久删除截止时间，用户可选择恢复账号（由调用方携带当前输入的
 * 用户名/密码调用 restore-account）或放弃。
 */
@Composable
fun RestoreAccountDialog(
    purgeAt: String?,
    isRestoring: Boolean,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        shape = SquircleShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.user_login_account_deletion_pending),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.user_login_account_deletion_pending_detail,
                        purgeAt?.take(10) ?: ""
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRestore,
                enabled = !isRestoring
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.user_login_restore_account))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isRestoring) onDismiss() },
                enabled = !isRestoring
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
