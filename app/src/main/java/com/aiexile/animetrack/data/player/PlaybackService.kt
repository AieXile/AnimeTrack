package com.aiexile.animetrack.data.player

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * 后台播放服务：ExoPlayer 的常驻之家。
 *
 * - 播放器实例在此创建，页面退出/锁屏/切后台不影响播放；
 * - MediaSession 自动提供通知栏媒体卡片（播放/暂停/进度/下一集）与蓝牙/耳机按键支持；
 * - UI 层通过 MediaController（遥控器）远程指挥本服务内的播放器。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
    }

    private var mediaSession: MediaSession? = null

    private val okHttpClient by lazy { OkHttpClient() }

    override fun onCreate() {
        super.onCreate()
        val settingsRepository = AppContainer.getSettingsRepository()

        // 服务启动时读取一次的开关：更改后重新进入播放器生效（与原实现一致）
        val allowHardware = runBlocking(Dispatchers.IO) {
            settingsRepository.playerHardwareAcceleration.first()
        }
        // 自动连播关闭时：每集结尾暂停（仅影响自动过渡，手动切集不受限）
        val autoPlayNext = runBlocking(Dispatchers.IO) {
            settingsRepository.playerAutoPlayNext.first()
        }

        val player = ExoPlayer.Builder(this, createRenderersFactory(allowHardware))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .build()
        player.setPauseAtEndOfMediaItems(!autoPlayNext)

        mediaSession = MediaSession.Builder(this, player).build()
    }

    /**
     * 数据源工厂：每次创建时实时读取 WebDAV 凭证（低频调用）。
     * DefaultDataSource 对 http(s) 委托给 WebDAV 认证源，file:// 等本地协议自行处理——
     * 一个工厂同时覆盖 WebDAV 流式播放与本地文件播放。
     */
    private fun createDataSourceFactory(): DataSource.Factory = DataSource.Factory {
        val username = runBlocking(Dispatchers.IO) { AppContainer.getSettingsRepository().webdavUsername.first() }
        val password = runBlocking(Dispatchers.IO) { AppContainer.getSettingsRepository().webdavPassword.first() }
        val webdavSource = WebDAVDataSourceFactory(okHttpClient, username, password).createDataSource()
        DefaultDataSource(this, webdavSource)
    }

    /** 硬件加速关闭时强制软件解码（过滤硬解码器；结果为空则回退原列表防黑屏） */
    private fun createRenderersFactory(allowHardware: Boolean): RenderersFactory =
        object : DefaultRenderersFactory(this) {
            override fun buildVideoRenderers(
                context: android.content.Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                if (allowHardware) {
                    super.buildVideoRenderers(
                        context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
                        eventHandler, eventListener, allowedVideoJoiningTimeMs, out
                    )
                    return
                }
                val softwareOnlySelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    all.filterNot { it.hardwareAccelerated }.ifEmpty { all }
                }
                super.buildVideoRenderers(
                    context, extensionRendererMode, softwareOnlySelector, true,
                    eventHandler, eventListener, allowedVideoJoiningTimeMs, out
                )
            }
        }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // onTaskRemoved 无需覆写：media3 默认行为即“未在播放则 stopSelf，播放中保活”

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        Log.d(TAG, "PlaybackService destroyed")
        super.onDestroy()
    }
}
