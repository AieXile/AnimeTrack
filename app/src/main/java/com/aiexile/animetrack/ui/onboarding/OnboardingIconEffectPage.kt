package com.aiexile.animetrack.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.components.GlassEffectPreviewCard
import com.aiexile.animetrack.ui.components.GlassEffectPreviewContent
import com.aiexile.animetrack.ui.components.IconPackPreviewCard
import com.aiexile.animetrack.ui.icons.IconPack
import kotlinx.coroutines.launch

// ==========================================
// 自定义页 2: 图标与效果（图标风格选择即时生效，整个向导界面图标实时切换预览）
// ==========================================
@Composable
internal fun OnboardingIconEffectPage(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    // 初始值取同步缓存（已持久化的值），避免首帧渲染默认态后跳变的闪变
    val iconPack by settingsRepository.iconPack.collectAsState(settingsRepository.cachedIconPack())
    val capsuleAdvancedBlur by settingsRepository.capsuleAdvancedBlurEnabled
        .collectAsState(settingsRepository.cachedCapsuleAdvancedBlur())
    val capsuleLiquidGlass by settingsRepository.capsuleLiquidGlassEnabled
        .collectAsState(settingsRepository.cachedCapsuleLiquidGlass())

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        PageHeader(
            title = stringResource(R.string.onboarding_icon_effect_title),
            description = stringResource(R.string.onboarding_icon_effect_description)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 图标风格：点选后整个向导界面的图标立即切换预览
        SectionLabel(text = stringResource(R.string.onboarding_icon_style_label))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconPackPreviewCard(
                modifier = Modifier.weight(1f),
                pack = IconPack.MATERIAL_SYMBOLS,
                label = stringResource(R.string.appearance_icon_pack_material),
                selected = iconPack == IconPack.MATERIAL_SYMBOLS,
                onClick = { scope.launch { settingsRepository.setIconPack(IconPack.MATERIAL_SYMBOLS) } }
            )
            IconPackPreviewCard(
                modifier = Modifier.weight(1f),
                pack = IconPack.LUCIDE,
                label = stringResource(R.string.appearance_icon_pack_lucide),
                selected = iconPack == IconPack.LUCIDE,
                onClick = { scope.launch { settingsRepository.setIconPack(IconPack.LUCIDE) } }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 玻璃效果：悬浮胶囊与悬浮按钮的质感，高级模糊与液态玻璃互斥
        SectionLabel(text = stringResource(R.string.onboarding_glass_effect_label))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassEffectPreviewCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.appearance_glass_effect_standard),
                selected = !capsuleAdvancedBlur && !capsuleLiquidGlass,
                onClick = {
                    scope.launch {
                        settingsRepository.setCapsuleAdvancedBlurEnabled(false)
                        settingsRepository.setCapsuleLiquidGlassEnabled(false)
                    }
                }
            ) {
                GlassEffectPreviewContent(advancedBlur = false, liquidGlass = false)
            }
            GlassEffectPreviewCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.nav_custom_advanced_blur),
                selected = capsuleAdvancedBlur,
                onClick = {
                    scope.launch {
                        // 与液态玻璃互斥：开启高级模糊时关闭液态玻璃
                        settingsRepository.setCapsuleAdvancedBlurEnabled(true)
                        settingsRepository.setCapsuleLiquidGlassEnabled(false)
                    }
                }
            ) {
                GlassEffectPreviewContent(advancedBlur = true, liquidGlass = false)
            }
            GlassEffectPreviewCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.nav_custom_liquid_glass),
                selected = capsuleLiquidGlass,
                onClick = {
                    scope.launch {
                        // 与高级模糊互斥：开启液态玻璃时关闭高级模糊
                        settingsRepository.setCapsuleLiquidGlassEnabled(true)
                        settingsRepository.setCapsuleAdvancedBlurEnabled(false)
                    }
                }
            ) {
                GlassEffectPreviewContent(advancedBlur = false, liquidGlass = true)
            }
        }
    }
}
