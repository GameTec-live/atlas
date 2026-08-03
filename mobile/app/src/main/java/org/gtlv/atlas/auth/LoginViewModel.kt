package org.gtlv.atlas.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.gtlv.core.repository.AuthRepository
import org.gtlv.core.repository.AuthResult
import org.gtlv.core.session.SessionRestoreResult
import org.gtlv.core.settings.ServerSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val serverSettingsRepository: ServerSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serverSettingsRepository.serverAddress.collect { address ->
                _uiState.update {
                    it.copy(serverAddress = address)
                }
            }
        }

        viewModelScope.launch {
            restoreSession()
        }
    }

    fun openServerDialog() {
        _uiState.update {
            it.copy(
                showServerDialog = true,
                serverAddressInput = it.serverAddress,
                serverAddressError = null
            )
        }
    }

    fun closeServerDialog() {
        _uiState.update {
            it.copy(
                showServerDialog = false,
                serverAddressError = null
            )
        }
    }

    fun onServerAddressChanged(value: String) {
        _uiState.update {
            it.copy(
                serverAddressInput = value,
                serverAddressError = null
            )
        }
    }

    fun saveServerAddress() {
        val input = _uiState.value.serverAddressInput
            .trim()
            .removeSuffix("/")

        val parsedAddress = input.toHttpUrlOrNull()

        if (
            parsedAddress == null ||
            parsedAddress.scheme !in setOf("http", "https")
        ) {
            _uiState.update {
                it.copy(
                    serverAddressError =
                        "Enter a valid HTTP or HTTPS address"
                )
            }
            return
        }

        viewModelScope.launch {
            serverSettingsRepository.setServerAddress(input)

            _uiState.update {
                it.copy(
                    showServerDialog = false,
                    serverAddressError = null
                )
            }
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = null,
                loginError = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                loginError = null
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update {
            it.copy(
                passwordVisible = !it.passwordVisible
            )
        }
    }

    private suspend fun restoreSession() {
        when (val result = authRepository.restoreStoredSession()) {
            is SessionRestoreResult.Valid -> {
                _uiState.update {
                    it.copy(
                        isCheckingSession = false,
                        loginSuccessful = true,
                        loginError = null
                    )
                }
            }

            SessionRestoreResult.NoStoredSession,
            SessionRestoreResult.Expired -> {
                _uiState.update {
                    it.copy(
                        isCheckingSession = false
                    )
                }
            }

            SessionRestoreResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isCheckingSession = false,
                        loginError =
                            "Cannot verify the saved session. Check your connection."
                    )
                }
            }

            SessionRestoreResult.InvalidResponse -> {
                _uiState.update {
                    it.copy(
                        isCheckingSession = false,
                        loginError =
                            "The server returned an invalid session response"
                    )
                }
            }

            is SessionRestoreResult.ServerError -> {
                _uiState.update {
                    it.copy(
                        isCheckingSession = false,
                        loginError =
                            "Session check failed (${result.statusCode})"
                    )
                }
            }
        }
    }

    fun login() {
        val currentState = _uiState.value

        val emailError = if (currentState.email.isBlank()) {
            "Enter your email address"
        } else {
            null
        }

        val passwordError = if (currentState.password.isBlank()) {
            "Enter your password"
        } else {
            null
        }

        if (emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError
                )
            }
            return
        }

        if (currentState.isLoading) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loginError = null
                )
            }

            when (
                val result = authRepository.login(
                    email = currentState.email.trim(),
                    password = currentState.password
                )
            ) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSuccessful = true,
                            loginError = null
                        )
                    }
                }

                AuthResult.InvalidCredentials -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginError = "Invalid email or password"
                        )
                    }
                }

                AuthResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginError = "Cannot connect to the server"
                        )
                    }
                }

                AuthResult.InvalidResponse -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginError = "The server returned an invalid response"
                        )
                    }
                }

                is AuthResult.ServerError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginError = result.message
                                ?: "Server error (${result.statusCode})"
                        )
                    }
                }
            }
        }
    }
}