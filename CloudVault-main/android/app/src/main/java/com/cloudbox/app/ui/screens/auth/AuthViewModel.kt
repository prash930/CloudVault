package com.cloudbox.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val forgotPasswordSuccess: Boolean = false
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

    fun registerThenLogin(email: String, displayName: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.register(email, displayName, password)
            if (result.isFailure) {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
                return@launch
            }
            val login = repository.login(email, password)
            if (login.isSuccess) {
                val tokenResponse = login.getOrNull()
                tokenResponse?.access_token?.let { TokenManager.saveToken(it) }
                tokenResponse?.user?.let { user ->
                    TokenManager.saveUserInfo(user.email, user.display_name, user.role)
                }
                _uiState.update { it.copy(isLoading = false, registerSuccess = true, loginSuccess = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Account created. Please sign in.") }
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
                _uiState.update { it.copy(isLoading = false) }
            } else {
                TokenManager.clearAll()
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
