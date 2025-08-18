package org.jetbrains.demo.auth

import androidx.lifecycle.ViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.demo.config.AppConfig

class AuthViewModel(
    private val tokenStorage: TokenProvider,
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    base: co.touchlab.kermit.Logger
) : ViewModel() {
    private val logger = base.withTag("AuthViewModel")

    sealed class AuthState {
        data object Loading : AuthState()
        data class Error(val message: String) : AuthState()
        data object SignedOut : AuthState()
        data object SignedIn : AuthState()
    }

    private val _state = MutableStateFlow<AuthState>(
        if (tokenStorage.getToken() != null) AuthState.SignedIn else AuthState.SignedOut
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun signIn() {
        logger.d("AuthViewModel: Starting sign-in process")
        _state.value = AuthState.Loading
        val newToken = tokenStorage.refreshToken()
        if (newToken != null) {
            logger.d("AuthViewModel: Token refresh successful")
            registerUser()
        } else {
            logger.d("AuthViewModel: Token refresh failed")
            _state.value = AuthState.SignedOut
        }
    }

    private suspend fun registerUser() {
        logger.d("AuthViewModel: Registering user")
        val response = httpClient.post("${appConfig.apiBaseUrl}/user/register")
        if (response.status == HttpStatusCode.OK) {
            logger.d("AuthViewModel: User registration successful")
            _state.value = AuthState.SignedIn
        } else {
            logger.d("AuthViewModel: User registration failed. status: ${response.status.value}}")
            _state.value = AuthState.Error("Failed to register user")
            tokenStorage.clearToken()
        }
    }

    fun signOut() {
        tokenStorage.clearToken()
        _state.value = AuthState.SignedOut
    }

    fun clearError() {
        logger.d("AuthViewModel: Clearing error state")
        _state.value = AuthState.SignedOut
    }
}
