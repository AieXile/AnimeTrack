package com.aiexile.animetrack.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 待删除键类型。
 *
 * 补偿重试时必须按真实类型删除：
 * - SERVER：服务端权威 subscriptionId，删除一次即连同 alias 一起清理；
 * - BANGUMI：Bangumi 条目 ID；
 * - REMOTE_SYNC：客户端 UUID 同步键；
 * - LEGACY_UUID：数据库升级遗留的 UUID 键，旧表无法区分 subscriptionId 与 remoteSyncId，
 *   重试时依次按两者尝试（都是精确标识，不会误删其他番剧）；
 * - LEGACY_LOCAL：旧版本本地自增 id。它只在旧服务端（v1）有意义，
 *   v2 下把它当数字 Bangumi ID 会误删另一部番，因此补偿重试直接跳过。
 */
enum class PendingDeletionKeyType {
    SERVER,
    BANGUMI,
    REMOTE_SYNC,
    LEGACY_UUID,
    LEGACY_LOCAL;

    /** 补偿重试优先级：先删 canonical，成功后可整组跳过 alias 重试 */
    val priority: Int
        get() = when (this) {
            SERVER -> 0
            BANGUMI -> 1
            REMOTE_SYNC -> 2
            LEGACY_UUID -> 3
            LEGACY_LOCAL -> 4
        }
}

/**
 * 待删除墓碑：番剧已在本地删除、但服务端订阅行的删除尚未确认成功的行键记录。
 *
 * 写入时机：deleteAnime 在删本地行之前，先把该番剧的全部稳定行键
 * （subscriptionId / bangumiId / remoteSyncId）落盘。此后即使网络删除失败或
 * 进程在请求发出前被杀，下次同步也会先按墓碑补偿重试，防止服务器残留行
 * 在合并时被重新插回本地（已删番剧复活）。
 *
 * 本地自增 id 不再写入墓碑：它不是服务端的稳定身份，按数字发送会被服务端当作
 * Bangumi ID 解析，可能误删另一部番。旧服务端的本地 id 行清理仍由删除当次的
 * v1 fallback 负责。
 *
 * groupKey 把同一部番的行键绑成一组：组内任一稳定键删除成功即代表 canonical
 * 已删除，其余 alias 键随之清理，避免同一次补偿循环继续误删其他番剧。
 *
 * ownerKey 绑定写入时登录的账号（服务器 user id 优先，回退 username）：
 * bangumiId 行键跨账号共享，换账号后旧墓碑直接清理，避免误删新账号的同名行。
 */
@Entity(
    tableName = "pending_deletions",
    indices = [
        Index(value = ["ownerKey", "animeId"], unique = true),
        Index(value = ["ownerKey", "groupKey"])
    ]
)
data class PendingDeletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** 服务端订阅行键值（语义由 [keyType] 决定） */
    val animeId: String,
    val keyType: PendingDeletionKeyType,
    /** 同一次删除写入的全部行键共享一个分组 ID */
    val groupKey: String,
    /** 写入时登录账号标识（服务器 user id，回退 username） */
    val ownerKey: String,
    val createdAt: Long
)
