package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.components.GlassEffectPreviewContent
import com.aiexile.animetrack.ui.components.IconPackPreviewContent
import com.aiexile.animetrack.ui.icons.IconPack
import kotlinx.coroutines.launch

@Composable
internal fun OnboardingIconEffectPage(
    settingsRepository: SettingsRepository,
    gateState: OnboardingGateState
) {
    val scope = rememberCoroutineScope()
    val chromeStyle by settingsRepository.chromeStyle
        .collectAsState(settingsRepository.cachedChromeStyle())
    // 高亮取点选结果，未点选时回落到持久化值，避免异步写入造成闪动
    val selectedChromeStyle = gateState.selectedChromeStyle ?: chromeStyle

    OnboardingStepScaffold(
        step = 2,
        title = stringResource(R.string.onboarding_icon_effect_title),
        description = stringResource(R.string.onboarding_icon_effect_description),
        requirement = OnboardingRequirement(
            text = stringResource(
                if (gateState.selectedIconPack != null) R.string.onboarding_requirement_icon_done
                else R.string.onboarding_requirement_icon
            ),
            satisfied = gateState.selectedIconPack != null
        )
    ) {
        Column {
            OnboardingSectionLabel(stringResource(R.string.onboarding_icon_style_label))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconPack.entries.forEach { pack ->
                    OnboardingOptionCard(
                        label = stringResource(iconPackLabelRes(pack)),
                        selected = gateState.selectedIconPack == pack,
                        onClick = {
                            gateState.selectIconPack(pack)
                            scope.launch { settingsRepository.setIconPack(pack) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        IconPackPreviewContent(
                            pack = pack,
                            iconSize = 16.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Column {
            OnboardingSectionLabel(stringResource(R.string.onboarding_glass_effect_label))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChromeStyle.entries.forEach { style ->
                    OnboardingOptionCard(
                        label = stringResource(style.labelRes),
                        selected = selectedChromeStyle == style,
                        onClick = {
                            gateState.selectChromeStyle(style)
                            scope.launch { settingsRepository.setChromeStyle(style) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        GlassEffectPreviewContent(
                            style = style,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.onboarding_requirement_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

internal fun iconPackLabelRes(pack: IconPack): Int = when (pack) {
    IconPack.MATERIAL_SYMBOLS -> R.string.appearance_icon_pack_material
    IconPack.LUCIDE -> R.string.appearance_icon_pack_lucide
}
