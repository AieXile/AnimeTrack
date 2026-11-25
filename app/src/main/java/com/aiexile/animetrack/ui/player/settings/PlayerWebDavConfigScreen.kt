package com.aiexile.animetrack.ui.player.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.webdav.PlayerWebDavDirectory
import com.aiexile.animetrack.ui.components.AppOutlinedTextField
import com.aiexile.animetrack.ui.components.AppSwitch
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.player.webdav.webDavDisplayPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

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
    var passwordDraft by remember { mutableStateOf<String?>(null) }
    var mediaRootDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var trustDraft by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var probeJob by remember { mutableStateOf<Job?>(null) }
    var probeFeedback by remember { mutableStateOf<ProbeFeedback?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    fun resetProbe() {
        probeJob?.cancel()
        probeJob = null
        probeFeedback = null
    }

    fun testConnection() {
        resetProbe()
        val config = PlayerWebDavDirectory.Config.parse(
            urlDraft ?: savedUrl, mediaRootDraft ?: savedMediaRoot,
            usernameDraft ?: savedUsername, passwordDraft ?: savedPassword, trustDraft ?: trustAllCerts
        )
        if (config == null) {
            probeFeedback = ProbeFeedback(R.string.player_webdav_probe_invalid_address)
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            probeFeedback = try {
                withTimeout(10_000) { PlayerWebDavDirectory.probe(config) }
                ProbeFeedback(R.string.player_webdav_probe_success)
            } catch (_: TimeoutCancellationException) {
                ProbeFeedback(R.string.player_webdav_probe_timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                probeFailure(failure)
            }
            probeJob = null
        }
        // 启动前先绑定 Job，避免同步完成的探测无法清理状态。
        probeJob = job
        job.start()
    }

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
                onValueChange = { resetProbe(); urlDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_server_address)) },
                placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppOutlinedTextField(
                value = usernameDraft ?: savedUsername,
                onValueChange = { resetProbe(); usernameDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppOutlinedTextField(
                value = passwordDraft ?: savedPassword,
                onValueChange = { resetProbe(); passwordDraft = it },
                label = { Text(stringResource(R.string.webdav_sync_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppOutlinedTextField(
                value = mediaRootDraft ?: savedMediaRoot,
                onValueChange = { resetProbe(); mediaRootDraft = it },
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

            OutlinedButton(
                onClick = { if (probeJob == null) testConnection() else resetProbe() },
                enabled = !saving,
                shape = SquircleShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (probeJob == null) R.string.webdav_sync_test_connection else R.string.common_cancel))
            }
            val feedback = if (probeJob != null) ProbeFeedback(R.string.player_webdav_probe_running) else probeFeedback
            if (feedback != null) {
                Text(
                    text = feedback.httpStatus?.let { stringResource(feedback.message, it) }
                        ?: stringResource(feedback.message),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    resetProbe()
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            settingsRepository.savePlayerWebdavConfiguration(
                                url = urlDraft ?: savedUrl,
                                username = usernameDraft ?: savedUsername,
                                password = passwordDraft ?: savedPassword,
                                root = mediaRootDraft ?: savedMediaRoot,
                                trust = trustDraft ?: trustAllCerts
                            )
                            onBack()
                        } catch (_: IOException) {
                            saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                shape = SquircleShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_save))
            }
            if (saveFailed) Text(stringResource(R.string.player_webdav_save_failed), color = MaterialTheme.colorScheme.error)

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
                    resetProbe()
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
                    checked = trustDraft ?: trustAllCerts,
                    onCheckedChange = { enabled ->
                        resetProbe()
                        trustDraft = enabled
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class ProbeFeedback(val message: Int, val httpStatus: Int? = null)

private fun probeFailure(failure: IOException): ProbeFeedback = when (failure) {
    is UnknownHostException -> ProbeFeedback(R.string.player_webdav_probe_dns)
    is SSLException -> ProbeFeedback(R.string.player_webdav_probe_tls)
    is InterruptedIOException -> ProbeFeedback(R.string.player_webdav_probe_timeout)
    is PlayerWebDavDirectory.ListingTooLarge -> ProbeFeedback(R.string.player_webdav_probe_too_large)
    is PlayerWebDavDirectory.InvalidListing -> ProbeFeedback(R.string.player_webdav_probe_unsupported)
    is PlayerWebDavDirectory.HttpFailure -> when (failure.status) {
        401 -> ProbeFeedback(R.string.player_webdav_probe_unauthorized)
        403 -> ProbeFeedback(R.string.player_webdav_probe_forbidden)
        404, 410 -> ProbeFeedback(R.string.player_webdav_probe_not_found)
        405, 501 -> ProbeFeedback(R.string.player_webdav_probe_unsupported)
        in 300..399 -> ProbeFeedback(R.string.player_webdav_probe_redirect)
        else -> ProbeFeedback(R.string.player_webdav_probe_http, failure.status)
    }
    else -> ProbeFeedback(R.string.player_webdav_probe_network)
}
