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

    val username: Flow<String?> = context.userAuthDataStore.data
        .map { preferences -> preferences[USERNAME_KEY] }

    val email: Flow<String?> = context.userAuthDataStore.data
        .map { preferences -> preferences[EMAIL_KEY] }

    val avatar: Flow<String?> = context.userAuthDataStore.data
        .map { preferences -> preferences[AVATAR_KEY] }

    val createdAt: Flow<String?> = context.userAuthDataStore.data
        .map { preferences -> preferences[CREATED_AT_KEY] }

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
        }
    }

    /** 更新头像路径（上传成功后调用） */
    suspend fun updateAvatar(avatar: String?) {
        context.userAuthDataStore.edit { preferences ->
            if (avatar != null) preferences[AVATAR_KEY] = avatar
            else preferences.remove(AVATAR_KEY)
        }
    }

    suspend fun updateAccessToken(newToken: String) {
        context.userAuthDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = newToken
        }
    }

    suspend fun logout() {
        context.userAuthDataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(USERNAME_KEY)
            preferences.remove(EMAIL_KEY)
            preferences.remove(AVATAR_KEY)
            preferences.remove(CREATED_AT_KEY)
            preferences[IS_LOGGED_IN_KEY] = false
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
