package com.aiexile.animetrack.loadinglab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.ui.theme.AnimeTrackTheme
import com.aiexile.animetrack.ui.theme.ThemePreset

/**
 * LoadingLab 的 Preview 入口。只有 @Preview，没有任何业务依赖，
 * 放在 debug 源集里，release 包不会带这段代码。
 *
 * 动画在 Android Studio 的 Preview 里是“活的”：用 Preview 面板顶部的
 * 「Interactive / 动画」按钮（或直接切到 Interactive Mode）就能看到循环。
 */

// ---------------------------------------------------------------- 方案 A

@Preview(name = "A1 反色描线 · 黑白简洁 · 浅色", widthDp = 300, heightDp = 170, showBackground = true)
@Composable
private fun PreviewLoaderMonoLight() = LabTheme(false, ThemePreset.MONO_BLACK) { LoaderStrip() }

@Preview(name = "A2 反色描线 · 晨雾蓝 · 浅色", widthDp = 300, heightDp = 170, showBackground = true)
@Composable
private fun PreviewLoaderBlueLight() = LabTheme(false, ThemePreset.BLUE) { LoaderStrip() }

@Preview(name = "A3 反色描线 · 雾霭紫 · 深色", widthDp = 300, heightDp = 170, showBackground = true)
@Composable
private fun PreviewLoaderPurpleDark() = LabTheme(true, ThemePreset.PURPLE) { LoaderStrip() }

// ---------------------------------------------------------------- 方案 B

@Preview(name = "B1 旋转换面 · 黑白简洁 · 浅色", widthDp = 330, heightDp = 400, showBackground = true)
@Composable
private fun PreviewSpinMonoLight() = LabTheme(false, ThemePreset.MONO_BLACK) { SpinStageOnly() }

@Preview(name = "B2 旋转换面 · 晨雾蓝 · 深色", widthDp = 330, heightDp = 400, showBackground = true)
@Composable
private fun PreviewSpinBlueDark() = LabTheme(true, ThemePreset.BLUE) { SpinStageOnly() }

@Preview(name = "B3 旋转换面 · 雾霭紫 · 浅色", widthDp = 330, heightDp = 400, showBackground = true)
@Composable
private fun PreviewSpinPurpleLight() = LabTheme(false, ThemePreset.PURPLE) { SpinStageOnly() }

// ---------------------------------------------------------------- 内部小工具

@Composable
private fun LoaderStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LabSectionHeader(
            title = "A · 反色对页 + 笔触生长",
            caption = "32 / 56 / 80dp，同一份归一化路径",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuotoneStrokeLoader(loaderSize = 32.dp, strokeWidth = 1.8.dp)
            DuotoneStrokeLoader(loaderSize = 56.dp)
            DuotoneStrokeLoader(loaderSize = 80.dp, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun SpinStageOnly() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LabSectionHeader(
            title = "B · 旋转换面 + 形变接续",
            caption = "骨架翻出 → 线条描出 → 形变成卡片轮廓 → 内容翻入",
        )
        SpinRevealStage(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LabTheme(
    dark: Boolean,
    preset: ThemePreset,
    content: @Composable () -> Unit,
) {
    AnimeTrackTheme(darkTheme = dark, themePreset = preset) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                content()
            }
        }
    }
}
