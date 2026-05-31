package com.aiexile.animetrack.data.auth

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.withContext
import java.io.File

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

class AuthManager(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        private val USER_AVATAR_KEY = stringPreferencesKey("user_avatar")
        private val USER_NICKNAME_KEY = stringPreferencesKey("user_nickname")
        private val USER_BANGUMI_ID_KEY = intPreferencesKey("user_bangumi_id")
        private val CUSTOM_AVATAR_URI_KEY = stringPreferencesKey("custom_avatar_uri")

        const val CLIENT_ID = "bgm61706a0cc8ae6c766"
        const val CLIENT_SECRET = "7023507e986957be53c3b36d69d0ac44"
        const val REDIRECT_URI = "https://localhost"
        const val AUTH_URL = "https://bgm.tv/oauth/authorize?client_id=$CLIENT_ID&response_type=code&redirect_uri=$REDIRECT_URI"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedAccessToken: String? = null

    init {
        scope.launch {
            cachedAccessToken = context.authDataStore.data.first()[ACCESS_TOKEN_KEY]
        }
        scope.launch {
            context.authDataStore.data.map { it[ACCESS_TOKEN_KEY] }.collect { token ->
                cachedAccessToken = token
            }
        }
    }

    fun getCachedAccessToken(): String? = cachedAccessToken

    val isLoggedIn: Flow<Boolean> = context.authDataStore.data
        .map { preferences -> preferences[IS_LOGGED_IN_KEY] ?: false }

    val accessToken: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[ACCESS_TOKEN_KEY] }

    val userAvatar: Flow<String?> = context.authDataStore.data
        .map { preferences ->
            val custom = preferences[CUSTOM_AVATAR_URI_KEY]
            if (custom != null && File(custom).exists()) custom
            else preferences[USER_AVATAR_KEY]
        }

    val userNickname: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[USER_NICKNAME_KEY] }

    val userBangumiId: Flow<Int?> = context.authDataStore.data
        .map { preferences -> preferences[USER_BANGUMI_ID_KEY] }

    val customAvatarUri: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[CUSTOM_AVATAR_URI_KEY] }

    suspend fun saveTokens(access: String, refresh: String) {
        context.authDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = access
            preferences[REFRESH_TOKEN_KEY] = refresh
            preferences[IS_LOGGED_IN_KEY] = true
        }
    }

    suspend fun saveUserProfile(avatar: String?, nickname: String?, bangumiId: Int?) {
        context.authDataStore.edit { preferences ->
            if (avatar != null) preferences[USER_AVATAR_KEY] = avatar
            if (nickname != null) preferences[USER_NICKNAME_KEY] = nickname
            if (bangumiId != null) preferences[USER_BANGUMI_ID_KEY] = bangumiId
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
        context.authDataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences[IS_LOGGED_IN_KEY] = false
            preferences.remove(USER_AVATAR_KEY)
            preferences.remove(USER_NICKNAME_KEY)
            preferences.remove(USER_BANGUMI_ID_KEY)
            preferences.remove(CUSTOM_AVATAR_URI_KEY)
        }
        if (customPath != null) {
            withContext(Dispatchers.IO) {
                File(customPath).takeIf { it.exists() }?.delete()
            }
        }
    }
}
