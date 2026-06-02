package com.aiexile.animetrack.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.data.ImportResult
import com.aiexile.animetrack.ui.components.ImportPreviewDialog
import com.aiexile.animetrack.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManageScreen(
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit,
    onNavigateWebDAV: () -> Unit = {}
) {
    val viewModel: DataManageViewModel = viewModel(factory = DataManageViewModel.Factory())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadConfig(themeViewModel)
    }

    LaunchedEffect(viewModel.snackbarMessage) {
        viewModel.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearSnackbar()
        }
    }

    var showImportGuide by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var duplicateCount by remember { mutableStateOf(0) }
    var pendingContent by remember { mutableStateOf<String?>(null) }

    val importGuideSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
            content?.let { text ->
                pendingContent = text
                viewModel.parseMarkdown(text)
            }
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        uri?.let {
            val markdown = viewModel.exportMarkdown
            if (markdown != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(markdown.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "导出成功！",
                            duration = SnackbarDuration.Short
                        )
                    }
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "导出失败：${e.message}",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
        viewModel.clearExportMarkdown()
    }

    LaunchedEffect(viewModel.exportMarkdown) {
        if (viewModel.exportMarkdown != null) {
            createDocLauncher.launch("anime_card_backup.md")
        }
    }

    viewModel.importResult?.let { result ->
        if (importResult == null) {
            importResult = result
            duplicateCount = viewModel.duplicateCount
        }
    }

    if (importResult != null) {
        ImportPreviewDialog(
            importResult = importResult!!,
            duplicateCount = duplicateCount,
            onConfirm = {
                val content = pendingContent
                importResult = null
                duplicateCount = 0
                pendingContent = null
                viewModel.resetImportState()
                content?.let { viewModel.importAnimesAndSync(it) }
            },
            onDismiss = {
                importResult = null
                duplicateCount = 0
                pendingContent = null
                viewModel.resetImportState()
            }
        )
    }

    if (showImportGuide) {
        ImportGuideBottomSheet(
            sheetState = importGuideSheetState,
            onDismiss = { showImportGuide = false },
            onSelectFile = {
                showImportGuide = false
                fileLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*"))
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "数据管理",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    SettingsGroup(title = "导入与导出") {
                        Column {
                            SettingActionItem(
                                title = "导入 Markdown",
                                subtitle = "从 Markdown 文件导入番剧列表",
                                icon = Icons.Default.FileOpen,
                                onClick = { showImportGuide = true }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingActionItem(
                                title = "导出番剧数据",
                                subtitle = "将番剧列表导出为 Markdown 文件",
                                icon = Icons.Default.FileDownload,
                                onClick = { viewModel.prepareExport() }
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = "云端同步") {
                        SettingActionItem(
                            title = "WebDAV 同步",
                            subtitle = "配置 WebDAV 服务器以实现云端备份与恢复",
                            icon = Icons.Default.CloudUpload,
                            onClick = onNavigateWebDAV
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = viewModel.loadingMessage,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportGuideBottomSheet(
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit
) {
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "导入 Markdown",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "请确保你的 Markdown 文件符合以下格式：",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val codeBlockShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(codeBlockShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = """Now
孤独摇滚！
约会大作战IV

Want
异度侵入
CLANNAD

Already

2026.01.20
彻夜之歌 第二季 (依旧夯)

2026.01.13
游戏人生 (还行吧)

Dropped
某番剧名称""",
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onSelectFile()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "准备好了，选择文件",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
