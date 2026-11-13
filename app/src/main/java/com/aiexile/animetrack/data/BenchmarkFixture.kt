package com.aiexile.animetrack.data

import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus

/**
 * 仅采集包（`BENCHMARK_MODE`）使用的本地测试数据。
 *
 * Baseline Profile 需要真实的网格滚动、详情进出与状态分布；通过 UI 一条条添加在
 * 不同厂商的输入法/权限弹窗下不可靠，因此改为首次启动直接写入本包自己的数据库。
 * 名称带 `Profile Sample` 前缀，便于采集脚本确认数据就绪，也避免与系列识别规则冲突。
 */
object BenchmarkFixture {
    private const val PREFIX = "Profile Sample"

    fun animes(): List<Anime> = (1..18).map { index ->
        Anime(
            title = "${PREFIX} ${index.toString().padStart(2, '0')}",
            totalEpisodes = 12,
            watchedEpisodes = index % 12,
            status = STATUSES[index % STATUSES.size],
            rating = (6 + index % 4).toFloat(),
            notes = "",
            startDate = System.currentTimeMillis() - index * 86_400_000L,
        )
    }

    /** 采集脚本用最后一条确认数据已写入。 */
    val lastTitle: String = "${PREFIX} 18"

    private val STATUSES = listOf(
        AnimeStatus.WATCHING,
        AnimeStatus.WATCHING,
        AnimeStatus.PLANNED,
        AnimeStatus.COMPLETED,
        AnimeStatus.DROPPED,
    )
}
