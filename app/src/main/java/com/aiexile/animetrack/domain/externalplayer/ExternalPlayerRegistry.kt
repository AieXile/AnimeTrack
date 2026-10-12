package com.aiexile.animetrack.domain.externalplayer

/** 已接入的外部播放器注册表；新增播放器在此登记 */
object ExternalPlayerRegistry {
    val all: List<ExternalPlayer> = listOf(AnimekoPlayer)

    fun byId(id: ExternalPlayerId): ExternalPlayer? = all.find { it.id == id }
}
