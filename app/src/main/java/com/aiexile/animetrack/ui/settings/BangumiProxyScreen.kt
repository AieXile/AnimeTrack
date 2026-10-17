package com.aiexile.animetrack.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import com.aiexile.animetrack.ui.components.AppSwitch
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.dialogBlurBehind
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ProxyType
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.navigation.Routes
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiProxyScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 液态玻璃顶栏（跟随胶囊导航「液态玻璃」开关）：开启时顶栏背景模糊、返回按钮为玻璃圆钮
    val liquidGlassEnabled by rememberSettingsLiquidGlassTopBarEnabled()
    val glassBackdrop = rememberLayerBackdrop()

    // Bangumi 镜像数据源：解锁前开关置灰，开启需密码验证
    val mirrorEnabled by settingsRepository.bangumiMirrorEnabledFlow.collectAsState(initial = false)
    val mirrorUnlocked by settingsRepository.bangumiMirrorUnlockedFlow.collectAsState(initial = false)

    // Bangumi 反向代理
    val proxyEnabled by settingsRepository.bangumiProxyEnabledFlow.collectAsState(initial = false)
    val proxyHost by settingsRepository.bangumiProxyHostFlow.collectAsState(initial = SettingsRepository.DEFAULT_BANGUMI_PROXY_HOST)

    // 网络代理（WEB / SOCKS5）
    val httpProxyEnabled by settingsRepository.httpProxyEnabledFlow.collectAsState(initial = false)
    val httpProxyHost by settingsRepository.httpProxyHostFlow.collectAsState(initial = SettingsRepository.DEFAULT_HTTP_PROXY_HOST)
    val httpProxyPort by settingsRepository.httpProxyPortFlow.collectAsState(initial = SettingsRepository.DEFAULT_HTTP_PROXY_PORT)
    val httpProxyType by settingsRepository.httpProxyTypeFlow.collectAsState(initial = ProxyType.HTTP)
    val httpProxyUsername by settingsRepository.httpProxyUsernameFlow.collectAsState(initial = SettingsRepository.DEFAULT_HTTP_PROXY_USERNAME)
    val httpProxyPassword by settingsRepository.httpProxyPasswordFlow.collectAsState(initial = SettingsRepository.DEFAULT_HTTP_PROXY_PASSWORD)

    var showBangumiHostDialog by remember { mutableStateOf(false) }
    var bangumiHostInput by remember { mutableStateOf("") }

    // 镜像数据源密码验证：解锁后每次开启均需重新输入
    var showMirrorPasswordDialog by remember { mutableStateOf(false) }
    var mirrorPasswordInput by remember { mutableStateOf("") }
    var mirrorPasswordError by remember { mutableStateOf(false) }

    // 表单本地输入态：失焦时保存到 DataStore，避免逐字符异步写导致的回显延迟与光标跳动
    var serverInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        serverInput = settingsRepository.httpProxyHost
        portInput = if (settingsRepository.httpProxyPort > 0) settingsRepository.httpProxyPort.toString() else ""
        usernameInput = settingsRepository.httpProxyUsername
        passwordInput = settingsRepository.httpProxyPassword
    }
    val portInvalid = portInput.isNotEmpty() &&
        (portInput.toIntOrNull() == null || portInput.toIntOrNull() !in 1..65535)

    // 搜索定位：高亮目标分组并滚动到位
    val highlightKey = rememberSettingsHighlight(Routes.BANGUMI_PROXY)
    val listState = rememberLazyListState()
    val highlightAnchors = mapOf("mirror" to 1, "reverse_proxy" to 2, "http_proxy" to 3)
    // 玻璃顶栏模式下滚动定位需补偿顶栏高度，避免目标项被顶栏遮挡
    val glassScrollCompensation = rememberGlassTopBarScrollCompensation(liquidGlassEnabled)
    LaunchedEffect(highlightKey) {
        highlightAnchors[highlightKey]?.let { listState.animateScrollToItem(it, glassScrollCompensation) }
    }

    Scaffold(
        topBar = {
            GlassSettingsTopBar(
                title = stringResource(R.string.bangumi_proxy_title),
                onBack = onBack,
                liquidGlassEnabled = liquidGlassEnabled,
                backdrop = glassBackdrop
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (liquidGlassEnabled) Modifier.layerBackdrop(glassBackdrop) else Modifier)
                .then(if (liquidGlassEnabled) Modifier else Modifier.padding(paddingValues))
                .padding(horizontal = 16.dp),
            // 玻璃模式下内容视口全高（滚到顶栏下方形成模糊背景），用 contentPadding 垫出顶栏与导航栏空间
            contentPadding = if (liquidGlassEnabled) {
                PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                )
            } else {
                PaddingValues(0.dp)
            },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                SettingsGroup(
                    title = stringResource(R.string.bangumi_mirror_group),
                    modifier = rememberHighlightModifier("mirror", highlightKey)
                ) {
                    SwitchItem(
                        title = stringResource(R.string.bangumi_mirror_enable),
                        description = if (mirrorUnlocked) {
                            stringResource(R.string.bangumi_mirror_desc)
                        } else {
                            stringResource(R.string.bangumi_mirror_locked_desc)
                        },
                        checked = mirrorEnabled,
                        enabled = mirrorUnlocked,
                        onCheckedChange = { target ->
                            if (target) {
                                // 开启前先过密码验证，关闭无需验证
                                mirrorPasswordInput = ""
                                mirrorPasswordError = false
                                showMirrorPasswordDialog = true
                            } else {
                                scope.launch { settingsRepository.setBangumiMirrorEnabled(false) }
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.bangumi_proxy_reverse_group),
                    modifier = rememberHighlightModifier("reverse_proxy", highlightKey)
                ) {
                    Column {
                        HostItem(
                            host = proxyHost,
                            onClick = {
                                bangumiHostInput = proxyHost
                                showBangumiHostDialog = true
                            }
                        )
                        SwitchItem(
                            title = stringResource(R.string.bangumi_proxy_enable_reverse),
                            description = if (proxyHost.isBlank()) {
                                stringResource(R.string.bangumi_proxy_set_address_first)
                            } else {
                                stringResource(R.string.bangumi_proxy_reverse_desc, proxyHost)
                            },
                            checked = proxyEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setBangumiProxyEnabled(it) } }
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.bangumi_proxy_http_group),
                    modifier = rememberHighlightModifier("http_proxy", highlightKey)
                ) {
                    Column {
                        ProxyTypeItem(
                            title = stringResource(R.string.bangumi_proxy_protocol_web),
                            description = stringResource(R.string.bangumi_proxy_protocol_web_desc),
                            selected = httpProxyType == ProxyType.HTTP,
                            onClick = { scope.launch { settingsRepository.setHttpProxyType(ProxyType.HTTP) } }
                        )
                        ProxyTypeItem(
                            title = stringResource(R.string.bangumi_proxy_protocol_socks5),
                            description = stringResource(R.string.bangumi_proxy_protocol_socks5_desc),
                            selected = httpProxyType == ProxyType.SOCKS5,
                            onClick = { scope.launch { settingsRepository.setHttpProxyType(ProxyType.SOCKS5) } }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = serverInput,
                            onValueChange = { serverInput = it },
                            label = { Text(stringResource(R.string.bangumi_proxy_server)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focus ->
                                    if (!focus.isFocused && serverInput != settingsRepository.httpProxyHost) {
                                        scope.launch { settingsRepository.setHttpProxyHost(serverInput) }
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(R.string.bangumi_proxy_port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = portInvalid,
                            supportingText = if (portInvalid) {
                                { Text(stringResource(R.string.bangumi_proxy_port_invalid)) }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focus ->
                                    if (!focus.isFocused) {
                                        val port = portInput.toIntOrNull()
                                        val newPort = when {
                                            portInput.isEmpty() -> 0
                                            port != null && port in 1..65535 -> port
                                            else -> return@onFocusChanged // 非法非空输入不保存
                                        }
                                        if (newPort != settingsRepository.httpProxyPort) {
                                            scope.launch { settingsRepository.setHttpProxyPort(newPort) }
                                        }
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text(stringResource(R.string.bangumi_proxy_username)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focus ->
                                    if (!focus.isFocused && usernameInput != settingsRepository.httpProxyUsername) {
                                        scope.launch { settingsRepository.setHttpProxyUsername(usernameInput) }
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text(stringResource(R.string.bangumi_proxy_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focus ->
                                    if (!focus.isFocused && passwordInput != settingsRepository.httpProxyPassword) {
                                        scope.launch { settingsRepository.setHttpProxyPassword(passwordInput) }
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SwitchItem(
                            title = stringResource(R.string.bangumi_proxy_enable_http),
                            description = if (httpProxyHost.isBlank() || httpProxyPort !in 1..65535) {
                                stringResource(R.string.bangumi_proxy_set_address_first)
                            } else {
                                stringResource(R.string.bangumi_proxy_http_desc, "${httpProxyHost}:${httpProxyPort}")
                            },
                            checked = httpProxyEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setHttpProxyEnabled(it) } }
                        )

                        ShareItem(
                            enabled = httpProxyHost.isNotBlank() && httpProxyPort in 1..65535,
                            onClick = {
                                val scheme = if (httpProxyType == ProxyType.SOCKS5) "socks5" else "http"
                                val shareText = buildString {
                                    append(scheme).append("://")
                                    if (httpProxyUsername.isNotBlank()) {
                                        append(httpProxyUsername)
                                        if (httpProxyPassword.isNotBlank()) append(":").append(httpProxyPassword)
                                        append("@")
                                    }
                                    append(httpProxyHost).append(":").append(httpProxyPort)
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Bangumi 代理地址弹窗
    if (showBangumiHostDialog) {
        AlertDialog(
            onDismissRequest = { showBangumiHostDialog = false },
            modifier = Modifier.dialogBlurBehind(),
            shape = SquircleShape(24.dp),
            title = { Text(stringResource(R.string.bangumi_proxy_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = bangumiHostInput,
                    onValueChange = { bangumiHostInput = it },
                    label = { Text(stringResource(R.string.bangumi_proxy_domain)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            settingsRepository.setBangumiProxyHost(bangumiHostInput)
                        }
                        showBangumiHostDialog = false
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showBangumiHostDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 镜像数据源开启密码验证：密码正确才写入开关并生效
    if (showMirrorPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showMirrorPasswordDialog = false },
            modifier = Modifier.dialogBlurBehind(),
            shape = SquircleShape(24.dp),
            title = { Text(stringResource(R.string.bangumi_mirror_password_title)) },
            text = {
                OutlinedTextField(
                    value = mirrorPasswordInput,
                    onValueChange = {
                        mirrorPasswordInput = it
                        mirrorPasswordError = false
                    },
                    label = { Text(stringResource(R.string.bangumi_mirror_password_label)) },
                    singleLine = true,
                    isError = mirrorPasswordError,
                    supportingText = if (mirrorPasswordError) {
                        { Text(stringResource(R.string.bangumi_mirror_password_wrong)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (mirrorPasswordInput == SettingsRepository.BANGUMI_MIRROR_PASSWORD) {
                            scope.launch { settingsRepository.setBangumiMirrorEnabled(true) }
                            showMirrorPasswordDialog = false
                        } else {
                            mirrorPasswordError = true
                        }
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showMirrorPasswordDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun HostItem(
    host: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.bangumi_proxy_domain_label),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = host.ifBlank { stringResource(R.string.common_not_set) },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) { Text(stringResource(R.string.bangumi_proxy_modify)) }
    }
}

/** 代理协议单选行：RadioButton 指示器，无 ripple（遵循项目偏好） */
@Composable
private fun ProxyTypeItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            interactionSource = interactionSource,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 分享代理配置入口行 */
@Composable
private fun ShareItem(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            icon = AppIcon.SHARE,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = stringResource(R.string.bangumi_proxy_share),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun SwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
