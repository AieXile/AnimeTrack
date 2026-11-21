package com.aiexile.animetrack.data.player.webdav

import com.aiexile.animetrack.domain.playback.SubtitleSpec
import okio.Buffer
import java.io.IOException
import java.io.InterruptedIOException

/** 限制单集的外挂候选数，以及实际交给 Media3 的单轨文件大小。 */
internal object WebDavSubtitleBudget {
    const val MAX_TRACKS = 3
    const val MAX_BYTES = 64L * 1024 * 1024
    const val TRACK_ID_BASE = 200
    // ASS 需要整条读完才开始显示，慢速但仍有数据的传输不应 8 秒就被切断。
    const val CALL_TIMEOUT_SECONDS = 90L
    const val READ_TIMEOUT_SECONDS = 20L

    fun isTrackId(id: String?): Boolean = id?.toIntOrNull()?.let { it in TRACK_ID_BASE until TRACK_ID_BASE + MAX_TRACKS } == true

    fun isCandidate(name: String, videoBase: String): Boolean =
        SubtitleSpec.isSupported(name) && SubtitleSpec.belongsTo(name, videoBase)

    /** 读到 EOF 或超限为止；长度未知也不会持续扩容。 */
    fun readBounded(bytes: Buffer, position: Long, declaredLength: Long, read: (ByteArray, Int) -> Int): Long {
        val limit = MAX_BYTES - position
        if (limit < 0 || declaredLength > limit) throw WebDavSubtitleException()
        val scratch = ByteArray(8 * 1024)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            // 多读至多 1 字节，区分刚好达到上限和实际超过上限。
            val count = read(scratch, minOf(scratch.size.toLong(), limit - bytes.size + 1).toInt())
            if (count == -1) break
            if (count <= 0 || bytes.size + count > limit) throw WebDavSubtitleException()
            bytes.write(scratch, 0, count)
        }
        if (bytes.size == 0L && position == 0L) throw WebDavSubtitleException()
        return bytes.size
    }
}

/** 可区分外挂字幕失败与主视频数据源失败；不向界面传输地址或服务器信息。 */
internal class WebDavSubtitleException(cause: Throwable? = null) : IOException("WebDAV external subtitle failed", cause)
