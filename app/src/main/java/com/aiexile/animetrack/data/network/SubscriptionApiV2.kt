package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.model.PendingDeletionKeyType
import retrofit2.HttpException

/**
 * 订阅 v2 接口入口，统一处理「新服务端 / 旧服务端」的切换：
 * - v2 正常：使用 subscriptionId / alias / 墓碑 / LWW；
 * - 服务端未部署 v2（404/405）：自动回退旧接口，行为与旧版本一致。
 *
 * 注意：只有「接口不存在」才回退。400/409/500 等业务或服务端错误向上抛出，
 * 避免用旧接口掩盖 v2 身份冲突。
 */
object SubscriptionApiV2 {
    private const val TAG = "SubscriptionApi"

    data class ListResult(
        val subscriptions: List<Subscription>,
        val isV2: Boolean,
        val tombstones: List<SubscriptionV2Tombstone> = emptyList()
    )

    data class UpsertResult(
        val success: Boolean,
        val usedV2: Boolean,
        val ignored: Boolean = false,
        val applied: Boolean? = null,
        val subscriptionId: String? = null,
        val canonical: Subscription? = null,
        val merged: List<SubscriptionV2MergedItem> = emptyList(),
        val conflicts: List<SubscriptionV2Conflict> = emptyList(),
        val revisionMatched: Boolean? = null,
        val message: String? = null
    )

    /**
     * 删除结果：
     * - [success]：请求链路成功（v2 返回 success 或 v1 回退全部成功）；
     * - [removed]：服务端确实找到了并删除了订阅行。
     *
     * 补偿重试必须看 [removed]：v2 对不存在的键返回 success=true + removed=false，
     * 若把这种情况当作成功，就会在 canonical 仍以其他 identity 存在时提前清墓碑。
     */
    data class DeleteResult(
        val success: Boolean,
        val removed: Boolean
    )

    private fun isMissingEndpoint(error: HttpException): Boolean =
        error.code() == 404 || error.code() == 405

    suspend fun fetchList(): ListResult {
        val api = RetrofitClient.userAuthApi
        try {
            val response = api.getSubscriptionsV2()
            if (response.success && response.apiVersion == 2) {
                return ListResult(
                    subscriptions = response.subscriptions.orEmpty(),
                    isV2 = true,
                    tombstones = response.tombstones.orEmpty()
                )
            }
            AppLogManager.w(TAG, "订阅 v2 列表返回异常: success=${response.success}, apiVersion=${response.apiVersion}")
        } catch (error: HttpException) {
            if (!isMissingEndpoint(error)) throw error
            AppLogManager.i(TAG, "服务端未部署订阅 v2，回退 v1 列表")
        }

        val legacy = api.getSubscriptions()
        return ListResult(
            subscriptions = legacy.subscriptions.orEmpty(),
            isV2 = false
        )
    }

    suspend fun upsert(
        request: SubscriptionV2UpsertRequest,
        fallback: SubscribeRequest
    ): UpsertResult {
        val api = RetrofitClient.userAuthApi
        try {
            val response = api.upsertSubscriptionV2(request)
            return UpsertResult(
                success = response.success,
                usedV2 = true,
                ignored = response.ignored == true,
                applied = response.applied,
                subscriptionId = response.subscriptionId ?: response.canonical?.subscriptionId,
                canonical = response.canonical,
                merged = response.merged.orEmpty(),
                conflicts = response.conflicts.orEmpty(),
                revisionMatched = response.revisionMatched,
                message = response.message
            )
        } catch (error: HttpException) {
            if (!isMissingEndpoint(error)) throw error
            AppLogManager.i(TAG, "服务端未部署订阅 v2，回退 v1 add")
        }

        val legacy = api.addSubscription(fallback)
        return UpsertResult(
            success = legacy.success,
            usedV2 = false,
            message = legacy.message
        )
    }

    /**
     * 删除一个 canonical 订阅。v2 下 alias 会一起清理，一次请求即可；
     * 回退 v1 时按 [fallbackAnimeIds] 逐个删除，全部成功才算链路成功。
     */
    suspend fun delete(
        request: SubscriptionV2DeleteRequest,
        fallbackAnimeIds: List<String>
    ): DeleteResult {
        val api = RetrofitClient.userAuthApi
        try {
            val response = api.deleteSubscriptionV2(request)
            return DeleteResult(
                success = response.success,
                removed = response.success && response.removed == true
            )
        } catch (error: HttpException) {
            if (!isMissingEndpoint(error)) throw error
            AppLogManager.i(TAG, "服务端未部署订阅 v2，回退 v1 remove")
        }

        var allSucceeded = true
        if (fallbackAnimeIds.isEmpty()) {
            // 没有 v1 等价键可删：不能假装成功，否则墓碑会被误清理
            return DeleteResult(success = false, removed = false)
        }
        for (animeId in fallbackAnimeIds.distinct()) {
            val removed = runCatching {
                api.removeSubscription(RemoveSubscribeRequest(animeId = animeId))
            }
            if (removed.isFailure || removed.getOrNull()?.success != true) {
                allSucceeded = false
            }
        }
        // 旧接口只返回 success，无法区分「不存在」与「已删除」；交给墓碑 TTL 兜底
        return DeleteResult(success = allSucceeded, removed = allSucceeded)
    }

    /**
     * 墓碑补偿重试：按真实键类型删除。
     *
     * 绝不能把纯数字键一律当 Bangumi ID：本地 Room 自增 id 也可能与另一部番的
     * Bangumi ID 相同，误判会直接删掉别人的订阅。LEGACY_LOCAL 只在旧服务端
     * （v1）有意义，v2 下无法安全寻址，直接跳过交给 TTL 清理。
     */
    suspend fun deleteByKey(key: String, keyType: PendingDeletionKeyType): DeleteResult {
        val value = key.trim()
        if (value.isEmpty() || value == "0") return DeleteResult(success = false, removed = false)
        return when (keyType) {
            PendingDeletionKeyType.SERVER -> delete(
                request = SubscriptionV2DeleteRequest(subscriptionId = value),
                // v1 没有 subscriptionId 概念，回退时没有等价键可删
                fallbackAnimeIds = emptyList()
            )

            PendingDeletionKeyType.BANGUMI -> {
                val bangumiId = value.toIntOrNull()?.takeIf { it > 0 }
                    ?: return DeleteResult(success = false, removed = false)
                delete(
                    request = SubscriptionV2DeleteRequest(
                        identity = SubscriptionV2Identity(bangumiId = bangumiId)
                    ),
                    fallbackAnimeIds = listOf(value)
                )
            }

            PendingDeletionKeyType.REMOTE_SYNC -> delete(
                request = SubscriptionV2DeleteRequest(
                    identity = SubscriptionV2Identity(remoteSyncId = value)
                ),
                fallbackAnimeIds = listOf(value)
            )

            // 数据库升级遗留：旧表无法区分 subscriptionId 与 remoteSyncId。
            // 两者都是精确标识，依次尝试不会误删其他番剧。
            PendingDeletionKeyType.LEGACY_UUID -> {
                val byServer = delete(
                    request = SubscriptionV2DeleteRequest(subscriptionId = value),
                    fallbackAnimeIds = emptyList()
                )
                if (byServer.removed) return byServer
                val byRemote = delete(
                    request = SubscriptionV2DeleteRequest(
                        identity = SubscriptionV2Identity(remoteSyncId = value)
                    ),
                    fallbackAnimeIds = listOf(value)
                )
                DeleteResult(
                    success = byServer.success || byRemote.success,
                    removed = byRemote.removed
                )
            }

            PendingDeletionKeyType.LEGACY_LOCAL -> DeleteResult(success = false, removed = false)
        }
    }
}
