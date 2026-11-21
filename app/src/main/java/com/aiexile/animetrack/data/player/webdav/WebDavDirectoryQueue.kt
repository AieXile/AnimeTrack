package com.aiexile.animetrack.data.player.webdav

import com.aiexile.animetrack.data.player.VideoFormats
import com.aiexile.animetrack.domain.playback.EpisodeFileNameParser
import com.thegrizzlylabs.sardineandroid.DavResource
import okhttp3.HttpUrl
import java.util.Locale

/** 同一个目录中的视频清单，只处理文件名和地址，不读取媒体内容。 */
internal object WebDavDirectoryQueue {
    data class Episode(val target: WebDavPlaybackIdentity.Target, val title: String, val number: Int?)

    fun build(
        server: HttpUrl,
        root: HttpUrl,
        username: String,
        selected: WebDavPlaybackIdentity.Target,
        selectedTitle: String?,
        resources: List<DavResource>
    ): List<Episode> {
        val directory = checkNotNull(selected.requestUrl.resolve("./"))
        fun episode(target: WebDavPlaybackIdentity.Target, title: String): Episode {
            val parsed = EpisodeFileNameParser.parse(target.title, directory.pathSegments.dropLast(1).joinToString("/"))
            val number = parsed.episodeNumber?.takeIf { parsed.isMainFeature && parsed.decimalEpisode == null &&
                (parsed.episodeEndNumber == null || parsed.episodeEndNumber == it) }
            return Episode(target, title, number)
        }
        val first = episode(selected, selectedTitle?.takeIf(String::isNotBlank) ?: selected.title)
        return (sequenceOf(first) + resources.asSequence().filterNot { it.isDirectory }.mapNotNull { resource ->
            val url = WebDavPlaybackIdentity.resolve(directory, resource.href.toString()) ?: return@mapNotNull null
            if (!WebDavPlaybackIdentity.sameDirectory(url, directory)) return@mapNotNull null
            val target = WebDavPlaybackIdentity.target(server, root, username, url.toString()) ?: return@mapNotNull null
            val title = resource.displayName?.takeIf(String::isNotBlank) ?: target.title
            if (!VideoFormats.isVideoFile(target.title) && !VideoFormats.isVideoFile(title)) return@mapNotNull null
            episode(target, title)
        }).distinctBy { it.target.mediaKey }
            .sortedWith(compareBy<Episode> { it.number ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }.thenBy { it.target.mediaKey })
            .toList()
    }
}
