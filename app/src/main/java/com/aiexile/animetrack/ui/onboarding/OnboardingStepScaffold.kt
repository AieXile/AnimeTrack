package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/** 步骤要求：常驻展示「本步要做什么」，满足后切换为完成态 */
internal data class OnboardingRequirement(
    val text: String,
    val satisfied: Boolean
)

/**
 * 自定义设置步骤的统一骨架：
 * 步骤进度 + 标题/说明 + 要求提示 + 可滚动内容区。
 * 标题统一左对齐，与设置页等二级页面保持同一套排版语言。
 */
@Composable
internal fun OnboardingStepScaffold(
    step: Int,
    title: String,
    description: String,
    requirement: OnboardingRequirement,
    modifier: Modifier = Modifier,
    showScrollHint: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(4.dp))
        OnboardingStepProgress(step = step)
        Spacer(Modifier.height(12.dp))
        OnboardingPageHeader(title = title, description = description)
        Spacer(Modifier.height(16.dp))
        OnboardingRequirementBanner(requirement = requirement)
        Spacer(Modifier.height(20.dp))
        // 仅内容确实超出视口且页面显式开启时才提示可滚动（目前只有导航定制页需要）
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                content()
                Spacer(Modifier.height(8.dp))
            }

            if (showScrollHint && scrollState.canScrollForward) {
                OnboardingScrollHint(modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** 页面标题：自定义步骤左对齐，介绍页/完成页居中 */
@Composable
internal fun OnboardingPageHeader(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    center: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = if (center) TextAlign.Center else TextAlign.Start
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            lineHeight = 20.sp
        )
    }
}

/** 分区标题：与选项区保持统一的 13sp Medium 样式 */
@Composable
internal fun OnboardingSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 「第 x 步 / 共 3 步」+ 分段进度 */
@Composable
private fun OnboardingStepProgress(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.onboarding_step_progress,
                step,
                OnboardingPages.CUSTOMIZE_STEP_COUNT
            ),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.4.sp
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(OnboardingPages.CUSTOMIZE_STEP_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

/** 步骤要求提示：未完成时常驻可见，完成后切换为完成态 */
@Composable
private fun OnboardingRequirementBanner(requirement: OnboardingRequirement) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = SquircleShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (requirement.satisfied) colorScheme.primary.copy(alpha = 0.10f)
                else colorScheme.surfaceContainer
            )
            .border(
                width = 1.dp,
                color = if (requirement.satisfied) colorScheme.primary.copy(alpha = 0.35f)
                else colorScheme.outlineVariant,
                shape = shape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = rememberAppIconPainter(
                if (requirement.satisfied) AppIcon.CHECK_CIRCLE else AppIcon.INFO
            ),
            contentDescription = null,
            tint = if (requirement.satisfied) colorScheme.primary
            else colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = requirement.text,
            fontSize = 12.sp,
            color = if (requirement.satisfied) colorScheme.primary
            else colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

/** 底部渐隐 + 下滑箭头：内容被视口裁切时提示「下面还有选项」 */
@Composable
private fun OnboardingScrollHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                )
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 2.dp)
                .clip(SquircleShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.KEYBOARD_ARROW_DOWN),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = stringResource(R.string.onboarding_scroll_hint),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
