package com.aiexile.animetrack.domain.media

import com.aiexile.animetrack.ui.home.SeriesMatcher

/** 文件名解析后的标题与集数信息。 */
data class EpisodeParseResult(
    val parsedTitle: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeEndNumber: Int?,
    val isMainFeature: Boolean,
    val confidence: Float
)

/**
 * 常见番剧文件名解析器。
 * 只处理命名文本，不读取视频内容，便于单元测试和大目录扫描。
 */
object EpisodeFileNameParser {
    private val extensionPattern = Regex("\\.[^.\\/]+$")
    private val leadingGroupPattern = Regex("^\\s*\\[[^]]+]\\s*")
    private val bracketNoisePattern = Regex(
        "(?i)\\[(?:\\d{3,4}p|2160p|4k|x264|x265|h264|h265|hevc|aac|flac|" +
            "简|繁|chs|cht|中日|日英|web|bluray|bdrip|webrip)[^]]*]"
    )
    private val seasonEpisodePattern = Regex("(?i)(?:s(\\d{1,2})\\s*[._ -]*e(\\d{1,4}))")
    private val seasonEpisodeWordPattern = Regex(
        "(?i)season\\s*(\\d{1,2})\\s*(?:episode|ep|e)\\s*(\\d{1,4})"
    )
    private val chineseEpisodePattern = Regex("第\\s*(\\d{1,4})(?:\\s*[-~至]\\s*(\\d{1,4}))?\\s*集")
    private val englishEpisodePattern = Regex("(?i)(?:^|[\\s._-])e?p(?:isode)?[ ._-]*(\\d{1,4})(?:[ ._-]*(?:-|~)[ ._-]*(\\d{1,4}))?")
    private val trailingRangePattern = Regex("(?:^|[\\s._-]+)(\\d{1,3})\\s*[-~至&+]\\s*(\\d{1,3})(?:v\\d+)?$")
    private val trailingEpisodePattern = Regex("(?:^|[\\s._-]+)(\\d{1,3})(?:v\\d+)?$")
    private val simpleTrailingEpisodePattern = Regex("[-\\s]+(\\d{1,3})(?:v\\d+)?$")
    private val technicalNoisePattern = Regex(
        "(?i)[ ._-]*(?:2160p|1080p|720p|480p|4k|8k|x264|x265|h264|h265|hevc|av1|aac|flac|" +
            "bluray|bdrip|webrip|web-dl|hdr|10bit|10-bit|hi10p|proper|repack|v\\d+)+[ ._-]*"
    )
    private val nonFeaturePattern = Regex("(?i)(?:^|[ ._\\-])(?:ncop|nced|pv|promo|preview|trailer|特典|预告|片头|片尾)(?:$|[ ._\\-])")

    fun parse(fileName: String, parentPath: String = ""): EpisodeParseResult {
        val withoutExtension = extensionPattern.replace(fileName, "")
        val isMain = !nonFeaturePattern.containsMatchIn(withoutExtension)

        var working = leadingGroupPattern.replace(withoutExtension, "").trim()
        working = bracketNoisePattern.replace(working, " ").trim()

        var season: Int? = null
        var episode: Int? = null
        var episodeEnd: Int? = null
        var confidence = 0.45f

        val explicit = seasonEpisodePattern.find(working)
            ?: seasonEpisodeWordPattern.find(working)
        if (explicit != null) {
            val values = explicit.groupValues.filter { it.isNotBlank() }
            if (values.size >= 3) {
                season = values[1].toIntOrNull()
                episode = values[2].toIntOrNull()
                working = working.removeRange(explicit.range.first, working.length)
                confidence = 0.98f
            }
        }

        if (episode == null) {
            val chinese = chineseEpisodePattern.find(working)
            if (chinese != null) {
                episode = chinese.groupValues[1].toIntOrNull()
                episodeEnd = chinese.groupValues.getOrNull(2)?.toIntOrNull()
                working = working.removeRange(chinese.range.first, working.length)
                confidence = 0.94f
            }
        }

        if (episode == null) {
            val english = englishEpisodePattern.find(working)
            if (english != null) {
                episode = english.groupValues[1].toIntOrNull()
                episodeEnd = english.groupValues.getOrNull(2)?.toIntOrNull()
                working = working.removeRange(english.range.first, english.range.last + 1)
                confidence = 0.92f
            }
        }

        if (episode == null) {
            val range = trailingRangePattern.find(working)
            if (range != null) {
                episode = range.groupValues[1].toIntOrNull()
                episodeEnd = range.groupValues[2].toIntOrNull()
                working = working.removeRange(range.range.first, working.length)
                confidence = 0.88f
            }
        }

        if (episode == null) {
            val trailing = simpleTrailingEpisodePattern.find(working)
                ?: trailingEpisodePattern.find(working)
            if (trailing != null) {
                episode = trailing.groupValues[1].toIntOrNull()
                working = working.removeRange(trailing.range.first, working.length)
                confidence = 0.82f
            }
        }

        working = technicalNoisePattern.replace(working, " ")
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '–', '—', '_', '.')

        if (working.isBlank()) {
            working = parentPath.substringBefore('/').trim()
        }
        if (working.isBlank()) {
            working = withoutExtension.ifBlank { fileName }
        }

        if (season == null) {
            season = SeriesMatcher.extractSeasonNumber(working).takeIf { it > 1 || SeriesMatcher.isSpecialEntry(working) }
        }
        val parsedTitle = SeriesMatcher.extractBaseTitle(working).trim().ifBlank { working }

        return EpisodeParseResult(
            parsedTitle = parsedTitle,
            seasonNumber = season,
            episodeNumber = episode,
            episodeEndNumber = episodeEnd,
            isMainFeature = isMain,
            confidence = if (isMain) confidence else 0f
        )
    }
}
