package com.aiexile.animetrack.data.network

import android.content.Context
import android.util.Log
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WbiKeyManager {

    private const val TAG = "WbiKeyManager"
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
    private const val PREFS_NAME = "wbi_key_cache"
    private const val KEY_IMG = "img_key"
    private const val KEY_SUB = "sub_key"
    private const val KEY_TIMESTAMP = "cache_timestamp"

    @Volatile
    private var cachedKeys: Pair<String, String>? = null

    @Volatile
    private var cacheTimestamp: Long = 0

    private val refreshMutex = Mutex()

    fun restoreFromStorage(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val imgKey = sp.getString(KEY_IMG, null)
        val subKey = sp.getString(KEY_SUB, null)
        val timestamp = sp.getLong(KEY_TIMESTAMP, 0)

        if (imgKey != null && subKey != null && timestamp > 0) {
            cachedKeys = Pair(imgKey, subKey)
            cacheTimestamp = timestamp
            return true
        }
        return false
    }

    private fun persistToStorage(context: Context) {
        val keys = cachedKeys ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IMG, keys.first)
            .putString(KEY_SUB, keys.second)
            .putLong(KEY_TIMESTAMP, cacheTimestamp)
            .apply()
    }

    private fun isCacheValid(): Boolean {
        return cachedKeys != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    suspend fun getWbiKeys(): Result<Pair<String, String>> {
        val cached = cachedKeys
        if (cached != null && isCacheValid()) return Result.success(cached)

        return refreshMutex.withLock {
            val rechecked = cachedKeys
            if (rechecked != null && isCacheValid()) return@withLock Result.success(rechecked)
            refreshKeysInternal()
        }
    }

    private suspend fun refreshKeysInternal(): Result<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = RetrofitClient.bilibiliApi
                val navResp = api.getNavInfo()

                if (navResp.code == 0 && navResp.data?.wbiImg != null) {
                    val wbiImg = navResp.data.wbiImg
                    val imgKey = wbiImg.imgUrl.substringAfterLast("/").substringBefore(".")
                    val subKey = wbiImg.subUrl.substringAfterLast("/").substringBefore(".")

                    cachedKeys = Pair(imgKey, subKey)
                    cacheTimestamp = System.currentTimeMillis()

                    try {
                        persistToStorage(AppContainer.getApplication())
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to persist Wbi keys", e)
                    }

                    Result.success(Pair(imgKey, subKey))
                } else {
                    Result.failure(Exception("WBI keys not found in nav response"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh Wbi keys", e)
                Result.failure(e)
            }
        }
    }
}
