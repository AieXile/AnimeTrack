package com.aiexile.animetrack.data.remote

import retrofit2.http.GET

interface UpdateApi {

    @GET("repos/AieXile/AnimeTrack/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}
