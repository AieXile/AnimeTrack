package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.aiexile.animetrack.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiAccountScreen(
    onBack: () -> Unit,
    viewModel: BangumiAccountViewModel = viewModel(factory = BangumiAccountViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    val syncState = uiState.syncState
    val isSyncing = syncState is BangumiSyncState.Syncing
    val syncingAction = (syncState as? BangumiSyncState.Syncing)?.action

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bangumi_account_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            if (isLoggedIn) {
                // 头像（持久化数据，无闪烁）
                val avatarUrl = uiState.avatar
                if (!avatarUrl.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(avatarUrl),
                        contentDescription = stringResource(R.string.bangumi_account_avatar),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 昵称（持久化数据）
                Text(
                    text = uiState.nickname ?: stringResource(R.string.bangumi_account_logged_in),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 已登录标识
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.bangumi_account_logged_in),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 自动同步说明
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.bangumi_account_auto_sync_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 全量拉取按钮
                Button(
                    onClick = { viewModel.pullFromRemote() },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing && syncingAction == BangumiSyncAction.PULL) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.bangumi_account_pulling))
                    } else {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.bangumi_account_pull))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 全量推送按钮
                Button(
                    onClick = { viewModel.pushToRemote() },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing && syncingAction == BangumiSyncAction.PUSH) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.bangumi_account_pushing))
                    } else {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.bangumi_account_push))
                    }
                }

                // 同步结果/错误信息
                when (val state = syncState) {
                    is BangumiSyncState.Success -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.action == BangumiSyncAction.PULL) {
                                stringResource(R.string.bangumi_account_pull_complete)
                            } else {
                                stringResource(R.string.bangumi_account_push_complete, state.count)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                    is BangumiSyncState.Failed -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.bangumi_account_sync_failed, state.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 退出登录
                OutlinedButton(
                    onClick = {
                        viewModel.logout()
                        onBack()
                    },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.bangumi_account_logout))
                }
            } else {
                Text(
                    text = stringResource(R.string.bangumi_account_not_logged_in),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
