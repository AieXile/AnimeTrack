package com.aiexile.animetrack.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.data.player.webdav.WebDavPlaybackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_positions")

class PlayerRepository(private val context: Context) {

    companion object {
        private val POSITIONS_KEY = stringPreferencesKey("playback_positions")
        private val PROGRESS_KEY = stringPreferencesKey("playback_progress")
        private val CLAIMED_WEBDAV_KEY = stringSetPreferencesKey("claimed_webdav_legacy_progress")
        private const val COMPLETION_THRESHOLD = 0.95
    }

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Long>>() {}.type
    private val progressType = object : TypeToken<Map<String, PlaybackProgress>>() {}.type

    /** 旧版本只有位置，没有时长；仍保留其断点并显示已观看位置。 */
    val progress: Flow<Map<String, PlaybackProgress>> = context.playerDataStore.data.map { prefs ->
        decodePositions(prefs[POSITIONS_KEY]).mapValues { PlaybackProgress(positionMs = it.value) } +
            decodeProgress(prefs[PROGRESS_KEY])
    }.distinctUntilChanged()

    private suspend fun getPositionsMap(): Map<String, Long> = withContext(Dispatchers.IO) {
        val json = context.playerDataStore.data.first()[POSITIONS_KEY] ?: return@withContext emptyMap()
        try {
            gson.fromJson<Map<String, Long>>(json, mapType) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long,
        completed: Boolean = false, observedAt: Long = System.currentTimeMillis(), watchedMs: Long = 0L) {
        context.playerDataStore.edit { prefs ->
            val records = decodeProgress(prefs[PROGRESS_KEY]).toMutableMap()
            val update = PlaybackProgress(positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L),
                completed, observedAt, watchedMs.coerceAtLeast(0L))
            val record = records[mediaId]?.merge(update) ?: update
            if (record.updatedAt > observedAt) return@edit
            records[mediaId] = record
            val positions = decodePositions(prefs[POSITIONS_KEY]).toMutableMap()
            if (record.completed || (!mediaId.startsWith("web:") &&
                    durationMs > 0 && positionMs > durationMs * COMPLETION_THRESHOLD)) {
                positions.remove(mediaId)
            } else {
                positions[mediaId] = record.positionMs
            }
            prefs[POSITIONS_KEY] = gson.toJson(positions)
            prefs[PROGRESS_KEY] = gson.toJson(records)
        }
    }

    /** 只在第一次打开该稳定身份时补齐记录；旧 URL 键原样保留。 */
    internal suspend fun importLegacyWebDavProgress(target: WebDavPlaybackIdentity.Target) {
        context.playerDataStore.edit { prefs ->
            val claim = WebDavPlaybackIdentity.legacyClaim(target)
            val claimed = prefs[CLAIMED_WEBDAV_KEY].orEmpty()
            if (claim in claimed) return@edit
            val records = decodeProgress(prefs[PROGRESS_KEY]).toMutableMap()
            val positions = decodePositions(prefs[POSITIONS_KEY]).toMutableMap()
            val legacy = chooseLegacyWebDavProgress(target, positions, records) ?: return@edit
            records[target.mediaKey] = legacy.progress
            legacy.positionMs?.let { positions[target.mediaKey] = it }
            prefs[CLAIMED_WEBDAV_KEY] = claimed + claim
            prefs[PROGRESS_KEY] = gson.toJson(records)
            prefs[POSITIONS_KEY] = gson.toJson(positions)
        }
    }

    suspend fun getPlaybackPosition(mediaId: String): Long? {
        return getPositionsMap()[mediaId]
    }

    suspend fun clearPlaybackPosition(mediaId: String) {
        context.playerDataStore.edit { prefs ->
            val positions = decodePositions(prefs[POSITIONS_KEY]).toMutableMap()
            positions.remove(mediaId)
            prefs[POSITIONS_KEY] = gson.toJson(positions)
        }
    }

    private fun decodePositions(raw: String?): Map<String, Long> = runCatching {
        gson.fromJson<Map<String, Long>>(raw, mapType)
    }.getOrNull().orEmpty()

    private fun decodeProgress(raw: String?): Map<String, PlaybackProgress> = runCatching {
        gson.fromJson<Map<String, PlaybackProgress>>(raw, progressType)
    }.getOrNull().orEmpty()
}

internal data class LegacyWebDavProgress(val progress: PlaybackProgress, val positionMs: Long?)

/** URL 旧键无账户身份，只有新键尚不存在时才作一次兼容导入。 */
internal fun chooseLegacyWebDavProgress(
    target: WebDavPlaybackIdentity.Target,
    positions: Map<String, Long>,
    records: Map<String, PlaybackProgress>
): LegacyWebDavProgress? {
    if (target.mediaKey in records || target.mediaKey in positions) return null
    val exact = target.requestUrl.toString()
    val key = (records.keys + positions.keys).asSequence()
        .filter { WebDavPlaybackIdentity.isLegacyAlias(it, target) }
        .maxWithOrNull(compareBy<String> { records[it]?.updatedAt ?: Long.MIN_VALUE }
            .thenBy { records[it]?.completed == true }
            .thenBy { it == exact }
            .thenBy { it }) ?: return null
    val progress = records[key] ?: PlaybackProgress(positionMs = positions.getValue(key))
    val position = positions[key]?.takeIf { it > 0 && !progress.completed }
    return LegacyWebDavProgress(progress, position)
}
