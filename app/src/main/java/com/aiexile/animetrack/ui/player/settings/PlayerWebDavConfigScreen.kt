package com.aiexile.animetrack.ui.player.settings

import androidx.compose.foundation.layout.Column
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.components.AppOutlinedTextField
import com.aiexile.animetrack.ui.components.AppSwitch
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.player.webdav.webDavDisplayPath
import kotlinx.coroutines.launch

/**
 * 播放器专属 WebDAV 配置页。
 *
 * 与备份同步的 WebDAV 相互独立；播放器额外保存媒体根目录和最近浏览记忆。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerWebDavConfigScreen(
    onBack: () -> Unit,
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()

    val savedUrl by settingsRepository.playerWebdavUrl.collectAsStateWithLifecycle(initialValue = "")
    val savedUsername by settingsRepository.playerWebdavUsername.collectAsStateWithLifecycle(initialValue = "")
    val savedPassword by settingsRepository.playerWebdavPassword.collectAsStateWithLifecycle(initialValue = "")
    val savedMediaRoot by settingsRepository.playerWebdavMediaRoot.collectAsStateWithLifecycle(initialValue = "")
    val lastBrowsePath by settingsRepository.playerWebdavLastBrowsePath.collectAsStateWithLifecycle(initialValue = "")
    val trustAllCerts by settingsRepository.playerWebdavTrustAllCerts.collectAsStateWithLifecycle(initialValue = false)
    val backupUrl by settingsRepository.webdavUrl.collectAsStateWithLifecycle(initialValue = "")
    val backupUsername by settingsRepository.webdavUsername.collectAsStateWithLifecycle(initialValue = "")
    val backupPassword by settingsRepository.webdavPassword.collectAsStateWithLifecycle(initialValue = "")

    // null = 未接管，直接展示存储中的值；用户编辑/导入后接管为本地草稿。
    var urlDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var usernameDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaRootDraft by rememberSaveable { mutableStateOf<String?>(null) }

    AppScaffold(
        title = stringResource(R.string.player_webdav_server),
        onBack = onBack
    ) { chrome ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(chrome.content)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.player_webdav_config_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AppOutlinedTextField(
                value = urlDraft ?: savedUrl,
                onValueChange = { urlDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_server_address)) },
                placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppOutlinedTextField(
                value = usernameDraft ?: savedUsername,
                onValueChange = { usernameDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppOutlinedTextField(
                value = passwordDraft ?: savedPassword,
                onValueChange = { passwordDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppOutlinedTextField(
                value = mediaRootDraft ?: savedMediaRoot,
                onValueChange = { mediaRootDraft = it },
                label = { Text(stringResource(R.string.player_webdav_media_root)) },
                placeholder = { Text(stringResource(R.string.player_webdav_media_root_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (lastBrowsePath.isNotBlank()) {
                Text(
                    text = stringResource(R.string.player_webdav_recent_location, webDavDisplayPath(lastBrowsePath)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        settingsRepository.setPlayerWebdavCredentials(
                            url = (urlDraft ?: savedUrl).trim(),
                            username = (usernameDraft ?: savedUsername).trim(),
                            password = passwordDraft ?: savedPassword
                        )
                        settingsRepository.setPlayerWebdavMediaRoot(mediaRootDraft ?: savedMediaRoot)
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

            OutlinedButton(
                onClick = {
                    scope.launch { settingsRepository.clearPlayerWebdavBrowseMemory() }
                },
                enabled = lastBrowsePath.isNotBlank(),
                shape = SquircleShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.player_webdav_clear_memory))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 从备份配置填入：仅当备份侧已配置服务器时可用
            OutlinedButton(
                onClick = {
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
