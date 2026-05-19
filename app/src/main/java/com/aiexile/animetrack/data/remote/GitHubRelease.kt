package com.aiexile.animetrack.data.remote

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("published_at") val publishedAt: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    val name: String = "",
    val size: Long = 0,
    @SerializedName("browser_download_url") val browserDownloadUrl: String = "",
    @SerializedName("content_type") val contentType: String = ""
)
