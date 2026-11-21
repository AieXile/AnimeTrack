package com.aiexile.animetrack.ui.navigation

import android.os.Bundle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

internal const val WEB_DAV_SELECTION_KEY = "webdav_file_selection"
internal const val WEB_DAV_SELECTION_PATH = "path"
internal const val WEB_DAV_SELECTION_NAME = "name"

/** 保留原目录在返回栈；点选结果只交给其上新建的播放器。 */
internal fun NavController.playWebDavSelection(browser: NavBackStackEntry, path: String, name: String) {
    if (currentBackStackEntry !== browser) return
    val animeId = browser.arguments?.getInt("animeId") ?: 0
    val selection = Bundle().apply {
        putString(WEB_DAV_SELECTION_PATH, path)
        putString(WEB_DAV_SELECTION_NAME, name)
    }
    navigate(Routes.player(animeId, webDavPlay = true))
    getBackStackEntry(Routes.PLAYER).savedStateHandle[WEB_DAV_SELECTION_KEY] = selection
}
