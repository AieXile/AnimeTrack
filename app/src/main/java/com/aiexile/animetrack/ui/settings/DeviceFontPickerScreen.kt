package com.aiexile.animetrack.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.AppLanguage
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.font.DeviceFontFamily
import com.aiexile.animetrack.data.font.DeviceFontScanner
import com.aiexile.animetrack.data.font.DeviceFontSelection
import com.aiexile.animetrack.data.font.ImportedFontStore
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.di.TopNoticeType
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.rememberDeviceFontPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本机字体选择页：合并展示「导入的字体 + 系统已安装字体」，支持搜索、逐行预览与导入。
 *
 * 导入的字体保存在应用私有目录，用户主动选择的字体不做语言覆盖过滤；
 * 系统字体仍按界面语言过滤，避免 emoji/生僻文字兜底字体混入列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFontPickerScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val appLanguage by settingsRepository.appLanguageFlow.collectAsStateWithLifecycle(
        initialValue = AppLanguage.SIMPLIFIED_CHINESE.name
    )
    val selection by settingsRepository.deviceFontSelectionFlow.collectAsStateWithLifecycle(
        initialValue = DeviceFontSelection()
    )

    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var importedFamilies by remember { mutableStateOf<List<DeviceFontFamily>>(emptyList()) }
    var systemFamilies by remember { mutableStateOf<List<DeviceFontFamily>>(emptyList()) }
    var importInProgress by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DeviceFontFamily?>(null) }

    LaunchedEffect(appLanguage) {
        val language = runCatching { AppLanguage.valueOf(appLanguage) }
            .getOrDefault(AppLanguage.SIMPLIFIED_CHINESE)
        loading = true
        withContext(Dispatchers.IO) {
            importedFamilies = ImportedFontStore.list(context)
            // 每次进入选择页都重新扫描：用户可能在 App 运行期间刚安装了字体
            systemFamilies = DeviceFontScanner.families(context, language, forceRefresh = true)
        }
        loading = false
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importInProgress = true
            val family = ImportedFontStore.import(context, uri)
            if (family == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.font_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                settingsRepository.applyDeviceFont(family)
                importedFamilies = withContext(Dispatchers.IO) { ImportedFontStore.list(context) }
                AppContainer.showTopNotice(
                    TopNoticeType.SUCCESS,
                    context.getString(R.string.font_import_success)
                )
            }
            importInProgress = false
        }
    }

    val allFamilies = remember(importedFamilies, systemFamilies) {
        importedFamilies + systemFamilies
    }
    val visibleFamilies = remember(allFamilies, query) {
        val keyword = query.trim()
        allFamilies.filter { family ->
            keyword.isEmpty() || family.name.contains(keyword, ignoreCase = true)
        }
    }

    AppScaffold(
        title = stringResource(R.string.font_device_picker_title),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    if (!importInProgress) importLauncher.launch(IMPORT_MIME_TYPES)
                },
                enabled = !importInProgress
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.FILE_INPUT),
                    contentDescription = stringResource(R.string.font_import_button),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { chrome ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                SettingsSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    hint = stringResource(R.string.font_device_search_hint),
                    autoFocus = false,
                    modifier = Modifier.padding(
                        top = chrome.content.calculateTopPadding() + 8.dp,
                        bottom = 8.dp
                    )
                )

                when {
                    loading -> ScanHint(
                        title = stringResource(R.string.font_device_scanning),
                        showProgress = true
                    )

                    visibleFamilies.isEmpty() -> ScanHint(
                        title = if (query.isBlank()) {
                            stringResource(R.string.font_device_empty)
                        } else {
                            stringResource(R.string.font_device_no_match)
                        },
                        showProgress = false,
                        action = if (query.isBlank() && !importInProgress) {
                            {
                                Button(onClick = { importLauncher.launch(IMPORT_MIME_TYPES) }) {
                                    Text(stringResource(R.string.font_import_button))
                                }
                            }
                        } else {
                            null
                        }
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .selectableGroup(),
                        contentPadding = PaddingValues(
                            bottom = chrome.content.calculateBottomPadding() + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleFamilies, key = { it.key }) { family ->
                            val selected = isSelected(family, selection)
                            DeviceFontRow(
                                family = family,
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        onBack()
                                    } else {
                                        scope.launch {
                                            settingsRepository.applyDeviceFont(family)
                                            AppContainer.showTopNotice(
                                                TopNoticeType.SUCCESS,
                                                context.getString(
                                                    R.string.font_device_applied,
                                                    family.name
                                                )
                                            )
                                            onBack()
                                        }
                                    }
                                },
                                onDelete = if (family.isImported) {
                                    { pendingDelete = family }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

            pendingDelete?.let { target ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text(stringResource(R.string.font_device_delete_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.font_device_delete_message,
                                target.name
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingDelete = null
                                scope.launch {
                                    val wasSelected = isSelected(target, selection)
                                    val deleted = ImportedFontStore.delete(context, target)
                                    if (!deleted) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.font_device_delete_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    if (wasSelected) {
                                        settingsRepository.clearDeviceFont()
                                    }
                                    importedFamilies = withContext(Dispatchers.IO) {
                                        ImportedFontStore.list(context)
                                    }
                                    AppContainer.showTopNotice(
                                        TopNoticeType.SUCCESS,
                                        context.getString(R.string.font_device_deleted, target.name)
                                    )
                                }
                            }
                        ) {
                            Text(stringResource(R.string.common_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }
        }
    }
}

/** 选中判断：优先比较实际文件（兼容导入/改名后的名称变化），旧值再按名称比较 */
private fun isSelected(family: DeviceFontFamily, selection: DeviceFontSelection): Boolean {
    if (selection.filePath.isNotBlank()) {
        return family.faces.any {
            it.filePath == selection.filePath && it.ttcIndex == selection.ttcIndex
        }
    }
    return family.name.equals(selection.familyName, ignoreCase = true)
}

private val IMPORT_MIME_TYPES = arrayOf(
    "font/ttf",
    "font/otf",
    "application/x-font-ttf",
    "application/x-font-opentype",
    "application/octet-stream"
)

@Composable
private fun ScanHint(
    title: String,
    showProgress: Boolean,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            action?.let {
                Spacer(modifier = Modifier.height(16.dp))
                it()
            }
        }
    }
}

/** 单个字体家族：名称与示例文案都用该字体自身渲染 */
@Composable
private fun DeviceFontRow(
    family: DeviceFontFamily,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val preview = rememberDeviceFontPreview(family) ?: FontFamily.Default
    val shape = SquircleShape(14.dp)
    val sample = stringResource(R.string.font_device_preview_sample)
    val importedTag = stringResource(R.string.font_device_imported_tag)
    val deleteLabel = stringResource(R.string.font_device_delete)
    val supportingLine = if (family.isImported) "$importedTag · $sample" else sample

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape
                    )
                } else {
                    Modifier
                }
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = family.name,
                fontFamily = preview,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = supportingLine,
                fontFamily = preview,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.DELETE),
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                painter = rememberAppIconPainter(AppIcon.CHECK),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
