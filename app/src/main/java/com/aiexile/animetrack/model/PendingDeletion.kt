package com.aiexile.animetrack.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 待删除墓碑：番剧已在本地删除、但服务端订阅行的删除尚未确认成功的行键记录。
 *
 * 写入时机：deleteAnime 在删本地行之前，先把该番剧的全部服务端行键
 * （bangumiId / remoteSyncId / 旧本地自增 id）落盘。此后即使网络删除失败或
 * 进程在请求发出前被杀，下次同步也会先按墓碑补偿重试，防止服务器残留行
 * 在合并时被重新插回本地（已删番剧复活）。
 *
 * ownerKey 绑定写入时登录的账号（服务器 user id 优先，回退 username）：
 * bangumiId 行键跨账号共享，换账号后旧墓碑直接清理，避免误删新账号的同名行。
 */
@Entity(
    tableName = "pending_deletions",
    indices = [Index(value = ["ownerKey", "animeId"], unique = true)]
)
data class PendingDeletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** 服务端订阅行键：Bangumi 条目 ID / UUID remoteSyncId / 旧版本本地自增 id */
    val animeId: String,
    /** 写入时登录账号标识（username，回退 bangumi:xxx） */
    val ownerKey: String,
    val createdAt: Long
)
