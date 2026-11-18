package com.aiexile.animetrack.domain.playback

import java.util.Locale

/**
 * 外挂字幕的公共识别规则：支持的格式、MIME 映射、与视频同名的归属判定及语言推测。
 * 本地媒体库（SAF）与 WebDAV 播放共用，避免两端各自维护扩展名与前缀匹配规则。
 */
object SubtitleSpec {

    const val MATCH_EXACT = 0
    const val MATCH_PREFIX = 1
    const val MATCH_NONE = 2

    private val mimeTypesByExtension = mapOf(
        "srt" to "application/x-subrip",
        "vtt" to "text/vtt",
        "ass" to "text/x-ssa",
        "ssa" to "text/x-ssa",
        "ttml" to "application/ttml+xml"
    )

    private val prefixSeparators = setOf('.', '-', '_', ' ', '[', '(')

    private val languageHints = linkedMapOf(
        "zh-CN" to listOf("chs", "zh-cn", "zh_cn", "sc", "hans", "gb2312", "简体", "简中", "简"),
        "zh-TW" to listOf("cht", "zh-tw", "zh_tw", "tc", "big5", "hant", "繁体", "繁中", "繁"),
        "ja" to listOf("jpn", "japanese", "jp", "ja", "日"),
        "en" to listOf("eng", "english")
    )

    fun mimeTypeForExtension(extension: String): String? =
        mimeTypesByExtension[extension.lowercase(Locale.ROOT)]

    fun mimeTypeForFileName(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "")
        if (ext == fileName) return null
        return mimeTypeForExtension(ext)
    }

    fun isSupported(fileName: String): Boolean = mimeTypeForFileName(fileName) != null

    /**
     * 判定字幕文件是否属于指定视频：
     * - [MATCH_EXACT]：与视频同名（不含扩展名）
     * - [MATCH_PREFIX]：以视频名为前缀且紧跟分隔符（避免 EP01 误命中 EP012）
     * - [MATCH_NONE]：不属于该视频
     */
    fun matchScore(subtitleFileName: String, videoBaseName: String): Int {
        val trimmedVideo = videoBaseName.trim()
        if (trimmedVideo.isBlank()) return MATCH_NONE
        val candidateBase = subtitleFileName.substringBeforeLast('.').trim()
        if (candidateBase.equals(trimmedVideo, ignoreCase = true)) return MATCH_EXACT
        if (candidateBase.length > trimmedVideo.length &&
            candidateBase.startsWith(trimmedVideo, ignoreCase = true) &&
            candidateBase[trimmedVideo.length] in prefixSeparators
        ) {
            return MATCH_PREFIX
        }
        return MATCH_NONE
    }

    fun belongsTo(subtitleFileName: String, videoBaseName: String): Boolean =
        matchScore(subtitleFileName, videoBaseName) != MATCH_NONE

    fun guessLanguage(nameOrSuffix: String): String? {
        val lower = nameOrSuffix.lowercase(Locale.ROOT)
        for ((lang, keys) in languageHints) {
            if (keys.any { lower.contains(it) }) return lang
        }
        return null
    }
}