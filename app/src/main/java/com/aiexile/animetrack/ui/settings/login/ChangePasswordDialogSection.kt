package com.aiexile.animetrack.ui.settings.login

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.VerificationCodeField
import com.aiexile.animetrack.ui.components.dialogBlurBehind

/**
 * 修改密码 Dialog：旧密码、新密码、确认新密码与邮箱验证码。
 */
@Composable
internal fun ChangePasswordDialogSection(
    visible: Boolean,
    oldPassword: String,
    onOldPasswordChange: (String) -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmNewPassword: String,
    onConfirmNewPasswordChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    isSendingCode: Boolean,
    errorMessage: String?,
    isChanging: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 修改密码 Dialog
    if (visible) {
        AlertDialog(
            onDismissRequest = {
                if (!isChanging) onDismiss()
            },
            modifier = Modifier.dialogBlurBehind(),
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
                        onValueChange = onOldPasswordChange,
                        label = { Text(stringResource(R.string.user_login_old_password)) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChanging,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = onNewPasswordChange,
                        label = { Text(stringResource(R.string.user_login_new_password)) },
                        placeholder = { Text(stringResource(R.string.user_login_password_min_hint)) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChanging,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = onConfirmNewPasswordChange,
                        label = { Text(stringResource(R.string.user_login_confirm_new_password)) },
                        singleLine = true,
                        shape = SquircleShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isChanging,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    VerificationCodeField(
                        code = code,
                        onCodeChange = onCodeChange,
                        onSendCode = onSendCode,
                        isSending = isSendingCode,
                        enabled = !isChanging
                    )
                    errorMessage?.let { err ->
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
                    onClick = onConfirm,
                    enabled = !isChanging
                ) {
                    if (isChanging) {
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
                        if (!isChanging) onDismiss()
                    },
                    enabled = !isChanging
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
