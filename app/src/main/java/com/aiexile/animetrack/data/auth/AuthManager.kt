package com.aiexile.animetrack.data.auth

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
import kotlinx.coroutines.withContext
import java.io.File

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

class AuthManager(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val EXPIRES_AT_KEY = longPreferencesKey("access_token_expires_at")
        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        private val USER_AVATAR_KEY = stringPreferencesKey("user_avatar")
        private val USER_NICKNAME_KEY = stringPreferencesKey("user_nickname")
        private val USER_BANGUMI_ID_KEY = intPreferencesKey("user_bangumi_id")
        private val USER_USERNAME_KEY = stringPreferencesKey("user_username")
        private val CUSTOM_AVATAR_URI_KEY = stringPreferencesKey("custom_avatar_uri")

        const val CLIENT_ID = "bgm61706a0cc8ae6c766"
        const val CLIENT_SECRET = "7023507e986957be53c3b36d69d0ac44"
        const val REDIRECT_URI = "https://localhost"
        const val AUTH_URL = "https://bgm.tv/oauth/authorize?client_id=$CLIENT_ID&response_type=code&redirect_uri=$REDIRECT_URI"

        /** 提前刷新阈值：token 在此时间内即将过期时主动刷新（5 分钟） */
        private const val REFRESH_ADVANCE_MS = 5 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    @Volatile
    private var cachedExpiresAt: Long = 0L

    init {
        scope.launch {
            val prefs = context.authDataStore.data.first()
            cachedAccessToken = prefs[ACCESS_TOKEN_KEY]
            cachedRefreshToken = prefs[REFRESH_TOKEN_KEY]
            cachedExpiresAt = prefs[EXPIRES_AT_KEY] ?: 0L
        }
        scope.launch {
            context.authDataStore.data.map { it[ACCESS_TOKEN_KEY] }.collect { token ->
                cachedAccessToken = token
            }
        }
        scope.launch {
            context.authDataStore.data.map { it[REFRESH_TOKEN_KEY] }.collect { token ->
                cachedRefreshToken = token
            }
        }
        scope.launch {
            context.authDataStore.data.map { it[EXPIRES_AT_KEY] ?: 0L }.collect { expiresAt ->
                cachedExpiresAt = expiresAt
            }
        }
    }

    fun getCachedAccessToken(): String? = cachedAccessToken

    fun getCachedRefreshToken(): String? = cachedRefreshToken

    fun getCachedExpiresAt(): Long = cachedExpiresAt

    /**
     * 判断 access_token 是否即将过期（距过期不足 [REFRESH_ADVANCE_MS]）或已过期。
     * 无过期时间记录（老用户未保存 expires_in）时返回 false，由 401 被动刷新兜底。
     */
    fun isTokenExpiringSoon(): Boolean {
        val expiresAt = cachedExpiresAt
        if (expiresAt <= 0L) return false
        return System.currentTimeMillis() + REFRESH_ADVANCE_MS >= expiresAt
    }

    val isLoggedIn: Flow<Boolean> = context.authDataStore.data
        .map { preferences -> preferences[IS_LOGGED_IN_KEY] ?: false }

    val accessToken: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[ACCESS_TOKEN_KEY] }

    val userAvatar: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[USER_AVATAR_KEY] }

    val userNickname: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[USER_NICKNAME_KEY] }

    val userBangumiId: Flow<Int?> = context.authDataStore.data
        .map { preferences -> preferences[USER_BANGUMI_ID_KEY] }

    val userUsername: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[USER_USERNAME_KEY] }

    val customAvatarUri: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[CUSTOM_AVATAR_URI_KEY] }

    /**
     * 保存登录后获取的 token 集合。
     * [expiresIn] 为 access_token 的有效期（秒），由 Bangumi OAuth 响应的 expires_in 字段提供；
     * 为 null 时（异常情况）不记录过期时间，由 401 被动刷新兜底。
     */
    suspend fun saveTokens(access: String, refresh: String, expiresIn: Int? = null) {
        val expiresAt = if (expiresIn != null && expiresIn > 0) {
            System.currentTimeMillis() + expiresIn * 1000L
        } else 0L
        context.authDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = access
            preferences[REFRESH_TOKEN_KEY] = refresh
            if (expiresAt > 0L) preferences[EXPIRES_AT_KEY] = expiresAt
            preferences[IS_LOGGED_IN_KEY] = true
        }
    }

    /**
     * 刷新 token 后更新 access_token 及其过期时间，保留原 refresh_token。
     * Bangumi 的 refresh_token 在每次刷新后会返回新的 refresh_token（滚动刷新），
     * 若响应携带新 refresh_token 则一并更新。
     */
    suspend fun updateAccessToken(newToken: String, expiresIn: Int? = null, newRefreshToken: String? = null) {
        val expiresAt = if (expiresIn != null && expiresIn > 0) {
            System.currentTimeMillis() + expiresIn * 1000L
        } else 0L
        context.authDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = newToken
            if (expiresAt > 0L) preferences[EXPIRES_AT_KEY] = expiresAt
            if (!newRefreshToken.isNullOrBlank()) preferences[REFRESH_TOKEN_KEY] = newRefreshToken
        }
    }

    suspend fun saveUserProfile(avatar: String?, nickname: String?, bangumiId: Int?, username: String? = null) {
        context.authDataStore.edit { preferences ->
            if (avatar != null) preferences[USER_AVATAR_KEY] = avatar
            if (nickname != null) preferences[USER_NICKNAME_KEY] = nickname
            if (bangumiId != null) preferences[USER_BANGUMI_ID_KEY] = bangumiId
            if (username != null) preferences[USER_USERNAME_KEY] = username
        }
    }

    suspend fun saveCustomAvatarUri(uri: String?) {
        if (uri == null) {
            val oldPath = context.authDataStore.data.first()[CUSTOM_AVATAR_URI_KEY]
            context.authDataStore.edit { preferences ->
                preferences.remove(CUSTOM_AVATAR_URI_KEY)
            }
            if (oldPath != null) {
                withContext(Dispatchers.IO) {
                    File(oldPath).takeIf { it.exists() }?.delete()
                }
            }
            return
        }

        val parsedUri = Uri.parse(uri)
        val savedPath = withContext(Dispatchers.IO) {
            val oldPath = context.authDataStore.data.first()[CUSTOM_AVATAR_URI_KEY]
            if (oldPath != null) {
                File(oldPath).takeIf { it.exists() }?.delete()
            }

            val avatarDir = File(context.filesDir, "avatars")
            if (!avatarDir.exists()) avatarDir.mkdirs()
            val destFile = File(avatarDir, "custom_avatar")

            context.contentResolver.openInputStream(parsedUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            destFile.absolutePath
        }

        context.authDataStore.edit { preferences ->
            preferences[CUSTOM_AVATAR_URI_KEY] = savedPath
        }
    }

    suspend fun logout() {
        val customPath = context.authDataStore.data.first()[CUSTOM_AVATAR_URI_KEY]
        cachedAccessToken = null
        cachedRefreshToken = null
        cachedExpiresAt = 0L
        context.authDataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(EXPIRES_AT_KEY)
            preferences[IS_LOGGED_IN_KEY] = false
            preferences.remove(USER_AVATAR_KEY)
            preferences.remove(USER_NICKNAME_KEY)
            preferences.remove(USER_BANGUMI_ID_KEY)
            preferences.remove(USER_USERNAME_KEY)
            preferences.remove(CUSTOM_AVATAR_URI_KEY)
        }
        if (customPath != null) {
            withContext(Dispatchers.IO) {
                File(customPath).takeIf { it.exists() }?.delete()
            }
        }
    }
}
