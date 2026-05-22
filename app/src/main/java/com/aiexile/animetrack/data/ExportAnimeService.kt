package com.aiexile.animetrack.data

import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import java.text.SimpleDateFormat
import java.util.Locale

object ExportAnimeService {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun exportToMarkdown(animes: List<Anime>): String {
        if (animes.isEmpty()) return ""

        val sb = StringBuilder()

        val statusGroups = listOf(
            AnimeStatus.WATCHING to "正在看",
            AnimeStatus.PLANNED to "计划看",
            AnimeStatus.COMPLETED to "已看完",
            AnimeStatus.DROPPED to "弃番"
        )

        var firstGroup = true

        for ((status, heading) in statusGroups) {
            val group = animes.filter { it.status == status }
            if (group.isEmpty()) continue

            if (!firstGroup) sb.appendLine()
            firstGroup = false

            if (status == AnimeStatus.COMPLETED) {
                appendCompletedGroup(sb, heading, group)
            } else {
                sb.appendLine("## $heading")
                for (anime in group) {
                    sb.appendLine(formatAnimeItem(anime))
                }
            }
        }

        return sb.toString().trimEnd()
    }

    private fun appendCompletedGroup(sb: StringBuilder, heading: String, animes: List<Anime>) {
        val byDate = animes.groupBy { it.finishDate }
        val nullDateAnimes = byDate[null].orEmpty()
        val datedEntries = byDate.filterKeys { it != null }
            .entries
            .sortedByDescending { it.key }

        var needsSeparator = false

        if (nullDateAnimes.isNotEmpty()) {
            sb.appendLine("## $heading")
            for (anime in nullDateAnimes) {
                sb.appendLine(formatAnimeItem(anime))
            }
            needsSeparator = true
        }

        for (entry in datedEntries) {
            if (needsSeparator) sb.appendLine()
            needsSeparator = true

            val dateStr = dateFormat.format(entry.key!!)
            sb.appendLine("## $heading - $dateStr")
            for (anime in entry.value) {
                sb.appendLine(formatAnimeItem(anime))
            }
        }
    }

    private fun formatAnimeItem(anime: Anime): String {
        return if (anime.notes.isNotBlank()) {
            "- ${anime.title} (${anime.notes})"
        } else {
            "- ${anime.title}"
        }
    }
}
