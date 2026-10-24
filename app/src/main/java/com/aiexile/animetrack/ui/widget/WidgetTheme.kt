package com.aiexile.animetrack.ui.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import com.aiexile.animetrack.R
import com.aiexile.animetrack.di.AppContainer
import java.util.Locale

/**
 * 桌面组件的静态调色板。
 *
 * 组件由系统桌面以 RemoteViews 渲染，无法复用 app 内的 CompositionLocal / 动态取色，
 * 这里取「黑白简洁」预设的浅色与深色两套品牌色，跟随系统深浅色切换。
 * 新增主题时无需同步：组件保持稳定的中性外观，避免桌面与 app 内主题不一致带来的辨识度问题。
 */
data class WidgetPalette(
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val outline: Color,
    /** 根背景 9-patch：超椭圆圆角，任意拉伸下保持平滑曲率（低版本也可用） */
    val backgroundRes: Int,
    /** 角标胶囊背景（primary 实底），9-patch 保证低版本圆角 */
    val pillRes: Int,
    /** 封面占位块背景（超椭圆） */
    val placeholderRes: Int
)

fun widgetPalette(context: Context): WidgetPalette {
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    return if (night) {
        WidgetPalette(
            onSurface = Color(0xFFE8E8E8),
            onSurfaceVariant = Color(0xFFBDBDBD),
            primary = Color(0xFFE0E0E0),
            onPrimary = Color(0xFF000000),
            container = Color(0xFF2E2E2E),
            outline = Color(0xFF2C2C2C),
            backgroundRes = R.drawable.widget_bg_dark,
            pillRes = R.drawable.widget_pill_dark,
            placeholderRes = R.drawable.widget_placeholder_dark
        )
    } else {
        WidgetPalette(
            onSurface = Color(0xFF1C1C1C),
            onSurfaceVariant = Color(0xFF424242),
            primary = Color(0xFF1A1A1A),
            onPrimary = Color(0xFFFFFFFF),
            container = Color(0xFFF0F0F0),
            outline = Color(0xFFE0E0E0),
            backgroundRes = R.drawable.widget_bg_light,
            pillRes = R.drawable.widget_pill_light,
            placeholderRes = R.drawable.widget_placeholder_light
        )
    }
}

/**
 * 组件的 Context 使用 app 内语言设置（MainActivity 的 locale 覆盖只作用于 Activity，
 * 桌面渲染拿到的 Application Context 仍跟随系统语言）。
 */
fun widgetLocalizedContext(context: Context): Context {
    val language = AppContainer.getSettingsRepository().getAppLanguageBlocking()
    val locale = when (language) {
        "ENGLISH" -> Locale.forLanguageTag("en")
        "TRADITIONAL_CHINESE" -> Locale.forLanguageTag("zh-TW")
        else -> Locale.forLanguageTag("zh-CN")
    }
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(locale)
    return context.createConfigurationContext(configuration)
}
