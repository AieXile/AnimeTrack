package com.aiexile.animetrack.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiProxyScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()

    // Bangumi 反向代理
    val proxyEnabled by settingsRepository.bangumiProxyEnabledFlow.collectAsState(initial = false)
    val proxyHost by settingsRepository.bangumiProxyHostFlow.collectAsState(initial = SettingsRepository.DEFAULT_BANGUMI_PROXY_HOST)

    // HTTP 普通代理
    val httpProxyEnabled by settingsRepository.httpProxyEnabledFlow.collectAsState(initial = false)
    val httpProxyHost by settingsRepository.httpProxyHostFlow.collectAsState(initial = SettingsRepository.DEFAULT_HTTP_PROXY_HOST)
    val httpProxyPort by settingsRepository.httpProxyPortFlow.collectAsState(initial = SettingsRepository.DEFAULT_HTTP_PROXY_PORT)

    var showBangumiHostDialog by remember { mutableStateOf(false) }
    var bangumiHostInput by remember { mutableStateOf("") }

    var showHttpProxyDialog by remember { mutableStateOf(false) }
    var httpHostInput by remember { mutableStateOf("") }
    var httpPortInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bangumi_proxy_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                SettingsGroup(title = stringResource(R.string.bangumi_proxy_reverse_group)) {
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
                SettingsGroup(title = stringResource(R.string.bangumi_proxy_http_group)) {
                    Column {
                        HostPortItem(
                            host = httpProxyHost,
                            port = httpProxyPort,
                            onClick = {
                                httpHostInput = httpProxyHost
                                httpPortInput = if (httpProxyPort > 0) httpProxyPort.toString() else ""
                                showHttpProxyDialog = true
                            }
                        )
                        SwitchItem(
                            title = stringResource(R.string.bangumi_proxy_enable_http),
                            description = if (httpProxyHost.isBlank()) {
                                stringResource(R.string.bangumi_proxy_set_address_first)
                            } else {
                                stringResource(R.string.bangumi_proxy_http_desc, httpProxyHost, httpProxyPort)
                            },
                            checked = httpProxyEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setHttpProxyEnabled(it) } }
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

    // HTTP 代理地址弹窗
    if (showHttpProxyDialog) {
        AlertDialog(
            onDismissRequest = { showHttpProxyDialog = false },
            title = { Text(stringResource(R.string.bangumi_proxy_http_group)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = httpHostInput,
                        onValueChange = { httpHostInput = it },
                        label = { Text(stringResource(R.string.bangumi_proxy_address)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = httpPortInput,
                        onValueChange = { httpPortInput = it },
                        label = { Text(stringResource(R.string.bangumi_proxy_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val port = httpPortInput.toIntOrNull() ?: 0
                        scope.launch {
                            settingsRepository.setHttpProxyHost(httpHostInput)
                            settingsRepository.setHttpProxyPort(port)
                        }
                        showHttpProxyDialog = false
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showHttpProxyDialog = false }) { Text(stringResource(R.string.common_cancel)) }
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

@Composable
private fun HostPortItem(
    host: String,
    port: Int,
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
                text = stringResource(R.string.bangumi_proxy_address_label),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (host.isNotBlank() && port > 0) "${host}:${port}" else stringResource(R.string.common_not_set),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) { Text(stringResource(R.string.bangumi_proxy_modify)) }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
