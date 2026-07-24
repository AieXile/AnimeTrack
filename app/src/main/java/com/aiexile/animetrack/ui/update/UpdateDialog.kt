package com.aiexile.animetrack.ui.update

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.aiexile.animetrack.ui.components.SquircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.MarkdownText

@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val info = uiState.updateInfo
    val unknownError = stringResource(R.string.update_dialog_unknown_error)

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
                message = uiState.error ?: unknownError,
                onDismiss = {
                    viewModel.clearError()
                    // 强制更新时不允许彻底关闭弹窗，仅清除错误以便用户重试
                    if (info.isForceUpdate != true) {
                        viewModel.dismissUpdate()
                    }
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
                shaVerifyState = uiState.shaVerifyState,
                isSimulated = uiState.isSimulated,
                isForceUpdate = info.isForceUpdate,
                installError = uiState.installError,
                onUpdate = { if (uiState.isSimulated) viewModel.startSimulatedDownload() else viewModel.startDownload(context) },
                onInstall = { viewModel.installApk(context) },
                onRetryInstall = {
                    viewModel.clearInstallError()
                    viewModel.installApk(context)
                },
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
    shaVerifyState: ShaVerifyState,
    isSimulated: Boolean,
    isForceUpdate: Boolean,
    installError: String?,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onRetryInstall: () -> Unit,
    onRedownload: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit
) {
    val shaVerifyingText = stringResource(R.string.update_dialog_sha_verifying)
    val shaVerifiedText = stringResource(R.string.update_dialog_sha_verified)
    val shaFailedText = stringResource(R.string.update_dialog_sha_failed)
    val shaPendingText = stringResource(R.string.update_dialog_sha_pending)
    val forceUpdateText = stringResource(R.string.update_dialog_force_update)
    val notForceUpdateText = stringResource(R.string.update_dialog_not_force_update)
    val downloadNoticeText = stringResource(R.string.update_dialog_download_notice)

    AlertDialog(
        // 强制更新或下载进行中时禁止关闭（点击外部 / 返回键）
        onDismissRequest = if (isForceUpdate || isDownloading) ({}) else onDismiss,
        title = null,
        text = {
            Column {
                // "发现新版本"标题与版本号对比紧凑排列
                Text(
                    text = stringResource(R.string.update_dialog_new_version),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
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

                // SHA 校验状态（非模拟更新时持续显示）
                if (!isSimulated) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        val (shaText, shaColor) = when (shaVerifyState) {
                            ShaVerifyState.VERIFYING -> shaVerifyingText to MaterialTheme.colorScheme.onSurfaceVariant
                            ShaVerifyState.VERIFIED -> shaVerifiedText to MaterialTheme.colorScheme.primary
                            ShaVerifyState.FAILED -> shaFailedText to MaterialTheme.colorScheme.error
                            ShaVerifyState.NONE -> shaPendingText to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = shaText,
                            fontSize = 12.sp,
                            color = shaColor
                        )
                    }
                }

                // 是否为强制更新标识
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isForceUpdate) forceUpdateText else notForceUpdateText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isForceUpdate) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // GitHub 下载慢说明（小浅字）
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = downloadNoticeText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // 更新日志（最下方）
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    shape = SquircleShape(8.dp),
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
                                text = stringResource(R.string.update_dialog_no_changelog),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            MarkdownText(markdown = changelog)
                        }
                    }
                }

                // 安装失败提示（如未开启安装权限），留在弹窗内允许重试
                if (!installError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SquircleShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = installError,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = onRetryInstall,
                                contentPadding = PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 0.dp
                                )
                            ) {
                                Text(stringResource(R.string.update_dialog_retry_install))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (!isDownloading && !downloadComplete) {
                    // 强制更新时屏蔽"跳过该版本"与"取消"
                    if (!isForceUpdate) {
                        TextButton(onClick = onSkip) {
                            Text(stringResource(R.string.update_dialog_skip))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                    Button(onClick = onUpdate) {
                        Text(stringResource(R.string.update_dialog_update_now))
                    }
                } else if (downloadComplete) {
                    // 强制更新时屏蔽"稍后"
                    if (!isForceUpdate) {
                        TextButton(onClick = onRedownload) {
                            Text(stringResource(R.string.update_dialog_redownload))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.update_dialog_later))
                        }
                    } else {
                        TextButton(onClick = onRedownload) {
                            Text(stringResource(R.string.update_dialog_redownload))
                        }
                    }
                    // 校验失败时禁用安装按钮
                    Button(
                        onClick = onInstall,
                        enabled = shaVerifyState != ShaVerifyState.FAILED && shaVerifyState != ShaVerifyState.VERIFYING
                    ) {
                        Text(stringResource(R.string.update_dialog_install))
                    }
                } else if (isDownloading) {
                    // 强制更新时下载中也不允许关闭，仅显示进度
                    TextButton(onClick = {}, enabled = false) {
                        Text("$downloadProgress%")
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
                text = stringResource(R.string.update_dialog_up_to_date),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(R.string.update_dialog_up_to_date_message))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
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
                text = stringResource(R.string.update_dialog_failed),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        }
    )
}
