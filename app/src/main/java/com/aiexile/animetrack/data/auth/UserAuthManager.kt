package com.aiexile.animetrack.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.userAuthDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_auth")

class UserAuthManager(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_ID_KEY = intPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val EMAIL_KEY = stringPreferencesKey("email")
        private val AVATAR_KEY = stringPreferencesKey("avatar")
        private val CREATED_AT_KEY = stringPreferencesKey("created_at")
        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        private val TOKEN_EXPIRED_KEY = booleanPreferencesKey("token_expired")
        private val TOKEN_KICKED_KEY = booleanPreferencesKey("token_kicked")
        private val REGISTRATION_ID_REPORTED_KEY = stringPreferencesKey("registration_id_reported")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    init {
        scope.launch {
            val prefs = context.userAuthDataStore.data.first()
            cachedAccessToken = prefs[ACCESS_TOKEN_KEY]
            cachedRefreshToken = prefs[REFRESH_TOKEN_KEY]
        }
        scope.launch {
            context.userAuthDataStore.data.map { it[ACCESS_TOKEN_KEY] }.collect { cachedAccessToken = it }
        }
        scope.launch {
            context.userAuthDataStore.data.map { it[REFRESH_TOKEN_KEY] }.collect { cachedRefreshToken = it }
        }
    }

    fun getCachedAccessToken(): String? = cachedAccessToken

    fun getCachedRefreshToken(): String? = cachedRefreshToken

    val isLoggedIn: Flow<Boolean> = context.userAuthDataStore.data
        .map { preferences -> preferences[IS_LOGGED_IN_KEY] ?: false }

    /** token 是否已失效（refresh 链路确认无法恢复时标记）；失效后条目变灰并引导强制重新登录 */
    val tokenExpired: Flow<Boolean> = context.userAuthDataStore.data
        .map { preferences -> preferences[TOKEN_EXPIRED_KEY] ?: false }

    /** 失效是否因设备被下线（服务端 kicked 标记）；用于横幅区分文案 */
    val tokenKicked: Flow<Boolean> = context.userAuthDataStore.data
        .map { preferences -> preferences[TOKEN_KICKED_KEY] ?: false }

    val username: Flow<String?> = context.userAuthDataStore.data
        .map { preferences -> preferences[USERNAME_KEY] }

    val email: Flow<String?> = context.userAuthDataStore.data
        .map { preferences -> preferences[EMAIL_KEY] }

    val avatar: Flow<String?> = context.userAuthDataStore.data
        .map { preferences -> preferences[AVATAR_KEY] }

    val createdAt: Flow<String?> = context.userAuthDataStore.data
        .map { preferences -> preferences[CREATED_AT_KEY] }

    /** 当前登录用户 ID，未登录返回 null */
    val userId: Flow<Int?> = context.userAuthDataStore.data
        .map { preferences -> preferences[USER_ID_KEY] }

    /** 同步获取当前用户 ID（用于非 UI 场景，未登录返回 null） */
    suspend fun getUserId(): Long? = userId.first()?.toLong()

    suspend fun saveLogin(
        accessToken: String,
        refreshToken: String,
        userId: Int,
        username: String,
        email: String?,
        createdAt: String?,
        avatar: String? = null
    ) {
        context.userAuthDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[USER_ID_KEY] = userId
            preferences[USERNAME_KEY] = username
            if (email != null) preferences[EMAIL_KEY] = email
            if (avatar != null) preferences[AVATAR_KEY] = avatar
            if (createdAt != null) preferences[CREATED_AT_KEY] = createdAt
            preferences[IS_LOGGED_IN_KEY] = true
            // 重新登录成功，清除失效与被踢标记
            preferences.remove(TOKEN_EXPIRED_KEY)
            preferences.remove(TOKEN_KICKED_KEY)
            // 新会话需重新上报 registrationId 绑定（服务端按会话级存储，用于被踢实时通知）
            preferences.remove(REGISTRATION_ID_REPORTED_KEY)
        }
    }

    /**
     * 标记 token 已失效（refresh 链路确认无法恢复时调用）。
     * 保留 token 与用户资料，仅置失效标记：UI 层据此变灰并引导强制重新登录。
     * [kicked] 为 true 表示设备被其他端下线（服务端 kicked 标记），横幅将展示被踢文案。
     * 幂等：已处于失效态时不重复写入。
     */
    suspend fun markTokenExpired(kicked: Boolean = false) {
        context.userAuthDataStore.edit { preferences ->
            if (preferences[TOKEN_EXPIRED_KEY] != true || (kicked && preferences[TOKEN_KICKED_KEY] != true)) {
                preferences[TOKEN_EXPIRED_KEY] = true
                if (kicked) preferences[TOKEN_KICKED_KEY] = true
            }
        }
    }

    /** 更新头像路径（上传成功后调用） */
    suspend fun updateAvatar(avatar: String?) {
        context.userAuthDataStore.edit { preferences ->
            if (avatar != null) preferences[AVATAR_KEY] = avatar
            else preferences.remove(AVATAR_KEY)
        }
    }

    /** 更新绑定邮箱（更换邮箱成功后调用） */
    suspend fun updateEmail(email: String) {
        context.userAuthDataStore.edit { preferences ->
            preferences[EMAIL_KEY] = email
        }
    }

    suspend fun updateAccessToken(newToken: String) {
        context.userAuthDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = newToken
        }
    }

    suspend fun logout() {
        cachedAccessToken = null
        cachedRefreshToken = null
        context.userAuthDataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(USERNAME_KEY)
            preferences.remove(EMAIL_KEY)
            preferences.remove(AVATAR_KEY)
            preferences.remove(CREATED_AT_KEY)
            preferences[IS_LOGGED_IN_KEY] = false
            // 主动登出后失效与被踢标记无意义，一并清除
            preferences.remove(TOKEN_EXPIRED_KEY)
            preferences.remove(TOKEN_KICKED_KEY)
            preferences.remove(REGISTRATION_ID_REPORTED_KEY)
        }
    }

    /** 获取已上报的 registrationId，用于判断是否需要重新上报 */
    suspend fun getReportedRegistrationId(): String? {
        return context.userAuthDataStore.data.first()[REGISTRATION_ID_REPORTED_KEY]
    }

    /** 标记 registrationId 已上报 */
    suspend fun setRegistrationIdReported(registrationId: String) {
        context.userAuthDataStore.edit { preferences ->
            preferences[REGISTRATION_ID_REPORTED_KEY] = registrationId
        }
    }
}
