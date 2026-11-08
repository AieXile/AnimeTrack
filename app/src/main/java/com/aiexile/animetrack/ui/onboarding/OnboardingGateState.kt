package com.aiexile.animetrack.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.data.NavigationLabelMode
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.icons.IconPack
import com.aiexile.animetrack.ui.theme.ThemePreset

/** 向导页索引：3 页功能介绍 + 主题 + 图标与效果 + 导航 + 完成页 */
internal object OnboardingPages {
    const val INTRO_COUNT = 3
    const val THEME = 3
    const val ICON_EFFECT = 4
    const val NAVIGATION = 5
    const val READY = 6
    const val COUNT = 7

    /** 自定义设置步骤数（主题 / 图标与效果 / 导航） */
    const val CUSTOMIZE_STEP_COUNT = 3
}

/**
 * 向导的自定义选择态与步骤完成态。
 *
 * 选择态直接持有用户点选结果，而不是读持久化后的设置值：
 * 设置写入是异步的，若用持久化值做高亮，点击后会先按旧值（通常是第一项）
 * 渲染一帧再跳变到所选项，产生闪动。持久化只在后台同步执行。
 *
 * 只对「必须由用户显式点选」的项目做门控：
 * - 主题配色、图标风格、导航样式与导航内容；
 * - 深浅模式、界面材质允许沿用已持久化的默认值，不参与门控。
 */
@Stable
internal class OnboardingGateState(
    selectedThemePreset: ThemePreset? = null,
    selectedThemeMode: ThemeMode? = null,
    selectedIconPack: IconPack? = null,
    selectedChromeStyle: ChromeStyle? = null,
    selectedNavStyle: NavigationStyle? = null,
    selectedNavLabelMode: NavigationLabelMode? = null
) {
    var selectedThemePreset by mutableStateOf(selectedThemePreset)
        private set
    var selectedThemeMode by mutableStateOf(selectedThemeMode)
        private set
    var selectedIconPack by mutableStateOf(selectedIconPack)
        private set
    var selectedChromeStyle by mutableStateOf(selectedChromeStyle)
        private set
    var selectedNavStyle by mutableStateOf(selectedNavStyle)
        private set
    var selectedNavLabelMode by mutableStateOf(selectedNavLabelMode)
        private set

    fun selectThemePreset(preset: ThemePreset) {
        selectedThemePreset = preset
    }

    fun selectThemeMode(mode: ThemeMode) {
        selectedThemeMode = mode
    }

    fun selectIconPack(pack: IconPack) {
        selectedIconPack = pack
    }

    fun selectChromeStyle(style: ChromeStyle) {
        selectedChromeStyle = style
    }

    fun selectNavStyle(style: NavigationStyle) {
        selectedNavStyle = style
    }

    fun selectNavLabelMode(mode: NavigationLabelMode) {
        selectedNavLabelMode = mode
    }

    /** 当前步骤是否已完成；介绍页与完成页恒为已完成 */
    fun isStepComplete(page: Int): Boolean = when (page) {
        OnboardingPages.THEME -> selectedThemePreset != null
        OnboardingPages.ICON_EFFECT -> selectedIconPack != null
        OnboardingPages.NAVIGATION -> selectedNavStyle != null && selectedNavLabelMode != null
        else -> true
    }

    /** 可到达的最远页：第一个未完成步骤；全部完成时为完成页 */
    val maxReachedPage: Int
        get() = (0 until OnboardingPages.COUNT).firstOrNull { !isStepComplete(it) }
            ?: (OnboardingPages.COUNT - 1)

    companion object {
        val Saver: Saver<OnboardingGateState, Any> = listSaver<OnboardingGateState, Int>(
            save = {
                listOf(
                    it.selectedThemePreset?.ordinal ?: -1,
                    it.selectedThemeMode?.ordinal ?: -1,
                    it.selectedIconPack?.ordinal ?: -1,
                    it.selectedChromeStyle?.ordinal ?: -1,
                    it.selectedNavStyle?.ordinal ?: -1,
                    it.selectedNavLabelMode?.ordinal ?: -1
                )
            },
            restore = {
                OnboardingGateState(
                    selectedThemePreset = ThemePreset.entries.getOrNull(it[0]),
                    selectedThemeMode = ThemeMode.entries.getOrNull(it[1]),
                    selectedIconPack = IconPack.entries.getOrNull(it[2]),
                    selectedChromeStyle = ChromeStyle.entries.getOrNull(it[3]),
                    selectedNavStyle = NavigationStyle.entries.getOrNull(it[4]),
                    selectedNavLabelMode = NavigationLabelMode.entries.getOrNull(it[5])
                )
            }
        )
    }
}

@Composable
internal fun rememberOnboardingGateState(): OnboardingGateState =
    rememberSaveable(saver = OnboardingGateState.Saver) { OnboardingGateState() }
