package com.aiexile.animetrack.ui.navigation

import android.content.Context
import android.content.Intent
import com.aiexile.animetrack.MainActivity

/**
 * 桌面组件点击后的导航目标。
 *
 * 项目没有系统 URI deep link，组件通过 Intent extra 启动 MainActivity，
 * 由 [com.aiexile.animetrack.ui.navigation.AnimeTrackApp] 在导航就绪后消费。
 */
sealed interface WidgetEntry {

    data class AnimeDetail(val animeId: Int) : WidgetEntry

    data class MainTab(val route: String) : WidgetEntry

    companion object {
        const val DEST_ANIME_DETAIL = "anime_detail"
        const val DEST_HOME = "home"
        const val DEST_FAVORITES = "favorites"
        const val DEST_SCHEDULE = "schedule"

        const val EXTRA_DESTINATION = "com.aiexile.animetrack.extra.WIDGET_DESTINATION"
        const val EXTRA_ANIME_ID = "com.aiexile.animetrack.extra.WIDGET_ANIME_ID"

        fun parse(intent: Intent?): WidgetEntry? {
            val destination = intent?.getStringExtra(EXTRA_DESTINATION) ?: return null
            return when (destination) {
                DEST_ANIME_DETAIL -> intent
                    .getIntExtra(EXTRA_ANIME_ID, -1)
                    .takeIf { it > 0 }
                    ?.let(::AnimeDetail)
                DEST_HOME, DEST_FAVORITES, DEST_SCHEDULE -> MainTab(destination)
                else -> null
            }
        }
    }
}

/** 包装一次点击请求：nonce 使重复点击同一目标也能被 LaunchedEffect 重新消费 */
data class WidgetEntryRequest(
    val entry: WidgetEntry,
    val nonce: Long = System.nanoTime()
)

/** 构建组件条目/空白区域的启动 Intent（Glance 的 actionStartActivity 自行生成 PendingIntent） */
fun widgetEntryIntent(context: Context, entry: WidgetEntry): Intent {
    return Intent(context, MainActivity::class.java).apply {
        putExtra(WidgetEntry.EXTRA_DESTINATION, entry.destinationId())
        if (entry is WidgetEntry.AnimeDetail) {
            putExtra(WidgetEntry.EXTRA_ANIME_ID, entry.animeId)
        }
        // singleTop：app 已在前台时复用 Activity 走 onNewIntent，避免重建整个界面
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

private fun WidgetEntry.destinationId(): String = when (this) {
    is WidgetEntry.AnimeDetail -> WidgetEntry.DEST_ANIME_DETAIL
    is WidgetEntry.MainTab -> route
}
