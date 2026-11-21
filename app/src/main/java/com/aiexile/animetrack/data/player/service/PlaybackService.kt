package com.aiexile.animetrack.data.player.service

import com.aiexile.animetrack.data.player.webdav.PlayerWebDavHttpClient
import com.aiexile.animetrack.data.player.webdav.WebDavSubtitleException

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aiexile.animetrack.MainActivity
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.di.AppContainer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException

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
        private const val MEDIA_NOTIFICATION_CHANNEL_ID = "anime_playback"
        private const val SESSION_ACTIVITY_REQUEST_CODE = 1002
    }

    private var mediaSession: MediaSession? = null
    private var coilBitmapLoader: CoilBitmapLoader? = null
    private var sessionPlayer: WebQueueForwardingPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** onCreate 初始化一次；凭据变化经 DataSource.Factory 每次实时读取，此处只涉及证书信任模式 */
    private lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        val settingsRepository = AppContainer.getSettingsRepository()

        // 并行读取服务启动所需的设置（原先串行 4 次 DataStore IO，都在主线程 runBlocking）
        data class StartupSettings(
            val allowHardware: Boolean,
            val autoPlayNext: Boolean,
            val trustAllCerts: Boolean
        )

        val startup = runBlocking(Dispatchers.IO) {
            val hardware = async { settingsRepository.playerHardwareAcceleration.first() }
            val autoNext = async { settingsRepository.playerAutoPlayNext.first() }
            val trustAll = async { settingsRepository.playerWebdavTrustAllCerts.first() }
            StartupSettings(hardware.await(), autoNext.await(), trustAll.await())
        }

        okHttpClient = PlayerWebDavHttpClient.create(startup.trustAllCerts)

        // libass 使用 GL 合成；外挂字幕的原始数据仍需单独限制，不能算进视频缓冲预算。
        val player = try {
            buildLibAssPlayer(this, startup.allowHardware, startup.autoPlayNext)
        } catch (t: Throwable) {
            // libass 桥接层基于 media3 1.8 开发，与 1.10.1 存在版本兼容风险：
            // 构建失败时回退普通播放器保证视频可看，并留下完整堆栈供定位
            AppLogManager.e(TAG, "LibAss player build failed, fallback to plain player", t)
            buildPlainPlayer(this, startup.allowHardware, startup.autoPlayNext)
        }

        val wrappedPlayer = WebQueueForwardingPlayer(
            player = player,
            onWebSeekPrevious = {
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(PlaybackSessionCommands.ACTION_WEB_PREVIOUS, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            },
            onWebSeekNext = {
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(PlaybackSessionCommands.ACTION_WEB_NEXT, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
        )
        sessionPlayer = wrappedPlayer

        val bitmapLoader = CoilBitmapLoader(this)
        coilBitmapLoader = bitmapLoader

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(MEDIA_NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.media_notification_channel_name)
            .build()
            .apply {
                setSmallIcon(R.drawable.lucide_clapperboard)
            }
        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setSessionActivity(createSessionActivityPendingIntent())
            .setBitmapLoader(CacheBitmapLoader(bitmapLoader))
            .setCallback(createSessionCallback())
            .build()

        player.addAnalyticsListener(object : AnalyticsListener {
            private val window = Timeline.Window()

            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
                error: IOException,
                wasCanceled: Boolean
            ) {
                if (!wasCanceled || !isWebDavSubtitleFailure(error)) return
                if (eventTime.windowIndex !in 0 until eventTime.timeline.windowCount) return
                val item = eventTime.timeline.getWindow(eventTime.windowIndex, window).mediaItem
                val subtitles = item.localConfiguration?.subtitleConfigurations.orEmpty()
                if (item.mediaId != player.currentMediaItem?.mediaId || !item.mediaId.startsWith("webdav:") ||
                    subtitles.none { it.uri == loadEventInfo.dataSpec.uri }
                ) return
                // Media3 会单独禁用有错误的字幕轨，不一定会调用 onPlayerError。
                val status = generateSequence<Throwable>(error) { it.cause }.take(16)
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
                val detail = status?.let { "HTTP $it" } ?: (error.cause ?: error).javaClass.simpleName
                notifyWebDavSubtitleSkipped(item.mediaId, status)
                AppLogManager.w(TAG, "WebDAV 外挂字幕轨读取失败 ($detail)")
            }
        })

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val item = player.currentMediaItem ?: return
                val subtitles = item.localConfiguration?.subtitleConfigurations.orEmpty()
                if (!item.mediaId.startsWith("webdav:") || subtitles.isEmpty() || !isWebDavSubtitleFailure(error)) return
                val index = player.currentMediaItemIndex
                val position = player.currentPosition.coerceAtLeast(0L)
                val playWhenReady = player.playWhenReady
                // Listener 分发期间不重入播放器；若已退出、换片或主动重试，不覆盖新的请求。
                serviceScope.launch(Dispatchers.Main) {
                    if (player.playerError !== error || player.currentMediaItem != item) return@launch
                    player.replaceMediaItem(index, item.buildUpon().setSubtitleConfigurations(emptyList()).build())
                    player.seekTo(index, position)
                    player.prepare()
                    player.playWhenReady = playWhenReady
                    notifyWebDavSubtitleSkipped(item.mediaId)
                    AppLogManager.w(TAG, "WebDAV 外挂字幕加载失败，已跳过并恢复视频")
                }
            }
        })

        serviceScope.launch {
            settingsRepository.playerAutoPlayNext.collect { enabled ->
                player.setPauseAtEndOfMediaItems(!enabled)
            }
        }
    }

    private fun notifyWebDavSubtitleSkipped(mediaId: String, status: Int? = null) {
        mediaSession?.broadcastCustomCommand(
            SessionCommand(PlaybackSessionCommands.ACTION_WEBDAV_SUBTITLE_SKIPPED, Bundle.EMPTY),
            Bundle().apply {
                putString(PlaybackSessionCommands.EXTRA_MEDIA_ID, mediaId)
                status?.let { putInt(PlaybackSessionCommands.EXTRA_HTTP_STATUS, it) }
            }
        )
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createSessionCallback(): MediaSession.Callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_WEB_NAVIGATION, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == PlaybackSessionCommands.ACTION_SET_WEB_NAVIGATION) {
                sessionPlayer?.updateWebNavigation(PlaybackSessionCommands.parseWebNavigationArgs(args))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /** libass 特效字幕模式的播放器构建（ass-media 桥接，EFFECTS_OPEN_GL 渲染） */
    private fun buildLibAssPlayer(
        context: Context,
        allowHardware: Boolean,
        autoPlayNext: Boolean
    ): ExoPlayer {
        val handler = AssHandler(
            AssRenderType.EFFECTS_OPEN_GL,
            AssHandlerConfig(maxRenderPixels = 1920 * 1080)
        )
        val assParserFactory = AssSubtitleParserFactory(handler)
        val parserFactory = WebDavSubtitleParserFactory(assParserFactory)
        val extractors = DefaultExtractorsFactory().withAssMkvSupport(assParserFactory, handler)
        val player = ExoPlayer.Builder(context, createRenderersFactory(allowHardware).withAssSupport(handler))
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
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory(), extractors)
                .setSubtitleParserFactory(parserFactory)
                .setLoadErrorHandlingPolicy(subtitleAwareLoadErrorPolicy()))
            .build()
        try {
            handler.init(player)
        } catch (e: Throwable) {
            player.release()
            throw e
        }
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
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory())
                .setSubtitleParserFactory(WebDavSubtitleParserFactory(DefaultSubtitleParserFactory()))
                .setLoadErrorHandlingPolicy(subtitleAwareLoadErrorPolicy()))
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
    private fun createDataSourceFactory(): DataSource.Factory =
        PlaybackDataSourceFactory(this, okHttpClient)

    private fun createLoadControl() = PlaybackBufferControl()

    private fun subtitleAwareLoadErrorPolicy(): LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
            if (isWebDavSubtitleFailure(loadErrorInfo.exception)) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
    }

    private fun isWebDavSubtitleFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(16).any { it is WebDavSubtitleException }

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
        serviceScope.cancel()
        coilBitmapLoader?.release()
        coilBitmapLoader = null
        sessionPlayer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        Log.d(TAG, "PlaybackService destroyed")
        super.onDestroy()
    }
}
