package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.components.AppSwitch
import com.aiexile.animetrack.ui.components.SquircleShape
import kotlinx.coroutines.launch

/**
 * 播放器专属 WebDAV 配置页。
 *
 * 与备份同步的 WebDAV 相互独立（可能是不同服务器/账号）；
 * 若备份侧已配置，可通过「从备份配置填入」一键带入后再自行修改保存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerWebDavConfigScreen(
    onBack: () -> Unit,
    settingsRepository: SettingsRepository = remember { AppContainer.getSettingsRepository() }
) {
    val scope = rememberCoroutineScope()

    val savedUrl by settingsRepository.playerWebdavUrl.collectAsState(initial = "")
    val savedUsername by settingsRepository.playerWebdavUsername.collectAsState(initial = "")
    val savedPassword by settingsRepository.playerWebdavPassword.collectAsState(initial = "")
    val trustAllCerts by settingsRepository.playerWebdavTrustAllCerts.collectAsState(initial = false)
    val backupUrl by settingsRepository.webdavUrl.collectAsState(initial = "")
    val backupUsername by settingsRepository.webdavUsername.collectAsState(initial = "")
    val backupPassword by settingsRepository.webdavPassword.collectAsState(initial = "")

    // null = 未接管，直接展示存储中的值；用户编辑/导入后接管为本地草稿。
    // 这样既避免 DataStore 异步加载完成前的闪烁，也不会覆盖未编辑字段。
    var urlDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var usernameDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordDraft by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.player_webdav_server),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.player_webdav_config_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = urlDraft ?: savedUrl,
                onValueChange = { urlDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_server_address)) },
                placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = usernameDraft ?: savedUsername,
                onValueChange = { usernameDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = passwordDraft ?: savedPassword,
                onValueChange = { passwordDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        settingsRepository.setPlayerWebdavCredentials(
                            url = (urlDraft ?: savedUrl).trim(),
                            username = (usernameDraft ?: savedUsername).trim(),
                            password = passwordDraft ?: savedPassword
                        )
                        onBack()
                    }
                },
                shape = SquircleShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_save))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 从备份配置填入：仅当备份侧已配置服务器时可用
            OutlinedButton(
                onClick = {
                    // 仅填入草稿，由用户确认后再点保存
                    urlDraft = backupUrl
                    usernameDraft = backupUsername
                    passwordDraft = backupPassword
                },
                enabled = backupUrl.isNotBlank(),
                shape = SquircleShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.player_webdav_import_from_backup))
            }

            if (backupUrl.isBlank()) {
                Text(
                    text = stringResource(R.string.player_webdav_backup_empty_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 信任所有证书：IP 直连 NAS / 自签名证书场景（写入 DataStore；
            // PlaybackService 的 OkHttp client 为懒加载单例，重启应用后生效）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.player_webdav_trust_all_certs),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.player_webdav_trust_all_certs_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                AppSwitch(
                    checked = trustAllCerts,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.setPlayerWebdavTrustAllCerts(enabled) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
