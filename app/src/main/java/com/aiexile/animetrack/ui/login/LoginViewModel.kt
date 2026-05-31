package com.aiexile.animetrack.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoginSuccess: Boolean = false,
    val error: String? = null
)

class LoginViewModel(
    private val authManager: AuthManager
) : ViewModel() {

    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun fetchAccessToken(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.bangumiAuthApi.getAccessToken(
                    clientId = AuthManager.CLIENT_ID,
                    clientSecret = AuthManager.CLIENT_SECRET,
                    code = code,
                    redirectUri = AuthManager.REDIRECT_URI
                )

                Log.d(TAG, "Token received, user_id=${response.user_id}")

                authManager.saveTokens(
                    access = response.access_token,
                    refresh = response.refresh_token ?: ""
                )

                try {
                    val profile = RetrofitClient.bangumiApi.getMyProfile()
                    authManager.saveUserProfile(
                        avatar = profile.avatar?.bestUrl,
                        nickname = profile.nickname,
                        bangumiId = profile.id
                    )
                    Log.d(TAG, "Profile fetched: nickname=${profile.nickname}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch profile", e)
                }

                _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch access token", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "登录失败：${e.message}"
                )
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(AppContainer.getAuthManager()) as T
        }
    }
}
