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
                shaVerifyState = uiState.shaVerifyState,
                isSimulated = uiState.isSimulated,
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
    shaVerifyState: ShaVerifyState,
    isSimulated: Boolean,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onRedownload: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit
) {
    val shaVerifyingText = stringResource(R.string.update_dialog_sha_verifying)
    val shaVerifiedText = stringResource(R.string.update_dialog_sha_verified)
    val shaFailedText = stringResource(R.string.update_dialog_sha_failed)

    AlertDialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
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

                // SHA 校验状态（非模拟更新时显示）
                if (!isSimulated && shaVerifyState != ShaVerifyState.NONE) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        val (shaText, shaColor) = when (shaVerifyState) {
                            ShaVerifyState.VERIFYING -> shaVerifyingText to MaterialTheme.colorScheme.onSurfaceVariant
                            ShaVerifyState.VERIFIED -> shaVerifiedText to MaterialTheme.colorScheme.primary
                            ShaVerifyState.FAILED -> shaFailedText to MaterialTheme.colorScheme.error
                            ShaVerifyState.NONE -> "" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = shaText,
                            fontSize = 12.sp,
                            color = shaColor
                        )
                    }
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
            }
        },
        confirmButton = {
            Row {
                if (!isDownloading && !downloadComplete) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.update_dialog_skip))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
                if (downloadComplete) {
                    TextButton(onClick = onRedownload) {
                        Text(stringResource(R.string.update_dialog_redownload))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_dialog_later))
                    }
                    // 校验失败时禁用安装按钮
                    Button(
                        onClick = onInstall,
                        enabled = shaVerifyState != ShaVerifyState.FAILED && shaVerifyState != ShaVerifyState.VERIFYING
                    ) {
                        Text(stringResource(R.string.update_dialog_install))
                    }
                } else if (isDownloading) {
                    TextButton(onClick = onDismiss, enabled = false) {
                        Text("$downloadProgress%")
                    }
                } else {
                    Button(onClick = onUpdate) {
                        Text(stringResource(R.string.update_dialog_update_now))
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
