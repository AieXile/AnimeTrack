package com.aiexile.animetrack.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.aiexile.animetrack.domain.media.WebPlaybackQueue
import com.aiexile.animetrack.model.WebSourceEpisode

private val Context.webPlaybackHistoryStore: DataStore<Preferences> by
    preferencesDataStore(name = "web_playback_history")

/** 网页播放历史：保存原网页地址，播放前重新解析可能过期的媒体地址。 */
class WebPlaybackHistoryRepository(private val context: Context) {
    data class Entry(
        val url: String,
        val title: String?,
        val lastPlayedAt: Long,
        val userAgent: String? = null,
        val referer: String? = null,
        val seriesUrl: String? = null,
        val mediaId: String? = null,
        val seriesTitle: String? = null,
        val episodeName: String? = null,
        val nextEpisode: WebSourceEpisode? = null,
        val nextMediaId: String? = null
    )

    val entries: Flow<List<Entry>> = context.webPlaybackHistoryStore.data
        .map { prefs -> decode(prefs[KEY].orEmpty()) }
        .distinctUntilChanged()

    suspend fun add(url: String, title: String?, userAgent: String? = null, referer: String? = null,
        seriesUrl: String? = null, queue: WebPlaybackQueue? = null, mediaId: String? = null) {
        val normalized = url.trim()
        if (normalized.isBlank()) return
        context.webPlaybackHistoryStore.edit { prefs ->
            val current = decode(prefs[KEY].orEmpty())
            val previous = current.firstOrNull { it.url == normalized }
            val next = queue?.select(queue.episodeIndex + 1)
            val identity = queue?.seriesUrl ?: seriesUrl ?: previous?.seriesUrl
            val entry = Entry(normalized, title, System.currentTimeMillis(), userAgent, referer,
                identity, mediaId ?: queue?.mediaKey() ?: previous?.mediaId,
                queue?.page?.title ?: previous?.seriesTitle,
                queue?.episode?.name ?: previous?.episodeName,
                if (queue != null) next?.episode else previous?.nextEpisode,
                if (queue != null) next?.mediaKey() else previous?.nextMediaId)
            // 仅保存重新解析所需的 UA/Referer，不持久化捕获的 Cookie 或鉴权头。
            val updated = (listOf(entry) +
                current.filterNot { it.url == normalized || (identity != null && it.seriesUrl == identity) })
                .take(MAX_ENTRIES)
            prefs[KEY] = encode(updated)
        }
    }

    suspend fun remove(url: String) {
        context.webPlaybackHistoryStore.edit { prefs ->
            prefs[KEY] = encode(decode(prefs[KEY].orEmpty()).filterNot { it.url == url })
        }
    }

    suspend fun clear() {
        context.webPlaybackHistoryStore.edit { it.remove(KEY) }
    }

    internal companion object {
        val KEY = stringPreferencesKey("entries")
        const val MAX_ENTRIES = 20

        private val gson = Gson()
        private val listType = object : TypeToken<List<Entry>>() {}.type

        internal fun encode(entries: List<Entry>): String = gson.toJson(entries)

        internal fun decode(raw: String): List<Entry> {
            if (raw.isBlank()) return emptyList()
            return runCatching { gson.fromJson<List<Entry>>(raw, listType) }
                .getOrDefault(emptyList())
        }
    }
}
