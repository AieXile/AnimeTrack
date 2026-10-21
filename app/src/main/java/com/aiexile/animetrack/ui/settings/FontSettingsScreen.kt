package com.aiexile.animetrack.ui.settings

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.AppLanguage
import com.aiexile.animetrack.data.FontFamilyType
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.di.TopNoticeType
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.settings.SettingsOption
import com.aiexile.animetrack.ui.components.settings.SettingsOptionHost
import com.aiexile.animetrack.ui.components.settings.SettingsOptionRow
import com.aiexile.animetrack.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 搜索定位：高亮目标分组并滚动到位
    val highlightKey = rememberSettingsHighlight(Routes.FONT_SETTINGS)
    val listState = rememberLazyListState()
    val highlightAnchors = mapOf("font" to 1, "custom_font" to 2, "language" to 3)

    val fontFamily by settingsRepository.fontFamilyFlow.collectAsState(initial = FontFamilyType.SYSTEM.name)
    val customFontPath by settingsRepository.customFontPathFlow.collectAsState(initial = "")
    val appLanguage by settingsRepository.appLanguageFlow.collectAsState(initial = AppLanguage.SIMPLIFIED_CHINESE.name)

    val currentFontType = remember(fontFamily) {
        runCatching { FontFamilyType.valueOf(fontFamily) }.getOrDefault(FontFamilyType.SYSTEM)
    }
    val currentLanguage = remember(appLanguage) {
        runCatching { AppLanguage.valueOf(appLanguage) }.getOrDefault(AppLanguage.SIMPLIFIED_CHINESE)
    }

    // 自定义字体导入：选择 ttf 文件，保留原文件名便于分辨
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching<String?> {
                    val fontsDir = File(context.filesDir, "fonts").apply { if (!exists()) mkdirs() }
                    // 从 URI 查询原始文件名（DISPLAY_NAME），失败时回退到 custom_font.ttf
                    val displayName = runCatching {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                        }
                    }.getOrNull()
                    val fileName = displayName?.takeIf { it.isNotBlank() } ?: "custom_font.ttf"
                    val destFile = File(fontsDir, fileName)
                    // 导入前清理 fonts 目录下其他 .ttf 文件（仅保留当前一个）
                    fontsDir.listFiles { f -> f.isFile && f.name.endsWith(".ttf", ignoreCase = true) }
                        ?.forEach { f ->
                            if (f.absolutePath != destFile.absolutePath) f.delete()
                        }
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching null
                    input.use { stream ->
                        destFile.outputStream().use { output ->
                            stream.copyTo(output)
                        }
                    }
                    destFile.absolutePath
                }.getOrNull()
            }
            if (success != null) {
                settingsRepository.setCustomFontPath(success)
                settingsRepository.setFontFamily(FontFamilyType.CUSTOM)
                AppContainer.showTopNotice(TopNoticeType.SUCCESS, context.getString(R.string.font_import_success))
            } else {
                Toast.makeText(context, context.getString(R.string.font_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val systemFontDesc = stringResource(R.string.font_system_default_desc)
    val misansFontDesc = stringResource(R.string.font_misans_desc)
    val customFontNotImportedDesc = stringResource(R.string.font_not_imported)
    val notImportedShort = stringResource(R.string.font_not_imported_short)
    // 自定义字体行描述显示文件名（如 LXG WenKai.ttf），便于分辨
    val customFontDisplayName = remember(customFontPath) {
        customFontPath.takeIf { it.isNotBlank() }?.let { File(it).name }
    }

    // 字体候选：自定义字体未导入时不可选，避免进入菜单再弹 toast
    val fontOptions = FontFamilyType.entries.map { type ->
        SettingsOption(
            value = type,
            label = type.displayName,
            supportingText = when (type) {
                FontFamilyType.SYSTEM -> systemFontDesc
                FontFamilyType.MISANS -> misansFontDesc
                FontFamilyType.CUSTOM -> customFontDisplayName ?: customFontNotImportedDesc
            },
            enabled = type != FontFamilyType.CUSTOM || customFontPath.isNotBlank()
        )
    }
    val languageOptions = AppLanguage.entries.map {
        SettingsOption(value = it, label = it.displayName)
    }

    AppScaffold(
        title = stringResource(R.string.font_title),
        onBack = onBack
    ) { chrome ->
        SettingsOptionHost(
            modifier = Modifier.fillMaxSize(),
            topInset = chrome.topInset,
            bottomInset = chrome.bottomInset,
            sources = chrome.sources
        ) {
            // 玻璃档滚动定位需补偿顶栏高度，避免目标项被顶栏遮挡
            LaunchedEffect(highlightKey) {
                highlightAnchors[highlightKey]?.let {
                    listState.animateScrollToItem(it, chrome.scrollCompensation)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(chrome.layout)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = chrome.content,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    item {
                        SettingsGroup(
                            title = stringResource(R.string.font_title),
                            subtitle = stringResource(R.string.font_select_font),
                            modifier = rememberHighlightModifier("font", highlightKey)
                        ) {
                            SettingsOptionRow(
                                title = stringResource(R.string.font_family_setting),
                                options = fontOptions,
                                selected = currentFontType,
                                onSelected = { type ->
                                    if (type == FontFamilyType.CUSTOM && customFontPath.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.font_import_first),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        scope.launch { settingsRepository.setFontFamily(type) }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        SettingsGroup(
                            title = stringResource(R.string.font_custom_title),
                            subtitle = stringResource(R.string.font_custom_subtitle),
                            modifier = rememberHighlightModifier("custom_font", highlightKey)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.font_current),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = customFontPath.ifBlank { notImportedShort },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Button(onClick = { fontPickerLauncher.launch("font/ttf") }) {
                                    Text(stringResource(R.string.font_import_button))
                                }
                            }
                        }
                    }

                    item {
                        SettingsGroup(
                            title = stringResource(R.string.font_language_title),
                            subtitle = stringResource(R.string.font_language_subtitle),
                            modifier = rememberHighlightModifier("language", highlightKey)
                        ) {
                            SettingsOptionRow(
                                title = stringResource(R.string.font_language_setting),
                                options = languageOptions,
                                selected = currentLanguage,
                                onSelected = { language ->
                                    if (currentLanguage != language) {
                                        scope.launch {
                                            settingsRepository.setAppLanguage(language)
                                            // 写入完成后重建 Activity，立即应用新语言
                                            (context as? android.app.Activity)?.recreate()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}
