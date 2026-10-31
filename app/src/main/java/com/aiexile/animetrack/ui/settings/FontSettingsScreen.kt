package com.aiexile.animetrack.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.aiexile.animetrack.data.font.DeviceFontSelection
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.settings.SettingsOption
import com.aiexile.animetrack.ui.components.settings.SettingsOptionHost
import com.aiexile.animetrack.ui.components.settings.SettingsOptionRow
import com.aiexile.animetrack.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenDeviceFontPicker: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 搜索定位：高亮目标分组并滚动到位
    val highlightKey = rememberSettingsHighlight(Routes.FONT_SETTINGS)
    val listState = rememberLazyListState()
    val highlightAnchors = mapOf(
        "font" to 1,
        "device_font" to 2,
        "language" to 3
    )

    val fontFamily by settingsRepository.fontFamilyFlow.collectAsState(initial = FontFamilyType.SYSTEM.name)
    val deviceFontSelection by settingsRepository.deviceFontSelectionFlow.collectAsState(
        initial = DeviceFontSelection()
    )
    val appLanguage by settingsRepository.appLanguageFlow.collectAsState(initial = AppLanguage.SIMPLIFIED_CHINESE.name)

    val currentFontType = remember(fontFamily) {
        val parsed = runCatching { FontFamilyType.valueOf(fontFamily) }
            .getOrDefault(FontFamilyType.SYSTEM)
        // 旧 CUSTOM 已迁移到本机字体；迁移尚未完成的首帧也按本机字体展示
        if (parsed == FontFamilyType.CUSTOM) FontFamilyType.DEVICE else parsed
    }
    val currentLanguage = remember(appLanguage) {
        runCatching { AppLanguage.valueOf(appLanguage) }.getOrDefault(AppLanguage.SIMPLIFIED_CHINESE)
    }

    val systemFontDesc = stringResource(R.string.font_system_default_desc)
    val misansFontDesc = stringResource(R.string.font_misans_desc)
    val deviceFontNotSelectedDesc = stringResource(R.string.font_device_not_selected)
    val deviceFontDisplayName = deviceFontSelection.familyName.takeIf { it.isNotBlank() }

    // 字体候选：旧「自定义字体」已合并进本机字体，不再单独展示
    val fontOptions = FontFamilyType.entries
        .filterNot { it == FontFamilyType.CUSTOM }
        .map { type ->
            SettingsOption(
                value = type,
                label = type.displayName,
                supportingText = when (type) {
                    FontFamilyType.SYSTEM -> systemFontDesc
                    FontFamilyType.MISANS -> misansFontDesc
                    FontFamilyType.DEVICE -> deviceFontDisplayName ?: deviceFontNotSelectedDesc
                    FontFamilyType.CUSTOM -> deviceFontDisplayName ?: deviceFontNotSelectedDesc
                },
                enabled = type != FontFamilyType.DEVICE || deviceFontSelection.isConfigured
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
                                    if (type == FontFamilyType.DEVICE &&
                                        !deviceFontSelection.isConfigured
                                    ) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.font_device_select_first),
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
                            title = stringResource(R.string.font_device_title),
                            subtitle = stringResource(R.string.font_device_subtitle),
                            modifier = rememberHighlightModifier("device_font", highlightKey)
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
                                        text = deviceFontDisplayName ?: deviceFontNotSelectedDesc,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Button(onClick = onOpenDeviceFontPicker) {
                                    Text(stringResource(R.string.font_device_select_button))
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
