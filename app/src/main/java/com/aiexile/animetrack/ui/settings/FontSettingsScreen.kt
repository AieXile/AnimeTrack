package com.aiexile.animetrack.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

    val fontFamily by settingsRepository.fontFamilyFlow.collectAsState(initial = FontFamilyType.SYSTEM.name)
    val customFontPath by settingsRepository.customFontPathFlow.collectAsState(initial = "")
    val appLanguage by settingsRepository.appLanguageFlow.collectAsState(initial = AppLanguage.SIMPLIFIED_CHINESE.name)

    val currentFontType = remember(fontFamily) {
        runCatching { FontFamilyType.valueOf(fontFamily) }.getOrDefault(FontFamilyType.SYSTEM)
    }
    val currentLanguage = remember(appLanguage) {
        runCatching { AppLanguage.valueOf(appLanguage) }.getOrDefault(AppLanguage.SIMPLIFIED_CHINESE)
    }

    // 自定义字体导入：选择 ttf 文件
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching<String?> {
                    val fontsDir = File(context.filesDir, "fonts").apply { if (!exists()) mkdirs() }
                    val destFile = File(fontsDir, "custom_font.ttf")
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
                Toast.makeText(context, context.getString(R.string.font_import_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.font_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.font_title),
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                SettingsGroup(title = stringResource(R.string.font_title), subtitle = stringResource(R.string.font_select_font)) {
                    Column {
                        val systemFontDesc = stringResource(R.string.font_system_default_desc)
                        val misansFontDesc = stringResource(R.string.font_misans_desc)
                        val customFontNotImportedDesc = stringResource(R.string.font_not_imported)
                        FontFamilyType.entries.forEach { type ->
                            FontOptionRow(
                                title = type.displayName,
                                description = when (type) {
                                    FontFamilyType.SYSTEM -> systemFontDesc
                                    FontFamilyType.MISANS -> misansFontDesc
                                    FontFamilyType.CUSTOM -> customFontPath.ifBlank { customFontNotImportedDesc }
                                },
                                selected = currentFontType == type,
                                onClick = {
                                    if (type == FontFamilyType.CUSTOM && customFontPath.isBlank()) {
                                        Toast.makeText(context, context.getString(R.string.font_import_first), Toast.LENGTH_SHORT).show()
                                        return@FontOptionRow
                                    }
                                    scope.launch {
                                        settingsRepository.setFontFamily(type)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.font_custom_title), subtitle = stringResource(R.string.font_custom_subtitle)) {
                    Column {
                        val notImportedShort = stringResource(R.string.font_not_imported_short)
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
            }

            item {
                SettingsGroup(title = stringResource(R.string.font_language_title), subtitle = stringResource(R.string.font_language_subtitle)) {
                    Column {
                        AppLanguage.entries.forEach { language ->
                            FontOptionRow(
                                title = language.displayName,
                                description = "",
                                selected = currentLanguage == language,
                                onClick = {
                                    if (currentLanguage == language) return@FontOptionRow
                                    scope.launch {
                                        settingsRepository.setAppLanguage(language)
                                    }
                                    Toast.makeText(context, context.getString(R.string.font_restart_to_apply), Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FontOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            interactionSource = interactionSource
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
