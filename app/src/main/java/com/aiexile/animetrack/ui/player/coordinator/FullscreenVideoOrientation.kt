package com.aiexile.animetrack.ui.player.coordinator

/** 只保留当前媒体已收到的方向依据。尺寸未就绪时交给全屏状态保留当前方向。 */
internal class FullscreenVideoOrientation {
    var renderedLandscape: Boolean? = null
    var trackLandscape: Boolean? = null

    val landscape: Boolean? get() = renderedLandscape ?: trackLandscape

    fun onMediaItemTransition() {
        renderedLandscape = null
        trackLandscape = null
    }
}
