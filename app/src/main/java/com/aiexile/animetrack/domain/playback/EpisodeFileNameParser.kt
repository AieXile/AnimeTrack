package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.ui.home.SeriesMatcher
import com.aiexile.animetrack.util.normalizeTitleForMatch
import com.github.TraceLTRC.AnitomyK
import com.github.TraceLTRC.ElementCategory
import java.text.Normalizer

/** 完整标题与基础标题分别保留，季/篇章信息不得因归一化丢失。 */
data class EpisodeParseResult(
    val parsedTitle: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeEndNumber: Int?,
    val isMainFeature: Boolean,
    val confidence: Float,
    val titleCandidates: List<String> = listOf(parsedTitle),
    val baseTitle: String = parsedTitle,
    val year: Int? = null,
    val decimalEpisode: String? = null,
    val explicitNumbering: Boolean = false,
    val isMovie: Boolean = false,
    val isPersonalRecording: Boolean = false
)

/** Anitomy 负责文件名分词；本层只补字幕组中文写法、父目录和播放器类型。 */
object EpisodeFileNameParser {
    internal const val VERSION = 2
    private val recordingName = Regex(
        """(?i)^(?:(?:屏幕录制|螢幕錄製|螢幕錄影|屏幕录像|手机拍摄|手機拍攝|相机拍摄|相機拍攝)(?=$|[\s._\-\[(0-9])|(?:screen[ ._-]*rec(?:ording|order)?|screencast|camera[ ._-]*recording)(?=$|[\s._\-\[(0-9])|(?:vid|img|mvimg|dsc|pxl)[ _-]?\d{4,}(?=$|[\s._\-\[(]))"""
    )
    private val chineseEpisode = Regex("第\\s*(\\d{1,4}(?:\\.\\d+)?)(?:\\s*[-~至]\\s*(\\d{1,4}))?\\s*[话話集]")
    private val explicitEpisode = Regex("(?i)(?<![a-z0-9])(?:s(\\d{1,2})[ ._-]*e|season[ ._-]*(\\d{1,2})[ ._-]*(?:episode|ep|e)[ ._-]*)(\\d{1,4}(?:\\.\\d+)?)(?:[ ._-]*(?:-e?|e)(\\d{1,4}))?(?:v\\d+)?(?![a-z0-9.])")
    private val seasonFolder = Regex("(?i)^(?:season[ ._-]*|s)(\\d{1,2})$|^第([一二三四五六七八九十\\d]+)[季期]$")
    private val numberedSeason = Regex("(?i)第[一二三四五六七八九十\\d]+[季期]|\\b(?:season[ ._-]*|s)\\d{1,2}\\b|\\b\\d+(?:st|nd|rd|th) season\\b|[ⅡⅢⅣⅤⅥⅦⅧⅨⅩ]$|\\s(?:II|III|IV|VI|VII|VIII|IX|XI|XII)$|\\s\\d+[期季]$")
    private val numberOnly = Regex("(?i)^(?:ep?[ ._-]*)?(\\d{1,4}(?:\\.\\d+)?)(?:[ -]+(\\d{1,4}))?(?:v\\d+)?$")
    private val episodeToken = Regex("(?i)^(?:ep?)?(\\d{1,4}(?:\\.\\d+)?)(?:[ -]+(\\d{1,4}))?(?:v\\d+)?(?:end|完)?$")
    private val brackets = Regex("\\[([^]\\[]+)]")
    private val bracketedTail = Regex("^\\s*(?:\\[[^]]+]\\s*)+$")
    private val bracketNoise = Regex("(?i)^(?:[0-9a-f]{8}|\\d{3,4}p|[hx]26[45]|chs|cht|简繁|简体|繁體|简日|繁日|1080|2160|4k|bdrip|web[- .]?dl|hevc|av1|aac|flac|mkv)[ ._-]*.*$")
    private val extra = Regex("(?i)(?:^|[^\\p{L}\\p{N}])(?:ncop|nced|pv|promo|preview|trailer)(?:\\d+)?(?=$|[^\\p{L}\\p{N}])|特典|预告|預告|片头|片尾")
    private val movie = Regex("剧场版|劇場版|(?i)(?:^|[ ._-])movie(?:$|[ ._-])")
    private val genericFolder = Regex("(?i)^(?:anime|animation|downloads?|videos?|media|tv|bd|bdrip|web[- .]?dl|\\d{3,4}p|动漫|动画|番剧|番劇|下载|下載|视频|視頻|正片|特典|sp|extras?|specials?)$")
    private val yearSuffix = Regex("\\s*[\\[(]((?:19|20)\\d{2})[)\\]]\\s*$")

    fun parse(fileName: String, parentPath: String = ""): EpisodeParseResult {
        val normalized = Normalizer.normalize(fileName, Normalizer.Form.NFKC)
        val stem = normalized.substringBeforeLast('.', normalized)
        if (recordingName.containsMatchIn(stem.trimStart())) {
            // 个人视频的名称可用于列表显示，但不是番名证据，也不要把相机日期解析成集号。
            return EpisodeParseResult(stem, null, null, null, true, 0f, emptyList(), isPersonalRecording = true)
        }
        val parsed = AnitomyK().apply { parse(normalized) }.elements
        fun field(category: ElementCategory) = parsed.firstOrNull { it.first == category }?.second
        val explicit = explicitEpisode.find(stem)
        val chinese = chineseEpisode.find(stem)
        val bare = numberOnly.matchEntire(stem.trim())
        val bracketParts = brackets.findAll(stem).map { it.groupValues[1].trim() }.toList()
        val fullyBracketed = bracketedTail.matches(stem)
        val bracketEpisodeIndex = if (fullyBracketed) bracketParts.indexOfFirst { episodeToken.matches(it) } else -1
        val bracketTitle = if (bracketEpisodeIndex > 0) {
            bracketParts.subList(0, bracketEpisodeIndex).let { parts ->
                (if (parts.size > 1) parts.drop(1) else parts)
                    .filterNot(bracketNoise::matches).takeIf { it.isNotEmpty() }?.joinToString(" / ")
            }
        } else null
        val episodeFields = parsed.filter { it.first == ElementCategory.kElementEpisodeNumber }.map { it.second }
        val token = episodeFields.firstOrNull()?.let(episodeToken::matchEntire)
        val rawEpisode = explicit?.groupValues?.get(3) ?: chinese?.groupValues?.get(1) ?: bare?.groupValues?.get(1)
            ?: token?.groupValues?.get(1)
        val episode = rawEpisode?.toIntOrNull()
        val decimal = rawEpisode?.takeIf { '.' in it }
        val end = (explicit?.groupValues?.get(4) ?: chinese?.groupValues?.get(2) ?: bare?.groupValues?.get(2))
            ?.toIntOrNull() ?: if (token != null) {
                token.groupValues[2].toIntOrNull() ?: episodeFields.getOrNull(1)?.toIntOrNull()
            } else null

        val folders = parentPath.split('/').asReversed().map { Normalizer.normalize(it.trim(), Normalizer.Form.NFKC) }
        val folderSeason = folders.firstNotNullOfOrNull { part ->
            seasonFolder.matchEntire(part)?.let { it.groupValues[1].toIntOrNull() ?: titleSeason(part) }
        }
        val folderTitle = folders.firstOrNull {
            it.isNotBlank() && it.any(Char::isLetter) && !genericFolder.matches(it) && !seasonFolder.matches(it)
        }
        val fileTitle = when {
            bare != null || explicit?.range?.first == 0 -> null
            bracketTitle != null -> bracketTitle
            explicit != null && explicit.range.first > 0 && !stem.startsWith('[') -> stem.substring(0, explicit.range.first)
            chinese != null -> stem.substring(0, chinese.range.first).replace(Regex("^\\[[^]]+]\\s*"), "")
            else -> field(ElementCategory.kElementAnimeTitle)
        }?.trim(' ', '-', '–', '—', '_', '.')?.takeIf { it.any(Char::isLetter) }
        val title = (fileTitle ?: folderTitle ?: stem).trim()
        val season = explicit?.let { it.groupValues[1].toIntOrNull() ?: it.groupValues[2].toIntOrNull() }
            ?: field(ElementCategory.kElementAnimeSeason)?.toIntOrNull()
            ?: titleSeason(title) ?: folderSeason
        val fullTitle = yearSuffix.replace(title, "").trim().ifBlank { title }
        val equivalentFolder = folderTitle?.let { yearSuffix.replace(it, "").trim() }?.takeIf { parent ->
            normalizeTitleForMatch(SeriesMatcher.extractBaseTitle(parent)) == normalizeTitleForMatch(SeriesMatcher.extractBaseTitle(fullTitle))
        }
        val year = field(ElementCategory.kElementAnimeYear)?.toIntOrNull()
            ?: yearSuffix.find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: folderTitle?.takeIf { equivalentFolder != null }?.let { yearSuffix.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val candidates = (listOfNotNull(fullTitle, equivalentFolder) + fullTitle.split(Regex("\\s+[/|]\\s+")))
            .filter { it.any(Char::isLetter) }.distinctBy(::normalizeTitleForMatch)
        val isMain = !extra.containsMatchIn(stem) && folders.none { extra.containsMatchIn(it) }
        val isMovie = movie.containsMatchIn(fullTitle) || field(ElementCategory.kElementAnimeType).equals("Movie", ignoreCase = true)
        val confidence = when {
            !isMain -> 0f
            explicit != null || chinese != null -> 0.98f
            episode != null && (fileTitle != null || folderTitle != null) -> 0.9f
            fileTitle != null || folderTitle != null -> 0.72f
            else -> 0.4f
        }
        return EpisodeParseResult(
            fullTitle, season, episode, end?.takeIf { episode != null && it >= episode }, isMain, confidence,
            candidates, SeriesMatcher.extractBaseTitle(fullTitle), year, decimal, explicit != null, isMovie
        )
    }

    /** 首页的 99 是展示排序标记，不是媒体季号。 */
    internal fun titleSeason(title: String): Int? =
        if (numberedSeason.containsMatchIn(title)) SeriesMatcher.extractSeasonNumber(title).takeIf { it in 1..98 }
        else null
}
