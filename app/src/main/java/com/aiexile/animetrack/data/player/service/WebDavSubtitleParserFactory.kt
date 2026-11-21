package com.aiexile.animetrack.data.player.service

import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import com.aiexile.animetrack.data.player.webdav.WebDavSubtitleBudget
import com.aiexile.animetrack.data.player.webdav.WebDavSubtitleException

/** 标记 WebDAV 外挂轨的解析失败；不改内嵌字幕或其他来源的解析行为。 */
@UnstableApi
internal class WebDavSubtitleParserFactory(private val delegate: SubtitleParser.Factory) : SubtitleParser.Factory by delegate {
    override fun create(format: Format): SubtitleParser {
        if (format.containerMimeType != null || !WebDavSubtitleBudget.isTrackId(format.id)) {
            return delegate.create(format)
        }
        val parser = parseSafely { delegate.create(format) }
        return object : SubtitleParser by parser {
            override fun parse(
                data: ByteArray,
                offset: Int,
                length: Int,
                outputOptions: SubtitleParser.OutputOptions,
                output: Consumer<CuesWithTiming>
            ) = parseSafely { parser.parse(data, offset, length, outputOptions, output) }
        }
    }

    private inline fun <T> parseSafely(block: () -> T): T = try {
        block()
    } catch (e: RuntimeException) {
        throw WebDavSubtitleException(e)
    }
}
