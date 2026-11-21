package com.aiexile.animetrack.data.player.webdav

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.aiexile.animetrack.domain.playback.SubtitleSpec
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
class WebDAVDataSourceFactory(
    private val okHttpClient: OkHttpClient,
    private val username: String,
    private val password: String
) : DataSource.Factory {

    override fun createDataSource(): WebDAVDataSource = WebDAVDataSource(okHttpClient, username, password)

    internal fun createDataSource(uri: Uri): DataSource {
        val filename = uri.toString().toHttpUrlOrNull()?.pathSegments?.lastOrNull().orEmpty()
        if (!SubtitleSpec.isSupported(filename)) return createDataSource()
        val subtitleClient = okHttpClient.newBuilder()
            .callTimeout(WebDavSubtitleBudget.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(WebDavSubtitleBudget.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        return BoundedWebDavSubtitleDataSource(WebDAVDataSource(subtitleClient, username, password))
    }
}
