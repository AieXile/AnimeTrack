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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.data.ImportResult
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.components.ImportPreviewDialog
import androidx.compose.foundation.layout.WindowInsets

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    showBottomBar: Boolean = true,
    onNavigateAbout: () -> Unit,
    onNavigateCustomize: () -> Unit = {},
    onNavigateAppearance: () -> Unit = {},
    onNavigateFeatures: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory()),
    themeViewModel: com.aiexile.animetrack.ui.theme.ThemeViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val importGuideSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var showImportGuide by remember { mutableStateOf(false) }
    
    val uiState by viewModel.uiState.collectAsState()
    
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var duplicateCount by remember { mutableStateOf(0) }
    var pendingContent by remember { mutableStateOf<String?>(null) }
    
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
            val markdown = uiState.exportMarkdown
            if (markdown != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(markdown.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "导出成功！已保存至所选位置",
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

    LaunchedEffect(uiState.exportMarkdown) {
        if (uiState.exportMarkdown != null) {
            createDocLauncher.launch("anime_card_backup.md")
        }
    }
    
    uiState.importResult?.let { result ->
        if (importResult == null) {
            importResult = result
            duplicateCount = uiState.duplicateCount
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
    
    LaunchedEffect(uiState.syncCompleted) {
        if (uiState.syncCompleted) {
            val message = if (uiState.syncedCount > 0) {
                "已成功补全 ${uiState.syncedCount} 个番剧封面"
            } else {
                "导入完成，无封面需要补全"
            }
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.resetSyncCompleted()
        }
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 40.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = "settings",
                    onNavigate = onNavigate
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 16.dp)
        ) {
            if (uiState.isSyncing) {
                SyncProgressCard(
                    progress = uiState.syncProgress,
                    syncedCount = uiState.syncedCount,
                    totalToSync = uiState.totalToSync
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            LazyColumn(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    val currentPreset = themeViewModel?.themePreset?.collectAsState()?.value
                    val currentMode = themeViewModel?.themeMode?.collectAsState()?.value
                    val modeLabel = when (currentMode) {
                        com.aiexile.animetrack.model.ThemeMode.LIGHT -> "浅色"
                        com.aiexile.animetrack.model.ThemeMode.DARK -> "深色"
                        else -> "跟随系统"
                    }
                    SettingCard(
                        title = "外观与主题",
                        subtitle = currentPreset?.let { "${it.displayName} · $modeLabel" } ?: "清透蓝 · 跟随系统",
                        icon = Icons.Default.Palette,
                        onClick = onNavigateAppearance
                    )
                }
                item {
                    SettingCard(
                        title = "定制导航栏",
                        icon = Icons.Default.Navigation,
                        onClick = onNavigateCustomize
                    )
                }
                item {
                    SettingCard(
                        title = "功能",
                        icon = Icons.Default.Tune,
                        onClick = onNavigateFeatures
                    )
                }
                item {
                    SettingCard(
                        title = "导入 Markdown",
                        subtitle = "从 Markdown 文件导入番剧列表",
                        icon = Icons.Default.FileOpen,
                        onClick = { showImportGuide = true }
                    )
                }
                item {
                    SettingCard(
                        title = "导出番剧数据",
                        subtitle = "将番剧列表导出为 Markdown 文件",
                        icon = Icons.Default.FileDownload,
                        onClick = { viewModel.prepareExport() }
                    )
                }
                item {
                    SettingCard(
                        title = "关于",
                        icon = Icons.Default.Info,
                        onClick = onNavigateAbout
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.outlineVariant
            )
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = "支持的标题：",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = "正在观看：Now / 正在 / Watching",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = "计划观看：Want / 计划 / 打算 / Wish",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = "已看完：Already / 已看完 / Completed / Done",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = "已弃番：Dropped / 弃番 / 放弃",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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

@Composable
private fun SyncProgressCard(
    progress: String?,
    syncedCount: Int,
    totalToSync: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.outlineVariant
            )
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = progress ?: "正在补全封面信息...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$syncedCount / $totalToSync",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { if (totalToSync > 0) syncedCount.toFloat() / totalToSync else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
