package com.aiexile.animetrack.data.media

import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

internal data class LocalMediaSidecarEvidence(
    val bangumiId: Int? = null,
    val tmdbId: Int? = null,
    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: String? = null,
    val offset: Int? = null,
    val movie: Boolean = false,
    val conflict: Boolean = false
)

/** 只识别 SAF 已枚举的边车；不会按 XML 中的路径或 URL 请求文件。 */
internal class LocalMediaSidecarIndex(files: Collection<LocalMediaScanFile>) {
    private val filesByPath = files.groupBy { pathKey(it.relativePath) }
    private val cache = mutableMapOf<String, LocalMediaSidecarEvidence?>()

    suspend fun readFor(relativePath: String, fileName: String, open: suspend (LocalMediaScanFile) -> ByteArray?): LocalMediaSidecarEvidence {
        val directory = relativePath.substringBeforeLast('/', "")
        val stem = fileName.substringBeforeLast('.')
        suspend fun read(parent: String, name: String): LocalMediaSidecarEvidence? {
            val path = pathKey(if (parent.isEmpty()) name else "$parent/$name")
            if (path in cache) return cache[path]
            val entries = filesByPath[path].orEmpty()
            val result = when (entries.size) {
                0 -> return null
                1 -> try {
                    open(entries.single())?.let { bytes ->
                        when (name) {
                            "bangumi.ini" -> LocalMediaSidecarParser.parseIni(bytes)
                            else -> LocalMediaSidecarParser.parseNfo(bytes, name)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null // 损坏或不可读的单个边车不妨碍视频入库。
                }
                else -> LocalMediaSidecarEvidence(conflict = true)
            }
            return result.also { cache[path] = it }
        }

        val episode = read(directory, "$stem.nfo")
        val season = read(directory, "season.nfo")
        val ancestry = generateSequence(directory) { parent ->
            parent.takeIf(String::isNotEmpty)?.substringBeforeLast('/', "")
        }.takeWhile { parent ->
            val relative = if (parent.isEmpty()) directory else directory.removePrefix("$parent/")
            parent == directory || relative.split('/').all { it.isBlank() || structuralDirectory.matches(it) }
        }.toList()
        val shows = ancestry.flatMap { listOfNotNull(read(it, "tvshow.nfo"), read(it, "movie.nfo")) }
        val inis = ancestry.mapNotNull { read(it, "bangumi.ini") }
        val show = shows.firstOrNull()
        val ini = inis.firstOrNull()
        // 番剧级元数据不覆盖同名剧集 NFO 的明确季/集；分季 NFO 的标题不是番名。
        val parts = listOfNotNull(episode, season) + shows + inis
        val bangumi = parts.mapNotNull { it.bangumiId }.distinct()
        val tmdb = parts.mapNotNull { it.tmdbId }.distinct()
        val seasons = listOfNotNull(episode?.season, season?.season, show?.season).distinct()
        val named = when {
            episode?.movie == true -> episode
            else -> show
        }
        val offsets = inis.mapNotNull { it.offset }.distinct()
        val hasMultipleMediaTypes = (listOfNotNull(episode?.takeIf { it.movie }) + shows).map { it.movie }.distinct().size > 1
        return LocalMediaSidecarEvidence(
            bangumi.singleOrNull(), tmdb.singleOrNull(), named?.title, named?.originalTitle,
            named?.year ?: season?.year, seasons.singleOrNull(), episode?.episode, ini?.offset,
            named?.movie == true, parts.any { it.conflict } || bangumi.size > 1 || tmdb.size > 1 ||
                seasons.size > 1 || offsets.size > 1 || hasMultipleMediaTypes
        )
    }

    internal companion object {
        private val structuralDirectory = Regex("(?i)^(?:season[ ._-]*\\d+|s\\d+|第[一二三四五六七八九十0-9]+[季期]|specials?|extras?|sp|(?:bd|web)[ ._-]?(?:rip|dl)?|\\d{3,4}p|正片|特典)$")
        private fun pathKey(path: String): String = path.substringBeforeLast('/', "").let { parent ->
            "$parent/${path.substringAfterLast('/').lowercase(Locale.ROOT)}"
        }

        fun supports(name: String): Boolean = name.equals("bangumi.ini", true) ||
            name.endsWith(".nfo", true)
    }
}

internal object LocalMediaSidecarParser {
    const val MAX_BYTES = 256 * 1024
    private val number = Regex("(?:0|[0-9]{1,4})(?:\\.[0-9]{1,3})?")
    private val fields = setOf("title", "originaltitle", "year", "season", "seasonnumber", "episode", "tmdbid", "bangumiid", "bgmid", "uniqueid", "showtitle")

    fun readBounded(stream: InputStream): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > MAX_BYTES) return null
            output.write(buffer, 0, count)
        }
    }

    fun parseIni(bytes: ByteArray): LocalMediaSidecarEvidence? {
        if (bytes.size > MAX_BYTES) return null
        var inSection = false
        val ids = mutableSetOf<Int>()
        val offsets = mutableSetOf<Int>()
        for (raw in bytes.toString(Charsets.UTF_8).removePrefix("﻿").lineSequence()) {
            val line = raw.trim()
            if (line.startsWith('[')) inSection = line.equals("[Bangumi]", true)
            if (!inSection || '=' !in line) continue
            val value = line.substringAfter('=').substringBefore(';').substringBefore('#').trim().toIntOrNull() ?: continue
            when (line.substringBefore('=').trim().lowercase(Locale.ROOT)) {
                "id" -> if (value > 0) ids += value
                "offset" -> if (value in -9999..9999) offsets += value
            }
        }
        return ids.singleOrNull()?.let { LocalMediaSidecarEvidence(bangumiId = it, offset = offsets.singleOrNull(), conflict = offsets.size > 1) }
            ?: if (ids.size > 1) LocalMediaSidecarEvidence(conflict = true) else null
    }

    fun parseNfo(bytes: ByteArray, name: String): LocalMediaSidecarEvidence? {
        if (bytes.size > MAX_BYTES) return null
        val handler = NfoHandler()
        return try {
            val factory = SAXParserFactory.newInstance()
            // Android 与 JVM 的 SAX 实现不完全一致；实体解析器及 lexicalHandler 都必须成功安装。
            val reader = factory.newSAXParser().xmlReader
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.entityResolver = handler
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.parse(InputSource(ByteArrayInputStream(bytes)))
            handler.evidence(name)
        } catch (_: Exception) {
            null
        }
    }

    private class NfoHandler : DefaultHandler(), org.xml.sax.ext.LexicalHandler {
        private val values = mutableMapOf<String, MutableList<String>>()
        private var depth = 0
        private var root = ""
        private var key: String? = null
        private val text = StringBuilder()

        override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("NFO DTD is not allowed") }
        override fun endDTD() = Unit
        override fun startEntity(name: String?) = Unit
        override fun endEntity(name: String?) = Unit
        override fun startCDATA() = Unit
        override fun endCDATA() = Unit
        override fun comment(ch: CharArray?, start: Int, length: Int) = Unit
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("NFO entity is not allowed")

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            when (++depth) {
                1 -> root = qName.lowercase(Locale.ROOT)
                2 -> {
                    val name = qName.lowercase(Locale.ROOT)
                    key = name.takeIf { it in fields }?.let {
                        if (it == "uniqueid") when (attributes.getValue("type")?.lowercase(Locale.ROOT)) {
                            "bangumi", "bgm", "bgm.tv" -> "bangumiid"
                            "tmdb", "themoviedb" -> "tmdbid"
                            else -> null
                        } else if (it == "bgmid") "bangumiid" else it
                    }
                    text.setLength(0)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (depth == 2 && key != null && text.length < 512) text.append(ch, start, length.coerceAtMost(512 - text.length))
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (depth-- == 2) key?.let { field ->
                text.toString().trim().takeIf(String::isNotBlank)?.let { values.getOrPut(field, ::mutableListOf) += it }
                key = null
            }
        }

        fun evidence(name: String): LocalMediaSidecarEvidence? {
            val validRoot = when (name.lowercase(Locale.ROOT)) {
                "tvshow.nfo" -> root == "tvshow"
                "season.nfo" -> root == "season"
                "movie.nfo" -> root == "movie"
                else -> root == "episodedetails" || root == "movie"
            }
            if (!validRoot) return null
            fun numbers(key: String) = values[key].orEmpty().mapNotNull(String::toIntOrNull).distinct()
            val bangumi = if (root in setOf("tvshow", "movie")) numbers("bangumiid").filter { it > 0 } else emptyList()
            val tmdb = if (root in setOf("tvshow", "movie")) numbers("tmdbid").filter { it > 0 } else emptyList()
            val seasons = (numbers("season") + numbers("seasonnumber")).filter { it in 0..99 }.distinct()
            val episodes = values["episode"].orEmpty().filter(number::matches).distinct()
            return LocalMediaSidecarEvidence(
                bangumi.singleOrNull(), tmdb.singleOrNull(), values["title"]?.firstOrNull(),
                values["originaltitle"]?.firstOrNull(), numbers("year").singleOrNull()?.takeIf { it in 1900..2099 },
                seasons.singleOrNull(), episodes.singleOrNull(), movie = root == "movie",
                conflict = bangumi.size > 1 || tmdb.size > 1 || seasons.size > 1 || episodes.size > 1
            )
        }
    }
}
