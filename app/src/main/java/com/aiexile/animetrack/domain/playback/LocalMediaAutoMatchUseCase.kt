package com.aiexile.animetrack.domain.playback

import android.os.SystemClock
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.BangumiSeriesResolver
import com.aiexile.animetrack.data.media.LocalEpisodeType
import com.aiexile.animetrack.data.media.LocalMediaArchiveEntity
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.media.LocalMediaScrapeState
import com.aiexile.animetrack.data.network.BangumiEpisode
import com.aiexile.animetrack.data.network.BangumiInfoboxItem
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.BangumiSubjectRelation
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.TmdbMovie
import com.aiexile.animetrack.data.network.TmdbTvDetail
import com.aiexile.animetrack.data.network.TmdbTvShow
import com.aiexile.animetrack.ui.home.SeriesMatcher
import com.aiexile.animetrack.util.normalizeTitleForMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException

/** 扫描后立即归档明确 ID/缓存结果；联网 Worker 只消费尚未完成的识别组。 */
internal class LocalMediaAutoMatchUseCase(
    private val media: LocalMediaRepository,
    private val animes: AnimeRepository,
    private val episodes: suspend (Int) -> List<BangumiEpisode> = { id ->
        val items = mutableListOf<BangumiEpisode>()
        var offset = 0
        do {
            val page = RetrofitClient.bangumiApi.getEpisodes(id, offset = offset)
            items += page.data
            offset += page.data.size
        } while (page.data.isNotEmpty() && offset < minOf(page.total, 1200))
        items
    },
    private val relations: suspend (Int) -> List<BangumiSubjectRelation> = { RetrofitClient.bangumiApi.getSubjectRelations(it) },
    private val searchMovies: suspend (String) -> List<TmdbMovie> = { RetrofitClient.tmdbApi.searchMovie(it).results },
    private val getMovie: suspend (Int) -> TmdbMovie = { RetrofitClient.tmdbApi.getMovie(it) }
) {
    /** 返回是否仍有等待联网或等待批次预算的项目。 */
    suspend fun run(sourceId: Long, online: Boolean = true): Boolean = withContext(Dispatchers.Default) {
        val rows = media.getScrapeQueue(sourceId)
        if (rows.isEmpty()) return@withContext false
        val library = animes.getAllAnimes().first()
        val session = Requests()
        val byBangumi = library.filter { it.bangumiId != null }.associateBy { it.bangumiId }
        val byTmdb = library.filter { it.tmdbId != null }.associateBy { it.tmdbId }
        for ((key, group) in rows.groupBy { checkNotNull(it.groupKey) }) {
            currentCoroutineContext().ensureActive()
            val row = group.firstOrNull { it.episodeOffset != null && it.episodeOffset != 0 } ?: group.first()
            val titles = group.flatMap { it.titleEvidence?.split('\u001f').orEmpty() }
                .filter(::usableTitle).distinctBy(::normalizeTitleForMatch)
            val evidence = MediaTitleEvidence(titles, row.baseTitle, row.seasonNumber, row.releaseYear, row.movie)
            val manual = media.manualArchivesForEvidence(sourceId, key)
            if (manual.isNotEmpty()) {
                val chosen = manual.singleOrNull()
                media.commitScrape(group, chosen, if (chosen != null) LocalMediaScrapeState.IDENTIFIED else LocalMediaScrapeState.REVIEW)
                continue
            }
            val attached = group.mapNotNull { it.archiveKey }.distinct().singleOrNull()
            val cached = if (attached != null) media.getArchive(sourceId, attached)
            else media.archivesForEvidence(sourceId, key).singleOrNull()
            val known = explicitArchive(row, cached)
            try {
                val result = when {
                    known != null && !online -> {
                        if (group.all { it.archiveKey == known.key && it.scrapeState == LocalMediaScrapeState.ENRICH }) continue
                        Outcome(known, needsEnrichment = known.coverUrl == null || group.any { it.episodeOffset != null && it.episodeOffset != 0 })
                    }
                    known != null && group.any { it.scrapeState == LocalMediaScrapeState.ENRICH } ->
                        enrich(known, row, session)
                    known != null && (row.bangumiHint != null || row.tmdbHint != null) &&
                        (known.coverUrl == null || group.any { it.episodeOffset != null && it.episodeOffset != 0 }) ->
                        enrich(known, row, session)
                    known != null -> Outcome(known)
                    !online -> {
                        // 已明确追番身份的条目也需满足年份与类型，避免离线先行误认同名重制版。
                        val local = if (evidence.year != null) localCandidate(evidence, library) else null
                        if (local == null) continue else Outcome(local.toArchive(sourceId))
                    }
                    evidence.normalizedTitles.isEmpty() -> Outcome(null)
                    else -> matchOnline(row, evidence, session)
                }
                val archive = result.archive
                var episodeList = result.bangumiEpisodes
                // 季内编号无法解释的连续集数，看站点自己的 sort → ep 对照，而不是猜减去上一季长度。
                val bangumiId = archive?.bangumiId
                val total = archive?.episodeCount
                if (online && episodeList.isEmpty() && bangumiId != null && total != null && total > 0 &&
                    group.any { (it.episodeEndNumber ?: it.episodeNumber ?: 0) > total }) {
                    episodeList = session.fetch("episodes:$bangumiId") { episodes(bangumiId) }.orEmpty()
                }
                val tracking = (archive?.bangumiId?.let(byBangumi::get) ?: archive?.tmdbId?.let(byTmdb::get))
                    ?.takeIf { anime -> archive?.bangumiId != null && anime.bangumiId == archive.bangumiId ||
                        anime.seasonNumber?.let { season -> row.seasonNumber == null || row.seasonNumber == season } != false }
                val mapped = if (episodeList.isEmpty()) emptyMap() else group.mapNotNull { file ->
                    (mapAniRssEpisode(file, episodeList) ?: mapBangumiAbsoluteEpisode(file, episodeList))?.let { file.id to it }
                }.toMap()
                media.commitScrape(group, archive, when {
                    archive == null -> LocalMediaScrapeState.REVIEW
                    result.needsEnrichment -> LocalMediaScrapeState.ENRICH
                    else -> LocalMediaScrapeState.IDENTIFIED
                }, tracking?.id, mapped)
            } catch (_: RetryLater) {
                // 单次网络失败不应改为“匹配失败”，也不停止使用已识别的缓存/本地 ID。
                if (known != null && row.archiveKey == null) {
                    media.commitScrape(group, known, LocalMediaScrapeState.ENRICH)
                }
                if (session.exhausted) break
            }
        }
        media.getScrapeQueue(sourceId).isNotEmpty()
    }

    private suspend fun explicitArchive(row: LocalMediaEntity, cached: LocalMediaArchiveEntity?): LocalMediaArchiveEntity? {
        val bgm = row.bangumiHint
        val tmdb = row.tmdbHint
        if (bgm == null && tmdb == null) return cached
        val key = if (bgm != null) "bgm:$bgm" else tmdbKey(checkNotNull(tmdb), row.movie)
        val existing = cached?.takeIf { it.key == key } ?: media.getArchive(row.sourceId, key)
        return existing?.copy(bangumiId = bgm ?: existing.bangumiId, tmdbId = tmdb ?: existing.tmdbId)
            ?: LocalMediaArchiveEntity(
            row.sourceId, key, row.parsedTitle ?: row.fileName, bangumiId = bgm, tmdbId = tmdb, year = row.releaseYear
        )
    }

    private suspend fun enrich(archive: LocalMediaArchiveEntity, row: LocalMediaEntity, session: Requests): Outcome {
        val bgm = archive.bangumiId
        var enriched = archive
        var episodes = emptyList<BangumiEpisode>()
        if (bgm != null) {
            val detail = session.fetch("bgm-detail:$bgm") { animes.getBangumiSubjectDetail(bgm) }
            if (detail != null && (detail.type == null || detail.type == 2)) {
                enriched = enriched.copy(
                    title = detail.nameCn?.takeIf(String::isNotBlank) ?: detail.name,
                    coverUrl = detail.images?.large ?: detail.images?.common ?: detail.images?.medium ?: enriched.coverUrl,
                    year = detail.date?.year() ?: enriched.year, summary = detail.summary,
                    episodeCount = detail.eps?.takeIf { it > 0 } ?: detail.totalEpisodes
                )
                if (row.episodeOffset != null && row.episodeOffset != 0) {
                    episodes = session.fetch("episodes:$bgm") { episodes(bgm) }.orEmpty()
                }
            }
        }
        val tmdb = archive.tmdbId
        if (tmdb != null && (enriched.coverUrl == null || bgm == null)) {
            val secondary = if (row.movie) session.fetch("tmdb-movie:$tmdb") { getMovie(tmdb) }?.toArchive(row.sourceId)
            else session.fetch("tmdb-tv:$tmdb") { animes.getTmdbTvDetail(tmdb) }?.toArchive(row.sourceId, row.seasonNumber)
            if (secondary != null) enriched = if (bgm == null) secondary else enriched.copy(
                coverUrl = enriched.coverUrl ?: secondary.coverUrl, summary = enriched.summary ?: secondary.summary
            )
        }
        return Outcome(enriched, bangumiEpisodes = episodes)
    }

    private suspend fun matchOnline(row: LocalMediaEntity, evidence: MediaTitleEvidence, session: Requests): Outcome {
        val queries = (evidence.titles + listOfNotNull(evidence.baseTitle))
            .distinctBy(::normalizeTitleForMatch).filter(::usableTitle)
        val bangumi = linkedMapOf<Int, BangumiSubject>()
        for (query in queries.take(MAX_TITLE_QUERIES)) {
            session.fetch("bgm-search:${normalizeTitleForMatch(query)}") { animes.searchBangumi(query) }.orEmpty()
                .filter { it.id > 0 }.forEach { bangumi.putIfAbsent(it.id, it) }
        }
        val relatedSeasons = mutableMapOf<Int, Int>()
        val season = evidence.season
        if (season != null && season in 2..5) {
            val seed = bangumi.values.filter { subject ->
                listOfNotNull(subject.name, subject.name_cn).any { normalizeTitleForMatch(it) == evidence.normalizedBase }
            }.singleOrNull()
            if (seed != null) {
                var current = seed.id
                val visited = mutableSetOf(current)
                var complete = true
                for (step in 2..season) {
                    val next = BangumiSeriesResolver.uniqueMainlineSequel(readRelations(current, session))?.subjectId
                    if (next == null || !visited.add(next) ||
                        !BangumiSeriesResolver.isUniqueMainlinePrequel(readRelations(next, session), current)) {
                        complete = false
                        break
                    }
                    current = next
                }
                if (complete) {
                    val detail = session.fetch("bgm-detail:$current") { animes.getBangumiSubjectDetail(current) }
                    if (detail != null && (detail.type == null || detail.type == 2)) {
                        bangumi[current] = BangumiSubject(detail.id, detail.name, detail.nameCn, detail.images, detail.eps,
                            detail.totalEpisodes, detail.rating, detail.summary, detail.date, detail.platform, detail.infobox)
                        relatedSeasons[current] = season
                    }
                }
            }
        }
        val candidates = bangumi.values.map { candidate ->
            val names = (listOfNotNull(candidate.name, candidate.name_cn) + aliases(candidate.infobox)).filter(String::isNotBlank)
            val inferred = relatedSeasons[candidate.id]
            val special = candidate.platform.equals("OVA", true) || names.any(SeriesMatcher::isSpecialEntry)
            MediaTitleCandidate(candidate, names, candidate.date?.year(),
                names.firstNotNullOfOrNull(EpisodeFileNameParser::titleSeason) ?: inferred?.takeUnless { special },
                candidate.platform.equals("剧场版") || candidate.platform.equals("Movie", true) || names.any(LocalMediaMatcher::movieTitle),
                names.map(SeriesMatcher::extractBaseTitle) + listOfNotNull(evidence.baseTitle.takeIf { inferred != null }), special)
        }
        val hits = LocalMediaMatcher.candidates(evidence, candidates)
        if (hits.size > 1) return Outcome(null)
        hits.singleOrNull()?.let { hit ->
            val archive = LocalMediaArchiveEntity(row.sourceId, "bgm:${hit.id}", hit.displayName, hit.coverUrl,
                bangumiId = hit.id, year = hit.date?.year(), summary = hit.summary, episodeCount = hit.episodeCount)
            return Outcome(archive, needsEnrichment = archive.coverUrl == null)
        }

        if (row.movie) {
            val movies = queries.take(MAX_TITLE_QUERIES).flatMap { query ->
                session.fetch("tmdb-movie-search:${normalizeTitleForMatch(query)}") { searchMovies(query) }.orEmpty()
            }.filter { 16 in it.genreIds.orEmpty() }.distinctBy { it.id }
            val exact = LocalMediaMatcher.candidates(evidence, movies.map { movie ->
                MediaTitleCandidate(movie, listOfNotNull(movie.title, movie.originalTitle), movie.date?.year(), null, true)
            }).singleOrNull()
            return Outcome(exact?.toArchive(row.sourceId))
        }
        val results = queries.take(MAX_TITLE_QUERIES).flatMap { query ->
            session.fetch("tmdb-search:${normalizeTitleForMatch(query)}") { animes.searchTmdbTvShows(query) }.orEmpty()
        }.distinctBy(TmdbTvShow::id)
        val tv = results.filter { show -> listOfNotNull(show.name, show.originalName).any {
            val normalized = normalizeTitleForMatch(it)
            normalized in evidence.normalizedTitles || (row.seasonNumber != null && normalized == evidence.normalizedBase)
        } }.take(MAX_TMDB_DETAILS + 1)
        if (tv.size > MAX_TMDB_DETAILS) return Outcome(null)
        val verified = tv.mapNotNull { show ->
            val detail = session.fetch("tmdb-tv:${show.id}") { animes.getTmdbTvDetail(show.id) } ?: return@mapNotNull null
            if (detail.genres.orEmpty().none { it.id == 16 }) return@mapNotNull null
            val season = row.seasonNumber?.let { expected -> detail.seasons.orEmpty().singleOrNull { it.seasonNumber == expected } }
            if (row.seasonNumber != null && season == null) return@mapNotNull null
            // 不把无季证据的分集绑定到 TMDB 多季剧的某一季。
            if (row.seasonNumber == null && detail.numberOfSeasons != 1 && row.episodeNumber != null) return@mapNotNull null
            if (row.releaseYear != null && row.releaseYear != (season?.date ?: show.firstAirDate)?.year()) return@mapNotNull null
            detail.toArchive(row.sourceId, row.seasonNumber)
        }
        return Outcome(verified.singleOrNull())
    }

    private suspend fun readRelations(id: Int, session: Requests): List<BangumiSeriesResolver.SubjectRelation> =
        session.fetch("bgm-rel:$id") { relations(id) }.orEmpty().map {
            BangumiSeriesResolver.SubjectRelation(it.id, it.displayName, it.relation.orEmpty(), it.isAnime)
        }

    private fun localCandidate(evidence: MediaTitleEvidence, library: List<com.aiexile.animetrack.model.Anime>) =
        LocalMediaMatcher.candidates(evidence, library.filter { it.bangumiId != null || it.tmdbId != null }.map { anime ->
            MediaTitleCandidate(anime, listOf(anime.title), anime.airDate?.year(), anime.seasonNumber ?: EpisodeFileNameParser.titleSeason(anime.title),
                LocalMediaMatcher.movieTitle(anime.title), listOf(SeriesMatcher.extractBaseTitle(anime.title)))
        }).singleOrNull()

    private data class Outcome(
        val archive: LocalMediaArchiveEntity?, val needsEnrichment: Boolean = false,
        val bangumiEpisodes: List<BangumiEpisode> = emptyList()
    )

    private class RetryLater : Exception()

    private class Requests {
        private val cache = mutableMapOf<String, Any?>()
        private val deferred = mutableSetOf<String>()
        private var calls = 0
        private val deadline = SystemClock.elapsedRealtime() + 45_000L
        val exhausted get() = calls >= 60 || SystemClock.elapsedRealtime() >= deadline

        @Suppress("UNCHECKED_CAST")
        suspend fun <T : Any> fetch(key: String, call: suspend () -> T): T? {
            if (key in cache) return cache[key] as T?
            if (key in deferred || exhausted) throw RetryLater()
            calls++
            return try {
                val value = withTimeoutOrNull((deadline - SystemClock.elapsedRealtime()).coerceIn(1L, 5_000L)) { call() }
                    ?: throw RetryLater()
                value.also { cache[key] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() in listOf(400, 401, 403, 404, 422)) {
                    cache[key] = null
                    null
                } else {
                    if (e.code() == 429) calls = 60
                    deferred += key
                    throw RetryLater()
                }
            } catch (_: IOException) {
                deferred += key
                throw RetryLater()
            } catch (_: RetryLater) {
                deferred += key
                throw RetryLater()
            }
        }
    }

    private companion object {
        const val MAX_TITLE_QUERIES = 3
        const val MAX_TMDB_DETAILS = 4
    }
}

private fun usableTitle(value: String): Boolean {
    val name = normalizeTitleForMatch(value)
    return name.length >= 2 || name.singleOrNull()?.let { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } == true
}

internal fun String.year(): Int? = take(4).toIntOrNull()?.takeIf { it in 1900..2099 }
internal fun tmdbKey(id: Int, movie: Boolean) = if (movie) "tmdb:movie:$id" else "tmdb:tv:$id"

internal fun com.aiexile.animetrack.model.Anime.toArchive(sourceId: Long) = LocalMediaArchiveEntity(
    sourceId, bangumiId?.let { "bgm:$it" } ?: tmdbId?.let { tmdbKey(it, LocalMediaMatcher.movieTitle(title)) } ?: "anime:$id",
    title, coverUrl, bangumiId, tmdbId, airDate?.year(), summary, totalEpisodes.takeIf { it > 0 }
)

private fun TmdbTvDetail.toArchive(sourceId: Long, season: Int?): LocalMediaArchiveEntity {
    val selected = seasons.orEmpty().singleOrNull { it.seasonNumber == season }
    // TMDB TV ID 定位整部剧，封面维持整剧级；剧集季数保存在文件上。
    return LocalMediaArchiveEntity(sourceId, tmdbKey(id, false), name, coverUrl ?: selected?.coverUrl,
        tmdbId = id, year = firstAirDate?.year(), summary = overview, episodeCount = numberOfEpisodes)
}

internal fun TmdbMovie.toArchive(sourceId: Long) = LocalMediaArchiveEntity(
    sourceId, tmdbKey(id, true), title, coverUrl, tmdbId = id, year = date?.year(), summary = overview
)

private fun aliases(infobox: List<BangumiInfoboxItem>?): List<String> = infobox.orEmpty()
    .filter { it.key in setOf("别名", "別名", "其他名称", "其他名稱", "英文名", "日文名") }
    .flatMap { field -> when (val value = field.value) {
        is String -> listOf(value)
        is List<*> -> value.mapNotNull { item -> if (item is Map<*, *>) item["v"] as? String else item as? String }
        else -> emptyList()
    } }

/** 只有 Bangumi 正片表确认「原号不是分季集号、偏移后恰好是」才采用，保护 Ani-RSS 已重命名的文件。 */
internal fun mapAniRssEpisode(file: LocalMediaEntity, episodes: List<BangumiEpisode>): Pair<Int, Int?>? {
    val offset = file.episodeOffset?.takeIf { it != 0 } ?: return null
    val raw = file.originalEpisode?.toIntOrNull() ?: return null
    val canonical = episodes.asSequence().filter { it.type == 0 }.map { it.ep ?: it.sort }.toSet()
    val adjusted = raw + offset
    if (raw.toDouble() in canonical || adjusted <= 0 || adjusted.toDouble() !in canonical) return null
    val end = file.episodeEndNumber?.let {
        val adjustedEnd = it + offset
        if (it.toDouble() in canonical || adjustedEnd < adjusted || adjustedEnd.toDouble() !in canonical) return null
        adjustedEnd
    }
    return adjusted to end
}

/** 仅在原号不属于该条目的季内集数、且 Bangumi 给出一对一 sort/ep 时转换。 */
internal fun mapBangumiAbsoluteEpisode(file: LocalMediaEntity, episodes: List<BangumiEpisode>): Pair<Int, Int?>? {
    if (file.explicitEpisodeNumbers || file.episodeType != LocalEpisodeType.MAIN || file.episodeNumber == null) return null
    val raw = file.originalEpisode?.toIntOrNull()?.takeIf { it == file.episodeNumber } ?: return null
    val main = episodes.filter { it.type == 0 }
    fun resolve(number: Int): Int? {
        if (main.any { it.ep == number.toDouble() }) return null
        val ep = main.singleOrNull { it.sort == number.toDouble() }?.ep ?: return null
        return ep.toInt().takeIf { it > 0 && it.toDouble() == ep }
    }
    val mapped = resolve(raw) ?: return null
    val end = file.episodeEndNumber?.let { resolve(it)?.takeIf { end -> end >= mapped } ?: return null }
    return mapped to end
}
