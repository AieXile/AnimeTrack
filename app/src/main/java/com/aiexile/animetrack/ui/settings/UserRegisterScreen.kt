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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.EmailCodePurpose
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.SendCodeRequest
import com.aiexile.animetrack.data.network.UserAuthRegisterRequest
import com.aiexile.animetrack.data.network.serverMessage
import com.aiexile.animetrack.ui.components.VerificationCodeField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRegisterScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var inputUsername by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }
    var inputConfirmPassword by remember { mutableStateOf("") }
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
            message = context.getString(R.string.user_register_enter_email)
            isMessageError = true
            return
        }
        if (isSendingCode) return
        isSendingCode = true
        message = null
        scope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.sendCode(
                    SendCodeRequest(email = email, purpose = EmailCodePurpose.REGISTER)
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
                    message = context.getString(R.string.user_register_network_error)
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
                        text = stringResource(R.string.user_register_title),
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
            OutlinedTextField(
                value = inputUsername,
                onValueChange = { inputUsername = it },
                label = { Text(stringResource(R.string.user_register_username)) },
                placeholder = { Text(stringResource(R.string.user_register_username_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = inputPassword,
                onValueChange = { inputPassword = it },
                label = { Text(stringResource(R.string.user_register_password)) },
                placeholder = { Text(stringResource(R.string.user_register_password_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = inputConfirmPassword,
                onValueChange = { inputConfirmPassword = it },
                label = { Text(stringResource(R.string.user_register_confirm_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = inputEmail,
                onValueChange = { inputEmail = it },
                label = { Text(stringResource(R.string.user_register_email)) },
                placeholder = { Text(stringResource(R.string.user_register_email_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            VerificationCodeField(
                code = inputCode,
                onCodeChange = { inputCode = it },
                onSendCode = { sendVerificationCode() },
                isSending = isSendingCode
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
                    // 本地验证
                    val trimmedUsername = inputUsername.trim()
                    val trimmedEmail = inputEmail.trim()
                    if (trimmedUsername.length < 3 || trimmedUsername.length > 20) {
                        message = context.getString(R.string.user_register_username_length_error)
                        isMessageError = true
                        return@Button
                    }
                    if (inputPassword.length < 6) {
                        message = context.getString(R.string.user_register_password_length_error)
                        isMessageError = true
                        return@Button
                    }
                    if (inputPassword != inputConfirmPassword) {
                        message = context.getString(R.string.user_register_password_mismatch)
                        isMessageError = true
                        return@Button
                    }
                    if (trimmedEmail.isEmpty()) {
                        message = context.getString(R.string.user_register_enter_email)
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
                            val response = RetrofitClient.userAuthApi.register(
                                UserAuthRegisterRequest(
                                    username = trimmedUsername,
                                    password = inputPassword,
                                    email = trimmedEmail,
                                    code = inputCode.trim()
                                )
                            )
                            if (response.success) {
                                withContext(Dispatchers.Main) {
                                    message = context.getString(R.string.user_register_success)
                                    isMessageError = false
                                }
                                delay(1500)
                                withContext(Dispatchers.Main) {
                                    onBack()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    message = response.message ?: context.getString(R.string.user_register_failed)
                                    isMessageError = true
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: HttpException) {
                            withContext(Dispatchers.Main) {
                                message = e.serverMessage() ?: context.getString(R.string.user_register_failed)
                                isMessageError = true
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                message = context.getString(R.string.user_register_network_error)
                                isMessageError = true
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                            }
                        }
                    }
                },
                enabled = !isLoading && inputUsername.isNotBlank() && inputPassword.isNotBlank()
                    && inputConfirmPassword.isNotBlank() && inputEmail.isNotBlank() && inputCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.user_register_button))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.user_register_has_account_login))
            }
        }
    }
}
