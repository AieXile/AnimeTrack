package com.aiexile.animetrack.data.player

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

    private val okHttpClient by lazy {
        PlayerWebDavHttpClient.create(
            runBlocking(Dispatchers.IO) {
                AppContainer.getSettingsRepository().playerWebdavTrustAllCerts.first()
            }
        )
    }

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

        // 字幕渲染交给 libass（native 引擎）：特效/定位/卡拉OK全支持，
        // 且解析与栅格化发生在 native 堆，不再挤爆 Java 堆（历史 OOM 根因）。
        // EFFECTS_OPEN_GL：字幕作为视频特效在 GL 管线内合成，与 MediaSession 架构自洽。
        val player = try {
            buildLibAssPlayer(this, allowHardware, autoPlayNext)
        } catch (t: Throwable) {
            // libass 桥接层基于 media3 1.8 开发，与 1.10.1 存在版本兼容风险：
            // 构建失败时回退普通播放器保证视频可看，并留下完整堆栈供定位
            Log.e(TAG, "LibAss player build failed, fallback to plain player", t)
            buildPlainPlayer(this, allowHardware, autoPlayNext)
        }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    /** libass 特效字幕模式的播放器构建（ass-media 桥接，EFFECTS_OPEN_GL 渲染） */
    private fun buildLibAssPlayer(
        context: Context,
        allowHardware: Boolean,
        autoPlayNext: Boolean
    ): ExoPlayer {
        val player = ExoPlayer.Builder(context, createRenderersFactory(allowHardware))
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
            .setLoadControl(createLoadControl())
            .buildWithAssSupport(
                context = context,
                renderType = AssRenderType.EFFECTS_OPEN_GL,
                config = AssHandlerConfig(maxRenderPixels = 1920 * 1080),
                dataSourceFactory = createDataSourceFactory(),
                renderersFactory = createRenderersFactory(allowHardware)
            )
        player.setPauseAtEndOfMediaItems(!autoPlayNext)
        return player
    }

    /** 无 libass 的普通播放器构建（兼容性兜底） */
    private fun buildPlainPlayer(
        context: Context,
        allowHardware: Boolean,
        autoPlayNext: Boolean
    ): ExoPlayer {
        val player = ExoPlayer.Builder(context, createRenderersFactory(allowHardware))
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
            .setLoadControl(createLoadControl())
            .build()
        player.setPauseAtEndOfMediaItems(!autoPlayNext)
        return player
    }

    /**
     * 数据源工厂：每次创建时实时读取 WebDAV 凭证（低频调用）。
     * DefaultDataSource 对 http(s) 委托给 WebDAV 认证源，file:// 等本地协议自行处理——
     * 一个工厂同时覆盖 WebDAV 流式播放与本地文件播放。
     */
    private fun createDataSourceFactory(): DataSource.Factory = DataSource.Factory {
        val username = runBlocking(Dispatchers.IO) { AppContainer.getSettingsRepository().playerWebdavUsername.first() }
        val password = runBlocking(Dispatchers.IO) { AppContainer.getSettingsRepository().playerWebdavPassword.first() }
        val webdavSource = WebDAVDataSourceFactory(okHttpClient, username, password).createDataSource()
        DefaultDataSource(this, webdavSource)
    }

    /**
     * 收紧缓冲（历史 OOM 缓解措施，保留）：
     * 默认 DefaultLoadControl 允许视频 SampleQueue 增长至约 128MB
     * （DEFAULT_VIDEO_BUFFER_SIZE = 2000×64KB）、最多预缓 50s；高码率局域网源可快速灌满。
     * 此处收紧至 30s/48MB。
     * 注意：外挂字幕由 SingleSampleMediaPeriod 全量载入堆、不受此限制——
     * 若日志显示 LOAD-DONE type=text bytes 异常巨大，则根因为字幕 URL 错误而非缓冲策略。
     */
    private fun createLoadControl(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                /* bufferForPlaybackAfterRebufferMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            // 字节上限硬生效：默认 prioritizeTimeOverSizeThresholds=true 时，
            // 缓冲时长未达上限就一直加载、无视 targetBufferBytes（实测高码率源一路灌到 139MB
            // 导致堆顶格 + 卡死看门狗误杀）。改为 false 后 48MB 到顶即停。
            .setPrioritizeTimeOverSizeThresholds(false)
            .setTargetBufferBytes(48 * 1024 * 1024)
            .build()

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
