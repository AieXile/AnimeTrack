package com.aiexile.animetrack.data

import android.util.Log
import androidx.room.withTransaction
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.network.BangumiSearchFilter
import com.aiexile.animetrack.data.network.BangumiSearchRequest
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.BangumiSubjectDetail
import com.aiexile.animetrack.data.network.CoverDownloader
import com.aiexile.animetrack.data.log.AppLogManager
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
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.di.ImportBannerState
import com.aiexile.animetrack.ui.home.SeriesMatcher
import com.aiexile.animetrack.util.RatingUtils
import com.aiexile.animetrack.util.cleanSummary
import com.aiexile.animetrack.util.formatDate
import com.aiexile.animetrack.util.parseDateToTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

interface AnimeRepository {

    fun getAllAnimes(): Flow<List<Anime>>

    fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>>

    /** 按状态分组统计数量，供个人页追番统计栏使用 */
    fun getStatusCounts(): Flow<List<StatusCount>>

    suspend fun getAnimeById(id: Int): Anime?

    fun observeAnimeById(id: Int): Flow<Anime?>

    suspend fun insertAnime(anime: Anime): Long

    suspend fun updateAnime(anime: Anime)

    /**
     * 纯本地更新：只写入数据库并通知本地同步（WebDAV），不触发后端订阅同步。
     * 适用于封面本地化、简介/开播信息回填、完结状态重算等内部字段变更。
     * @return 更新前的旧数据（不存在则为 null），便于调用方按需比对。
     */
    suspend fun updateAnimeInternal(anime: Anime): Anime?

    /**
     * 批量更新番剧（单事务写库）：与逐条调用 [updateAnime] 的最终状态与副作用一致
     * （WebDAV 通知合并为一次、完结统计逐条比对、去抖 reassign、逐条条件性后端同步），
     * 但只触发一次 Room 失效，避免启动期批量同步时 N 次事务引发 N 次列表重算。
     */
    suspend fun batchUpdateAnimes(animes: List<Anime>)

    /**
     * 批量纯本地更新（单事务写库）：与逐条调用 [updateAnimeInternal] 一致
     * （仅 WebDAV 通知 + 完结统计 + 标题变化 reassign，不触发后端同步）。
     * @return 与入参顺序对应的旧数据列表（不存在则为 null）
     */
    suspend fun batchUpdateAnimesInternal(animes: List<Anime>): List<Anime?>

    /**
     * 手动触发后端订阅同步（POST /subscriptions/add）。
     * 供 ViewModel 在防抖后统一调用，避免逐集更新时频繁请求后端。
     */
    fun syncAnimeToServer(anime: Anime)

    /** 标记「Bangumi 未关联」详情页提示已展示（同一部番剧仅提示一次） */
    suspend fun markBangumiMatchHintShown(id: Int)

    suspend fun deleteAnime(anime: Anime)

    suspend fun getAnimeByTitle(title: String): Anime?

    suspend fun getAnimeByBangumiId(bangumiId: Int): Anime?

    suspend fun insertAnimes(animes: List<Anime>)

    /**
     * 批量插入番剧（单事务），并保留与单条 insertAnime 等价的副作用：
     * - remoteCoverUrl 计算
     * - WebDAV 通知
     * - usageStats 自增
     * - 单次去抖 reassignSeriesKeys
     * - 逐条 syncSubscriptionToServer
     * @return 与入参顺序对应的插入结果 ID 列表（OnConflict IGNORE 时为 -1）
     */
    suspend fun batchInsertAnimes(animes: List<Anime>): List<Long>

    suspend fun getAnimesWithoutCover(): List<Anime>

    suspend fun searchBangumi(query: String): List<BangumiSubject>

    suspend fun searchTmdb(query: String): List<SearchResult>

    suspend fun searchAll(query: String): List<SearchResult>

    suspend fun getTmdbTvDetail(tmdbId: Int): TmdbTvDetail

    suspend fun fetchBangumiDetail(bangumiId: Int): BangumiSubjectDetail?

    /** 获取 Bangumi 条目详情（不捕获异常，供调用方区分失败原因） */
    suspend fun getBangumiSubjectDetail(bangumiId: Int): BangumiSubjectDetail

    suspend fun getAnimeByTmdbId(tmdbId: Int): Anime?

    fun getAiringAnimes(): Flow<List<Anime>>

    suspend fun getAiringAnimesList(): List<Anime>

    suspend fun getAiringAnimesWithBangumiId(): List<Anime>

    suspend fun clearNewUpdate(id: Int)

    /**
     * 设置/取消置顶。置顶的卡片固定排在主界面列表最前，不受筛选/排序影响。
     * 纯本地字段变更，不触发后端订阅同步，但会通知 WebDAV 备份。
     */
    suspend fun setPinned(id: Int, isPinned: Boolean)

    /**
     * 重新识别所有番剧的 seriesKey 并持久化。
     * 在添加/删除番剧或 app 启动时调用，避免每次列表刷新都重新匹配标题。
     */
    suspend fun reassignSeriesKeys()

    fun downloadCoverAsync(animeId: Int, coverUrl: String?, bangumiId: Int?, tmdbId: Int?)

    /**
     * 在应用级协程中补全番剧封面/简介等信息，不会因 ViewModel 销毁而中断。
     * @param animesToSync 指定补全列表；空列表时自动捞取所有无封面的番剧
     * @param notifySuccess 全部成功时是否发送全局横幅（手动触发场景为 true，导入/启动自动补全静默）
     */
    fun syncCoversInBackground(
        animesToSync: List<Anime> = emptyList(),
        notifySuccess: Boolean = false
    )

    /**
     * 启动时检查是否存在元数据缺失的番剧（无封面），存在且距上次自动补全超过 24 小时则触发补全。
     * 覆盖场景：导入时网络不可达（如未挂代理）导致 Bangumi 匹配失败，恢复网络后重启应用自动补齐。
     */
    fun triggerCoverBackfillIfNeeded()

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

    /** 封面本地化写库合批队列：key=animeId，value=最新 coverUrl（flush 前合并同 id 的更新） */
    private val pendingCoverUrlUpdates = ConcurrentHashMap<Int, String>()
    private val coverBatcherStarted = AtomicBoolean(false)

    // ===== Bangumi 关系链系列识别（进程级缓存，冷启动后增量补拉） =====

    /** 关系数据缓存：subjectId → 已过滤为动漫类型的关联列表。仅缓存拉取成功的结果（空列表 = 确认无关联）。 */
    private val relationsCache = ConcurrentHashMap<Int, List<BangumiSeriesResolver.SubjectRelation>>()

    /** 拉取失败的 subjectId 集合：本次进程内不再重试（避免断网时反复请求），冷启动后重新尝试 */
    private val failedSubjectIds = ConcurrentHashMap.newKeySet<Int>()

    /** Bangumi 关系链解析结果缓存：anime.id → 系列归属，供 reassignSeriesKeys 融合（权威，优先于正则） */
    @Volatile
    private var bangumiSeriesCache: Map<Int, BangumiSeriesResolver.SeriesAssignment> = emptyMap()

    /** 关系链同步单飞锁：正在同步时新触发直接跳过（下次 reassign 再补） */
    private val seriesRelationsMutex = Mutex()

    /** 关系链同步时单次最多拉取的条目数（库内 + 递归库外前传），防止异常数据导致请求风暴 */
    private val relationsFetchLimit = 300

    companion object {
        private const val TAG = "AnimeTrack"
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // 下载封面并发上限。启动期可考虑后续优化为动态调整（如启动期 2、空闲期 3），
        // 但 Semaphore 无法动态调整且重建会丢失排队任务，当前保持固定 3。
        private val downloadSemaphore = Semaphore(3)
        /** 订阅同步互斥锁：防止批量同步重入 */
        private val subscriptionSyncMutex = Mutex()
        /** 防抖标志：true 表示已有待执行的同步任务 */
        private val pendingSubscriptionSync = java.util.concurrent.atomic.AtomicBoolean(false)
        /** 上传每条间隔，避免触发后端限流 */
        private const val UPLOAD_THROTTLE_MS = 200L
        /** 防抖延迟，批量操作完成后等待此时间再执行同步 */
        private const val SYNC_DEBOUNCE_MS = 3000L
        /** reassignSeriesKeys 防抖延迟，批量增删时只执行最后一次 */
        private const val REASSIGN_DEBOUNCE_MS = 1000L
        /** Bangumi 关系拉取间隔（毫秒），与封面补全节奏一致，避免触发限流 */
        private const val RELATIONS_FETCH_INTERVAL_MS = 700L
        /** 封面本地化写库合批 flush 延迟：短时间内的多次封面更新合并为一次事务写库 */
        private const val COVER_BATCH_FLUSH_MS = 300L
        /** reassignSeriesKeys 防抖 Job（在 appScope 上调度，cancel/replace 安全） */
        @Volatile
        private var reassignSeriesKeysJob: Job? = null
    }

    override fun getAllAnimes(): Flow<List<Anime>> {
        if (BuildConfig.DEBUG) Log.d(TAG, "getAllAnimes: Getting all animes from DAO")
        return animeDao.getAllAnimes()
    }

    override fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>> {
        return animeDao.getAnimesByStatus(status)
    }

    override fun getStatusCounts(): Flow<List<StatusCount>> {
        return animeDao.getStatusCounts()
    }

    override suspend fun getAnimeById(id: Int): Anime? {
        return animeDao.getAnimeById(id)
    }

    override fun observeAnimeById(id: Int): Flow<Anime?> {
        return animeDao.observeAnimeById(id)
    }

    override suspend fun insertAnime(anime: Anime): Long {
        if (BuildConfig.DEBUG) Log.d(TAG, "insertAnime: Inserting anime - $anime")
        // 如果 coverUrl 是远程 URL，保存其可公开访问版本到 remoteCoverUrl
        // coverUrl 后续会被本地化（下载到本地路径），remoteCoverUrl 保留用于同步到后端
        val animeToInsert = anime.remoteCoverUrl?.let { anime } ?: run {
            val remoteUrl = computeRemoteCoverUrl(anime.coverUrl) ?: return@run anime
            anime.copy(remoteCoverUrl = remoteUrl)
        }
        val id = animeDao.insertAnime(animeToInsert)
        if (BuildConfig.DEBUG) Log.d(TAG, "insertAnime: Inserted with id=$id")
        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
        if (id > 0) {
            com.aiexile.animetrack.di.AppContainer.getUsageStatsRepository().incrementAddedAnime()
            triggerReassignSeriesKeysDebounced()
            // 同步订阅到后端
            syncSubscriptionToServer(animeToInsert, isAdd = true)
            // 异步推送到 Bangumi（fire-and-forget，不阻塞新增流程）
            // batchInsertAnimes 不推 Bangumi，避免从 Bangumi 拉取后又推回造成循环
            val bangumiId = animeToInsert.bangumiId
            if (bangumiId != null) {
                val watchedEps = animeToInsert.watchedEpisodes
                val status = animeToInsert.status
                appScope.launch {
                    val syncManager = com.aiexile.animetrack.di.AppContainer.getSyncManager()
                    if (watchedEps > 0) {
                        syncManager.pushProgressThenStatus(bangumiId, watchedEps, status)
                    } else {
                        syncManager.pushStatusToRemote(bangumiId, status)
                    }
                }
            }
        }
        return id
    }

    override suspend fun updateAnimeInternal(anime: Anime): Anime? {
        // 与批量路径共用单事务实现，副作用一致
        return batchUpdateAnimesInternal(listOf(anime)).firstOrNull()
    }

    override suspend fun batchUpdateAnimesInternal(animes: List<Anime>): List<Anime?> {
        if (animes.isEmpty()) return emptyList()
        val database = AnimeDatabase.getDatabase(context)
        // 单事务内读取旧值并写入：N 次写库只触发一次 Room 失效（一次列表重算）
        val oldAnimes = database.withTransaction {
            animes.map { anime ->
                val old = animeDao.getAnimeById(anime.id)
                animeDao.updateAnime(anime)
                old
            }
        }
        // 副作用在事务外执行（与 batchInsertAnimes 一致）
        var completedCount = 0
        var titleChanged = false
        for ((old, anime) in oldAnimes.zip(animes)) {
            // 检测状态变为已看完时记录完结统计
            if (old != null && old.status != AnimeStatus.COMPLETED && anime.status == AnimeStatus.COMPLETED) {
                completedCount++
            }
            // 标题可能变化，重新识别 seriesKey
            if (old != null && old.title != anime.title) {
                titleChanged = true
            }
        }
        if (completedCount > 0) {
            val usageStats = com.aiexile.animetrack.di.AppContainer.getUsageStatsRepository()
            repeat(completedCount) { usageStats.incrementCompletedAnime() }
        }
        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
        if (titleChanged) {
            triggerReassignSeriesKeysDebounced()
        }
        return oldAnimes
    }

    override suspend fun batchUpdateAnimes(animes: List<Anime>) {
        if (animes.isEmpty()) return
        val oldAnimes = batchUpdateAnimesInternal(animes)
        // 仅当用户可见字段变化时才同步到后端（与 updateAnime 逐条行为一致）
        for ((old, anime) in oldAnimes.zip(animes)) {
            if (shouldSyncToServer(old, anime)) {
                syncSubscriptionToServer(anime, isAdd = true)
            }
        }
    }

    override suspend fun updateAnime(anime: Anime) {
        val oldAnime = updateAnimeInternal(anime)
        // 仅当用户可见字段变化时才同步到后端，避免封面/简介等内部回填触发无谓的 POST
        if (shouldSyncToServer(oldAnime, anime)) {
            syncSubscriptionToServer(anime, isAdd = true)
        }
    }

    override fun syncAnimeToServer(anime: Anime) {
        syncSubscriptionToServer(anime, isAdd = true)
    }

    override suspend fun markBangumiMatchHintShown(id: Int) {
        animeDao.markBangumiMatchHintShown(id)
    }

    /**
     * 判断本次更新是否需要同步到后端。
     * 仅关注用户可见/后端关心的字段：状态、观看进度、评分、备注、标题、完结状态、Bangumi 绑定。
     * oldAnime 为 null（新数据或查不到）时保守返回 true。
     *
     * isFinished 纳入比对的原因：后端用 isAiring(0/1) 表示连载状态，
     * 当本地因拉取到 Bangumi infobox「播放结束」而把 isFinished 翻为 true 时，
     * 需要触发 syncSubscriptionToServer 把 isAiring 更新为 0。
     *
     * bangumiId 纳入比对的原因：详情页搜索重新匹配只改 bangumiId（封面/简介等元数据
     * 回填不触发同步），若不上传，服务器上仍是旧标识（'0'/UUID/旧本地 id）的寄生行，
     * 重启拉取合并时按旧标题/无效 id 匹配失败，会插入"幽灵卡片"（旧名、无元数据）。
     */
    private fun shouldSyncToServer(oldAnime: Anime?, newAnime: Anime): Boolean {
        if (oldAnime == null) return true
        return oldAnime.status != newAnime.status
                || oldAnime.watchedEpisodes != newAnime.watchedEpisodes
                || oldAnime.rating != newAnime.rating
                || oldAnime.notes != newAnime.notes
                || oldAnime.title != newAnime.title
                || oldAnime.isFinished != newAnime.isFinished
                || oldAnime.bangumiId != newAnime.bangumiId
    }

    override suspend fun deleteAnime(anime: Anime) {
        // 先同步取消订阅到后端
        syncSubscriptionToServer(anime, isAdd = false)
        animeDao.deleteAnime(anime)
        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
        triggerReassignSeriesKeysDebounced()
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

    override suspend fun batchInsertAnimes(animes: List<Anime>): List<Long> {
        if (animes.isEmpty()) return emptyList()
        // 与单条 insertAnime 一致：保存可公开访问的远程封面 URL
        val animesToInsert = animes.map { anime ->
            anime.remoteCoverUrl?.let { anime } ?: run {
                val remoteUrl = computeRemoteCoverUrl(anime.coverUrl) ?: return@run anime
                anime.copy(remoteCoverUrl = remoteUrl)
            }
        }
        // 单事务批量插入，避免 N 次独立事务的开销
        val database = AnimeDatabase.getDatabase(context)
        val ids = database.withTransaction {
            animesToInsert.map { animeDao.insertAnime(it) }
        }
        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
        // 仅对成功插入的项触发副作用（与单条 insertAnime 的 id > 0 判断一致）
        val successAnimes = mutableListOf<Anime>()
        for ((anime, id) in animesToInsert.zip(ids)) {
            if (id > 0) {
                com.aiexile.animetrack.di.AppContainer.getUsageStatsRepository().incrementAddedAnime()
                successAnimes.add(anime)
            }
        }
        if (successAnimes.isNotEmpty()) {
            // 单次去抖触发 reassignSeriesKeys，不在事务内调用
            triggerReassignSeriesKeysDebounced()
            // 同步订阅到后端（批量，与单条 insertAnime 行为一致）
            successAnimes.forEach { anime -> syncSubscriptionToServer(anime, isAdd = true) }
        }
        return ids
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

    override suspend fun getBangumiSubjectDetail(bangumiId: Int): BangumiSubjectDetail {
        return RetrofitClient.bangumiApi.getSubjectDetail(bangumiId)
    }

    override suspend fun getAnimeByTmdbId(tmdbId: Int): Anime? {
        return animeDao.getAnimeByTmdbId(tmdbId)
    }

    override fun getAiringAnimes(): Flow<List<Anime>> {
        return animeDao.getAiringAnimes()
    }

    override suspend fun getAiringAnimesList(): List<Anime> {
        return animeDao.getAiringAnimesList()
    }

    override suspend fun getAiringAnimesWithBangumiId(): List<Anime> {
        return animeDao.getAiringAnimesWithBangumiId()
    }

    override suspend fun clearNewUpdate(id: Int) {
        animeDao.clearNewUpdate(id)
    }

    override suspend fun setPinned(id: Int, isPinned: Boolean) {
        animeDao.setPinned(id, isPinned)
        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
    }

    /**
     * 防抖触发 reassignSeriesKeys：[REASSIGN_DEBOUNCE_MS] 内多次调用只执行最后一次。
     * 适用于 insertAnime/updateAnime/deleteAnime 等可能连续触发的场景，
     * 避免 SeriesMatcher 全表扫描重复执行。
     *
     * 注意：cancel + 重新赋值在协程模型下是安全的（Job.cancel 是非阻塞的）。
     * 即便存在轻微竞态（两次并发调用各自 launch），reassignSeriesKeys 本身是幂等的，
     * 最坏情况只是多执行一次，不会产生数据不一致。
     */
    fun triggerReassignSeriesKeysDebounced() {
        reassignSeriesKeysJob?.cancel()
        reassignSeriesKeysJob = appScope.launch {
            delay(REASSIGN_DEBOUNCE_MS)
            reassignSeriesKeys()
        }
    }

    override suspend fun reassignSeriesKeys() {
        recomputeAndPersistSeriesKeys(triggerRelationsSync = true)
    }

    /**
     * 重算并持久化 seriesKey / seasonNumber：
     * - 标题正则分组（SeriesMatcher.assignSeriesKeys）
     * - 融合 Bangumi 关系链结果（[bangumiSeriesCache]，权威优先）
     * 仅持久化发生变化的项，合并为单事务写库（一次 Room 失效）。
     *
     * @param triggerRelationsSync true 时在完成后异步触发 Bangumi 关系链增量同步
     *        （关系链同步完成后的重算传 false，避免无限递归触发）
     */
    private suspend fun recomputeAndPersistSeriesKeys(triggerRelationsSync: Boolean) {
        val allAnimes = animeDao.getAllAnimesList()
        if (allAnimes.isEmpty()) return
        // 正则匹配计算挪到 Default，避免启动期在主线程全表扫描
        val updated = withContext(Dispatchers.Default) {
            SeriesMatcher.assignSeriesKeys(allAnimes, bangumiSeriesCache)
        }
        // 建立 id → 原对象索引，避免在 filter lambda 内做 O(n²) 线性查找
        val oldById = allAnimes.associateBy { it.id }
        // 仅持久化 seriesKey/seasonNumber 变化的项，且合并为单事务写库（一次 Room 失效）
        val changed = updated.filter {
            it.seriesKey != oldById[it.id]?.seriesKey || it.seasonNumber != oldById[it.id]?.seasonNumber
        }
        if (changed.isNotEmpty()) {
            val database = AnimeDatabase.getDatabase(context)
            database.withTransaction { changed.forEach { animeDao.updateAnime(it) } }
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "reassignSeriesKeys: processed ${allAnimes.size}, updated ${changed.size}, bangumiOverrides=${bangumiSeriesCache.size}")
        }
        if (triggerRelationsSync) {
            appScope.launch { syncSeriesRelationsFromBangumi() }
        }
    }

    /**
     * Bangumi 关系链系列同步（后台单飞）：
     * 1. 增量拉取「未缓存」条目的关联关系（含递归库外前传，用于计算准确的链头与季数深度）
     * 2. 调 [BangumiSeriesResolver.resolve] 计算每部番剧的系列归属（seriesKey + seasonNumber）
     * 3. 结果写入 [bangumiSeriesCache] 并触发重算持久化（不回调本方法，避免循环）
     *
     * 失败静默降级：拉取失败的条目跳过（进程内不重试），正则分组结果保持不变。
     * 拉取间隔 [RELATIONS_FETCH_INTERVAL_MS]，与封面补全的节流节奏一致。
     */
    private suspend fun syncSeriesRelationsFromBangumi() {
        if (!seriesRelationsMutex.tryLock()) return
        try {
            // bangumiId <= 0 为历史脏数据（B站导入经服务端同步产生的 animeId='0'），
            // 请求 /v0/subjects/0/subjects 必然 400，跳过以免浪费时间与请求配额
            val animesWithBangumiId = animeDao.getAllAnimesList().filter { (it.bangumiId ?: 0) > 0 }
            if (animesWithBangumiId.isEmpty()) return

            // 增量队列：库内未缓存的 + 递归发现的库外前传
            val queue = ArrayDeque<Int>()
            val enqueued = mutableSetOf<Int>()
            for (anime in animesWithBangumiId) {
                val id = anime.bangumiId ?: continue
                if (!relationsCache.containsKey(id) && !failedSubjectIds.contains(id) && enqueued.add(id)) {
                    queue.addLast(id)
                }
            }
            var fetched = 0
            while (queue.isNotEmpty() && enqueued.size <= relationsFetchLimit) {
                val subjectId = queue.removeFirst()
                if (relationsCache.containsKey(subjectId) || failedSubjectIds.contains(subjectId)) continue
                try {
                    val relations = RetrofitClient.bangumiApi.getSubjectRelations(subjectId)
                    relationsCache[subjectId] = relations.filter { it.isAnime }.map {
                        BangumiSeriesResolver.SubjectRelation(
                            subjectId = it.id,
                            title = it.displayName,
                            relation = it.relation ?: "",
                            isAnime = it.isAnime
                        )
                    }
                    fetched++
                    // 递归：库外前传入队（计算链头深度需要完整前传链）
                    relationsCache[subjectId].orEmpty()
                        .filter { it.relation == "前传" }
                        .forEach { pre ->
                            if (!relationsCache.containsKey(pre.subjectId) && !failedSubjectIds.contains(pre.subjectId) && enqueued.add(pre.subjectId)) {
                                queue.addLast(pre.subjectId)
                            }
                        }
                } catch (e: Exception) {
                    failedSubjectIds.add(subjectId)
                    AppLogManager.w(TAG, "拉取条目关联关系失败: subjectId=$subjectId", e)
                }
                delay(RELATIONS_FETCH_INTERVAL_MS)
            }

            if (fetched > 0 || bangumiSeriesCache.isEmpty()) {
                val resolved = withContext(Dispatchers.Default) {
                    BangumiSeriesResolver.resolve(animesWithBangumiId, relationsCache.toMap())
                }
                bangumiSeriesCache = resolved
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "syncSeriesRelations: fetched=$fetched, cacheSize=${relationsCache.size}, resolved=${resolved.size}")
                }
                recomputeAndPersistSeriesKeys(triggerRelationsSync = false)
            }
        } finally {
            seriesRelationsMutex.unlock()
        }
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
                        AppLogManager.e(TAG, "Failed to clear missing cover: animeId=$animeId", e)
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
                        // 合批写库：等待 flush worker 统一写入，避免逐张封面触发列表重算
                        pendingCoverUrlUpdates[animeId] = localPath
                        ensureCoverBatcherStarted()
                        if (BuildConfig.DEBUG) Log.d(TAG, "Cover localized async: animeId=$animeId")
                    }
                } catch (e: Exception) {
                    AppLogManager.w(TAG, "封面下载失败: animeId=$animeId", e)
                }
            }
        }
    }

    /**
     * 启动封面本地化写库合批 worker：将 [COVER_BATCH_FLUSH_MS] 窗口内完成的多次
     * updateCoverUrl 合并为单事务写库，避免每次封面下载完成都触发一次 Room 失效
     * 与主线程列表重算（启动期多张封面并发下载时尤其明显）。
     */
    private fun ensureCoverBatcherStarted() {
        if (!coverBatcherStarted.compareAndSet(false, true)) return
        appScope.launch {
            while (true) {
                if (pendingCoverUrlUpdates.isEmpty()) {
                    delay(COVER_BATCH_FLUSH_MS)
                    continue
                }
                val batch = pendingCoverUrlUpdates.toMap()
                // 按「key+值」精确移除：toMap 与移除之间新入队的更新（同 key 更新值）
                // 不会被误清，留待下一轮 flush
                batch.forEach { (id, url) -> pendingCoverUrlUpdates.remove(id, url) }
                try {
                    val database = AnimeDatabase.getDatabase(context)
                    database.withTransaction {
                        batch.forEach { (id, url) -> animeDao.updateCoverUrl(id, url) }
                    }
                } catch (e: Exception) {
                    AppLogManager.e(TAG, "Cover batch update failed", e)
                }
            }
        }
    }

    override fun syncCoversInBackground(
        animesToSync: List<Anime>,
        notifySuccess: Boolean
    ) {
        appScope.launch {
            val animesWithoutCover = if (animesToSync.isNotEmpty()) {
                animesToSync.reversed()
            } else {
                animeDao.getAnimesWithoutCover().reversed()
            }

            if (animesWithoutCover.isEmpty()) return@launch

            if (BuildConfig.DEBUG) Log.d(TAG, "Background cover sync: ${animesWithoutCover.size} animes to process")

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
                            // 源评分（10 分制）转 5 分制；本地已有评分（含手动打分）时不覆盖
                            rating = anime.rating ?: RatingUtils.sourceScoreToRating(detail?.score ?: bestMatch.score),
                            totalEpisodes = finalTotalEpisodes,
                            currentEpisodes = finalCurrentEpisodes,
                            watchedEpisodes = newWatchedEpisodes,
                            summary = summary,
                            bangumiId = bestMatch.id,
                            airDate = detail?.date ?: anime.airDate,
                            airWeekday = detail?.airWeekday ?: anime.airWeekday,
                            notes = if (extractedNote.isNotEmpty()) extractedNote else anime.notes
                        )

                        // 直接写入 DAO，避免每部番剧触发一次网络同步（updateAnime 会 POST）
                        animeDao.updateAnime(updatedAnime)
                        WebDAVAutoSyncManager.getInstance().notifyDataChanged()
                        downloadCoverAsync(
                            animeId = updatedAnime.id,
                            coverUrl = updatedAnime.coverUrl,
                            bangumiId = updatedAnime.bangumiId,
                            tmdbId = updatedAnime.tmdbId
                        )
                        count++
                        if (BuildConfig.DEBUG) Log.d(TAG, "Background sync: synced ${anime.title}")
                    }

                    delay(800)
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync failed for: ${anime.title}", e)
                }
            }

            if (count > 0) {
                // 标题可能变化，统一重算 seriesKey；并只触发一次防抖同步，避免 N 次网络请求
                reassignSeriesKeys()
                triggerSyncSubscriptionsFromServerDebounced()
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "Background cover sync complete: $count/${animesWithoutCover.size}")

            // 补全结果反馈：失败（网络不可达或无匹配）时全局横幅提示，恢复途径见文案；
            // 全部成功时仅手动触发场景提示，导入/启动自动补全保持静默
            val failedCount = animesWithoutCover.size - count
            if (failedCount > 0) {
                AppLogManager.w(TAG, "番剧信息补全失败 $failedCount/${animesWithoutCover.size} 部（Bangumi 匹配失败或网络不可达）")
                AppContainer.importBanner.value = ImportBannerState(
                    loading = false,
                    text = "$failedCount 部番剧信息补全失败（访问 Bangumi 需代理），恢复网络后重启应用将自动重试",
                    isError = true
                )
            } else if (notifySuccess) {
                AppContainer.importBanner.value = ImportBannerState(
                    loading = false,
                    text = "已补全 ${animesWithoutCover.size} 部番剧信息"
                )
            }
        }
    }

    override fun triggerCoverBackfillIfNeeded() {
        appScope.launch {
            try {
                val settings = AppContainer.getSettingsRepository()
                val last = settings.lastCoverBackfillTime.first()
                // 24 小时节流：网络持续不可达时避免每次启动都发起一轮必败请求
                if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return@launch

                val pending = animeDao.getAnimesWithoutCover()
                if (pending.isEmpty()) return@launch

                settings.setLastCoverBackfillTime(System.currentTimeMillis())
                if (BuildConfig.DEBUG) Log.d(TAG, "Cover backfill on launch: ${pending.size} animes pending")
                syncCoversInBackground()
            } catch (e: Exception) {
                AppLogManager.w(TAG, "启动补全检查失败", e)
            }
        }
    }

    /**
     * 同步订阅状态到后端（仅登录时生效）。
     * - isAdd=true: 调用 POST /subscriptions/add
     * - isAdd=false: 调用 POST /subscriptions/remove（新稳定 ID 与旧本地 id 各删一次，兼容新旧记录）
     * 网络失败不影响本地操作。
     */
    private fun syncSubscriptionToServer(anime: Anime, isAdd: Boolean) {
        appScope.launch {
            try {
                val userAuthManager = com.aiexile.animetrack.di.AppContainer.getUserAuthManager()
                if (!userAuthManager.isLoggedIn.first()) return@launch

                if (isAdd) {
                    // 无 bangumiId 的番剧懒生成稳定远程 ID（首次上传时回存）
                    val synced = ensureRemoteSyncId(anime)
                    // bangumiId<=0 为历史脏数据，不得上传（服务端已拒绝 '0'）
                    val animeId = synced.bangumiId?.takeIf { it > 0 }?.toString() ?: synced.remoteSyncId!!
                    val response = RetrofitClient.userAuthApi.addSubscription(
                        buildSubscribeRequest(synced, animeId)
                    )
                    if (response.success) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Subscription added to server: ${anime.title}")
                        // 服务器行标识迁移：条目绑定有效 bangumiId 后，原先以 remoteSyncId
                        // 为 anime_id 的行成为寄生行（sanitize 清洗/早期上传的产物），
                        // 不删会残留旧标题旧元数据，重启合并时可能被当成新番剧插入（幽灵卡片）。
                        // UUID 全局唯一、remove 幂等，失败不影响主流程，下次同步会重试。
                        val oldSyncId = synced.remoteSyncId
                        if (oldSyncId != null && oldSyncId != animeId) {
                            runCatching {
                                RetrofitClient.userAuthApi.removeSubscription(
                                    RemoveSubscribeRequest(animeId = oldSyncId)
                                )
                            }.onFailure {
                                Log.w(TAG, "Remove legacy remoteSyncId row failed (non-fatal): ${anime.title}", it)
                            }
                        }
                    } else {
                        Log.w(TAG, "Subscription add failed: ${anime.title}, message=${response.message}")
                    }
                } else {
                    // 新稳定 ID（bangumiId 或 remoteSyncId）
                    val currentId = anime.bangumiId?.toString() ?: anime.remoteSyncId
                    if (currentId != null) {
                        RetrofitClient.userAuthApi.removeSubscription(
                            RemoveSubscribeRequest(animeId = currentId)
                        )
                    }
                    // 旧本地 id（旧版本客户端上传的记录），与上面不同才删
                    val legacyId = anime.id.toString()
                    if (currentId != legacyId) {
                        RetrofitClient.userAuthApi.removeSubscription(
                            RemoveSubscribeRequest(animeId = legacyId)
                        )
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Subscription removed from server: ${anime.title}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync subscription failed (non-fatal): ${anime.title}", e)
            }
        }
    }

    /**
     * 确保番剧拥有跨设备稳定的远程同步 ID。
     * bangumiId 有效（>0）的番剧天然稳定，直接返回；其余若无 remoteSyncId 则生成 UUID 并回存。
     * 懒生成策略：存量数据不批量回填，首次上传时逐条补全。
     */
    private suspend fun ensureRemoteSyncId(anime: Anime): Anime {
        // bangumiId<=0 为历史脏数据，不视为有效标识，仍需生成 remoteSyncId
        if ((anime.bangumiId ?: 0) > 0 || anime.remoteSyncId != null) return anime
        val syncId = java.util.UUID.randomUUID().toString()
        animeDao.updateRemoteSyncId(anime.id, syncId)
        return anime.copy(remoteSyncId = syncId)
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

    /** 构建上传到后端的订阅请求体（统一逻辑，避免多处重复） */
    private fun buildSubscribeRequest(anime: Anime, animeId: String): SubscribeRequest {
        return SubscribeRequest(
            animeId = animeId,
            animeTitle = anime.title,
            animeImage = resolveAnimeImageForServer(anime),
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
            finishDate = anime.finishDate?.let { formatDate(it) },
            // 附带旧版本客户端使用的本地 id，服务端 upsert 后据此清理存量旧记录
            legacyAnimeId = anime.id.toString()
        )
    }

    /**
     * 判断本地番剧与后端订阅是否一致（一致则无需重复上传）。
     * 仅比对会上传到后端的字段，字符串统一按「空视为 null」归一化。
     */
    private fun isRemoteInSync(anime: Anime, remote: com.aiexile.animetrack.data.network.Subscription): Boolean {
        fun String?.norm() = this?.takeIf { it.isNotBlank() }
        return anime.title == remote.animeTitle
            && anime.status.toApiString() == remote.status
            && anime.watchedEpisodes == (remote.watchedEpisodes ?: 0)
            && anime.currentEpisodes == (remote.currentEpisodes ?: 0)
            && anime.totalEpisodes == (remote.totalEpisodes ?: 0)
            && anime.rating == remote.rating
            && anime.notes.norm() == remote.notes.norm()
            && (!anime.isFinished) == remote.isAiring
            && anime.airWeekday == remote.weekday
            && anime.airDate.norm() == remote.airDate.norm()
            && anime.startDate?.let { formatDate(it) } == remote.startDate.norm()
            && anime.finishDate?.let { formatDate(it) } == remote.finishDate.norm()
    }

    /**
     * 清洗历史脏数据：B 站导入经服务端同步曾产生 animeId='0'（旧版客户端上传，
     * 服务端旧校验拦不住字符串 '0'），下行解析后落地为本地 bangumiId=0。
     * 0 不是有效 Bangumi ID，会导致关系拉取恒 400、系列堆叠异常，
     * 且上行会把 '0' 原样传回服务端，形成自我维持的脏数据循环。
     *
     * 处理：置空 bangumiId 并改用稳定 remoteSyncId → 以新 ID 重新上传 →
     * 删除服务端 '0' 旧行（唯一索引下同用户仅一行，删一次即够，幂等）。
     * 先写库后上传：上传失败时本地已干净，仅暂时缺云端记录，
     * 后续单条同步路径（ensureRemoteSyncId 已有 remoteSyncId）会补传。
     */
    private suspend fun sanitizeDirtyBangumiIds() {
        val dirty = animeDao.getAllAnimesList().filter { it.bangumiId != null && it.bangumiId <= 0 }
        if (dirty.isEmpty()) return
        AppLogManager.w(TAG, "检测到 ${dirty.size} 条 bangumiId<=0 历史脏数据，开始清洗")
        var removedServerZeroRow = false
        for (anime in dirty) {
            try {
                // 1. 本地清洗：置空 bangumiId，改用稳定远程 ID（已生成过则复用）
                val syncId = anime.remoteSyncId ?: java.util.UUID.randomUUID().toString()
                animeDao.clearDirtyBangumiId(anime.id, syncId)
                val cleaned = anime.copy(bangumiId = null, remoteSyncId = syncId)
                // 2. 以正确 ID 重新上传（服务端 upsert，字段为本地当前值，覆盖无害）
                RetrofitClient.userAuthApi.addSubscription(buildSubscribeRequest(cleaned, syncId))
                // 3. 删除服务端 '0' 旧行，切断脏数据循环（失败不影响本地清洗结果）
                if (!removedServerZeroRow) {
                    runCatching {
                        RetrofitClient.userAuthApi.removeSubscription(RemoveSubscribeRequest(animeId = "0"))
                    }.onSuccess { removedServerZeroRow = true }
                }
                AppLogManager.i(TAG, "脏数据清洗完成: ${anime.title}")
            } catch (e: Exception) {
                AppLogManager.w(TAG, "脏数据清洗失败（不影响本地，后续单条同步会补传）: ${anime.title}", e)
            }
        }
    }

    override suspend fun syncSubscriptionsFromServer() {
        if (BuildConfig.DEBUG) Log.d(TAG, "syncSubscriptionsFromServer: start")
        val userAuthManager = com.aiexile.animetrack.di.AppContainer.getUserAuthManager()
        val loggedIn = userAuthManager.isLoggedIn.first()
        if (!loggedIn) {
            Log.w(TAG, "syncSubscriptionsFromServer: skipped, user not logged in")
            return
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "syncSubscriptionsFromServer: user logged in, proceeding")

        try {
            // ===== 第零步：清洗历史脏数据（bangumiId<=0），并修正服务端对应记录 =====
            sanitizeDirtyBangumiIds()

            // ===== 第一步：先拉取后端订阅列表，用于上传前的差异比对 =====
            val response = RetrofitClient.userAuthApi.getSubscriptions()
            val remoteList = response.subscriptions
            // 以 animeId 建索引，便于本地逐条比对
            val remoteMap = remoteList?.associateBy { it.animeId } ?: emptyMap()

            // ===== 第二步：仅上传「远程缺失」或「字段不一致」的本地番剧（客户端 Diff） =====
            val localAnimes = animeDao.getAllAnimesList()
            if (BuildConfig.DEBUG) Log.d(TAG, "syncSubscriptionsFromServer: local animes count=${localAnimes.size}, remote count=${remoteMap.size}")
            var uploadedCount = 0
            var skippedCount = 0
            for (anime in localAnimes) {
                // bangumiId<=0 为历史脏数据，跳过上传（sanitizeDirtyBangumiIds 负责清洗后重传）
                val animeId = anime.bangumiId?.takeIf { it > 0 }?.toString() ?: continue
                // 远程已存在且字段完全一致 → 跳过上传
                val remote = remoteMap[animeId]
                if (remote != null && isRemoteInSync(anime, remote)) {
                    skippedCount++
                    continue
                }
                try {
                    RetrofitClient.userAuthApi.addSubscription(buildSubscribeRequest(anime, animeId))
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
            if (BuildConfig.DEBUG) Log.d(TAG, "syncSubscriptionsFromServer: uploaded=$uploadedCount, skipped(in-sync)=$skippedCount, total=${localAnimes.size}")

            // ===== 第三步：将后端订阅列表合并到本地（复用第一步已拉取的数据） =====
            if (!response.success || remoteList == null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Sync done: uploaded=$uploadedCount, remote list unavailable")
                return
            }

            var mergedCount = 0
            val pendingInsert = mutableListOf<Anime>()
            for (remote in remoteList) {
                // 历史 '0' 脏行（旧版 B 站导入上传）：不入库，顺手清理服务器行（幂等）。
                // 不清理会残留旧标题/无元数据记录，标题兜底匹配失败时被当成新番剧插入（幽灵卡片）
                if (remote.animeId == "0") {
                    runCatching {
                        RetrofitClient.userAuthApi.removeSubscription(RemoveSubscribeRequest(animeId = "0"))
                    }
                    continue
                }
                // animeId 格式分流：纯数字（>0）→ Bangumi 条目 ID；UUID → 稳定远程同步 ID。
                val bangumiId = remote.animeId.toIntOrNull()?.takeIf { it > 0 }
                val remoteSyncId = if (bangumiId == null) remote.animeId else null
                val existing = findExistingAnimeByRemote(bangumiId, remoteSyncId, remote.animeTitle)
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
                        remoteSyncId = remoteSyncId,
                        airWeekday = remote.weekday,
                        isFinished = !remote.isAiring, // isAiring: true=连载中, false=已完结
                        currentEpisodes = remote.currentEpisodes ?: 0
                    )
                    pendingInsert.add(metaAnime)
                } else {
                    // 本地已有 → 跳过，保留本地完整数据。
                    // 额外清理寄生行：本地条目已绑定有效 bangumiId，但远程仍存在以它的
                    // remoteSyncId 为 anime_id 的旧行（sanitize 清洗/早期上传产物，且
                    // 未能被 add 路径顺带删除），留着会在换绑设备外的场景复活幽灵卡片
                    if (remoteSyncId != null && remoteSyncId == existing.remoteSyncId
                        && (existing.bangumiId ?: 0) > 0
                        && remote.animeId != existing.bangumiId.toString()
                    ) {
                        runCatching {
                            RetrofitClient.userAuthApi.removeSubscription(
                                RemoveSubscribeRequest(animeId = remote.animeId)
                            )
                        }
                    }
                    mergedCount++
                }
            }
            // 批量插入（单事务）：避免逐条插入触发 N 次 Room 失效与列表重算
            if (pendingInsert.isNotEmpty()) {
                animeDao.insertAnimes(pendingInsert)
            }
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Sync subscriptions: uploaded=$uploadedCount, " +
                    "merged(skipped)=$mergedCount, inserted=${pendingInsert.size}, " +
                    "remote total=${remoteList.size}"
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
                if (BuildConfig.DEBUG) Log.d(TAG, "triggerSyncSubscriptionsFromServerDebounced: sync already in progress, skipped")
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
                if (BuildConfig.DEBUG) Log.d(TAG, "triggerPullSubscriptionsFromServer: sync already in progress, skipped")
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
     * 按后端订阅记录查找本地已有番剧（合并前的存在性判断），三级匹配：
     * 1. bangumiId（animeId 为纯数字时，可能是 Bangumi 条目 ID 或旧版本客户端的本地 id）
     * 2. remoteSyncId（animeId 为 UUID 时，稳定远程 ID 直接命中）
     * 3. 标题兜底（兼容旧版本客户端用本地自增 id 上传的记录，防止重复插入）
     */
    private suspend fun findExistingAnimeByRemote(
        bangumiId: Int?,
        remoteSyncId: String?,
        title: String
    ): Anime? {
        if (bangumiId != null) {
            animeDao.getAnimeByBangumiId(bangumiId)?.let { return it }
        }
        if (remoteSyncId != null) {
            animeDao.getAnimeByRemoteSyncId(remoteSyncId)?.let { return it }
        }
        return animeDao.getAnimeByTitle(title)
    }

    /**
     * 只从后端拉取订阅列表合并到本地（不上传本地数据）。
     * - 本地已有的番剧跳过（保留本地完整数据）
     * - 本地没有的番剧插入元数据（完整信息留空待用户点击详情时补全）
     * - 未登录静默跳过
     */
    private suspend fun pullSubscriptionsFromServer() {
        if (BuildConfig.DEBUG) Log.d(TAG, "pullSubscriptionsFromServer: start")
        val userAuthManager = com.aiexile.animetrack.di.AppContainer.getUserAuthManager()
        val loggedIn = userAuthManager.isLoggedIn.first()
        if (!loggedIn) {
            if (BuildConfig.DEBUG) Log.d(TAG, "pullSubscriptionsFromServer: skipped, user not logged in")
            return
        }

        try {
            val response = RetrofitClient.userAuthApi.getSubscriptions()
            if (!response.success || response.subscriptions == null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "pullSubscriptionsFromServer: remote list unavailable")
                return
            }

            var mergedCount = 0
            val pendingInsert = mutableListOf<Anime>()
            for (remote in response.subscriptions) {
                // 历史 '0' 脏行（旧版 B 站导入上传）：不入库，顺手清理服务器行（幂等），
                // 防止旧标题/无元数据记录在标题兜底匹配失败时被当成新番剧插入（幽灵卡片）
                if (remote.animeId == "0") {
                    runCatching {
                        RetrofitClient.userAuthApi.removeSubscription(RemoveSubscribeRequest(animeId = "0"))
                    }
                    continue
                }
                // animeId 格式分流：纯数字（>0）→ Bangumi 条目 ID；UUID → 稳定远程同步 ID。
                val bangumiId = remote.animeId.toIntOrNull()?.takeIf { it > 0 }
                val remoteSyncId = if (bangumiId == null) remote.animeId else null
                val existing = findExistingAnimeByRemote(bangumiId, remoteSyncId, remote.animeTitle)
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
                        remoteSyncId = remoteSyncId,
                        airWeekday = remote.weekday,
                        isFinished = !remote.isAiring,
                        currentEpisodes = remote.currentEpisodes ?: 0
                    )
                    pendingInsert.add(metaAnime)
                } else {
                    // 本地已有 → 跳过，保留本地完整数据。
                    // 额外清理寄生行：本地条目已绑定有效 bangumiId，但远程仍存在以它的
                    // remoteSyncId 为 anime_id 的旧行，留着会在换绑场景复活幽灵卡片
                    if (remoteSyncId != null && remoteSyncId == existing.remoteSyncId
                        && (existing.bangumiId ?: 0) > 0
                        && remote.animeId != existing.bangumiId.toString()
                    ) {
                        runCatching {
                            RetrofitClient.userAuthApi.removeSubscription(
                                RemoveSubscribeRequest(animeId = remote.animeId)
                            )
                        }
                    }
                    mergedCount++
                }
            }
            // 批量插入（单事务）：避免逐条插入触发 N 次 Room 失效与列表重算
            if (pendingInsert.isNotEmpty()) {
                animeDao.insertAnimes(pendingInsert)
            }
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "pullSubscriptionsFromServer: merged(skipped)=$mergedCount, " +
                    "inserted=${pendingInsert.size}, remote total=${response.subscriptions.size}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "pullSubscriptionsFromServer failed (non-fatal)", e)
        }
    }
}
