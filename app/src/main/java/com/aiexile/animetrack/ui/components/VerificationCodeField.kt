package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import kotlinx.coroutines.delay

/**
 * 邮箱验证码输入组件：验证码输入框，「发送验证码 / 倒计时」内嵌在输入框尾部（trailing）。
 *
 * @param code 验证码文本（由调用方持有）
 * @param onCodeChange 验证码输入回调
 * @param onSendCode 点击发送回调（组件内已做倒计时防抖，调用方执行网络请求）
 * @param isSending 是否正在发送（尾部显示加载中，结束后开始倒计时）
 * @param modifier 修饰符
 * @param enabled 输入框是否可用
 */
@Composable
fun VerificationCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var countdown by remember { mutableIntStateOf(0) }
    var sendRequested by remember { mutableStateOf(false) }

    // 发送完成（isSending 从 true → false）后开始 60 秒倒计时
    LaunchedEffect(isSending) {
        if (sendRequested && !isSending) {
            sendRequested = false
            countdown = 60
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
    }

    OutlinedTextField(
        value = code,
        onValueChange = { input -> onCodeChange(input.filter { it.isDigit() }.take(6)) },
        label = { Text(stringResource(R.string.verification_code_label)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            Box(contentAlignment = Alignment.Center) {
                when {
                    isSending -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    countdown > 0 -> {
                        Text(
                            text = stringResource(R.string.verification_code_resend_in, countdown),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    else -> {
                        TextButton(
                            onClick = {
                                sendRequested = true
                                onSendCode()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp, vertical = 6.dp
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.verification_code_send),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
