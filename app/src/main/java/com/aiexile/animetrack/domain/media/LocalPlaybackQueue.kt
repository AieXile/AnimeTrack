package com.aiexile.animetrack.domain.media

import com.aiexile.animetrack.data.media.LocalEpisodeType
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaMatchState

/** 本地文件身份与选集规则；不访问播放器、数据库或 Android URI。 */
object LocalPlaybackQueue {
    fun mediaKey(id: Long): String = "local:$id"

    fun build(selected: LocalMediaEntity, files: List<LocalMediaEntity>): List<LocalMediaEntity> {
        require(!selected.isMissing) { "文件已缺失，请重新扫描或授权" }
        if (!isEpisode(selected) || selected.matchedAnimeId == null) return listOf(selected)
        return files.asSequence()
            .filter { !it.isMissing && isEpisode(it) }
            .filter { it.sourceId == selected.sourceId && it.matchedAnimeId == selected.matchedAnimeId }
            .filter { sameSeason(it.seasonNumber, selected.seasonNumber) }
            .groupBy { it.episodeNumber!! }
            .map { (_, versions) ->
                versions.firstOrNull { it.id == selected.id }
                    ?: versions.sortedWith(compareByDescending<LocalMediaEntity> { it.isUserOverride }.thenBy { it.id }).first()
            }
            .sortedBy { it.episodeNumber }
    }

    /** S1 与未标记季数视为同一季；S2 及以上必须完全相同，避免跨季混排。 */
    private fun sameSeason(fileSeason: Int?, selectedSeason: Int?): Boolean {
        val file = fileSeason?.takeIf { it > 1 }
        val selected = selectedSeason?.takeIf { it > 1 }
        return file == selected
    }

    fun isEpisode(file: LocalMediaEntity): Boolean =
        file.episodeType != LocalEpisodeType.EXTRA &&
            file.matchState != LocalMediaMatchState.IGNORED &&
            file.episodeNumber != null && file.episodeNumber > 0 && file.seasonNumber != 99 &&
            (file.episodeEndNumber == null || file.episodeEndNumber == file.episodeNumber)
}
