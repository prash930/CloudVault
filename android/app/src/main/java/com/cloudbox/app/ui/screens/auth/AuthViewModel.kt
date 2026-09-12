package com.cloudbox.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudbox.app.data.api.models.UserResponse
import com.cloudbox.app.data.local.TokenManager
import com.cloudbox.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val registerSuccess: Boolean = false,
    val forgotPasswordSuccess: Boolean = false,
    val user: UserResponse? = null
)

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.login(email, password)
            if (result.isSuccess) {
                val tokenResponse = result.getOrNull()
                tokenResponse?.access_token?.let { TokenManager.saveToken(it) }
                tokenResponse?.user?.let { user ->
                    TokenManager.saveUserInfo(user.email, user.display_name, user.role)
                }
                _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun register(email: String, displayName: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.register(email, displayName, password)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, registerSuccess = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, forgotPasswordSuccess = false) }
            val result = repository.forgotPassword(email)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, forgotPasswordSuccess = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.logout()
            TokenManager.clearAll()
            _uiState.update { it.copy(isLoading = false, loginSuccess = false) }
        }
    }

    fun checkAuthStatus() {
        viewModelScope.launch {
            if (TokenManager.isLoggedIn()) {
                val result = repository.getMe()
                if (result.isSuccess) {
                    _uiState.update { it.copy(user = result.getOrNull()) }
                } else {
                    // Token might be invalid
                    TokenManager.clearAll()
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
