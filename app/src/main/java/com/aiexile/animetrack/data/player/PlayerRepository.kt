package com.aiexile.animetrack.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_positions")

class PlayerRepository(private val context: Context) {

    companion object {
        private val POSITIONS_KEY = stringPreferencesKey("playback_positions")
        private const val COMPLETION_THRESHOLD = 0.95
    }

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Long>>() {}.type

    private suspend fun getPositionsMap(): Map<String, Long> = withContext(Dispatchers.IO) {
        val json = context.playerDataStore.data.first()[POSITIONS_KEY] ?: return@withContext emptyMap()
        try {
            gson.fromJson<Map<String, Long>>(json, mapType) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun savePositionsMap(positions: Map<String, Long>) = withContext(Dispatchers.IO) {
        val json = gson.toJson(positions)
        context.playerDataStore.edit { it[POSITIONS_KEY] = json }
    }

    suspend fun savePlaybackPosition(mediaId: String, positionMs: Long, durationMs: Long) {
        val positions = getPositionsMap().toMutableMap()
        if (durationMs > 0 && positionMs > durationMs * COMPLETION_THRESHOLD) {
            positions.remove(mediaId)
        } else {
            positions[mediaId] = positionMs
        }
        savePositionsMap(positions)
    }

    suspend fun getPlaybackPosition(mediaId: String): Long? {
        return getPositionsMap()[mediaId]
    }

    suspend fun clearPlaybackPosition(mediaId: String) {
        val positions = getPositionsMap().toMutableMap()
        positions.remove(mediaId)
        savePositionsMap(positions)
    }
}
