package com.aiexile.animetrack.data.player.service

import androidx.media3.common.FlagSet
import androidx.media3.common.PlaybackException
import com.aiexile.animetrack.ui.player.PlaybackFailureResolver
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.IdentityHashMap

/**
 * 会话侧播放器包装：在网页队列模式下接管上一集/下一集可用性与切集回调。
 *
 * 本地队列直接由 ExoPlayer 维护多条目播放列表；网页队列每次只向 ExoPlayer 提交单集直链，
 * 通过本包装向 MediaSession 暴露准确的上下集命令，并把系统通知栏/蓝牙按键转发回 UI 层解析下一集。
 */
@UnstableApi
class WebQueueForwardingPlayer(
    player: Player,
    private val onWebSeekPrevious: () -> Unit,
    private val onWebSeekNext: () -> Unit
) : ForwardingPlayer(player) {

    private var webNavigation = PlaybackSessionCommands.WebNavigationState()
    private var originalError: PlaybackException? = null
    private var sessionError: PlaybackException? = null
    private val listenerProxies = IdentityHashMap<Player.Listener, ListenerProxy>()

    fun updateWebNavigation(state: PlaybackSessionCommands.WebNavigationState) {
        if (webNavigation == state) return
        webNavigation = state
        val commands = availableCommands
        val listeners = synchronized(listenerProxies) { listenerProxies.keys.toList() }
        val events = Player.Events(
            FlagSet.Builder()
                .add(Player.EVENT_AVAILABLE_COMMANDS_CHANGED)
                .build()
        )
        listeners.forEach { listener ->
            listener.onAvailableCommandsChanged(commands)
            listener.onEvents(this, events)
        }
    }

    override fun addListener(listener: Player.Listener) {
        val proxy = synchronized(listenerProxies) {
            listenerProxies.getOrPut(listener) { ListenerProxy(listener) }
        }
        wrappedPlayer.addListener(proxy)
    }

    override fun removeListener(listener: Player.Listener) {
        val proxy = synchronized(listenerProxies) {
            listenerProxies.remove(listener)
        }
        if (proxy != null) {
            wrappedPlayer.removeListener(proxy)
        }
    }

    override fun getPlayerError(): PlaybackException? = asSessionError(super.getPlayerError())

    private fun asSessionError(error: PlaybackException?): PlaybackException? {
        if (originalError !== error) {
            originalError = error
            sessionError = error?.let(PlaybackFailureResolver::forSession)
        }
        return sessionError
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!webNavigation.isWebQueue) return base
        return base.buildUpon()
            .remove(COMMAND_SEEK_TO_PREVIOUS)
            .remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .remove(COMMAND_SEEK_TO_NEXT)
            .remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .addIf(COMMAND_SEEK_TO_PREVIOUS, webNavigation.hasPrevious)
            .addIf(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, webNavigation.hasPrevious)
            .addIf(COMMAND_SEEK_TO_NEXT, webNavigation.hasNext)
            .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, webNavigation.hasNext)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        if (!webNavigation.isWebQueue) return super.isCommandAvailable(command)
        return when (command) {
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> webNavigation.hasPrevious
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> webNavigation.hasNext
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasPreviousMediaItem(): Boolean =
        if (webNavigation.isWebQueue) webNavigation.hasPrevious else super.hasPreviousMediaItem()

    override fun hasNextMediaItem(): Boolean =
        if (webNavigation.isWebQueue) webNavigation.hasNext else super.hasNextMediaItem()

    override fun seekToPrevious() {
        if (webNavigation.isWebQueue) {
            if (webNavigation.hasPrevious) onWebSeekPrevious()
            return
        }
        super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        if (webNavigation.isWebQueue) {
            if (webNavigation.hasPrevious) onWebSeekPrevious()
            return
        }
        super.seekToPreviousMediaItem()
    }

    override fun seekToNext() {
        if (webNavigation.isWebQueue) {
            if (webNavigation.hasNext) onWebSeekNext()
            return
        }
        super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (webNavigation.isWebQueue) {
            if (webNavigation.hasNext) onWebSeekNext()
            return
        }
        super.seekToNextMediaItem()
    }

    /**
     * ForwardingPlayer 默认转发 onAvailableCommandsChanged 时会传入底层播放器未经修饰的 Commands，
     * 这里改写为包装后的 availableCommands，避免网页队列的上下集按钮被底层单集状态冲掉。
     */
    private inner class ListenerProxy(
        private val delegate: Player.Listener
    ) : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            delegate.onEvents(this@WebQueueForwardingPlayer, events)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            delegate.onTimelineChanged(timeline, reason)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            delegate.onMediaItemTransition(mediaItem, reason)
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            delegate.onTracksChanged(tracks)
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            delegate.onMediaMetadataChanged(mediaMetadata)
        }

        override fun onPlaylistMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            delegate.onPlaylistMetadataChanged(mediaMetadata)
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            delegate.onIsLoadingChanged(isLoading)
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            delegate.onAvailableCommandsChanged(this@WebQueueForwardingPlayer.availableCommands)
        }

        override fun onTrackSelectionParametersChanged(parameters: androidx.media3.common.TrackSelectionParameters) {
            delegate.onTrackSelectionParametersChanged(parameters)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            delegate.onPlaybackStateChanged(playbackState)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            delegate.onPlayWhenReadyChanged(playWhenReady, reason)
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            delegate.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            delegate.onIsPlayingChanged(isPlaying)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            delegate.onRepeatModeChanged(repeatMode)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            delegate.onShuffleModeEnabledChanged(shuffleModeEnabled)
        }

        override fun onPlayerError(error: PlaybackException) {
            delegate.onPlayerError(checkNotNull(asSessionError(error)))
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            delegate.onPlayerErrorChanged(asSessionError(error))
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            delegate.onPositionDiscontinuity(oldPosition, newPosition, reason)
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            delegate.onPlaybackParametersChanged(playbackParameters)
        }

        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
            delegate.onSeekBackIncrementChanged(seekBackIncrementMs)
        }

        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
            delegate.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        }

        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) {
            delegate.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        }

        override fun onAudioAttributesChanged(audioAttributes: androidx.media3.common.AudioAttributes) {
            delegate.onAudioAttributesChanged(audioAttributes)
        }

        override fun onVolumeChanged(volume: Float) {
            delegate.onVolumeChanged(volume)
        }

        override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) {
            delegate.onDeviceInfoChanged(deviceInfo)
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            delegate.onDeviceVolumeChanged(volume, muted)
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            delegate.onVideoSizeChanged(videoSize)
        }

        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            delegate.onSurfaceSizeChanged(width, height)
        }

        override fun onRenderedFirstFrame() {
            delegate.onRenderedFirstFrame()
        }

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            delegate.onCues(cueGroup)
        }

        override fun onMetadata(metadata: androidx.media3.common.Metadata) {
            delegate.onMetadata(metadata)
        }
    }
}