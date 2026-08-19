package com.aiexile.animetrack.data.remote

/**
 * 自建更新服务器 GET /update 的响应体。
 *
 * 字段说明：
 * - [version] 最新版本号（如 "v1.2.3" 或 "1.2.3"）
 * - [size] APK 文件大小（字节）
 * - [sha256] APK 文件的 SHA-256 摘要（裸 hex，无 "sha256:" 前缀）
 * - [date] 发布日期（ISO 8601 格式，如 "2026-08-18T19:02:58Z"）
 * - [notes] 更新日志（Markdown）
 * - [url] APK 下载直链
 * - [prerelease] 是否为预发布版本（App 端忽略预发布）
 * - [mirror] 数据来源标识："server"=服务器本地已同步（站点内直链，快）；
 *   "github"=服务器本地未同步，实时拉 GitHub 兜底（GitHub 直链，国内可能慢）
 */
data class ServerUpdateResponse(
    val version: String = "",
    val size: Long = 0,
    val sha256: String = "",
    val date: String = "",
    val notes: String = "",
    val url: String = "",
    val prerelease: Boolean = false,
    val mirror: String = ""
)
