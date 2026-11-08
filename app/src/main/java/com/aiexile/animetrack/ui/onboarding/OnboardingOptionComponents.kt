package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.rememberMonetPalette

/**
 * 向导统一选项卡：预览区 + 底部标签 + 右上角选中角标。
 * 图标风格、界面材质、导航样式与导航内容共用同一套圆角、间距与选中态。
 */
@Composable
internal fun OnboardingOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable BoxScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = SquircleShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) colorScheme.primary.copy(alpha = 0.08f)
                else colorScheme.surfaceContainer
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colorScheme.primary else colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(SquircleShape(10.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .border(
                        1.dp,
                        colorScheme.outlineVariant.copy(alpha = 0.5f),
                        SquircleShape(10.dp)
                    ),
                contentAlignment = Alignment.Center,
                content = preview
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) colorScheme.primary else colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CHECK),
                    contentDescription = null,
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/** 主题色卡：圆形色块 + 名称，选中时 primary 描边 + 对勾 */
@Composable
internal fun OnboardingSwatchOption(
    label: String,
    selected: Boolean,
    seedColor: Color?,
    onClick: () -> Unit
) {
    // 莫奈取色用壁纸动态配色的多色渐变（取色感），固定预设用单色 seed
    val monetSwatchBrush = if (seedColor == null) rememberMonetPalette()?.swatchBrush else null
    val swatchColor = seedColor ?: MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (monetSwatchBrush != null) Modifier.background(monetSwatchBrush)
                    else Modifier.background(swatchColor)
                )
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) selectedColor else outlineColor,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CHECK),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 短文本单选行：主题深浅模式等选项，与卡片共用描边与选中底色 */
@Composable
internal fun <T> OnboardingSegmentedRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val shape = SquircleShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        if (isSelected) colorScheme.primary.copy(alpha = 0.12f)
                        else colorScheme.surfaceContainer
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                        shape = shape
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 完成页已选配置摘要标签 */
@Composable
internal fun OnboardingSummaryChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(SquircleShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
