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
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileOpen
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ImportResult
import com.aiexile.animetrack.ui.components.ImportPreviewDialog
import com.aiexile.animetrack.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManageScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onNavigateWebDAV: () -> Unit = {}
) {
    val viewModel: DataManageViewModel = viewModel(factory = DataManageViewModel.Factory())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isLoading by viewModel.isLoading.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val duplicateCount by viewModel.duplicateCount.collectAsState()
    val exportMarkdown by viewModel.exportMarkdown.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadConfig()
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearSnackbar()
        }
    }

    var showImportGuide by remember { mutableStateOf(false) }
    var localImportResult by remember { mutableStateOf<ImportResult?>(null) }
    var localDuplicateCount by remember { mutableStateOf(0) }
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
            if (exportMarkdown != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(exportMarkdown!!.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.data_manage_export_success),
                            duration = SnackbarDuration.Short
                        )
                    }
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.data_manage_export_failed, e.message ?: ""),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
        viewModel.clearExportMarkdown()
    }

    LaunchedEffect(exportMarkdown) {
        if (exportMarkdown != null) {
            createDocLauncher.launch("AnimeTrack_backup.md")
        }
    }

    importResult?.let { result ->
        if (localImportResult == null) {
            localImportResult = result
            localDuplicateCount = duplicateCount
        }
    }

    if (localImportResult != null) {
        ImportPreviewDialog(
            importResult = localImportResult!!,
            duplicateCount = localDuplicateCount,
            onConfirm = {
                val content = pendingContent
                localImportResult = null
                localDuplicateCount = 0
                pendingContent = null
                viewModel.resetImportState()
                content?.let { viewModel.importAnimesAndSync(it) }
            },
            onDismiss = {
                localImportResult = null
                localDuplicateCount = 0
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
                        text = stringResource(R.string.data_manage_title),
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
                    SettingsGroup(title = stringResource(R.string.data_manage_import_export)) {
                        Column {
                            SettingActionItem(
                                title = stringResource(R.string.data_manage_import_markdown),
                                subtitle = stringResource(R.string.data_manage_import_markdown_subtitle),
                                icon = Icons.Rounded.FileOpen,
                                onClick = { showImportGuide = true }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingActionItem(
                                title = stringResource(R.string.data_manage_export_data),
                                subtitle = stringResource(R.string.data_manage_export_data_subtitle),
                                icon = Icons.Rounded.FileDownload,
                                onClick = { viewModel.prepareExport() }
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.data_manage_cloud_sync)) {
                        SettingActionItem(
                            title = stringResource(R.string.data_manage_webdav_sync),
                            subtitle = stringResource(R.string.data_manage_webdav_sync_subtitle),
                            icon = Icons.Rounded.CloudUpload,
                            onClick = onNavigateWebDAV
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            if (isLoading) {
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
                            text = loadingMessage,
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
            .clip(SquircleShape(12.dp))
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
                    .clip(SquircleShape(2.dp))
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
                text = stringResource(R.string.data_manage_import_guide_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.data_manage_import_guide_format_hint),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val codeBlockShape = SquircleShape(12.dp)
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
                shape = SquircleShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.data_manage_select_file),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
