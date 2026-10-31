package com.aiexile.animetrack.data.sync

import android.util.Log
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.AnimeDao
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.RemoveSubscribeRequest
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.SubscribeRequest
import com.aiexile.animetrack.data.network.Subscription
import com.aiexile.animetrack.data.network.SubscriptionApiV2
import com.aiexile.animetrack.data.network.SubscriptionV2DeleteRequest
import com.aiexile.animetrack.data.network.SubscriptionV2Identity
import com.aiexile.animetrack.data.network.SubscriptionV2MergedItem
import com.aiexile.animetrack.data.network.SubscriptionV2UpsertRequest
import com.aiexile.animetrack.data.network.toApiString
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.PendingDeletion
import com.aiexile.animetrack.model.PendingDeletionKeyType
import com.aiexile.animetrack.util.formatDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 服务端订阅同步编排（单条上传/删除、全量同步、仅拉取、待删除补偿）。
 *
 * 从 [com.aiexile.animetrack.data.AnimeRepositoryImpl] 抽出，Repository 只做委托。
 * 行为与抽取前逐行等价：判定顺序、日志内容、限流/防抖参数、互斥语义均不变。
 */
class SubscriptionSyncService(
    private val animeDao: AnimeDao,
    private val repository: AnimeRepository,
    private val appScope: CoroutineScope,
    /** 本地封面 → 后端可访问的公开 URL；由 Repository 注入，保持封面策略单一来源 */
    private val resolveRemoteCoverUrl: (String?) -> String?
) {

    /** 订阅合并器：sync / pull 两个入口共享的合并决策核心 */
    private val subscriptionMerger by lazy {
        SubscriptionMerger(animeDao, repository, TAG)
    }

    /** 服务端墓碑 → 本地删除；规则独立可单测，不混进同步编排 */
    private val subscriptionTombstoneApplier by lazy {
        SubscriptionTombstoneApplier(animeDao)
    }

    /**
     * 同步订阅状态到后端（仅登录时生效）。
     * - isAdd=true: 调用 POST /subscriptions/add
     * - isAdd=false: 调用 POST /subscriptions/remove（新稳定 ID 与旧本地 id 各删一次，兼容新旧记录）
     * 网络失败不影响本地操作。
     */
    internal fun syncSubscriptionToServer(
        anime: Anime,
        isAdd: Boolean,
        oldAnime: Anime? = null,
        explicitAdd: Boolean = false
    ) {
        appScope.launch {
            try {
                val userAuthManager = AppContainer.getUserAuthManager()
                if (!userAuthManager.isLoggedIn.first()) return@launch
                val ownerKey = currentOwnerKey()

                if (isAdd) {
                    // 无 bangumiId 的番剧懒生成稳定远程 ID（首次上传时回存）
                    val synced = ensureRemoteSyncId(anime)
                    // bangumiId<=0 为历史脏数据，不得上传（服务端已拒绝 '0'）
                    val animeId = synced.bangumiId?.takeIf { it > 0 }?.toString()
                        ?: synced.remoteSyncId!!
                    val result = uploadSubscription(
                        anime = synced,
                        animeId = animeId,
                        ownerKey = ownerKey,
                        oldAnime = oldAnime,
                        explicitAdd = explicitAdd
                    )
                    if (result.success) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Subscription upserted: ${anime.title}, v2=${result.usedV2}")
                        }
                    } else {
                        AppLogManager.w(TAG, "Subscription add failed: ${anime.title}, message=${result.message}")
                    }
                } else {
                    val stableIds = SubscriptionIdentity.keysForDelete(anime)
                    val legacyKeys = SubscriptionIdentity.legacyKeysOf(anime)
                    // 清除墓碑只按稳定键：本地自增 id 是数字，可能与其他番的
                    // Bangumi ID 撞车，按值清理会误删别的番墓碑（它也不再写入墓碑）
                    val clearableKeys = (stableIds +
                        listOfNotNull(anime.serverSubscriptionId)).distinct()

                    // v2：一次 delete 会顺着 alias 删除 canonical 并登记墓碑；
                    // 旧服务端：SubscriptionApiV2 内部按 fallbackAnimeIds 逐个 remove。
                    val deleteResult = SubscriptionApiV2.delete(
                        request = SubscriptionV2DeleteRequest(
                            subscriptionId = anime.serverSubscriptionId,
                            identity = SubscriptionIdentity.identityOf(anime),
                            legacyKeys = legacyKeys,
                            supersededKeys = SubscriptionIdentity.supersededKeysOf(anime)
                        ),
                        fallbackAnimeIds = stableIds + legacyKeys
                    )
                    if (deleteResult.success && ownerKey != null) {
                        clearableKeys.forEach { animeDao.clearPendingDeletion(ownerKey, it) }
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "Subscription removed from server: ${anime.title}, " +
                                "success=${deleteResult.success}, removed=${deleteResult.removed}"
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogManager.w(TAG, "Sync subscription failed (non-fatal): ${anime.title}", e)
            }
        }
    }

    /**
     * 当前登录账号的墓碑归属标识：服务器 user id 优先，回退 username；未登录返回 null。
     * bangumiId 行键跨账号共享，墓碑必须绑定账号，防止换账号后补偿重试误删新账号的行。
     */
    private suspend fun currentOwnerKey(): String? {
        val userAuthManager = AppContainer.getUserAuthManager()
        if (!userAuthManager.isLoggedIn.first()) return null
        return userAuthManager.userId.first()?.let { "user:$it" }
            ?: userAuthManager.username.first()?.let { "username:$it" }
    }

    /**
     * 将番剧的全部服务端稳定行键写入待删除墓碑（仅登录时记录）。
     *
     * 只记录能安全寻址服务端的键：subscriptionId / bangumiId / remoteSyncId。
     * 本地自增 id 绝不写入——它不是服务端身份，按数字发送会被当作 Bangumi ID，
     * 可能删掉另一部番；旧服务端的本地 id 行仍由当次删除的 v1 fallback 清理。
     * 同一部番的键共享 groupKey，补偿时任一键成功即整组结束。
     */
    internal suspend fun recordPendingDeletions(anime: Anime) {
        val ownerKey = currentOwnerKey() ?: return
        val groupKey = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val keys = buildList {
            anime.serverSubscriptionId?.takeIf { it.isNotBlank() }
                ?.let { add(it to PendingDeletionKeyType.SERVER) }
            anime.bangumiId?.takeIf { it > 0 }
                ?.let { add(it.toString() to PendingDeletionKeyType.BANGUMI) }
            anime.remoteSyncId?.takeIf { it.isNotBlank() }
                ?.let { add(it to PendingDeletionKeyType.REMOTE_SYNC) }
        }.distinctBy { it.first }
        keys.forEach { (id, type) ->
            animeDao.insertPendingDeletion(
                PendingDeletion(
                    animeId = id,
                    keyType = type,
                    groupKey = groupKey,
                    ownerKey = ownerKey,
                    createdAt = now
                )
            )
        }
    }

    /** 合并防线只认稳定键；LEGACY_LOCAL 与远端 animeId 撞车会误拦其他番剧 */
    private suspend fun pendingDeletionStableKeys(ownerKey: String): Set<String> =
        animeDao.getPendingDeletionStableKeys(ownerKey).toSet()

    /**
     * 补偿重试待删除墓碑：删除番剧时网络失败/进程被杀遗留的服务端行，
     * 在拉取订阅列表前先按墓碑重删，防止残留行合并时被重新插回本地（复活）。
     *
     * 同一 groupKey 的键按 SERVER → BANGUMI → REMOTE_SYNC 顺序尝试，
     * 任一成功即清理整组，绝不在 canonical 已删除后继续按其他键删除
     * （否则本地 id 之类的弱键可能命中并误删另一部番）。
     * LEGACY_LOCAL 无法在 v2 安全寻址，跳过并交由 TTL 清理。
     *
     * 顺手清理：其他账号遗留的墓碑（换账号后无法安全执行，直接丢弃）、
     * 超过 TTL 的过期墓碑（长期补偿失败，放弃以防永久阻断该行键的合并）。
     * 单条失败保留墓碑，下次同步再试，不阻塞其余条目。
     */
    private suspend fun retryPendingDeletions() {
        val ownerKey = currentOwnerKey() ?: return
        animeDao.clearPendingDeletionsForOtherOwners(ownerKey)
        animeDao.clearExpiredPendingDeletions(System.currentTimeMillis() - PENDING_DELETION_TTL_MS)
        val pending = animeDao.getPendingDeletions(ownerKey)
        if (pending.isEmpty()) return
        var successGroups = 0
        var consecutiveFailures = 0
        for ((groupKey, rows) in pending.groupBy { it.groupKey }) {
            var attempted = 0
            var hadError = false
            var removedAny = false
            for (row in rows.sortedBy { it.keyType.priority }) {
                if (row.keyType == PendingDeletionKeyType.LEGACY_LOCAL) continue
                attempted++
                try {
                    val result = SubscriptionApiV2.deleteByKey(row.animeId, row.keyType)
                    if (result.removed) {
                        removedAny = true
                        break
                    }
                    if (!result.success) hadError = true
                    consecutiveFailures = 0
                } catch (e: Exception) {
                    hadError = true
                    AppLogManager.w(
                        TAG,
                        "待删除补偿重试失败（保留，下次同步再试）: type=${row.keyType}, animeId=${row.animeId}",
                        e
                    )
                    // 连续失败大概率是断网/服务端异常，短路退出避免逐条等待超时拖慢同步；
                    // 墓碑全部保留，下次同步从头再试
                    if (++consecutiveFailures >= 2) {
                        AppLogManager.w(
                            TAG,
                            "补偿重试连续失败 ${consecutiveFailures} 次，本次跳过剩余墓碑"
                        )
                        return
                    }
                }
            }
            // 组内任一稳定键删除了行 → canonical 已不存在；
            // 组内所有键请求成功但服务端均无对应行（removed=false）→ 订阅早已不存在，
            // 删除同样已满足。只有发生错误才保留墓碑，下次同步重试。
            if (removedAny || (attempted > 0 && !hadError)) {
                animeDao.clearPendingDeletionGroup(ownerKey, groupKey)
                successGroups++
            }
        }
        if (successGroups > 0) {
            AppLogManager.i(TAG, "补偿处理服务端订阅 ${successGroups} 组（共 ${pending.size} 个墓碑键）")
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
     * 解析番剧封面 URL 为后端可访问的公开 URL。
     * 优先使用 remoteCoverUrl（添加时已保存），兼容旧数据回退到 coverUrl。
     */
    private fun resolveAnimeImageForServer(anime: Anime): String? {
        // 优先使用添加时保存的远程 URL
        anime.remoteCoverUrl?.let { return it }
        // 兼容旧数据（remoteCoverUrl 为 null 时从 coverUrl 计算）
        return resolveRemoteCoverUrl(anime.coverUrl)
    }

    /** 构建上传到后端的订阅请求体（统一逻辑，避免多处重复） */
    private fun buildSubscribeRequest(
        anime: Anime,
        animeId: String,
        supersededAnimeIds: List<String>? = null
    ): SubscribeRequest {
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
            legacyAnimeId = anime.id.toString(),
            // 行键迁移时被取代的旧行键（服务端删旧行 + 记录迁移映射）
            supersededAnimeIds = supersededAnimeIds
        )
    }

    /** 构建订阅 v2 请求体（身份 / 旧键 / 客户端修改时间 / 数据） */
    private fun buildV2UpsertRequest(
        anime: Anime,
        animeId: String,
        oldAnime: Anime?,
        explicitAdd: Boolean,
        remote: Subscription? = null
    ): SubscriptionV2UpsertRequest {
        val numericId = animeId.toIntOrNull()?.takeIf { it > 0 }
        val identity = numericId
            ?.let { SubscriptionV2Identity(bangumiId = it) }
            ?: SubscriptionV2Identity(remoteSyncId = animeId)
        val supersededKeys = SubscriptionIdentity
            .supersededKeysOf(anime, oldAnime)
            .takeIf { it.isNotEmpty() }
        return SubscriptionV2UpsertRequest(
            subscriptionId = anime.serverSubscriptionId,
            intent = if (explicitAdd) "add" else "sync",
            identity = identity,
            legacyKeys = SubscriptionIdentity.legacyKeysOf(anime),
            supersededKeys = supersededKeys,
            clientUpdatedAt = if (remote != null) {
                SubscriptionIdentity.clientUpdatedAtAgainstRemote(anime, remote)
            } else {
                SubscriptionIdentity.clientUpdatedAt(anime, oldAnime)
            },
            progressReset = SubscriptionIdentity.isProgressReset(anime, oldAnime),
            // 仅用于服务端诊断 revision 漂移；LWW 仍由 clientUpdatedAt 决定
            baseRevision = remote?.revision,
            data = SubscriptionIdentity.dataOf(anime, resolveAnimeImageForServer(anime))
        )
    }

    /**
     * 单条上传（v2 优先，旧服务端自动回退 v1）。
     * v2 成功时服务端已处理 alias/旧行合并，不再额外调 v1 remove 旧 remoteSyncId
     * （否则会顺着 alias 把刚写入的 canonical 订阅删掉）。
     */
    private suspend fun uploadSubscription(
        anime: Anime,
        animeId: String,
        ownerKey: String?,
        oldAnime: Anime? = null,
        explicitAdd: Boolean = false,
        remote: Subscription? = null
    ): SubscriptionApiV2.UpsertResult {
        val superseded = SubscriptionIdentity.supersededKeysOf(anime, oldAnime)
            .map { it.value }
            .takeIf { it.isNotEmpty() }
        val fallback = buildSubscribeRequest(anime, animeId, superseded)
        val result = SubscriptionApiV2.upsert(
            request = buildV2UpsertRequest(anime, animeId, oldAnime, explicitAdd, remote),
            fallback = fallback
        )

        if (result.success) {
            // 上传成功说明这组行键已归属当前条目：之前删除失败留下的墓碑已无意义。
            // 按「组」清理而不是逐个按值清：组里可能包含旧 bangumi/remote alias，
            // 逐个按值清会因数字键与其他番撞车而误删别人的墓碑。
            // 注意本地自增 id 不参与查找：它不是稳定身份，可能与其他番的 Bangumi ID 同值。
            if (ownerKey != null) {
                val lookupKeys = buildSet {
                    add(animeId)
                    addAll(SubscriptionIdentity.keysForDelete(anime))
                    anime.serverSubscriptionId?.let { add(it) }
                    result.subscriptionId?.let { add(it) }
                    result.canonical?.supersededAnimeIds?.forEach { add(it) }
                }
                animeDao.getPendingDeletionGroups(ownerKey, lookupKeys.toList())
                    .forEach { animeDao.clearPendingDeletionGroup(ownerKey, it) }
            }
            val canonicalId = result.subscriptionId
            // ignored 表示服务端墓碑判定这是过期重传：返回的是已删除订阅的 ID，
            // 不能回写到本地，否则本地会永远绑在一条不存在的服务端订阅上。
            if (!result.ignored && canonicalId != null && canonicalId != anime.serverSubscriptionId) {
                animeDao.updateServerSubscriptionId(anime.id, canonicalId)
            }
            // 服务端发生合并后，本地指向被合并行的副本也应清理，否则会一直显示重复卡
            if (result.usedV2 && result.merged.isNotEmpty()) {
                deleteLocalMergedDuplicates(result.merged, anime.id)
            }

            // 仅旧服务端走行键清理；新服务端由 alias + merge 处理
            if (!result.usedV2) {
                val oldSyncId = anime.remoteSyncId
                if (oldSyncId != null && oldSyncId != animeId) {
                    runCatching {
                        RetrofitClient.userAuthApi.removeSubscription(
                            RemoveSubscribeRequest(animeId = oldSyncId)
                        )
                    }.onSuccess { removed ->
                        if (removed.success && ownerKey != null) {
                            animeDao.clearPendingDeletion(ownerKey, oldSyncId)
                        }
                    }.onFailure {
                        AppLogManager.w(TAG, "Remove legacy remoteSyncId row failed (non-fatal): ${anime.title}", it)
                    }
                }
            }
        }
        return result
    }

    /**
     * 判断本地番剧与后端订阅是否一致（一致则无需重复上传）。
     * 仅比对会上传到后端的字段，字符串统一按「空视为 null」归一化。
     */
    private fun isRemoteInSync(anime: Anime, remote: Subscription): Boolean {
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
     * 服务端合并后清理本地重复卡片。
     * 只按 mergedSubscriptionId / remote_sync 别名删：数字 bangumi 别名可能来自
     * 历史污染，拿来删本地卡会误删另一部番。
     */
    private suspend fun deleteLocalMergedDuplicates(
        merged: List<SubscriptionV2MergedItem>,
        keepAnimeId: Int
    ) {
        if (merged.isEmpty()) return
        val local = animeDao.getAllAnimesList()
        val scheduledIds = mutableSetOf<Int>()
        val toDelete = mutableListOf<Anime>()
        for (item in merged) {
            val remoteAliases = item.aliases.orEmpty()
                .filter { it.type == "remote_sync" }
                .map { it.value }
                .toSet()
            for (candidate in local) {
                if (candidate.id == keepAnimeId || candidate.id in scheduledIds) continue
                val matched = candidate.serverSubscriptionId == item.mergedSubscriptionId ||
                    (
                        candidate.serverSubscriptionId == null &&
                            candidate.remoteSyncId != null &&
                            candidate.remoteSyncId in remoteAliases
                        )
                if (!matched) continue
                scheduledIds.add(candidate.id)
                toDelete.add(candidate)
            }
        }
        toDelete.forEach { animeDao.deleteAnime(it) }
        if (toDelete.isNotEmpty()) {
            AppLogManager.i(TAG, "清理服务端合并后的本地重复卡: ${toDelete.size}")
        }
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
        val ownerKey = currentOwnerKey()
        var removedServerZeroRow = false
        for (anime in dirty) {
            try {
                // 1. 本地清洗：置空 bangumiId，改用稳定远程 ID（已生成过则复用）
                val syncId = anime.remoteSyncId ?: java.util.UUID.randomUUID().toString()
                animeDao.clearDirtyBangumiId(anime.id, syncId)
                val cleaned = anime.copy(bangumiId = null, remoteSyncId = syncId)
                // 2. 以正确 ID 重新上传（v2 优先；服务端 upsert，字段为本地当前值）
                uploadSubscription(
                    anime = cleaned,
                    animeId = syncId,
                    ownerKey = ownerKey,
                    explicitAdd = true
                )
                // 3. 删除服务端 '0' 旧行，切断脏数据循环（失败不影响本地清洗结果）
                if (!removedServerZeroRow) {
                    runCatching {
                        SubscriptionApiV2.delete(
                            request = SubscriptionV2DeleteRequest(legacyKeys = listOf("0")),
                            fallbackAnimeIds = listOf("0")
                        )
                    }.onSuccess { removedServerZeroRow = true }
                }
                AppLogManager.i(TAG, "脏数据清洗完成: ${anime.title}")
            } catch (e: Exception) {
                AppLogManager.w(TAG, "脏数据清洗失败（不影响本地，后续单条同步会补传）: ${anime.title}", e)
            }
        }
    }

    /** 拉取列表 + 上传差异 + 合并（登录态下执行；失败静默） */
    suspend fun syncSubscriptionsFromServer() {
        if (BuildConfig.DEBUG) Log.d(TAG, "syncSubscriptionsFromServer: start")
        val userAuthManager = AppContainer.getUserAuthManager()
        val loggedIn = userAuthManager.isLoggedIn.first()
        if (!loggedIn) {
            AppLogManager.w(TAG, "syncSubscriptionsFromServer: skipped, user not logged in")
            return
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "syncSubscriptionsFromServer: user logged in, proceeding")

        try {
            // ===== 第零步：清洗历史脏数据（bangumiId<=0），并修正服务端对应记录 =====
            sanitizeDirtyBangumiIds()

            // ===== 第零点五步：补偿重试待删除墓碑（必须在拉取列表前执行，
            // 否则服务器残留行会先被合并插回本地，导致已删番剧复活） =====
            retryPendingDeletions()

            // ===== 第一步：先拉取后端订阅列表，用于上传前的差异比对 =====
            val listResult = SubscriptionApiV2.fetchList()
            val remoteList = listResult.subscriptions
            // 索引同时覆盖 canonical 键、subscriptionId 与旧行键 alias，
            // 这样本地换了键的条目也能找到远端正身，不会无谓重传。
            val remoteMap = buildMap {
                for (row in remoteList) {
                    put(row.animeId, row)
                    row.subscriptionId?.let { put(it, row) }
                    row.supersededAnimeIds?.forEach { put(it, row) }
                }
            }

            // ===== 第一步半：先消费服务端墓碑，删掉其他设备已删除的本地卡片 =====
            // 必须在拉本地列表/上传前执行：否则幂等重传会把它们再写回去。
            // 本地在删除之后又改过进度/状态的行由 applier 跳过，上传后会正常恢复。
            val tombstoneResult = if (listResult.isV2) {
                subscriptionTombstoneApplier.apply(
                    listResult.tombstones,
                    animeDao.getAllAnimesList()
                )
            } else {
                null
            }
            if (tombstoneResult != null && tombstoneResult.deletedCount > 0) {
                AppLogManager.i(
                    TAG,
                    "应用服务端墓碑: deleted=${tombstoneResult.deletedCount}, " +
                        "skippedNewer=${tombstoneResult.skippedNewerCount}"
                )
            }

            // ===== 第二步：仅上传「远程缺失」或「字段不一致」的本地番剧（客户端 Diff） =====
            val localAnimes = animeDao.getAllAnimesList()
            val ownerKey = currentOwnerKey()
            if (BuildConfig.DEBUG) Log.d(TAG, "syncSubscriptionsFromServer: local animes count=${localAnimes.size}, remote count=${remoteMap.size}")
            var uploadedCount = 0
            var skippedCount = 0
            for (anime in localAnimes) {
                // bangumiId<=0 为历史脏数据，跳过上传（sanitizeDirtyBangumiIds 负责清洗后重传）
                // bangumiId=null（B 站导入清洗产物等）回退 remoteSyncId 上传：
                // 否则其服务端行自首次写入后永不刷新，进度/元数据变化无法上行，
                // 新设备拉到的永远是残缺数据（仅标题、进度为 0）
                val bangumiId = anime.bangumiId
                val animeId = when {
                    (bangumiId ?: 0) > 0 -> bangumiId.toString()
                    bangumiId != null -> null // <=0 脏数据，交由 sanitize 清洗
                    else -> anime.remoteSyncId ?: ensureRemoteSyncId(anime).remoteSyncId
                } ?: continue
                val remote = anime.serverSubscriptionId?.let { remoteMap[it] }
                    ?: remoteMap[animeId]
                    ?: anime.remoteSyncId?.let { remoteMap[it] }
                // 远程已存在且字段完全一致 → 跳过上传
                if (remote != null && isRemoteInSync(anime, remote)) {
                    skippedCount++
                    continue
                }
                try {
                    val result = uploadSubscription(
                        anime = anime,
                        animeId = animeId,
                        ownerKey = ownerKey,
                        remote = remote
                    )
                    if (result.success) {
                        uploadedCount++
                        if (result.ignored) {
                            AppLogManager.i(TAG, "服务端墓碑拦截过期重传: ${anime.title}")
                        }
                    } else {
                        AppLogManager.w(TAG, "syncSubscriptionsFromServer: upload rejected for ${anime.title}, message=${result.message}")
                    }
                } catch (e: retrofit2.HttpException) {
                    val errorBody = try {
                        e.response()?.errorBody()?.string()
                    } catch (_: Exception) { null }
                    AppLogManager.w(TAG, "syncSubscriptionsFromServer: upload failed for ${anime.title}, code=${e.code()}, error=$errorBody", e)
                } catch (e: Exception) {
                    AppLogManager.w(TAG, "syncSubscriptionsFromServer: upload failed for ${anime.title}", e)
                }
                // 限流：每条上传后间隔，避免短时间内大量请求导致后端拒绝
                delay(UPLOAD_THROTTLE_MS)
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "syncSubscriptionsFromServer: uploaded=$uploadedCount, skipped(in-sync)=$skippedCount, total=${localAnimes.size}")

            // ===== 第三步：将后端订阅列表合并到本地（复用第一步已拉取的数据） =====
            // 墓碑防线：仍带墓碑的行键 = 补偿重试也失败的服务器残留行，合并时跳过防复活
            val pendingDeletionIds = ownerKey
                ?.let { pendingDeletionStableKeys(it) }
                ?: emptySet()
            val result = subscriptionMerger.merge(
                remoteList = remoteList,
                localCandidates = localAnimes,
                isV2 = listResult.isV2,
                pendingDeletionIds = pendingDeletionIds
            )
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "Sync subscriptions: uploaded=$uploadedCount, " +
                    "merged(skipped)=${result.mergedCount}, inserted=${result.insertedCount}, " +
                    "remote total=${remoteList.size}"
            )
        } catch (e: Exception) {
            AppLogManager.w(TAG, "Sync subscriptions from server failed (non-fatal)", e)
        }
    }

    fun triggerSyncSubscriptionsFromServer() {
        appScope.launch {
            syncSubscriptionsFromServer()
        }
    }

    fun triggerSyncSubscriptionsFromServerDebounced() {
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

    fun triggerPullSubscriptionsFromServer() {
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
     * 只从后端拉取订阅列表合并到本地（不上传本地数据）。
     * - 本地已有的番剧跳过（保留本地完整数据）
     * - 本地没有的番剧插入元数据（完整信息留空待用户点击详情时补全）
     * - 未登录静默跳过
     */
    private suspend fun pullSubscriptionsFromServer() {
        if (BuildConfig.DEBUG) Log.d(TAG, "pullSubscriptionsFromServer: start")
        val userAuthManager = AppContainer.getUserAuthManager()
        val loggedIn = userAuthManager.isLoggedIn.first()
        if (!loggedIn) {
            if (BuildConfig.DEBUG) Log.d(TAG, "pullSubscriptionsFromServer: skipped, user not logged in")
            return
        }

        try {
            // 补偿重试待删除墓碑（必须在拉取列表前执行，防止服务器残留行合并时复活）
            retryPendingDeletions()
            val listResult = SubscriptionApiV2.fetchList()
            val remoteList = listResult.subscriptions
            // 空列表是合法状态（用户可能删光了）：仍需消费墓碑，不能提前返回。
            if (listResult.isV2) {
                subscriptionTombstoneApplier.apply(
                    listResult.tombstones,
                    animeDao.getAllAnimesList()
                )
            }

            // 换键行兜底识别的候选集（本地记录 + 本批次已插入的）
            val localAnimes = animeDao.getAllAnimesList()
            // 墓碑防线：仍带墓碑的行键 = 补偿重试也失败的服务器残留行，合并时跳过防复活
            val ownerKey = currentOwnerKey()
            val pendingDeletionIds = ownerKey
                ?.let { pendingDeletionStableKeys(it) }
                ?: emptySet()
            val result = subscriptionMerger.merge(
                remoteList = remoteList,
                localCandidates = localAnimes,
                isV2 = listResult.isV2,
                pendingDeletionIds = pendingDeletionIds
            )
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "pullSubscriptionsFromServer: merged(skipped)=${result.mergedCount}, " +
                    "inserted=${result.insertedCount}, remote total=${remoteList.size}"
            )
        } catch (e: Exception) {
            AppLogManager.w(TAG, "pullSubscriptionsFromServer failed (non-fatal)", e)
        }
    }

    companion object {
        private const val TAG = "AnimeTrack"
        /** 订阅同步互斥锁：防止批量同步重入 */
        private val subscriptionSyncMutex = Mutex()
        /** 防抖标志：true 表示已有待执行的同步任务 */
        private val pendingSubscriptionSync = AtomicBoolean(false)
        /** 上传每条间隔，避免触发后端限流 */
        private const val UPLOAD_THROTTLE_MS = 200L
        /** 防抖延迟，批量操作完成后等待此时间再执行同步 */
        private const val SYNC_DEBOUNCE_MS = 3000L
        /** 待删除墓碑 TTL：超期未补偿成功则放弃，避免长期失败永久阻断该行键的合并 */
        private const val PENDING_DELETION_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}
