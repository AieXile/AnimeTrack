package com.aiexile.animetrack.loadinglab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.ui.theme.AnimeTrackTheme
import com.aiexile.animetrack.ui.theme.ThemePreset

/**
 * 真机验收入口（仅 debug 源集 + debug 清单声明，release 包不存在这个 Activity）：
 * `adb shell am start -n com.aiexile.animetrack.debug/com.aiexile.animetrack.loadinglab.LoadingLabActivity`
 */
class LoadingLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LoadingLabScreen() }
    }
}

@Composable
private fun LoadingLabScreen() {
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    var preset by remember { mutableStateOf(ThemePreset.MONO_BLACK) }
    val presets = remember { listOf(ThemePreset.MONO_BLACK, ThemePreset.BLUE, ThemePreset.PURPLE) }

    AnimeTrackTheme(darkTheme = dark, themePreset = preset) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("LoadingLab", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "debug-only 预览，未接入任何业务页面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { item ->
                        FilterChip(
                            selected = preset == item,
                            onClick = { preset = item },
                            label = { Text(item.displayName) },
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("深色", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = dark, onCheckedChange = { dark = it })
                }

                LoadingLabShowcase()
            }
        }
    }
}
