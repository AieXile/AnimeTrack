package com.aiexile.animetrack.data.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.aiexile.animetrack.domain.playback.SubtitleSpec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset

/** 仅供当前播放会话使用的有界副本，避免把临时 SAF 权限带进续播记录。 */
internal object ManualSubtitleFile {
    const val TRACK_ID = "manual-subtitle"
    private const val MAX_BYTES = 8 * 1024 * 1024

    data class Prepared(val config: MediaItem.SubtitleConfiguration, val file: File)

    suspend fun prepare(context: Context, uri: Uri, encoding: String?): Prepared {
        if (uri.scheme != "content") throw IOException("Not a document")
        val coroutine = currentCoroutineContext()
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: throw IOException("Document name unavailable")
        val mime = SubtitleSpec.mimeTypeForFileName(name) ?: throw IOException("Unsupported subtitle format")
        val directory = File(context.cacheDir, "manual-player-subtitles")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Subtitle cache unavailable")
        val file = File.createTempFile("subtitle-", "." + name.substringAfterLast('.'), directory)
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Document unavailable")
            input.use { source ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        coroutine.ensureActive()
                        val count = source.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total + 1))
                        if (count < 0) break
                        if (count == 0 || count > MAX_BYTES - total) throw IOException("Subtitle size limit exceeded")
                        out.write(buffer, 0, count)
                        total += count
                    }
                    if (total == 0) throw IOException("Empty subtitle")
                }
            }
            if (encoding != null) {
                val decoder = Charset.forName(encoding).newDecoder()
                val bytes = file.readBytes()
                val decoded = decoder.decode(ByteBuffer.wrap(bytes))
                val text = if (decoded.hasRemaining() && decoded[0] == '\uFEFF') {
                    CharBuffer.wrap(decoded, 1, decoded.length)
                } else decoded
                val output = Charsets.UTF_8.newEncoder().encode(text)
                if (output.remaining() > MAX_BYTES) throw IOException("Transcoded subtitle size limit exceeded")
                file.outputStream().channel.use { channel -> while (output.hasRemaining()) channel.write(output) }
            }
            coroutine.ensureActive()
            return Prepared(MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(mime).setId(TRACK_ID)
                .setLabel(name.substringBeforeLast('.')).setLanguage(SubtitleSpec.guessLanguage(name))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(), file)
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }
}
