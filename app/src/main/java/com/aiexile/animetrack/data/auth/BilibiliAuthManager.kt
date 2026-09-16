package com.aiexile.animetrack.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.bilibiliAuthDataStore: DataStore<Preferences> by preferencesDataStore(name = "bilibili_auth")

class BilibiliAuthManager(private val context: Context) {

    companion object {
        private val SESSDATA_KEY = stringPreferencesKey("bilibili_sessdata")
        private val BILI_JCT_KEY = stringPreferencesKey("bilibili_bili_jct")
        private val MID_KEY = longPreferencesKey("bilibili_mid")
        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("bilibili_is_logged_in")
        private val TOKEN_EXPIRED_KEY = booleanPreferencesKey("bilibili_token_expired")
        private val USER_AVATAR_KEY = stringPreferencesKey("bilibili_user_avatar")
        private val USER_NICKNAME_KEY = stringPreferencesKey("bilibili_user_nickname")
        private val LAST_SYNC_TIME_KEY = longPreferencesKey("bilibili_last_sync_time")
        private val BILIBILI_AUTO_SYNC_KEY = booleanPreferencesKey("bilibili_auto_sync")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedSessData: String? = null

    @Volatile
    private var cachedBiliJct: String? = null

    @Volatile
    private var cachedMid: Long? = null

    init {
        scope.launch {
            cachedSessData = context.bilibiliAuthDataStore.data.first()[SESSDATA_KEY]
        }
        scope.launch {
            cachedBiliJct = context.bilibiliAuthDataStore.data.first()[BILI_JCT_KEY]
        }
        scope.launch {
            cachedMid = context.bilibiliAuthDataStore.data.first()[MID_KEY]
        }
        scope.launch {
            context.bilibiliAuthDataStore.data.map { it[SESSDATA_KEY] }.collect { token ->
                cachedSessData = token
            }
        }
        scope.launch {
            context.bilibiliAuthDataStore.data.map { it[BILI_JCT_KEY] }.collect { jct ->
                cachedBiliJct = jct
            }
        }
    }

    fun getCachedSessData(): String? = cachedSessData
    fun getCachedBiliJct(): String? = cachedBiliJct
    fun getCachedMid(): Long? = cachedMid

    val isLoggedIn: Flow<Boolean> = context.bilibiliAuthDataStore.data
        .map { preferences -> preferences[IS_LOGGED_IN_KEY] ?: false }

    /** 会话（SESSDATA）是否已失效（API 返回 -101/-111 时标记）；失效后条目变灰并引导强制重新登录 */
    val tokenExpired: Flow<Boolean> = context.bilibiliAuthDataStore.data
        .map { preferences -> preferences[TOKEN_EXPIRED_KEY] ?: false }

    val sessData: Flow<String?> = context.bilibiliAuthDataStore.data
        .map { preferences -> preferences[SESSDATA_KEY] }

    val mid: Flow<Long?> = context.bilibiliAuthDataStore.data
        .map { preferences -> preferences[MID_KEY] }

    val userAvatar: Flow<String?> = context.bilibiliAuthDataStore.data
        .map { preferences -> preferences[USER_AVATAR_KEY] }

    val userNickname: Flow<String?> = context.bilibiliAuthDataStore.data
        .map { preferences -> preferences[USER_NICKNAME_KEY] }

    val lastSyncTime: Flow<Long> = context.bilibiliAuthDataStore.data
        .map { preferences -> preferences[LAST_SYNC_TIME_KEY] ?: 0L }

    val bilibiliAutoSync: Flow<Boolean> = context.bilibiliAuthDataStore.data
        .map { preferences -> preferences[BILIBILI_AUTO_SYNC_KEY] ?: false }

    suspend fun saveSession(sessData: String, biliJct: String, mid: Long, avatar: String? = null, nickname: String? = null) {
        cachedSessData = sessData
        cachedBiliJct = biliJct
        cachedMid = mid
        context.bilibiliAuthDataStore.edit { preferences ->
            preferences[SESSDATA_KEY] = sessData
            preferences[BILI_JCT_KEY] = biliJct
            preferences[MID_KEY] = mid
            preferences[IS_LOGGED_IN_KEY] = true
            // 重新登录成功，清除失效标记
            preferences.remove(TOKEN_EXPIRED_KEY)
            if (avatar != null) preferences[USER_AVATAR_KEY] = avatar
            if (nickname != null) preferences[USER_NICKNAME_KEY] = nickname
        }
    }

    /**
     * 标记会话已失效（B 站 API 返回 -101 未登录 / -111 csrf 失效时调用）。
     * 保留 cookie 与用户资料，仅置失效标记：UI 层据此变灰并引导强制重新登录。
     * 幂等：已处于失效态时不重复写入。
     */
    suspend fun markTokenExpired() {
        context.bilibiliAuthDataStore.edit { preferences ->
            if (preferences[TOKEN_EXPIRED_KEY] != true) {
                preferences[TOKEN_EXPIRED_KEY] = true
            }
        }
    }

    suspend fun saveUserProfile(avatar: String? = null, nickname: String? = null, mid: Long? = null) {
        if (mid != null) cachedMid = mid
        context.bilibiliAuthDataStore.edit { preferences ->
            if (avatar != null) preferences[USER_AVATAR_KEY] = avatar
            if (nickname != null) preferences[USER_NICKNAME_KEY] = nickname
            if (mid != null) preferences[MID_KEY] = mid
        }
    }

    suspend fun saveLastSyncTime(time: Long) {
        context.bilibiliAuthDataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = time
        }
    }

    suspend fun setBilibiliAutoSync(enabled: Boolean) {
        context.bilibiliAuthDataStore.edit { preferences ->
            preferences[BILIBILI_AUTO_SYNC_KEY] = enabled
        }
    }

    suspend fun logout() {
        cachedSessData = null
        cachedBiliJct = null
        cachedMid = null
        context.bilibiliAuthDataStore.edit { preferences ->
            preferences.remove(SESSDATA_KEY)
            preferences.remove(BILI_JCT_KEY)
            preferences.remove(MID_KEY)
            preferences[IS_LOGGED_IN_KEY] = false
            // 主动登出后失效标记无意义，一并清除
            preferences.remove(TOKEN_EXPIRED_KEY)
            preferences.remove(USER_AVATAR_KEY)
            preferences.remove(USER_NICKNAME_KEY)
            preferences.remove(LAST_SYNC_TIME_KEY)
        }
    }
}
