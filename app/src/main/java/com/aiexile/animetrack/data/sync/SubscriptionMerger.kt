package com.aiexile.animetrack.data.sync

import com.aiexile.animetrack.data.AnimeDao
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.RemoveSubscribeRequest
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.Subscription
import com.aiexile.animetrack.data.network.parseAnimeStatus
import com.aiexile.animetrack.data.network.toApiString
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.util.formatDate
import com.aiexile.animetrack.util.parseDateToTimestamp

/**
 * 订阅合并器：后端订阅列表 → 本地合并的唯一决策核心。
 *
 * 由 [AnimeRepository] 的 syncSubscriptionsFromServer（上传 + 合并）与
 * pullSubscriptionsFromServer（仅拉取合并）两个入口共享，替代原先两段
 * 近似复制的合并循环。行为契约：本类与重构前的合并逻辑逐行为等价
 * （判定顺序、副作用、日志内容均不变），后续任何调整只改这里即可对
 * 两个入口同时生效——这正是本次抽离的目的。
 *
 * 判定规则链（顺序敏感，勿调整顺序）：
 * 1. 墓碑行：本地已删但服务端删除尚未确认成功的行键，跳过插入防复活（由补偿重试善后）
 * 2. '0' 脏行：不入库，顺手清理服务器行（幂等）
 * 3. 三级身份匹配（已落库记录）：bangumiId → remoteSyncId → 标题兜底（仅 v1）
 * 4. 同批次内存去重（未落库记录）：pendingInsert 中三级匹配
 * 5. 换键强匹配：服务端迁移映射命中本地旧副本 → 换键合并（不插入）
 * 6. 进度指纹兜底：老服务器无映射时保守拦截（仅 v1）
 * 7. 寄生行清理：本地已绑定 bangumiId 但远程残留以其 remoteSyncId 为键的旧行 → 删服务端行
 *
 * v2 收紧原则：远端行都有服务端权威身份（subscriptionId / bangumi / remote_sync），
 * 不能再按标题或进度指纹猜测同一部番。标题兜底、数字 superseded 换绑只保留给
 * v1 旧服务端，避免把「同名不同季」或被污染的本地数字 id 合并成一部。
 */
class SubscriptionMerger(
    private val animeDao: AnimeDao,
    private val repository: AnimeRepository,
    private val logTag: String
) {

    data class MergeResult(val mergedCount: Int, val insertedCount: Int)

    /** 本地命中结果；identityMatched=false 表示仅靠标题兜底命中，不可回写服务端权威 ID */
    private data class AnimeMatch(val anime: Anime, val identityMatched: Boolean)

    /**
     * 将后端订阅列表合并到本地。
     *
     * @param remoteList 后端下发的订阅行（任意顺序，内部按 [remoteMergeOrder] 排序）
     * @param localCandidates 本地记录快照（换键兜底识别的候选集，与调用入口各自
     *   已有的本地列表保持同源即可；合并过程中的 DAO 查询仍为实时查询）
     * @param isV2 服务端是否为 v2；v2 下禁用标题兜底与进度指纹兜底
     * @param pendingDeletionIds 待删除墓碑行键集合（本地已删、服务端删除尚未确认
     *   成功的行）：本次同步前的补偿重试仍失败，跳过插入防止已删番剧复活
     */
    suspend fun merge(
        remoteList: List<Subscription>,
        localCandidates: List<Anime>,
        isV2: Boolean,
        pendingDeletionIds: Set<String> = emptySet()
    ): MergeResult {
        var mergedCount = 0
        val pendingInsert = mutableListOf<Anime>()
        for (remote in remoteList.sortedBy { remoteMergeOrder(it) }) {
            // 墓碑防线：该行键的删除补偿重试仍失败（服务器残留行），跳过插入，
            // 待下次同步补偿重试成功后，该行从服务端消失
            if (remote.animeId in pendingDeletionIds) continue
            if (remote.subscriptionId != null && remote.subscriptionId in pendingDeletionIds) continue
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
            // v2 下服务端已提供权威 identity/alias，不再用标题猜测；
            // v1 旧服务端（含旧版居留的本地 id 行）保留标题兜底，但兜底命中不回写权威 ID。
            val allowTitleFallback = !isV2
            val candidates = localCandidates + pendingInsert
            val existing = findExistingAnimeByRemote(
                remote.subscriptionId, bangumiId, remoteSyncId, remote.animeTitle, allowTitleFallback
            ) ?: findPendingAnime(
                pendingInsert, remote.subscriptionId, bangumiId, remoteSyncId,
                remote.animeTitle, allowTitleFallback
            )
            if (existing == null) {
                // 换键行强匹配：服务端迁移映射命中本地旧副本 → 换键合并（不插入）。
                // v2 只认 UUID（remoteSyncId）旧键；数字 superseded 可能是被污染的本地自增 id，
                // 拿来换绑本地 bangumiId 会把另一部番改身份（P0-1 存量数据尚未修复）。
                findTwinBySupersededIds(candidates, remote, includeBangumi = !isV2)?.let { twin ->
                    applyServerKeyMigration(twin, remote)
                    mergedCount++
                    continue
                }
                // 换键行弱信号兜底（老服务器无映射）：v2 禁用，避免同名/进度巧合误拦
                if (!isV2) {
                    findTwinByProgressFingerprint(candidates, remote)?.let { twin ->
                        AppLogManager.i(
                            logTag,
                            "拦截疑似换键行插入：server=[${remote.animeTitle}] → 本地=[${twin.title}]（进度指纹一致）"
                        )
                        mergedCount++
                        continue
                    }
                }
                // 本地没有 → 插入（同步其他设备添加的番剧），完整信息留空待用户点击详情时补全
                pendingInsert.add(buildMetaAnime(remote, bangumiId, remoteSyncId))
            } else {
                val matched = existing.anime
                // 服务端权威 ID 回写：服务端合并后旧行键会变，但 subscriptionId 不变；
                // 后续删除/匹配优先用它，避免旧键漂移。
                // 仅身份键命中才回写：标题兜底命中无法证明是同一部番，写回会把
                // subscriptionId 污染到「同名不同季」的本地条目上。
                if (existing.identityMatched && SubscriptionIdentity.shouldAdoptMigratedKey(matched, remote)) {
                    // 服务端已把这条订阅换绑到 Bangumi（另一台设备补了 Bangumi ID/改名）。
                    // 不采纳新键的话，本机会一直显示旧标题、没有 Bangumi 元数据，
                    // 且下次上传仍以旧键为行键（虽然 alias 能解析，但本地元数据永远不刷新）。
                    applyServerKeyMigration(matched, remote)
                } else if (
                    existing.identityMatched &&
                    remote.subscriptionId != null &&
                    matched.serverSubscriptionId != remote.subscriptionId
                ) {
                    animeDao.updateServerSubscriptionId(matched.id, remote.subscriptionId)
                }
                // 本地已有 → 跳过，保留本地完整数据。
                // 额外清理寄生行：本地条目已绑定有效 bangumiId，但远程仍存在以它的
                // remoteSyncId 为 anime_id 的旧行（sanitize 清洗/早期上传产物，且
                // 未能被 add 路径顺带删除），留着会在换绑设备外的场景复活幽灵卡片
                if (remoteSyncId != null && remoteSyncId == matched.remoteSyncId
                    && (matched.bangumiId ?: 0) > 0
                    && remote.animeId != matched.bangumiId.toString()
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
        return MergeResult(mergedCount, pendingInsert.size)
    }

    /**
     * 按后端订阅记录查找本地已有番剧（合并前的存在性判断），四级匹配：
     * 0. subscriptionId（v2 服务端权威 ID，合并/换键后依然不变）
     * 1. bangumiId（animeId 为纯数字时，可能是 Bangumi 条目 ID 或旧版本客户端的本地 id）
     * 2. remoteSyncId（animeId 为 UUID 时，稳定远程 ID 直接命中）
     * 3. 标题兜底（仅 v1 旧服务端启用，兼容本地 id 上传的旧记录；命中后不回写 subscriptionId）
     *
     * v2 下禁止标题兜底：服务端已提供权威身份，同名不同季/改名的行不能被合并。
     */
    private suspend fun findExistingAnimeByRemote(
        subscriptionId: String?,
        bangumiId: Int?,
        remoteSyncId: String?,
        title: String,
        allowTitleFallback: Boolean
    ): AnimeMatch? {
        if (subscriptionId != null) {
            animeDao.getAnimeByServerSubscriptionId(subscriptionId)
                ?.let { return AnimeMatch(it, identityMatched = true) }
        }
        if (bangumiId != null) {
            animeDao.getAnimeByBangumiId(bangumiId)
                ?.let { return AnimeMatch(it, identityMatched = true) }
        }
        if (remoteSyncId != null) {
            animeDao.getAnimeByRemoteSyncId(remoteSyncId)
                ?.let { return AnimeMatch(it, identityMatched = true) }
        }
        if (!allowTitleFallback) return null
        return animeDao.getAnimeByTitle(title)?.let { AnimeMatch(it, identityMatched = false) }
    }

    /**
     * 远程订阅合并顺序键：数字 animeId（Bangumi 条目 ID）行优先于 UUID 行。
     * 服务端存在同一番剧的新旧行（sanitize/换绑产物，UUID 行通常残缺）时，
     * 完整数据先落地，后续行靠身份键/服务端 alias 或 v1 标题兜底命中已插入记录
     * 而被跳过，避免 0 进度脏记录占位导致新设备"只同步到番剧名称"。
     */
    private fun remoteMergeOrder(remote: Subscription): Int =
        if (remote.animeId.toIntOrNull()?.takeIf { it > 0 } != null) 0 else 1

    /**
     * 在待插入列表中查找同番剧记录（身份匹配规则与 [findExistingAnimeByRemote] 一致，
     * 标题兜底仅 v1 启用）。
     * 合并循环将新番剧攒入 pendingInsert、循环结束后才统一入库，DAO 查不到尚未
     * 落库的行，需在内存中先行去重——否则同一番剧的服务端新旧行（数字 id 行与
     * UUID 行）会双双插入，形成"一条带 id + 一条无 id"的重复条目。
     */
    private fun findPendingAnime(
        pending: List<Anime>,
        subscriptionId: String?,
        bangumiId: Int?,
        remoteSyncId: String?,
        title: String,
        allowTitleFallback: Boolean
    ): AnimeMatch? {
        val byIdentity = pending.firstOrNull { p ->
            (subscriptionId != null && p.serverSubscriptionId == subscriptionId) ||
                (bangumiId != null && p.bangumiId == bangumiId) ||
                (remoteSyncId != null && p.remoteSyncId == remoteSyncId)
        }
        if (byIdentity != null) return AnimeMatch(byIdentity, identityMatched = true)
        if (!allowTitleFallback) return null
        return pending.firstOrNull { it.title == title }
            ?.let { AnimeMatch(it, identityMatched = false) }
    }

    /**
     * 合并插入前的「换键行」兜底识别（第 4 级防线）。
     *
     * 场景：md 导入/手动添加的番剧无 bangumiId，首次上传以懒生成的 remoteSyncId
     * （UUID）为行键；此后某台设备在详情页补全封面绑上 bangumiId 并改名，服务器
     * 行键从 UUID 迁移为数字 ID（旧行虽有迁移删除兜底，但另一台设备的旧副本仍会
     * 以 UUID 重传），且新行的新标题与本地旧名互不匹配，三级匹配全落空，新行被
     * 误判为「其他设备添加的新番剧」插入本地 → 同一部番出现新旧两张卡片。
     *
     * 识别信号：换键/改名/补封面均不会触碰进度四元组 (status, watchedEpisodes,
     * startDate, finishDate)，两台设备的记录同源同步、必然一致，故以四元组为
     * 进度指纹做最后防线。仅保守拦截（跳过插入）不改写本地；真巧合误拦时源设备
     * 下次全量同步会重传该行，自愈无害。
     */
    private fun findTwinByProgressFingerprint(
        candidates: List<Anime>,
        remote: Subscription
    ): Anime? {
        // 仅拦截数字行键（换键后的正身行）；UUID 行走原三级匹配即可
        if (remote.animeId.toIntOrNull()?.takeIf { it > 0 } == null) return null
        val watched = remote.watchedEpisodes ?: 0
        // 0 进度组合过于普遍（大量计划观看番），宁可漏拦不可误拦
        if (watched <= 0) return null
        fun String?.norm() = this?.takeIf { it.isNotBlank() }
        val remoteStatus = remote.status
        val remoteStart = remote.startDate.norm()
        val remoteFinish = remote.finishDate.norm()
        return candidates.firstOrNull { local ->
            // 旧副本特征：尚未换绑 bangumiId、且曾以 UUID 行键同步过
            local.bangumiId == null && local.remoteSyncId != null
                    // 进度四元组全等（比对口径与客户端 Diff 的 isRemoteInSync 一致）
                    && local.status.toApiString() == remoteStatus
                    && local.watchedEpisodes == watched
                    && local.startDate?.let { formatDate(it) } == remoteStart
                    && local.finishDate?.let { formatDate(it) } == remoteFinish
        }
    }

    /**
     * 换键行强匹配：远程正身行携带服务端迁移映射（supersededAnimeIds = 被本行
     * 取代的旧行键，来自换键设备的上报），本地旧副本的 remoteSyncId / bangumiId
     * 命中即视为同一部番。旧行键（UUID / 旧 bangumiId）全局唯一，误伤率≈0，
     * 可安全自动换键合并（区别于进度指纹的保守拦截）。
     *
     * @param includeBangumi v2 下传 false：数字 superseded 可能来自历史 backfill
     *   误当 Bangumi ID 的本地自增 id，与另一部番的 bangumiId 撞车会改错身份；
     *   只保留 UUID（remoteSyncId）匹配。v1 旧服务端保持原行为。
     */
    private fun findTwinBySupersededIds(
        candidates: List<Anime>,
        remote: Subscription,
        includeBangumi: Boolean
    ): Anime? {
        val superseded = remote.supersededAnimeIds?.takeIf { it.isNotEmpty() } ?: return null
        if (remote.animeId.toIntOrNull()?.takeIf { it > 0 } == null) return null
        return candidates.firstOrNull { local ->
            (local.remoteSyncId != null && local.remoteSyncId in superseded) ||
                (includeBangumi && local.bangumiId != null &&
                    local.bangumiId.toString() in superseded)
        }
    }

    /**
     * 换键合并：远程正身行与本地旧副本是同一部番，采纳其行键与元数据
     * （换键设备已完成补全封面/改名），保留本地进度/评分/笔记/日期，不插入新卡。
     * 服务端已有该行，本地对齐即可，不触发后端同步；标题变化会重识别 seriesKey。
     */
    private suspend fun applyServerKeyMigration(existing: Anime, remote: Subscription) {
        val bangumiId = remote.animeId.toIntOrNull()?.takeIf { it > 0 } ?: return
        val updated = existing.copy(
            bangumiId = bangumiId,
            serverSubscriptionId = remote.subscriptionId ?: existing.serverSubscriptionId,
            title = remote.animeTitle,
            coverUrl = remote.animeImage ?: existing.coverUrl,
            airDate = remote.airDate ?: existing.airDate,
            airWeekday = remote.weekday ?: existing.airWeekday,
            totalEpisodes = (remote.totalEpisodes ?: 0).takeIf { it > 0 } ?: existing.totalEpisodes,
            isFinished = !remote.isAiring
        )
        repository.updateAnimeInternal(updated)
        AppLogManager.i(
            logTag,
            "换键合并：[${existing.title}] → [${updated.title}]，行键 ${existing.bangumiId ?: existing.remoteSyncId} → $bangumiId"
        )
    }

    /** 由后端订阅行构建本地元数据条目（完整信息留空待用户点击详情时补全） */
    private fun buildMetaAnime(remote: Subscription, bangumiId: Int?, remoteSyncId: String?): Anime =
        Anime(
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
            serverSubscriptionId = remote.subscriptionId,
            airWeekday = remote.weekday,
            isFinished = !remote.isAiring, // isAiring: true=连载中, false=已完结
            currentEpisodes = remote.currentEpisodes ?: 0
        )
}
