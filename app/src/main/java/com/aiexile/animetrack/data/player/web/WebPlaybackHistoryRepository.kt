package com.aiexile.animetrack.data.player.web

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
import com.aiexile.animetrack.domain.playback.WebPlaybackQueue
import com.aiexile.animetrack.model.WebSourceEpisode

private val Context.webPlaybackHistoryStore: DataStore<Preferences> by
    preferencesDataStore(name = "web_playback_history")

/** 为最近播放和持久网页来源保存原网页地址，起播时重新解析媒体地址。 */
class WebPlaybackHistoryRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.webPlaybackHistoryStore)

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
        val nextMediaId: String? = null,
        /** 从番剧详情页进入时绑定的番剧；全局网页历史为 null。 */
        val animeId: Int? = null,
        val animeTitle: String? = null
    ) {
        val libraryKey: String get() = seriesUrl ?: url
    }

    val entries: Flow<List<Entry>> = store.data
        .map { prefs -> decode(prefs[KEY].orEmpty()) }
        .distinctUntilChanged()

    val libraryEntries: Flow<List<Entry>> = store.data
        .map(::readLibrary)
        .distinctUntilChanged()

    suspend fun add(url: String, title: String?, userAgent: String? = null, referer: String? = null,
        seriesUrl: String? = null, queue: WebPlaybackQueue? = null, mediaId: String? = null,
        animeId: Int? = null, animeTitle: String? = null) {
        val normalized = url.trim()
        if (normalized.isBlank()) return
        store.edit { prefs ->
            val current = decode(prefs[KEY].orEmpty())
            val library = readLibrary(prefs)
            val previous = current.firstOrNull { it.url == normalized } ?: library.firstOrNull { it.url == normalized }
            val next = queue?.select(queue.episodeIndex + 1)
            val identity = queue?.seriesUrl ?: seriesUrl ?: previous?.seriesUrl
            val saved = library.firstOrNull { it.matchesSource(normalized, identity) }
            val entry = Entry(normalized, title ?: previous?.title, System.currentTimeMillis(),
                userAgent ?: previous?.userAgent, referer ?: previous?.referer,
                identity, mediaId ?: queue?.mediaKey() ?: previous?.mediaId,
                queue?.page?.title ?: previous?.seriesTitle,
                queue?.episode?.name ?: previous?.episodeName,
                if (queue != null) next?.episode else previous?.nextEpisode,
                if (queue != null) next?.mediaKey() else previous?.nextMediaId,
                // 同一页面可能先以全局身份播放，之后再从详情页进入；保留已有绑定
                animeId ?: saved?.animeId ?: previous?.animeId,
                animeTitle ?: saved?.animeTitle ?: previous?.animeTitle)
            // 仅保存重新解析所需的 UA/Referer，不持久化捕获的 Cookie 或鉴权头。
            val updated = (listOf(entry) +
                current.filterNot { it.url == normalized || (identity != null && it.seriesUrl == identity) })
                .take(MAX_ENTRIES)
            prefs[KEY] = encode(updated)
            prefs[LIBRARY_KEY] = encode(if (saved != null || animeId != null) {
                library.upsert(entry.copy(seriesTitle = saved?.seriesTitle ?: entry.seriesTitle))
            } else library)
        }
    }

    suspend fun saveToLibrary(entry: Entry) {
        store.edit { prefs ->
            val library = readLibrary(prefs)
            val saved = library.firstOrNull { it.matchesSource(entry.url, entry.seriesUrl) }
                ?: decode(prefs[KEY].orEmpty()).firstOrNull { it.matchesSource(entry.url, entry.seriesUrl) }
            val update = if (saved == null || entry.lastPlayedAt > saved.lastPlayedAt) entry else saved
            prefs[LIBRARY_KEY] = encode(library.upsert(update.copy(
                seriesTitle = entry.seriesTitle ?: saved?.seriesTitle,
                animeId = entry.animeId ?: saved?.animeId,
                animeTitle = entry.animeTitle ?: saved?.animeTitle
            )))
        }
    }

    suspend fun removeFromLibrary(key: String) {
        store.edit { prefs ->
            prefs[LIBRARY_KEY] = encode(readLibrary(prefs).filterNot { it.libraryKey == key })
        }
    }

    suspend fun remove(url: String) {
        store.edit { prefs ->
            prefs[LIBRARY_KEY] = encode(readLibrary(prefs))
            prefs[KEY] = encode(decode(prefs[KEY].orEmpty()).filterNot { it.url == url })
        }
    }

    suspend fun clear() {
        store.edit { prefs ->
            prefs[LIBRARY_KEY] = encode(readLibrary(prefs))
            prefs.remove(KEY)
        }
    }

    internal companion object {
        val KEY = stringPreferencesKey("entries")
        val LIBRARY_KEY = stringPreferencesKey("library_entries")
        const val MAX_ENTRIES = 20

        private val gson = Gson()
        private val listType = object : TypeToken<List<Entry>>() {}.type

        internal fun encode(entries: List<Entry>): String = gson.toJson(entries)

        private fun readLibrary(prefs: Preferences): List<Entry> =
            prefs[LIBRARY_KEY]?.let(::decode)
                ?: decode(prefs[KEY].orEmpty()).filter { it.animeId != null }.distinctBy(Entry::libraryKey)

        private fun List<Entry>.upsert(entry: Entry): List<Entry> =
            listOf(entry) + filterNot { it.matchesSource(entry.url, entry.seriesUrl) }

        private fun Entry.matchesSource(address: String, seriesAddress: String?): Boolean =
            url == address || seriesUrl == address ||
                (seriesAddress != null && (seriesUrl == seriesAddress || url == seriesAddress))

        internal fun decode(raw: String): List<Entry> {
            if (raw.isBlank()) return emptyList()
            return runCatching { gson.fromJson<List<Entry>>(raw, listType) }
                .getOrDefault(emptyList())
        }
    }
}
