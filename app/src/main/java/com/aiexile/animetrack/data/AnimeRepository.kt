package com.aiexile.animetrack.data

import android.util.Log
import com.aiexile.animetrack.data.network.BangumiSearchFilter
import com.aiexile.animetrack.data.network.BangumiSearchRequest
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.BangumiSubjectDetail
import com.aiexile.animetrack.data.network.CoverDownloader
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.SubscribeRequest
import com.aiexile.animetrack.data.network.RemoveSubscribeRequest
import com.aiexile.animetrack.data.network.TmdbTvDetail
import com.aiexile.animetrack.data.network.parseAnimeStatus
import com.aiexile.animetrack.data.network.toApiString
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.data.sync.WebDAVAutoSyncManager
import com.aiexile.animetrack.ui.home.SeriesMatcher
import com.aiexile.animetrack.util.cleanSummary
import com.aiexile.animetrack.util.formatDate
import com.aiexile.animetrack.util.parseDateToTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

interface AnimeRepository {

    fun getAllAnimes(): Flow<List<Anime>>

    fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>>

    suspend fun getAnimeById(id: Int): Anime?

    fun observeAnimeById(id: Int): Flow<Anime?>

    suspend fun insertAnime(anime: Anime): Long

    suspend fun updateAnime(anime: Anime)

    suspend fun deleteAnime(anime: Anime)

    suspend fun getAnimeByTitle(title: String): Anime?

    suspend fun getAnimeByBangumiId(bangumiId: Int): Anime?

    suspend fun insertAnimes(animes: List<Anime>)

    suspend fun getAnimesWithoutCover(): List<Anime>

    suspend fun searchBangumi(query: String): List<BangumiSubject>

    suspend fun searchTmdb(query: String): List<SearchResult>

    suspend fun searchAll(query: String): List<SearchResult>

    suspend fun getTmdbTvDetail(tmdbId: Int): TmdbTvDetail

    suspend fun fetchBangumiDetail(bangumiId: Int): BangumiSubjectDetail?

    suspend fun getAnimeByTmdbId(tmdbId: Int): Anime?

    fun getAiringAnimes(): Flow<List<Anime>>

    suspend fun getAiringAnimesWithBangumiId(): List<Anime>

    suspend fun clearNewUpdate(id: Int)

    /**
     * 重新识别所有番剧的 seriesKey 并持久化。
     * 在添加/删除番剧或 app 启动时调用，避免每次列表刷新都重新匹配标题。
     */
    suspend fun reassignSeriesKeys()

    fun downloadCoverAsync(animeId: Int, coverUrl: String?, bangumiId: Int?, tmdbId: Int?)

    /** 在应用级协程中补全番剧封面/简介等信息，不会因 ViewModel 销毁而中断 */
    fun syncCoversInBackground(animesToSync: List<Anime> = emptyList())

    /**
     * 从后端同步订阅列表到本地数据库。
     * - 仅更新订阅状态字段（isSubscribed、isFinished、airWeekday）
     * - 不覆盖番剧的完整信息（标题、封面、集数等）
     * - 本地不存在的番剧跳过（不插入）
     * - 同步失败静默处理，不影响用户使用
     */
    suspend fun syncSubscriptionsFromServer()

    /**
     * 在应用级协程中触发订阅同步（脱离 UI 生命周期，避免登录后 UI 切换导致协程被取消）。
     */
    fun triggerSyncSubscriptionsFromServer()

    /**
     * 防抖触发订阅同步：3 秒内多次调用只执行最后一次，正在执行时跳过新请求。
     * 适用于批量导入/同步后触发（Bilibili 同步、Markdown 导入、WebDAV 恢复）。
     */
    fun triggerSyncSubscriptionsFromServerDebounced()

    /**
     * 只从后端拉取订阅列表合并到本地（不上传本地数据）。
     * 适用于 App 冷启动 / 从后台切回前台，避免每次都上传所有本地数据。
     */
    fun triggerPullSubscriptionsFromServer()
}

class AnimeRepositoryImpl(
    private val animeDao: AnimeDao,
    private val context: android.content.Context
) : AnimeRepository {

    companion object {
        private const val TAG = "AnimeTrack"
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val downloadSemaphore = Semaphore(3)
        /** 订阅同步互斥锁：防止批量同步重入 */
        private val subscriptionSyncMutex = Mutex()
        /** 防抖标志：true 表示已有待执行的同步任务 */
        private val pendingSubscriptionSync = java.util.concurrent.atomic.AtomicBoolean(false)
        /** 上传每条间隔，避免触发后端限流 */
        private const val UPLOAD_THROTTLE_MS = 200L
        /** 防抖延迟，批量操作完成后等待此时间再执行同步 */
        private const val SYNC_DEBOUNCE_MS = 3000L
    }

    override fun getAllAnimes(): Flow<List<Anime>> {
        Log.d(TAG, "getAllAnimes: Getting all animes from DAO")
        return animeDao.getAllAnimes()
    }

    override fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>> {
        return animeDao.getAnimesByStatus(status)
    }

    override suspend fun getAnimeById(id: Int): Anime? {
        return animeDao.getAnimeById(id)
    }

    override fun observeAnimeById(id: Int): Flow<Anime?> {
        return animeDao.observeAnimeById(id)
    }

    override suspend fun insertAnime(anime: Anime): Long {
        Log.d(TAG, "insertAnime: Inserting anime - $anime")
        // 如果 coverUrl 是远程 URL，保存其可公开访问版本到 remoteCoverUrl
        // coverUrl 后续会被本地化（下载到本地路径），remoteCoverUrl 保留用于同步到后端
        val animeToInsert = anime.remoteCoverUrl?.let { anime } ?: run {
            val remoteUrl = computeRemoteCoverUrl(anime.coverUrl) ?: return@run anime
            anime.copy(remoteCoverUrl = remoteUrl)
        }
        val id = animeDao.insertAnime(animeToInsert)
        Log.d(TAG, "insertAnime: Inserted with id=$id")
        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
        if (id > 0) {
            com.aiexile.animetrack.di.AppContainer.getUsageStatsRepository().incrementAddedAnime()
            reassignSeriesKeys()
            // 同步订阅到后端
            syncSubscriptionToServer(animeToInsert, isAdd = true)
        }
        return id
    }

    override suspend fun updateAnime(anime: Anime) {
        val oldAnime = animeDao.getAnimeById(anime.id)
        animeDao.updateAnime(anime)
        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
        // 检测状态变为已看完时记录完结统计
        if (oldAnime != null && oldAnime.status != AnimeStatus.COMPLETED && anime.status == AnimeStatus.COMPLETED) {
            com.aiexile.animetrack.di.AppContainer.getUsageStatsRepository().incrementCompletedAnime()
        }
        // 标题可能变化，重新识别 seriesKey
        if (oldAnime?.title != anime.title) {
            reassignSeriesKeys()
        }
        // 同步修改到后端（POST /api/subscriptions/add 为 upsert 语义，存在则更新）
        syncSubscriptionToServer(anime, isAdd = true)
    }

    override suspend fun deleteAnime(anime: Anime) {
        // 先同步取消订阅到后端
        syncSubscriptionToServer(anime, isAdd = false)
        animeDao.deleteAnime(anime)
        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
        reassignSeriesKeys()
    }

    override suspend fun getAnimeByTitle(title: String): Anime? {
        return animeDao.getAnimeByTitle(title)
    }

    override suspend fun getAnimeByBangumiId(bangumiId: Int): Anime? {
        return animeDao.getAnimeByBangumiId(bangumiId)
    }

    override suspend fun insertAnimes(animes: List<Anime>) {
        animeDao.insertAnimes(animes)
    }

    override suspend fun getAnimesWithoutCover(): List<Anime> {
        return animeDao.getAnimesWithoutCover()
    }

    override suspend fun searchBangumi(query: String): List<BangumiSubject> {
        val response = RetrofitClient.bangumiApi.searchSubjects(
            BangumiSearchRequest(
                keyword = query,
                sort = "match",
                filter = BangumiSearchFilter(type = listOf(2))
            )
        )
        return response.data
    }

    override suspend fun searchTmdb(query: String): List<SearchResult> {
        val response = RetrofitClient.tmdbApi.searchTv(query = query)
        return response.results.map { show ->
            SearchResult(
                source = SearchSource.TMDB,
                sourceId = show.id,
                title = show.name,
                coverUrl = show.coverUrl,
                episodeCount = null,
                airDate = show.firstAirDate,
                rating = show.voteAverage,
                summary = show.overview,
                episodeCountText = show.episodeCountText
            )
        }
    }

    override suspend fun searchAll(query: String): List<SearchResult> {
        val bangumiResults = try {
            val response = RetrofitClient.bangumiApi.searchSubjects(
                BangumiSearchRequest(
                    keyword = query,
                    sort = "match",
                    filter = BangumiSearchFilter(type = listOf(2))
                )
            )
            response.data.map { subject ->
                SearchResult(
                    source = SearchSource.BANGUMI,
                    sourceId = subject.id,
                    title = subject.displayName,
                    coverUrl = subject.coverUrl,
                    episodeCount = subject.episodeCount,
                    airDate = subject.date,
                    rating = subject.score?.toFloat(),
                    summary = subject.summary,
                    episodeCountText = subject.episodeCountText
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bangumi search failed in searchAll", e)
            emptyList()
        }

        val tmdbResults = try {
            searchTmdb(query)
        } catch (e: Exception) {
            Log.e(TAG, "TMDB search failed in searchAll", e)
            emptyList()
        }

        return bangumiResults + tmdbResults
    }

    override suspend fun getTmdbTvDetail(tmdbId: Int): TmdbTvDetail {
        return RetrofitClient.tmdbApi.getTvDetail(tmdbId)
    }

    override suspend fun fetchBangumiDetail(bangumiId: Int): BangumiSubjectDetail? {
        return try {
            RetrofitClient.bangumiApi.getSubjectDetail(bangumiId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Bangumi detail for bangumiId: $bangumiId", e)
            null
        }
    }

    override suspend fun getAnimeByTmdbId(tmdbId: Int): Anime? {
        return animeDao.getAnimeByTmdbId(tmdbId)
    }

    override fun getAiringAnimes(): Flow<List<Anime>> {
        return animeDao.getAiringAnimes()
    }

    override suspend fun getAiringAnimesWithBangumiId(): List<Anime> {
        return animeDao.getAiringAnimesWithBangumiId()
    }

    override suspend fun clearNewUpdate(id: Int) {
        animeDao.clearNewUpdate(id)
    }

    override suspend fun reassignSeriesKeys() {
        val allAnimes = animeDao.getAllAnimesList()
        if (allAnimes.isEmpty()) return
        val updated = SeriesMatcher.assignSeriesKeys(allAnimes)
        // 仅持久化 seriesKey 变化的项
        updated.filter { it.seriesKey != allAnimes.find { a -> a.id == it.id }?.seriesKey }
            .forEach { animeDao.updateAnime(it) }
        Log.d(TAG, "reassignSeriesKeys: processed ${allAnimes.size}, updated ${updated.count { it.seriesKey != allAnimes.find { a -> a.id == it.id }?.seriesKey }}")
    }

    override fun downloadCoverAsync(animeId: Int, coverUrl: String?, bangumiId: Int?, tmdbId: Int?) {
        if (coverUrl.isNullOrBlank()) return
        if (bangumiId == null && tmdbId == null) return

        // 本地路径但文件已不存在，清除 DB 中的 coverUrl 避免 UI 空白
        if (coverUrl.startsWith("/") || coverUrl.startsWith("file://")) {
            val localFile = java.io.File(coverUrl.removePrefix("file://"))
            if (!localFile.exists() || localFile.length() == 0L) {
                appScope.launch {
                    try {
                        animeDao.updateCoverUrl(animeId, "")
                        Log.w(TAG, "Local cover file missing, cleared coverUrl: animeId=$animeId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to clear missing cover: animeId=$animeId", e)
                    }
                }
            }
            return
        }

        appScope.launch {
            downloadSemaphore.withPermit {
                try {
                    val localPath = if (bangumiId != null) {
                        CoverDownloader.downloadAndLocalize(
                            context = context,
                            coverUrl = coverUrl,
                            bangumiId = bangumiId
                        ) ?: return@withPermit
                    } else {
                        CoverDownloader.downloadAndLocalizeById(
                            context = context,
                            coverUrl = coverUrl,
                            id = tmdbId!!,
                            prefix = "tmdb"
                        ) ?: return@withPermit
                    }

                    if (localPath != coverUrl) {
                        animeDao.updateCoverUrl(animeId, localPath)
                        Log.d(TAG, "Cover localized async: animeId=$animeId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Async cover download failed: animeId=$animeId", e)
                }
            }
        }
    }

    override fun syncCoversInBackground(animesToSync: List<Anime>) {
        appScope.launch {
            val animesWithoutCover = if (animesToSync.isNotEmpty()) {
                animesToSync.reversed()
            } else {
                animeDao.getAnimesWithoutCover().reversed()
            }

            if (animesWithoutCover.isEmpty()) return@launch

            Log.d(TAG, "Background cover sync: ${animesWithoutCover.size} animes to process")

            var count = 0
            for (anime in animesWithoutCover) {
                try {
                    val parenIndex = anime.title.indexOf("(")
                    val bracketIndex = anime.title.indexOf("（")
                    val splitIndex = when {
                        parenIndex >= 0 && bracketIndex >= 0 -> minOf(parenIndex, bracketIndex)
                        parenIndex >= 0 -> parenIndex
                        bracketIndex >= 0 -> bracketIndex
                        else -> -1
                    }
                    val cleanTitle = if (splitIndex > 0) anime.title.substring(0, splitIndex).trim() else anime.title.trim()
                    val extractedNote = if (splitIndex > 0) {
                        anime.title.substring(splitIndex + 1).removeSuffix(")").removeSuffix("）").trim()
                    } else ""

                    val results = searchBangumi(cleanTitle)
                    val bestMatch = results.firstOrNull()

                    if (bestMatch != null) {
                        val detail = fetchBangumiDetail(bestMatch.id)

                        val summary = detail?.summary?.cleanSummary() ?: bestMatch.summary

                        val apiEps = detail?.eps
                        val apiTotalEps = detail?.totalEpisodes

                        val mainEps = if (apiEps != null && apiEps > 0) apiEps else 0
                        val allEps = if (apiTotalEps != null && apiTotalEps > 0) apiTotalEps else 0

                        val finalTotalEpisodes = when {
                            mainEps > 0 -> mainEps
                            allEps > 0 -> allEps
                            else -> anime.totalEpisodes
                        }
                        val finalCurrentEpisodes = if (mainEps > 0 || allEps > 0) 0 else anime.currentEpisodes

                        val newWatchedEpisodes = if (
                            anime.status == AnimeStatus.COMPLETED
                            && anime.watchedEpisodes == 0
                            && finalTotalEpisodes > 0
                        ) finalTotalEpisodes else anime.watchedEpisodes

                        val updatedAnime = anime.copy(
                            title = cleanTitle,
                            coverUrl = bestMatch.coverUrl,
                            rating = detail?.score?.toFloat() ?: bestMatch.score?.toFloat(),
                            totalEpisodes = finalTotalEpisodes,
                            currentEpisodes = finalCurrentEpisodes,
                            watchedEpisodes = newWatchedEpisodes,
                            summary = summary,
                            bangumiId = bestMatch.id,
                            airDate = detail?.date ?: anime.airDate,
                            airWeekday = detail?.airWeekday ?: anime.airWeekday,
                            notes = if (extractedNote.isNotEmpty()) extractedNote else anime.notes
                        )

                        updateAnime(updatedAnime)
                        downloadCoverAsync(
                            animeId = updatedAnime.id,
                            coverUrl = updatedAnime.coverUrl,
                            bangumiId = updatedAnime.bangumiId,
                            tmdbId = updatedAnime.tmdbId
                        )
                        count++
                        Log.d(TAG, "Background sync: synced ${anime.title}")
                    }

                    delay(800)
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync failed for: ${anime.title}", e)
                }
            }

            Log.d(TAG, "Background cover sync complete: $count/${animesWithoutCover.size}")
        }
    }

    /**
     * 同步订阅状态到后端（仅登录时生效）。
     * - isAdd=true: 调用 POST /subscriptions/add
     * - isAdd=false: 调用 POST /subscriptions/remove
     * 网络失败不影响本地操作。
     */
    private fun syncSubscriptionToServer(anime: Anime, isAdd: Boolean) {
        appScope.launch {
            try {
                val userAuthManager = com.aiexile.animetrack.di.AppContainer.getUserAuthManager()
                if (!userAuthManager.isLoggedIn.first()) return@launch

                val animeId = anime.bangumiId?.toString() ?: anime.id.toString()
                if (isAdd) {
                    // 解析可公开访问的封面 URL（优先使用 remoteCoverUrl）
                    val animeImage = resolveAnimeImageForServer(anime)
                    val response = RetrofitClient.userAuthApi.addSubscription(
                        SubscribeRequest(
                            animeId = animeId,
                            animeTitle = anime.title,
                            animeImage = animeImage,
                            airDate = anime.airDate,
                            isAiring = if (anime.isFinished) 0 else 1, // 1=连载中, 0=已完结
                            weekday = anime.airWeekday,
                            totalEpisodes = anime.totalEpisodes,
                            watchedEpisodes = anime.watchedEpisodes,
                            currentEpisodes = anime.currentEpisodes,
                            status = anime.status.toApiString(),
                            rating = anime.rating,
                            notes = anime.notes.ifBlank { null },
                            startDate = anime.startDate?.let { formatDate(it) },
                            finishDate = anime.finishDate?.let { formatDate(it) }
                        )
                    )
                    if (response.success) {
                        Log.d(TAG, "Subscription added to server: ${anime.title}")
                    } else {
                        Log.w(TAG, "Subscription add failed: ${anime.title}, message=${response.message}")
                    }
                } else {
                    RetrofitClient.userAuthApi.removeSubscription(
                        RemoveSubscribeRequest(animeId = animeId)
                    )
                    Log.d(TAG, "Subscription removed from server: ${anime.title}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync subscription failed (non-fatal): ${anime.title}", e)
            }
        }
    }

    /**
     * 计算远程封面 URL（可公开访问的 wsrv.nl 代理 URL）。
     * - lain.bgm.tv URL → wsrv.nl 代理 URL
     * - 其他 http/https URL → 原样返回
     * - 本地路径/null → null
     */
    private fun computeRemoteCoverUrl(coverUrl: String?): String? {
        val url = coverUrl ?: return null
        return when {
            url.contains("lain.bgm.tv") -> "https://wsrv.nl/?url=$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> null
        }
    }

    /**
     * 解析番剧封面 URL 为后端可访问的公开 URL。
     * 优先使用 remoteCoverUrl（添加时已保存），兼容旧数据回退到 coverUrl。
     */
    private fun resolveAnimeImageForServer(anime: Anime): String? {
        // 优先使用添加时保存的远程 URL
        anime.remoteCoverUrl?.let { return it }
        // 兼容旧数据（remoteCoverUrl 为 null 时从 coverUrl 计算）
        return computeRemoteCoverUrl(anime.coverUrl)
    }

    override suspend fun syncSubscriptionsFromServer() {
        Log.d(TAG, "syncSubscriptionsFromServer: start")
        val userAuthManager = com.aiexile.animetrack.di.AppContainer.getUserAuthManager()
        val loggedIn = userAuthManager.isLoggedIn.first()
        if (!loggedIn) {
            Log.w(TAG, "syncSubscriptionsFromServer: skipped, user not logged in")
            return
        }
        Log.d(TAG, "syncSubscriptionsFromServer: user logged in, proceeding")

        try {
            // ===== 第一步：上传本地所有番剧到后端 =====
            // 本地有 = 已订阅，逐条上传到后端（单条失败不影响整体）
            val localAnimes = animeDao.getAllAnimesList()
            Log.d(TAG, "syncSubscriptionsFromServer: local animes count=${localAnimes.size}")
            var uploadedCount = 0
            for (anime in localAnimes) {
                val animeId = anime.bangumiId?.toString() ?: continue
                // 解析可公开访问的封面 URL（优先使用 remoteCoverUrl）
                val animeImage = resolveAnimeImageForServer(anime)
                try {
                    RetrofitClient.userAuthApi.addSubscription(
                        SubscribeRequest(
                            animeId = animeId,
                            animeTitle = anime.title,
                            animeImage = animeImage,
                            airDate = anime.airDate,
                            isAiring = if (anime.isFinished) 0 else 1, // 1=连载中, 0=已完结
                            weekday = anime.airWeekday,
                            totalEpisodes = anime.totalEpisodes,
                            watchedEpisodes = anime.watchedEpisodes,
                            currentEpisodes = anime.currentEpisodes,
                            status = anime.status.toApiString(),
                            rating = anime.rating,
                            notes = anime.notes.ifBlank { null },
                            startDate = anime.startDate?.let { formatDate(it) },
                            finishDate = anime.finishDate?.let { formatDate(it) }
                        )
                    )
                    uploadedCount++
                } catch (e: retrofit2.HttpException) {
                    val errorBody = try {
                        e.response()?.errorBody()?.string()
                    } catch (_: Exception) { null }
                    Log.w(TAG, "syncSubscriptionsFromServer: upload failed for ${anime.title}, code=${e.code()}, error=$errorBody", e)
                } catch (e: Exception) {
                    Log.w(TAG, "syncSubscriptionsFromServer: upload failed for ${anime.title}", e)
                }
                // 限流：每条上传后间隔，避免短时间内大量请求导致后端拒绝
                delay(UPLOAD_THROTTLE_MS)
            }
            Log.d(TAG, "syncSubscriptionsFromServer: uploaded=$uploadedCount/${localAnimes.size}")

            // ===== 第二步：从后端拉取订阅列表，合并到本地 =====
            val response = RetrofitClient.userAuthApi.getSubscriptions()
            if (!response.success || response.subscriptions == null) {
                Log.d(TAG, "Sync done: uploaded=$uploadedCount, remote list unavailable")
                return
            }

            var mergedCount = 0
            var insertedCount = 0
            for (remote in response.subscriptions) {
                val bangumiId = remote.animeId.toIntOrNull() ?: continue
                val existing = animeDao.getAnimeByBangumiId(bangumiId)
                if (existing == null) {
                    // 本地没有 → 插入（同步其他设备添加的番剧），完整信息留空待用户点击详情时补全
                    val metaAnime = Anime(
                        title = remote.animeTitle,
                        totalEpisodes = remote.totalEpisodes ?: 0,
                        watchedEpisodes = remote.watchedEpisodes ?: 0,
                        status = parseAnimeStatus(remote.status),
                        rating = remote.rating,
                        notes = remote.notes ?: "",
                        startDate = parseDateToTimestamp(remote.startDate),
                        finishDate = parseDateToTimestamp(remote.finishDate),
                        coverUrl = remote.animeImage,
                        airDate = remote.airDate,
                        summary = null,
                        bangumiId = bangumiId,
                        airWeekday = remote.weekday,
                        isFinished = !remote.isAiring, // isAiring: true=连载中, false=已完结
                        currentEpisodes = remote.currentEpisodes ?: 0
                    )
                    animeDao.insertAnime(metaAnime)
                    insertedCount++
                } else {
                    // 本地已有 → 跳过，保留本地完整数据
                    mergedCount++
                }
            }
            Log.d(
                TAG,
                "Sync subscriptions: uploaded=$uploadedCount, " +
                    "remote merged(skipped)=$mergedCount, inserted=$insertedCount, " +
                    "remote total=${response.subscriptions.size}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Sync subscriptions from server failed (non-fatal)", e)
        }
    }

    override fun triggerSyncSubscriptionsFromServer() {
        appScope.launch {
            syncSubscriptionsFromServer()
        }
    }

    override fun triggerSyncSubscriptionsFromServerDebounced() {
        // 已有待执行任务则跳过（防抖）
        if (!pendingSubscriptionSync.compareAndSet(false, true)) return
        appScope.launch {
            try {
                // 等待防抖窗口，期间新的调用会被上面的 compareAndSet 拦截
                delay(SYNC_DEBOUNCE_MS)
            } finally {
                pendingSubscriptionSync.set(false)
            }
            // 用 Mutex 防止与正在进行的同步重入
            if (!subscriptionSyncMutex.tryLock()) {
                Log.d(TAG, "triggerSyncSubscriptionsFromServerDebounced: sync already in progress, skipped")
                return@launch
            }
            try {
                syncSubscriptionsFromServer()
            } finally {
                subscriptionSyncMutex.unlock()
            }
        }
    }

    override fun triggerPullSubscriptionsFromServer() {
        appScope.launch {
            // 用 Mutex 防止与正在进行的全量同步重入
            if (!subscriptionSyncMutex.tryLock()) {
                Log.d(TAG, "triggerPullSubscriptionsFromServer: sync already in progress, skipped")
                return@launch
            }
            try {
                pullSubscriptionsFromServer()
            } finally {
                subscriptionSyncMutex.unlock()
            }
        }
    }

    /**
     * 只从后端拉取订阅列表合并到本地（不上传本地数据）。
     * - 本地已有的番剧跳过（保留本地完整数据）
     * - 本地没有的番剧插入元数据（完整信息留空待用户点击详情时补全）
     * - 未登录静默跳过
     */
    private suspend fun pullSubscriptionsFromServer() {
        Log.d(TAG, "pullSubscriptionsFromServer: start")
        val userAuthManager = com.aiexile.animetrack.di.AppContainer.getUserAuthManager()
        val loggedIn = userAuthManager.isLoggedIn.first()
        if (!loggedIn) {
            Log.d(TAG, "pullSubscriptionsFromServer: skipped, user not logged in")
            return
        }

        try {
            val response = RetrofitClient.userAuthApi.getSubscriptions()
            if (!response.success || response.subscriptions == null) {
                Log.d(TAG, "pullSubscriptionsFromServer: remote list unavailable")
                return
            }

            var mergedCount = 0
            var insertedCount = 0
            for (remote in response.subscriptions) {
                val bangumiId = remote.animeId.toIntOrNull() ?: continue
                val existing = animeDao.getAnimeByBangumiId(bangumiId)
                if (existing == null) {
                    // 本地没有 → 插入（同步其他设备添加的番剧），完整信息留空待用户点击详情时补全
                    val metaAnime = Anime(
                        title = remote.animeTitle,
                        totalEpisodes = remote.totalEpisodes ?: 0,
                        watchedEpisodes = remote.watchedEpisodes ?: 0,
                        status = parseAnimeStatus(remote.status),
                        rating = remote.rating,
                        notes = remote.notes ?: "",
                        startDate = parseDateToTimestamp(remote.startDate),
                        finishDate = parseDateToTimestamp(remote.finishDate),
                        coverUrl = remote.animeImage,
                        airDate = remote.airDate,
                        summary = null,
                        bangumiId = bangumiId,
                        airWeekday = remote.weekday,
                        isFinished = !remote.isAiring,
                        currentEpisodes = remote.currentEpisodes ?: 0
                    )
                    animeDao.insertAnime(metaAnime)
                    insertedCount++
                } else {
                    // 本地已有 → 跳过，保留本地完整数据
                    mergedCount++
                }
            }
            Log.d(
                TAG,
                "pullSubscriptionsFromServer: merged(skipped)=$mergedCount, " +
                    "inserted=$insertedCount, remote total=${response.subscriptions.size}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "pullSubscriptionsFromServer failed (non-fatal)", e)
        }
    }
}
