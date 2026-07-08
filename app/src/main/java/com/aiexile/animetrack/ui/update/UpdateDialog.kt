package com.aiexile.animetrack.ui.update

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aiexile.animetrack.ui.components.MarkdownText

@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val info = uiState.updateInfo

    LifecycleResumeEffect(Unit) {
        viewModel.checkPendingInstall(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(info?.versionName) {
        if (info != null && !uiState.isDownloading && !uiState.downloadComplete && !uiState.isSimulated) {
            viewModel.tryRecoverDownload(context)
        }
    }

    if (uiState.isUpToDate) {
        UpToDateDialog(onDismiss = { viewModel.dismissUpdate() })
        return
    }

    if (info == null) return

    when {
        uiState.error != null -> {
            ErrorDialog(
                message = uiState.error ?: "未知错误",
                onDismiss = {
                    viewModel.clearError()
                    viewModel.dismissUpdate()
                }
            )
        }
        else -> {
            UpdateAvailableDialog(
                currentVersion = uiState.currentVersion,
                newVersion = info.versionName,
                changelog = info.changelog,
                isDownloading = uiState.isDownloading,
                downloadProgress = uiState.downloadProgress,
                downloadComplete = uiState.downloadComplete,
                onUpdate = { if (uiState.isSimulated) viewModel.startSimulatedDownload() else viewModel.startDownload(context) },
                onInstall = { viewModel.installApk(context) },
                onRedownload = { if (!uiState.isSimulated) viewModel.redownload(context) },
                onDismiss = { viewModel.dismissUpdate() },
                onSkip = { if (uiState.isSimulated) viewModel.dismissUpdate() else viewModel.skipVersion() }
            )
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    currentVersion: String,
    newVersion: String,
    changelog: String,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadComplete: Boolean,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onRedownload: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
        title = {
            Text(
                text = "发现新版本",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = currentVersion,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "→",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = newVersion,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                    ) {
                        if (changelog.isBlank()) {
                            Text(
                                text = "暂无更新日志",
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            MarkdownText(markdown = changelog)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (!isDownloading && !downloadComplete) {
                    TextButton(onClick = onSkip) {
                        Text("跳过该版本")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
                if (downloadComplete) {
                    TextButton(onClick = onRedownload) {
                        Text("重新下载")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("稍后")
                    }
                    Button(onClick = onInstall) {
                        Text("安装")
                    }
                } else if (isDownloading) {
                    TextButton(onClick = onDismiss, enabled = false) {
                        Text("$downloadProgress%")
                    }
                } else {
                    Button(onClick = onUpdate) {
                        Text("立即更新")
                    }
                }
            }
        }
    )
}

@Composable
private fun UpToDateDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "已是最新版本",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("当前已是最新版本，无需更新。")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "更新失败",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}
