package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.layout.Arrangement
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                        Icon(rememberAppIconPainter(AppIcon.ARROW_BACK), contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoggedIn) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                // 头像（持久化数据，无闪烁，带占位兜底）
                AccountAvatar(
                    avatarUrl = uiState.avatar,
                    contentDescription = stringResource(R.string.bangumi_account_avatar)
                )
                Spacer(modifier = Modifier.height(16.dp))
                // 昵称（持久化数据）
                Text(
                    text = uiState.nickname ?: stringResource(R.string.bangumi_account_logged_in),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 已登录标识
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.CHECK_CIRCLE),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.bangumi_account_logged_in),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 自动同步说明
                Text(
                    text = stringResource(R.string.bangumi_account_auto_sync_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                // 数据同步卡片
                Surface(
                    shape = SquircleShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // 全量拉取
                        AccountActionRow(
                            icon = rememberAppIconPainter(AppIcon.CLOUD_DOWNLOAD),
                            label = if (isSyncing && syncingAction == BangumiSyncAction.PULL) {
                                stringResource(R.string.bangumi_account_pulling)
                            } else {
                                stringResource(R.string.bangumi_account_pull)
                            },
                            onClick = { viewModel.pullFromRemote() },
                            enabled = !isSyncing,
                            busy = isSyncing && syncingAction == BangumiSyncAction.PULL
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 52.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        // 全量推送
                        AccountActionRow(
                            icon = rememberAppIconPainter(AppIcon.CLOUD_UPLOAD),
                            label = if (isSyncing && syncingAction == BangumiSyncAction.PUSH) {
                                stringResource(R.string.bangumi_account_pushing)
                            } else {
                                stringResource(R.string.bangumi_account_push)
                            },
                            onClick = { viewModel.pushToRemote() },
                            enabled = !isSyncing,
                            busy = isSyncing && syncingAction == BangumiSyncAction.PUSH
                        )
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
                Spacer(modifier = Modifier.height(24.dp))
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
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
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
