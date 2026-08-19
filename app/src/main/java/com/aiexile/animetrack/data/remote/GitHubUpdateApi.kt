package com.aiexile.animetrack.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

/** GitHub Releases 接口，作为自建更新服务器不可用时的兜底更新源 */
interface GitHubUpdateApi {

    @GET("repos/AieXile/AnimeTrack/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease

    @GET("repos/AieXile/AnimeTrack/releases/tags/{tag}")
    suspend fun getReleaseByTag(@Path("tag") tag: String): GitHubRelease
}
