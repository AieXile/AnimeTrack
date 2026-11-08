package com.aiexile.animetrack.ui.navigation

import androidx.compose.runtime.Composable
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.settings.HelpImproveDialog
import com.aiexile.animetrack.ui.settings.PrivacyConsentDialog

/**
 * 全局弹窗组：隐私政策同意（未同意时模态覆盖主界面）与首启「帮助改进应用」弹窗。
 *
 * 可见性状态（隐私同意状态 / 已展示标记 / splash 是否退出）与业务触发保留在
 * AnimeTrackApp，本文件只做渲染；组合顺序即弹窗叠放顺序。
 */
@Composable
internal fun GlobalDialogs(
    privacyConsentVisible: Boolean,
    onAcceptPrivacy: () -> Unit,
    helpImproveVisible: Boolean,
    settingsRepository: SettingsRepository,
    onConfirmHelpImprove: () -> Unit
) {
    // ===== 隐私政策同意（合规：首启弹窗，优先于新手引导与主界面） =====
    if (privacyConsentVisible) {
        PrivacyConsentDialog(
            onAccept = onAcceptPrivacy
        )
    }

    // ===== 首启「帮助改进应用」弹窗（崩溃追踪开关） =====
    if (helpImproveVisible) {
        HelpImproveDialog(
            settingsRepository = settingsRepository,
            onConfirm = onConfirmHelpImprove
        )
    }
}
