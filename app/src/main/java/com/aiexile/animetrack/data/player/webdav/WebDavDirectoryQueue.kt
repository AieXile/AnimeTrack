package com.aiexile.animetrack.data.player.webdav

import com.aiexile.animetrack.data.player.VideoFormats
import com.aiexile.animetrack.domain.playback.EpisodeFileNameParser
import com.aiexile.animetrack.util.normalizeTitleForMatch
import com.thegrizzlylabs.sardineandroid.DavResource
import okhttp3.HttpUrl

/** 同一个目录中的视频清单，只处理文件名和地址，不读取媒体内容。 */
internal object WebDavDirectoryQueue {
    data class Episode(val target: WebDavPlaybackIdentity.Target, val title: String, val number: Int?, val series: Series?)

    data class Series(val title: String, val season: Int?, val year: Int?)

    /** 手选清单不限制作品；逐集导航只走无歧义的同作品、同季正片。 */
    fun adjacentIndex(episodes: List<Episode>, index: Int, forward: Boolean): Int? {
        val current = episodes.getOrNull(index) ?: return null
        val series = current.series ?: return null
        val number = current.number ?: return null
        val adjacentNumber = number + if (forward) 1 else -1
        return episodes.indices.singleOrNull { candidate ->
            episodes[candidate].series == series && episodes[candidate].number == adjacentNumber
        }
    }

    fun build(
        server: HttpUrl,
        root: HttpUrl,
        username: String,
        selected: WebDavPlaybackIdentity.Target,
        selectedTitle: String?,
        resources: List<DavResource>
    ): List<Episode> {
        val directory = checkNotNull(selected.requestUrl.resolve("./"))
        val parentPath = directory.pathSegments.dropLast(1).joinToString("/")
        fun episode(target: WebDavPlaybackIdentity.Target, title: String): Episode {
            val fromPath = EpisodeFileNameParser.parse(target.title, parentPath)
            val parsed = if (fromPath.episodeNumber == null && fromPath.decimalEpisode == null &&
                fromPath.isMainFeature && !fromPath.isMovie && !fromPath.isPersonalRecording &&
                title != target.title && VideoFormats.isVideoFile(title)
            ) EpisodeFileNameParser.parse(title, parentPath) else fromPath
            val number = parsed.episodeNumber?.takeIf { it > 0 && parsed.isMainFeature && !parsed.isMovie &&
                parsed.seasonNumber !in setOf(0, 99) && parsed.decimalEpisode == null &&
                (parsed.episodeEndNumber == null || parsed.episodeEndNumber == it) }
            val seriesTitle = normalizeTitleForMatch(parsed.parsedTitle)
            val series = if (number == null || parsed.confidence < 0.9f || seriesTitle.none(Char::isLetter)) null
            else Series(seriesTitle, parsed.seasonNumber, parsed.year)
            return Episode(target, title, number, series)
        }
        val first = episode(selected, selectedTitle?.takeIf(String::isNotBlank) ?: selected.title)
        return (sequenceOf(first) + resources.asSequence().filterNot { it.isDirectory || it.statusCode >= 400 }.mapNotNull { resource ->
            val url = PlayerWebDavDirectory.entryUrl(directory, resource) ?: return@mapNotNull null
            if (!WebDavPlaybackIdentity.sameDirectory(url, directory)) return@mapNotNull null
            val target = WebDavPlaybackIdentity.target(server, root, username, url.toString()) ?: return@mapNotNull null
            val title = resource.displayName?.takeIf(String::isNotBlank) ?: target.title
            if (!VideoFormats.isVideoFile(target.title) && !VideoFormats.isVideoFile(title)) return@mapNotNull null
            if (target.mediaKey == selected.mediaKey) return@mapNotNull null
            episode(target, title)
        }).distinctBy { it.target.mediaKey }
            .sortedWith(compareBy<Episode> { it.number ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                .thenBy { it.target.mediaKey })
            .toList()
    }
}
