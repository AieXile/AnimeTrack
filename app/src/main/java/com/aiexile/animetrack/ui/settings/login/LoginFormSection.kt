package com.aiexile.animetrack.ui.settings.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 未登录 / token 已失效时的登录表单区：错误提示、账号密码输入、登录按钮与注册/找回密码入口。
 */
@Composable
internal fun LoginFormSection(
    tokenExpired: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    errorMessage: String?,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onNavigateRegister: () -> Unit,
    onNavigateForgotPassword: () -> Unit
) {
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
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.user_login_username)) },
        singleLine = true,
        shape = SquircleShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
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
        onClick = onSubmit,
        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
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
