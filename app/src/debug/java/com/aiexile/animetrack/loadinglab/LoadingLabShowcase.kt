package com.aiexile.animetrack.loadinglab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 两个产物的组合展示，Preview 与 debug 入口页共用这一份，避免两边各写一套布局。
 */
@Composable
internal fun LoadingLabShowcase(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        LabSectionHeader(
            title = "A · 反色对页 + 笔触生长",
            caption = "循环 1.74s：描线 0.90s → 停留 0.30s → 收笔 0.42s。对应原片 0–2.2s 的双联锦鲤。",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuotoneStrokeLoader(loaderSize = 32.dp, strokeWidth = 1.8.dp)
            DuotoneStrokeLoader(loaderSize = 56.dp)
            DuotoneStrokeLoader(loaderSize = 80.dp, strokeWidth = 3.dp)
        }

        LabSectionHeader(
            title = "B · 旋转换面 + 形变接续",
            caption = "每 4s 一轮：骨架绕左缘翻出 → 线条描出 → 形变成卡片轮廓 → 内容绕右缘翻入，错峰 45ms。",
        )
        SpinRevealStage(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun LabSectionHeader(title: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
