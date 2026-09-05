package com.aiexile.animetrack.ui.feedback

import android.widget.Toast
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.FEEDBACK_ATTACHMENT_TYPE_FILE
import com.aiexile.animetrack.data.network.FEEDBACK_ATTACHMENT_TYPE_IMAGE
import com.aiexile.animetrack.data.network.FEEDBACK_ATTACHMENT_TYPE_LOG
import com.aiexile.animetrack.data.remote.FeedbackRepository
import com.aiexile.animetrack.data.remote.PendingAttachment
import com.aiexile.animetrack.ui.theme.isAppDarkTheme

/**
 * 聊天式反馈主页：浮动圆形返回/历史按钮 + 消息气泡列表 + 底部大圆角输入框。
 * 样式参考魅族风聊天界面（仅布局样式，无截图文案）。
 */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateLogin: () -> Unit
) {
    val viewModel: FeedbackViewModel = viewModel(factory = FeedbackViewModel.Factory())
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val context = LocalContext.current

    var input by remember { mutableStateOf("") }

    // 错误提示（一次性消费）
    LaunchedEffect(sendError) {
        sendError?.let { error ->
            val message = when (error) {
                is FeedbackSendError.RateLimited ->
                    context.getString(R.string.feedback_error_rate_limited, error.retryAfterSeconds)
                FeedbackSendError.HourlyLimit ->
                    context.getString(R.string.feedback_error_hourly_limit)
                FeedbackSendError.Sensitive ->
                    context.getString(R.string.feedback_error_sensitive)
                FeedbackSendError.SessionClosed ->
                    context.getString(R.string.feedback_error_session_closed)
                FeedbackSendError.SessionNotFound ->
                    context.getString(R.string.feedback_error_session_not_found)
                FeedbackSendError.AttachmentFailed ->
                    context.getString(R.string.feedback_error_attachment)
                FeedbackSendError.LogGenerateFailed ->
                    context.getString(R.string.feedback_log_generate_failed)
                FeedbackSendError.AuthExpired ->
                    context.getString(R.string.feedback_error_auth_expired)
                FeedbackSendError.Network ->
                    context.getString(R.string.feedback_error_network)
                FeedbackSendError.Empty ->
                    context.getString(R.string.feedback_error_empty)
                FeedbackSendError.TooLong ->
                    context.getString(R.string.feedback_error_too_long, FeedbackRepository.MAX_CONTENT_LENGTH)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    // 新消息到达自动滚到底部
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 消息列表 / 空态 =====
            if (uiState.messages.isEmpty() && !uiState.isSending) {
                // 顶部预留：状态栏高度 + 浮动按钮行（8 + 44 + 8），问候语不被顶栏遮挡
                val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column(
                        modifier = Modifier.padding(start = 28.dp, top = topInset + 76.dp)
                    ) {
                        val username = viewModel.username.collectAsState().value
                        Text(
                            text = if (username.isNullOrBlank()) {
                                stringResource(R.string.feedback_greeting_no_name)
                            } else {
                                stringResource(R.string.feedback_greeting, username)
                            },
                            fontSize = 24.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.feedback_greeting_sub),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 顶部预留：状态栏高度 + 浮动按钮行（8 + 44 + 8），消息不被顶栏遮挡
                val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = topInset + 68.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        FeedbackMessageBubble(message)
                    }
                    if (uiState.isSending) {
                        item(key = "sending") {
                            Text(
                                text = stringResource(R.string.common_loading),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
            }

            // ===== 底部输入区 =====
            FeedbackChatInput(
                value = input,
                onValueChange = { input = it },
                attachments = uiState.attachments,
                onAttachmentsChange = { newList ->
                    // 与 VM 状态做差量同步：新增/移除分别提交
                    val current = uiState.attachments
                    newList.filterNot { current.contains(it) }.forEach { viewModel.addAttachment(it) }
                    current.filterNot { newList.contains(it) }.forEach { viewModel.removeAttachment(it) }
                },
                isSending = uiState.isSending,
                enabled = isLoggedIn,
                onSend = {
                    viewModel.send(input)
                    input = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // ===== 浮动圆形按钮：返回（左上）/ 历史（右上，有未读回复时带红点） =====
        val hasNewReplies by viewModel.hasNewReplies.collectAsState()
        // 页面重新可见时刷新未读状态（从历史界面返回后红点及时熄灭）
        androidx.lifecycle.compose.LifecycleResumeEffect(isLoggedIn) {
            if (isLoggedIn) viewModel.refreshNewReplies()
            onPauseOrDispose { }
        }
        FeedbackFloatingHeader(
            onBack = onBack,
            onHistory = onNavigateHistory,
            showHistoryBadge = hasNewReplies,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ===== 未登录遮罩提示 =====
        if (!isLoggedIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.feedback_login_required),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(SquircleShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onNavigateLogin() }
                            .padding(horizontal = 40.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_login_action),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** 顶部浮动圆形按钮行：左侧返回、右侧历史入口（有未读回复时红点提示） */
@Composable
private fun FeedbackFloatingHeader(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    showHistoryBadge: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeedbackCircleButton(
            icon = rememberAppIconPainter(AppIcon.ARROW_BACK),
            contentDescription = stringResource(R.string.feedback_back),
            onClick = onBack
        )
        Spacer(modifier = Modifier.weight(1f))
        FeedbackCircleButton(
            icon = rememberAppIconPainter(AppIcon.HISTORY),
            contentDescription = stringResource(R.string.feedback_history_entry),
            onClick = onHistory,
            showBadge = showHistoryBadge
        )
    }
}

/** 圆形浮动按钮（surfaceContainerLowest 底 + 微投影观感由底色区分），showBadge 时右上角红点 */
@Composable
private fun FeedbackCircleButton(
    icon: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
    showBadge: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        if (showBadge) {
            // 新回复红点：右上角小圆点
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 9.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}

/**
 * 底部输入区（参考图样式）：
 * - 附件 chips 行（有附件时展示，可移除）
 * - 大圆角容器：亮边描边，上行多行输入框，下行 [+] 附件按钮 / 「附带 App 日志」胶囊 / 圆形发送按钮
 * - [+] 打开菜单选择图片或文件；日志胶囊首次点击弹隐私说明，确认后以占位附件形式加入
 *   chips（发送时才导出实际日志文件）
 * 未登录时整体禁用。
 */
@Composable
fun FeedbackChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<PendingAttachment>,
    onAttachmentsChange: (List<PendingAttachment>) -> Unit,
    isSending: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var plusMenuOpen by remember { mutableStateOf(false) }

    // 附件选择：本地校验数量/大小后加入列表（上传在发送时进行）
    fun addAttachment(uri: android.net.Uri, kind: String) {
        if (attachments.size >= FeedbackRepository.MAX_ATTACHMENTS_PER_MESSAGE) {
            Toast.makeText(
                context,
                context.getString(R.string.feedback_attachment_limit, FeedbackRepository.MAX_ATTACHMENTS_PER_MESSAGE),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val meta = FeedbackRepository.readAttachmentMeta(context, uri) ?: return
        val mime = context.contentResolver.getType(uri)
        val resolvedKind = if (mime?.startsWith("image/") == true) {
            FEEDBACK_ATTACHMENT_TYPE_IMAGE
        } else {
            kind
        }
        if (meta.second >= 0) {
            val cap = if (resolvedKind == FEEDBACK_ATTACHMENT_TYPE_IMAGE) {
                FeedbackRepository.MAX_IMAGE_BYTES
            } else {
                FeedbackRepository.MAX_FILE_BYTES
            }
            if (meta.second > cap) {
                val message = if (resolvedKind == FEEDBACK_ATTACHMENT_TYPE_IMAGE) {
                    context.getString(R.string.feedback_image_too_large, cap / FeedbackRepository.MB_PER_BYTE)
                } else {
                    context.getString(R.string.feedback_file_too_large, cap / FeedbackRepository.MB_PER_BYTE)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return
            }
        }
        onAttachmentsChange(
            attachments + PendingAttachment(uri.toString(), meta.first, mime, resolvedKind)
        )
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { addAttachment(it, FEEDBACK_ATTACHMENT_TYPE_IMAGE) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { addAttachment(it, FEEDBACK_ATTACHMENT_TYPE_FILE) }
    }

    // 亮边配色：浅色主题灰底白边，深色主题深底浅灰边（边均亮于填充）
    val isDark = isAppDarkTheme()
    val fillColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerLow
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val edgeColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.surfaceContainerLowest
    val innerButtonColor = edgeColor

    Column(modifier = modifier.widthIn(max = 720.dp)) {
        if (attachments.isNotEmpty()) {
            FeedbackAttachmentChips(
                attachments = attachments,
                onRemove = { removed -> onAttachmentsChange(attachments.filterNot { it == removed }) },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 3.dp, shape = SquircleShape(28.dp), spotColor = MaterialTheme.colorScheme.outlineVariant)
                .clip(SquircleShape(28.dp))
                .background(fillColor)
                .border(width = 1.dp, color = edgeColor, shape = SquircleShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp),
                    enabled = enabled && !isSending,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.feedback_input_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // [+] 附件按钮：菜单选择图片 / 文件
                    Box {
                        FeedbackCircleSmallButton(
                            icon = rememberAppIconPainter(AppIcon.ADD),
                            contentDescription = stringResource(R.string.feedback_attach_image),
                            containerColor = innerButtonColor,
                            enabled = enabled && !isSending
                        ) { plusMenuOpen = true }
                        DropdownMenu(
                            expanded = plusMenuOpen,
                            onDismissRequest = { plusMenuOpen = false },
                            shape = SquircleShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.feedback_attach_image)) },
                                leadingIcon = { Icon(rememberAppIconPainter(AppIcon.IMAGE), contentDescription = null) },
                                onClick = {
                                    plusMenuOpen = false
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.feedback_attach_file)) },
                                leadingIcon = { Icon(rememberAppIconPainter(AppIcon.DESCRIPTION), contentDescription = null) },
                                onClick = {
                                    plusMenuOpen = false
                                    filePicker.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    // 「附带 App 日志」胶囊：选中后随反馈上传最近运行日志，再次点击取消
                    val logAttached = attachments.any { it.kind == FEEDBACK_ATTACHMENT_TYPE_LOG }
                    var showLogDialog by remember { mutableStateOf(false) }

                    fun addLogAttachment() {
                        if (attachments.size >= FeedbackRepository.MAX_ATTACHMENTS_PER_MESSAGE) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.feedback_attachment_limit,
                                    FeedbackRepository.MAX_ATTACHMENTS_PER_MESSAGE
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                            return
                        }
                        // 占位附件：uriString 留空，发送时才导出实际日志文件（见 FeedbackViewModel.send）
                        onAttachmentsChange(
                            attachments + PendingAttachment(
                                uriString = "",
                                fileName = context.getString(R.string.feedback_log_file_name),
                                mimeType = "application/zip",
                                kind = FEEDBACK_ATTACHMENT_TYPE_LOG
                            )
                        )
                    }

                    if (showLogDialog) {
                        AlertDialog(
                            onDismissRequest = { showLogDialog = false },
                            title = { Text(stringResource(R.string.feedback_log_dialog_title)) },
                            text = { Text(stringResource(R.string.feedback_log_dialog_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showLogDialog = false
                                        addLogAttachment()
                                    }
                                ) { Text(stringResource(R.string.feedback_log_dialog_confirm)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLogDialog = false }) {
                                    Text(stringResource(R.string.common_cancel))
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(SquircleShape(18.dp))
                            .background(
                                if (logAttached) MaterialTheme.colorScheme.primaryContainer
                                else innerButtonColor
                            )
                            .clickable(enabled = enabled && !isSending) {
                                if (logAttached) {
                                    onAttachmentsChange(attachments.filterNot { it.kind == FEEDBACK_ATTACHMENT_TYPE_LOG })
                                } else {
                                    showLogDialog = true
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = if (logAttached) rememberAppIconPainter(AppIcon.CHECK) else rememberAppIconPainter(AppIcon.DESCRIPTION),
                            contentDescription = null,
                            tint = if (logAttached) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.feedback_attach_log),
                            fontSize = 13.sp,
                            color = if (logAttached) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    FeedbackSendButton(
                        enabled = enabled && !isSending && (value.isNotBlank() || attachments.isNotEmpty()),
                        onClick = onSend
                    )
                }
            }
        }
    }
}

/** 附件 chips 行：图标 + 文件名 + 移除按钮 */
@Composable
private fun FeedbackAttachmentChips(
    attachments: List<PendingAttachment>,
    onRemove: (PendingAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments.size) { index ->
            val attachment = attachments[index]
            Row(
                modifier = Modifier
                    .clip(SquircleShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = if (attachment.kind == FEEDBACK_ATTACHMENT_TYPE_IMAGE) {
                        rememberAppIconPainter(AppIcon.IMAGE)
                    } else {
                        rememberAppIconPainter(AppIcon.DESCRIPTION)
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = attachment.fileName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CLOSE),
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onRemove(attachment) }
                )
            }
        }
    }
}

/** 输入区内的小圆形按钮（[+]） */
@Composable
private fun FeedbackCircleSmallButton(
    icon: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    containerColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
