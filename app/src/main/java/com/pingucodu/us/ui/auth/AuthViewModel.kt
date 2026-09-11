package com.pingucodu.us.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.auth.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthStatus {
    data object Loading : AuthStatus
    data object LoggedOut : AuthStatus
    data class LoggedIn(val username: String) : AuthStatus
}

data class LoginUiState(val isLoading: Boolean = false, val errorMessage: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {

    val authStatus: StateFlow<AuthStatus> = repository.loggedInUsername
        .map { username -> if (username != null) AuthStatus.LoggedIn(username) else AuthStatus.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthStatus.Loading)

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    fun login(username: String, pin: String) {
        _loginUiState.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            _loginUiState.value = when (val result = repository.login(username, pin)) {
                LoginResult.Success -> LoginUiState()
                LoginResult.WrongPin -> LoginUiState(errorMessage = "wrong pin")
                LoginResult.PinNotSetUp -> LoginUiState(errorMessage = "pin not set up yet")
                is LoginResult.NetworkError -> LoginUiState(errorMessage = result.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}
