package com.aiexile.animetrack.data.player

/** 站内支持索引/浏览的常见视频容器扩展名。 */
object VideoFormats {
    val extensions: Set<String> = setOf(
        "mp4", "mkv", "avi", "webm", "mov", "flv", "wmv", "ts", "m4v"
    )

    fun isVideoFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in extensions
}
