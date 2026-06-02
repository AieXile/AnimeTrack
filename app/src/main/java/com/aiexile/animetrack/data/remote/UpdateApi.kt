package com.aiexile.animetrack.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface UpdateApi {

    @GET("repos/AieXile/AnimeTrack/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease

    @GET("repos/AieXile/AnimeTrack/releases/tags/{tag}")
    suspend fun getReleaseByTag(@Path("tag") tag: String): GitHubRelease
}
