package com.aiexile.animetrack.data.player

import androidx.media3.datasource.DataSource
import okhttp3.OkHttpClient

class WebDAVDataSourceFactory(
    private val okHttpClient: OkHttpClient,
    private val username: String,
    private val password: String
) : DataSource.Factory {

    override fun createDataSource(): WebDAVDataSource {
        return WebDAVDataSource(okHttpClient, username, password)
    }
}
