package com.aiexile.animetrack.data

import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedAnime(
    val title: String,
    val status: AnimeStatus,
    val note: String,
    val finishDate: Long?,
    val watchedEpisodes: Int = 0,
    val totalEpisodes: Int = 0
)

data class ImportResult(
    val watchingCount: Int,
    val completedCount: Int,
    val plannedCount: Int,
    val droppedCount: Int,
    val animes: List<ParsedAnime>
)

object MarkdownParser {
    
    private val headerRegex = Regex("""^#+\s+(.*)""")
    private val dateRegex = Regex("""(\d{4})[./-](\d{1,2})[./-](\d{1,2})""")
    private val listPrefixRegex = Regex("""^([-*]\s+|\d+\.\s*)""")
    private val episodeInfoRegex = Regex("""\s+(\d+)/(\d+|\?)\s*$""")
    
    private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    
    private val statusKeywords = setOf(
        "now", "watching", "want", "wish", "already", "completed", "done", "dropped",
        "正在", "计划", "打算", "已看完", "弃番", "放弃"
    )
    
    fun parse(content: String): ImportResult {
        val lines = content.lines()
        var currentStatus: AnimeStatus? = null
        var lastDate: Long? = null
        val parsedAnimes = mutableListOf<ParsedAnime>()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            val headerMatch = headerRegex.find(trimmed)
            if (headerMatch != null) {
                val headerText = headerMatch.groupValues[1]
                
                val dateMatch = dateRegex.find(headerText)
                if (dateMatch != null) {
                    lastDate = parseDate(dateMatch.value)
                }
                
                val newStatus = determineStatus(headerText)
                if (newStatus != null) {
                    currentStatus = newStatus
                    if (newStatus != AnimeStatus.COMPLETED) {
                        lastDate = null
                    }
                }
                continue
            }
            
            val directStatus = determineStatus(trimmed)
            if (directStatus != null && isStandaloneStatusLine(trimmed)) {
                currentStatus = directStatus
                lastDate = null
                continue
            }
            
            if (currentStatus != null) {
                var cleanLine = listPrefixRegex.replace(trimmed, "")
                if (cleanLine.isEmpty()) continue

                val episodeInfo = extractEpisodeInfo(cleanLine)
                val title = extractTitle(cleanLine)
                val note = extractNote(cleanLine)

                if (title.isNotEmpty()) {
                    parsedAnimes.add(ParsedAnime(
                        title = title,
                        status = currentStatus,
                        note = note,
                        finishDate = if (currentStatus == AnimeStatus.COMPLETED) lastDate else null,
                        watchedEpisodes = episodeInfo.first,
                        totalEpisodes = episodeInfo.second
                    ))
                }
            }
        }
        
        return ImportResult(
            watchingCount = parsedAnimes.count { it.status == AnimeStatus.WATCHING },
            completedCount = parsedAnimes.count { it.status == AnimeStatus.COMPLETED },
            plannedCount = parsedAnimes.count { it.status == AnimeStatus.PLANNED },
            droppedCount = parsedAnimes.count { it.status == AnimeStatus.DROPPED },
            animes = parsedAnimes
        )
    }
    
    private fun isStandaloneStatusLine(line: String): Boolean {
        val normalized = line.lowercase().trim()
        
        val statusPatterns = listOf(
            "now", "watching", "want", "wish", "already", "completed", "done", "dropped",
            "正在观看", "正在", "计划观看", "计划", "打算", "已看完", "弃番", "放弃"
        )
        
        return statusPatterns.any { pattern ->
            normalized == pattern.lowercase() ||
            normalized.matches(Regex("""^#+\s*${Regex.escape(pattern)}\s*$""", RegexOption.IGNORE_CASE))
        }
    }
    
    private fun determineStatus(headerText: String): AnimeStatus? {
        return when {
            headerText.contains("Now", true) 
                || headerText.contains("正在") 
                || headerText.contains("Watching", true) -> AnimeStatus.WATCHING
            
            headerText.contains("Want", true) 
                || headerText.contains("计划") 
                || headerText.contains("打算") 
                || headerText.contains("Wish", true) -> AnimeStatus.PLANNED
            
            headerText.contains("Already", true) 
                || headerText.contains("已看完") 
                || headerText.contains("Completed", true) 
                || headerText.contains("Done", true) -> AnimeStatus.COMPLETED
            
            headerText.contains("Dropped", true) 
                || headerText.contains("弃番") 
                || headerText.contains("放弃") -> AnimeStatus.DROPPED
            
            else -> null
        }
    }
    
    private fun parseDate(dateStr: String): Long? {
        return try {
            val normalized = dateStr.replace("/", ".").replace("-", ".")
            dateFormat.parse(normalized)?.time
        } catch (e: Exception) {
            null
        }
    }
    
    private fun cleanMarkdown(text: String): String {
        return text
            .replace("**", "")
            .replace("*", "")
            .replace("__", "")
            .replace("_", "")
            .replace("~~", "")
            .replace("`", "")
            .trim()
    }

    private fun extractEpisodeInfo(line: String): Pair<Int, Int> {
        // 先去掉括号备注部分，再在剩余内容末尾匹配 数字/数字 或 数字/?
        val lineWithoutNote = removeNotePart(line)
        val match = episodeInfoRegex.find(lineWithoutNote)
        if (match != null) {
            val watched = match.groupValues[1].toIntOrNull() ?: 0
            val totalStr = match.groupValues[2]
            val total = if (totalStr == "?") 0 else totalStr.toIntOrNull() ?: 0
            return Pair(watched, total)
        }
        return Pair(0, 0)
    }

    private fun removeNotePart(line: String): String {
        val parenIndex = line.indexOf("(")
        val bracketIndex = line.indexOf("（")
        val splitIndex = when {
            parenIndex < 0 && bracketIndex < 0 -> -1
            parenIndex < 0 -> bracketIndex
            bracketIndex < 0 -> parenIndex
            else -> minOf(parenIndex, bracketIndex)
        }
        return if (splitIndex > 0) line.substring(0, splitIndex).trim() else line.trim()
    }

    private fun extractTitle(line: String): String {
        // 先去掉集数和备注部分，提取纯标题
        val lineWithoutNote = removeNotePart(line)
        val episodeMatch = episodeInfoRegex.find(lineWithoutNote)
        val rawTitle = if (episodeMatch != null) {
            lineWithoutNote.substring(0, episodeMatch.range.first).trim()
        } else {
            lineWithoutNote.trim()
        }
        return cleanMarkdown(rawTitle)
    }
    
    private fun extractNote(line: String): String {
        val startParen = line.indexOf("(")
        val startBracket = line.indexOf("（")
        
        val startIndex = when {
            startParen < 0 && startBracket < 0 -> -1
            startParen < 0 -> startBracket
            startBracket < 0 -> startParen
            else -> minOf(startParen, startBracket)
        }
        
        if (startIndex < 0) return ""
        
        val endParen = line.lastIndexOf(")")
        val endBracket = line.lastIndexOf("）")
        
        val endIndex = when {
            endParen < 0 && endBracket < 0 -> -1
            endParen < 0 -> endBracket
            endBracket < 0 -> endParen
            else -> maxOf(endParen, endBracket)
        }
        
        val rawNote = if (endIndex > startIndex) {
            line.substring(startIndex + 1, endIndex).trim()
        } else {
            ""
        }
        
        return cleanMarkdown(rawNote)
    }
    
    fun toAnimeEntity(parsed: ParsedAnime): Anime {
        return Anime(
            title = parsed.title,
            totalEpisodes = parsed.totalEpisodes,
            watchedEpisodes = parsed.watchedEpisodes,
            status = parsed.status,
            rating = null,
            notes = parsed.note,
            finishDate = parsed.finishDate
        )
    }
}
